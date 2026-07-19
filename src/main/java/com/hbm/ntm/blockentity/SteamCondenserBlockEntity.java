package com.hbm.ntm.blockentity;

import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.network.SteamCondenserStatePayload;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Turns sad steam back into water at a deeply ambitious 1:1 ratio. */
public final class SteamCondenserBlockEntity extends BlockEntity {
    private final FluidTank spentSteam = new FluidTank(HbmConfig.CONDENSER_INPUT_CAPACITY.get(),
            stack -> stack.getFluid().isSame(ModFluids.SPENTSTEAM.get())) {
        @Override protected void onContentsChanged() { SteamCondenserBlockEntity.this.setChanged(); }
    };
    private final FluidTank water = new FluidTank(HbmConfig.CONDENSER_OUTPUT_CAPACITY.get(),
            stack -> stack.getFluid().isSame(Fluids.WATER)) {
        @Override protected void onContentsChanged() { SteamCondenserBlockEntity.this.setChanged(); }
    };
    private final IFluidHandler fluidHandler = new IFluidHandler() {
        @Override public int getTanks() { return 2; }
        @Override public FluidStack getFluidInTank(int tank) {
            return tank == 0 ? spentSteam.getFluid() : tank == 1 ? water.getFluid() : FluidStack.EMPTY;
        }
        @Override public int getTankCapacity(int tank) {
            return tank == 0 ? spentSteam.getCapacity() : tank == 1 ? water.getCapacity() : 0;
        }
        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 && stack.getFluid().isSame(ModFluids.SPENTSTEAM.get());
        }
        @Override public int fill(FluidStack resource, FluidAction action) {
            return resource.getFluid().isSame(ModFluids.SPENTSTEAM.get())
                    ? spentSteam.fill(resource, action) : 0;
        }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            return resource.getFluid().isSame(Fluids.WATER) ? water.drain(resource, action) : FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int maxDrain, FluidAction action) {
            return water.drain(maxDrain, action);
        }
    };

    private int age;
    private int waterTimer;
    private int throughput;

    public SteamCondenserBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_CONDENSER.get(), position, state);
    }

    public static void tick(Level level, BlockPos position, BlockState state, SteamCondenserBlockEntity condenser) {
        if (!level.isClientSide) condenser.serverTick((ServerLevel) level, position);
    }

    private void serverTick(ServerLevel level, BlockPos position) {
        conformTanks();
        age++;
        if (age >= 2) age = 0;
        if (waterTimer > 0) waterTimer--;

        int convert = Math.min(spentSteam.getFluidAmount(), water.getSpace());
        throughput = convert;
        spentSteam.drain(convert, IFluidHandler.FluidAction.EXECUTE);
        if (convert > 0) waterTimer = 20;
        water.fill(new FluidStack(Fluids.WATER, convert), IFluidHandler.FluidAction.EXECUTE);

        // Condense first, then pull and push on every face.
        for (Direction direction : Direction.values()) pullSpentSteam(level, position.relative(direction), direction);
        for (Direction direction : Direction.values()) pushWater(level, position.relative(direction), direction);

        PacketDistributor.sendToPlayersNear(level, null, position.getX() + 0.5D,
                position.getY() + 0.5D, position.getZ() + 0.5D, 150.0D,
                new SteamCondenserStatePayload(position, spentSteam.getFluidAmount(),
                        water.getFluidAmount(), waterTimer));
        setChanged();
    }

    private void pullSpentSteam(ServerLevel level, BlockPos target, Direction outward) {
        if (spentSteam.getSpace() <= 0) return;
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK,
                target, outward.getOpposite());
        if (handler == null) return;
        FluidStack request = new FluidStack(ModFluids.SPENTSTEAM.get(), spentSteam.getSpace());
        FluidStack available = handler.drain(request, IFluidHandler.FluidAction.SIMULATE);
        if (available.isEmpty() || !available.getFluid().isSame(ModFluids.SPENTSTEAM.get())) return;
        int accepted = spentSteam.fill(available, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) return;
        FluidStack drained = handler.drain(new FluidStack(ModFluids.SPENTSTEAM.get(), accepted),
                IFluidHandler.FluidAction.EXECUTE);
        spentSteam.fill(drained, IFluidHandler.FluidAction.EXECUTE);
    }

    private void pushWater(ServerLevel level, BlockPos target, Direction outward) {
        if (water.isEmpty()) return;
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK,
                target, outward.getOpposite());
        if (handler == null) return;
        int accepted = handler.fill(water.getFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
        if (accepted > 0) water.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
    }

    private void conformTanks() {
        if (!spentSteam.isEmpty() && !spentSteam.getFluid().getFluid().isSame(ModFluids.SPENTSTEAM.get())) {
            spentSteam.setFluid(FluidStack.EMPTY);
        }
        if (!water.isEmpty() && !water.getFluid().getFluid().isSame(Fluids.WATER)) {
            water.setFluid(FluidStack.EMPTY);
        }
    }

    public void applyClientSnapshot(int spentAmount, int waterAmount, int timer) {
        spentSteam.setFluid(spentAmount <= 0 ? FluidStack.EMPTY
                : new FluidStack(ModFluids.SPENTSTEAM.get(), Math.min(spentAmount, spentSteam.getCapacity())));
        water.setFluid(waterAmount <= 0 ? FluidStack.EMPTY
                : new FluidStack(Fluids.WATER, Math.min(waterAmount, water.getCapacity())));
        waterTimer = Math.max(0, Math.min(timer, 20));
    }

    public IFluidHandler fluidHandler() { return fluidHandler; }
    public FluidTank spentSteamTank() { return spentSteam; }
    public FluidTank waterTank() { return water; }
    public int waterTimer() { return waterTimer; }
    public int throughput() { return throughput; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // Yes, the NBT keys are backwards. Saves already know.
        tag.put("water", spentSteam.writeToNBT(registries, new CompoundTag()));
        tag.put("steam", water.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("water")) spentSteam.readFromNBT(registries, tag.getCompound("water"));
        if (tag.contains("steam")) water.readFromNBT(registries, tag.getCompound("steam"));
        conformTanks();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("spentSteam", spentSteam.getFluidAmount());
        tag.putInt("water", water.getFluidAmount());
        tag.putInt("waterTimer", waterTimer);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        applyClientSnapshot(tag.getInt("spentSteam"), tag.getInt("water"), tag.getInt("waterTimer"));
    }

    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
