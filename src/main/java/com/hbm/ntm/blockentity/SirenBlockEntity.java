package com.hbm.ntm.blockentity;

import com.hbm.ntm.inventory.SirenMenu;
import com.hbm.ntm.item.SirenTrackItem;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
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

import java.util.function.Consumer;

/** A redstone-operated argument with the entire neighborhood. */
public final class SirenBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    private static final int[] CASSETTE_SLOT = new int[]{0};
    private static Consumer<SirenBlockEntity> clientEffectTick = siren -> { };

    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private Component customName;
    private boolean lock;
    private boolean active;
    private int pulseSerial;
    private SirenTrackItem.Track soundTrack = SirenTrackItem.Track.NONE;
    private int lastSyncedTrack = -1;
    private boolean lastSyncedActive;
    private int lastSyncedPulse = -1;

    public SirenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE_SIREN.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SirenBlockEntity siren) {
        if (level.isClientSide) clientEffectTick.accept(siren);
        else siren.serverTick((ServerLevel) level, pos, state);
    }

    private void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        SirenTrackItem.Track track = currentTrack();
        soundTrack = track;
        boolean nextActive = false;

        // The old tile returned here too. Pulling the tape does not reset a latched one-shot.
        if (track != SirenTrackItem.Track.NONE) {
            boolean powered = level.hasNeighborSignal(pos);
            if (track.type() == SirenTrackItem.SoundType.LOOP) {
                nextActive = powered;
            } else {
                if (!lock && powered) {
                    lock = true;
                    pulseSerial++;
                }
                if (lock && !powered) lock = false;
            }
        }

        active = nextActive;
        int trackId = track.legacyMetadata();
        if (trackId != lastSyncedTrack || active != lastSyncedActive || pulseSerial != lastSyncedPulse) {
            lastSyncedTrack = trackId;
            lastSyncedActive = active;
            lastSyncedPulse = pulseSerial;
            setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public static void installClientEffectTick(Consumer<SirenBlockEntity> tick) {
        clientEffectTick = tick;
    }

    public SirenTrackItem.Track currentTrack() {
        return SirenTrackItem.track(items.getFirst());
    }

    public SirenTrackItem.Track soundTrack() { return soundTrack; }
    public boolean active() { return active; }
    public int pulseSerial() { return pulseSerial; }

    public void setCustomName(Component name) {
        customName = name;
        setChanged();
    }

    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.siren");
    }

    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SirenMenu(id, inventory, this);
    }

    @Override public int getContainerSize() { return 1; }
    @Override public int getMaxStackSize() { return 1; }
    @Override public boolean isEmpty() { return items.getFirst().isEmpty(); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }

    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > 1) stack.setCount(1);
        setChanged();
    }

    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getCenter()) <= 64.0D;
    }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == 0 && stack.getItem() instanceof SirenTrackItem;
    }

    @Override public void clearContent() {
        items.set(0, ItemStack.EMPTY);
        setChanged();
    }

    @Override public int[] getSlotsForFace(Direction side) { return CASSETTE_SLOT; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return false;
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        customName = tag.contains("name")
                ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("track", soundTrack.legacyMetadata());
        tag.putBoolean("active", active);
        tag.putInt("pulse", pulseSerial);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        soundTrack = SirenTrackItem.Track.byMetadata(tag.getInt("track"));
        active = tag.getBoolean("active");
        pulseSerial = tag.getInt("pulse");
    }

    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }

    // GameTest does not need to borrow a redstone torch from the prop department.
    public void tickForTest(ServerLevel level) { serverTick(level, worldPosition, getBlockState()); }
}
