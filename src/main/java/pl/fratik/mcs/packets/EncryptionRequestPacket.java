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

package pl.fratik.mcs.packets;

import io.netty.buffer.ByteBuf;
import lombok.Data;
import pl.fratik.mcs.ProtocolUtil;

@Data
public class EncryptionRequestPacket implements ResponsePacket {
    private final byte[] publicKey;
    private final byte[] verifyToken;

    @Override
    public int calculateLength(int protVer) {
        return 0;
    }

    @Override
    public void encode(ByteBuf buf, int protVer) {
        ProtocolUtil.writeVarInt(buf, 0x01);
        ProtocolUtil.writeString(buf, "");
        ProtocolUtil.writeVarInt(buf, publicKey.length);
        buf.writeBytes(publicKey);
        ProtocolUtil.writeVarInt(buf, verifyToken.length);
        buf.writeBytes(verifyToken);
        if (protVer >= 766) buf.writeBoolean(true);
    }
}
