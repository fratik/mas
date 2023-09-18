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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Arrays;

public class TranslateChatComponent extends TextChatComponent {
    private final String key;
    private final ChatComponent[] objs;
    private final String fallback;
    private final int minimumVersion;

    public TranslateChatComponent(String key, ChatComponent[] objs, String fallback, int minimumVersion) {
        super(null);
        this.key = key;
        this.objs = objs;
        this.fallback = fallback;
        this.minimumVersion = minimumVersion;
    }

    @Override
    public JsonObject serialize(int version) {
        if (version < minimumVersion) text = fallback;
        JsonObject serialized = super.serialize(version);
        if (version >= minimumVersion) {
            serialized.addProperty("translate", key);
            if (objs != null && objs.length != 0) {
                JsonArray arr = new JsonArray();
                for (ChatComponent obj : objs) {
                    arr.add(obj.serialize(version));
                }
                serialized.add("with", arr);
            }
        }
        return serialized;
    }

    @Override
    public String serializeLegacy() {
        if (objs != null && objs.length != 0) text = String.format(fallback, (Object[]) Arrays.stream(objs).map(ChatComponent::serializeLegacy).toArray(String[]::new));
        else text = fallback;
        return super.serializeLegacy();
    }
}
