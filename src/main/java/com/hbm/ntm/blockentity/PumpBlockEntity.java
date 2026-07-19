package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.PumpBlock;
import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.energy.HeReceiver;
import com.hbm.ntm.network.PumpStatePayload;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Turns either steam or electricity into suspiciously local groundwater. */
public final class PumpBlockEntity extends BlockEntity implements HeReceiver {
    public static final int STEAM_CAPACITY = 1_000;
    public static final int SPENT_STEAM_CAPACITY = 10;
    public static final int STEAM_PER_OPERATION = 100;
    public static final int SPENT_STEAM_PER_OPERATION = 1;
    public static final long ELECTRIC_POWER_CAPACITY = 10_000L;
    public static final long ELECTRIC_POWER_PER_OPERATION = 1_000L;

    private final FluidTank water = fixedTank(Math.max(1, speed() * 100), Fluids.WATER);
    private final FluidTank steam = fixedTank(STEAM_CAPACITY, ModFluids.STEAM.get());
    private final FluidTank spentSteam = fixedTank(SPENT_STEAM_CAPACITY, ModFluids.SPENTSTEAM.get());
    private final IFluidHandler portFluidHandler = new IFluidHandler() {
        @Override public int getTanks() { return electric() ? 1 : 3; }
        @Override public FluidStack getFluidInTank(int tank) {
            if (tank == 0) return water.getFluid();
            if (!electric() && tank == 1) return steam.getFluid();
            if (!electric() && tank == 2) return spentSteam.getFluid();
            return FluidStack.EMPTY;
        }
        @Override public int getTankCapacity(int tank) {
            if (tank == 0) return water.getCapacity();
            if (!electric() && tank == 1) return steam.getCapacity();
            if (!electric() && tank == 2) return spentSteam.getCapacity();
            return 0;
        }
        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            return !electric() && tank == 1 && stack.getFluid().isSame(ModFluids.STEAM.get());
        }
        @Override public int fill(FluidStack resource, FluidAction action) {
            return !electric() && resource.getFluid().isSame(ModFluids.STEAM.get())
                    ? steam.fill(resource, action) : 0;
        }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.getFluid().isSame(Fluids.WATER)) return water.drain(resource, action);
            if (!electric() && resource.getFluid().isSame(ModFluids.SPENTSTEAM.get())) {
                return spentSteam.drain(resource, action);
            }
            return FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int maxDrain, FluidAction action) {
            FluidStack drained = water.drain(maxDrain, action);
            if (!drained.isEmpty() || electric()) return drained;
            return spentSteam.drain(maxDrain, action);
        }
    };

    private long power;
    private boolean isOn;
    private boolean onGround;
    private int groundCheckDelay;
    private float rotor;
    private float lastRotor;

    public PumpBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.PUMP.get(), position, state);
    }

    private FluidTank fixedTank(int capacity, Fluid fluid) {
        return new FluidTank(capacity, stack -> stack.getFluid().isSame(fluid)) {
            @Override protected void onContentsChanged() { PumpBlockEntity.this.setChanged(); }
        };
    }

    public static void tick(Level level, BlockPos position, BlockState state, PumpBlockEntity pump) {
        if (level.isClientSide) pump.clientTick();
        else pump.serverTick((ServerLevel) level, position);
    }

    private void serverTick(ServerLevel level, BlockPos position) {
        conformTanks();
        if (electric()) electricPrePass(level, position);
        else steamPrePass(level, position);

        // Export first. Water made this tick can travel next tick.
        for (PumpBlock.Connection connection : PumpBlock.connections(position)) {
            pushTank(level, connection.target(), connection.outward(), water);
        }

        if (groundCheckDelay > 0) groundCheckDelay--;
        else onGround = checkGround(level, position);

        isOn = false;
        if (canOperate() && position.getY() <= HbmConfig.WATER_PUMP_GROUND_HEIGHT.get() && onGround) {
            isOn = true;
            operate();
        }

        PacketDistributor.sendToPlayersNear(level, null, position.getX() + 0.5D,
                position.getY() + 1.0D, position.getZ() + 0.5D, 150.0D,
                new PumpStatePayload(position, isOn, onGround, water.getFluidAmount(),
                        steam.getFluidAmount(), spentSteam.getFluidAmount(), power));
        setChanged();
    }

    private void steamPrePass(ServerLevel level, BlockPos position) {
        for (PumpBlock.Connection connection : PumpBlock.connections(position)) {
            if (!spentSteam.isEmpty()) {
                pushTank(level, connection.target(), connection.outward(), spentSteam);
            }
            pullSteam(level, connection.target(), connection.outward());
        }
    }

    private void electricPrePass(ServerLevel level, BlockPos position) {
        if (level.getGameTime() % 20L != 0L) return;
        for (PumpBlock.Connection connection : PumpBlock.connections(position)) {
            trySubscribe(level, connection.target(), connection.outward());
        }
    }

    private void pullSteam(ServerLevel level, BlockPos target, Direction outward) {
        if (steam.getSpace() <= 0) return;
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK,
                target, outward.getOpposite());
        if (handler == null) return;
        FluidStack request = new FluidStack(ModFluids.STEAM.get(), steam.getSpace());
        FluidStack available = handler.drain(request, IFluidHandler.FluidAction.SIMULATE);
        if (available.isEmpty() || !available.getFluid().isSame(ModFluids.STEAM.get())) return;
        int accepted = steam.fill(available, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) return;
        FluidStack drained = handler.drain(new FluidStack(ModFluids.STEAM.get(), accepted),
                IFluidHandler.FluidAction.EXECUTE);
        steam.fill(drained, IFluidHandler.FluidAction.EXECUTE);
    }

    private static void pushTank(ServerLevel level, BlockPos target, Direction outward, FluidTank tank) {
        if (tank.isEmpty()) return;
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK,
                target, outward.getOpposite());
        if (handler == null) return;
        int accepted = handler.fill(tank.getFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
        if (accepted > 0) tank.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
    }

    private boolean checkGround(ServerLevel level, BlockPos position) {
        if (!level.dimensionType().hasSkyLight()) return false;
        int depth = HbmConfig.WATER_PUMP_GROUND_DEPTH.get();
        int valid = 0;
        int invalid = 0;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = -1; y >= -depth; y--) {
                    BlockPos checked = position.offset(x, y, z);
                    BlockState state = level.getBlockState(checked);
                    if (y == -1 && !state.isCollisionShapeFullBlock(level, checked)) return false;
                    if (isValidGround(state)) valid++;
                    else invalid++;
                }
            }
        }
        return valid >= invalid;
    }

    private static boolean isValidGround(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.SAND)
                || state.is(Blocks.MYCELIUM) || state.is(ModBlocks.DIRT_DEAD.get())
                || state.is(ModBlocks.DIRT_OILY.get()) || state.is(ModBlocks.SAND_DIRTY.get())
                || state.is(ModBlocks.SAND_DIRTY_RED.get());
    }

    private boolean canOperate() {
        if (water.getSpace() <= 0) return false;
        if (electric()) return power >= ELECTRIC_POWER_PER_OPERATION;
        return steam.getFluidAmount() >= STEAM_PER_OPERATION
                && spentSteam.getSpace() >= SPENT_STEAM_PER_OPERATION;
    }

    private void operate() {
        if (electric()) power -= ELECTRIC_POWER_PER_OPERATION;
        else {
            steam.drain(STEAM_PER_OPERATION, IFluidHandler.FluidAction.EXECUTE);
            spentSteam.fill(new FluidStack(ModFluids.SPENTSTEAM.get(), SPENT_STEAM_PER_OPERATION),
                    IFluidHandler.FluidAction.EXECUTE);
        }
        water.fill(new FluidStack(Fluids.WATER, speed()), IFluidHandler.FluidAction.EXECUTE);
    }

    private void clientTick() {
        lastRotor = rotor;
        if (!isOn) return;
        rotor += 10.0F;
        if (rotor >= 360.0F) {
            rotor -= 360.0F;
            lastRotor -= 360.0F;
            if (level != null) {
                level.playLocalSound(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                        worldPosition.getZ() + 0.5D, ModSounds.STEAM_ENGINE_OPERATE.get(),
                        SoundSource.BLOCKS, 0.5F, 0.75F, false);
                level.playLocalSound(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                        worldPosition.getZ() + 0.5D, SoundEvents.GENERIC_SPLASH,
                        SoundSource.BLOCKS, 1.0F, 0.5F, false);
            }
        }
    }

    private void conformTanks() {
        conform(water, Fluids.WATER);
        conform(steam, ModFluids.STEAM.get());
        conform(spentSteam, ModFluids.SPENTSTEAM.get());
        power = Math.max(0L, Math.min(power, ELECTRIC_POWER_CAPACITY));
    }

    private static void conform(FluidTank tank, Fluid expected) {
        if (!tank.isEmpty() && !tank.getFluid().getFluid().isSame(expected)) tank.setFluid(FluidStack.EMPTY);
    }

    public void applyClientSnapshot(boolean running, boolean validGround, int waterAmount,
                                    int steamAmount, int spentSteamAmount, long storedPower) {
        isOn = running;
        onGround = validGround;
        water.setFluid(waterAmount <= 0 ? FluidStack.EMPTY
                : new FluidStack(Fluids.WATER, Math.min(waterAmount, water.getCapacity())));
        steam.setFluid(steamAmount <= 0 ? FluidStack.EMPTY
                : new FluidStack(ModFluids.STEAM.get(), Math.min(steamAmount, steam.getCapacity())));
        spentSteam.setFluid(spentSteamAmount <= 0 ? FluidStack.EMPTY
                : new FluidStack(ModFluids.SPENTSTEAM.get(), Math.min(spentSteamAmount, spentSteam.getCapacity())));
        power = Math.max(0L, Math.min(storedPower, ELECTRIC_POWER_CAPACITY));
    }

    public boolean electric() {
        return getBlockState().getBlock() instanceof PumpBlock pump && pump.electric();
    }
    public int speed() {
        return electric() ? HbmConfig.WATER_PUMP_ELECTRIC_SPEED.get() : HbmConfig.WATER_PUMP_STEAM_SPEED.get();
    }
    public IFluidHandler portFluidHandler() { return portFluidHandler; }
    public FluidTank waterTank() { return water; }
    public FluidTank steamTank() { return steam; }
    public FluidTank spentSteamTank() { return spentSteam; }
    public boolean isOn() { return isOn; }
    public boolean onGround() { return onGround; }
    public float rotor() { return rotor; }
    public float lastRotor() { return lastRotor; }
    public AABB renderBounds() {
        return new AABB(worldPosition.getX() - 1.0D, worldPosition.getY(), worldPosition.getZ() - 1.0D,
                worldPosition.getX() + 2.0D, worldPosition.getY() + 5.0D, worldPosition.getZ() + 2.0D);
    }

    @Override public boolean canConnect(Direction side) {
        return electric() && side != null && side.getAxis().isHorizontal();
    }
    @Override public long getPower() { return power; }
    @Override public void setPower(long power) {
        this.power = Math.max(0L, Math.min(power, ELECTRIC_POWER_CAPACITY));
        setChanged();
    }
    @Override public long getMaxPower() { return ELECTRIC_POWER_CAPACITY; }
    @Override public boolean isHeLoaded() { return hasLevel() && !isRemoved(); }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("water", water.writeToNBT(registries, new CompoundTag()));
        if (electric()) tag.putLong("power", power);
        else {
            tag.put("steam", steam.writeToNBT(registries, new CompoundTag()));
            tag.put("spentSteam", spentSteam.writeToNBT(registries, new CompoundTag()));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("water")) water.readFromNBT(registries, tag.getCompound("water"));
        if (electric()) power = tag.getLong("power");
        else {
            if (tag.contains("steam")) steam.readFromNBT(registries, tag.getCompound("steam"));
            if (tag.contains("spentSteam")) spentSteam.readFromNBT(registries, tag.getCompound("spentSteam"));
        }
        conformTanks();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("isOn", isOn);
        tag.putBoolean("onGround", onGround);
        tag.putInt("water", water.getFluidAmount());
        tag.putInt("steam", steam.getFluidAmount());
        tag.putInt("spentSteam", spentSteam.getFluidAmount());
        tag.putLong("power", power);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        applyClientSnapshot(tag.getBoolean("isOn"), tag.getBoolean("onGround"), tag.getInt("water"),
                tag.getInt("steam"), tag.getInt("spentSteam"), tag.getLong("power"));
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
