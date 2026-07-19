package com.hbm.ntm.item;

import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModSounds;
import com.hbm.ntm.weapon.FiftyCalAmmoType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Source {@code gun_amat_penance}: legendary suppressed .50 BMG anti-materiel rifle. */
public final class PenanceItem extends AmatItem {
    public static final int DURABILITY = 5_000;
    public static final float BASE_DAMAGE = 45.0F;
    public static final ResourceLocation PENANCE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "hbm", "textures/models/weapons/amat_penance.png");
    public static final ResourceLocation THERMAL_SCOPE = ResourceLocation.fromNamespaceAndPath(
            "hbm", "textures/misc/scope_penance.png");

    public static final Profile PENANCE = new Profile(
            DURABILITY, BASE_DAMAGE, 0.0F, FiftyCalAmmoType.HOLLOW_POINT,
            List.of(FiftyCalAmmoType.SOFT_POINT, FiftyCalAmmoType.FULL_METAL_JACKET,
                    FiftyCalAmmoType.HOLLOW_POINT, FiftyCalAmmoType.ARMOR_PIERCING,
                    FiftyCalAmmoType.DEPLETED_URANIUM, FiftyCalAmmoType.STARMETAL,
                    FiftyCalAmmoType.HIGH_EXPLOSIVE, FiftyCalAmmoType.BLACK),
            ModSounds.GUN_AMAT_SILENCER, "gui.weapon.quality.legendary");

    public PenanceItem() {
        super(PENANCE);
    }

    @Override public ResourceLocation gunScopeTexture() { return THERMAL_SCOPE; }

    @Override
    protected boolean isAmmoStack(ItemStack stack) {
        return stack.is(ModItems.AMMO_STANDARD.get()) || stack.is(ModItems.AMMO_SECRET.get());
    }

    @Override
    protected Item ammoIconItem(FiftyCalAmmoType ammo) {
        return ammo.secret() ? ModItems.AMMO_SECRET.get() : ModItems.AMMO_STANDARD.get();
    }
}
