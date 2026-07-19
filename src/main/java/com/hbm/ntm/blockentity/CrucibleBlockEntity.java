package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.CrucibleBlock;
import com.hbm.ntm.foundry.FoundryMaterial;
import com.hbm.ntm.foundry.MoltenAcceptor;
import com.hbm.ntm.inventory.CrucibleMenu;
import com.hbm.ntm.pollution.PollutionData;
import com.hbm.ntm.recipe.CrucibleRecipes;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.thermal.HeatSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class CrucibleBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int SLOT_COUNT = 10;
    public static final int INPUT_START = 1;
    public static final int INPUT_END = 10;
    public static final int RECIPE_CAPACITY = FoundryMaterial.BLOCK * 16;
    public static final int WASTE_CAPACITY = FoundryMaterial.BLOCK * 16;
    public static final int PROCESS_TIME = 20_000;
    public static final int MAX_HEAT = 100_000;
    public static final double DIFFUSION = 0.25D;
    public static final int POUR_AMOUNT = FoundryMaterial.NUGGET * 3;
    public static final float SOOT_PER_ACTIVE_STACK_TICK = 1F / 25F / 20F;
    public static final int RECIPE_NONE = CrucibleRecipes.NONE;
    public static final int RECIPE_STEEL = CrucibleRecipes.STEEL;
    public static final int RECIPE_DURA_STEEL = CrucibleRecipes.DURA_STEEL;
    public static final int RECIPE_RED_COPPER = CrucibleRecipes.RED_COPPER;
    public static final int RECIPE_FERROURANIUM = CrucibleRecipes.FERROURANIUM;
    public static final int RECIPE_TECHNETIUM_STEEL = CrucibleRecipes.TECHNETIUM_STEEL;
    public static final int RECIPE_CADMIUM_STEEL = CrucibleRecipes.CADMIUM_STEEL;
    public static final int RECIPE_BISMUTH_BRONZE = CrucibleRecipes.BISMUTH_BRONZE;
    public static final int RECIPE_ARSENIC_BRONZE = CrucibleRecipes.ARSENIC_BRONZE;
    public static final int RECIPE_BSCCO = CrucibleRecipes.BSCCO;
    public static final int RECIPE_HEMATITE = CrucibleRecipes.HEMATITE;
    public static final int RECIPE_MALACHITE = CrucibleRecipes.MALACHITE;
    public static final int RECIPE_MAGNETIZED_TUNGSTEN = CrucibleRecipes.MAGNETIZED_TUNGSTEN;
    public static final int RECIPE_COMBINE_STEEL = CrucibleRecipes.COMBINE_STEEL;
    private static final int[] AUTOMATION_SLOTS = {1, 2, 3, 4, 5, 6, 7, 8, 9};

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final EnumMap<FoundryMaterial, Integer> recipeStack = new EnumMap<>(FoundryMaterial.class);
    private final EnumMap<FoundryMaterial, Integer> wasteStack = new EnumMap<>(FoundryMaterial.class);
    private int heat;
    private int progress;
    private int selectedRecipe;
    private Component customName;
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> heat;
                case 2 -> selectedRecipe;
                case 3 -> total(recipeStack);
                case 4 -> total(wasteStack);
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> progress = value;
                case 1 -> heat = value;
                case 2 -> selectedRecipe = Math.max(RECIPE_NONE, Math.min(value, CrucibleRecipes.lastId()));
                default -> { }
            }
        }
        @Override public int getCount() { return 5; }
    };

    public CrucibleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE_CRUCIBLE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CrucibleBlockEntity crucible) {
        if (level.isClientSide) {
            if ((!crucible.recipeStack.isEmpty() || !crucible.wasteStack.isEmpty()) && level.getGameTime() % 10L == 0L) {
                level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, pos.getX() + .5, pos.getY() + 1,
                        pos.getZ() + .5, 0, .08, 0);
            }
            return;
        }
        crucible.serverTick((ServerLevel) level, pos, state);
    }

    private void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean changed = pullHeat(level, pos);
        if (level.getGameTime() % 5L == 0L) changed |= collectItems(level, pos);
        damageMoltenContact(level, pos);
        if (!trySmelt()) {
            if (progress != 0) changed = true;
            progress = 0;
        } else changed = true;
        changed |= trySelectedRecipe(level.getGameTime());

        Direction facing = state.getValue(CrucibleBlock.FACING);
        if (!wasteStack.isEmpty()) {
            changed |= pour(level, pos, facing.getOpposite(), wasteStack, null);
            PollutionData.get(level).increment(pos, PollutionData.Type.SOOT, SOOT_PER_ACTIVE_STACK_TICK);
        }
        if (!recipeStack.isEmpty()) {
            changed |= pour(level, pos, facing, recipeStack, recipeOutputs());
            PollutionData.get(level).increment(pos, PollutionData.Type.SOOT, SOOT_PER_ACTIVE_STACK_TICK);
        }
        recipeStack.entrySet().removeIf(entry -> entry.getValue() <= 0);
        wasteStack.entrySet().removeIf(entry -> entry.getValue() <= 0);
        if (changed || level.getGameTime() % 20L == 0L) sync();
    }

    private boolean pullHeat(ServerLevel level, BlockPos pos) {
        if (heat >= MAX_HEAT) return false;
        BlockEntity below = level.getBlockEntity(pos.below());
        if (below instanceof HeatSource source) {
            int difference = source.getHeatStored() - heat;
            if (difference == 0) return false;
            difference = Math.min(difference, MAX_HEAT - heat);
            if (difference > 0) {
                int transfer = (int) Math.ceil(difference * DIFFUSION);
                source.useUpHeat(transfer);
                heat = Math.min(heat + transfer, MAX_HEAT);
                return true;
            }
        }
        int cooled = Math.max(heat - Math.max(heat / 1000, 1), 0);
        boolean changed = cooled != heat;
        heat = cooled;
        return changed;
    }

    private boolean collectItems(ServerLevel level, BlockPos pos) {
        boolean changed = false;
        AABB area = new AABB(pos.getX() - .5, pos.getY() + .5, pos.getZ() - .5,
                pos.getX() + 1.5, pos.getY() + 1, pos.getZ() + 1.5);
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area)) {
            ItemStack stack = entity.getItem();
            while (!stack.isEmpty() && canAccept(stack)) {
                int slot = firstEmptyInput();
                if (slot < 0) break;
                items.set(slot, stack.copyWithCount(1));
                stack.shrink(1);
                changed = true;
            }
            if (stack.isEmpty()) entity.discard();
            else entity.setItem(stack);
        }
        return changed;
    }

    private void damageMoltenContact(ServerLevel level, BlockPos pos) {
        int totalCapacity = RECIPE_CAPACITY + WASTE_CAPACITY;
        double moltenLevel = (double) (total(recipeStack) + total(wasteStack)) / totalCapacity * .875D;
        if (moltenLevel <= 0D) return;
        AABB area = new AABB(pos.getX() - .5, pos.getY() + .5, pos.getZ() - .5,
                pos.getX() + 1.5, pos.getY() + .5 + moltenLevel, pos.getZ() + 1.5);
        for (Entity entity : level.getEntities((Entity) null, area, Entity::isAlive)) {
            entity.hurt(level.damageSources().lava(), 5F);
            entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), 100));
        }
    }

    private boolean trySmelt() {
        if (heat < MAX_HEAT / 2) return false;
        int slot = firstSmeltableInput();
        if (slot < 0) return false;
        int delta = (int) ((heat - MAX_HEAT / 2) * .05D);
        progress += delta;
        heat -= delta;
        if (progress >= PROCESS_TIME) {
            progress = 0;
            FoundryMaterial.MaterialAmount material = FoundryMaterial.fromItem(items.get(slot));
            if (material != null) {
                if (belongsInRecipe(material.material())) add(recipeStack, material.material(), material.amount());
                else add(wasteStack, material.material(), material.amount());
            }
            removeItem(slot, 1);
        }
        return true;
    }

    private boolean trySelectedRecipe(long gameTime) {
        CrucibleRecipes.Recipe recipe = selectedRecipeDefinition();
        if (recipe == null || gameTime % recipe.frequency() != 0L) return false;
        for (FoundryMaterial.MaterialAmount input : recipe.inputs()) {
            if (amount(recipeStack, input.material()) < input.amount()) return false;
        }
        for (FoundryMaterial.MaterialAmount input : recipe.inputs()) {
            add(recipeStack, input.material(), -input.amount());
        }
        for (FoundryMaterial.MaterialAmount output : recipe.outputs()) {
            add(recipeStack, output.material(), output.amount());
        }
        return true;
    }

    private boolean pour(ServerLevel level, BlockPos pos, Direction direction,
                         EnumMap<FoundryMaterial, Integer> source, @Nullable Set<FoundryMaterial> only) {
        double x = pos.getX() + .5D + direction.getStepX() * 1.875D;
        double z = pos.getZ() + .5D + direction.getStepZ() * 1.875D;
        int startY = pos.getY();
        MoltenAcceptor target = null;
        BlockPos targetPosition = null;
        for (int drop = 0; drop <= 6; drop++) {
            BlockPos targetPos = BlockPos.containing(x, startY - drop + .249D, z);
            if (level.getBlockEntity(targetPos) instanceof MoltenAcceptor acceptor) {
                target = acceptor;
                targetPosition = targetPos;
                break;
            }
            if (!level.getBlockState(targetPos).isAir() && drop > 0) break;
        }
        if (target == null || targetPosition == null) return false;
        for (FoundryMaterial material : FoundryMaterial.values()) {
            if (only != null && !only.contains(material)) continue;
            int stored = amount(source, material);
            if (stored <= 0 || material.additive()) continue;
            int offered = Math.min(stored, POUR_AMOUNT);
            if (!target.canAcceptPour(material, offered, Direction.UP)) continue;
            int accepted = target.acceptPour(material, offered, Direction.UP);
            if (accepted > 0) {
                add(source, material, -accepted);
                emitPourParticles(level, x, z, pos.getY() + .25D, targetPosition.getY() + .5D, material);
                return true;
            }
        }
        return false;
    }

    private static void emitPourParticles(ServerLevel level, double x, double z, double fromY, double toY,
                                          FoundryMaterial material) {
        int color = material.moltenColor();
        Vector3f rgb = new Vector3f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F,
                (color & 255) / 255F);
        DustParticleOptions particle = new DustParticleOptions(rgb, 1F);
        double bottom = Math.min(fromY, toY);
        double top = Math.max(fromY, toY);
        for (double y = bottom; y <= top; y += .25D) level.sendParticles(particle, x, y, z, 1, 0, 0, 0, 0);
    }

    private boolean belongsInRecipe(FoundryMaterial material) {
        CrucibleRecipes.Recipe recipe = selectedRecipeDefinition();
        return recipe != null && recipe.contains(material);
    }

    private Set<FoundryMaterial> recipeOutputs() {
        CrucibleRecipes.Recipe recipe = selectedRecipeDefinition();
        if (recipe == null) return null;
        EnumSet<FoundryMaterial> outputs = EnumSet.noneOf(FoundryMaterial.class);
        recipe.outputs().forEach(output -> outputs.add(output.material()));
        return outputs;
    }

    private CrucibleRecipes.Recipe selectedRecipeDefinition() {
        return CrucibleRecipes.byId(selectedRecipe);
    }

    public boolean canAccept(ItemStack stack) {
        FoundryMaterial.MaterialAmount material = FoundryMaterial.fromItem(stack);
        if (material == null || firstEmptyInput() < 0) return false;
        CrucibleRecipes.Recipe recipe = selectedRecipeDefinition();
        if (recipe == null) return total(wasteStack) + material.amount() <= WASTE_CAPACITY;
        if (recipe.outputAmount(material.material()) > 0) {
            return total(recipeStack) + material.amount() <= RECIPE_CAPACITY;
        }
        int required = recipe.inputAmount(material.material());
        if (required == 0) return false;
        int maximum = required * RECIPE_CAPACITY / recipe.inputAmount();
        return amount(recipeStack, material.material()) + material.amount() <= maximum
                && total(recipeStack) + material.amount() <= RECIPE_CAPACITY;
    }

    private int firstEmptyInput() {
        for (int slot = INPUT_START; slot < INPUT_END; slot++) if (items.get(slot).isEmpty()) return slot;
        return -1;
    }

    private int firstSmeltableInput() {
        for (int slot = INPUT_START; slot < INPUT_END; slot++) if (!items.get(slot).isEmpty()
                && FoundryMaterial.fromItem(items.get(slot)) != null) return slot;
        return -1;
    }

    private static void add(Map<FoundryMaterial, Integer> map, FoundryMaterial material, int amount) {
        map.merge(material, amount, Integer::sum);
    }
    private static int amount(Map<FoundryMaterial, Integer> map, FoundryMaterial material) { return map.getOrDefault(material, 0); }
    private static int total(Map<FoundryMaterial, Integer> map) { return map.values().stream().mapToInt(Integer::intValue).sum(); }

    public void selectRecipe(int recipe) {
        selectedRecipe = Math.max(RECIPE_NONE, Math.min(recipe, CrucibleRecipes.lastId()));
        progress = 0;
        sync();
    }
    public void selectNextRecipe() { selectRecipe(CrucibleRecipes.nextId(selectedRecipe)); }
    public void selectSteelRecipe(boolean selected) { selectRecipe(selected ? RECIPE_STEEL : RECIPE_NONE); }
    public boolean steelRecipeSelected() { return selectedRecipe == RECIPE_STEEL; }
    public int selectedRecipe() { return selectedRecipe; }
    public int heat() { return heat; }
    public int progress() { return progress; }
    public int recipeAmount(FoundryMaterial material) { return amount(recipeStack, material); }
    public int wasteAmount(FoundryMaterial material) { return amount(wasteStack, material); }
    public int recipeTotal() { return total(recipeStack); }
    public int wasteTotal() { return total(wasteStack); }

    public void setHeatForTest(int heat) { this.heat = Math.max(0, Math.min(heat, MAX_HEAT)); }
    public boolean runSteelRecipeForTest(long gameTime) { return trySelectedRecipe(gameTime); }
    public boolean runSelectedRecipeForTest(long gameTime) { return trySelectedRecipe(gameTime); }
    public void addMoltenForTest(boolean recipe, FoundryMaterial material, int amount) {
        add(recipe ? recipeStack : wasteStack, material, amount);
        sync();
    }

    public void clearMolten() {
        recipeStack.clear();
        wasteStack.clear();
        progress = 0;
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("heat", heat);
        tag.putInt("progress", progress);
        tag.putInt("selectedRecipe", selectedRecipe);
        saveMaterials(tag, "recipe", recipeStack);
        saveMaterials(tag, "waste", wasteStack);
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        heat = tag.getInt("heat");
        progress = tag.getInt("progress");
        selectedRecipe = tag.contains("selectedRecipe") ? tag.getInt("selectedRecipe")
                : tag.getBoolean("steelRecipe") ? RECIPE_STEEL : RECIPE_NONE;
        selectedRecipe = Math.max(RECIPE_NONE, Math.min(selectedRecipe, CrucibleRecipes.lastId()));
        loadMaterials(tag, "recipe", recipeStack);
        loadMaterials(tag, "waste", wasteStack);
        customName = tag.contains("name") ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("heat", heat);
        tag.putInt("progress", progress);
        tag.putInt("selectedRecipe", selectedRecipe);
        saveMaterials(tag, "recipe", recipeStack);
        saveMaterials(tag, "waste", wasteStack);
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        heat = tag.getInt("heat");
        progress = tag.getInt("progress");
        selectedRecipe = tag.contains("selectedRecipe") ? tag.getInt("selectedRecipe")
                : tag.getBoolean("steelRecipe") ? RECIPE_STEEL : RECIPE_NONE;
        selectedRecipe = Math.max(RECIPE_NONE, Math.min(selectedRecipe, CrucibleRecipes.lastId()));
        loadMaterials(tag, "recipe", recipeStack);
        loadMaterials(tag, "waste", wasteStack);
    }

    private static void saveMaterials(CompoundTag tag, String key, Map<FoundryMaterial, Integer> materials) {
        CompoundTag values = new CompoundTag();
        materials.forEach((material, amount) -> values.putInt(material.id(), amount));
        tag.put(key, values);
    }

    private static void loadMaterials(CompoundTag tag, String key, EnumMap<FoundryMaterial, Integer> materials) {
        materials.clear();
        CompoundTag values = tag.getCompound(key);
        for (FoundryMaterial material : FoundryMaterial.values()) {
            int amount = values.getInt(material.id());
            if (amount > 0) materials.put(material, amount);
        }
    }

    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public Component getDisplayName() { return customName != null ? customName : Component.translatable("container.machineCrucible"); }
    public void setCustomName(Component name) { customName = name; sync(); }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CrucibleMenu(id, inventory, this, data);
    }

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public int getMaxStackSize() { return 1; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > 1) stack.setCount(1);
        setChanged();
    }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getCenter()) <= 128D;
    }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return slot >= INPUT_START && slot < INPUT_END && canAccept(stack); }
    @Override public int[] getSlotsForFace(Direction side) { return AUTOMATION_SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) { return canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }
}
