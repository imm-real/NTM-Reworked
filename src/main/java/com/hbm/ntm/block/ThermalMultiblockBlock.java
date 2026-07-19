package com.hbm.ntm.block;

import com.hbm.ntm.blockentity.AshpitBlockEntity;
import com.hbm.ntm.blockentity.FireboxBlockEntity;
import com.hbm.ntm.blockentity.ThermalProxyBlockEntity;
import com.hbm.ntm.blockentity.StirlingBlockEntity;
import com.hbm.ntm.blockentity.SawmillBlockEntity;
import com.hbm.ntm.item.SawmillMachineBlockItem;
import com.hbm.ntm.item.StirlingMachineBlockItem;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public abstract class ThermalMultiblockBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    // Integer blockstate properties cannot be negative; values encode delta-to-core + 1.
    public static final IntegerProperty CORE_X = IntegerProperty.create("core_x", 0, 2);
    public static final IntegerProperty CORE_Y = IntegerProperty.create("core_y", 0, 1);
    public static final IntegerProperty CORE_Z = IntegerProperty.create("core_z", 0, 2);
    public static final BooleanProperty COG = BooleanProperty.create("cog");
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);

    private final Kind kind;

    protected ThermalMultiblockBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(CORE_X, 1)
                .setValue(CORE_Y, 1)
                .setValue(CORE_Z, 1)
                .setValue(COG, true));
    }

    public Kind kind() {
        return kind;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos clicked = context.getClickedPos();
        BlockPos core = clicked.relative(facing.getOpposite());
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y < kind.height; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos part = core.offset(x, y, z);
                    if (!part.equals(clicked) && !context.getLevel().getBlockState(part).canBeReplaced(context)) {
                        return null;
                    }
                }
            }
        }
        return stateForPart(clicked, core, facing, true);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos clicked, BlockState state, LivingEntity placer, ItemStack stack) {
        BlockPos core = corePosition(clicked, state);
        Direction facing = state.getValue(FACING);
        boolean hasCog = switch (kind) {
            case STIRLING -> !StirlingMachineBlockItem.isMissingCog(stack);
            case SAWMILL -> !SawmillMachineBlockItem.isMissingBlade(stack);
            default -> true;
        };
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y < kind.height; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos part = core.offset(x, y, z);
                    level.setBlock(part, stateForPart(part, core, facing, hasCog), Block.UPDATE_ALL);
                }
            }
        }
        if (level.getBlockEntity(core) instanceof StirlingBlockEntity stirling) {
            stirling.setHasCog(hasCog);
        } else if (level.getBlockEntity(core) instanceof SawmillBlockEntity sawmill) {
            sawmill.setHasBlade(hasCog);
        }
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            if (level.getBlockEntity(core) instanceof FireboxBlockEntity firebox) {
                firebox.setCustomName(stack.getHoverName());
            } else if (level.getBlockEntity(core) instanceof AshpitBlockEntity ashpit) {
                ashpit.setCustomName(stack.getHoverName());
            }
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos position,
            Player player,
            BlockHitResult hitResult
    ) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        BlockPos core = corePosition(position, state);
        BlockEntity blockEntity = level.getBlockEntity(core);
        if (kind == Kind.STIRLING && blockEntity instanceof StirlingBlockEntity stirling) {
            ItemStack held = player.getMainHandItem();
            if (!level.isClientSide && !stirling.hasCog() && held.is(ModItems.GEAR_LARGE.get())) {
                held.shrink(1); // Original consumes the gear even in creative mode.
                stirling.setHasCog(true);
                updateCogState(level, core, true);
                level.playSound(null, position, com.hbm.ntm.registry.ModSounds.UPGRADE_PLUG.get(),
                        SoundSource.PLAYERS, 1.5F, 0.75F);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && blockEntity instanceof net.minecraft.world.MenuProvider provider) {
            serverPlayer.openMenu(provider, buffer -> buffer.writeBlockPos(core));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos position, BlockState newState, boolean movedByPiston) {
        if (state.is(newState.getBlock())) {
            super.onRemove(state, level, position, newState, movedByPiston);
            return;
        }
        BlockPos core = corePosition(position, state);
        if (!level.isClientSide && level.getBlockEntity(core) instanceof net.minecraft.world.Container container) {
            Containers.dropContents(level, core, container);
        }
        if (!REMOVING.get()) {
            REMOVING.set(true);
            try {
                for (int x = -1; x <= 1; x++) {
                    for (int y = 0; y < kind.height; y++) {
                        for (int z = -1; z <= 1; z++) {
                            BlockPos part = core.offset(x, y, z);
                            if (!part.equals(position) && level.getBlockState(part).is(this)) {
                                level.setBlock(part, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                                        Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                            }
                        }
                    }
                }
            } finally {
                REMOVING.set(false);
            }
        }
        super.onRemove(state, level, position, newState, movedByPiston);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        if (isCore(state)) {
            return switch (kind) {
                case FIREBOX -> new FireboxBlockEntity(position, state);
                case HEATING_OVEN -> new FireboxBlockEntity(position, state);
                case ASHPIT -> new AshpitBlockEntity(position, state);
                case STIRLING -> new StirlingBlockEntity(position, state);
                case SAWMILL -> new SawmillBlockEntity(position, state);
            };
        }
        if (kind != Kind.STIRLING && kind != Kind.SAWMILL || isLowerCardinalPart(state)) {
            return new ThermalProxyBlockEntity(position, state);
        }
        return null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        if (!isCore(state)) {
            return null;
        }
        return switch (kind) {
            case FIREBOX -> createTickerHelper(type, ModBlockEntities.HEATER_FIREBOX.get(), FireboxBlockEntity::tick);
            case HEATING_OVEN -> createTickerHelper(type, ModBlockEntities.HEATER_OVEN.get(), FireboxBlockEntity::tick);
            case ASHPIT -> createTickerHelper(type, ModBlockEntities.MACHINE_ASHPIT.get(), AshpitBlockEntity::tick);
            case STIRLING -> createTickerHelper(type, ModBlockEntities.MACHINE_STIRLING.get(), StirlingBlockEntity::tick);
            case SAWMILL -> createTickerHelper(type, ModBlockEntities.MACHINE_SAWMILL.get(), SawmillBlockEntity::tick);
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CORE_X, CORE_Y, CORE_Z, COG);
    }

    public static BlockPos corePosition(BlockPos position, BlockState state) {
        return position.offset(state.getValue(CORE_X) - 1, state.getValue(CORE_Y) - 1,
                state.getValue(CORE_Z) - 1);
    }

    public static boolean isCore(BlockState state) {
        return state.getValue(CORE_X) == 1 && state.getValue(CORE_Y) == 1 && state.getValue(CORE_Z) == 1;
    }

    public static boolean isStirlingPowerPort(BlockState state) {
        if (!(state.getBlock() instanceof ThermalMultiblockBlock block) || block.kind != Kind.STIRLING
                || !isLowerCardinalPart(state)) {
            return false;
        }
        return true;
    }

    private static boolean isLowerCardinalPart(BlockState state) {
        if (state.getValue(CORE_Y) != 1) return false;
        int coreX = state.getValue(CORE_X);
        int coreZ = state.getValue(CORE_Z);
        return (coreX == 1 && coreZ != 1) || (coreZ == 1 && coreX != 1);
    }

    public static void updateCogState(Level level, BlockPos core, boolean hasCog) {
        BlockState coreState = level.getBlockState(core);
        if (!(coreState.getBlock() instanceof ThermalMultiblockBlock block) || block.kind != Kind.STIRLING) {
            return;
        }
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos part = core.offset(x, y, z);
                    BlockState state = level.getBlockState(part);
                    if (state.is(block) && state.getValue(COG) != hasCog) {
                        level.setBlock(part, state.setValue(COG, hasCog), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    public static void updateSawbladeState(Level level, BlockPos core, boolean hasBlade) {
        BlockState coreState = level.getBlockState(core);
        if (!(coreState.getBlock() instanceof ThermalMultiblockBlock block) || block.kind != Kind.SAWMILL) {
            return;
        }
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos part = core.offset(x, y, z);
                    BlockState state = level.getBlockState(part);
                    if (state.is(block) && state.getValue(COG) != hasBlade) {
                        level.setBlock(part, state.setValue(COG, hasBlade), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private BlockState stateForPart(BlockPos part, BlockPos core, Direction facing, boolean hasCog) {
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(CORE_X, core.getX() - part.getX() + 1)
                .setValue(CORE_Y, core.getY() - part.getY() + 1)
                .setValue(CORE_Z, core.getZ() - part.getZ() + 1)
                .setValue(COG, hasCog);
    }

    public enum Kind implements StringRepresentable {
        FIREBOX("firebox", 1),
        HEATING_OVEN("heating_oven", 1),
        ASHPIT("ashpit", 1),
        STIRLING("stirling", 2),
        SAWMILL("sawmill", 2);

        private final String name;
        private final int height;

        Kind(String name, int height) {
            this.name = name;
            this.height = height;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
