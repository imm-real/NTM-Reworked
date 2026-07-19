package com.hbm.ntm.thermal;

import com.hbm.ntm.item.AshItem;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

public final class FireboxFuel {
    private static final TagKey<Item> COKE = common("gems/coke");
    private static final TagKey<Item> COKE_BLOCKS = common("storage_blocks/coke");
    private static final TagKey<Item> LIGNITE = common("gems/lignite");
    private static final TagKey<Item> LIGNITE_DUST = common("dusts/lignite");
    private static final TagKey<Item> COAL_DUST = common("dusts/coal");
    private FireboxFuel() {
    }

    public static int rawBurnTime(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (stack.is(ModItems.SOLID_FUEL.get())) return 3_200;
        if (stack.is(ModItems.SOLID_FUEL_PRESTO.get())) return 8_000;
        if (stack.is(ModItems.SOLID_FUEL_PRESTO_TRIPLET.get())) return 40_000;
        if (stack.is(ModItems.SOLID_FUEL_BF.get())) return 32_000;
        if (stack.is(ModItems.SOLID_FUEL_PRESTO_BF.get())) return 80_000;
        if (stack.is(ModItems.SOLID_FUEL_PRESTO_TRIPLET_BF.get())) return 400_000;
        if (stack.is(ModItems.ROCKET_FUEL.get())) return 6_400;
        if (stack.is(ModItems.COKE_COAL.get()) || stack.is(ModItems.COKE_LIGNITE.get())
                || stack.is(ModItems.COKE_PETROLEUM.get())) return 3_200;
        if (stack.is(ModItems.BLOCK_COKE_COAL_ITEM.get()) || stack.is(ModItems.BLOCK_COKE_LIGNITE_ITEM.get())
                || stack.is(ModItems.BLOCK_COKE_PETROLEUM_ITEM.get())) return 32_000;
        if (stack.is(ModItems.legacyOreResourceItem("lignite").get())
                || stack.is(ModItems.get("powder_lignite").get())) return 1_200;
        if (stack.is(ModItems.get("powder_coal").get())) return 1_600;
        if (stack.is(ModItems.legacyOreResourceItem("coal_infernal").get())) return 4_800;
        if (stack.is(ModItems.get("scrap").get())) {
            return 50;
        }
        if (stack.is(ModItems.get("dust").get())) {
            return 25;
        }
        if (stack.is(ModItems.POWDER_ASH.get())) {
            return AshItem.type(stack).burnTime();
        }
        return AbstractFurnaceBlockEntity.getFuel().getOrDefault(stack.getItem(), 0);
    }

    /** Blast furnaces demand proper hot fuel. Sticks, charcoal and planks may complain outside. */
    public static boolean isBlastFurnaceFuel(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return isCoal(stack) || stack.is(ItemTags.LOGS)
                || stack.is(ModItems.SOLID_FUEL.get())
                || stack.is(ModItems.SOLID_FUEL_PRESTO.get())
                || stack.is(ModItems.SOLID_FUEL_PRESTO_TRIPLET.get())
                || stack.is(ModItems.SOLID_FUEL_BF.get())
                || stack.is(ModItems.SOLID_FUEL_PRESTO_BF.get())
                || stack.is(ModItems.SOLID_FUEL_PRESTO_TRIPLET_BF.get())
                || stack.is(ModItems.ROCKET_FUEL.get());
    }

    public static int burnTime(ItemStack stack) {
        int raw = rawBurnTime(stack);
        if (raw <= 0) {
            return 0;
        }
        return (int) (raw * category(stack).timeMultiplier);
    }

    public static int burnHeat(ItemStack stack) {
        return burnHeat(stack, 100);
    }

    public static int burnHeat(ItemStack stack, int baseHeat) {
        return (int) (baseHeat * category(stack).heatMultiplier);
    }

    public static AshItem.AshType ashType(ItemStack stack) {
        if (isCoal(stack)) {
            return AshItem.AshType.COAL;
        }
        if (stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS) || stack.is(ItemTags.SAPLINGS)) {
            return AshItem.AshType.WOOD;
        }
        return AshItem.AshType.MISC;
    }

    private static Category category(ItemStack stack) {
        if (stack.is(ModItems.SOLID_FUEL_BF.get())
                || stack.is(ModItems.SOLID_FUEL_PRESTO_BF.get())
                || stack.is(ModItems.SOLID_FUEL_PRESTO_TRIPLET_BF.get())) {
            return Category.BALEFIRE;
        }
        if (stack.is(ModItems.ROCKET_FUEL.get())) {
            return Category.ROCKET;
        }
        if (stack.is(ModItems.SOLID_FUEL.get())
                || stack.is(ModItems.SOLID_FUEL_PRESTO.get())
                || stack.is(ModItems.SOLID_FUEL_PRESTO_TRIPLET.get())) {
            return Category.SOLID;
        }
        if (isCoal(stack)) {
            return Category.COAL;
        }
        return Category.ORDINARY;
    }

    private static boolean isCoal(ItemStack stack) {
        return stack.is(Items.COAL) || stack.is(Items.COAL_BLOCK)
                || stack.is(COAL_DUST) || stack.is(LIGNITE) || stack.is(LIGNITE_DUST)
                || stack.is(COKE) || stack.is(COKE_BLOCKS);
    }

    private static TagKey<Item> common(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
    }

    private enum Category {
        ORDINARY(1.0D, 1.0D),
        COAL(1.25D, 2.0D),
        SOLID(1.5D, 3.0D),
        ROCKET(1.5D, 5.0D),
        BALEFIRE(0.5D, 15.0D);

        private final double timeMultiplier;
        private final double heatMultiplier;

        Category(double timeMultiplier, double heatMultiplier) {
            this.timeMultiplier = timeMultiplier;
            this.heatMultiplier = heatMultiplier;
        }
    }
}
