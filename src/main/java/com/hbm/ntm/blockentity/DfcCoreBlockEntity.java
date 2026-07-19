package com.hbm.ntm.blockentity;

import com.hbm.ntm.dfc.DfcKind;
import com.hbm.ntm.dfc.DfcTank;
import com.hbm.ntm.item.DfcCatalystItem;
import com.hbm.ntm.item.DfcCoreItem;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.nuclear.FleijaExplosionEntity;
import com.hbm.ntm.nuclear.FleijaRainbowCloudEntity;
import com.hbm.ntm.radiation.ChunkRadiationData;
import com.hbm.ntm.radiation.ModDamageTypes;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Dark Fusion Core. The safe operating temperature is elsewhere. */
public final class DfcCoreBlockEntity extends DfcBlockEntity {
    public static final int TANK_CAPACITY = 128_000;
    private final DfcTank fuel0 = new DfcTank(ModFluids.DEUTERIUM.get(), TANK_CAPACITY,
            fluid -> fuelEfficiency(fluid) > 0.0F, this::setChanged);
    private final DfcTank fuel1 = new DfcTank(ModFluids.TRITIUM.get(), TANK_CAPACITY,
            fluid -> fuelEfficiency(fluid) > 0.0F, this::setChanged);

    private int field;
    private int heat;
    /** Yesterday's heat, for clients living in the past. */
    private int displayHeat;
    private int color;
    private boolean lastTickValid;
    private boolean meltdownTick;
    private int consumption;
    private int previousConsumption;
    private long lastSync = Long.MIN_VALUE;

