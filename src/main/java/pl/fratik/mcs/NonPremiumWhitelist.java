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

import pl.fratik.mcs.players.Player;
import pl.fratik.mcs.players.WhitelistPlayer;

import java.util.ArrayList;
import java.util.List;

public class NonPremiumWhitelist implements Whitelist {
    private final List<String> storage = new ArrayList<>();

    @Override
    public boolean contains(Player identifier) {
        return storage.contains(identifier.getNick());
    }

    @Override
    public void add(WhitelistPlayer identifier) {
        storage.add(identifier.getNick());
    }

    @Override
    public boolean remove(WhitelistPlayer identifier) {
        return storage.remove(identifier.getNick());
    }

    @Override
    public void clear() {
        storage.clear();
    }

    @Override
    public int size() {
        return storage.size();
    }
}
