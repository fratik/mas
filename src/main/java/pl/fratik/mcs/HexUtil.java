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

public class HexUtil {
    private HexUtil() {}

    public static String byteToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static byte[] hexToByte(CharSequence hexToByte) {
        if (hexToByte.length() % 2 != 0) throw new IllegalArgumentException("invalid string");
        byte[] data = new byte[hexToByte.length() / 2];
        for (int i = 0; i < hexToByte.length(); i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexToByte.charAt(i), 16) << 4) |
                    Character.digit(hexToByte.charAt(i + 1), 16));
        }
        return data;
    }
}
