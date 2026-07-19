package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.CrackingTowerBlock;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.recipe.CrackingRecipes;
import com.hbm.ntm.recipe.CrackingRecipes.CrackingRecipe;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/** Source Catalytic Cracker fluid-only processing core. */
public final class CrackingTowerBlockEntity extends BlockEntity {
    public static final int INPUT_CAPACITY = 4_000;
    public static final int STEAM_CAPACITY = 8_000;
    public static final int OUTPUT_CAPACITY = 4_000;
    public static final int SPENT_STEAM_CAPACITY = 800;
    public static final int STEAM_PER_OPERATION = 200;
    public static final int SPENT_STEAM_PER_OPERATION = 2;
    public static final int OPERATIONS_PER_CYCLE = 2;

    private FluidIdentifierItem.Selection selectedFluid = FluidIdentifierItem.Selection.NONE;
    private final FluidTank input = changedTank(INPUT_CAPACITY,
            stack -> selectedFluid.accepts(stack.getFluid()));
    private final FluidTank steam = changedTank(STEAM_CAPACITY,
            stack -> stack.getFluid().isSame(ModFluids.STEAM.get()));
    private final FluidTank outputLeft = changedTank(OUTPUT_CAPACITY,
            stack -> stack.getFluid().isSame(ModFluids.PETROLEUM.get()));
    private final FluidTank outputRight = changedTank(OUTPUT_CAPACITY,
            stack -> stack.getFluid().isSame(ModFluids.UNSATURATEDS.get()));
    private final FluidTank spentSteam = changedTank(SPENT_STEAM_CAPACITY,
            stack -> stack.getFluid().isSame(ModFluids.SPENTSTEAM.get()));
    private final FluidTank[] tanks = {input, steam, outputLeft, outputRight, spentSteam};
    private final IFluidHandler fluidHandler = new IFluidHandler() {
        @Override public int getTanks() { return tanks.length; }
        @Override public FluidStack getFluidInTank(int tank) { return tanks[tank].getFluid(); }
        @Override public int getTankCapacity(int tank) { return tanks[tank].getCapacity(); }
        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            return tank >= 0 && tank < 2 && tanks[tank].isFluidValid(stack);
        }
        @Override public int fill(FluidStack stack, FluidAction action) {
            if (input.isFluidValid(stack)) return input.fill(stack, action);
            return steam.isFluidValid(stack) ? steam.fill(stack, action) : 0;
        }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) {
            for (int index = 2; index < tanks.length; index++) {
                FluidStack drained = tanks[index].drain(stack, action);
                if (!drained.isEmpty()) return drained;
            }
            return FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int amount, FluidAction action) {
            for (int index = 2; index < tanks.length; index++) {
                FluidStack drained = tanks[index].drain(amount, action);
                if (!drained.isEmpty()) return drained;
            }
            return FluidStack.EMPTY;
        }
    };

    private int[] lastAmounts = {-1, -1, -1, -1, -1};
    private FluidIdentifierItem.Selection lastSelected;

    public CrackingTowerBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_CATALYTIC_CRACKER.get(), position, state);
    }

    private FluidTank changedTank(int capacity, java.util.function.Predicate<FluidStack> validator) {
        return new FluidTank(capacity, validator) {
            @Override protected void onContentsChanged() { CrackingTowerBlockEntity.this.setChanged(); }
        };
    }

    public static void tick(Level level, BlockPos position, BlockState state, CrackingTowerBlockEntity tower) {
        if (!level.isClientSide) tower.serverTick((ServerLevel) level, position, state);
    }

    private void serverTick(ServerLevel level, BlockPos position, BlockState state) {
        conformTanks();
        if (level.getGameTime() % 5L == 0L) processCrackingCycle();
        if (level.getGameTime() % 10L == 0L) pushOutputs(level, position, state.getValue(CrackingTowerBlock.FACING));
        syncIfChanged(level, position, state);
    }

    /** Two batches every five ticks. Public so tests can skip the clock. */
    public int processCrackingCycle() {
        CrackingRecipe recipe = CrackingRecipes.get(selectedFluid.fluid());
        if (recipe == null) return 0;
        int operations = 0;
        for (int operation = 0; operation < OPERATIONS_PER_CYCLE; operation++) {
            if (input.getFluidAmount() < recipe.inputAmount()
                    || steam.getFluidAmount() < STEAM_PER_OPERATION
                    || outputLeft.getSpace() < recipe.outputLeftAmount()
                    || outputRight.getSpace() < recipe.outputRightAmount()
                    || spentSteam.getSpace() < SPENT_STEAM_PER_OPERATION) break;
            input.drain(recipe.inputAmount(), IFluidHandler.FluidAction.EXECUTE);
            steam.drain(STEAM_PER_OPERATION, IFluidHandler.FluidAction.EXECUTE);
            outputLeft.fill(new FluidStack(recipe.outputLeft().get(), recipe.outputLeftAmount()),
                    IFluidHandler.FluidAction.EXECUTE);
            outputRight.fill(new FluidStack(recipe.outputRight().get(), recipe.outputRightAmount()),
                    IFluidHandler.FluidAction.EXECUTE);
            spentSteam.fill(new FluidStack(ModFluids.SPENTSTEAM.get(), SPENT_STEAM_PER_OPERATION),
                    IFluidHandler.FluidAction.EXECUTE);
            operations++;
        }
        if (operations > 0) setChanged();
        return operations;
    }

    private void pushOutputs(ServerLevel level, BlockPos core, Direction facing) {
        for (CrackingTowerBlock.Connection connection : CrackingTowerBlock.connections(core, facing)) {
            BlockPos target = connection.port().relative(connection.outward());
            IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK,
                    target, connection.outward().getOpposite());
            if (handler == null) continue;
            for (int index = 2; index < tanks.length; index++) {
                FluidTank tank = tanks[index];
                if (tank.isEmpty()) continue;
                int accepted = handler.fill(tank.getFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
                if (accepted > 0) tank.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }

    public void configureInput(FluidIdentifierItem.Selection selection) {
        if (selection == null) selection = FluidIdentifierItem.Selection.NONE;
        if (selectedFluid == selection) return;
        selectedFluid = selection;
        input.setFluid(FluidStack.EMPTY);
        conformTanks();
        setChanged();
    }

    private void conformTanks() {
        if (!input.isEmpty() && !selectedFluid.accepts(input.getFluid().getFluid())) input.setFluid(FluidStack.EMPTY);
        CrackingRecipe recipe = CrackingRecipes.get(selectedFluid.fluid());
        if (recipe == null) {
            outputLeft.setFluid(FluidStack.EMPTY);
            outputRight.setFluid(FluidStack.EMPTY);
            spentSteam.setFluid(FluidStack.EMPTY);
            return;
        }
        if (!outputLeft.isEmpty() && !outputLeft.getFluid().getFluid().isSame(recipe.outputLeft().get())) {
            outputLeft.setFluid(FluidStack.EMPTY);
        }
        if (!outputRight.isEmpty() && !outputRight.getFluid().getFluid().isSame(recipe.outputRight().get())) {
            outputRight.setFluid(FluidStack.EMPTY);
        }
        if (!spentSteam.isEmpty() && !spentSteam.getFluid().getFluid().isSame(ModFluids.SPENTSTEAM.get())) {
            spentSteam.setFluid(FluidStack.EMPTY);
        }
    }

    private void syncIfChanged(ServerLevel level, BlockPos position, BlockState state) {
        int[] amounts = Arrays.stream(tanks).mapToInt(FluidTank::getFluidAmount).toArray();
        if (!Arrays.equals(amounts, lastAmounts) || selectedFluid != lastSelected
                || level.getGameTime() % 25L == 0L) {
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
                : FluidIdentifierItem.Selection.NONE;
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