    public DfcCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DFC_CORE.get(), pos, state, DfcKind.CORE);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DfcCoreBlockEntity core) {
        if (level instanceof ServerLevel server) core.serverTick(server, pos, state);
    }

    private void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        previousConsumption = consumption;
        consumption = 0;
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        meltdownTick = false;
        // Axials are irrelevant. Diagonals are mandatory. Fusion geometry, probably.
        lastTickValid = level.hasChunk(chunkX, chunkZ)
                && level.hasChunk(chunkX + 1, chunkZ + 1)
                && level.hasChunk(chunkX + 1, chunkZ - 1)
                && level.hasChunk(chunkX - 1, chunkZ + 1)
                && level.hasChunk(chunkX - 1, chunkZ - 1);

        if (lastTickValid && heat > 0 && heat >= field) triggerMeltdown(level, pos);
        color = catalystColor();
        if (heat > 0) irradiate(level, pos);
        displayHeat = heat;
        sync(level, pos, state);
        heat = 0;
        if (lastTickValid && field > 0) field--;
        setChanged();
    }

    private void triggerMeltdown(ServerLevel level, BlockPos pos) {
        int size = meltdownSize(fuel0.amount() + fuel1.amount(), heat);
        // TODO jammer support; for now the core is an equal-opportunity crater
        boolean canExplode = true;
        if (canExplode) {
            FleijaExplosionEntity explosion = FleijaExplosionEntity.create(level,
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, size);
            level.addFreshEntity(explosion);
            level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 100_000.0F, 1.0F);
            level.addFreshEntity(FleijaRainbowCloudEntity.create(level,
                    pos.getX(), pos.getY(), pos.getZ(), size));
        } else {
            meltdownTick = true;
            ChunkRadiationData.get(level).increment(pos, 100.0F);
        }
    }

    /** Int overflow is the secret eighth fuel. */
    public static int meltdownSize(int combinedFill, int heat) {
        int mod = heat * 10;
        return Math.max(Math.min(combinedFill * mod / (TANK_CAPACITY * 2), 1_000), 50);
    }

    private int catalystColor() {
        if (!(items.get(0).getItem() instanceof DfcCatalystItem first)
                || !(items.get(2).getItem() instanceof DfcCatalystItem second)) return 0;
        return averageColor(first.color(), second.color());
    }

    public static int averageColor(int first, int second) {
        int red = (((first >> 16 & 255) + (second >> 16 & 255)) / 2) << 16;
        int green = (((first >> 8 & 255) + (second >> 8 & 255)) / 2) << 8;
        int blue = ((first & 255) + (second & 255)) / 2;
        return red | green | blue;
    }

    private void irradiate(ServerLevel level, BlockPos pos) {
        double innerRange = meltdownTick ? 5.0D : 3.0D;
        double outerRange = meltdownTick ? 50.0D : 10.0D;
        Vec3 center = pos.getCenter();
        Vec3 rayOrigin = new Vec3(pos.getX() + 0.5D, pos.getY() + 6.5D, pos.getZ() + 0.5D);
        List<Entity> outer = level.getEntities(null, new AABB(center, center).inflate(outerRange));
        for (Entity entity : outer) {
            if (!entity.isAlive() || entity instanceof Player player && hasYellowHazmat(player)) continue;
            Vec3 target = new Vec3(entity.getX(), entity.getEyeY(), entity.getZ());
            if (level.clip(new ClipContext(rayOrigin, target, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, entity)).getType() != HitResult.Type.MISS) continue;
            entity.hurt(level.damageSources().source(ModDamageTypes.AMS), 1_000.0F);
            entity.igniteForSeconds(3.0F);
        }
        // TODO Haz2 suit; current dress code is "perish"
        for (Entity entity : level.getEntities(null, new AABB(center, center).inflate(innerRange))) {
            if (entity.isAlive()) entity.hurt(level.damageSources().source(ModDamageTypes.AMS_CORE), 10_000.0F);
        }
    }

    private static boolean hasYellowHazmat(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.HAZMAT_HELMET.get())
                && player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.HAZMAT_PLATE.get())
                && player.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.HAZMAT_LEGS.get())
                && player.getItemBySlot(EquipmentSlot.FEET).is(ModItems.HAZMAT_BOOTS.get());
    }

    public boolean isReady() {
        return lastTickValid && coreMultiplier() != 0 && color != 0
                && fuelEfficiency(fuel0.fluid()) > 0.0F && fuelEfficiency(fuel1.fluid()) > 0.0F;
    }

    public long burn(long joules) {
        if (!isReady()) return joules;
        int demand = (int) Math.ceil((double) joules / 1_000.0D);
        if (fuel0.amount() < demand || fuel1.amount() < demand) return joules;
        consumption += demand;
        heat += (int) Math.ceil((double) joules / 10_000.0D);
        fuel0.remove(demand);
        fuel1.remove(demand);
        setChanged();
        return (long) (joules * coreMultiplier() * fuelEfficiency(fuel0.fluid()) * fuelEfficiency(fuel1.fluid()));
    }

    public int coreMultiplier() {
        return items.get(1).getItem() instanceof DfcCoreItem core ? core.dfcMultiplier() : 0;
    }

    public static float fuelEfficiency(Fluid fluid) {
        if (fluid == null) return 0.0F;
        if (fluid.isSame(ModFluids.HYDROGEN.get())) return 1.0F;
        if (fluid.isSame(ModFluids.DEUTERIUM.get())) return 1.5F;
        if (fluid.isSame(ModFluids.TRITIUM.get())) return 1.7F;
        if (fluid.isSame(ModFluids.PEROXIDE.get())) return 1.4F;
        var id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid);
        if (id == null || !id.getNamespace().equals("hbm")) return 0.0F;
        return switch (id.getPath()) {
            case "oxygen" -> 1.2F;
            case "xenon" -> 1.5F;
            case "sas3" -> 2.0F;
            case "balefire" -> 2.5F;
            case "amat" -> 2.2F;
            case "aschrab" -> 2.7F;
            default -> 0.0F;
        };
    }

    public DfcTank tank(int index) { return index == 0 ? fuel0 : fuel1; }
    public int field() { return field; }
    public int heat() { return displayHeat; }
    /** Client-friendly translation of "something is happening." */
    public boolean isReacting() { return displayHeat > 0 || previousConsumption > 0; }
    public int color() { return color; }
    public boolean meltdownTick() { return meltdownTick; }
    public int previousConsumption() { return previousConsumption; }
    public void reinforceField(int strength) { field = Math.max(field, strength); setChanged(); }

    private void sync(ServerLevel level, BlockPos pos, BlockState state) {
        long signature = ((long) field << 48) ^ ((long) displayHeat << 32) ^ ((long) color << 8)
                ^ fuel0.amount() ^ ((long) fuel1.amount() << 17) ^ (meltdownTick ? 1L : 0L);
        if (signature != lastSync || level.getGameTime() % 20L == 0L) {
            lastSync = signature;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override protected int menuValue(int index) {
        return switch (index) {
            case 9 -> fuel0.amount();
            case 10 -> FluidIdentifierItem.Selection.fromFluid(fuel0.fluid()).ordinal();
            case 11 -> fuel1.amount();
            case 12 -> FluidIdentifierItem.Selection.fromFluid(fuel1.fluid()).ordinal();
            case 13 -> field;
            case 14 -> displayHeat;
            case 15 -> color;
            case 16 -> meltdownTick ? 1 : 0;
            case 17 -> previousConsumption;
            default -> 0;
        };
    }

    @Override protected void saveDfcState(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        tag.put("fuel0", fuel0.save(registries));
        tag.put("fuel1", fuel1.save(registries));
        tag.putInt("field", field);
        if (clientPacket) {
            tag.putInt("heat", displayHeat);
            tag.putInt("color", color);
            tag.putBoolean("meltdownTick", meltdownTick);
            tag.putInt("previousConsumption", previousConsumption);
        }
    }

    @Override protected void loadDfcState(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        if (tag.contains("fuel0")) fuel0.load(tag.getCompound("fuel0"), registries);
        if (tag.contains("fuel1")) fuel1.load(tag.getCompound("fuel1"), registries);
        field = tag.getInt("field");
        if (clientPacket) {
            displayHeat = tag.getInt("heat");
            color = tag.getInt("color");
            meltdownTick = tag.getBoolean("meltdownTick");
            previousConsumption = tag.getInt("previousConsumption");
        }
    }

    @Override public boolean canPlaceItem(int slot, net.minecraft.world.item.ItemStack stack) {
        if (slot == 0 || slot == 2) return stack.getItem() instanceof DfcCatalystItem;
        return slot == 1 && stack.getItem() instanceof DfcCoreItem;
    }

    public void setReadyForTest(boolean valid, int reactionColor) {
        lastTickValid = valid;
        color = reactionColor;
    }
}
