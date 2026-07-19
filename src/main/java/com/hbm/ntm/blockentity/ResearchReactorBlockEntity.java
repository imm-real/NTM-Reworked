package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.ResearchReactorBlock;
import com.hbm.ntm.inventory.ResearchReactorMenu;
import com.hbm.ntm.machine.BreederFluxProvider;
import com.hbm.ntm.item.DepletedPlateFuelItem;
import com.hbm.ntm.item.PlateFuelItem;
import com.hbm.ntm.radiation.ChunkRadiationData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/** Research reactor kinetics, cooling and the usual exciting failure threshold. */
public final class ResearchReactorBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider, BreederFluxProvider {
    public static final int SLOT_COUNT = 12;
    public static final int MAX_HEAT = 50_000;
    public static final double ROD_SPEED = 0.04D;
    public static final ResourceLocation MENU_ID = ResourceLocation.fromNamespaceAndPath("hbm", "reactor_research");
    private static final ResourceLocation DECO_STEEL = hbm("deco_steel");
    private static final ResourceLocation CORIUM = hbm("corium_block");
    private static final ResourceLocation BLOCK_LEAD = hbm("block_lead");
    private static final ResourceLocation BLOCK_DESH = hbm("block_desh");
    private static final ResourceLocation BREEDER = hbm("machine_reactor_breeding");
    private static final ResourceLocation METEOR_BRED = hbm("meteorite_sword_bred");
    private static final ResourceLocation METEOR_IRRADIATED = hbm("meteorite_sword_irradiated");
    private static final int[] AUTOMATION_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    private static final int[][] NEIGHBORS = {
            {1, 5}, {0, 6}, {3, 7}, {2, 4, 8}, {3, 9}, {0, 6, 10},
            {1, 5, 11}, {2, 8}, {3, 7, 9}, {4, 8}, {5, 11}, {6, 10}
    };

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int heat;
    private byte water;
    private double control;
    private double lastLevel;
    private double targetLevel;
    private int[] slotFlux = new int[SLOT_COUNT];
    private int totalFlux;

    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> heat;
                case 1 -> water;
                case 2 -> (int) Math.round(control * 10_000.0D);
                case 3 -> (int) Math.round(targetLevel * 10_000.0D);
                case 4 -> totalFlux;
                default -> 0;
            };
        }

        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> heat = value;
                case 1 -> water = (byte) value;
                case 2 -> control = value / 10_000.0D;
                case 3 -> targetLevel = value / 10_000.0D;
                case 4 -> totalFlux = value;
                default -> { }
            }
        }

        @Override public int getCount() { return 5; }
    };

    @SuppressWarnings("unchecked")
    public ResearchReactorBlockEntity(BlockPos pos, BlockState state) {
        super((BlockEntityType<ResearchReactorBlockEntity>) BuiltInRegistries.BLOCK_ENTITY_TYPE
                .get(ResearchReactorBlock.BLOCK_ENTITY_ID), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state,
                            ResearchReactorBlockEntity reactor) {
        if (level.isClientSide) {
            reactor.lastLevel = reactor.control;
            return;
        }
        reactor.serverTick((ServerLevel) level, pos, state);
    }

    private void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        moveRods();
        totalFlux = 0;
        if (control > 0.0D) react();

        if (heat > 0) {
            water = countWater();
            if (water > 0) heat = coolHeat(heat, water);
            else heat -= 1;
            if (heat < 0) heat = 0;
        }

        if (heat > MAX_HEAT) {
            meltdown(level, pos, state);
            return;
        }

        if (control > 0.0D && heat > 0 && !radiationShielded()) {
            float radiation = heat / (float) MAX_HEAT * 50.0F;
            ChunkRadiationData.get(level).increment(pos, radiation);
        }

        setChanged();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    public static int coolHeat(int heat, int water) {
        if (water <= 0) return Math.max(heat - 1, 0);
        return Math.max((int) (heat - heat * 0.07F * water / 12.0F), 0);
    }

    private void moveRods() {
        if (control < targetLevel) {
            control += ROD_SPEED;
            if (control >= targetLevel) control = targetLevel;
        }
        if (control > targetLevel) {
            control -= ROD_SPEED;
            if (control <= targetLevel) control = targetLevel;
        }
    }

    private void react() {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) {
                slotFlux[slot] = 0;
                continue;
            }
            if (stack.getItem() instanceof PlateFuelItem plate) {
                int output = plate.react(stack, slotFlux[slot]);
                heat += output * 2;
                slotFlux[slot] = 0;
                totalFlux += output;
                if (plate.depleted(stack)) items.set(slot, depleted(plate.type()));
                for (int neighbor : NEIGHBORS[slot]) {
                    slotFlux[neighbor] += (int) (output * control);
                }
                continue;
            }
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(METEOR_BRED)) {
                Item irradiated = BuiltInRegistries.ITEM.get(METEOR_IRRADIATED);
                if (irradiated != net.minecraft.world.item.Items.AIR) items.set(slot, new ItemStack(irradiated));
            }
            slotFlux[slot] = 0;
        }
    }

    private static ItemStack depleted(PlateFuelItem.Type type) {
        Item item = BuiltInRegistries.ITEM.get(hbm(type.depletedId()));
        return item == net.minecraft.world.item.Items.AIR ? ItemStack.EMPTY : DepletedPlateFuelItem.hot(item);
    }

    private byte countWater() {
        if (this.level == null) return 0;
        int count = 0;
        if (waterAt(worldPosition.below())) count++;
        if (waterAt(worldPosition.above(3))) count++;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int y = 0; y < 3; y++) if (waterAt(worldPosition.relative(direction).above(y))) count++;
        }
        return (byte) count;
    }

    private boolean waterAt(BlockPos pos) {
        return level != null && level.getFluidState(pos).is(FluidTags.WATER);
    }

    private boolean sourceWater(BlockPos pos) {
        return level != null && level.getFluidState(pos).is(FluidTags.WATER)
                && level.getFluidState(pos).isSource();
    }

    public boolean isSubmerged() {
        if (level == null) return false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (level.getFluidState(worldPosition.relative(direction).above()).is(FluidTags.WATER)) return true;
        }
        return false;
    }

    private boolean radiationShielded() {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!blocksRadiation(worldPosition.relative(direction).above())) return false;
        }
        return true;
    }

    private boolean blocksRadiation(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getFluidState().is(FluidTags.WATER) && state.getFluidState().isSource()) return true;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id.equals(BLOCK_LEAD) || id.equals(BLOCK_DESH) || id.equals(ResearchReactorBlock.BLOCK_ID)
                || id.equals(BREEDER)) return true;
        return state.getBlock().getExplosionResistance() >= 100.0F;
    }

    private void meltdown(ServerLevel level, BlockPos pos, BlockState state) {
        items.clear();
        for (int y = 0; y <= 2; y++) {
            BlockPos part = pos.above(y);
            if (level.getBlockState(part).is(state.getBlock())) {
                level.setBlock(part, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        removeCoolingWater(level, pos);
        level.explode(null, pos.getX(), pos.getY(), pos.getZ(), 18.0F, true, Level.ExplosionInteraction.BLOCK);

        Block deco = BuiltInRegistries.BLOCK.get(DECO_STEEL);
        Block corium = BuiltInRegistries.BLOCK.get(CORIUM);
        if (deco != Blocks.AIR) {
            level.setBlock(pos, deco.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(pos.above(2), deco.defaultBlockState(), Block.UPDATE_ALL);
        }
        if (corium != Blocks.AIR) level.setBlock(pos.above(), corium.defaultBlockState(), Block.UPDATE_ALL);
        ChunkRadiationData.get(level).increment(pos, 50.0F);
        // Elemental spawning can return when the elemental system does.
    }

    private void removeCoolingWater(ServerLevel level, BlockPos pos) {
        if (waterAt(pos.below())) level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        if (waterAt(pos.above(3))) level.setBlock(pos.above(3), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int y = 0; y < 3; y++) {
                BlockPos waterPos = pos.relative(direction).above(y);
                if (waterAt(waterPos)) level.setBlock(waterPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private static ResourceLocation hbm(String path) {
        return ResourceLocation.fromNamespaceAndPath("hbm", path);
    }

    public void setTarget(double target) { targetLevel = target; setChanged(); }
    public double controlLevel() { return control; }
    public double previousControlLevel() { return lastLevel; }
    public double targetLevel() { return targetLevel; }
    public int heat() { return heat; }
    public int water() { return water; }
    public int totalFlux() { return totalFlux; }
    @Override public int hbm$breederFlux() { return totalFlux; }
    public int[] slotFlux() { return Arrays.copyOf(slotFlux, slotFlux.length); }
    public int displayedTemperature() { return (int) Math.round(heat * 0.00002D * 980.0D + 20.0D); }
    public AABB renderBounds() { return new AABB(worldPosition).inflate(1.0D).expandTowards(0.0D, 3.0D, 0.0D); }

    @Override public Component getDisplayName() { return Component.translatable("container.reactorResearch"); }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ResearchReactorMenu(id, inventory, this, data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("heat", heat);
        tag.putByte("water", water);
        tag.putDouble("level", control);
        tag.putDouble("targetLevel", targetLevel);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        heat = tag.getInt("heat");
        water = tag.getByte("water");
        control = tag.getDouble("level");
        targetLevel = tag.getDouble("targetLevel");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("heat", heat);
        tag.putByte("water", water);
        tag.putDouble("level", control);
        tag.putDouble("targetLevel", targetLevel);
        tag.putIntArray("slotFlux", slotFlux);
        tag.putInt("totalFlux", totalFlux);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        heat = tag.getInt("heat");
        water = tag.getByte("water");
        control = tag.getDouble("level");
        targetLevel = tag.getDouble("targetLevel");
        int[] incoming = tag.getIntArray("slotFlux");
        slotFlux = incoming.length == SLOT_COUNT ? incoming : new int[SLOT_COUNT];
        totalFlux = tag.getInt("totalFlux");
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack stack = ContainerHelper.removeItem(items, slot, count);
        if (!stack.isEmpty()) setChanged();
        return stack;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getCenter()) <= 128.0D;
    }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public int[] getSlotsForFace(Direction side) { return AUTOMATION_SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot == 0 && stack.getItem() instanceof PlateFuelItem;
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        // The old identity check was always false. Extraction remains forbidden.
        return false;
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == 0 && stack.getItem() instanceof PlateFuelItem;
    }
}
