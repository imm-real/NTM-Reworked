package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.IndustrialTurbineBlock;
import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.energy.HeProvider;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.network.IndustrialTurbineStatePayload;
import com.hbm.ntm.registry.ModBlockEntities;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Source Industrial Steam Turbine, including its 20% throughput and flywheel buffer. */
public final class IndustrialTurbineBlockEntity extends BlockEntity implements HeProvider {
    public static final double CONSUMPTION_PERCENT = 0.2D;
    public static final long FLYWHEEL_MAX_ENERGY = 50_000_000L;

    private SteamGrade grade = SteamGrade.STEAM;
    private final FluidTank input = new FluidTank(inputCapacity(),
            stack -> grade.input().accepts(stack.getFluid())) {
        @Override protected void onContentsChanged() { IndustrialTurbineBlockEntity.this.setChanged(); }
    };
    private final FluidTank output = new FluidTank(outputCapacity(),
            stack -> grade.output().accepts(stack.getFluid())) {
        @Override protected void onContentsChanged() { IndustrialTurbineBlockEntity.this.setChanged(); }
    };
    private final IFluidHandler portFluidHandler = new IFluidHandler() {
        @Override public int getTanks() { return 2; }
        @Override public FluidStack getFluidInTank(int tank) {
            return tank == 0 ? input.getFluid() : tank == 1 ? output.getFluid() : FluidStack.EMPTY;
        }
        @Override public int getTankCapacity(int tank) {
            return tank == 0 ? input.getCapacity() : tank == 1 ? output.getCapacity() : 0;
        }
        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 && grade.input().accepts(stack.getFluid());
        }
        @Override public int fill(FluidStack resource, FluidAction action) {
            return isFluidValid(0, resource) ? input.fill(resource, action) : 0;
        }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            return grade.output().accepts(resource.getFluid()) ? output.drain(resource, action) : FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int maxDrain, FluidAction action) { return output.drain(maxDrain, action); }
    };

    private long powerBuffer;
    private long maxPower;
    private long lastPowerTarget;
    private long flywheelEnergy;
    private double spin;
    private float rotor;
    private float lastRotor;
    private boolean operational;

    public IndustrialTurbineBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_INDUSTRIAL_TURBINE.get(), position, state);
    }

    public static void tick(Level level, BlockPos position, BlockState state,
                            IndustrialTurbineBlockEntity turbine) {
        if (level.isClientSide) turbine.clientTick();
        else turbine.serverTick((ServerLevel) level, position, state);
    }

    private void serverTick(ServerLevel level, BlockPos position, BlockState state) {
        powerBuffer = 0L;
        operational = false;
        conformTanks();

        int inputShare = Math.min((int) Math.ceil(input.getFluidAmount() * CONSUMPTION_PERCENT),
                input.getFluidAmount());
        int inputOperations = inputShare / grade.inputAmount();
        int outputOperations = output.getSpace() / grade.outputAmount();
        int operations = Math.min(inputOperations, outputOperations);
        if (operations > 0) {
            input.drain(operations * grade.inputAmount(), IFluidHandler.FluidAction.EXECUTE);
            output.fill(new FluidStack(grade.output().fluid(), operations * grade.outputAmount()),
                    IFluidHandler.FluidAction.EXECUTE);
            long generated = (long) (operations * (double) grade.heatEnergy()
                    * HbmConfig.INDUSTRIAL_TURBINE_EFFICIENCY.get());
            int maximumOperations = (int) Math.ceil(
                    input.getCapacity() * CONSUMPTION_PERCENT / grade.inputAmount());
            maxPower = (long) (maximumOperations * (double) grade.heatEnergy()
                    * HbmConfig.INDUSTRIAL_TURBINE_EFFICIENCY.get());
            flywheelEnergy += generated;
            operational = true;
        }

        spin = (double) flywheelEnergy / FLYWHEEL_MAX_ENERGY;
        lastPowerTarget = Math.min((long) (Math.max(spin, 0.05D) * maxPower), flywheelEnergy);
        flywheelEnergy -= lastPowerTarget;
        powerBuffer = lastPowerTarget;

        PacketDistributor.sendToPlayersNear(level, null, position.getX() + 0.5D,
                position.getY() + 1.5D, position.getZ() + 0.5D, 150.0D,
                new IndustrialTurbineStatePayload(position, powerBuffer, flywheelEnergy, spin,
                        grade.ordinal(), input.getFluidAmount(), output.getFluidAmount(), operational));

        Direction facing = state.getValue(IndustrialTurbineBlock.FACING);
        IndustrialTurbineBlock.Connection power = IndustrialTurbineBlock.powerConnection(position, facing);
        if (powerBuffer > 0L) tryProvide(level, power.target(), power.outward());
        for (IndustrialTurbineBlock.Connection connection
                : IndustrialTurbineBlock.fluidConnections(position, facing)) {
            exchangeFluids(level, connection.target(), connection.outward());
        }
        setChanged();
    }

    private void exchangeFluids(ServerLevel level, BlockPos target, Direction outward) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK,
                target, outward.getOpposite());
        if (handler == null) return;

        // TileEntityTurbineBase provided the cooled output before subscribing for fresh input.
        if (!output.isEmpty()) {
            int accepted = handler.fill(output.getFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
            if (accepted > 0) output.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
        }

        if (input.getSpace() > 0) {
            FluidStack request = new FluidStack(grade.input().fluid(), input.getSpace());
            FluidStack available = handler.drain(request, IFluidHandler.FluidAction.SIMULATE);
            if (!available.isEmpty() && grade.input().accepts(available.getFluid())) {
                int accepted = input.fill(available, IFluidHandler.FluidAction.SIMULATE);
                if (accepted > 0) {
                    FluidStack drained = handler.drain(new FluidStack(grade.input().fluid(), accepted),
                            IFluidHandler.FluidAction.EXECUTE);
                    input.fill(drained, IFluidHandler.FluidAction.EXECUTE);
                }
            }
        }
    }

    private void clientTick() {
        lastRotor = rotor;
        float speed = spin >= 0.5D ? 30.0F : (float) (Math.sqrt(Math.max(spin, 0D) * 2D) * 30D);
        rotor += speed;
        if (rotor >= 360.0F) {
            rotor -= 360.0F;
            lastRotor -= 360.0F;
        }
    }

    public void cycleSteamGrade() {
        if (operational) return;
        grade = SteamGrade.values()[(grade.ordinal() + 1) % SteamGrade.values().length];
        input.setFluid(FluidStack.EMPTY);
        output.setFluid(FluidStack.EMPTY);
        conformTanks();
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            PacketDistributor.sendToPlayersNear(serverLevel, null, worldPosition.getX() + 0.5D,
                    worldPosition.getY() + 1.5D, worldPosition.getZ() + 0.5D, 150.0D,
                    new IndustrialTurbineStatePayload(worldPosition, powerBuffer, flywheelEnergy, spin,
                            grade.ordinal(), 0, 0, false));
        }
    }

    private void conformTanks() {
        input.setCapacity(inputCapacity());
        output.setCapacity(outputCapacity());
        if (!input.isEmpty() && !grade.input().accepts(input.getFluid().getFluid())) input.setFluid(FluidStack.EMPTY);
        if (!output.isEmpty() && !grade.output().accepts(output.getFluid().getFluid())) output.setFluid(FluidStack.EMPTY);
    }

    private int inputCapacity() {
        return scaledCapacity(HbmConfig.INDUSTRIAL_TURBINE_INPUT_CAPACITY.get());
    }

    private int outputCapacity() {
        return scaledCapacity(HbmConfig.INDUSTRIAL_TURBINE_OUTPUT_CAPACITY.get());
    }

    private int scaledCapacity(int base) {
        int divisor = switch (grade) {
            case STEAM -> 1;
            case DENSE -> 10;
            case SUPER_DENSE -> 100;
            case ULTRA_DENSE -> 1_000;
        };
        return Math.max(base / divisor, 1);
    }

    public void applyClientSnapshot(long power, long flywheel, double spin, int gradeIndex,
                                    int inputAmount, int outputAmount, boolean operational) {
        grade = SteamGrade.byIndex(gradeIndex);
        conformTanks();
        powerBuffer = Math.max(power, 0L);
        flywheelEnergy = Math.max(flywheel, 0L);
        this.spin = Math.max(spin, 0D);
        this.operational = operational;
        input.setFluid(inputAmount <= 0 ? FluidStack.EMPTY
                : new FluidStack(grade.input().fluid(), Math.min(inputAmount, input.getCapacity())));
        output.setFluid(outputAmount <= 0 ? FluidStack.EMPTY
                : new FluidStack(grade.output().fluid(), Math.min(outputAmount, output.getCapacity())));
    }

    public IFluidHandler portFluidHandler() { return portFluidHandler; }
    public FluidTank inputTank() { return input; }
    public FluidTank outputTank() { return output; }
    public SteamGrade grade() { return grade; }
    public double spin() { return spin; }
    public float rotor() { return rotor; }
    public float lastRotor() { return lastRotor; }
    public boolean operational() { return operational; }
    public long flywheelEnergy() { return flywheelEnergy; }
    public long lastPowerTarget() { return lastPowerTarget; }

    public AABB renderBounds() {
        Direction facing = getBlockState().hasProperty(IndustrialTurbineBlock.FACING)
                ? getBlockState().getValue(IndustrialTurbineBlock.FACING) : Direction.NORTH;
        AABB bounds = new AABB(worldPosition);
        for (BlockPos part : IndustrialTurbineBlock.partPositions(worldPosition, facing)) {
            bounds = bounds.minmax(new AABB(part));
        }
        return bounds.inflate(0.25D);
    }

    @Override public boolean canConnect(Direction side) {
        if (side == null || !getBlockState().hasProperty(IndustrialTurbineBlock.FACING)) return false;
        return side == getBlockState().getValue(IndustrialTurbineBlock.FACING).getOpposite();
    }
    @Override public long getPower() { return Math.max(powerBuffer, 0L); }
    @Override public void setPower(long power) { powerBuffer = Math.max(power, 0L); }
    @Override public long getMaxPower() { return Math.max(powerBuffer, 0L); }
    @Override public boolean isHeLoaded() { return hasLevel() && !isRemoved(); }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("steamGrade", grade.id());
        tag.putLong("power", powerBuffer);
        tag.putLong("lastPowerTarget", lastPowerTarget);
        tag.putLong("flywheel_energy", flywheelEnergy);
        tag.putLong("maxPower", maxPower);
        tag.putDouble("spin", spin);
        tag.put("input", input.writeToNBT(registries, new CompoundTag()));
        tag.put("output", output.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        grade = SteamGrade.byId(tag.getString("steamGrade"));
        powerBuffer = Math.max(tag.getLong("power"), 0L);
        lastPowerTarget = Math.max(tag.getLong("lastPowerTarget"), 0L);
        flywheelEnergy = Math.max(tag.getLong("flywheel_energy"), 0L);
        maxPower = Math.max(tag.getLong("maxPower"), 0L);
        spin = Math.max(tag.getDouble("spin"), 0D);
        conformTanks();
        if (tag.contains("input")) input.readFromNBT(registries, tag.getCompound("input"));
        if (tag.contains("output")) output.readFromNBT(registries, tag.getCompound("output"));
        conformTanks();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("steamGrade", grade.id());
        tag.putLong("power", powerBuffer);
        tag.putLong("flywheel", flywheelEnergy);
        tag.putDouble("spin", spin);
        tag.putInt("inputAmount", input.getFluidAmount());
        tag.putInt("outputAmount", output.getFluidAmount());
        tag.putBoolean("operational", operational);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        applyClientSnapshot(tag.getLong("power"), tag.getLong("flywheel"), tag.getDouble("spin"),
                SteamGrade.byId(tag.getString("steamGrade")).ordinal(), tag.getInt("inputAmount"),
                tag.getInt("outputAmount"), tag.getBoolean("operational"));
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public enum SteamGrade {
        STEAM("steam", FluidIdentifierItem.Selection.STEAM, 100,
                FluidIdentifierItem.Selection.SPENTSTEAM, 1, 200),
        DENSE("hotsteam", FluidIdentifierItem.Selection.HOTSTEAM, 1,
                FluidIdentifierItem.Selection.STEAM, 10, 2),
        SUPER_DENSE("superhotsteam", FluidIdentifierItem.Selection.SUPERHOTSTEAM, 1,
                FluidIdentifierItem.Selection.HOTSTEAM, 10, 18),
        ULTRA_DENSE("ultrahotsteam", FluidIdentifierItem.Selection.ULTRAHOTSTEAM, 1,
                FluidIdentifierItem.Selection.SUPERHOTSTEAM, 10, 120);

        private final String id;
        private final FluidIdentifierItem.Selection input;
        private final int inputAmount;
        private final FluidIdentifierItem.Selection output;
        private final int outputAmount;
        private final int heatEnergy;

        SteamGrade(String id, FluidIdentifierItem.Selection input, int inputAmount,
                   FluidIdentifierItem.Selection output, int outputAmount, int heatEnergy) {
            this.id = id;
            this.input = input;
            this.inputAmount = inputAmount;
            this.output = output;
            this.outputAmount = outputAmount;
            this.heatEnergy = heatEnergy;
        }

        public String id() { return id; }
        public FluidIdentifierItem.Selection input() { return input; }
        public int inputAmount() { return inputAmount; }
        public FluidIdentifierItem.Selection output() { return output; }
        public int outputAmount() { return outputAmount; }
        public int heatEnergy() { return heatEnergy; }

        public static SteamGrade byId(String id) {
            for (SteamGrade grade : values()) if (grade.id.equals(id)) return grade;
            return STEAM;
        }

        public static SteamGrade byIndex(int index) {
            return values()[Mth.clamp(index, 0, values().length - 1)];
        }
    }
}
