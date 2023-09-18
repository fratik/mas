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

package pl.fratik.mcs.chat;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;

import java.io.StringWriter;
import java.util.Locale;

public class TextChatComponent implements ChatComponent {
    private static final char CONTROL = '§';
    private static final int BOLD_OFFSET = 4;
    private static final int ITALIC_OFFSET = 3;
    private static final int UNDER_OFFSET = 2;
    private static final int STRIKE_OFFSET = 1;
    private static final int OBFUSCATED_OFFSET = 0;
    @Getter @Setter protected String text;
    @Getter @Setter protected int color = -1; //-1 / 0-15, dla prostoty nie dla hexu
    protected byte style; // BIUSO

    public TextChatComponent(String text) {
        this.text = text;
    }

    public void setBold(boolean bold) {
        if (bold) style |= 1 << BOLD_OFFSET;
        else style &= ~(1 << BOLD_OFFSET);
    }

    public void setItalic(boolean italic) {
        if (italic) style |= 1 << ITALIC_OFFSET;
        else style &= ~(1 << ITALIC_OFFSET);
    }

    public void setUnderline(boolean under) {
        if (under) style |= 1 << UNDER_OFFSET;
        else style &= ~(1 << UNDER_OFFSET);
    }

    public void setStrikethrough(boolean strike) {
        if (strike) style |= 1 << STRIKE_OFFSET;
        else style &= ~(1 << STRIKE_OFFSET);
    }

    public void setObfuscated(boolean obfuscated) {
        if (obfuscated) style |= 1 << OBFUSCATED_OFFSET;
        else style &= ~(1 << OBFUSCATED_OFFSET);
    }

    public boolean isBold() {
        return ((style >> BOLD_OFFSET) & 1) == 1;
    }

    public boolean isItalic() {
        return ((style >> ITALIC_OFFSET) & 1) == 1;
    }

    public boolean isUnderlined() {
        return ((style >> UNDER_OFFSET) & 1) == 1;
    }

    public boolean isStrikethrough() {
        return ((style >> STRIKE_OFFSET) & 1) == 1;
    }

    public boolean isObfuscated() {
        return ((style >> OBFUSCATED_OFFSET) & 1) == 1;
    }

    @Override
    public JsonObject serialize(int version) {
        JsonObject obj = new JsonObject();
        if (text != null) obj.addProperty("text", text);
        if (isBold()) obj.addProperty("bold", true);
        if (isItalic()) obj.addProperty("italic", true);
        if (isUnderlined()) obj.addProperty("underlined", true);
        if (isStrikethrough()) obj.addProperty("strikethrough", true);
        if (isObfuscated()) obj.addProperty("obfuscated", true);
        if (color != -1) obj.addProperty("color", Integer.toString(Math.abs(color), 10));
        return obj;
    }

    @Override
    public String serializeLegacy() {
        StringBuilder sb = new StringBuilder();
        if (color != -1) sb.append(CONTROL).append(Integer.toString(color, 16));
        if (isBold()) sb.append(CONTROL).append('l');
        if (isItalic()) sb.append(CONTROL).append('o');
        if (isUnderlined()) sb.append(CONTROL).append('n');
        if (isStrikethrough()) sb.append(CONTROL).append('m');
        if (isObfuscated()) sb.append(CONTROL).append('k');
        if (text != null) sb.append(text);
        return sb.toString();
    }
}
