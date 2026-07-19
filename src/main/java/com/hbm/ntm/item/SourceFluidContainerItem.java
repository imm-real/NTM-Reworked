package com.hbm.ntm.item;

import com.hbm.ntm.registry.ModFluids;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluid;

import java.util.function.Supplier;

/** Canister and gas-tank identities with defaults safe for untagged stacks. */
public final class SourceFluidContainerItem extends Item {
    private static final String FLUID = "fluid";
    private final ContainedFluid defaultFluid;
    private final Supplier<Item> emptyContainer;

    public SourceFluidContainerItem(ContainedFluid defaultFluid, Supplier<Item> emptyContainer) {
        super(new Properties());
        this.defaultFluid = defaultFluid;
        this.emptyContainer = emptyContainer;
    }

    public static ItemStack create(Item item, ContainedFluid fluid, int count) {
        ItemStack stack = new ItemStack(item, count);
        CompoundTag tag = new CompoundTag();
        tag.putString(FLUID, fluid.id());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static ContainedFluid fluid(ItemStack stack) {
        if (!(stack.getItem() instanceof SourceFluidContainerItem item)) return ContainedFluid.NONE;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ContainedFluid stored = tag.contains(FLUID) ? ContainedFluid.byId(tag.getString(FLUID)) : null;
        return stored == null || stored == ContainedFluid.NONE ? item.defaultFluid : stored;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack))
                .append(" ")
                .append(Component.translatable(fluid(stack).translationKey()));
    }

    @Override public boolean hasCraftingRemainingItem(ItemStack stack) { return true; }
    @Override public ItemStack getCraftingRemainingItem(ItemStack stack) {
        return new ItemStack(emptyContainer.get());
    }

    public enum ContainedFluid {
        NONE("none", "hbmfluid.none", 0xFFFFFF, 0xFFFFFF,
                () -> net.minecraft.world.level.material.Fluids.EMPTY),
        OIL("oil", "hbmfluid.oil", 0x424242, 0xFFFFFF, ModFluids.OIL::get),
        HEAVYOIL("heavyoil", "hbmfluid.heavyoil", 0x513F39, 0xFFFFFF, ModFluids.HEAVYOIL::get),
        NAPHTHA("naphtha", "hbmfluid.naphtha", 0x5F6D44, 0xFFFFFF, ModFluids.NAPHTHA::get),
        LIGHTOIL("lightoil", "hbmfluid.lightoil", 0xB46B52, 0xFFFFFF, ModFluids.LIGHTOIL::get),
        BITUMEN("bitumen", "hbmfluid.bitumen", 0x5A5877, 0xFFFFFF, ModFluids.BITUMEN::get),
        SMEAR("smear", "hbmfluid.smear", 0x624F3B, 0xFFFFFF, ModFluids.SMEAR::get),
        HEATINGOIL("heatingoil", "hbmfluid.heatingoil", 0x694235, 0xFFFFFF, ModFluids.HEATINGOIL::get),
        WOODOIL("woodoil", "hbmfluid.woodoil", 0xBF7E4F, 0xFFFFFF, ModFluids.WOODOIL::get),
        COALCREOSOTE("coalcreosote", "hbmfluid.coalcreosote", 0x285A3F, 0xFFFFFF,
                ModFluids.COALCREOSOTE::get),
        LUBRICANT("lubricant", "hbmfluid.lubricant", 0xF1CC05, 0xFFFFFF, ModFluids.LUBRICANT::get),
        DIESEL("diesel", "hbmfluid.diesel", 0xFF2C2C, 0xFFFFFF, ModFluids.DIESEL::get),
        KEROSENE("kerosene", "hbmfluid.kerosene", 0xFF377D, 0xFFFFFF, ModFluids.KEROSENE::get),
        GAS("gas", "hbmfluid.gas", 0xFF4545, 0xFFE97F, ModFluids.GAS::get),
        CARBONDIOXIDE("carbondioxide", "hbmfluid.carbondioxide", 0xB0B0B0, 0xFFFFFF,
                ModFluids.CARBONDIOXIDE::get),
        PETROLEUM("petroleum", "hbmfluid.petroleum", 0x5E7CFF, 0xFFE97F, ModFluids.PETROLEUM::get),
        HYDROGEN("hydrogen", "hbmfluid.hydrogen", 0x4286F4, 0xFFFFFF, ModFluids.HYDROGEN::get),
        DEUTERIUM("deuterium", "hbmfluid.deuterium", 0x3A6EA5, 0xFFFFFF, ModFluids.DEUTERIUM::get),
        TRITIUM("tritium", "hbmfluid.tritium", 0x2FB24C, 0xFFFFFF, ModFluids.TRITIUM::get),
        UNSATURATEDS("unsaturateds", "hbmfluid.unsaturateds", 0x628FAE, 0xEDCF27,
                ModFluids.UNSATURATEDS::get),
        OXYGEN("oxygen", "hbmfluid.oxygen", 0x98BDF9, 0xFFFFFF, ModFluids.OXYGEN::get);

        private final String id;
        private final String translationKey;
        private final int containerColor;
        private final int labelColor;
        private final Supplier<Fluid> fluid;

        ContainedFluid(String id, String translationKey, int containerColor, int labelColor,
                       Supplier<Fluid> fluid) {
            this.id = id;
            this.translationKey = translationKey;
            this.containerColor = containerColor;
            this.labelColor = labelColor;
            this.fluid = fluid;
        }

        public String id() { return id; }
        public String translationKey() { return translationKey; }
        public int containerColor() { return containerColor; }
        public int labelColor() { return labelColor; }
        public Fluid fluid() { return fluid.get(); }

        public static ContainedFluid byId(String id) {
            for (ContainedFluid value : values()) if (value.id.equals(id)) return value;
            return NONE;
        }

        public static ContainedFluid fromFluid(Fluid fluid) {
            for (ContainedFluid value : values()) if (fluid.isSame(value.fluid())) return value;
            return NONE;
        }
    }
}
