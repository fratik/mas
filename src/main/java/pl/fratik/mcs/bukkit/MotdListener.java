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

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerListPingEvent;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public abstract class MotdListener implements Listener {
    private final BaseMain main;
    private final Map<InetAddress, ScheduledFuture<?>> recentlyPinged = new HashMap<>();

    public MotdListener(BaseMain main) {
        this.main = main;
    }

    protected void handlePing(ServerListPingEvent e) {
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            // jeśli pingnie osoba kiedy juz ktos jest na serwerze (i notice o wygaszeniu zniknie), nie wysylaj informacji po wejsciu
            ScheduledFuture<?> f;
            synchronized (recentlyPinged) {
                f = recentlyPinged.remove(e.getAddress());
            }
            if (f != null) f.cancel(false);
            return;
        }
        synchronized (recentlyPinged) {
            recentlyPinged.compute(e.getAddress(), (k, v) -> {
                if (v != null) v.cancel(false);
                return main.getExecutor().schedule(() -> {
                    synchronized (recentlyPinged) {
                        recentlyPinged.remove(e.getAddress());
                    }
                }, 30, TimeUnit.SECONDS);
            });
        }
        long delay = main.getTask().getDelay(TimeUnit.SECONDS);
        int minutes = (int) Math.floor(delay / 60d);
        int seconds = (int) (delay - (minutes * 60L));
        appendToMotd(e, "\nSerwer zostanie wyłączony za " + String.format("%d:%02d", minutes, seconds) + ".");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        ScheduledFuture<?> f;
        synchronized (recentlyPinged) {
            f = recentlyPinged.remove(e.getPlayer().getAddress().getAddress());
        }
        if (f != null) {
            sendServerShutdownStopped(e.getPlayer());
            f.cancel(false);
        }
        sendLeaveNotify(e.getPlayer());
    }

    protected abstract void appendToMotd(ServerListPingEvent e, String text);
    protected abstract void sendServerShutdownStopped(Player player);
    protected abstract void sendLeaveNotify(Player player);
}
