package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.HeatBoilerBlock;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.thermal.HeatSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

public final class HeatBoilerBlockEntity extends BlockEntity {
    public static final int MAX_HEAT = 3_200_000;
    public static final double DIFFUSION = 0.1D;
    public static final int INPUT_CAPACITY = 16_000;
    public static final int WATER_CAPACITY = INPUT_CAPACITY;
    public static final int STEAM_CAPACITY = 1_600_000;
    public static final int HOTOIL_CAPACITY = INPUT_CAPACITY;
    public static final int WATER_HEAT_PER_OPERATION = 200;
    public static final int STEAM_PER_OPERATION = 100;
    public static final int OIL_HEAT_PER_OPERATION = 10;
    public static final int HOTOIL_PER_OPERATION = 1;
    public static final int AIR_HEAT_PER_OPERATION = 5;
    public static final int AIRBLAST_PER_OPERATION = 1;
    public static final int AIRBLAST_CAPACITY = INPUT_CAPACITY;

    private FluidIdentifierItem.Selection inputSelection = FluidIdentifierItem.Selection.WATER;
    private final FluidTank input = new FluidTank(INPUT_CAPACITY, this::isValidInput) {
        @Override protected void onContentsChanged() { HeatBoilerBlockEntity.this.setChanged(); }
    };
    private final FluidTank output = new FluidTank(STEAM_CAPACITY, this::isValidOutput) {
        @Override protected void onContentsChanged() { HeatBoilerBlockEntity.this.setChanged(); }
    };
    private final IFluidHandler fluidHandler = new IFluidHandler() {
        @Override public int getTanks() { return 2; }
        @Override public FluidStack getFluidInTank(int tank) { return tank == 0 ? input.getFluid() : output.getFluid(); }
        @Override public int getTankCapacity(int tank) { return tank == 0 ? input.getCapacity() : output.getCapacity(); }
        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 && isValidInput(stack);
        }
        @Override public int fill(FluidStack resource, FluidAction action) { return input.fill(resource, action); }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            return output.drain(resource, action);
        }
        @Override public FluidStack drain(int maxDrain, FluidAction action) { return output.drain(maxDrain, action); }
    };

    private Component customName;
    private int heat;
    private boolean active;
    private boolean exploded;
    private int lastHeat = Integer.MIN_VALUE;
    private int lastInput = Integer.MIN_VALUE;
    private int lastOutput = Integer.MIN_VALUE;
    private FluidIdentifierItem.Selection lastInputSelection;
    private boolean lastActive;
    private boolean lastExploded;

    public HeatBoilerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE_BOILER.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, HeatBoilerBlockEntity boiler) {
        if (!level.isClientSide) boiler.serverTick((ServerLevel) level, pos, state);
    }

    private void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (exploded) {
            syncIfChanged(level, pos, state);
            return;
        }
        setupTanks();
        pullHeat(level, pos);
        HeatingStep step = heatingStep();
        int inputOps = input.getFluidAmount() / step.inputAmount();
        int outputOps = output.getSpace() / step.outputAmount();
        int heatOps = heat / step.heatRequired();
        int operations = Math.min(inputOps, Math.min(outputOps, heatOps));
        active = operations > 0;
        if (operations > 0) {
            input.drain(operations * step.inputAmount(), IFluidHandler.FluidAction.EXECUTE);
            output.fill(new FluidStack(step.output(), operations * step.outputAmount()),
                    IFluidHandler.FluidAction.EXECUTE);
            heat -= operations * step.heatRequired();
            if (level.random.nextInt(400) == 0) {
                level.playSound(null, pos.above(2), ModSounds.BOILER_GROAN.get(), SoundSource.BLOCKS,
                        0.5F, 1.0F);
            }
        }
        if (outputOps == 0) burst(level, pos);
        pushOutput(level, pos, state);
        syncIfChanged(level, pos, state);
    }

    private void setupTanks() {
        input.setCapacity(INPUT_CAPACITY);
        output.setCapacity(heatingStep().outputCapacity());
        if (!input.isEmpty() && !input.getFluid().getFluid().isSame(inputFluid())) {
            input.setFluid(FluidStack.EMPTY);
        }
        if (!output.isEmpty() && !output.getFluid().getFluid().isSame(outputFluid())) {
            output.setFluid(FluidStack.EMPTY);
        }
        if (input.getFluidAmount() > INPUT_CAPACITY) {
            input.setFluid(input.getFluid().copyWithAmount(INPUT_CAPACITY));
        }
        if (output.getFluidAmount() > output.getCapacity()) {
            output.setFluid(output.getFluid().copyWithAmount(output.getCapacity()));
        }
    }

    private void pullHeat(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos.below()) instanceof HeatSource source) {
            int difference = source.getHeatStored() - heat;
            if (difference == 0) return;
            if (difference > 0) {
                int amount = (int) Math.ceil(difference * DIFFUSION);
                amount = Math.min(amount, MAX_HEAT - heat);
                source.useUpHeat(amount);
                heat = Math.min(MAX_HEAT, heat + amount);
                return;
            }
        }
        heat = Math.max(heat - Math.max(heat / 1_000, 1), 0);
    }

    private void pushOutput(ServerLevel level, BlockPos pos, BlockState state) {
        if (output.isEmpty()) return;
        Direction facing = state.getValue(HeatBoilerBlock.FACING);
        Direction cross = facing.getClockWise();
        pushTo(level, pos.relative(cross, 2), cross);
        pushTo(level, pos.relative(cross, -2), cross.getOpposite());
        pushTo(level, pos.above(4), Direction.UP);
    }

    private void pushTo(ServerLevel level, BlockPos target, Direction outward) {
        if (output.isEmpty()) return;
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK,
                target, outward.getOpposite());
        if (handler == null) return;
        int accepted = handler.fill(output.getFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
        if (accepted > 0) output.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
    }

    private void burst(ServerLevel level, BlockPos pos) {
        if (exploded) return;
        exploded = true;
        active = false;
        if (getBlockState().getBlock() instanceof HeatBoilerBlock block) block.burst(level, pos);
        level.explode(null, pos.getX() + 0.5D, pos.getY() + 2D, pos.getZ() + 0.5D,
                5F, false, Level.ExplosionInteraction.NONE);
        setChanged();
    }

    private void syncIfChanged(ServerLevel level, BlockPos pos, BlockState state) {
        if (heat != lastHeat || input.getFluidAmount() != lastInput || output.getFluidAmount() != lastOutput
                || inputSelection != lastInputSelection
                || active != lastActive || exploded != lastExploded || level.getGameTime() % 20L == 0L) {
            lastHeat = heat;
            lastInput = input.getFluidAmount();
            lastOutput = output.getFluidAmount();
            lastInputSelection = inputSelection;
            lastActive = active;
            lastExploded = exploded;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
        setChanged();
    }

    public int fillWater(int amount) {
        return input.fill(new FluidStack(Fluids.WATER, amount), IFluidHandler.FluidAction.EXECUTE);
    }

    public int fillOil(int amount) {
        return input.fill(new FluidStack(ModFluids.OIL.get(), amount), IFluidHandler.FluidAction.EXECUTE);
    }

    public int fillAir(int amount) {
        return input.fill(new FluidStack(ModFluids.AIR.get(), amount), IFluidHandler.FluidAction.EXECUTE);
    }

    public boolean configureInput(FluidIdentifierItem.Selection selection) {
        if (!supportsInput(selection)) return false;
        if (selection == inputSelection) return true;
        inputSelection = selection;
        input.setFluid(FluidStack.EMPTY);
        setChanged();
        return true;
    }

    private static boolean supportsInput(FluidIdentifierItem.Selection selection) {
        return selection == FluidIdentifierItem.Selection.WATER
                || selection == FluidIdentifierItem.Selection.OIL
                || selection == FluidIdentifierItem.Selection.AIR;
    }

    private boolean isValidInput(FluidStack stack) {
        return !stack.isEmpty() && stack.getFluid().isSame(inputFluid());
    }

    private boolean isValidOutput(FluidStack stack) {
        return !stack.isEmpty() && stack.getFluid().isSame(outputFluid());
    }

    private Fluid inputFluid() {
        return switch (inputSelection) {
            case OIL -> ModFluids.OIL.get();
            case AIR -> ModFluids.AIR.get();
            default -> Fluids.WATER;
        };
    }

    private Fluid outputFluid() {
        return switch (inputSelection) {
            case OIL -> ModFluids.HOTOIL.get();
            case AIR -> ModFluids.AIRBLAST.get();
            default -> ModFluids.STEAM.get();
        };
    }

    private HeatingStep heatingStep() {
        return switch (inputSelection) {
            case OIL -> new HeatingStep(ModFluids.HOTOIL.get(), 1, HOTOIL_PER_OPERATION,
                    OIL_HEAT_PER_OPERATION, HOTOIL_CAPACITY);
            case AIR -> new HeatingStep(ModFluids.AIRBLAST.get(), 1, AIRBLAST_PER_OPERATION,
                    AIR_HEAT_PER_OPERATION, AIRBLAST_CAPACITY);
            default -> new HeatingStep(ModFluids.STEAM.get(), 1, STEAM_PER_OPERATION,
                    WATER_HEAT_PER_OPERATION, STEAM_CAPACITY);
        };
    }

    public IFluidHandler fluidHandler(@Nullable Direction side) { return fluidHandler; }
    public FluidTank inputTank() { return input; }
    public FluidTank outputTank() { return output; }
    public FluidIdentifierItem.Selection inputSelection() { return inputSelection; }
    public FluidIdentifierItem.Selection outputSelection() {
        return switch (inputSelection) {
            case OIL -> FluidIdentifierItem.Selection.HOTOIL;
            case AIR -> FluidIdentifierItem.Selection.AIRBLAST;
            default -> FluidIdentifierItem.Selection.STEAM;
        };
    }
    public int heat() { return heat; }
    public boolean active() { return active; }
    public boolean hasExploded() { return exploded; }
    public void setCustomName(Component name) { customName = name; setChanged(); }
    public Component displayName() { return customName != null ? customName : Component.translatable("block.hbm.machine_boiler"); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("heat", heat);
        tag.putBoolean("active", active);
        tag.putBoolean("exploded", exploded);
        tag.putString("inputType", inputSelection.id());
        tag.put("water", input.writeToNBT(registries, new CompoundTag()));
        tag.put("steam", output.writeToNBT(registries, new CompoundTag()));
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        heat = tag.getInt("heat");
        active = tag.getBoolean("active");
        exploded = tag.getBoolean("exploded");
        FluidIdentifierItem.Selection loadedSelection = tag.contains("inputType")
                ? FluidIdentifierItem.Selection.byId(tag.getString("inputType"))
                : FluidIdentifierItem.Selection.WATER;
        inputSelection = supportsInput(loadedSelection) ? loadedSelection : FluidIdentifierItem.Selection.WATER;
        if (tag.contains("water")) input.readFromNBT(registries, tag.getCompound("water"));
        if (tag.contains("steam")) output.readFromNBT(registries, tag.getCompound("steam"));
        setupTanks();
        customName = tag.contains("name") ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("heat", heat);
        tag.putBoolean("active", active);
        tag.putBoolean("exploded", exploded);
        tag.putString("inputType", inputSelection.id());
        tag.put("water", input.writeToNBT(registries, new CompoundTag()));
        tag.put("steam", output.writeToNBT(registries, new CompoundTag()));
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        heat = tag.getInt("heat");
        active = tag.getBoolean("active");
        exploded = tag.getBoolean("exploded");
        FluidIdentifierItem.Selection loadedSelection = tag.contains("inputType")
                ? FluidIdentifierItem.Selection.byId(tag.getString("inputType"))
                : FluidIdentifierItem.Selection.WATER;
        inputSelection = supportsInput(loadedSelection) ? loadedSelection : FluidIdentifierItem.Selection.WATER;
        if (tag.contains("water")) input.readFromNBT(registries, tag.getCompound("water"));
        if (tag.contains("steam")) output.readFromNBT(registries, tag.getCompound("steam"));
        setupTanks();
    }

    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private record HeatingStep(Fluid output, int inputAmount, int outputAmount,
                               int heatRequired, int outputCapacity) { }
}
