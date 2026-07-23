package com.hbm.ntm.blockentity;

import com.hbm.ntm.inventory.RadioTelexMenu;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.ror.RttySystem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class RadioTelexBlockEntity extends BlockEntity implements MenuProvider {
    public static final int LINE_WIDTH = 33;
    public static final char EOL = '\n';
    public static final char EOT = '\u0004';
    public static final char BELL = '\u0007';
    public static final char PRINT = '\u000c';
    public static final char PAUSE = '\u0016';
    public static final char CLEAR = '\u007f';

    private final String[] txBuffer = {"", "", "", "", ""};
    private final String[] rxBuffer = {"", "", "", "", ""};
    private String txChannel = "";
    private String rxChannel = "";
    private int sendingLine;
    private int sendingIndex;
    private boolean sending;
    private int sendingWait;
    private int writingLine;
    private boolean printAfterReceive;
    private boolean deleteOnReceive = true;
    private char sendingChar = ' ';
    private long lastReceiveTick = Long.MIN_VALUE;

    public RadioTelexBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.RADIO_TELEX.get(), position, state);
    }

    public static void tick(Level level, BlockPos position, BlockState state, RadioTelexBlockEntity telex) {
        if (level instanceof ServerLevel server) telex.serverTick(server, position, state);
    }

    private void serverTick(ServerLevel level, BlockPos position, BlockState state) {
        sendingChar = ' ';
        if (sending && txChannel.isEmpty()) sending = false;

        if (sending) {
            if (sendingWait > 0) {
                sendingWait--;
            } else {
                String line = txBuffer[sendingLine];
                if (sendingIndex < line.length()) {
                    char next = line.charAt(sendingIndex++);
                    if (next == PAUSE) {
                        sendingWait = 20;
                    } else {
                        RttySystem.broadcast(level, txChannel, Character.toString(next));
                        sendingChar = next;
                    }
                } else if (sendingLine >= 4) {
                    sending = false;
                    RttySystem.broadcast(level, txChannel, Character.toString(EOT));
                    sendingLine = 0;
                    sendingIndex = 0;
                } else {
                    RttySystem.broadcast(level, txChannel, Character.toString(EOL));
                    sendingLine++;
                    sendingIndex = 0;
                }
            }
        }

        receive(level);
        if (level.getGameTime() % 16L == 0L) {
            setChanged();
            level.sendBlockUpdated(position, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private void receive(ServerLevel level) {
        if (rxChannel.isEmpty()) return;
        RttySystem.Message message = RttySystem.listen(level, rxChannel);
        if (message == null || message.tick() == lastReceiveTick
                || message.tick() <= level.getGameTime() - 2L || message.value().length() != 1) return;
        lastReceiveTick = message.tick();
        char received = message.value().charAt(0);

        if (deleteOnReceive) {
            deleteOnReceive = false;
            clearReceiveBuffer();
        }

        if (received == EOT) {
            if (printAfterReceive) {
                printAfterReceive = false;
                printMessage();
            }
            deleteOnReceive = true;
        } else if (received == EOL) {
            if (writingLine < 4) writingLine++;
            setChanged();
        } else if (received == BELL) {
            level.playSound(null, worldPosition, SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.BLOCKS, 2F, .5F);
        } else if (received == PRINT) {
            printAfterReceive = true;
        } else if (received == CLEAR) {
            clearReceiveBuffer();
        } else {
            rxBuffer[writingLine] += received;
            setChanged();
        }
    }

    public void handleCommand(String command, String[] lines, String tx, String rx) {
        copyLines(lines, txBuffer);
        switch (command) {
            case "snd" -> {
                if (!sending) {
                    sending = true;
                    sendingLine = 0;
                    sendingIndex = 0;
                }
            }
            case "rxprt" -> printMessage();
            case "rxcls" -> clearReceiveBuffer();
            case "sve" -> {
                txChannel = trim(tx, 10);
                rxChannel = trim(rx, 10);
            }
            default -> { }
        }
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public void printMessage() {
        if (!(level instanceof ServerLevel server)) return;
        List<Component> lines = new ArrayList<>();
        for (String line : rxBuffer) if (!line.isEmpty()) lines.add(formatLore(line));
        ItemStack paper = new ItemStack(Items.PAPER);
        paper.set(DataComponents.CUSTOM_NAME, Component.literal("Message"));
        paper.set(DataComponents.LORE, new ItemLore(lines));
        server.addFreshEntity(new ItemEntity(server, worldPosition.getX() + .5D,
                worldPosition.getY() + 1D, worldPosition.getZ() + .5D, paper));
    }

    private void clearReceiveBuffer() {
        Arrays.fill(rxBuffer, "");
        writingLine = 0;
        setChanged();
    }

    private static void copyLines(String[] source, String[] destination) {
        for (int i = 0; i < destination.length; i++) {
            destination[i] = trim(i < source.length ? source[i] : "", LINE_WIDTH);
        }
    }

    private static Component formatLore(String text) {
        MutableComponent result = Component.empty();
        Style style = Style.EMPTY.applyLegacyFormat(ChatFormatting.GRAY);
        int start = 0;
        for (int index = 0; index + 1 < text.length(); index++) {
            if (text.charAt(index) != '\u00a7') continue;
            if (index > start) {
                result.append(Component.literal(text.substring(start, index)).setStyle(style));
            }
            ChatFormatting format = ChatFormatting.getByCode(text.charAt(index + 1));
            if (format != null) style = style.applyLegacyFormat(format);
            index++;
            start = index + 1;
        }
        if (start < text.length()) {
            result.append(Component.literal(text.substring(start)).setStyle(style));
        }
        return result;
    }

    private static String trim(@Nullable String value, int limit) {
        if (value == null) return "";
        return value.substring(0, Math.min(value.length(), limit));
    }

    public String txLine(int line) {
        return txBuffer[Math.max(0, Math.min(line, 4))];
    }

    public String rxLine(int line) {
        return rxBuffer[Math.max(0, Math.min(line, 4))];
    }

    public String[] txLines() {
        return txBuffer.clone();
    }

    public String txChannel() {
        return txChannel;
    }

    public String rxChannel() {
        return rxChannel;
    }

    public char sendingChar() {
        return sendingChar;
    }

    public boolean isSending() {
        return sending;
    }

    public void setChannels(String tx, String rx) {
        txChannel = trim(tx, 10);
        rxChannel = trim(rx, 10);
        setChanged();
    }

    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + .5D, worldPosition.getY() + .5D,
                worldPosition.getZ() + .5D) < 256D;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.hbm.radio_telex");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new RadioTelexMenu(id, inventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (int i = 0; i < 5; i++) {
            tag.putString("tx" + i, txBuffer[i]);
            tag.putString("rx" + i, rxBuffer[i]);
        }
        tag.putString("txChan", txChannel);
        tag.putString("rxChan", rxChannel);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (int i = 0; i < 5; i++) {
            txBuffer[i] = tag.getString("tx" + i);
            rxBuffer[i] = tag.getString("rx" + i);
        }
        txChannel = tag.getString("txChan");
        rxChannel = tag.getString("rxChan");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        for (int i = 0; i < 5; i++) {
            tag.putString("tx" + i, txBuffer[i]);
            tag.putString("rx" + i, rxBuffer[i]);
        }
        tag.putString("txChan", txChannel);
        tag.putString("rxChan", rxChannel);
        tag.putInt("sendingChar", sendingChar);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
        sendingChar = (char) tag.getInt("sendingChar");
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
