package com.hbm.ntm.inventory;

import com.hbm.ntm.block.RadioTorchBlock;
import com.hbm.ntm.blockentity.RadioTorchBlockEntity;
import com.hbm.ntm.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class RadioTorchMenu extends AbstractContainerMenu {
    private final RadioTorchBlockEntity radio;
    private final RadioTorchBlock.Kind kind;

    public RadioTorchMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readBlockPos()));
    }

    public RadioTorchMenu(int id, Inventory inventory, RadioTorchBlockEntity radio) {
        super(ModMenus.RADIO_TORCH.get(), id);
        this.radio = radio;
        this.kind = radio == null ? RadioTorchBlock.Kind.SENDER : radio.kind();
        if (kind == RadioTorchBlock.Kind.COUNTER) {
            var container = radio == null ? new SimpleContainer(3) : radio;
            for (int i = 0; i < 3; i++) addSlot(new Slot(container, i, 138, 18 + i * 44) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
            });
        }
        if (kind != RadioTorchBlock.Kind.COUNTER) return;
        int startY = kind == RadioTorchBlock.Kind.COUNTER ? 156 : 122;
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column + row * 9 + 9, 12 + column * 18, startY + row * 18));
        for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column, 12 + column * 18, startY + 58));
    }

    private static RadioTorchBlockEntity find(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof RadioTorchBlockEntity radio ? radio : null;
    }

    @Override public void clicked(int slotId, int button, ClickType type, Player player) {
        if (kind == RadioTorchBlock.Kind.COUNTER && slotId >= 0 && slotId < 3 && radio != null) {
            if (button == 1 && getCarried().isEmpty()) radio.cyclePatternMode(slotId);
            else radio.setPattern(slotId, getCarried());
            broadcastChanges();
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return radio != null && radio.stillValid(player); }
    public RadioTorchBlockEntity blockEntity() { return radio; }
    public RadioTorchBlock.Kind kind() { return kind; }
}
