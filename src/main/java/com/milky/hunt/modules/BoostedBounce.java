package com.milky.hunt.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.ChestSwap;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import com.milky.hunt.Addon;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

import java.util.List;

public class BoostedBounce extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgObstacle = settings.createGroup("Obstacle Pause");

    private final Setting<Boolean> highwayObstaclePause = sgObstacle.add(new BoolSetting.Builder()
        .name("Highway Obstacle Pause")
        .description("Met le module en pause si un obstacle physique est détecté.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> toggleElytra = sgGeneral.add(new BoolSetting.Builder()
        .name("Toggle Elytra")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoReplaceElytra = sgGeneral.add(new BoolSetting.Builder()
        .name("Auto Replace Elytra")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minElytraDurability = sgGeneral.add(new IntSetting.Builder()
        .name("Min Elytra Durability")
        .defaultValue(10)
        .visible(autoReplaceElytra::get)
        .build()
    );

    private final Setting<Boolean> autoWearGold = sgGeneral.add(new BoolSetting.Builder()
        .name("Auto Wear Gold")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> minGoldDurability = sgGeneral.add(new IntSetting.Builder()
        .name("Min Gold Durability")
        .defaultValue(10)
        .visible(autoWearGold::get)
        .build()
    );

    private final Setting<List<Item>> goldPieces = sgGeneral.add(new ItemListSetting.Builder()
        .name("Gold Pieces")
        .filter(it -> it == Items.GOLDEN_HELMET || it == Items.GOLDEN_LEGGINGS || it == Items.GOLDEN_BOOTS)
        .defaultValue(List.of(Items.GOLDEN_HELMET, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS))
        .visible(autoWearGold::get)
        .build()
    );

    public BoostedBounce() {
        super(Addon.MilkyModCategory, "BoostedBounce", "Elytra assist hautement optimisé pour GrimAC.");
    }

    private boolean paused = false;
    private boolean elytraToggled = false;
    private int reopenTicks = 0;
    private int glideCooldown = 0;

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof CloseScreenS2CPacket) event.cancel();
    }

    @Override
    public void onActivate() {
        paused = false;
        elytraToggled = false;
        glideCooldown = 0;
    }

    @Override
    public void onDeactivate() {
        if (toggleElytra.get()) {
            maybeSwapBackChestplate();
            maybeSwapBackLeggings();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.player.getAbilities().allowFlying) return;

        if (toggleElytra.get() && !elytraToggled) {
            if (!mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
                Modules.get().get(ChestSwap.class).swap();
            }
            elytraToggled = true;
        }

        if (autoReplaceElytra.get()) maybeReplaceElytra();
        if (autoWearGold.get()) maintainGoldArmor();

        if (reopenTicks > 0) {
            if (!mc.player.isOnGround()) sendStartFlyingPacket();
            reopenTicks--;
        }

        if (highwayObstaclePause.get()) {
            paused = mc.player.horizontalCollision && isFrontBlocked(mc.player);
        }

        if (enabled()) {
            handleLegitElytraFly();
        }
    }

    public boolean enabled() {
        return this.isActive() && !paused && mc.player != null && mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    private void handleLegitElytraFly() {
        // Déclenchement automatique de l'ouverture
        if (!mc.player.isOnGround() && !mc.player.isFallFlying() && mc.player.getVelocity().y < -0.05 && glideCooldown <= 0) {
            sendStartFlyingPacket();
            glideCooldown = 20;
        }
        
        if (mc.player.isFallFlying()) applyGrimSafeMovement();
        if (glideCooldown > 0) glideCooldown--;
    }

    private void applyGrimSafeMovement() {
        Vec3d vel = mc.player.getVelocity();
        double x = vel.x;
        double y = vel.y;
        double z = vel.z;

        double maxHorizontalSpeed = 1.2;
        double horizontal = Math.sqrt(x * x + z * z);
        boolean modified = false;

        // Soft horizontal limiter avec lissage (Smoothing)
        if (horizontal > maxHorizontalSpeed) {
            double scale = maxHorizontalSpeed / horizontal;
            double targetX = x * scale;
            double targetZ = z * scale;

            // Interpolation pour éviter les cassures nettes de trajectoire
            x += (targetX - x) * 0.15;
            z += (targetZ - z) * 0.15;
            modified = true;
        }

        // Correction de gravité ultra-légère pour simuler un vol plané naturel
        if (y > -0.08) {
            y -= 0.005;
            modified = true;
        }

        // Application finale uniquement si une modification a été nécessaire
        if (modified) {
            mc.player.setVelocity(x, y, z);
        }
    }

    private void sendStartFlyingPacket() {
        if (mc.player == null) return;
        mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
    }

    private void maybeReplaceElytra() {
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (isHealthyElytra(chest)) return;
        
        int slot = findBestElytraSlot();
        if (slot != -1) {
            InvUtils.move().from(slot).toArmor(2);
            reopenTicks = 5;
        }
    }

    private boolean isHealthyElytra(ItemStack stack) {
        return !stack.isEmpty() && stack.isOf(Items.ELYTRA) && (stack.getMaxDamage() - stack.getDamage()) >= minElytraDurability.get();
    }

    private int findBestElytraSlot() {
        int bestSlot = -1, bestRemain = -1;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isOf(Items.ELYTRA)) {
                int remain = s.getMaxDamage() - s.getDamage();
                if (remain >= minElytraDurability.get() && remain > bestRemain) {
                    bestRemain = remain;
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }

    private void maintainGoldArmor() {
        for (Item it : goldPieces.get()) {
            EquipmentSlot slot = getSlotForItem(it);
            if (slot == null) continue;
            
            ItemStack equipped = mc.player.getEquippedStack(slot);
            if (equipped.isOf(it) && (equipped.getMaxDamage() - equipped.getDamage()) >= minGoldDurability.get()) continue;
            
            int best = findBestGoldSlot(it);
            if (best != -1) InvUtils.move().from(best).toArmor(getArmorInventoryIndex(slot));
        }
    }

    private EquipmentSlot getSlotForItem(Item it) {
        if (it == Items.GOLDEN_HELMET) return EquipmentSlot.HEAD;
        if (it == Items.GOLDEN_LEGGINGS) return EquipmentSlot.LEGS;
        if (it == Items.GOLDEN_BOOTS) return EquipmentSlot.FEET;
        return null;
    }

    private int getArmorInventoryIndex(EquipmentSlot slot) {
        return switch (slot) {
            case FEET -> 0;
            case LEGS -> 1;
            case CHEST -> 2;
            case HEAD -> 3;
            default -> -1;
        };
    }

    private int findBestGoldSlot(Item it) {
        int best = -1, bestRemain = -1;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isOf(it)) {
                int r = s.getMaxDamage() - s.getDamage();
                if (r >= minGoldDurability.get() && r > bestRemain) {
                    bestRemain = r;
                    best = i;
                }
            }
        }
        return best;
    }

    private void maybeSwapBackChestplate() {
        int best = -1, bestTier = -1;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            Item item = mc.player.getInventory().getStack(i).getItem();
            int tier = (item == Items.NETHERITE_CHESTPLATE) ? 3 : (item == Items.DIAMOND_CHESTPLATE) ? 2 : (item == Items.IRON_CHESTPLATE) ? 1 : -1;
            if (tier > bestTier) {
                bestTier = tier;
                best = i;
            }
        }
        if (best != -1) InvUtils.move().from(best).toArmor(2);
    }

    private void maybeSwapBackLeggings() {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() instanceof ArmorItem armor && armor.getSlotType() == EquipmentSlot.LEGS) {
                if (!s.isOf(Items.GOLDEN_LEGGINGS)) {
                    InvUtils.move().from(i).toArmor(1);
                    break;
                }
            }
        }
    }

    private static boolean isFrontBlocked(net.minecraft.entity.player.PlayerEntity p) {
        World w = p.getWorld();
        Direction facing = p.getHorizontalFacing();
        BlockPos base = BlockPos.ofFloored(p.getX() + facing.getOffsetX() * 0.8, p.getY(), p.getZ() + facing.getOffsetZ() * 0.8);
        return isSolid(w, base) || isSolid(w, base.up());
    }

    private static boolean isSolid(World w, BlockPos pos) {
        BlockState s = w.getBlockState(pos);
        return !s.isAir() && !s.getCollisionShape(w, pos).isEmpty();
    }
}
