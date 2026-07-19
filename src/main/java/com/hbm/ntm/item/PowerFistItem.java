package com.hbm.ntm.item;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.SellafieldBlock;
import com.hbm.ntm.entity.PowerFistBeamEntity;
import com.hbm.ntm.entity.PowerFistRubbleEntity;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

/** Ten Power Fists wearing one class in a trench coat. */
public final class PowerFistItem extends Item {
    private static final int MAX_DAMAGE = 5_000;
    private static final float TOOL_SPEED = 25.0F;
    private static final float UNBREAKABLE_RESISTANCE = 6_000.0F;

    private final Mode mode;

    public PowerFistItem(Mode mode) {
        super(properties(mode));
        this.mode = mode;
    }

    private static Properties properties(Mode mode) {
        Properties properties = new Properties()
                .stacksTo(1)
                .durability(MAX_DAMAGE)
                .attributes(ItemAttributeModifiers.builder()
                        .add(Attributes.ATTACK_DAMAGE,
                                new AttributeModifier(
                                        ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID,
                                                "multitool_" + mode.id() + "_damage"),
                                        mode.attackDamage(), AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .build());
        if (mode.isTool()) {
            // Empty rules leave the explicit overrides below in charge of every block.
            properties.component(DataComponents.TOOL, new Tool(List.of(), TOOL_SPEED, 1));
        }
        return properties;
    }

    public Mode mode() {
        return mode;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (level instanceof ServerLevel server) cycle(server, player, hand, stack);
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }

        if (!(level instanceof ServerLevel server)) {
            return mode.hasAirAction()
                    ? InteractionResultHolder.sidedSuccess(stack, true)
                    : InteractionResultHolder.pass(stack);
        }

        switch (mode) {
            case MINER -> {
                server.addFreshEntity(PowerFistBeamEntity.createMiner(server, player));
                playIgnite(server, player);
                return InteractionResultHolder.success(stack);
            }
            case BEAM -> {
                server.addFreshEntity(PowerFistBeamEntity.createLaser(server, player));
                playIgnite(server, player);
                return InteractionResultHolder.success(stack);
            }
            case SKY -> {
                crackTheSky(server, player);
                return InteractionResultHolder.success(stack);
            }
            default -> {
                return InteractionResultHolder.pass(stack);
            }
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!(context.getLevel() instanceof ServerLevel server)) {
            if (mode == Mode.EXT) {
                return smeltingResult(context.getLevel(), context.getLevel().getBlockState(context.getClickedPos()))
                        .isEmpty() ? InteractionResult.PASS : InteractionResult.SUCCESS;
            }
            return mode.hasBlockAction() ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }

        boolean acted = switch (mode) {
            case EXT -> extract(server, player, context.getClickedPos());
            case MEGA -> {
                levelDown(server, context.getClickedPos(), 2);
                yield true;
            }
            case JOULE -> {
                gigaJoule(server, player, context.getClickedPos());
                yield true;
            }
            case DECON -> {
                decontaminate(server, context.getClickedPos());
                yield true;
            }
            default -> false;
        };
        if (acted) player.swing(context.getHand(), true);
        return acted ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    private void cycle(ServerLevel level, Player player, InteractionHand hand, ItemStack oldStack) {
        ItemStack next = new ItemStack(itemFor(mode.next()));
        next.setDamageValue(Math.min(oldStack.getDamageValue(), next.getMaxDamage() - 1));
        addDestinationEnchantments(level, next, mode.next());
        player.setItemInHand(hand, next);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.TECH_BOOP.get(),
                SoundSource.PLAYERS, 2.0F, 1.0F);
    }

    private static void addDestinationEnchantments(ServerLevel level, ItemStack stack, Mode destination) {
        switch (destination) {
            case SILK -> enchant(level, stack, Enchantments.SILK_TOUCH, 3);
            case EXT -> enchant(level, stack, Enchantments.FIRE_ASPECT, 3);
            case HIT -> {
                enchant(level, stack, Enchantments.LOOTING, 3);
                enchant(level, stack, Enchantments.KNOCKBACK, 3);
            }
            case MEGA -> enchant(level, stack, Enchantments.KNOCKBACK, 5);
            case JOULE -> enchant(level, stack, Enchantments.KNOCKBACK, 3);
            case DIG -> {
                enchant(level, stack, Enchantments.LOOTING, 3);
                enchant(level, stack, Enchantments.FORTUNE, 3);
            }
            default -> { }
        }
    }

