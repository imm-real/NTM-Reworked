package com.hbm.ntm.blockentity;

import com.hbm.ntm.autocal.IParse;
import com.hbm.ntm.autocal.ParseMSES1Ext1;
import com.hbm.ntm.inventory.RadioAutocalMenu;
import com.hbm.ntm.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class RadioAutocalBlockEntity extends BlockEntity implements MenuProvider {
    private final IParse parser = new ParseMSES1Ext1();
    private final String[] history = {"", "", "", "", "", ""};
    private boolean on;
    private boolean ignoreError;
    private boolean autoReboot;
    private String[] script = new String[0];
    private IParse.ParseContext context;

    public RadioAutocalBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.RADIO_AUTOCAL.get(), position, state);
    }

    public static void tick(Level level, BlockPos position, BlockState state,
                            RadioAutocalBlockEntity autocal) {
        if (level instanceof ServerLevel server) autocal.serverTick(server, position, state);
    }

    private void serverTick(ServerLevel level, BlockPos position, BlockState state) {
        if (level.getGameTime() % 60L == 0L) setChanged();
        if (context == null) context = new IParse.ParseContext(level);
        if (context.level() != level) context.setLevel(level);

        if (!on && autoReboot) on = true;
        if (on) {
            int emergencyBrake = 100;
            for (int cycle = 0; cycle < context.clockSpeed() && emergencyBrake > 0; cycle++) {
                emergencyBrake--;
                if (context.current() == script.length) {
                    stop("Program has terminated");
                    break;
                }
                if (context.current() < 0 || context.current() >= script.length) {
                    stop("Program index is out of bounds");
                    break;
                }

                try {
                    int index = context.current();
                    context.setCurrent(index + 1);
                    String line = script[index];
                    IParse.StatementResult result = parser.eval(context, line);
                    if (result != IParse.StatementResult.SKIP) pushMessage(index + ": " + line);
                    history[0] = "Buffer: " + context.readBuffer();
                    if (result == IParse.StatementResult.END_TICK) break;
                    if (result == IParse.StatementResult.SHUTDOWN) {
                        stop("Program requested shutdown");
                    }
                    if (!ignoreError) {
                        if (result == IParse.StatementResult.UNRECOGNIZED_COMMAND) {
                            stop("Unrecognized command");
                        }
                        if (result == IParse.StatementResult.PARAMETER_ERROR) stop("Parameter error");
                        if (result == IParse.StatementResult.UNDEFINED) stop("Undefined behavior");
                        if (result == IParse.StatementResult.STACK_EXCEEDED) {
                            stop("Stack exceeded capacity");
                        }
                    }
                    if (result == IParse.StatementResult.SKIP) cycle--;
                } catch (Exception ignored) {
                    stop("Evaluation unsuccessful");
                }
            }
        }

        if (level.getGameTime() % 15L == 0L) {
            level.sendBlockUpdated(position, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private void pushMessage(String message) {
        for (int index = 2; index < history.length; index++) {
            history[index - 1] = history[index];
        }
        history[history.length - 1] = message;
    }

    private void stop(String reason) {
        on = false;
        context.turnOff();
        pushMessage(reason);
    }

    public void handleCommand(String command, String payload) {
        if (context == null && level instanceof ServerLevel server) {
            context = new IParse.ParseContext(server);
        }
        switch (command) {
            case "on" -> {
                if (on) stop("User requested shutdown");
                else on = true;
            }
            case "ignore" -> ignoreError = !ignoreError;
            case "auto" -> autoReboot = !autoReboot;
            case "upload" -> loadScript(payload);
            default -> {
            }
        }
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public void loadScript(String payload) {
        if (context == null && level instanceof ServerLevel server) {
            context = new IParse.ParseContext(server);
        }
        if (context == null) return;
        context.jumps().clear();
        script = payload.split("\n");
        for (int index = 0; index < script.length; index++) {
            script[index] = script[index].trim();
            parser.generateJumpPoints(context, script[index], index);
        }
        if (on) stop("Script has changed");
        setChanged();
    }

    public boolean isOn() {
        return on;
    }

    public boolean ignoreError() {
        return ignoreError;
    }

    public boolean autoReboot() {
        return autoReboot;
    }

    public String history(int index) {
        return history[Math.max(0, Math.min(index, history.length - 1))];
    }

    public String[] script() {
        return script.clone();
    }

    public String buffer() {
        return context == null ? "" : context.readBuffer();
    }

    public int instruction() {
        return context == null ? 0 : context.current();
    }

    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + .5D, worldPosition.getY() + 1D,
                worldPosition.getZ() + .5D) <= 225D;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.autocal");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new RadioAutocalMenu(id, inventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("isOn", on);
        tag.putBoolean("ignoreError", ignoreError);
        tag.putBoolean("autoReboot", autoReboot);
        ListTag lines = new ListTag();
        for (String line : script) lines.add(StringTag.valueOf(line));
        tag.put("script", lines);
        if (context != null) context.save(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        on = tag.getBoolean("isOn");
        ignoreError = tag.getBoolean("ignoreError");
        autoReboot = tag.getBoolean("autoReboot");
        ListTag lines = tag.getList("script", Tag.TAG_STRING);
        script = new String[lines.size()];
        for (int index = 0; index < script.length; index++) script[index] = lines.getString(index);
        context = new IParse.ParseContext(null);
        context.load(tag, script, parser);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("isOn", on);
        tag.putBoolean("ignoreError", ignoreError);
        tag.putBoolean("autoReboot", autoReboot);
        for (int index = 0; index < history.length; index++) {
            tag.putString("history" + index, history[index]);
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        on = tag.getBoolean("isOn");
        ignoreError = tag.getBoolean("ignoreError");
        autoReboot = tag.getBoolean("autoReboot");
        for (int index = 0; index < history.length; index++) {
            history[index] = tag.getString("history" + index);
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
