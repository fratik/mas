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

import lombok.Getter;
import lombok.SneakyThrows;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public abstract class BaseMain extends JavaPlugin implements Listener {
    @Getter protected ScheduledExecutorService executor;
    @Getter protected ScheduledFuture<?> task;
    @Getter protected BukkitTask bukkitTask;

    @Override
    public void onEnable() {
        executor = Executors.newSingleThreadScheduledExecutor();
        scheduleTask();
        getServer().getPluginManager().registerEvents(this, this);
    }

    protected void scheduleTask() {
        getLogger().info("Nikogo nie ma - startuje timer");
        if (task != null && !task.isCancelled()) task.cancel(false);
        if (bukkitTask != null && !bukkitTask.isCancelled()) bukkitTask.cancel();
        task = executor.schedule(this::shutdown, 10, TimeUnit.MINUTES);
    }

    protected void shutdown() {
        bukkitTask = Bukkit.getScheduler().runTask(this, Bukkit::shutdown);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent e) {
        stopTimer();
    }

    @SneakyThrows
    protected void stopTimer() {
        if (!task.isCancelled()) {
            if (!task.cancel(false)) {
                if (!task.isDone()) task.get();
                if (bukkitTask != null && !bukkitTask.isCancelled()) bukkitTask.cancel();
            }
            getLogger().info("Zatrzymano timer");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLeave(PlayerQuitEvent e) {
        if (getServer().getOnlinePlayers().size() - 1 < 1) scheduleTask();
    }

    @Override
    public void onDisable() {
        executor.shutdown();
    }
}
