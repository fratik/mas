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
import pl.fratik.mcs.ProtocolUtil;
import pl.fratik.mcs.packets.HandshakePacket;

import java.util.List;

public class HandshakeMinecraftPacketDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        int id = ProtocolUtil.readVarInt(in);
        switch (id) {
            case 0x00 -> {
                int protVer = ProtocolUtil.readVarInt(in);
                String ip = ProtocolUtil.readString(in);
                int port = in.readUnsignedShort();
                int state = ProtocolUtil.readVarInt(in);
                out.add(new HandshakePacket(protVer, ip, port, state));
            }
            default -> throw new IllegalStateException();
        }
    }
}
