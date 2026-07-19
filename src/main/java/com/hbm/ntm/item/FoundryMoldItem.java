package com.hbm.ntm.item;

import com.hbm.ntm.foundry.FoundryMaterial;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;

/** Foundry molds carrying shape, size and cost in component paperwork. */
public final class FoundryMoldItem extends Item {
    public static final int INGOT_ID = 2;
    public static final int CAST_PLATE_ID = 19;
    private static final String MOLD = "mold";

    public FoundryMoldItem() {
        super(new Properties());
    }

    public static ItemStack nugget(Item item) { return create(item, Mold.NUGGET); }
    public static ItemStack billet(Item item) { return create(item, Mold.BILLET); }
    public static ItemStack ingot(Item item) { return create(item, Mold.INGOT); }
    public static ItemStack plate(Item item) { return create(item, Mold.PLATE); }
    public static ItemStack castPlate(Item item) { return create(item, Mold.CAST_PLATE); }

    public static ItemStack create(Item item, int moldId) {
        Mold mold = Mold.byId(moldId);
        return create(item, mold == null ? Mold.NUGGET : mold);
    }

    public static ItemStack create(Item item, Mold mold) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putInt(MOLD, mold.id());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(mold.id()));
        return stack;
    }

    public static boolean isIngot(ItemStack stack) { return type(stack) == Mold.INGOT; }
    public static boolean isCastPlate(ItemStack stack) { return type(stack) == Mold.CAST_PLATE; }
    public static boolean isSmall(ItemStack stack) {
        Mold mold = type(stack);
        return mold != null && mold.size() == MoldSize.SMALL;
    }
    public static boolean isLarge(ItemStack stack) {
        Mold mold = type(stack);
        return mold != null && mold.size() == MoldSize.LARGE;
    }

    public static Mold type(ItemStack stack) {
        if (!(stack.getItem() instanceof FoundryMoldItem)) return null;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(MOLD)) return Mold.byId(tag.getInt(MOLD));
        CustomModelData model = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return model == null ? Mold.NUGGET : Mold.byId(model.value());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        Mold mold = type(stack);
        if (mold == null) return;
        tooltip.add(Component.translatable(mold.titleKey()).append(" x" + mold.outputCount())
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable(mold.size() == MoldSize.SMALL
                        ? "block.hbm.foundry_mold" : "block.hbm.foundry_basin")
                .withStyle(mold.size() == MoldSize.SMALL ? ChatFormatting.GOLD : ChatFormatting.RED));
    }

    public enum MoldSize { SMALL, LARGE }

    /** Stable IDs, display order, sizes, costs and how many castings fall out. */
    public enum Mold {
        NUGGET(0, MoldSize.SMALL, "nugget", "shape.nugget", 8, 1),
        BILLET(1, MoldSize.SMALL, "billet", "shape.billet", 48, 1),
        INGOT(2, MoldSize.SMALL, "ingot", "shape.ingot", 72, 1),
        PLATE(3, MoldSize.SMALL, "plate", "shape.plate", 72, 1),
        WIRES(4, MoldSize.SMALL, "wire", "shape.wireFine", 72, 8),
        CAST_PLATE(19, MoldSize.SMALL, "plate_cast", "shape.plateTriple", 216, 1),
        DENSE_WIRE(20, MoldSize.SMALL, "wire_dense", "shape.wireDense", 72, 1),
        BLADE(5, MoldSize.SMALL, "blade", "shape.blade", 216, 1),
        BLADES(6, MoldSize.SMALL, "blades", "shape.blades", 288, 1),
        STAMP(7, MoldSize.SMALL, "stamp", "shape.stamp", 288, 1),
        SHELL(8, MoldSize.SMALL, "shell", "shape.shell", 288, 1),
        PIPE(9, MoldSize.SMALL, "pipe", "shape.ntmpipe", 216, 1),
        INGOTS(10, MoldSize.LARGE, "ingots", "shape.ingot", 648, 9),
        PLATES(11, MoldSize.LARGE, "plates", "shape.plate", 648, 9),
        CAST_PLATES(13, MoldSize.LARGE, "plates_cast", "shape.plateTriple", 648, 3),
        DENSE_WIRES(21, MoldSize.LARGE, "wires_dense", "shape.wiresDense", 648, 9),
        BLOCK(12, MoldSize.LARGE, "block", "shape.block", 648, 1),
        SMALL_CASING(16, MoldSize.SMALL, "c9", "shape.c9", 18, 1),
        LARGE_CASING(17, MoldSize.SMALL, "c50", "shape.c50", 36, 1),
        LIGHT_BARREL(22, MoldSize.SMALL, "barrel_light", "shape.barrelLight", 216, 1),
        HEAVY_BARREL(23, MoldSize.SMALL, "barrel_heavy", "shape.barrelHeavy", 432, 1),
        LIGHT_RECEIVER(24, MoldSize.SMALL, "receiver_light", "shape.receiverLight", 288, 1),
        HEAVY_RECEIVER(25, MoldSize.SMALL, "receiver_heavy", "shape.receiverHeavy", 648, 1),
        MECHANISM(26, MoldSize.SMALL, "mechanism", "shape.gunMechanism", 288, 1),
        STOCK(27, MoldSize.SMALL, "stock", "shape.stock", 288, 1),
        GRIP(28, MoldSize.SMALL, "grip", "shape.grip", 144, 1);

        private final int id;
        private final MoldSize size;
        private final String texture;
        private final String titleKey;
        private final int cost;
        private final int outputCount;

        Mold(int id, MoldSize size, String texture, String titleKey, int cost, int outputCount) {
            this.id = id;
            this.size = size;
            this.texture = texture;
            this.titleKey = titleKey;
            this.cost = cost;
            this.outputCount = outputCount;
        }

        public int id() { return id; }
        public MoldSize size() { return size; }
        public String texture() { return texture; }
        public String titleKey() { return titleKey; }
        public int cost() { return cost; }
        public int outputCount() { return outputCount; }
        public ItemStack output(FoundryMaterial material) { return material.output(this); }

        public static Mold byId(int id) {
            for (Mold mold : values()) if (mold.id == id) return mold;
            return null;
        }
    }
}
