package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.SteamEngineBlock;
import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.energy.HeProvider;
import com.hbm.ntm.network.SteamEngineStatePayload;
import com.hbm.ntm.registry.ModBlockEntities;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Exact normal-Steam turbine path of the 1.7.10 Steam Engine. */
public final class SteamEngineBlockEntity extends BlockEntity implements HeProvider {
    public static final int STEAM_CAPACITY = 2_000;
    public static final int SPENT_STEAM_CAPACITY = 20;
    public static final int STEAM_PER_OPERATION = 100;
    public static final int SPENT_STEAM_PER_OPERATION = 1;
    public static final int HEAT_PER_OPERATION = 200;
    public static final double EFFICIENCY = 0.85D;
    public static final float MAX_ACCELERATION = 40.0F;

    private final FluidTank steam = fixedTank(HbmConfig.STEAM_ENGINE_STEAM_CAPACITY.get(), ModFluids.STEAM.get());
    private final FluidTank spentSteam = fixedTank(HbmConfig.STEAM_ENGINE_SPENT_STEAM_CAPACITY.get(),
            ModFluids.SPENTSTEAM.get());
    private final IFluidHandler portFluidHandler = new IFluidHandler() {
        @Override public int getTanks() { return 2; }
        @Override public FluidStack getFluidInTank(int tank) {
            return tank == 0 ? steam.getFluid() : tank == 1 ? spentSteam.getFluid() : FluidStack.EMPTY;
        }
        @Override public int getTankCapacity(int tank) {
            return tank == 0 ? steam.getCapacity() : tank == 1 ? spentSteam.getCapacity() : 0;
        }
        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 && stack.getFluid().isSame(ModFluids.STEAM.get());
        }
        @Override public int fill(FluidStack resource, FluidAction action) {
            return isFluidValid(0, resource) ? steam.fill(resource, action) : 0;
        }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            return resource.getFluid().isSame(ModFluids.SPENTSTEAM.get())
                    ? spentSteam.drain(resource, action) : FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int maxDrain, FluidAction action) {
            return spentSteam.drain(maxDrain, action);
        }
    };

    private long powerBuffer;
    private float acceleration;
    private float rotor;
    private float lastRotor;
    private float synchronizedRotor;
    private int turnProgress;

    public SteamEngineBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_STEAM_ENGINE.get(), position, state);
    }

    private FluidTank fixedTank(int capacity, Fluid fluid) {
        return new FluidTank(capacity, stack -> stack.getFluid().isSame(fluid)) {
            @Override protected void onContentsChanged() { SteamEngineBlockEntity.this.setChanged(); }
        };
    }

    public static void tick(Level level, BlockPos position, BlockState state, SteamEngineBlockEntity engine) {
        if (level.isClientSide) engine.clientTick();
        else engine.serverTick((ServerLevel) level, position, state);
    }

    private void serverTick(ServerLevel level, BlockPos position, BlockState state) {
        powerBuffer = 0L;
        conformTanks();

        int operations = Math.min(steam.getFluidAmount() / STEAM_PER_OPERATION,
                spentSteam.getSpace() / SPENT_STEAM_PER_OPERATION);
        if (operations > 0) {
            steam.drain(operations * STEAM_PER_OPERATION, IFluidHandler.FluidAction.EXECUTE);
            spentSteam.fill(new FluidStack(ModFluids.SPENTSTEAM.get(),
                    operations * SPENT_STEAM_PER_OPERATION), IFluidHandler.FluidAction.EXECUTE);
            powerBuffer += (long) (operations * HEAT_PER_OPERATION * HbmConfig.STEAM_ENGINE_EFFICIENCY.get());
            acceleration += 0.1F;
        } else {
            acceleration -= 0.1F;
        }

        acceleration = Mth.clamp(acceleration, 0.0F, MAX_ACCELERATION);
        rotor += acceleration;
        if (rotor >= 360.0F) {
            rotor -= 360.0F;
            level.playSound(null, position, ModSounds.STEAM_ENGINE_OPERATE.get(), SoundSource.BLOCKS,
                    1.0F, 0.5F + acceleration / 80.0F);
        }

        // Source serialized before its port loop, so the overlay shows generated HE and pre-push fluid levels.
        PacketDistributor.sendToPlayersNear(level, null, position.getX() + 0.5D,
                position.getY() + 1.0D, position.getZ() + 0.5D, 150.0D,
                new SteamEngineStatePayload(position, powerBuffer, rotor,
                        steam.getFluidAmount(), spentSteam.getFluidAmount()));

        Direction facing = state.getValue(SteamEngineBlock.FACING);
        for (SteamEngineBlock.Connection connection : SteamEngineBlock.connections(position, facing)) {
            if (powerBuffer > 0L) tryProvide(level, connection.target(), connection.outward());
            exchangeFluids(level, connection.target(), connection.outward());
        }
        setChanged();
    }

    private void exchangeFluids(ServerLevel level, BlockPos target, Direction outward) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK,
                target, outward.getOpposite());
        if (handler == null) return;

        if (steam.getSpace() > 0) {
            FluidStack request = new FluidStack(ModFluids.STEAM.get(), steam.getSpace());
            FluidStack available = handler.drain(request, IFluidHandler.FluidAction.SIMULATE);
            if (!available.isEmpty() && available.getFluid().isSame(ModFluids.STEAM.get())) {
                int accepted = steam.fill(available, IFluidHandler.FluidAction.SIMULATE);
                if (accepted > 0) {
                    FluidStack drained = handler.drain(new FluidStack(ModFluids.STEAM.get(), accepted),
                            IFluidHandler.FluidAction.EXECUTE);
                    steam.fill(drained, IFluidHandler.FluidAction.EXECUTE);
                }
            }
        }

        if (!spentSteam.isEmpty()) {
            int accepted = handler.fill(spentSteam.getFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
            if (accepted > 0) spentSteam.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private void clientTick() {
        lastRotor = rotor;
        if (turnProgress > 0) {
            float difference = Mth.wrapDegrees(synchronizedRotor - rotor);
            rotor += difference / turnProgress;
            turnProgress--;
        } else {
            rotor = synchronizedRotor;
        }
    }

    private void conformTanks() {
        if (!steam.isEmpty() && !steam.getFluid().getFluid().isSame(ModFluids.STEAM.get())) {
            steam.setFluid(FluidStack.EMPTY);
        }
        if (!spentSteam.isEmpty() && !spentSteam.getFluid().getFluid().isSame(ModFluids.SPENTSTEAM.get())) {
            spentSteam.setFluid(FluidStack.EMPTY);
        }
    }

    public void applyClientSnapshot(long power, float rotor, int steamAmount, int spentSteamAmount) {
        powerBuffer = Math.max(power, 0L);
        synchronizedRotor = rotor;
        turnProgress = 3;
        steam.setFluid(steamAmount <= 0 ? FluidStack.EMPTY
                : new FluidStack(ModFluids.STEAM.get(), Math.min(steamAmount, steam.getCapacity())));
        spentSteam.setFluid(spentSteamAmount <= 0 ? FluidStack.EMPTY
                : new FluidStack(ModFluids.SPENTSTEAM.get(), Math.min(spentSteamAmount, spentSteam.getCapacity())));
    }

    public IFluidHandler portFluidHandler() { return portFluidHandler; }
    public FluidTank steamTank() { return steam; }
    public FluidTank spentSteamTank() { return spentSteam; }
    public float rotor() { return rotor; }
    public float lastRotor() { return lastRotor; }
    public float acceleration() { return acceleration; }
    public AABB renderBounds() {
        BlockState state = getBlockState();
        Direction facing = state.hasProperty(SteamEngineBlock.FACING)
                ? state.getValue(SteamEngineBlock.FACING) : Direction.NORTH;
        AABB bounds = new AABB(worldPosition);
        for (BlockPos part : SteamEngineBlock.partPositions(worldPosition, facing)) {
            bounds = bounds.minmax(new AABB(part));
        }
        return bounds.inflate(0.25D);
    }

    @Override public boolean canConnect(Direction side) { return side != null && side.getAxis().isHorizontal(); }
    @Override public long getPower() { return Math.max(powerBuffer, 0L); }
    @Override public void setPower(long power) { powerBuffer = Math.max(power, 0L); }
    @Override public long getMaxPower() { return Math.max(powerBuffer, 0L); }
    @Override public boolean isHeLoaded() { return hasLevel() && !isRemoved(); }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("powerBuffer", powerBuffer);
        tag.putFloat("acceleration", acceleration);
        tag.put("steam", steam.writeToNBT(registries, new CompoundTag()));
        tag.put("spentSteam", spentSteam.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        powerBuffer = Math.max(tag.getLong("powerBuffer"), 0L);
        acceleration = Mth.clamp(tag.getFloat("acceleration"), 0.0F, MAX_ACCELERATION);
        if (tag.contains("steam")) steam.readFromNBT(registries, tag.getCompound("steam"));
        if (tag.contains("spentSteam")) spentSteam.readFromNBT(registries, tag.getCompound("spentSteam"));
        conformTanks();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("powerBuffer", powerBuffer);
        tag.putFloat("rotor", rotor);
        tag.putInt("steam", steam.getFluidAmount());
        tag.putInt("spentSteam", spentSteam.getFluidAmount());
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        applyClientSnapshot(tag.getLong("powerBuffer"), tag.getFloat("rotor"),
                tag.getInt("steam"), tag.getInt("spentSteam"));
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
