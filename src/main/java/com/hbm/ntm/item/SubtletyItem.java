package com.hbm.ntm.item;

import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.weapon.FiftyCalAmmoType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Source {@code gun_amat_subtlety}: legendary AMAT receiver with the Demolisher round. */
public final class SubtletyItem extends AmatItem {
    public static final int DURABILITY = 1_000;
    public static final float BASE_DAMAGE = 50.0F;

    public static final Profile PROFILE = new Profile(
            DURABILITY,
            BASE_DAMAGE,
            HIP_SPREAD,
            FiftyCalAmmoType.HOLLOW_POINT,
            // Hidden round first. Subtlety insists.
            List.of(FiftyCalAmmoType.EQUESTRIAN,
                    FiftyCalAmmoType.SOFT_POINT,
                    FiftyCalAmmoType.FULL_METAL_JACKET,
                    FiftyCalAmmoType.HOLLOW_POINT,
                    FiftyCalAmmoType.ARMOR_PIERCING,
                    FiftyCalAmmoType.DEPLETED_URANIUM,
                    FiftyCalAmmoType.STARMETAL,
                    FiftyCalAmmoType.HIGH_EXPLOSIVE),
            ModSounds.GUN_AMAT_FIRE,
            "gui.weapon.quality.legendary");

    public SubtletyItem() {
        super(PROFILE);
    }

    @Override
    protected boolean isAmmoStack(ItemStack stack) {
        return stack.is(ModItems.AMMO_STANDARD.get()) || stack.is(ModItems.AMMO_SECRET.get());
    }

    @Override
    protected FiftyCalAmmoType ammoFromStack(ItemStack stack) {
        if (!isAmmoStack(stack)) return null;
        return FiftyCalAmmoType.fromStack(stack);
    }

    @Override
    protected Item ammoIconItem(FiftyCalAmmoType ammo) {
        return ammo.secret() ? ModItems.AMMO_SECRET.get() : ModItems.AMMO_STANDARD.get();
    }
}
