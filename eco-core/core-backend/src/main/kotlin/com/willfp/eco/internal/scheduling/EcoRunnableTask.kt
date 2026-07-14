package com.willfp.eco.internal.scheduling

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.scheduling.RunnableTask
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.TimeUnit

abstract class EcoRunnableTask(protected val plugin: EcoPlugin) : BukkitRunnable(), RunnableTask {
    @Volatile
    private var scheduledTask: ScheduledTask? = null

    @Synchronized
    override fun runTask(): BukkitTask {
        return track(Bukkit.getGlobalRegionScheduler().run(plugin) { this.run() }, sync = true)
    }

    @Synchronized
    override fun runTaskAsynchronously(): BukkitTask {
        return track(Bukkit.getAsyncScheduler().runNow(plugin) { this.run() }, sync = false)
    }

    @Synchronized
    override fun runTaskLater(delay: Long): BukkitTask {
        return track(
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, { this.run() }, delay.coerceAtLeast(1)),
            sync = true
        )
    }

    @Synchronized
    override fun runTaskLaterAsynchronously(delay: Long): BukkitTask {
        return track(
            Bukkit.getAsyncScheduler()
                .runDelayed(plugin, { this.run() }, delay.coerceAtLeast(1) * 50, TimeUnit.MILLISECONDS),
            sync = false
        )
    }

    @Synchronized
    override fun runTaskTimer(delay: Long, period: Long): BukkitTask {
        return track(
            Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(plugin, { this.run() }, delay.coerceAtLeast(1), period.coerceAtLeast(1)),
            sync = true
        )
    }

    @Synchronized
    override fun runTaskTimerAsynchronously(delay: Long, period: Long): BukkitTask {
        return track(
            Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin,
                { this.run() },
                delay.coerceAtLeast(1) * 50,
                period.coerceAtLeast(1) * 50,
                TimeUnit.MILLISECONDS
            ),
            sync = false
        )
    }

    @Synchronized
    override fun cancel() {
        scheduledTask?.cancel()
    }

    private fun track(task: ScheduledTask, sync: Boolean): BukkitTask {
        scheduledTask = task
        return FoliaBukkitTask(task, sync)
    }
}

/**
 * Adapts a Folia [ScheduledTask] to the legacy [BukkitTask] interface so that the
 * public [RunnableTask] API keeps returning a [BukkitTask] on regionised threading.
 */
private class FoliaBukkitTask(
    private val task: ScheduledTask,
    private val sync: Boolean
) : BukkitTask {
    override fun getTaskId(): Int = System.identityHashCode(task)

    override fun getOwner(): Plugin = task.owningPlugin

    override fun isSync(): Boolean = sync

    override fun isCancelled(): Boolean = task.isCancelled

    override fun cancel() {
        task.cancel()
    }
}
