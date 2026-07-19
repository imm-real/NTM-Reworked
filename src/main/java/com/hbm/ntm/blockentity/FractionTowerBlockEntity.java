package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.FractionTowerBlock;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.recipe.FractionRecipes;
import com.hbm.ntm.recipe.FractionRecipes.FractionRecipe;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/** One fractioning tower floor and its three layers of suspicious soup. */
public final class FractionTowerBlockEntity extends BlockEntity {
    public static final int TANK_CAPACITY = 4_000;

    private FluidIdentifierItem.Selection selectedFluid = FluidIdentifierItem.Selection.HEAVYOIL;
    private final FluidTank input = changedTank(stack -> selectedFluid.accepts(stack.getFluid()));
    private final FluidTank outputLeft = changedTank(stack -> {
        FractionRecipe recipe = FractionRecipes.get(selectedFluid.fluid());
        return recipe != null && stack.getFluid().isSame(recipe.outputLeft().get());
    });
    private final FluidTank outputRight = changedTank(stack -> {
        FractionRecipe recipe = FractionRecipes.get(selectedFluid.fluid());
        return recipe != null && stack.getFluid().isSame(recipe.outputRight().get());
    });
    private final FluidTank[] tanks = {input, outputLeft, outputRight};
    private final IFluidHandler fluidHandler = new IFluidHandler() {
        @Override public int getTanks() { return tanks.length; }
        @Override public FluidStack getFluidInTank(int tank) { return tanks[tank].getFluid(); }
        @Override public int getTankCapacity(int tank) { return tanks[tank].getCapacity(); }
        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 && input.isFluidValid(stack);
        }
        @Override public int fill(FluidStack stack, FluidAction action) {
            return input.fill(stack, action);
        }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) {
            FluidStack drained = outputLeft.drain(stack, action);
            return drained.isEmpty() ? outputRight.drain(stack, action) : drained;
        }
        @Override public FluidStack drain(int amount, FluidAction action) {
            FluidStack drained = outputLeft.drain(amount, action);
            return drained.isEmpty() ? outputRight.drain(amount, action) : drained;
        }
    };

    private int[] lastAmounts = {-1, -1, -1};
    private FluidIdentifierItem.Selection lastSelected;

    public FractionTowerBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_FRACTION_TOWER.get(), position, state);
    }

    private FluidTank changedTank(java.util.function.Predicate<FluidStack> validator) {
        return new FluidTank(TANK_CAPACITY, validator) {
            @Override protected void onContentsChanged() { FractionTowerBlockEntity.this.setChanged(); }
        };
    }

    public static void tick(Level level, BlockPos position, BlockState state, FractionTowerBlockEntity tower) {
        if (!level.isClientSide) tower.serverTick((ServerLevel) level, position, state);
    }

    private void serverTick(ServerLevel level, BlockPos position, BlockState state) {
        conformTanks();
        if (level.getBlockEntity(position.above(3)) instanceof FractionTowerBlockEntity upper) {
            synchronizeWithUpper(upper);
        }
        if (level.getGameTime() % 10L == 0L) processFractionation();
        pushOutputs(level, position);
        syncIfChanged(level, position, state);
    }

    /** Steal from upstairs before doing chemistry. */
    public void synchronizeWithUpper(FractionTowerBlockEntity upper) {
        if (upper == this) return;
        if (upper.selectedFluid != selectedFluid) upper.configureInput(selectedFluid);
        transfer(input, upper.input, input.getFluidAmount());
        transfer(upper.outputLeft, outputLeft, upper.outputLeft.getFluidAmount());
        transfer(upper.outputRight, outputRight, upper.outputRight.getFluidAmount());
    }

    private static void transfer(FluidTank from, FluidTank to, int amount) {
        if (amount <= 0 || from.isEmpty()) return;
        FluidStack simulated = from.drain(amount, IFluidHandler.FluidAction.SIMULATE);
        int accepted = to.fill(simulated, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) return;
        FluidStack drained = from.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
        to.fill(drained, IFluidHandler.FluidAction.EXECUTE);
    }

    /** Ten-tick fractionation step. Public so tests can skip the waiting room. */
    public boolean processFractionation() {
        FractionRecipe recipe = FractionRecipes.get(selectedFluid.fluid());
        if (recipe == null || input.getFluidAmount() < FractionRecipe.INPUT_AMOUNT
                || outputLeft.getSpace() < recipe.outputLeftAmount()
                || outputRight.getSpace() < recipe.outputRightAmount()) return false;
        input.drain(FractionRecipe.INPUT_AMOUNT, IFluidHandler.FluidAction.EXECUTE);
        outputLeft.fill(new FluidStack(recipe.outputLeft().get(), recipe.outputLeftAmount()),
                IFluidHandler.FluidAction.EXECUTE);
        outputRight.fill(new FluidStack(recipe.outputRight().get(), recipe.outputRightAmount()),
                IFluidHandler.FluidAction.EXECUTE);
        setChanged();
        return true;
    }

    private void pushOutputs(ServerLevel level, BlockPos core) {
        for (FractionTowerBlock.Connection connection : FractionTowerBlock.connections(core)) {
            BlockPos target = connection.port().relative(connection.outward());
            IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK,
                    target, connection.outward().getOpposite());
            if (handler == null) continue;
            pushTank(handler, outputLeft);
            pushTank(handler, outputRight);
        }
    }

    private static void pushTank(IFluidHandler handler, FluidTank tank) {
        if (tank.isEmpty()) return;
        int accepted = handler.fill(tank.getFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
        if (accepted > 0) tank.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
    }

    public void configureInput(FluidIdentifierItem.Selection selection) {
        if (selection == null || FractionRecipes.get(selection.fluid()) == null) {
            selection = FluidIdentifierItem.Selection.NONE;
        }
        if (selectedFluid == selection) return;
        selectedFluid = selection;
        for (FluidTank tank : tanks) tank.setFluid(FluidStack.EMPTY);
        setChanged();
    }

    private void conformTanks() {
        FractionRecipe recipe = FractionRecipes.get(selectedFluid.fluid());
        if (recipe == null) {
            if (selectedFluid != FluidIdentifierItem.Selection.NONE) selectedFluid = FluidIdentifierItem.Selection.NONE;
            for (FluidTank tank : tanks) if (!tank.isEmpty()) tank.setFluid(FluidStack.EMPTY);
            return;
        }
        if (!input.isEmpty() && !selectedFluid.accepts(input.getFluid().getFluid())) input.setFluid(FluidStack.EMPTY);
        if (!outputLeft.isEmpty() && !outputLeft.getFluid().getFluid().isSame(recipe.outputLeft().get())) {
            outputLeft.setFluid(FluidStack.EMPTY);
        }
        if (!outputRight.isEmpty() && !outputRight.getFluid().getFluid().isSame(recipe.outputRight().get())) {
            outputRight.setFluid(FluidStack.EMPTY);
        }
    }

    private void syncIfChanged(ServerLevel level, BlockPos position, BlockState state) {
        int[] amounts = Arrays.stream(tanks).mapToInt(FluidTank::getFluidAmount).toArray();
        if (!Arrays.equals(amounts, lastAmounts) || selectedFluid != lastSelected
                || level.getGameTime() % 50L == 0L) {
            lastAmounts = amounts;
            lastSelected = selectedFluid;
            level.sendBlockUpdated(position, state, state, Block.UPDATE_CLIENTS);
        }
        setChanged();
    }

    public FluidIdentifierItem.Selection configuredFluid() { return selectedFluid; }
    public FluidTank tank(int index) { return tanks[index]; }
    public IFluidHandler fluidHandler() { return fluidHandler; }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("selectedFluid", selectedFluid.id());
        for (int index = 0; index < tanks.length; index++) {
            tag.put("tank" + index, tanks[index].writeToNBT(registries, new CompoundTag()));
        }
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        selectedFluid = tag.contains("selectedFluid")
                ? FluidIdentifierItem.Selection.byId(tag.getString("selectedFluid"))
                : FluidIdentifierItem.Selection.HEAVYOIL;
        for (int index = 0; index < tanks.length; index++) {
            if (tag.contains("tank" + index)) tanks[index].readFromNBT(registries, tag.getCompound("tank" + index));
        }
        conformTanks();
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("selectedFluid", selectedFluid.id());
        for (int index = 0; index < tanks.length; index++) {
            tag.put("tank" + index, tanks[index].writeToNBT(registries, new CompoundTag()));
        }
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        selectedFluid = FluidIdentifierItem.Selection.byId(tag.getString("selectedFluid"));
        for (int index = 0; index < tanks.length; index++) {
            if (tag.contains("tank" + index)) tanks[index].readFromNBT(registries, tag.getCompound("tank" + index));
        }
        conformTanks();
    }

    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
