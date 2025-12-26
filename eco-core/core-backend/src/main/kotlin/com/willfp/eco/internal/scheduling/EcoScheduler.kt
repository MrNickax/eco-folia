package com.willfp.eco.internal.scheduling

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.scheduling.Scheduler
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import java.util.concurrent.TimeUnit

class EcoScheduler(private val plugin: EcoPlugin) : Scheduler {
    override fun runLater(
        runnable: Runnable,
        ticksLater: Long
    ): ScheduledTask {
        return Bukkit.getGlobalRegionScheduler().runDelayed(
            plugin,
            { runnable.run() },
            ticksLater
        )
    }

    override fun runTimer(
        runnable: Runnable,
        delay: Long,
        repeat: Long
    ): ScheduledTask {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(
            plugin,
            { runnable.run() },
            delay,
            repeat
        )
    }

    override fun runAsyncTimer(
        runnable: Runnable,
        delay: Long,
        repeat: Long
    ): ScheduledTask {
        return Bukkit.getAsyncScheduler().runAtFixedRate(
            plugin,
            { runnable.run() },
            delay * 50,
            repeat * 50,
            TimeUnit.MILLISECONDS
        )
    }

    override fun run(runnable: Runnable): ScheduledTask {
        return Bukkit.getGlobalRegionScheduler().run(plugin) { runnable.run() }
    }

    override fun runAsync(runnable: Runnable): ScheduledTask {
        return Bukkit.getAsyncScheduler().runNow(plugin) { runnable.run() }
    }

    override fun cancelAll() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(this.plugin)
        Bukkit.getAsyncScheduler().cancelTasks(this.plugin)
    }
}