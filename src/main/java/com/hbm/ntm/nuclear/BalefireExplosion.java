package com.hbm.ntm.nuclear;

import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Spiral balefire column eraser, driven one cell per tick by {@link BalefireEntity}.
 * Schrabidium clusters do not exist yet, so only the stone-to-slaked-Sellafite mutation remains.
 */
public final class BalefireExplosion {
    public int posX;
    public int posY;
    public int posZ;
    public int lastposX = 0;
    public int lastposZ = 0;
    public int radius;
    public int radius2;
    private final ServerLevel level;
    private int n = 1;
    private int nlimit;
    private int shell;
    private int leg;
    private int element;

    public BalefireExplosion(int x, int y, int z, ServerLevel level, int rad) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        this.level = level;
        this.radius = rad;
        this.radius2 = this.radius * this.radius;
        this.nlimit = this.radius2 * 4;
    }

    public void saveToNbt(CompoundTag nbt, String name) {
        nbt.putInt(name + "posX", posX);
        nbt.putInt(name + "posY", posY);
        nbt.putInt(name + "posZ", posZ);
        nbt.putInt(name + "lastposX", lastposX);
        nbt.putInt(name + "lastposZ", lastposZ);
        nbt.putInt(name + "radius", radius);
        nbt.putInt(name + "radius2", radius2);
        nbt.putInt(name + "n", n);
        nbt.putInt(name + "nlimit", nlimit);
        nbt.putInt(name + "shell", shell);
        nbt.putInt(name + "leg", leg);
        nbt.putInt(name + "element", element);
    }

    public void readFromNbt(CompoundTag nbt, String name) {
        posX = nbt.getInt(name + "posX");
        posY = nbt.getInt(name + "posY");
        posZ = nbt.getInt(name + "posZ");
        lastposX = nbt.getInt(name + "lastposX");
        lastposZ = nbt.getInt(name + "lastposZ");
        radius = nbt.getInt(name + "radius");
        radius2 = nbt.getInt(name + "radius2");
        n = Math.max(nbt.getInt(name + "n"), 1); // prevents invalid read operation
        nlimit = nbt.getInt(name + "nlimit");
        shell = nbt.getInt(name + "shell");
        leg = nbt.getInt(name + "leg");
        element = nbt.getInt(name + "element");
    }

    public int spiralIndex() { return n; }

    public boolean update() {
        if (n == 0) return true;

        breakColumn(this.lastposX, this.lastposZ);
        this.shell = (int) Math.floor((Math.sqrt(n) + 1) / 2);
        int shell2 = this.shell * 2;

        if (shell2 == 0) return true;

        this.leg = (int) Math.floor((double) (this.n - (shell2 - 1) * (shell2 - 1)) / shell2);
        this.element = (this.n - (shell2 - 1) * (shell2 - 1)) - shell2 * this.leg - this.shell + 1;
        this.lastposX = this.leg == 0 ? this.shell : this.leg == 1 ? -this.element : this.leg == 2 ? -this.shell : this.element;
        this.lastposZ = this.leg == 0 ? this.element : this.leg == 1 ? this.shell : this.leg == 2 ? -this.element : -this.shell;
        this.n++;
        return this.n > this.nlimit;
    }

    private void breakColumn(int x, int z) {
        int dist = (int) (radius - Math.sqrt(x * x + z * z));

        if (dist > 0) {
            int pX = posX + x;
            int pZ = posZ + z;

            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pX, pZ);
            int maxdepth = (int) (10 + radius * 0.25);
            int depth = (int) ((maxdepth * dist / radius) + (Math.sin(dist * 0.15 + 2) * 2));

            // Source clamps at the 1.7.10 world floor (0); mapped to each dimension's floor.
            depth = Math.max(y - depth, level.getMinBuildHeight());

            while (y > depth) {
                // Schrabidium cluster mutation can return when those blocks exist.
                level.setBlock(new BlockPos(pX, y, pZ), Blocks.AIR.defaultBlockState(), 3);
                y--;
            }

            if (level.random.nextInt(10) == 0) {
                level.setBlock(new BlockPos(pX, depth + 1, pZ),
                        ModBlocks.BALEFIRE.get().defaultBlockState(), 3);
                // Source schrabidium cluster branch omitted here as well.
            }

            for (int i = depth; i > depth - 5; i--) {
                BlockPos pos = new BlockPos(pX, i, pZ);
                BlockState state = level.getBlockState(pos);
                if (state.is(Blocks.STONE)) {
                    level.setBlock(pos, ModBlocks.SELLAFIELD_SLAKED.get().defaultBlockState(), 3);
                }
            }
        }
    }
}
