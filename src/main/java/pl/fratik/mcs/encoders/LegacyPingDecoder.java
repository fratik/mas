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
import pl.fratik.mcs.packets.LegacyHandshakePacket;
import pl.fratik.mcs.packets.LegacyPingPacket;

import java.util.List;

public class LegacyPingDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (!in.isReadable()) {
            return;
        }

        if (!ctx.channel().isActive()) {
            in.clear();
            return;
        }

        int originalReaderIndex = in.readerIndex();
        short first = in.readUnsignedByte();
        if (first == 0xfe) {
            // possibly a ping
            if (!in.isReadable()) {
                out.add(new LegacyPingPacket());
                return;
            }

            short next = in.readUnsignedByte();
            if (next == 1 && !in.isReadable()) {
                out.add(new LegacyPingPacket());
                return;
            }

            in.skipBytes(in.readableBytes());
            out.add(new LegacyPingPacket());
            ctx.pipeline().remove("length");
        } else if (first == 0x02 && in.isReadable()) {
            in.skipBytes(in.readableBytes());
            out.add(new LegacyHandshakePacket());
            ctx.pipeline().remove("length");
        } else {
            in.readerIndex(originalReaderIndex);
            ctx.pipeline().remove(this);
        }

    }
}
