package com.hbm.ntm.blockentity;

import com.hbm.ntm.inventory.CombinationOvenMenu;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.item.InfiniteFluidBarrelItem;
import com.hbm.ntm.pollution.PollutionData;
import com.hbm.ntm.recipe.CombinationOvenRecipes;
import com.hbm.ntm.recipe.CombinationOvenRecipes.Recipe;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.thermal.HeatSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/** Combination oven loop: heat, progress, solids, liquids, repeat. */
public final class CombinationOvenBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int SLOT_COUNT = 4;
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    public static final int CONTAINER_INPUT = 2;
    public static final int CONTAINER_OUTPUT = 3;
    public static final int PROCESS_TIME = 20_000;
    public static final int MAX_HEAT = 100_000;
    public static final int TANK_CAPACITY = 24_000;
    public static final int SMOKE_CAPACITY = 50;
    public static final double DIFFUSION = 0.25D;
    private static final float SOOT_PER_SECOND = 3F / 25F;
    private static final int[] AUTOMATION_SLOTS = {INPUT, OUTPUT};

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final FluidTank tank = new FluidTank(TANK_CAPACITY) {
        @Override protected void onContentsChanged() { CombinationOvenBlockEntity.this.setChanged(); }
    };
    private final IFluidHandler fluidHandler = new IFluidHandler() {
        @Override public int getTanks() { return 2; }
        @Override public FluidStack getFluidInTank(int index) {
            if (index == 0) return tank.getFluid();
            return index == 1 && smoke > 0 ? new FluidStack(ModFluids.SMOKE.get(), smoke) : FluidStack.EMPTY;
        }
        @Override public int getTankCapacity(int index) {
            return index == 0 ? TANK_CAPACITY : index == 1 ? SMOKE_CAPACITY : 0;
        }
        @Override public boolean isFluidValid(int index, FluidStack stack) { return false; }
        @Override public int fill(FluidStack resource, FluidAction action) { return 0; }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return FluidStack.EMPTY;
            if (resource.is(ModFluids.SMOKE.get())) return drainSmoke(resource.getAmount(), action);
            return tank.drain(resource, action);
        }
        @Override public FluidStack drain(int maxDrain, FluidAction action) {
            FluidStack drained = tank.drain(maxDrain, action);
            return drained.isEmpty() ? drainSmoke(maxDrain, action) : drained;
        }
    };
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> heat;
                case 2 -> tank.getFluidAmount();
                case 3 -> fluidSelection().ordinal();
                case 4 -> wasOn ? 1 : 0;
                default -> 0;
            };
        }

        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> progress = value;
                case 1 -> heat = value;
                case 2 -> { }
                case 3 -> { }
                case 4 -> wasOn = value != 0;
                default -> { }
            }
        }

        @Override public int getCount() { return 5; }
    };

    private int progress;
    private int heat;
    private int smoke;
    private boolean wasOn;
    private Component customName;
    private int lastProgress = Integer.MIN_VALUE;
    private int lastHeat = Integer.MIN_VALUE;
    private int lastFluid = Integer.MIN_VALUE;
    private int lastFluidType = Integer.MIN_VALUE;
    private boolean lastWasOn;
    private boolean hasSynced;

    public CombinationOvenBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.FURNACE_COMBINATION.get(), position, state);
    }

    public static void tick(Level level, BlockPos position, BlockState state, CombinationOvenBlockEntity oven) {
        if (level.isClientSide) oven.clientTick(level, position);
        else oven.serverTick((ServerLevel) level, position, state);
    }

    private void serverTick(ServerLevel level, BlockPos position, BlockState state) {
        tryPullHeat();
        if (level.getGameTime() % 20L == 0L) pushOutput(level, position);
        fillContainerFromTank();
        wasOn = false;

        Recipe recipe = CombinationOvenRecipes.find(items.get(INPUT));
        if (canProcess(recipe)) {
            int burn = heat / 100;
            if (burn > 0) {
                wasOn = true;
                progress += burn;
                heat -= burn;
                if (progress >= PROCESS_TIME) {
                    finish(recipe);
                    progress -= PROCESS_TIME;
                }
                igniteAbove(level, position);
                if (level.getGameTime() % 10L == 0L) {
                    level.playSound(null, position.above(), ModSounds.FURNACE_COMBINATION.get(),
                            SoundSource.BLOCKS, 0.25F, 0.5F);
                }
                if (level.getGameTime() % 20L == 0L) {
                    polluteSoot(level, position, SOOT_PER_SECOND);
                }
            }
        } else {
            progress = 0;
        }

        setChanged();
        syncIfChanged(level, position, state);
    }

    private boolean canProcess(@Nullable Recipe recipe) {
        if (recipe == null || !recipe.input().test(items.get(INPUT))) return false;
        ItemStack result = recipe.result();
        ItemStack existing = items.get(OUTPUT);
        if (!existing.isEmpty() && (!ItemStack.isSameItemSameComponents(existing, result)
                || existing.getCount() + result.getCount() > existing.getMaxStackSize())) return false;
        if (recipe.resultFluid() == null) return true;
        return tank.isEmpty() || tank.getFluid().is(recipe.resultFluid())
                && tank.getFluidAmount() + recipe.fluidAmount() <= TANK_CAPACITY;
    }

    private void finish(Recipe recipe) {
        ItemStack result = recipe.result();
        if (items.get(OUTPUT).isEmpty()) items.set(OUTPUT, result);
        else items.get(OUTPUT).grow(result.getCount());
        if (recipe.resultFluid() != null && recipe.fluidAmount() > 0) {
            tank.fill(new FluidStack(recipe.resultFluid(), recipe.fluidAmount()),
                    IFluidHandler.FluidAction.EXECUTE);
        }
        items.get(INPUT).shrink(1);
        if (items.get(INPUT).isEmpty()) items.set(INPUT, ItemStack.EMPTY);
    }

    private void tryPullHeat() {
        if (level == null || heat >= MAX_HEAT) return;
        BlockEntity below = level.getBlockEntity(worldPosition.below());
        if (below instanceof HeatSource source) {
            int difference = source.getHeatStored() - heat;
            if (difference == 0) return;
            if (difference > 0) {
                int transfer = (int) Math.ceil(difference * DIFFUSION);
                source.useUpHeat(transfer);
                heat = Math.min(MAX_HEAT, heat + transfer);
                return;
            }
        }
        heat = Math.max(heat - Math.max(heat / 1_000, 1), 0);
    }

    private void fillContainerFromTank() {
        ItemStack input = items.get(CONTAINER_INPUT);
        if (input.isEmpty() || tank.isEmpty()) return;
        if (InfiniteFluidBarrelItem.is(input)) {
            if (InfiniteFluidBarrelItem.discardTank(tank) > 0) setChanged();
            return;
        }
        FluidActionResult simulated = FluidUtil.tryFillContainer(input.copyWithCount(1), tank,
                Integer.MAX_VALUE, null, false);
        if (!simulated.isSuccess() || !canMerge(items.get(CONTAINER_OUTPUT), simulated.getResult())) return;
        FluidActionResult executed = FluidUtil.tryFillContainer(input.copyWithCount(1), tank,
                Integer.MAX_VALUE, null, true);
        if (!executed.isSuccess()) return;
        input.shrink(1);
        if (input.isEmpty()) items.set(CONTAINER_INPUT, ItemStack.EMPTY);
        if (items.get(CONTAINER_OUTPUT).isEmpty()) items.set(CONTAINER_OUTPUT, executed.getResult());
        else items.get(CONTAINER_OUTPUT).grow(executed.getResult().getCount());
    }

    private static boolean canMerge(ItemStack target, ItemStack addition) {
        return !addition.isEmpty() && (target.isEmpty()
                || ItemStack.isSameItemSameComponents(target, addition)
                && target.getCount() + addition.getCount() <= target.getMaxStackSize());
    }

    private void pushOutput(ServerLevel level, BlockPos core) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Direction across = direction.getClockWise();
            for (int y = 0; y <= 1; y++) for (int offset = -1; offset <= 1; offset++) {
                pushTo(level, core.relative(direction, 2).relative(across, offset).above(y), direction);
            }
        }
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            pushTo(level, core.offset(x, 2, z), Direction.UP);
        }
    }

    private void pushTo(ServerLevel level, BlockPos target, Direction outward) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK,
                target, outward.getOpposite());
        if (handler == null) return;
        if (!tank.isEmpty()) {
            int accepted = handler.fill(tank.getFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
            if (accepted > 0) tank.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
        }
        if (smoke > 0) {
            int accepted = handler.fill(new FluidStack(ModFluids.SMOKE.get(), smoke),
                    IFluidHandler.FluidAction.EXECUTE);
            smoke -= Math.min(Math.max(accepted, 0), smoke);
        }
    }

    private FluidStack drainSmoke(int maxDrain, IFluidHandler.FluidAction action) {
        int drained = Math.min(Math.max(maxDrain, 0), smoke);
        if (drained <= 0) return FluidStack.EMPTY;
        FluidStack result = new FluidStack(ModFluids.SMOKE.get(), drained);
        if (action.execute()) {
            smoke -= drained;
            setChanged();
        }
        return result;
    }

    private void polluteSoot(ServerLevel level, BlockPos position, float amount) {
        smoke += (int) Math.ceil(amount * 100F);
        if (smoke > SMOKE_CAPACITY) {
            int overflow = smoke - SMOKE_CAPACITY;
            smoke = SMOKE_CAPACITY;
            PollutionData.get(level).increment(position, PollutionData.Type.SOOT, overflow / 100F);
        }
    }

    private static void igniteAbove(ServerLevel level, BlockPos position) {
        AABB area = new AABB(position.getX() - 0.5D, position.getY() + 2D, position.getZ() - 0.5D,
                position.getX() + 1.5D, position.getY() + 4D, position.getZ() + 1.5D);
        for (Entity entity : level.getEntities((Entity) null, area, entity -> true)) {
            entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), 100));
        }
    }

    private void clientTick(Level level, BlockPos position) {
        if (wasOn && level.random.nextInt(15) == 0) {
            level.addParticle(ParticleTypes.LAVA,
                    position.getX() + 0.5D + level.random.nextGaussian() * 0.5D,
                    position.getY() + 2D,
                    position.getZ() + 0.5D + level.random.nextGaussian() * 0.5D,
                    0D, 0D, 0D);
        }
    }

    private void syncIfChanged(ServerLevel level, BlockPos position, BlockState state) {
        int fluidType = fluidSelection().ordinal();
        if (!hasSynced || progress != lastProgress || heat != lastHeat
                || tank.getFluidAmount() != lastFluid || fluidType != lastFluidType || wasOn != lastWasOn) {
            lastProgress = progress;
            lastHeat = heat;
            lastFluid = tank.getFluidAmount();
            lastFluidType = fluidType;
            lastWasOn = wasOn;
            hasSynced = true;
            level.sendBlockUpdated(position, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public FluidIdentifierItem.Selection fluidSelection() {
        if (tank.isEmpty()) return FluidIdentifierItem.Selection.NONE;
        for (FluidIdentifierItem.Selection selection : FluidIdentifierItem.Selection.values()) {
            if (selection.accepts(tank.getFluid().getFluid())) return selection;
        }
        return FluidIdentifierItem.Selection.NONE;
    }

    public int progress() { return progress; }
    public int heat() { return heat; }
    public boolean wasOn() { return wasOn; }
    public FluidTank tank() { return tank; }
    public int smokeStored() { return smoke; }
    public IFluidHandler fluidHandler() { return fluidHandler; }
    public ContainerData data() { return data; }
    public void setHeatForTest(int value) { heat = Mth.clamp(value, 0, MAX_HEAT); }
    public void setProgressForTest(int value) { progress = Mth.clamp(value, 0, PROCESS_TIME); }
    public void pullHeatForTest() { tryPullHeat(); }

    public void setCustomName(Component name) { customName = name; setChanged(); }
    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.furnaceCombination");
    }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CombinationOvenMenu(id, inventory, this, data);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("prog", progress);
        tag.putInt("heat", heat);
        tag.putInt("smoke0", smoke);
        tag.put("tank", tank.writeToNBT(registries, new CompoundTag()));
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        progress = Math.max(tag.getInt("prog"), 0);
        heat = Mth.clamp(tag.getInt("heat"), 0, MAX_HEAT);
        smoke = Mth.clamp(tag.getInt("smoke0"), 0, SMOKE_CAPACITY);
        if (tag.contains("tank")) tank.readFromNBT(registries, tag.getCompound("tank"));
        customName = tag.contains("name") ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("prog", progress);
        tag.putInt("heat", heat);
        tag.putBoolean("wasOn", wasOn);
        tag.put("tank", tank.writeToNBT(registries, new CompoundTag()));
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        progress = tag.getInt("prog");
        heat = tag.getInt("heat");
        wasOn = tag.getBoolean("wasOn");
        if (tag.contains("tank")) tank.readFromNBT(registries, tag.getCompound("tank"));
    }

    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() {
        for (ItemStack stack : items) if (!stack.isEmpty()) return false;
        return true;
    }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack result = ContainerHelper.removeItem(items, slot, count);
        if (!result.isEmpty()) setChanged();
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getCenter()) <= 128D;
    }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT && CombinationOvenRecipes.isValidInput(stack) || slot == CONTAINER_INPUT;
    }
    @Override public int[] getSlotsForFace(Direction side) { return AUTOMATION_SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot == INPUT && CombinationOvenRecipes.isValidInput(stack);
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == OUTPUT;
    }
}