    private static void enchant(ServerLevel level, ItemStack stack,
                                net.minecraft.resources.ResourceKey<Enchantment> key, int levelValue) {
        Holder<Enchantment> enchantment = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        stack.enchant(enchantment, levelValue);
    }

    private static boolean extract(ServerLevel level, Player player, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        ItemStack result = smeltingResult(level, state);
        if (result.isEmpty()) return false;

        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        ItemStack output = result.copy();
        if (!player.getInventory().add(output)) player.drop(output, false);
        return true;
    }

    private static ItemStack smeltingResult(Level level, BlockState state) {
        Item blockItem = state.getBlock().asItem();
        if (state.isAir() || blockItem == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;

        SingleRecipeInput input = new SingleRecipeInput(new ItemStack(blockItem));
        return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, level)
                .map(holder -> holder.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    private static void crackTheSky(ServerLevel level, Player player) {
        int originX = (int) player.getX();
        int originZ = (int) player.getZ();
        for (int index = 0; index < 15; index++) {
            int x = originX - 15 + level.random.nextInt(31);
            int z = originZ - 15 + level.random.nextInt(31);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(x, y, z);
                level.addFreshEntity(bolt);
            }
        }
    }

    public static void levelDown(ServerLevel level, BlockPos center, int radius) {
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                ejectBlock(level, new BlockPos(x, center.getY(), z), 0.4D);
            }
        }
    }

    public static void gigaJoule(ServerLevel level, Player player, BlockPos origin) {
        Vec3 ray = player.getLookAngle().yRot(0.25F);
        for (int path = 0; path < 9; path++) {
            for (int distance = 0; distance <= 25; distance++) {
                BlockPos target = new BlockPos(
                        (int) (origin.getX() + ray.x * distance),
                        origin.getY(),
                        (int) (origin.getZ() + ray.z * distance));
                ejectBlock(level, target, 0.15D + 0.025D * distance);
            }
            ray = ray.yRot(-1.0F / 16.0F);
        }
    }

    private static boolean ejectBlock(ServerLevel level, BlockPos pos, double upwardVelocity) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getBlock().getExplosionResistance() >= UNBREAKABLE_RESISTANCE) return false;
        PowerFistRubbleEntity rubble = PowerFistRubbleEntity.create(level, pos, state, upwardVelocity);
        level.addFreshEntity(rubble);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        return true;
    }

    public static void decontaminate(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockState replacement = decontaminatedState(state, level.random::nextInt);
        if (replacement != state) level.setBlock(pos, replacement, Block.UPDATE_ALL);
    }

    /** Conversion roulette, separated so tests can rig the wheel. */
    public static BlockState decontaminatedState(BlockState state, IntUnaryOperator nextInt) {
        if (state.is(ModBlocks.WASTE_EARTH.get()) && nextInt.applyAsInt(3) != 0) {
            return Blocks.GRASS_BLOCK.defaultBlockState();
        }
        if (state.is(ModBlocks.WASTE_MYCELIUM.get()) && nextInt.applyAsInt(5) == 0) {
            return Blocks.MYCELIUM.defaultBlockState();
        }
        if (state.is(ModBlocks.WASTE_TRINITITE.get()) && nextInt.applyAsInt(3) == 0) {
            return Blocks.SAND.defaultBlockState();
        }
        if (state.is(ModBlocks.WASTE_TRINITITE_RED.get()) && nextInt.applyAsInt(3) == 0) {
            return Blocks.RED_SAND.defaultBlockState();
        }
        if (state.is(ModBlocks.WASTE_LOG.get()) && nextInt.applyAsInt(3) != 0) {
            return Blocks.OAK_LOG.defaultBlockState();
        }
        if (state.is(ModBlocks.WASTE_PLANKS.get()) && nextInt.applyAsInt(3) != 0) {
            return Blocks.OAK_PLANKS.defaultBlockState();
        }
        if ((state.is(ModBlocks.BLOCK_TRINITITE.get()) || state.is(ModBlocks.BLOCK_WASTE.get()))
                && nextInt.applyAsInt(10) == 0) {
            return ModBlocks.get("block_lead").get().defaultBlockState();
        }
        if (state.is(ModBlocks.SELLAFIELD.get())) {
            int radiationLevel = state.getValue(SellafieldBlock.LEVEL);
            if (radiationLevel > 0) {
                if (radiationLevel == 5 && nextInt.applyAsInt(10) == 0) {
                    return state.setValue(SellafieldBlock.LEVEL, 4);
                }
                if (nextInt.applyAsInt(5) == 0) {
                    return state.setValue(SellafieldBlock.LEVEL, radiationLevel - 1);
                }
            } else if (nextInt.applyAsInt(5) == 0) {
                return ModBlocks.SELLAFIELD_SLAKED.get().defaultBlockState();
            }
        }
        return state;
    }

    private static void playIgnite(ServerLevel level, Player player) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.IMMOLATOR_IGNITE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static Item itemFor(Mode mode) {
        Supplier<? extends Item> item = switch (mode) {
            case DIG -> ModItems.MULTITOOL_DIG;
            case SILK -> ModItems.MULTITOOL_SILK;
            case EXT -> ModItems.MULTITOOL_EXT;
            case MINER -> ModItems.MULTITOOL_MINER;
            case HIT -> ModItems.MULTITOOL_HIT;
            case BEAM -> ModItems.MULTITOOL_BEAM;
            case SKY -> ModItems.MULTITOOL_SKY;
            case MEGA -> ModItems.MULTITOOL_MEGA;
            case JOULE -> ModItems.MULTITOOL_JOULE;
            case DECON -> ModItems.MULTITOOL_DECON;
        };
        return item.get();
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return mode.isTool() && !state.isAir() ? TOOL_SPEED : super.getDestroySpeed(stack, state);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return mode.isTool() || super.isCorrectToolForDrops(stack, state);
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility ability) {
        return mode.isTool() && (ItemAbilities.DEFAULT_PICKAXE_ACTIONS.contains(ability)
                || ItemAbilities.DEFAULT_AXE_ACTIONS.contains(ability)
                || ItemAbilities.DEFAULT_SHOVEL_ACTIONS.contains(ability)
                || ItemAbilities.DEFAULT_HOE_ACTIONS.contains(ability));
    }

    @Override
    public int getEnchantmentValue() {
        // Only the two tool claws know how enchanting tables work.
        return mode.isTool() ? 25 : 0;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return mode.isTool();
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (mode.isTool()) stack.hurtAndBreak(2, attacker, EquipmentSlot.MAINHAND);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        for (int line = 0; line < 2; line++) {
            tooltip.add(Component.translatable("item.hbm.multitool_" + mode.id() + ".desc." + line)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    public enum Mode {
        DIG("dig", 9.5D, true, false, false),
        SILK("silk", 9.5D, true, false, false),
        EXT("ext", 7.0D, false, false, true),
        MINER("miner", 8.0D, false, true, false),
        HIT("hit", 16.0D, false, false, false),
        BEAM("beam", 8.0D, false, true, false),
        SKY("sky", 5.0D, false, true, false),
        MEGA("mega", 12.0D, false, false, true),
        JOULE("joule", 12.0D, false, false, true),
        DECON("decon", 5.0D, false, false, true);

        private final String id;
        private final double attackDamage;
        private final boolean tool;
        private final boolean airAction;
        private final boolean blockAction;

        Mode(String id, double attackDamage, boolean tool, boolean airAction, boolean blockAction) {
            this.id = id;
            this.attackDamage = attackDamage;
            this.tool = tool;
            this.airAction = airAction;
            this.blockAction = blockAction;
        }

        public String id() { return id; }
        public double attackDamage() { return attackDamage; }
        public boolean isTool() { return tool; }
        public boolean hasAirAction() { return airAction; }
        public boolean hasBlockAction() { return blockAction; }
        public Mode next() { return values()[(ordinal() + 1) % values().length]; }
    }
}
