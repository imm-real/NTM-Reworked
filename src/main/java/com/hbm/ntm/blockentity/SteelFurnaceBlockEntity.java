package com.hbm.ntm.blockentity;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.SteelFurnaceBlock;
import com.hbm.ntm.inventory.SteelFurnaceMenu;
import com.hbm.ntm.pollution.PollutionData;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.thermal.HeatSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/** Three smelting lanes fighting over one external heat reservoir. */
public final class SteelFurnaceBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider {
    public static final int LANE_COUNT = 3;
    public static final int SLOT_COUNT = 6;
    public static final int PROCESS_TIME = 40_000;
    public static final int MAX_HEAT = 100_000;
    public static final int MINIMUM_HEAT = MAX_HEAT / 3;
    public static final double DIFFUSION = 0.05D;
    private static final float SOOT_PER_ACTIVE_LANE_SECOND = 0.08F;
    private static final int[] ALL_SLOTS = {0, 1, 2, 3, 4, 5};
    private static final TagKey<Item> ORES = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "ores"));

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final int[] progress = new int[LANE_COUNT];
    private final int[] bonus = new int[LANE_COUNT];
    private final ItemStack[] lastItems = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private int heat;
    private boolean wasOn;
    private Component customName;

    private final int[] lastSyncedProgress = {-1, -1, -1};
    private final int[] lastSyncedBonus = {-1, -1, -1};
    private int lastSyncedHeat = -1;
    private boolean lastSyncedWasOn;
    private boolean hasSynced;

    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            if (index >= 0 && index < 3) return progress[index];
            if (index >= 3 && index < 6) return bonus[index - 3];
            return switch (index) {
                case 6 -> heat;
                case 7 -> wasOn ? 1 : 0;
                default -> 0;
            };
        }

        @Override public void set(int index, int value) {
            if (index >= 0 && index < 3) progress[index] = value;
            else if (index >= 3 && index < 6) bonus[index - 3] = value;
            else if (index == 6) heat = value;
            else if (index == 7) wasOn = value != 0;
        }

        @Override public int getCount() { return 8; }
    };

    public SteelFurnaceBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.FURNACE_STEEL.get(), position, state);
    }

    public static void tick(Level level, BlockPos position, BlockState state, SteelFurnaceBlockEntity furnace) {
        if (level.isClientSide) furnace.clientTick(level, position, state);
        else furnace.serverTick((ServerLevel) level, position, state);
    }

    private void serverTick(ServerLevel level, BlockPos position, BlockState state) {
        tryPullHeat();
        wasOn = false;
        int burn = (heat - MINIMUM_HEAT) / 10;

        for (int lane = 0; lane < LANE_COUNT; lane++) {
            ItemStack input = items.get(lane);
            if (input.isEmpty() || lastItems[lane].isEmpty()
                    || !ItemStack.isSameItemSameComponents(input, lastItems[lane])) {
                progress[lane] = 0;
                bonus[lane] = 0;
            }

            ItemStack result = smeltingResult(input);
            if (canSmelt(lane, result)) {
                progress[lane] += burn;
                heat -= burn;
                wasOn = true;
                if (level.getGameTime() % 20L == 0L) {
                    PollutionData.get(level).increment(position, PollutionData.Type.SOOT,
                            SOOT_PER_ACTIVE_LANE_SECOND);
                }
            }

            // Keep the live slot reference, later count mutation included.
            lastItems[lane] = input;

            if (progress[lane] >= PROCESS_TIME) {
                finishLane(lane, result);
            }
        }

        setChanged();
        syncIfChanged(level, position, state);
    }

    private void finishLane(int lane, ItemStack result) {
        if (result.isEmpty()) return;
        int outputSlot = lane + LANE_COUNT;
        ItemStack output = items.get(outputSlot);
        if (output.isEmpty()) {
            items.set(outputSlot, result.copy());
            output = items.get(outputSlot);
        } else {
            output.grow(result.getCount());
        }

        addBonus(items.get(lane), lane);
        while (bonus[lane] >= 100) {
            output.setCount(Math.min(output.getMaxStackSize(), output.getCount() + result.getCount()));
            bonus[lane] -= 100;
        }

        ItemStack input = items.get(lane);
        // Remove one item without mutating the snapshot held by lastItems.
        if (input.getCount() <= 1) items.set(lane, ItemStack.EMPTY);
        else input.shrink(1);
        progress[lane] = 0;
    }

    private void addBonus(ItemStack stack, int lane) {
        if (stack.is(ORES)) bonus[lane] += 25;
        else if (stack.is(ItemTags.LOGS)) bonus[lane] += 50;
        else if (stack.is(ModItems.OIL_TAR.get())) bonus[lane] += 50;
    }

    private ItemStack smeltingResult(ItemStack input) {
        if (level == null || input.isEmpty()) return ItemStack.EMPTY;
        return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING,
                        new SingleRecipeInput(input), level)
                .map(holder -> holder.value().assemble(new SingleRecipeInput(input), level.registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    public boolean canSmelt(int lane) {
        return lane >= 0 && lane < LANE_COUNT && canSmelt(lane, smeltingResult(items.get(lane)));
    }

    private boolean canSmelt(int lane, ItemStack result) {
        if (heat < MINIMUM_HEAT || items.get(lane).isEmpty() || result.isEmpty()) return false;
        ItemStack output = items.get(lane + LANE_COUNT);
        return output.isEmpty() || ItemStack.isSameItemSameComponents(result, output)
                && result.getCount() + output.getCount() <= output.getMaxStackSize();
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
                heat += transfer;
                if (heat > MAX_HEAT) heat = MAX_HEAT;
                return;
            }
        }
        heat = Math.max(heat - Math.max(heat / 1000, 1), 0);
    }

    private void clientTick(Level level, BlockPos position, BlockState state) {
        if (!wasOn) return;
        Direction facing = state.getValue(SteelFurnaceBlock.FACING);
        Direction side = facing.getClockWise();
        level.addParticle(ParticleTypes.SMOKE,
                position.getX() + 0.5D - facing.getStepX() * 1.125D - side.getStepX() * 0.75D,
                position.getY() + 2.625D,
                position.getZ() + 0.5D - facing.getStepZ() * 1.125D - side.getStepZ() * 0.75D,
                0.0D, 0.05D, 0.0D);
        if (level.random.nextInt(20) == 0) {
            level.addParticle(ParticleTypes.CLOUD,
                    position.getX() + 0.5D + facing.getStepX() * 0.75D,
                    position.getY() + 2.0D,
                    position.getZ() + 0.5D + facing.getStepZ() * 0.75D,
                    0.0D, 0.05D, 0.0D);
        }
        if (level.random.nextInt(15) == 0) {
            level.addParticle(ParticleTypes.LAVA,
                    position.getX() + 0.5D + facing.getStepX() * 1.5D
                            + side.getStepX() * (level.random.nextDouble() - 0.5D),
                    position.getY() + 0.75D,
                    position.getZ() + 0.5D + facing.getStepZ() * 1.5D
                            + side.getStepZ() * (level.random.nextDouble() - 0.5D),
                    facing.getStepX() * 0.5D, 0.05D, facing.getStepZ() * 0.5D);
        }
    }

    private void syncIfChanged(ServerLevel level, BlockPos position, BlockState state) {
        if (!hasSynced || heat != lastSyncedHeat || wasOn != lastSyncedWasOn
                || !Arrays.equals(progress, lastSyncedProgress) || !Arrays.equals(bonus, lastSyncedBonus)) {
            System.arraycopy(progress, 0, lastSyncedProgress, 0, LANE_COUNT);
            System.arraycopy(bonus, 0, lastSyncedBonus, 0, LANE_COUNT);
            lastSyncedHeat = heat;
            lastSyncedWasOn = wasOn;
            hasSynced = true;
            level.sendBlockUpdated(position, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public int progress(int lane) { return progress[lane]; }
    public int bonus(int lane) { return bonus[lane]; }
    public int heat() { return heat; }
    public boolean wasOn() { return wasOn; }
    public ContainerData dataAccess() { return data; }

    public void setHeatForTest(int value) { heat = value; }
    public void setProgressForTest(int lane, int value) { progress[lane] = value; }
    public void setBonusForTest(int lane, int value) { bonus[lane] = value; }
    public void pullHeatForTest() { tryPullHeat(); }

    public void setCustomName(Component name) { customName = name; setChanged(); }
    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.furnaceSteel");
    }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SteelFurnaceMenu(id, inventory, this, data);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putIntArray("progress", progress);
        tag.putIntArray("bonus", bonus);
        tag.putInt("heat", heat);
        ListTag previous = new ListTag();
        for (int lane = 0; lane < LANE_COUNT; lane++) {
            if (lastItems[lane].isEmpty()) continue;
            CompoundTag entry = (CompoundTag) lastItems[lane].save(registries);
            entry.putByte("lastItem", (byte) lane);
            previous.add(entry);
        }
        tag.put("lastItems", previous);
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        copyArray(tag.getIntArray("progress"), progress);
        copyArray(tag.getIntArray("bonus"), bonus);
        heat = tag.getInt("heat");
        Arrays.fill(lastItems, ItemStack.EMPTY);
        ListTag previous = tag.getList("lastItems", Tag.TAG_COMPOUND);
        for (int index = 0; index < previous.size(); index++) {
            CompoundTag entry = previous.getCompound(index);
            int lane = entry.getByte("lastItem");
            if (lane >= 0 && lane < LANE_COUNT) lastItems[lane] = ItemStack.parseOptional(registries, entry);
        }
        customName = tag.contains("name") ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
    }

    private static void copyArray(int[] source, int[] destination) {
        Arrays.fill(destination, 0);
        System.arraycopy(source, 0, destination, 0, Math.min(source.length, destination.length));
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("progress", progress);
        tag.putIntArray("bonus", bonus);
        tag.putInt("heat", heat);
        tag.putBoolean("wasOn", wasOn);
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        copyArray(tag.getIntArray("progress"), progress);
        copyArray(tag.getIntArray("bonus"), bonus);
        heat = tag.getInt("heat");
        wasOn = tag.getBoolean("wasOn");
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
        return slot >= 0 && slot < LANE_COUNT && !smeltingResult(stack).isEmpty();
    }
    @Override public int[] getSlotsForFace(Direction side) { return ALL_SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= LANE_COUNT && slot < SLOT_COUNT;
    }
}
