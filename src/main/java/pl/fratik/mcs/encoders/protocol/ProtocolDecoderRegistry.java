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

import it.unimi.dsi.fastutil.ints.IntIntPair;
import lombok.SneakyThrows;
import org.jetbrains.annotations.Nullable;
import pl.fratik.mcs.Main;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ProtocolDecoderRegistry {
    private static final Map<IntIntPair, Class<? extends ProtocolDecoder>> registry;

    static {
        Map<IntIntPair, Class<? extends ProtocolDecoder>> reg = new HashMap<>();
        reg.put(IntIntPair.of(340, 758), ProtocolDecoderNoKey.class);
        reg.put(IntIntPair.of(759, 760), ProtocolDecoderSigData.class);
        reg.put(IntIntPair.of(761, 763), ProtocolDecoderUUID.class);
        registry = Collections.unmodifiableMap(reg);
    }

    @SneakyThrows
    public static ProtocolDecoder createRegistry(Main main) {
        Class<? extends ProtocolDecoder> decoder = findDecoder(main.getProtVer());
        if (decoder == null) return null;
        return decoder.getDeclaredConstructor(Main.class).newInstance(main);
    }

    @Nullable
    private static Class<? extends ProtocolDecoder> findDecoder(int protVer) {
        for (Map.Entry<IntIntPair, Class<? extends ProtocolDecoder>> e : registry.entrySet()) {
            IntIntPair k = e.getKey();
            int min = k.leftInt();
            int max = k.rightInt();
            if (protVer >= min && protVer <= max)
                return e.getValue();
        }
        return null;
    }

    public static boolean hasDecoderForVersion(int version) {
        return findDecoder(version) != null;
    }
}
