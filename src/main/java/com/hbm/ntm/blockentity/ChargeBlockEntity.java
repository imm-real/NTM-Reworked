package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.ChargeBlock;
import com.hbm.ntm.network.ChargeStatePayload;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ChargeBlockEntity extends BlockEntity {
    private boolean started;
    private int timer;
    private boolean safeRemoval;

    public ChargeBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.CHARGE.get(), position, state);
    }

    public static void tick(Level level, BlockPos position, BlockState state, ChargeBlockEntity charge) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (charge.started) {
            charge.timer--;
            if (charge.timer > 0 && charge.timer % 20 == 0) {
                serverLevel.playSound(null, position, ModSounds.CHARGE_PING.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            if (charge.timer <= 0 && state.getBlock() instanceof ChargeBlock block) {
                block.detonate(serverLevel, position);
                return;
            }
        }

        PacketDistributor.sendToPlayersNear(
                serverLevel,
                null,
                position.getX() + 0.5D,
                position.getY() + 0.5D,
                position.getZ() + 0.5D,
                100.0D,
                new ChargeStatePayload(position, charge.timer, charge.started)
        );
    }

    public void cycleTimer() {
        timer = switch (timer) {
            case 0 -> 100;
            case 100 -> 200;
            case 200 -> 300;
            case 300 -> 600;
            case 600 -> 1_200;
            case 1_200 -> 3_600;
            case 3_600 -> 6_000;
            default -> 0;
        };
        setChanged();
    }

    public boolean arm() {
        if (started || timer <= 0) {
            return false;
        }
        started = true;
        setChanged();
        return true;
    }

    public boolean pause() {
        if (!started) {
            return false;
        }
        started = false;
        setChanged();
        return true;
    }

    public void applyClientState(int timer, boolean started) {
        this.timer = timer;
        this.started = started;
    }

    public int timer() { return timer; }
    public boolean started() { return started; }
    public String minutes() { return twoDigits(timer / 1_200); }
    public String seconds() { return twoDigits((timer / 20) % 60); }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }

    public boolean safeRemoval() { return safeRemoval; }
    public void setSafeRemoval(boolean safeRemoval) { this.safeRemoval = safeRemoval; }
}
