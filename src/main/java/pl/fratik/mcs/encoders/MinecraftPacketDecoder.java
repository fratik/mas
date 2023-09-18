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

package pl.fratik.mcs.encoders;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import pl.fratik.mcs.Main;
import pl.fratik.mcs.ProtocolUtil;
import pl.fratik.mcs.encoders.protocol.ProtocolDecoder;
import pl.fratik.mcs.encoders.protocol.ProtocolDecoderRegistry;
import pl.fratik.mcs.packets.PingRequestPacket;
import pl.fratik.mcs.packets.StatusRequestPacket;

import java.util.List;

public class MinecraftPacketDecoder extends ByteToMessageDecoder {
    private final Main main;
    private final ProtocolDecoder decoder;

    public MinecraftPacketDecoder(Main main) {
        this.main = main;
        this.decoder = ProtocolDecoderRegistry.createRegistry(main);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        switch (main.getState()) {
            case STATUS -> decodeStatus(ctx, in, out);
            default -> {
                if (decoder == null) in.skipBytes(in.readableBytes());
                else decoder.decodeLogin(ctx, in, out);
            }
        }
    }

    private void decodeStatus(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        int id = ProtocolUtil.readVarInt(in);
        switch (id) {
            case 0x00 -> out.add(new StatusRequestPacket());
            case 0x01 -> out.add(new PingRequestPacket(in.readLong()));
            default -> throw new IllegalStateException();
        }
    }
}
