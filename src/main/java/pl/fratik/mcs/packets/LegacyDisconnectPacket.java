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

import java.nio.charset.StandardCharsets;

@Data
public class LegacyDisconnectPacket implements ResponsePacket {
    private final String text;

    @Override
    public int calculateLength() {
        return 3 + text.getBytes(StandardCharsets.UTF_16BE).length;
    }

    @Override
    public void encode(ByteBuf buf) {
        buf.writeByte(0xFF);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_16BE);
        buf.writeShort(text.length());
        buf.writeBytes(bytes);
    }
}
