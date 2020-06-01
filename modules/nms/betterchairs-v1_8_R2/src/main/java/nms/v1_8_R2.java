package nms;

import de.sprax2013.betterchairs.ChairNMS;
import de.sprax2013.betterchairs.ChairUtils;
import net.minecraft.server.v1_8_R2.EntityArmorStand;
import net.minecraft.server.v1_8_R2.EntityHuman;
import net.minecraft.server.v1_8_R2.World;
import net.minecraft.server.v1_8_R2.WorldServer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_8_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R2.entity.CraftArmorStand;
import org.bukkit.craftbukkit.v1_8_R2.entity.CraftHumanEntity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.material.Stairs;
import org.bukkit.material.Step;
import org.bukkit.material.WoodenStep;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@SuppressWarnings("unused")
public class v1_8_R2 extends ChairNMS {
    @Override
    public @NotNull ArmorStand spawnChairArmorStand(Location loc) {
        WorldServer nmsWorld = ((CraftWorld) Objects.requireNonNull(loc.getWorld())).getHandle();
        CustomArmorStand nmsArmorStand = new CustomArmorStand(
                nmsWorld, loc.getX(), loc.getY(), loc.getZ(),
                -1);  //TODO: Read regeneration effect from config and check permissions
        ArmorStand armorStand = (ArmorStand) nmsArmorStand.getBukkitEntity();

        try {
            setValue(nmsArmorStand, "invulnerable", true);     // Invulnerable
            setValue(nmsArmorStand, "bi", 2031616);            // DisabledSlots
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            // fail gracefully
            System.err.println("BetterChairs could not apply protections to a Chair at " +
                    armorStand.getLocation().getBlock().getLocation() +
                    " (" + ex.getClass().getName() + ": " + ex.getMessage() + ")");
        }

        ChairUtils.applyBasicChairModifications(armorStand);

        nmsWorld.addEntity(nmsArmorStand, CreatureSpawnEvent.SpawnReason.CUSTOM);

        return armorStand;
    }

    @Override
    public void killChairArmorStand(ArmorStand armorStand) {
        EntityArmorStand nmsArmorStand = ((CraftArmorStand) armorStand).getHandle();

        if (!(nmsArmorStand instanceof CustomArmorStand))
            throw new IllegalArgumentException("The provided ArmorStand is not an instance of " +
                    CustomArmorStand.class.getName());

        ((CustomArmorStand) nmsArmorStand).remove = true;
        armorStand.remove();
    }

    @Override
    protected boolean isStair(Block block) {
        return block.getState().getData() instanceof Stairs;
    }

    @Override
    protected boolean isStairUpsideDown(Block block) {
        return ((Stairs) block.getState().getData()).isInverted();
    }

    @Override
    protected boolean isSlab(Block block) {
        return (block.getState().getData() instanceof Step ||
                block.getState().getData() instanceof WoodenStep) &&
                block.getType() != Material.DOUBLE_STEP &&
                block.getType() != Material.WOOD_DOUBLE_STEP;
    }

    @Override
    protected boolean isSlabTop(Block block) {
        if (block.getState().getData() instanceof Step) {
            return ((Step) block.getState().getData()).isInverted();
        }

        return ((WoodenStep) block.getState().getData()).isInverted();
    }

    @Override
    protected boolean hasEmptyHands(Player player) {
        return player.getInventory().getItemInHand().getType() == Material.AIR;
    }

    private static class CustomArmorStand extends EntityArmorStand {
        public boolean remove = false;
        private final int regenerationAmplifier;

        /**
         * @param regenerationAmplifier provide a negative value to disable regeneration
         */
        public CustomArmorStand(World world, double d0, double d1, double d2, int regenerationAmplifier) {
            super(world, d0, d1, d2);

            this.regenerationAmplifier = regenerationAmplifier;
        }

        @Override
        public void g(float f, float f1) {
            if (remove) return; // If the ArmorStand is being removed, no need to bother
            if (this.ticksLived % 10 == 0) return;  // Only run every 10 ticks

            if (!(this.passenger instanceof EntityHuman)) {
                remove = true;
                this.bukkitEntity.remove();
                return;
            }

            // Rotate the ArmorStand together with its passenger
            this.setYawPitch(this.passenger.yaw, this.passenger.pitch * .5F);
            this.aK = this.yaw;

            if (this.regenerationAmplifier >= 0) {
                CraftHumanEntity p = ((EntityHuman) this.passenger).getBukkitEntity();

                if (!p.hasPotionEffect(PotionEffectType.REGENERATION)) {
                    p.addPotionEffect(new PotionEffect(
                            PotionEffectType.REGENERATION, ChairNMS.regenerationEffectDuration, this.regenerationAmplifier,
                            false, false), true);
                }
            }
        }

        @Override
        public void die() {
            // Prevents the ArmorStand from getting killed unexpectedly
            if (shouldDie()) super.die();
        }

        private boolean shouldDie() {
            return remove || this.passenger == null || !(this.passenger instanceof EntityHuman);
        }
    }
}