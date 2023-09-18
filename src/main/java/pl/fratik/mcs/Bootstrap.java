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

import ch.qos.logback.classic.spi.LogbackServiceProvider;
import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.fratik.mcs.encoders.*;
import pl.fratik.mcs.players.WhitelistPlayer;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Bootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger(Bootstrap.class);
    @Getter private static Channel channel;
    @Getter private static Whitelist whitelist = null;
    @Getter private static boolean premium = true;

    public static void main(String[] args) throws InterruptedException, IOException {
        ServiceLoader.load(LogbackServiceProvider.class);
        LOGGER.info("Wystartowano");
        LOGGER.info("Sprawdzam konfigurację");
        LOGGER.debug("Czytam server.properties");
        Integer port = null;
        try (FileReader fr = new FileReader("./server.properties")) {
            String props = CharStreams.toString(fr);
            Pattern p = Pattern.compile("server-port=(\\d{1,5})");
            Matcher m = p.matcher(props);
            if (m.find()) {
                try {
                    port = Integer.parseInt(m.group(1));
                } catch (Exception ignored) {}
            }
            if (props.contains("online-mode=false")) premium = false;
            if (props.contains("enforce-whitelist=true")) whitelist = (premium ? new PremiumWhitelist() : new NonPremiumWhitelist());
            LOGGER.debug("Odczytano server.properties");
        } catch (FileNotFoundException e) {
            LOGGER.error("Plik server.properties nie istnieje", e);
            System.exit(1);
        } catch (Exception e) {
            LOGGER.error("Nie udało się odczytać pliku server.properties", e);
            System.exit(1);
        }
        if (port == null) {
            LOGGER.error("Nie udało się odczytać portu!");
            System.exit(1);
        }
        if (whitelist != null) {
            try (FileReader fr = new FileReader("./whitelist.json")) {
                for (JsonElement el : new Gson().fromJson(fr, JsonArray.class))
                    whitelist.add(new WhitelistPlayer(el.getAsJsonObject().get("name").getAsString(),
                            UUID.fromString(el.getAsJsonObject().get("uuid").getAsString())));
            } catch (FileNotFoundException e) {
                LOGGER.error("Plik whitelist.json nie istnieje", e);
                System.exit(1);
            } catch (Exception e) {
                LOGGER.error("Nie udało się odczytać", e);
                System.exit(1);
            }
        }
        LOGGER.info("Odczytano konfigurację: port: {}; whitelista {}; online-mode: {}", port,
                whitelist != null ? String.format("włączona (%s osób)", whitelist.size()) : "wyłączona",
                premium ? "włączony" : "wyłączony (!)");
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new LegacyPingDecoder());
                            ch.pipeline().addLast(new MinecraftFrameDecoder(), new HandshakeMinecraftPacketDecoder());
                            ch.pipeline().addLast("length", new LengthEncoder());
                            ch.pipeline().addLast("MPE", new MinecraftPacketEncoder());
                            ch.pipeline().addLast(new Main());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            LOGGER.debug("Startuję nasłuch...");
            ChannelFuture f = b.bind(port).sync();
            LOGGER.info("Gotowy na połączenia!");

            channel = f.channel();
            channel.closeFuture().sync();
            LOGGER.info("Nasłuch zakończony!");
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}
