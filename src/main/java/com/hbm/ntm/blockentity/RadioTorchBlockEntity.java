package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.RadioTorchBlock;
import com.hbm.ntm.inventory.RadioTorchMenu;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.ror.RorFunctionException;
import com.hbm.ntm.ror.RorInteractive;
import com.hbm.ntm.ror.RorValueProvider;
import com.hbm.ntm.ror.RttySystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;

public final class RadioTorchBlockEntity extends BlockEntity implements Container, MenuProvider {
    private final NonNullList<ItemStack> patterns = NonNullList.withSize(3, ItemStack.EMPTY);
    private final String[] channels = new String[8];
    private final String[] names = new String[8];
    private final String[] mapping = new String[16];
    private final byte[] conditions = new byte[16];
    private final byte[] patternModes = new byte[3];
    private final String[] patternTags = {"", "", ""};
    private final String[] previous = new String[8];
    private int output;
    private int lastInput = -1;
    private long lastMessageTick = Long.MIN_VALUE;
    private boolean polling;
    private boolean customMapping;
    private boolean descending;

    public RadioTorchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RADIO_TORCH.get(), pos, state);
        Arrays.fill(channels, "");
        Arrays.fill(names, "");
        Arrays.fill(mapping, "");
        Arrays.fill(previous, "");
        if (kind() == RadioTorchBlock.Kind.CONTROLLER) polling = true;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, RadioTorchBlockEntity radio) {
        if (level instanceof ServerLevel server) radio.serverTick(server, pos, state);
    }

    private void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        switch (kind()) {
            case SENDER -> tickSender(level, pos, state);
            case RECEIVER -> tickReceiver(level, pos, state, false);
            case LOGIC -> tickReceiver(level, pos, state, true);
            case READER -> tickReader(level, state);
            case CONTROLLER -> tickController(level, pos, state);
            case COUNTER -> tickCounter(level, state);
        }
    }

    private void tickSender(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos support = attachedPos();
        int signal = level.getBestNeighborSignal(support);
        BlockState attached = level.getBlockState(support);
        if (attached.hasAnalogOutputSignal()) signal = Math.max(signal, attached.getAnalogOutputSignal(level, support));
        signal = Mth.clamp(signal, 0, 15);
        if (polling || signal != lastInput) {
            String value = customMapping ? mapping[signal] : Integer.toString(signal);
            if (!value.isEmpty()) RttySystem.broadcast(level, channels[0], value);
            lastInput = signal;
            setOutput(level, pos, state, signal);
        }
    }

    private void tickReceiver(ServerLevel level, BlockPos pos, BlockState state, boolean logic) {
        RttySystem.Message message = RttySystem.listen(level, channels[0]);
        if (message == null) {
            if (polling && level.getGameTime() - lastMessageTick > 2) setOutput(level, pos, state, 0);
            return;
        }
        if (!polling && message.tick() == lastMessageTick) return;
        lastMessageTick = message.tick();
        String value = message.value();
        if (value.equals("selfdestruct")) {
            level.removeBlock(pos, false);
            level.explode(null, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 5, Level.ExplosionInteraction.BLOCK);
            return;
        }
        setOutput(level, pos, state, logic ? logicOutput(value) : receiverOutput(value));
    }

    private int receiverOutput(String value) {
        if (customMapping) {
            for (int i = 15; i >= 0; i--) if (!mapping[i].isEmpty() && mapping[i].equals(value)) return i;
            return 0;
        }
        try { return Mth.clamp(Integer.parseInt(value), 0, 15); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private int logicOutput(String value) {
        if (descending) {
            for (int i = 15; i >= 0; i--) if (matches(value, mapping[i], conditions[i])) return i;
        } else {
            for (int i = 0; i < 16; i++) if (matches(value, mapping[i], conditions[i])) return i;
        }
        return 0;
    }

    private static boolean matches(String input, String constant, int condition) {
        if (constant.isEmpty()) return false;
        if (condition <= 5) {
            try {
                long a = Long.parseLong(input), b = Long.parseLong(constant);
                return switch (condition) {
                    case 0 -> a < b; case 1 -> a <= b; case 2 -> a >= b;
                    case 3 -> a > b; case 4 -> a == b; default -> a != b;
                };
            } catch (NumberFormatException ignored) { return false; }
        }
        return switch (condition) {
            case 7 -> !input.equals(constant);
            case 8 -> input.contains(constant);
            case 9 -> !input.contains(constant);
            default -> input.equals(constant);
        };
    }

    private void tickReader(ServerLevel level, BlockState state) {
        if (!(level.getBlockEntity(attachedPos()) instanceof RorValueProvider provider)) return;
        for (int i = 0; i < 8; i++) {
            if (channels[i].isEmpty() || names[i].isEmpty()) continue;
            String value = provider.provideRorValue(RorValueProvider.VALUE_PREFIX + names[i].toLowerCase(Locale.ROOT));
            if (value == null) continue;
            if (polling || !value.equals(previous[i])) RttySystem.broadcast(level, channels[i], value);
            previous[i] = value;
        }
        changed(level, state);
    }

    private void tickController(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(attachedPos()) instanceof RorInteractive target)) return;
        RttySystem.Message message = RttySystem.listen(level, channels[0]);
        if (message == null || !((polling && message.tick() >= level.getGameTime() - 1)
                || !message.value().equals(previous[0]))) return;
        if (message.value().equals("selfdestruct")) {
            level.removeBlock(pos, false);
            level.explode(null, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 5, Level.ExplosionInteraction.BLOCK);
            return;
        }
        try {
            target.runRorFunction(RorInteractive.FUNCTION_PREFIX + RorInteractive.command(message.value()),
                    RorInteractive.parameters(message.value()));
        } catch (RorFunctionException ignored) {
            // Radio heard it just fine. The machine chose violence through bureaucracy.
        }
        previous[0] = message.value();
        changed(level, state);
    }

    private void tickCounter(ServerLevel level, BlockState state) {
        if (!(level.getBlockEntity(attachedPos()) instanceof Container inventory)) return;
        for (int pattern = 0; pattern < 3; pattern++) {
            ItemStack wanted = patterns.get(pattern);
            if (wanted.isEmpty() || channels[pattern].isEmpty()) continue;
            int count = 0;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack found = inventory.getItem(slot);
                if (patternMatches(pattern, wanted, found)) count += found.getCount();
            }
            String value = Integer.toString(count);
            if (polling || !value.equals(previous[pattern])) RttySystem.broadcast(level, channels[pattern], value);
            previous[pattern] = value;
        }
        changed(level, state);
    }

    private boolean patternMatches(int slot, ItemStack wanted, ItemStack found) {
        if (found.isEmpty()) return false;
        return switch (patternModes[slot]) {
            case 1 -> found.is(wanted.getItem());
            case 2 -> !patternTags[slot].isEmpty() && found.is(TagKey.create(Registries.ITEM,
                    ResourceLocation.parse(patternTags[slot])));
            default -> ItemStack.isSameItemSameComponents(wanted, found);
        };
    }

    private void setOutput(ServerLevel level, BlockPos pos, BlockState state, int next) {
        next = Mth.clamp(next, 0, 15);
        if (next == output) return;
        output = next;
        BlockState updated = state.setValue(RadioTorchBlock.LIT, output > 0);
        level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        level.updateNeighborsAt(pos, state.getBlock());
        level.updateNeighborsAt(attachedPos(), state.getBlock());
        changed(level, updated);
    }

    private void changed(ServerLevel level, BlockState state) {
        setChanged();
        if (level.getGameTime() % 10 == 0) level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    public RadioTorchBlock.Kind kind() {
        return getBlockState().getBlock() instanceof RadioTorchBlock block ? block.kind() : RadioTorchBlock.Kind.SENDER;
    }
    public BlockPos attachedPos() { return worldPosition.relative(getBlockState().getValue(RadioTorchBlock.FACING).getOpposite()); }
    public int output() { return output; }
    public boolean polling() { return polling; }
    public boolean customMapping() { return customMapping; }
    public boolean descending() { return descending; }
    public String channel(int i) { return channels[Mth.clamp(i, 0, 7)]; }
    public String name(int i) { return names[Mth.clamp(i, 0, 7)]; }
    public String mapping(int i) { return mapping[Mth.clamp(i, 0, 15)]; }
    public int condition(int i) { return conditions[Mth.clamp(i, 0, 15)]; }
    public String patternMode(int i) {
        i = Mth.clamp(i, 0, 2);
        return switch (patternModes[i]) {
            case 1 -> "Wildcard";
            case 2 -> patternTags[i].isEmpty() ? "Tag" : "Tag: #" + patternTags[i];
            default -> "Exact";
        };
    }

    public void configure(boolean polling, boolean customMapping, boolean descending,
                          String[] channels, String[] names, String[] mapping, byte[] conditions) {
        this.polling = polling;
        this.customMapping = customMapping;
        this.descending = descending;
        copy(channels, this.channels, 15);
        copy(names, this.names, 25);
        copy(mapping, this.mapping, 32);
        System.arraycopy(conditions, 0, this.conditions, 0, Math.min(conditions.length, this.conditions.length));
        for (int i = 0; i < this.conditions.length; i++) this.conditions[i] = (byte) Mth.clamp(this.conditions[i], 0, 9);
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private static void copy(String[] source, String[] destination, int limit) {
        for (int i = 0; i < destination.length; i++) {
            String value = i < source.length && source[i] != null ? source[i] : "";
            destination[i] = value.substring(0, Math.min(value.length(), limit));
        }
    }

    public void setPattern(int index, ItemStack stack) {
        if (index < 0 || index >= patterns.size()) return;
        patterns.set(index, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        patternModes[index] = (byte) (stack.isEmpty() || stack.getComponents().isEmpty() ? 1 : 0);
        patternTags[index] = "";
        setChanged();
    }

    public void cyclePatternMode(int index) {
        if (index < 0 || index >= patterns.size() || patterns.get(index).isEmpty()) return;
        var tags = patterns.get(index).getItem().builtInRegistryHolder().tags().map(tag -> tag.location().toString()).toList();
        if (patternModes[index] == 0) {
            patternModes[index] = 1;
        } else if (patternModes[index] == 1 && !tags.isEmpty()) {
            patternModes[index] = 2; patternTags[index] = tags.getFirst();
        } else if (patternModes[index] == 2) {
            int current = tags.indexOf(patternTags[index]);
            if (current >= 0 && current + 1 < tags.size()) patternTags[index] = tags.get(current + 1);
            else { patternModes[index] = 0; patternTags[index] = ""; }
        } else {
            patternModes[index] = 0; patternTags[index] = "";
        }
        setChanged();
    }

    @Override public Component getDisplayName() {
        return Component.translatable("container.rtty" + switch (kind()) {
            case SENDER -> "Sender"; case RECEIVER -> "Receiver"; case COUNTER -> "Counter";
            case LOGIC -> "Logic"; case READER -> "Reader"; case CONTROLLER -> "Controller";
        });
    }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new RadioTorchMenu(id, inventory, this);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("polling", polling); tag.putBoolean("custom", customMapping); tag.putBoolean("descending", descending);
        tag.putInt("output", output); tag.putInt("input", lastInput); tag.putLong("messageTick", lastMessageTick);
        for (int i = 0; i < 8; i++) { tag.putString("channel" + i, channels[i]); tag.putString("name" + i, names[i]); tag.putString("previous" + i, previous[i]); }
        for (int i = 0; i < 16; i++) { tag.putString("mapping" + i, mapping[i]); tag.putByte("condition" + i, conditions[i]); }
        for (int i = 0; i < 3; i++) { tag.putByte("patternMode" + i, patternModes[i]); tag.putString("patternTag" + i, patternTags[i]); }
        ContainerHelper.saveAllItems(tag, patterns, registries);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        polling = tag.getBoolean("polling"); customMapping = tag.getBoolean("custom"); descending = tag.getBoolean("descending");
        output = tag.getInt("output"); lastInput = tag.getInt("input"); lastMessageTick = tag.getLong("messageTick");
        for (int i = 0; i < 8; i++) { channels[i] = tag.getString("channel" + i); names[i] = tag.getString("name" + i); previous[i] = tag.getString("previous" + i); }
        for (int i = 0; i < 16; i++) { mapping[i] = tag.getString("mapping" + i); conditions[i] = tag.getByte("condition" + i); }
        for (int i = 0; i < 3; i++) { patternModes[i] = tag.getByte("patternMode" + i); patternTags[i] = tag.getString("patternTag" + i); }
        ContainerHelper.loadAllItems(tag, patterns, registries);
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override public int getContainerSize() { return 3; }
    @Override public boolean isEmpty() { return patterns.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return patterns.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) { ItemStack old = patterns.get(slot); patterns.set(slot, ItemStack.EMPTY); setChanged(); return old; }
    @Override public ItemStack removeItemNoUpdate(int slot) { ItemStack old = patterns.get(slot); patterns.set(slot, ItemStack.EMPTY); return old; }
    @Override public void setItem(int slot, ItemStack stack) { setPattern(slot, stack); }
    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }
    @Override public void clearContent() { patterns.clear(); setChanged(); }
}
