package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.AirIntakeBlock;
import com.hbm.ntm.energy.HeReceiver;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/** Source Air Intake: 100 HE/t refills a 1,000 mB ordinary-air sender tank. */
public final class AirIntakeBlockEntity extends BlockEntity implements HeReceiver {
    public static final int AIR_CAPACITY = 1_000;
    public static final long POWER_CAPACITY = 2_000L;
    public static final long POWER_PER_TICK = POWER_CAPACITY / 20L;

    private final FluidTank air = new FluidTank(AIR_CAPACITY,
            stack -> !stack.isEmpty() && stack.getFluid().isSame(ModFluids.AIR.get())) {
        @Override protected void onContentsChanged() { AirIntakeBlockEntity.this.setChanged(); }
    };
    private final IFluidHandler outputHandler = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) {
            return tank == 0 ? air.getFluid().copy() : FluidStack.EMPTY;
        }
        @Override public int getTankCapacity(int tank) { return tank == 0 ? AIR_CAPACITY : 0; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return false; }
        @Override public int fill(FluidStack resource, FluidAction action) { return 0; }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !resource.getFluid().isSame(ModFluids.AIR.get())) return FluidStack.EMPTY;
            return air.drain(resource, action);
        }
        @Override public FluidStack drain(int maxDrain, FluidAction action) { return air.drain(maxDrain, action); }
    };

    private long power;
    private boolean active;
    private float fan;
    private float previousFan;
    private long lastPower = Long.MIN_VALUE;
    private int lastAir = Integer.MIN_VALUE;
    private boolean lastActive;

    public AirIntakeBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_INTAKE.get(), position, state);
    }

    public static void tick(Level level, BlockPos position, BlockState state, AirIntakeBlockEntity intake) {
        if (level.isClientSide) intake.clientTick();
        else intake.serverTick((ServerLevel) level, position, state);
    }

    private void serverTick(ServerLevel level, BlockPos position, BlockState state) {
        conform();
        if (power >= POWER_PER_TICK) {
            air.setFluid(new FluidStack(ModFluids.AIR.get(), AIR_CAPACITY));
            power -= POWER_PER_TICK;
        }

        Direction facing = state.getValue(AirIntakeBlock.FACING);
        for (AirIntakeBlock.Connection connection : AirIntakeBlock.connections(position, facing)) {
            if (!air.isEmpty()) pushAir(level, connection);
            trySubscribe(level, connection.target(), connection.outward());
        }

        // The old client spins when the post-consumption power packet still contains at least one tick.
        active = power >= POWER_PER_TICK;
        syncIfChanged(level, position, state);
    }

    private void pushAir(ServerLevel level, AirIntakeBlock.Connection connection) {
        IFluidHandler target = level.getCapability(Capabilities.FluidHandler.BLOCK,
                connection.target(), connection.outward().getOpposite());
        if (target == null) return;
        int accepted = target.fill(air.getFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
        if (accepted > 0) air.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
    }

    private void clientTick() {
        previousFan = fan;
        if (!active) return;
        fan += 45.0F;
        if (fan >= 360.0F) {
            fan -= 360.0F;
            previousFan -= 360.0F;
        }
    }

    private void conform() {
        power = Math.clamp(power, 0L, POWER_CAPACITY);
        if (!air.isEmpty() && !air.getFluid().getFluid().isSame(ModFluids.AIR.get())) {
            air.setFluid(FluidStack.EMPTY);
        }
        if (air.getFluidAmount() > AIR_CAPACITY) {
            air.setFluid(air.getFluid().copyWithAmount(AIR_CAPACITY));
        }
    }

    private void syncIfChanged(ServerLevel level, BlockPos position, BlockState state) {
        if (power != lastPower || air.getFluidAmount() != lastAir || active != lastActive
                || level.getGameTime() % 50L == 0L) {
            lastPower = power;
            lastAir = air.getFluidAmount();
            lastActive = active;
            level.sendBlockUpdated(position, state, state, Block.UPDATE_CLIENTS);
        }
        setChanged();
    }

    public FluidTank airTank() { return air; }
    public IFluidHandler outputHandler() { return outputHandler; }
    public boolean active() { return active; }
    public float fan(float partialTick) { return Mth.lerp(partialTick, previousFan, fan); }
    public AABB renderBounds() {
        return new AABB(worldPosition.getX() - 1.0D, worldPosition.getY(), worldPosition.getZ() - 1.0D,
                worldPosition.getX() + 2.0D, worldPosition.getY() + 1.0D, worldPosition.getZ() + 2.0D);
    }

    @Override public boolean canConnect(Direction side) {
        return side != null && side.getAxis().isHorizontal();
    }
    @Override public long getPower() { return power; }
    @Override public void setPower(long power) {
        this.power = Math.clamp(power, 0L, POWER_CAPACITY);
        setChanged();
    }
    @Override public long getMaxPower() { return POWER_CAPACITY; }
    @Override public boolean isHeLoaded() { return hasLevel() && !isRemoved(); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("power", power);
        tag.put("compair", air.writeToNBT(registries, new CompoundTag()));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        power = tag.getLong("power");
        if (tag.contains("compair")) air.readFromNBT(registries, tag.getCompound("compair"));
        conform();
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("power", power);
        tag.putInt("air", air.getFluidAmount());
        tag.putBoolean("active", active);
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        power = Math.clamp(tag.getLong("power"), 0L, POWER_CAPACITY);
        int amount = Math.clamp(tag.getInt("air"), 0, AIR_CAPACITY);
        air.setFluid(amount == 0 ? FluidStack.EMPTY : new FluidStack(ModFluids.AIR.get(), amount));
        active = tag.getBoolean("active");
    }

    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
