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

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.*;

public class ProtocolUtil {
    private ProtocolUtil() {}

    public static Integer getVarInt(ByteBuf buf) {
        return readVarInt0(index -> {
            if (buf.readableBytes() < index) return null;
            return buf.getByte(index);
        });
    }

    public static Integer readVarInt(ByteBuf buf) {
        int index = buf.readerIndex();
        Integer res = readVarInt0(i -> buf.readByte());
        if (res == null) buf.readerIndex(index);
        return res;
    }

    public static Integer readVarInt0(IntFunction<Byte> readByte) {
        int i = 0;
        int value = 0;
        int position = 0;
        Byte currentByte;

        while (true) {
            currentByte = readByte.apply(i);
            if (currentByte == null) return null;
            value |= (currentByte & 0x7F) << position;

            if ((currentByte & 0x80) == 0) break;

            position += 7;

            if (position >= 32) throw new RuntimeException("VarInt is too big");
        }

        return value;
    }
    public static Long getVarLong(ByteBuf buf) {
        return readVarLong0(index -> {
            if (buf.readableBytes() < index) return null;
            return buf.getByte(index);
        });
    }

    public static Long readVarLong(ByteBuf buf) {
        int index = buf.readerIndex();
        Long res = readVarLong0(i -> buf.readByte());
        if (res == null) buf.readerIndex(index);
        return res;
    }

    public static Long readVarLong0(IntFunction<Byte> readByte) {
        int i = 0;
        long value = 0;
        int position = 0;
        Byte currentByte;

        while (true) {
            currentByte = readByte.apply(i);
            if (currentByte == null) return null;
            value |= (long) (currentByte & 0x7F) << position;

            if ((currentByte & 0x80) == 0) break;

            position += 7;

            if (position >= 64) throw new RuntimeException("VarLong is too big");
        }

        return value;
    }
    public static void writeVarInt(ByteBuf buf, int value) {
        while (true) {
            if ((value & ~0x7F) == 0) {
                buf.writeByte(value);
                return;
            }

            buf.writeByte((value & 0x7F) | 0x80);

            value >>>= 7;
        }
    }
    public static void writeVarLong(ByteBuf buf, long value) {
        while (true) {
            if ((value & ~((long) 0x7F)) == 0) {
                buf.writeByte((int) value);
                return;
            }

            buf.writeByte((int) ((value & 0x7F) | 0x80));

            value >>>= 7;
        }
    }

    public static String readString(ByteBuf buf) {
        int index = buf.readerIndex();
        Integer length = readVarInt(buf);
        if (length == null) return null;
        if (buf.readableBytes() < length) {
            buf.readerIndex(index);
            return null;
        }
        return String.valueOf(buf.readCharSequence(length, StandardCharsets.UTF_8));
    }

    public static void writeString(ByteBuf buf, String s) {
        writeVarInt(buf, s.length());
        buf.writeCharSequence(s, StandardCharsets.UTF_8);
    }

    public static UUID readUUID(ByteBuf buf) {
        if (buf.readableBytes() < 4) return null;
        long msb = buf.readLong();
        long lsb = buf.readLong();
        return new UUID(msb, lsb);
    }

    public static void writeUUID(ByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }
}
