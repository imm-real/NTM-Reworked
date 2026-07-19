package com.hbm.ntm.blockentity;

import com.hbm.ntm.inventory.NukeFstbmbMenu;
import com.hbm.ntm.nuclear.BalefireEntity;
import com.hbm.ntm.nuclear.MushroomCloudEntity;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModEntities;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModSounds;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
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
import org.jetbrains.annotations.Nullable;

/**
 * Source {@code com.hbm.tileentity.bomb.TileEntityNukeBalefire} (extends {@code TileEntityMachineBase},
 * 2 slots). Ticks the arming timer (default 18000 ticks = 15:00), plays the ping/start sounds, and on
 * timer expiry or an external {@code explode()} spawns the {@link BalefireEntity} spiral and the
 * green-forward bale Torex. The renderer and GUI receive loaded, started and timer every tick.
 */
public final class NukeBalefireBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int SLOT_EGG = 0;
    public static final int SLOT_BATTERY = 1;
    private static final int[] NO_AUTOMATION = new int[0];

    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private Component customName;

    public boolean loaded;
    public boolean started;
    public int timer = 18000;
    private boolean detonating;

    public NukeBalefireBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.NUKE_FSTBMB.get(), position, state);
    }

    public static void tick(Level level, BlockPos position, BlockState state, NukeBalefireBlockEntity bomb) {
        if (!level.isClientSide) {
            bomb.serverTick((ServerLevel) level, position, state);
        }
    }

    private void serverTick(ServerLevel level, BlockPos position, BlockState state) {
        if (isRemoved()) return;
        this.loaded = this.isLoaded();

        if (!loaded) {
            started = false;
        }

        if (started) {
            timer--;

            if (timer % 20 == 0) {
                level.playSound(null, position, ModSounds.CHARGE_PING.get(), SoundSource.BLOCKS, 5.0F, 1.0F);
            }
        }

        if (timer <= 0) {
            explode();
            return;
        }

        setChanged();
        level.sendBlockUpdated(position, state, state, Block.UPDATE_CLIENTS);
    }

    public void handleButtonPacket(int value, int meta) {
        if (level == null || level.isClientSide) return;

        if (meta == 0 && this.isLoaded()) {
            level.playSound(null, worldPosition, ModSounds.CHARGE_START.get(), SoundSource.BLOCKS, 5.0F, 1.0F);
            started = true;
        }

        if (meta == 1) {
            timer = value * 20;
        }
        setChanged();
    }

    public boolean isLoaded() {
        return hasEgg() && hasBattery();
    }

    public boolean hasEgg() {
        return items.get(SLOT_EGG).is(ModItems.EGG_BALEFIRE.get());
    }

    public boolean hasBattery() {
        return getBattery() > 0;
    }

    public int getBattery() {
        if (items.get(SLOT_BATTERY).is(ModItems.BATTERY_SPARK.get())) return 1;
        if (items.get(SLOT_BATTERY).is(ModItems.BATTERY_TRIXITE.get())) return 2;
        return 0;
    }

    public void explode() {
        if (!(level instanceof ServerLevel server)) return;

        detonating = true;
        for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY);

        // Source func_147480_a(x, y, z, false) == destroyBlock without drops (slots already cleared).
        server.removeBlock(worldPosition, false);

        double x = worldPosition.getX() + 0.5D;
        double y = worldPosition.getY() + 0.5D;
        double z = worldPosition.getZ() + 0.5D;

        BalefireEntity balefire = BalefireEntity.create(server, x, y, z, 250);
        server.addFreshEntity(balefire);

        // Source EntityNukeTorex.statFacBale(world, x, y, z, 250): green-forward Torex cloud.
        MushroomCloudEntity cloud = new MushroomCloudEntity(ModEntities.MUSHROOM_CLOUD.get(), server);
        cloud.setPos(x, y, z);
        cloud.configureBale(250);
        server.addFreshEntity(cloud);
    }

    public String getMinutes() {
        String mins = "" + (timer / 1200);
        if (mins.length() == 1) mins = "0" + mins;
        return mins;
    }

    public String getSeconds() {
        String secs = "" + ((timer / 20) % 60);
        if (secs.length() == 1) secs = "0" + secs;
        return secs;
    }

    public boolean started() { return started; }
    public int timer() { return timer; }
    public boolean detonating() { return detonating; }

    public void dropContents() {
        if (level == null) return;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5D,
                        worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D, stack);
            }
        }
        for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        // Source writeToNBT persists only started + timer (slots via super); loaded is recomputed.
        tag.putBoolean("started", started);
        tag.putInt("timer", timer);
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        started = tag.getBoolean("started");
        timer = tag.getInt("timer");
        customName = tag.contains("name") ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("loaded", loaded);
        tag.putBoolean("started", started);
        tag.putInt("timer", timer);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loaded = tag.getBoolean("loaded");
        started = tag.getBoolean("started");
        timer = tag.getInt("timer");
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }

    @Override public int getMaxStackSize() { return 64; }
    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public int[] getSlotsForFace(Direction side) { return NO_AUTOMATION; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) { return false; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }

    public void setCustomName(Component name) { customName = name; setChanged(); }
    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.nukeFstbmb");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new NukeFstbmbMenu(id, inventory, this);
    }
}
