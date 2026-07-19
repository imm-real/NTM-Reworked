package com.hbm.ntm.blockentity;

import com.hbm.ntm.inventory.AshpitMenu;
import com.hbm.ntm.item.AshItem;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModItems;
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
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public final class AshpitBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int SLOT_COUNT = 5;
    public static final int THRESHOLD_WOOD = 2_000;
    public static final int THRESHOLD_COAL = 2_000;
    public static final int THRESHOLD_MISC = 2_000;
    public static final int THRESHOLD_FLY = 2_000;
    public static final int THRESHOLD_SOOT = 8_000;
    private static final int[] SLOTS = {0, 1, 2, 3, 4};

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private Component customName;
    private int playersUsing;
    private boolean isFull;
    private int ashLevelWood;
    private int ashLevelCoal;
    private int ashLevelMisc;
    private int ashLevelFly;
    private int ashLevelSoot;
    private float doorAngle;
    private float previousDoorAngle;

    public AshpitBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_ASHPIT.get(), position, state);
    }

    public static void tick(Level level, BlockPos position, BlockState state, AshpitBlockEntity ashpit) {
        if (level.isClientSide) {
            ashpit.clientTick();
        } else {
            ashpit.serverTick((ServerLevel) level, position, state);
        }
    }

    private void serverTick(ServerLevel level, BlockPos position, BlockState state) {
        if (processAsh(ashLevelWood, AshItem.AshType.WOOD, THRESHOLD_WOOD)) ashLevelWood -= THRESHOLD_WOOD;
        if (processAsh(ashLevelCoal, AshItem.AshType.COAL, THRESHOLD_COAL)) ashLevelCoal -= THRESHOLD_COAL;
        if (processAsh(ashLevelMisc, AshItem.AshType.MISC, THRESHOLD_MISC)) ashLevelMisc -= THRESHOLD_MISC;
        if (processAsh(ashLevelFly, AshItem.AshType.FLY, THRESHOLD_FLY)) ashLevelFly -= THRESHOLD_FLY;
        if (processAsh(ashLevelSoot, AshItem.AshType.SOOT, THRESHOLD_SOOT)) ashLevelSoot -= THRESHOLD_SOOT;

        isFull = false;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                isFull = true;
            }
        }
        setChanged();
        level.sendBlockUpdated(position, state, state, Block.UPDATE_CLIENTS);
    }

    private void clientTick() {
        previousDoorAngle = doorAngle;
        float swingSpeed = doorAngle / 10.0F + 3.0F;
        doorAngle += playersUsing > 0 ? swingSpeed : -swingSpeed;
        doorAngle = Mth.clamp(doorAngle, 0.0F, 135.0F);
    }

    private boolean processAsh(int level, AshItem.AshType type, int threshold) {
        if (level < threshold) {
            return false;
        }
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack existing = items.get(slot);
            if (existing.isEmpty()) {
                items.set(slot, AshItem.create(ModItems.POWDER_ASH.get(), type));
                ashLevelWood -= threshold; // Preserved original empty-slot counter bug.
                return true;
            }
            if (existing.getCount() < existing.getMaxStackSize()
                    && existing.is(ModItems.POWDER_ASH.get())
                    && AshItem.type(existing) == type) {
                existing.grow(1);
                return true;
            }
        }
        return false;
    }

    public void addAsh(AshItem.AshType type, int amount) {
        switch (type) {
            case WOOD -> ashLevelWood += amount;
            case COAL -> ashLevelCoal += amount;
            case MISC -> ashLevelMisc += amount;
            case FLY -> ashLevelFly += amount;
            case SOOT -> ashLevelSoot += amount;
            case FULLERENE -> {
            }
        }
        setChanged();
    }

    public void setCustomName(Component customName) {
        this.customName = customName;
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.ashpit");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new AshpitMenu(containerId, inventory, this);
    }

    @Override
    public void startOpen(Player player) {
        if (!player.isSpectator()) {
            playersUsing++;
            syncNow();
        }
    }

    @Override
    public void stopOpen(Player player) {
        if (!player.isSpectator()) {
            playersUsing--;
            syncNow();
        }
    }

    private void syncNow() {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("ashLevelWood", ashLevelWood);
        tag.putInt("ashLevelCoal", ashLevelCoal);
        tag.putInt("ashLevelMisc", ashLevelMisc);
        tag.putInt("ashLevelFly", ashLevelFly);
        tag.putInt("ashLevelSoot", ashLevelSoot);
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        ashLevelWood = tag.getInt("ashLevelWood");
        ashLevelCoal = tag.getInt("ashLevelCoal");
        ashLevelMisc = tag.getInt("ashLevelMisc");
        ashLevelFly = tag.getInt("ashLevelFly");
        ashLevelSoot = tag.getInt("ashLevelSoot");
        customName = tag.contains("name") ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("playersUsing", playersUsing);
        tag.putBoolean("isFull", isFull);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        playersUsing = tag.getInt("playersUsing");
        isFull = tag.getBoolean("isFull");
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) { ItemStack result = ContainerHelper.removeItem(items, slot, amount); if (!result.isEmpty()) setChanged(); return result; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) { items.set(slot, stack); setChanged(); }
    @Override public boolean stillValid(Player player) { return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getCenter()) <= 128.0D; }
    @Override public void clearContent() { items.clear(); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return false; }
    @Override public int[] getSlotsForFace(Direction direction) { return SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) { return false; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) { return true; }

    public boolean isFull() { return isFull; }
    public float doorAngle() { return doorAngle; }
    public float previousDoorAngle() { return previousDoorAngle; }

    public AABB renderBounds() {
        return new AABB(worldPosition.getX() - 1, worldPosition.getY(), worldPosition.getZ() - 1,
                worldPosition.getX() + 2, worldPosition.getY() + 1, worldPosition.getZ() + 2);
    }
}
