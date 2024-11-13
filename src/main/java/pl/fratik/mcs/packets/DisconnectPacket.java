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
import lombok.Getter;
import pl.fratik.mcs.ProtocolUtil;
import pl.fratik.mcs.chat.ChatComponent;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Data
public class DisconnectPacket implements ResponsePacket {
    private final ChatComponent chatComponent;
    private final int version;
    @Getter(lazy = true) private final String serialized = serializeChatComponent();

    private static String hex(final int codepoint) {
        return Integer.toHexString(codepoint).toUpperCase(Locale.ENGLISH);
    }

    // z org.apache.commons.lang.StringEscapeUtils
    private String convertToEscaped(String str) {
        StringWriter out = new StringWriter(str.length() * 2);
        int sz;
        sz = str.length();
        for (int i = 0; i < sz; i++) {
            char ch = str.charAt(i);
            // handle unicode
            if (ch > 0xfff) {
                out.write("\\u" + hex(ch));
            } else if (ch > 0xff) {
                out.write("\\u0" + hex(ch));
            } else if (ch > 0x7f) {
                out.write("\\u00" + hex(ch));
            } else if (ch < 32) {
                switch (ch) {
                    case '\b' :
                        out.write('\\');
                        out.write('b');
                        break;
                    case '\n' :
                        out.write('\\');
                        out.write('n');
                        break;
                    case '\t' :
                        out.write('\\');
                        out.write('t');
                        break;
                    case '\f' :
                        out.write('\\');
                        out.write('f');
                        break;
                    case '\r' :
                        out.write('\\');
                        out.write('r');
                        break;
                    default :
                        if (ch > 0xf) {
                            out.write("\\u00" + hex(ch));
                        } else {
                            out.write("\\u000" + hex(ch));
                        }
                        break;
                }
            } else out.write(ch);
        }
        return out.toString();
    }

    private String serializeChatComponent() {
        return convertToEscaped(chatComponent.serialize(version).toString());
    }

    @Override
    public int calculateLength(int protVer) {
        return 1 + getSerialized().getBytes(StandardCharsets.UTF_8).length;
    }

    @Override
    public void encode(ByteBuf buf, int protVer) {
        ProtocolUtil.writeVarInt(buf, 0x00);
        ProtocolUtil.writeString(buf, getSerialized());
    }
}
