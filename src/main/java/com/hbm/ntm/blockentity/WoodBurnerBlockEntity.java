package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.WoodBurnerBlock;
import com.hbm.ntm.energy.HeProvider;
import com.hbm.ntm.inventory.WoodBurnerMenu;
import com.hbm.ntm.item.AshItem;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.item.HeBatteryItem;
import com.hbm.ntm.item.SourceFluidContainerItem;
import com.hbm.ntm.item.InfiniteFluidBarrelItem;
import com.hbm.ntm.item.UniversalFluidTankItem;
import com.hbm.ntm.pollution.PollutionData;
import com.hbm.ntm.recipe.FluidBurnerFuels;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.thermal.FireboxFuel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/** Burns wood, fluids and any remaining confidence in indoor air quality. */
public final class WoodBurnerBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider, HeProvider {
    public static final int SOLID_FUEL = 0;
    public static final int ASH_OUTPUT = 1;
    public static final int FLUID_IDENTIFIER = 2;
    public static final int FLUID_INPUT = 3;
    public static final int FLUID_OUTPUT = 4;
    public static final int BATTERY = 5;
    public static final int SLOT_COUNT = 6;
    public static final int TANK_CAPACITY = 16_000;
    public static final long MAX_POWER = 100_000L;
    public static final int ASH_THRESHOLD = 2_000;
    public static final int DATA_COUNT = 9;
    private static final int[] AUTOMATION_SLOTS = {SOLID_FUEL, ASH_OUTPUT};

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private FluidIdentifierItem.Selection selectedFluid = FluidIdentifierItem.Selection.WOODOIL;
    private final FluidTank tank = new FluidTank(TANK_CAPACITY,
            stack -> selectedFluid.accepts(stack.getFluid())) {
        @Override protected void onContentsChanged() { WoodBurnerBlockEntity.this.setChanged(); }
    };
    private final IFluidHandler fluidHandler = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int index) {
            return index == 0 ? tank.getFluid().copy() : FluidStack.EMPTY;
        }
        @Override public int getTankCapacity(int index) { return index == 0 ? TANK_CAPACITY : 0; }
        @Override public boolean isFluidValid(int index, FluidStack stack) {
            return index == 0 && selectedFluid.accepts(stack.getFluid());
        }
        @Override public int fill(FluidStack resource, FluidAction action) {
            return selectedFluid.accepts(resource.getFluid()) ? tank.fill(resource, action) : 0;
        }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
    };
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> (int) power;
                case 1 -> (int) (power >>> 32);
                case 2 -> burnTime;
                case 3 -> maxBurnTime;
                case 4 -> tank.getFluidAmount();
                case 5 -> selectedFluid.ordinal();
                case 6 -> isOn ? 1 : 0;
                case 7 -> liquidBurn ? 1 : 0;
                case 8 -> powerGeneration;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> power = power & 0xFFFFFFFF00000000L | value & 0xFFFFFFFFL;
                case 1 -> power = power & 0xFFFFFFFFL | (long) value << 32;
                case 2 -> burnTime = value;
                case 3 -> maxBurnTime = value;
                case 4 -> tank.setFluid(value <= 0 ? FluidStack.EMPTY
                        : new FluidStack(selectedFluid.fluid(), Math.min(value, TANK_CAPACITY)));
                case 5 -> {
                    FluidIdentifierItem.Selection[] values = FluidIdentifierItem.Selection.values();
                    if (value >= 0 && value < values.length) selectedFluid = values[value];
                }
                case 6 -> isOn = value != 0;
                case 7 -> liquidBurn = value != 0;
                case 8 -> powerGeneration = value;
                default -> { }
            }
        }
        @Override public int getCount() { return DATA_COUNT; }
    };

    private long power;
    private int burnTime;
    private int maxBurnTime;
    private boolean liquidBurn;
    private boolean isOn;
    private int powerGeneration;
    private int ashLevelWood;
    private int ashLevelCoal;
    private int ashLevelMisc;
    private Component customName;

    private long lastPower = Long.MIN_VALUE;
    private int lastBurn = Integer.MIN_VALUE;
    private int lastMaxBurn = Integer.MIN_VALUE;
    private int lastTank = Integer.MIN_VALUE;
    private FluidIdentifierItem.Selection lastFluid;
    private boolean lastOn;
    private boolean lastLiquid;
    private int lastGeneration = Integer.MIN_VALUE;

    public WoodBurnerBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_WOOD_BURNER.get(), position, state);
    }

    public static void tick(Level level, BlockPos position, BlockState state, WoodBurnerBlockEntity burner) {
        if (!level.isClientSide) burner.serverTick((ServerLevel) level, position, state);
    }

    private void serverTick(ServerLevel level, BlockPos position, BlockState state) {
        powerGeneration = 0;
        refreshSelectedFluid();
        loadFluidContainer();
        chargeBattery();

        Direction facing = state.getValue(WoodBurnerBlock.FACING);
        for (WoodBurnerBlock.Connection connection : WoodBurnerBlock.connections(position, facing)) {
            if (power > 0L) tryProvide(level, connection.target(), connection.outward());
            if (level.getGameTime() % 20L == 0L) pullFluid(level, connection.target(), connection.outward());
        }

        if (liquidBurn) burnLiquid(level, position);
        else burnSolid(level, position);

        power = Math.min(power + powerGeneration, MAX_POWER);
        syncIfChanged(level, position, state);
    }

    private void refreshSelectedFluid() {
        ItemStack identifier = items.get(FLUID_IDENTIFIER);
        if (!(identifier.getItem() instanceof FluidIdentifierItem)) return;
        FluidIdentifierItem.Selection selection = FluidIdentifierItem.primary(identifier);
        if (selection == selectedFluid) return;
        selectedFluid = selection;
        tank.setFluid(FluidStack.EMPTY);
        setChanged();
    }

    private void loadFluidContainer() {
        ItemStack input = items.get(FLUID_INPUT);
        if (input.isEmpty()) return;
        if (InfiniteFluidBarrelItem.is(input)) {
            if (selectedFluid != FluidIdentifierItem.Selection.NONE
                    && InfiniteFluidBarrelItem.fillTank(tank, selectedFluid.fluid()) > 0) setChanged();
            return;
        }
        Fluid fluid;
        ItemStack remainder;
        if (input.getItem() instanceof UniversalFluidTankItem) {
            fluid = UniversalFluidTankItem.fluid(input).fluid();
            remainder = new ItemStack(ModItems.FLUID_TANK_EMPTY.get());
        } else if (input.is(ModItems.CANISTER_FULL.get())) {
            fluid = SourceFluidContainerItem.fluid(input).fluid();
            remainder = new ItemStack(ModItems.CANISTER_EMPTY.get());
        } else if (input.is(ModItems.GAS_FULL.get())) {
            fluid = SourceFluidContainerItem.fluid(input).fluid();
            remainder = new ItemStack(ModItems.GAS_EMPTY.get());
        } else return;
        if (!selectedFluid.accepts(fluid) || !canMerge(items.get(FLUID_OUTPUT), remainder)
                || tank.fill(new FluidStack(fluid, 1_000), IFluidHandler.FluidAction.SIMULATE) != 1_000) return;
        tank.fill(new FluidStack(fluid, 1_000), IFluidHandler.FluidAction.EXECUTE);
        input.shrink(1);
        if (input.isEmpty()) items.set(FLUID_INPUT, ItemStack.EMPTY);
        mergeOutput(FLUID_OUTPUT, remainder);
        setChanged();
    }

    private void chargeBattery() {
        ItemStack stack = items.get(BATTERY);
        if (!(stack.getItem() instanceof HeBatteryItem battery)) return;
        long amount = Math.min(Math.min(power, battery.getChargeRate(stack)),
                Math.max(battery.getMaxCharge(stack) - battery.getCharge(stack), 0L));
        if (amount <= 0L) return;
        battery.charge(stack, amount);
        power -= amount;
        setChanged();
    }

    private void pullFluid(ServerLevel level, BlockPos target, Direction outward) {
        if (selectedFluid == FluidIdentifierItem.Selection.NONE || tank.getSpace() <= 0) return;
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK,
                target, outward.getOpposite());
        if (handler == null) return;
        FluidStack request = new FluidStack(selectedFluid.fluid(), tank.getSpace());
        FluidStack available = handler.drain(request, IFluidHandler.FluidAction.SIMULATE);
        if (available.isEmpty() || !selectedFluid.accepts(available.getFluid())) return;
        int accepted = tank.fill(available, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) return;
        FluidStack drained = handler.drain(new FluidStack(selectedFluid.fluid(), accepted),
                IFluidHandler.FluidAction.EXECUTE);
        tank.fill(drained, IFluidHandler.FluidAction.EXECUTE);
    }

    private void burnSolid(ServerLevel level, BlockPos position) {
        if (burnTime <= 0) {
            ItemStack fuel = items.get(SOLID_FUEL);
            int burn = burnTime(fuel);
            if (burn > 0) startSolidFuel(fuel, burn);
        } else if (power < MAX_POWER && isOn) {
            burnTime--;
            powerGeneration += 100;
            if (level.getGameTime() % 20L == 0L) {
                PollutionData.get(level).increment(position, PollutionData.Type.SOOT, 0.04F);
            }
        }
    }

    private void startSolidFuel(ItemStack fuel, int burn) {
        ItemStack consumed = fuel.copyWithCount(1);
        addAsh(FireboxFuel.ashType(consumed), burn);
        while (processAsh(ashLevelWood, AshItem.AshType.WOOD)) ashLevelWood -= ASH_THRESHOLD;
        while (processAsh(ashLevelCoal, AshItem.AshType.COAL)) ashLevelCoal -= ASH_THRESHOLD;
        while (processAsh(ashLevelMisc, AshItem.AshType.MISC)) ashLevelMisc -= ASH_THRESHOLD;
        maxBurnTime = burnTime = burn;
        ItemStack remainder = consumed.getCraftingRemainingItem();
        fuel.shrink(1);
        if (fuel.isEmpty()) items.set(SOLID_FUEL, remainder);
        setChanged();
    }

    private void burnLiquid(ServerLevel level, BlockPos position) {
        if (power >= MAX_POWER || tank.isEmpty() || !isOn || !FluidBurnerFuels.flammable(selectedFluid)) return;
        int toBurn = Math.min(tank.getFluidAmount(), 2);
        if (toBurn <= 0) return;
        powerGeneration += FluidBurnerFuels.heatPerMb(selectedFluid) * toBurn / 2;
        tank.drain(toBurn, IFluidHandler.FluidAction.EXECUTE);
        if (level.getGameTime() % 20L == 0L) {
            PollutionData.get(level).increment(position, PollutionData.Type.SOOT, 0.04F * toBurn / 2F);
        }
    }

    private void addAsh(AshItem.AshType type, int amount) {
        if (type == AshItem.AshType.WOOD) ashLevelWood += amount;
        else if (type == AshItem.AshType.COAL) ashLevelCoal += amount;
        else ashLevelMisc += amount;
    }

    private boolean processAsh(int level, AshItem.AshType type) {
        if (level < ASH_THRESHOLD) return false;
        ItemStack output = items.get(ASH_OUTPUT);
        if (output.isEmpty()) {
            items.set(ASH_OUTPUT, AshItem.create(ModItems.POWDER_ASH.get(), type));
            return true;
        }
        if (output.is(ModItems.POWDER_ASH.get()) && AshItem.type(output) == type
                && output.getCount() < output.getMaxStackSize()) {
            output.grow(1);
            return true;
        }
        return false;
    }

    /** Logs last 4x, lesser wood 2x, ordinary furnace fuel exactly as advertised. */
    public static int burnTime(ItemStack stack) {
        int raw = FireboxFuel.rawBurnTime(stack);
        if (raw <= 0) return 0;
        if (stack.is(ItemTags.LOGS)) return raw * 4;
        if (stack.is(ItemTags.PLANKS) || stack.is(Items.STICK)) return raw * 2;
        return raw;
    }

    private void syncIfChanged(ServerLevel level, BlockPos position, BlockState state) {
        if (power != lastPower || burnTime != lastBurn || maxBurnTime != lastMaxBurn
                || tank.getFluidAmount() != lastTank || selectedFluid != lastFluid
                || isOn != lastOn || liquidBurn != lastLiquid || powerGeneration != lastGeneration
                || level.getGameTime() % 20L == 0L) {
            lastPower = power;
            lastBurn = burnTime;
            lastMaxBurn = maxBurnTime;
            lastTank = tank.getFluidAmount();
            lastFluid = selectedFluid;
            lastOn = isOn;
            lastLiquid = liquidBurn;
            lastGeneration = powerGeneration;
            level.sendBlockUpdated(position, state, state, Block.UPDATE_CLIENTS);
        }
        setChanged();
    }

    private static boolean canMerge(ItemStack target, ItemStack addition) {
        return !addition.isEmpty() && (target.isEmpty() || ItemStack.isSameItemSameComponents(target, addition)
                && target.getCount() + addition.getCount() <= target.getMaxStackSize());
    }

    private void mergeOutput(int slot, ItemStack addition) {
        if (items.get(slot).isEmpty()) items.set(slot, addition.copy());
        else items.get(slot).grow(addition.getCount());
    }

    public static boolean isFluidContainer(ItemStack stack) {
        return InfiniteFluidBarrelItem.is(stack) || stack.getItem() instanceof UniversalFluidTankItem
                || stack.is(ModItems.CANISTER_FULL.get()) || stack.is(ModItems.GAS_FULL.get());
    }

    public void toggleOn() { isOn = !isOn; setChanged(); }
    public void switchMode() { liquidBurn = !liquidBurn; setChanged(); }
    public void setCustomName(Component name) { customName = name; setChanged(); }

    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.machineWoodBurner");
    }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new WoodBurnerMenu(id, inventory, this, data);
    }

    public IFluidHandler fluidHandler() { return fluidHandler; }
    public FluidIdentifierItem.Selection selectedFluid() { return selectedFluid; }
    public int tankAmount() { return tank.getFluidAmount(); }
    public int burnTime() { return burnTime; }
    public int maxBurnTime() { return maxBurnTime; }
    public int powerGeneration() { return powerGeneration; }
    public boolean isOn() { return isOn; }
    public boolean liquidBurn() { return liquidBurn; }
    public int ashLevel(AshItem.AshType type) {
        return type == AshItem.AshType.WOOD ? ashLevelWood
                : type == AshItem.AshType.COAL ? ashLevelCoal : ashLevelMisc;
    }
    public AABB renderBounds() {
        return new AABB(worldPosition.getX() - 1, worldPosition.getY(), worldPosition.getZ() - 1,
                worldPosition.getX() + 2, worldPosition.getY() + 6, worldPosition.getZ() + 2);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putLong("power", power);
        tag.putInt("burnTime", burnTime);
        tag.putInt("maxBurnTime", maxBurnTime);
        tag.putBoolean("isOn", isOn);
        tag.putBoolean("liquidBurn", liquidBurn);
        tag.putString("selectedFluid", selectedFluid.id());
        tag.put("tank", tank.writeToNBT(registries, new CompoundTag()));
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
        // Ash ledgers and current generation vanish on reload. Spring cleaning.
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        power = Math.clamp(tag.getLong("power"), 0L, MAX_POWER);
        burnTime = Math.max(tag.getInt("burnTime"), 0);
        maxBurnTime = Math.max(tag.getInt("maxBurnTime"), 0);
        isOn = tag.getBoolean("isOn");
        liquidBurn = tag.getBoolean("liquidBurn");
        selectedFluid = tag.contains("selectedFluid")
                ? FluidIdentifierItem.Selection.byId(tag.getString("selectedFluid"))
                : FluidIdentifierItem.Selection.WOODOIL;
        if (tag.contains("tank")) tank.readFromNBT(registries, tag.getCompound("tank"));
        if (!tank.isEmpty() && !selectedFluid.accepts(tank.getFluid().getFluid())) tank.setFluid(FluidStack.EMPTY);
        customName = tag.contains("name") ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("power", power);
        tag.putInt("burnTime", burnTime);
        tag.putInt("maxBurnTime", maxBurnTime);
        tag.putBoolean("isOn", isOn);
        tag.putBoolean("liquidBurn", liquidBurn);
        tag.putInt("powerGeneration", powerGeneration);
        tag.putString("selectedFluid", selectedFluid.id());
        tag.put("tank", tank.writeToNBT(registries, new CompoundTag()));
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        power = Math.clamp(tag.getLong("power"), 0L, MAX_POWER);
        burnTime = Math.max(tag.getInt("burnTime"), 0);
        maxBurnTime = Math.max(tag.getInt("maxBurnTime"), 0);
        isOn = tag.getBoolean("isOn");
        liquidBurn = tag.getBoolean("liquidBurn");
        powerGeneration = Math.max(tag.getInt("powerGeneration"), 0);
        selectedFluid = FluidIdentifierItem.Selection.byId(tag.getString("selectedFluid"));
        if (tag.contains("tank")) tank.readFromNBT(registries, tag.getCompound("tank"));
    }

    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) setChanged();
        return removed;
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
        return slot == SOLID_FUEL && burnTime(stack) > 0;
    }
    @Override public int[] getSlotsForFace(Direction side) { return AUTOMATION_SLOTS.clone(); }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot == SOLID_FUEL && burnTime(stack) > 0;
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == ASH_OUTPUT;
    }

    @Override public boolean canConnect(Direction side) {
        return side == getBlockState().getValue(WoodBurnerBlock.FACING).getOpposite();
    }
    @Override public long getPower() { return power; }
    @Override public void setPower(long power) { this.power = Math.clamp(power, 0L, MAX_POWER); setChanged(); }
    @Override public long getMaxPower() { return MAX_POWER; }
    @Override public boolean isHeLoaded() { return hasLevel() && !isRemoved(); }

    public void setOnForTest(boolean on) { isOn = on; }
    public void setLiquidForTest(boolean liquid) { liquidBurn = liquid; }
    public void setSelectedForTest(FluidIdentifierItem.Selection selection) {
        selectedFluid = selection;
        tank.setFluid(FluidStack.EMPTY);
    }
    public int addFluidForTest(int amount) {
        return tank.fill(new FluidStack(selectedFluid.fluid(), amount), IFluidHandler.FluidAction.EXECUTE);
    }
    public void setPowerForTest(long power) { this.power = Math.clamp(power, 0L, MAX_POWER); }
    public void serverTickForTest(ServerLevel level) { serverTick(level, worldPosition, getBlockState()); }
}
