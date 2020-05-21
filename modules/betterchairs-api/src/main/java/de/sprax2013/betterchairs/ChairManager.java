package de.sprax2013.betterchairs;

import de.sprax2013.betterchairs.events.PlayerEnterChairEvent;
import de.sprax2013.betterchairs.events.PlayerLeaveChairEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class ChairManager {
    protected static ChairManager instance;

    private final JavaPlugin plugin;
    protected final ChairNMS chairNMS;
    protected final List<Chair> chairs = new ArrayList<>();
    protected final HashMap<Player, Chair> chairsAwaitTeleport = new HashMap<>();

    protected ChairManager(@NotNull JavaPlugin plugin, @NotNull ChairNMS chairNMS) {
        this.plugin = plugin;
        this.chairNMS = Objects.requireNonNull(chairNMS);

        instance = this;
    }

    /**
     * Check if a chair can be spawned and call an Event.
     * If the event doesn't get cancelled,
     * the player should now be able to sit.
     *
     * @param player The player that should sit
     * @param block  The block the player should sit on
     *
     * @return true if player is now sitting on a chair, false otherwise
     *
     * @throws IllegalArgumentException When {@code block} is not a valid chair block
     */
    public boolean create(Player player, Block block) {
        if (!chairNMS.isStair(block) && !chairNMS.isSlab(block))
            throw new IllegalArgumentException("The provided block is neither a stair nor a slab");

        if (isOccupied(block)) return false;

        // Slabs that are placed in the upper half of an block need the player to sit 0.5 blocks higher
        double yOffset = chairNMS.isSlab(block) && chairNMS.isSlabTop(block) ? 0.5 : 0;

        ArmorStand armorStand = instance.chairNMS.spawnChairArmorStand(
                block.getLocation().add(0.5, -1.2 + yOffset, 0.5));

        Chair chair = new Chair(block, armorStand, player);

        PlayerEnterChairEvent event = new PlayerEnterChairEvent(player, chair);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            instance.chairNMS.killChairArmorStand(armorStand);
            return false;
        }

        chairs.add(chair);
        armorStand.setPassenger(player);
        return true;
    }

    /**
     * @param chair    The {@link Chair} that should be destroyed
     * @param teleport true, when <b>not</b> called without an {@link org.bukkit.event.player.PlayerTeleportEvent}
     *                 being fired
     */
    public void destroy(Chair chair, boolean teleport) {
        Bukkit.getPluginManager().callEvent(new PlayerLeaveChairEvent(chair.player, chair));

        chairNMS.killChairArmorStand(chair.armorStand);
        chairs.remove(chair);

        if (!teleport) {
            chairsAwaitTeleport.put(chair.player, chair);
        } else {
            //TODO: Extract teleport into own method as it is used in the onTeleport listener
            //TODO: Check if block on location is solid, increment y if it is
            //TODO: Check in config if 'return to old location' is enabled and teleport player on-top of chair of not
            Location loc = chair.player.getLocation();  // Keep Yaw/Pitch and only clone Location once for it

            // Set the coordinates the player came from
            loc.setX(chair.playerOriginalLoc.getX());
            loc.setY(chair.playerOriginalLoc.getY());
            loc.setZ(chair.playerOriginalLoc.getZ());

            chair.player.teleport(loc);
        }
    }

    /**
     * @param b The block to check
     *
     * @return true if a player is sitting on it, false otherwise
     */
    public boolean isOccupied(@NotNull Block b) {
        for (Chair c : chairs) {
            if (b.equals(c.block)) {
                return true;
            }
        }

        return false;
    }

    @Nullable
    public Chair getChair(@NotNull Player p) {
        for (Chair c : chairs) {
            if (p == c.player) {
                return c;
            }
        }

        return null;
    }

    @Nullable
    public Chair getChair(@NotNull ArmorStand armorStand) {
        for (Chair c : chairs) {
            if (armorStand == c.armorStand) {
                return c;
            }
        }

        return null;
    }

    public boolean isChair(@NotNull ArmorStand armorStand) {
        for (Chair c : chairs) {
            if (armorStand.equals(c.armorStand)) {
                return true;
            }
        }

        return false;
    }

    // TODO: Methods to create and destroy chairs (and automatically sit players on them)

    /**
     * May be null if BetterChairs is not enabled
     *
     * @return The {@link ChairManager} instance created by BetterChairs, or null
     */
    @Nullable
    public static ChairManager getInstance() {
        return instance;
    }
}