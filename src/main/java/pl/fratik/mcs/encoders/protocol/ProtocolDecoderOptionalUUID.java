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

package pl.fratik.mcs.encoders.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import pl.fratik.mcs.Main;
import pl.fratik.mcs.ProtocolUtil;
import pl.fratik.mcs.packets.EncryptionResponsePacket;
import pl.fratik.mcs.packets.LoginStartPacket;

import java.util.List;

public class ProtocolDecoderOptionalUUID extends ProtocolDecoder {
    public ProtocolDecoderOptionalUUID(Main main) {
        super(main);
    }

    @Override
    public void decodeLogin(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        int id = ProtocolUtil.readVarInt(in);
        switch (id) {
            case 0x00 -> {
                out.add(new LoginStartPacket(ProtocolUtil.readString(in), in.readBoolean() ? ProtocolUtil.readUUID(in) : null, null));
            }
            case 0x01 -> {
                int sslen = ProtocolUtil.readVarInt(in);
                byte[] ss = new byte[sslen];
                in.readBytes(ss);
                int vtlen = ProtocolUtil.readVarInt(in);
                byte[] vt = new byte[vtlen];
                in.readBytes(vt);
                out.add(new EncryptionResponsePacket(ss, vt, null));
            }
            default -> throw new IllegalStateException();
        }
    }
}
