package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.ThermalMultiblockBlock;
import com.hbm.ntm.entity.SawbladeEntity;
import com.hbm.ntm.network.SawmillStatePayload;
import com.hbm.ntm.radiation.ModDamageTypes;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModEntities;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.thermal.HeatSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Three slots and borrowed heat. The client sees it just before it vanishes. */
public final class SawmillBlockEntity extends BlockEntity implements WorldlyContainer {
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int BYPRODUCT_SLOT = 2;
    public static final int PROCESSING_TIME = 600;
    public static final int MIN_HEAT = 100;
    public static final int MAX_HEAT = 300;
    public static final int OVERSPEED_LIMIT = 300;
    public static final double DIFFUSION = 0.1D;
    private static final int[] ALL_SLOTS = {INPUT_SLOT, OUTPUT_SLOT, BYPRODUCT_SLOT};
    private static final TagKey<Item> WOODEN_RODS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "rods/wooden"));

    private final NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
    private int heat;
    private int synchronizedHeat;
    private int progress;
    private int warningCooldown;
    private int overspeed;
    private boolean hasBlade = true;
    private float spin;
    private float lastSpin;

    public SawmillBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_SAWMILL.get(), position, state);
    }

    public static void tick(Level level, BlockPos position, BlockState state, SawmillBlockEntity sawmill) {
        if (level.isClientSide) sawmill.clientTick();
        else sawmill.serverTick((ServerLevel) level, position, state);
    }

    private void serverTick(ServerLevel level, BlockPos position, BlockState state) {
        if (hasBlade) {
            pullHeat();
            if (warningCooldown > 0) warningCooldown--;

            if (heat >= MIN_HEAT) {
                ItemStack result = outputFor(items.get(INPUT_SLOT));
                if (!result.isEmpty()) {
                    progress += heat / 10;
                    if (progress >= PROCESSING_TIME) finishProcess(level, result);
                } else {
                    progress = 0;
                }
                hurtBladeArea(level, position, state.getValue(ThermalMultiblockBlock.FACING));
            } else {
                progress = 0;
            }

            if (heat > MAX_HEAT) {
                overspeed++;
                if (overspeed > 60 && warningCooldown == 0) {
                    warningCooldown = 100;
                    level.playSound(null, position.getX() + 0.5D, position.getY() + 1.0D,
                            position.getZ() + 0.5D, ModSounds.WARN_OVERSPEED.get(),
                            SoundSource.BLOCKS, 2.0F, 1.0F);
                }
                if (overspeed > OVERSPEED_LIMIT) fail(level, position, state);
            } else {
                overspeed = 0;
            }
        } else {
            overspeed = 0;
            warningCooldown = 0;
        }

        synchronizedHeat = heat;
        PacketDistributor.sendToPlayersNear(level, null,
                position.getX() + 0.5D, position.getY() + 1.0D, position.getZ() + 0.5D, 150.0D,
                new SawmillStatePayload(position, synchronizedHeat, progress, hasBlade,
                        items.get(INPUT_SLOT).copy(), items.get(OUTPUT_SLOT).copy(),
                        items.get(BYPRODUCT_SLOT).copy()));
        heat = 0;
        setChanged();
    }

    private void finishProcess(ServerLevel level, ItemStack result) {
        progress = 0;
        items.set(INPUT_SLOT, ItemStack.EMPTY);
        items.set(OUTPUT_SLOT, result.copy());
        if (!result.is(ModItems.POWDER_SAWDUST.get())) {
            float chance = result.is(Items.STICK) ? 0.1F : 0.5F;
            if (level.random.nextFloat() < chance) {
                items.set(BYPRODUCT_SLOT, new ItemStack(ModItems.POWDER_SAWDUST.get()));
            }
        }
    }

    private void pullHeat() {
        if (level != null && level.getBlockEntity(worldPosition.below()) instanceof HeatSource source) {
            int pulled = (int) (source.getHeatStored() * DIFFUSION);
            if (pulled > 0) {
                source.useUpHeat(pulled);
                heat += pulled;
                return;
            }
        }
        heat = Math.max(heat - Math.max(heat / 1000, 1), 0);
    }

    private void hurtBladeArea(ServerLevel level, BlockPos position, Direction facing) {
        Direction side = facing.getClockWise();
        double centerX = position.getX() + 0.5D + facing.getStepX() * 0.9375D;
        double centerZ = position.getZ() + 0.5D + facing.getStepZ() * 0.9375D;
        double halfX = Math.abs(facing.getStepX()) * 0.0625D + Math.abs(side.getStepX());
        double halfZ = Math.abs(facing.getStepZ()) * 0.0625D + Math.abs(side.getStepZ());
        AABB blade = new AABB(centerX - halfX, position.getY() + 0.375D, centerZ - halfZ,
                centerX + halfX, position.getY() + 2.375D, centerZ + halfZ);

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, blade)) {
            if (entity.isAlive() && entity.hurt(level.damageSources().source(ModDamageTypes.BLENDER), 100.0F)) {
                level.playSound(null, entity.blockPosition(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR,
                        SoundSource.BLOCKS, 2.0F, 0.95F + level.random.nextFloat() * 0.2F);
                int count = Math.min((int) Math.ceil(entity.getMaxHealth() / 4.0F), 250) * 4;
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                                Blocks.REDSTONE_BLOCK.defaultBlockState()),
                        entity.getX(), entity.getY() + entity.getBbHeight() * 0.5D, entity.getZ(), count,
                        entity.getBbWidth() * 0.25D, entity.getBbHeight() * 0.25D,
                        entity.getBbWidth() * 0.25D, 0.1D);
            }
        }
    }

    private void fail(ServerLevel level, BlockPos position, BlockState state) {
        hasBlade = false;
        ThermalMultiblockBlock.updateSawbladeState(level, position, false);
        level.explode(null, position.getX() + 0.5D, position.getY() + 1.0D, position.getZ() + 0.5D,
                5.0F, false, Level.ExplosionInteraction.NONE);

        Direction facing = state.getValue(ThermalMultiblockBlock.FACING);
        SawbladeEntity blade = new SawbladeEntity(ModEntities.SAWBLADE.get(), level);
        blade.setPos(position.getX() + 0.5D + facing.getStepX(), position.getY() + 1.0D,
                position.getZ() + 0.5D + facing.getStepZ());
        blade.setOrientation(facing);
        Direction tangent = facing.getCounterClockWise();
        blade.setDeltaMovement(tangent.getStepX(), 1.0D + (heat - MIN_HEAT) * 0.0001D,
                tangent.getStepZ());
        level.addFreshEntity(blade);
    }

    private void clientTick() {
        float momentum = heat * 25.0F / MAX_HEAT;
        lastSpin = spin;
        spin += momentum;
        if (spin >= 360.0F) {
            spin -= 360.0F;
            lastSpin -= 360.0F;
        }
    }

    public ItemStack outputFor(ItemStack input) {
        if (level == null || input.isEmpty()) return ItemStack.EMPTY;
        if (input.is(WOODEN_RODS)) return new ItemStack(ModItems.POWDER_SAWDUST.get());
        if (input.is(ItemTags.LOGS)) {
            CraftingInput crafting = CraftingInput.of(1, 1, List.of(input));
            ItemStack output = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, crafting, level)
                    .map(holder -> holder.value().assemble(crafting, level.registryAccess()))
                    .orElse(ItemStack.EMPTY);
            if (!output.isEmpty()) output.setCount(output.getCount() * 6 / 4);
            return output;
        }
        if (input.is(ItemTags.PLANKS)) return new ItemStack(Items.STICK, 6);
        if (input.is(ItemTags.SAPLINGS)) return new ItemStack(Items.STICK);
        return ItemStack.EMPTY;
    }

    public void applyClientSnapshot(int heat, int progress, boolean hasBlade,
                                    ItemStack input, ItemStack output, ItemStack byproduct) {
        this.heat = heat;
        this.progress = progress;
        this.hasBlade = hasBlade;
        items.set(INPUT_SLOT, input);
        items.set(OUTPUT_SLOT, output);
        items.set(BYPRODUCT_SLOT, byproduct);
    }

    public void setHasBlade(boolean hasBlade) {
        this.hasBlade = hasBlade;
        setChanged();
        if (level != null) {
            ThermalMultiblockBlock.updateSawbladeState(level, worldPosition, hasBlade);
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putBoolean("hasBlade", hasBlade);
        tag.putInt("progress", progress);
        tag.putInt("overspeed", overspeed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        hasBlade = !tag.contains("hasBlade") || tag.getBoolean("hasBlade");
        progress = tag.getInt("progress");
        overspeed = tag.getInt("overspeed");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("heat", synchronizedHeat);
        tag.putInt("progress", progress);
        tag.putBoolean("hasBlade", hasBlade);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        heat = tag.getInt("heat");
        progress = tag.getInt("progress");
        hasBlade = tag.getBoolean("hasBlade");
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public int heat() { return heat; }
    public int progress() { return progress; }
    public boolean hasBlade() { return hasBlade; }
    public float spin() { return spin; }
    public float lastSpin() { return lastSpin; }
    public AABB renderBounds() {
        return new AABB(worldPosition.getX() - 1, worldPosition.getY(), worldPosition.getZ() - 1,
                worldPosition.getX() + 2, worldPosition.getY() + 2, worldPosition.getZ() + 2);
    }

    public void setHeatForTest(int heat) { this.heat = heat; }
    public int overspeedForTest() { return overspeed; }

    @Override public int getContainerSize() { return items.size(); }
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
                && player.distanceToSqr(worldPosition.getCenter()) <= 128.0D;
    }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT_SLOT && items.get(INPUT_SLOT).isEmpty()
                && items.get(OUTPUT_SLOT).isEmpty() && items.get(BYPRODUCT_SLOT).isEmpty()
                && stack.getCount() == 1 && !outputFor(stack).isEmpty();
    }
    @Override public int[] getSlotsForFace(Direction side) { return ALL_SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot > INPUT_SLOT;
    }
}
