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

package pl.fratik.mcs.bukkit;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.server.ServerListPingEvent;

public class PaperMotdListener extends MotdListener {
    public PaperMotdListener(PaperMain main) {
        super(main);
    }

    @EventHandler
    public void onPing(PaperServerListPingEvent e) {
        handlePing(e);
    }

    @Override
    protected void appendToMotd(ServerListPingEvent e, String text) {
        e.motd(e.motd().append(Component.text(text).color(NamedTextColor.RED)));
    }

    @Override
    protected void sendServerShutdownStopped(Player player) {
        player.sendMessage(Component.text("Przerwano wyłączenie serwera.").color(NamedTextColor.GREEN));
    }

    @Override
    protected void sendLeaveNotify(Player player) {
        player.sendMessage(Component.text("Wyłączenie serwera nastąpi po 10 minutach od wyjścia ostatniego gracza."));
    }
}
