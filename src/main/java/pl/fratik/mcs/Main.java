/*
 * Copyright (c) 2023 fratik
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package pl.fratik.mcs;

import com.google.common.primitives.Longs;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.Getter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.fratik.mcs.chat.ChatComponent;
import pl.fratik.mcs.chat.TextChatComponent;
import pl.fratik.mcs.chat.TranslateChatComponent;
import pl.fratik.mcs.encoders.HandshakeMinecraftPacketDecoder;
import pl.fratik.mcs.encoders.MinecraftPacketDecoder;
import pl.fratik.mcs.encoders.protocol.ProtocolDecoderRegistry;
import pl.fratik.mcs.encryption.EncryptionUtils;
import pl.fratik.mcs.encryption.Encryptor;
import pl.fratik.mcs.encryption.IdentifiedKey;
import pl.fratik.mcs.packets.*;
import pl.fratik.mcs.players.NonPremiumPlayer;
import pl.fratik.mcs.players.PremiumPlayer;

import javax.crypto.spec.SecretKeySpec;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static pl.fratik.mcs.encryption.EncryptionUtils.generateServerId;

public class Main extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private static final KeyPair SERVER_KEY = EncryptionUtils.createRsaKeyPair(1024);
    @Getter private State state;
    @Getter private int protVer;
    private byte[] verifyToken;
    private String name;
    private UUID uuid;
    private IdentifiedKey key;
    private byte[] sharedSecret;

    @Override
    public void channelRead(@NotNull ChannelHandlerContext ctx, @NotNull Object msg) throws Exception {
        if (state == null) {
            if (msg instanceof LegacyPingPacket) {
                LOGGER.debug("<-> LegacyPing");
                ctx.writeAndFlush(new LegacyDisconnectPacket("\u00A71\000127\000Offline\000Serwer jest offline\0000\0000")).addListener(ChannelFutureListener.CLOSE);
                return;
            }
            if (msg instanceof LegacyHandshakePacket) {
                LOGGER.debug("<-> LegacyHandshake");
                ctx.writeAndFlush(new LegacyDisconnectPacket("Jestes na prehistorycznej wersji Minecrafta. Czemu?")).addListener(ChannelFutureListener.CLOSE);
                return;
            }
            if (!(msg instanceof HandshakePacket)) throw new IllegalStateException();
            LOGGER.debug("-> Handshake: {}", msg);
            switch (((HandshakePacket) msg).getState()) {
                case 1 -> state = State.STATUS;
                case 2 -> {
                    state = State.LOGIN;
                    protVer = ((HandshakePacket) msg).getProtVer();
                    if (!ProtocolDecoderRegistry.hasDecoderForVersion(protVer)) {
                        ctx.writeAndFlush(new DisconnectPacket(new TranslateChatComponent("multiplayer.disconnect.incompatible",
                                new ChatComponent[]{new TextChatComponent("1.12.2-1.20.1")}, "Niezgodny klient! Użyj: 1.12.2-1.20.1", 735),
                                protVer)).addListener(ChannelFutureListener.CLOSE);
                        LOGGER.debug("<- Nieprawidłowa wersja: {}", protVer);
                    }
                }
                default -> throw new IllegalStateException();
            }
            ctx.pipeline().replace(HandshakeMinecraftPacketDecoder.class, "MPD", new MinecraftPacketDecoder(this));
            return;
        }
        switch (state) {
            case STATUS -> {
                if (msg instanceof StatusRequestPacket) {
                    LOGGER.debug("<- Status");
                    ctx.writeAndFlush(new StatusResponsePacket("{\"version\":{\"name\":\"Offline\",\"protocol\":-1}," +
                            "\"players\":{\"max\":0,\"online\":0},\"description\":{\"text\":\"Serwer jest offline\"}}"));
                } else if (msg instanceof PingRequestPacket pmsg) {
                    LOGGER.debug("<-> Ping");
                    ctx.writeAndFlush(new PingResponsePacket(pmsg.getVal()));
                    ctx.close();
                } else throw new IllegalStateException();
            }
            case LOGIN -> {
                if (msg instanceof LoginStartPacket lmsg) {
                    LOGGER.debug("-> LoginStart: {}", lmsg);
                    name = lmsg.getName();
                    uuid = lmsg.getUuid();
                    key = lmsg.getIdentifiedKey();
                    if (!Bootstrap.isPremium()) {
                        verified(ctx);
                        return;
                    }
                    byte[] arr = new byte[4];
                    ThreadLocalRandom.current().nextBytes(arr);
                    verifyToken = arr;
                    LOGGER.debug("<- EncryptionRequestPacket{}", key != null ? " (z kluczem)" : "");
                    ctx.writeAndFlush(new EncryptionRequestPacket(SERVER_KEY.getPublic().getEncoded(), arr));
                    state = State.ENCRYPTION_REQUESTED;
                } else throw new IllegalStateException();
            }
            case ENCRYPTION_REQUESTED -> {
                if (msg instanceof EncryptionResponsePacket emsg) {
                    LOGGER.debug("-> EncryptionResponsePacket");
                    sharedSecret = EncryptionUtils.decryptRsa(SERVER_KEY, emsg.getSharedSecret());
                    ctx.pipeline().addBefore("length", "encrypt", new Encryptor(new SecretKeySpec(sharedSecret, "AES")));
                    try {
                        if (key == null) {
                            if (!Arrays.equals(verifyToken, EncryptionUtils.decryptRsa(SERVER_KEY, emsg.getVerifyToken())))
                                throw new SecurityException();
                        } else {
                            if (!key.verifyDataSignature(emsg.getVerifyToken(), verifyToken, Longs.toByteArray(emsg.getSalt())))
                                throw new SecurityException();
                        }
                        try (Response resp = new OkHttpClient().newCall(new Request.Builder().url("https://sessionserver.mojang.com/session/minecraft/hasJoined?username=" +
                                URLEncoder.encode(name, StandardCharsets.UTF_8) + "&serverId=" + generateServerId(sharedSecret, SERVER_KEY.getPublic())).build()).execute()) {
                            if (resp.code() != 200) throw new IllegalStateException();
                            try (Reader r = resp.body().charStream()) {
                                String rawUuid = new Gson().fromJson(r, JsonObject.class).getAsJsonPrimitive("id").getAsString();
                                UUID decodedUUID = UUID.fromString(rawUuid.substring(0, 8) + '-' + rawUuid.substring(8, 12) + '-' +
                                            rawUuid.substring(12, 16) + '-' + rawUuid.substring(16, 20) + '-' + rawUuid.substring(20));
                                if (key != null) {
                                    if (!key.internalAddHolder(decodedUUID)) throw new IllegalStateException("invalid UUID");
                                } else {
                                    if (uuid != null) {
                                        if (!decodedUUID.equals(uuid)) throw new IllegalStateException("invalid UUID");
                                    } else {
                                        uuid = decodedUUID;
                                    }
                                }
                            }
                        }
                        state = State.ENCRYPTED;
                    } catch (Exception e) {
                        LOGGER.error("<- Weryfikacja nieudana", e);
                        ctx.writeAndFlush(new DisconnectPacket(new TranslateChatComponent("multiplayer.disconnect.unverified_username", null, null, -1), protVer)).addListener(ChannelFutureListener.CLOSE);
                        return;
                    }
                    verified(ctx);
                }
            }
        }
    }

    private void verified(@NotNull ChannelHandlerContext ctx) {
        boolean whitelisted;
        if (Bootstrap.getWhitelist() != null) whitelisted = Bootstrap.isPremium() ? isWhitelisted(uuid) : isWhitelisted(name);
        else whitelisted = true;
        if (!whitelisted) {
            LOGGER.warn("<- {} ({}): Nie na whiteliście", name, uuid);
            ctx.writeAndFlush(new DisconnectPacket(new TranslateChatComponent("multiplayer.disconnect.not_whitelisted", null, "Nie jesteś na białej liście tego serwera!", 393), protVer)).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        if (Bootstrap.getBackuper().isCriticalBackupInProgress()) {
            LOGGER.info("<- Krytyczny backup w toku, poczekaj");
            ctx.writeAndFlush(new DisconnectPacket(new TextChatComponent("Krytyczny backup w toku, poczekaj chwilę i spróbuj ponownie!"), protVer)).addListener(ChannelFutureListener.CLOSE);
        } else {
            LOGGER.info("<- Uruchamiam serwer");
            ctx.writeAndFlush(new DisconnectPacket(new TextChatComponent("Uruchamiam serwer."), protVer)).addListener(ChannelFutureListener.CLOSE)
                    .addListener((ChannelFutureListener) f -> closeServer());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOGGER.error("!X Wykryto błąd w połączeniu z {}", getIp(ctx.channel()), cause);
        ctx.close();
    }

    @Override
    public void channelActive(@NotNull ChannelHandlerContext ctx) throws Exception {
        LOGGER.info("!> Połączenie przychodzące od {}", getIp(ctx.channel()));
    }

    @Override
    public void channelInactive(@NotNull ChannelHandlerContext ctx) throws Exception {
        LOGGER.info("<! Rozłączono z {}", getIp(ctx.channel()));
    }

    private static String getIp(@NotNull Channel chan) {
        if (chan.remoteAddress() instanceof InetSocketAddress address) {
            return address.getAddress().getHostAddress() + ":" + address.getPort();
        } else {
            return chan.remoteAddress().toString();
        }
    }

    private void closeServer() {
        Bootstrap.getChannel().close();
    }

    private boolean isWhitelisted(UUID uuid) {
        return Bootstrap.getWhitelist() == null || Bootstrap.getWhitelist().contains(new PremiumPlayer(null, uuid));
    }

    private boolean isWhitelisted(String nick) {
        return Bootstrap.getWhitelist() == null || Bootstrap.getWhitelist().contains(new NonPremiumPlayer(nick, null));
    }

    public enum State {
        STATUS,
        LOGIN,
        ENCRYPTION_REQUESTED,
        ENCRYPTED
    }
}
