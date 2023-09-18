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

package pl.fratik.mcs.forge;

import com.mojang.logging.LogUtils;
import lombok.Getter;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static net.minecraftforge.eventbus.api.EventPriority.LOWEST;

@Mod(ForgeMain.MODID)
public class ForgeMain {
    public static final String MODID = "mcs";
    private static final Logger LOGGER = LogUtils.getLogger();
    @Getter private ScheduledExecutorService executor;
    @Getter private ScheduledFuture<?> task;

    public ForgeMain() {
        executor = Executors.newSingleThreadScheduledExecutor();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent e) {
        scheduleTask();
    }

    private void scheduleTask() {
        if (task != null && !task.isCancelled()) {
            if (!task.cancel(false)) return;
        }
        LOGGER.info("Nikogo nie ma - startuje timer");
        task = executor.schedule(this::shutdown, 10, TimeUnit.MINUTES);
    }

    private void shutdown() {
        ServerLifecycleHooks.getCurrentServer().stopServer();
    }

    @SubscribeEvent(priority = LOWEST)
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent e) {
        if (ServerLifecycleHooks.getCurrentServer().getPlayerCount() - 1 < 1) scheduleTask();
    }

    @SubscribeEvent(priority = LOWEST)
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent e) {
        if (task != null && !task.isCancelled()) {
            if (!task.cancel(false)) return;
            LOGGER.info("Zatrzymano timer");
        }
    }

}
