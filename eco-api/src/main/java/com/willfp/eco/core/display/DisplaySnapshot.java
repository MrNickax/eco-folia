package com.willfp.eco.core.display;

import com.willfp.eco.core.Eco;
import com.willfp.eco.core.integrations.guidetection.GUIDetectionManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The player state that {@link Display} needs, but cannot read for itself.
 * <p>
 * Display runs on the netty thread (see {@link DisplayModule}), which does not own the
 * player, so reading the open container from there is both a thread check violation and a
 * race against the region thread that mutates it. Display cannot hop threads to read it
 * either, as it has to return the displayed item synchronously to the packet write.
 * <p>
 * So the thread that owns the player takes the readings and publishes them here, and the
 * netty thread reads the last published values instead of touching the container.
 */
final class DisplaySnapshot {
    /**
     * The snapshot for each player.
     */
    private static final Map<UUID, DisplaySnapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    /**
     * If a refresh is already queued.
     * <p>
     * A single window packet displays every item in the window, so requests arrive in
     * bursts; this coalesces each burst down to one reading.
     */
    private final AtomicBoolean refreshQueued = new AtomicBoolean();

    /**
     * The contents of the player's open top inventory, as of the last refresh.
     */
    @Nullable
    private volatile List<ItemStack> topInventory = null;

    /**
     * If the player had a GUI open, as of the last refresh.
     */
    private volatile boolean inGui = false;

    private DisplaySnapshot() {

    }

    /**
     * Get if an item was in the player's open top inventory.
     *
     * @param player The player.
     * @param item   The item.
     * @return If the inventory contained the item, as of the last refresh.
     */
    static boolean isInInventory(@NotNull final Player player,
                                 @NotNull final ItemStack item) {
        DisplaySnapshot snapshot = SNAPSHOTS.get(player.getUniqueId());

        if (snapshot == null) {
            return false;
        }

        List<ItemStack> contents = snapshot.topInventory;

        if (contents == null) {
            return false;
        }

        // Mirrors Inventory#contains, which matches on both type and amount.
        for (ItemStack content : contents) {
            if (content.equals(item)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get if the player had a GUI open.
     *
     * @param player The player.
     * @return If the player had a GUI open, as of the last refresh.
     */
    static boolean hasGUIOpen(@NotNull final Player player) {
        DisplaySnapshot snapshot = SNAPSHOTS.get(player.getUniqueId());

        return snapshot != null && snapshot.inGui;
    }

    /**
     * Queue a refresh on the thread that owns the player.
     *
     * @param player The player.
     */
    static void requestRefresh(@NotNull final Player player) {
        DisplaySnapshot snapshot = SNAPSHOTS.computeIfAbsent(
                player.getUniqueId(),
                uuid -> new DisplaySnapshot()
        );

        if (!snapshot.refreshQueued.compareAndSet(false, true)) {
            return;
        }

        ScheduledTask task = player.getScheduler().run(
                Eco.get().getEcoPlugin(),
                ignored -> snapshot.refresh(player),
                null
        );

        // Returns null, rather than throwing, if the player has retired. The refresh will
        // never run to clear the flag, and nothing will read the snapshot again, so drop it.
        if (task == null) {
            snapshot.refreshQueued.set(false);
            SNAPSHOTS.remove(player.getUniqueId(), snapshot);
        }
    }

    /**
     * Take the readings. Only ever runs on the thread that owns the player.
     *
     * @param player The player.
     */
    private void refresh(@NotNull final Player player) {
        List<ItemStack> contents = new ArrayList<>();

        for (ItemStack item : player.getOpenInventory().getTopInventory().getContents()) {
            if (item != null) {
                // Cloned, as the netty thread must not read items backed by the live container.
                contents.add(item.clone());
            }
        }

        this.topInventory = contents;
        this.inGui = GUIDetectionManager.hasGUIOpen(player);

        this.refreshQueued.set(false);
    }
}
