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

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.server.ServerListPingEvent;

public class BukkitMotdListener extends MotdListener {
    public BukkitMotdListener(BukkitMain main) {
        super(main);
    }

    @EventHandler
    public void onPing(ServerListPingEvent e) {
        handlePing(e);
    }

    @Override
    protected void appendToMotd(ServerListPingEvent e, String text) {
        e.setMotd(e.getMotd() + ChatColor.RED + text);
    }

    @Override
    protected void sendServerShutdownStopped(Player player) {
        player.sendMessage(ChatColor.GREEN + "Przerwano wyłączenie serwera.");
    }

    @Override
    protected void sendLeaveNotify(Player player) {
        player.sendMessage("Wyłączenie serwera nastąpi po 10 minutach od wyjścia ostatniego gracza.");
    }
}
