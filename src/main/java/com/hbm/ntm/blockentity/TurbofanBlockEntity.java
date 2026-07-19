package com.hbm.ntm.blockentity;

import com.hbm.ntm.block.TurbofanBlock;
import com.hbm.ntm.compat.TurbofanAirflowFrame;
import com.hbm.ntm.energy.HeProvider;
import com.hbm.ntm.inventory.TurbofanMenu;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.item.HeBatteryItem;
import com.hbm.ntm.item.InfiniteFluidBarrelItem;
import com.hbm.ntm.item.MachineUpgradeItem;
import com.hbm.ntm.item.SourceFluidContainerItem;
import com.hbm.ntm.item.UniversalFluidTankItem;
import com.hbm.ntm.network.GibletPayload;
import com.hbm.ntm.pollution.PollutionData;
import com.hbm.ntm.radiation.ModDamageTypes;
import com.hbm.ntm.recipe.DieselGeneratorFuels;
import com.hbm.ntm.registry.ModBlockEntities;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.registry.ModParticles;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/** Jet engine in a box. Keep limbs clear of the box. */
public final class TurbofanBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider, HeProvider {
    private static Consumer<TurbofanBlockEntity> clientEffectTick = turbofan -> { };

    public static final int FUEL_INPUT = 0;
    public static final int CONTAINER_OUTPUT = 1;
    public static final int AFTERBURNER = 2;
    public static final int BATTERY = 3;
    public static final int IDENTIFIER = 4;
    public static final int SLOT_COUNT = 5;

    public static final long MAX_POWER = 1_000_000L;
    public static final int FUEL_CAPACITY = 24_000;
    public static final int BLOOD_CAPACITY = 24_000;
    public static final int SMOKE_CAPACITY = 150;
    private static final int[] NO_SLOTS = new int[0];

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private FluidIdentifierItem.Selection selectedFluid = FluidIdentifierItem.Selection.KEROSENE;
    private final FluidTank fuel = new FluidTank(FUEL_CAPACITY,
            stack -> selectedFluid.accepts(stack.getFluid())) {
        @Override
        protected void onContentsChanged() {
            TurbofanBlockEntity.this.setChanged();
        }
    };
    private final FluidTank blood = new FluidTank(BLOOD_CAPACITY,
            stack -> !stack.isEmpty() && stack.is(ModFluids.BLOOD.get())) {
        @Override
        protected void onContentsChanged() {
            TurbofanBlockEntity.this.setChanged();
        }
    };

    private long power;
    private int smoke;
    private int afterburner;
    private boolean wasOn;
    private boolean showBlood;
    private int output;
    private int consumption;
    private float spin;
    private float lastSpin;
    private int momentum;
    private long lastClientAnimationTick = Long.MIN_VALUE;
    private Component customName;

    private final IFluidHandler portFluidHandler = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 3;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return switch (tank) {
                case 0 -> fuel.getFluid().copy();
                case 1 -> blood.getFluid().copy();
                case 2 -> smoke > 0 ? new FluidStack(ModFluids.SMOKE.get(), smoke) : FluidStack.EMPTY;
                default -> FluidStack.EMPTY;
            };
        }

        @Override
        public int getTankCapacity(int tank) {
            return switch (tank) {
                case 0 -> FUEL_CAPACITY;
                case 1 -> BLOOD_CAPACITY;
                case 2 -> SMOKE_CAPACITY;
                default -> 0;
            };
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 && selectedFluid.accepts(stack.getFluid());
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return selectedFluid.accepts(resource.getFluid()) ? fuel.fill(resource, action) : 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return FluidStack.EMPTY;
            if (resource.is(ModFluids.BLOOD.get())) return blood.drain(resource, action);
            if (!resource.is(ModFluids.SMOKE.get())) return FluidStack.EMPTY;
            return drainSmoke(resource.getAmount(), action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (!blood.isEmpty()) return blood.drain(maxDrain, action);
            return drainSmoke(maxDrain, action);
        }
    };

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> (int) power;
                case 1 -> (int) (power >>> 32);
                case 2 -> (int) MAX_POWER;
                case 3 -> (int) (MAX_POWER >>> 32);
                case 4 -> fuel.getFluidAmount();
                case 5 -> selectedFluid.ordinal();
                case 6 -> afterburner;
                case 7 -> wasOn ? 1 : 0;
                case 8 -> showBlood ? 1 : 0;
                case 9 -> blood.getFluidAmount();
                case 10 -> smoke;
                case 11 -> output;
                case 12 -> consumption;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> power = power & 0xFFFFFFFF00000000L | value & 0xFFFFFFFFL;
                case 1 -> power = power & 0xFFFFFFFFL | (long) value << 32;
                case 5 -> {
                    FluidIdentifierItem.Selection[] values = FluidIdentifierItem.Selection.values();
                    if (value >= 0 && value < values.length) selectedFluid = values[value];
                }
                case 6 -> afterburner = value;
                case 7 -> wasOn = value != 0;
                case 8 -> showBlood = value != 0;
                case 9 -> setBloodAmount(value);
                case 10 -> smoke = value;
                case 11 -> output = value;
                case 12 -> consumption = value;
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return TurbofanMenu.DATA_COUNT;
        }
    };

    public TurbofanBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MACHINE_TURBOFAN.get(), position, state);
    }

    public static void tick(Level level, BlockPos position, BlockState state, TurbofanBlockEntity turbofan) {
        if (level.isClientSide) turbofan.clientTick();
        else turbofan.serverTick((ServerLevel) level, position, state);
    }

    private void serverTick(ServerLevel level, BlockPos position, BlockState state) {
        output = 0;
        consumption = 0;

        // Pick a fuel before drinking the bucket. Basic manners.
        refreshSelectedFluid();
        loadFuelContainer();
        wasOn = false;
        updateAfterburner();

        int amount = 1 + afterburner;
        int amountToBurn = Math.min(amount, fuel.getFluidAmount());
        long burnValue = 0L;
        DieselGeneratorFuels.Fuel fuelProfile = DieselGeneratorFuels.fuel(selectedFluid);
        boolean redstone = hasPortRedstone(level, position, state.getValue(TurbofanBlock.FACING));

        if (!redstone) {
            if (fuelProfile.grade() == DieselGeneratorFuels.Grade.AERO) {
                burnValue = fuelProfile.combustionEnergyPerBucket() / 1_000L;
            }
            if (amountToBurn > 0) {
                wasOn = true;
                fuel.drain(amountToBurn, IFluidHandler.FluidAction.EXECUTE);
                output = (int) (burnValue * amountToBurn
                        * (1.0D + Math.min(afterburner / 3.0D, 4.0D)));
                power += output;
                consumption = amountToBurn;
                if (level.getGameTime() % 20L == 0L && fuelProfile.polluting()) emitSmoke(level, position);
            }
        }

        // Battery gets first dibs.
        chargeBattery();
        Direction facing = state.getValue(TurbofanBlock.FACING);
        for (TurbofanBlock.Connection connection : TurbofanBlock.connections(position, facing)) {
            if (power > 0L) tryProvide(level, connection.target(), connection.outward());
            exchangeFluids(level, connection.target(), connection.outward());
        }

        // No burn means no thrust, despite what the sound department says.
        if (burnValue > 0L && amountToBurn > 0) runCombustionEffects(level, position, facing);

        // Clamp last. Let the battery and ports loot the excess first.
        if (power > MAX_POWER) power = MAX_POWER;
        if (power < 0L) power = 0L;
        setChanged();
        level.sendBlockUpdated(position, state, state, Block.UPDATE_CLIENTS);
    }

    private void clientTick() {
        ensureClientAnimationTick();
        repeatLocalPlayerMotion();
        clientEffectTick.accept(this);
    }

    /** One rotor tick per game tick, even when Sable is doing Sable things. */
    public void ensureClientAnimationTick() {
        if (level == null || !level.isClientSide) return;
        long gameTime = level.getGameTime();
        if (lastClientAnimationTick == gameTime) return;
        if (lastClientAnimationTick == Long.MIN_VALUE || gameTime < lastClientAnimationTick) {
            lastClientAnimationTick = gameTime - 1L;
        }

        // Catch up a little, not until the heat death of the universe.
        long elapsed = Math.min(gameTime - lastClientAnimationTick, 200L);
        for (long tick = 0L; tick < elapsed; tick++) advanceClientAnimationOneTick();
        lastClientAnimationTick = gameTime;
    }

    private void advanceClientAnimationOneTick() {
        lastSpin = spin;
        if (wasOn) {
            if (momentum < 100) momentum++;
        } else if (momentum > 0) {
            momentum--;
        }
        // Integer division gives the rotor its charming mechanical constipation.
        spin += momentum / 2;
        if (spin >= 360.0F) {
            spin -= 360.0F;
            lastSpin -= 360.0F;
        }
    }

    /** Keeps client noise out of the server's sock drawer. */
    public static void installClientEffectTick(Consumer<TurbofanBlockEntity> tick) {
        clientEffectTick = tick;
    }

    /** Repeat thrust locally because old networking was held together with aviation fuel. */
    private void repeatLocalPlayerMotion() {
        if (!wasOn || level == null) return;
        Player localPlayer = null;
        for (Player player : level.players()) {
            if (player.isLocalPlayer()) {
                localPlayer = player;
                break;
            }
        }
        if (localPlayer == null || localPlayer.getAbilities().instabuild) return;

        Direction facing = getBlockState().getValue(TurbofanBlock.FACING);
        Direction airflow = facing.getClockWise();
        Direction width = airflow.getClockWise();
        AABB playerBounds = worldBoundsToAirflowFrame(localPlayer.getBoundingBox());
        Vec3 airflowPush = localVectorToWorld(
                Vec3.atLowerCornerOf(airflow.getNormal()).scale(-0.2D));
        if (playerBounds.intersects(directionalBounds(worldPosition, airflow, width, -3.5D, -19.5D))
                || playerBounds.intersects(directionalBounds(worldPosition, airflow, width, 3.5D, 8.5D))) {
            push(localPlayer, airflowPush);
        }
        if (playerBounds.intersects(directionalBounds(worldPosition, airflow, width, 3.5D, 3.75D))) {
            localPlayer.makeStuckInBlock(Blocks.COBWEB.defaultBlockState(), new Vec3(0.25D, 0.05D, 0.25D));
        }
    }

    private void updateAfterburner() {
        ItemStack stack = items.get(AFTERBURNER);
        if (stack.is(ModItems.FLAME_PONY.get())) {
            afterburner = 100;
            return;
        }
        if (stack.getItem() instanceof MachineUpgradeItem upgrade
                && upgrade.type() == MachineUpgradeItem.Type.AFTERBURN) {
            afterburner = Math.min(Math.max(upgrade.level(), 0), 3);
            return;
        }
        afterburner = 0;
    }

    private boolean hasPortRedstone(ServerLevel level, BlockPos position, Direction facing) {
        for (TurbofanBlock.Connection connection : TurbofanBlock.connections(position, facing)) {
            // Check the plugs, not the decorative lies beside them.
            if (level.hasNeighborSignal(connection.target())) return true;
        }
        return false;
    }

    private void refreshSelectedFluid() {
        ItemStack identifier = items.get(IDENTIFIER);
        if (!(identifier.getItem() instanceof FluidIdentifierItem)) return;
        FluidIdentifierItem.Selection selection = FluidIdentifierItem.primary(identifier);
        if (selection == selectedFluid) return;
        selectedFluid = selection;
        fuel.setFluid(FluidStack.EMPTY);
        setChanged();
    }

    private void loadFuelContainer() {
        ItemStack input = items.get(FUEL_INPUT);
        if (input.isEmpty()) return;
        if (InfiniteFluidBarrelItem.is(input)) {
            if (selectedFluid != FluidIdentifierItem.Selection.NONE
                    && InfiniteFluidBarrelItem.fillTank(fuel, selectedFluid.fluid()) > 0) setChanged();
            return;
        }

        Fluid fluid;
        ItemStack remainder;
        if (input.getItem() instanceof UniversalFluidTankItem) {
            fluid = UniversalFluidTankItem.fluid(input).fluid();
            remainder = new ItemStack(ModItems.FLUID_TANK_EMPTY.get());
        } else if (input.is(ModItems.CANISTER_FULL.get())) {
            fluid = SourceFluidContainerItem.fluid(input).fluid();
            remainder = new ItemStack(ModItems.CANISTER_EMPTY.get());
        } else if (input.is(ModItems.GAS_FULL.get())) {
            fluid = SourceFluidContainerItem.fluid(input).fluid();
            remainder = new ItemStack(ModItems.GAS_EMPTY.get());
        } else {
            drainFillableItem(input);
            return;
        }

        if (!selectedFluid.accepts(fluid) || !canMerge(items.get(CONTAINER_OUTPUT), remainder)) return;
        FluidStack load = new FluidStack(fluid, 1_000);
        if (fuel.fill(load, IFluidHandler.FluidAction.SIMULATE) != 1_000) return;
        fuel.fill(load, IFluidHandler.FluidAction.EXECUTE);
        input.shrink(1);
        if (input.isEmpty()) items.set(FUEL_INPUT, ItemStack.EMPTY);
        mergeOutput(CONTAINER_OUTPUT, remainder);
        setChanged();
    }

    /** Bucket violence fallback. */
    private void drainFillableItem(ItemStack input) {
        if (input.getCount() != 1 || selectedFluid == FluidIdentifierItem.Selection.NONE
                || fuel.getSpace() <= 0) return;
        IFluidHandlerItem handler = input.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) return;
        FluidStack requested = new FluidStack(selectedFluid.fluid(), fuel.getSpace());
        FluidStack available = handler.drain(requested, IFluidHandler.FluidAction.SIMULATE);
        if (available.isEmpty() || !selectedFluid.accepts(available.getFluid())) return;
        int accepted = fuel.fill(available, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) return;
        FluidStack drained = handler.drain(available.copyWithAmount(accepted),
                IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) return;
        fuel.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        items.set(FUEL_INPUT, handler.getContainer());
        setChanged();
    }

    private void chargeBattery() {
        ItemStack stack = items.get(BATTERY);
        if (!(stack.getItem() instanceof HeBatteryItem battery)) return;
        long amount = Math.min(Math.min(power, battery.getChargeRate(stack)),
                Math.max(battery.getMaxCharge(stack) - battery.getCharge(stack), 0L));
        if (amount <= 0L) return;
        battery.charge(stack, amount);
        power -= amount;
        setChanged();
    }

    private void exchangeFluids(ServerLevel level, BlockPos targetPosition, Direction outward) {
        IFluidHandler target = level.getCapability(Capabilities.FluidHandler.BLOCK,
                targetPosition, outward.getOpposite());
        if (target == null) return;

        if (selectedFluid != FluidIdentifierItem.Selection.NONE && fuel.getSpace() > 0) {
            FluidStack request = new FluidStack(selectedFluid.fluid(), fuel.getSpace());
            FluidStack available = target.drain(request, IFluidHandler.FluidAction.SIMULATE);
            if (!available.isEmpty() && selectedFluid.accepts(available.getFluid())) {
                int accepted = fuel.fill(available, IFluidHandler.FluidAction.SIMULATE);
                if (accepted > 0) {
                    FluidStack drained = target.drain(new FluidStack(selectedFluid.fluid(), accepted),
                            IFluidHandler.FluidAction.EXECUTE);
                    fuel.fill(drained, IFluidHandler.FluidAction.EXECUTE);
                }
            }
        }

        if (!blood.isEmpty()) {
            int accepted = target.fill(blood.getFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
            if (accepted > 0) blood.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
        }
        if (smoke > 0) {
            int accepted = target.fill(new FluidStack(ModFluids.SMOKE.get(), smoke),
                    IFluidHandler.FluidAction.EXECUTE);
            smoke -= Math.min(Math.max(accepted, 0), smoke);
        }
    }

    private void emitSmoke(ServerLevel level, BlockPos position) {
        // amount was decorative in 1.7.10. Beautiful API design.
        smoke++;
        if (smoke <= SMOKE_CAPACITY) return;
        int overflow = smoke - SMOKE_CAPACITY;
        smoke = SMOKE_CAPACITY;
        PollutionData.get(level).increment(position, PollutionData.Type.SOOT, overflow / 100.0F);
        if (level.random.nextInt(3) == 0) {
            level.playSound(null, position.getX(), position.getY(), position.getZ(),
                    SoundEvents.FIRE_EXTINGUISH,
                    SoundSource.BLOCKS, 0.1F, 1.5F);
        }
    }

    private void runCombustionEffects(ServerLevel level, BlockPos position, Direction facing) {
        // Placement faces sideways because the model said so.
        Direction airflow = facing.getClockWise();
        Direction width = airflow.getClockWise();
        Vec3 airflowPush = localVectorToWorld(
                Vec3.atLowerCornerOf(airflow.getNormal()).scale(-0.2D));

        if (afterburner > 0) emitAfterburnerParticles(level, position, airflow, width);

        // Plot-local first; Sable can unfold the universe afterward.
        AABB exhaust = directionalBounds(position, airflow, width, -3.5D, -19.5D);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, exhaust)) {
            if (!worldBoundsToAirflowFrame(entity.getBoundingBox()).intersects(exhaust)) continue;
            if (afterburner > 0) {
                entity.igniteForSeconds(5.0F);
                entity.hurt(level.damageSources().onFire(), 5.0F);
            }
            push(entity, airflowPush);
        }

        AABB intake = directionalBounds(position, airflow, width, 3.5D, 8.5D);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, intake)) {
            if (!worldBoundsToAirflowFrame(entity.getBoundingBox()).intersects(intake)) continue;
            push(entity, airflowPush);
        }

        AABB blades = directionalBounds(position, airflow, width, 3.5D, 3.75D);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, blades)) {
            if (!worldBoundsToAirflowFrame(entity.getBoundingBox()).intersects(blades)) continue;
            entity.hurt(level.damageSources().source(ModDamageTypes.BLENDER), 1_000.0F);
            entity.makeStuckInBlock(Blocks.COBWEB.defaultBlockState(), new Vec3(0.25D, 0.05D, 0.25D));
            if (!entity.isAlive() && entity instanceof LivingEntity) {
                PacketDistributor.sendToPlayersNear(level, null, entity.getX(), entity.getY(), entity.getZ(),
                        150.0D, new GibletPayload(entity.getId()));
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR,
                        SoundSource.BLOCKS, 2.0F, 0.95F + level.random.nextFloat() * 0.2F);
                blood.fill(new FluidStack(ModFluids.BLOOD.get(), 50), IFluidHandler.FluidAction.EXECUTE);
                showBlood = true;
            }
        }
    }

    private void emitAfterburnerParticles(ServerLevel level, BlockPos position,
                                          Direction airflow, Direction width) {
        for (int index = 0; index < 2; index++) {
            double speed = 2.0D + level.random.nextDouble() * 3.0D;
            double deviation = level.random.nextGaussian() * 0.2D;
            double x = position.getX() + 0.5D - airflow.getStepX() * (3 - index);
            double y = position.getY() + 1.5D;
            double z = position.getZ() + 0.5D - airflow.getStepZ() * (3 - index);
            double velocityX = -airflow.getStepX() * speed + deviation;
            double velocityZ = -airflow.getStepZ() * speed + deviation;
            sendParticleWithin(level, position, ModParticles.GAS_FLAME_LARGE.get(), x, y, z,
                    velocityX, 0.0D, velocityZ);
        }

        if (afterburner <= 90) return;
        if (level.random.nextInt(30) == 0) {
            level.playSound(null, position.getX() + 0.5D, position.getY() + 1.5D,
                    position.getZ() + 0.5D,
                    ModSounds.TURBOFAN_DAMAGE.get(), SoundSource.BLOCKS,
                    3.0F, 0.95F + level.random.nextFloat() * 0.2F);
        }

        double along = level.random.nextDouble() * 4.0D - 2.0D;
        double across = level.random.nextDouble() * 2.0D - 1.0D;
        double x = position.getX() + 0.5D + airflow.getStepX() * along + width.getStepX() * across;
        double y = position.getY() + 1.0D + level.random.nextDouble() * 2.0D;
        double z = position.getZ() + 0.5D - airflow.getStepZ() * along + width.getStepZ() * across;
        sendParticleWithin(level, position, ModParticles.GAS_FLAME_SMALL.get(), x, y, z,
                0.0D, level.random.nextDouble() * 0.1D, 0.0D);
    }

    /** Smoke visible from the next postal code. */
    private void sendParticleWithin(ServerLevel level, BlockPos source, ParticleOptions particle,
                                    double x, double y, double z,
                                    double velocityX, double velocityY, double velocityZ) {
        ClientboundLevelParticlesPacket packet = new ClientboundLevelParticlesPacket(
                // Force it through; Sable still has to unbend plot space.
                particle, true, x, y, z,
                (float) velocityX, (float) velocityY, (float) velocityZ, 1.0F, 0);
        Vec3 center = new Vec3(source.getX(), source.getY(), source.getZ());
        for (ServerPlayer player : level.players()) {
            if (distanceSquaredToLocalPosition(player.position(), center) <= 150.0D * 150.0D) {
                player.connection.send(packet);
            }
        }
    }

    private static AABB directionalBounds(BlockPos position, Direction axis, Direction width,
                                          double first, double second) {
        double centerX = position.getX() + 0.5D;
        double centerZ = position.getZ() + 0.5D;
        double firstX = centerX + axis.getStepX() * first - width.getStepX() * 1.5D;
        double firstZ = centerZ + axis.getStepZ() * first - width.getStepZ() * 1.5D;
        double secondX = centerX + axis.getStepX() * second + width.getStepX() * 1.5D;
        double secondZ = centerZ + axis.getStepZ() * second + width.getStepZ() * 1.5D;
        return new AABB(Math.min(firstX, secondX), position.getY(), Math.min(firstZ, secondZ),
                Math.max(firstX, secondX), position.getY() + 3.0D, Math.max(firstZ, secondZ));
    }

    private Vec3 localVectorToWorld(Vec3 localVector) {
        if ((Object) this instanceof TurbofanAirflowFrame frame) {
            return frame.hbm$localVectorToWorld(localVector);
        }
        return localVector;
    }

    private AABB worldBoundsToAirflowFrame(AABB worldBounds) {
        if ((Object) this instanceof TurbofanAirflowFrame frame) {
            return frame.hbm$worldBoundsToLocal(worldBounds);
        }
        return worldBounds;
    }

    private double distanceSquaredToLocalPosition(Vec3 observerPosition, Vec3 localPosition) {
        if ((Object) this instanceof TurbofanAirflowFrame frame) {
            return frame.hbm$distanceSquaredToLocalPosition(observerPosition, localPosition);
        }
        return observerPosition.distanceToSqr(localPosition);
    }

    private static void push(Entity entity, Vec3 impulse) {
        entity.setDeltaMovement(entity.getDeltaMovement().add(impulse));
        entity.hurtMarked = true;
    }

    private FluidStack drainSmoke(int maxDrain, IFluidHandler.FluidAction action) {
        int drained = Math.min(Math.max(maxDrain, 0), smoke);
        if (drained <= 0) return FluidStack.EMPTY;
        FluidStack result = new FluidStack(ModFluids.SMOKE.get(), drained);
        if (action.execute()) {
            smoke -= drained;
            setChanged();
        }
        return result;
    }

    private static boolean canMerge(ItemStack target, ItemStack addition) {
        return !addition.isEmpty() && (target.isEmpty()
                || ItemStack.isSameItemSameComponents(target, addition)
                && target.getCount() + addition.getCount() <= target.getMaxStackSize());
    }

    private void mergeOutput(int slot, ItemStack addition) {
        if (items.get(slot).isEmpty()) items.set(slot, addition.copy());
        else items.get(slot).grow(addition.getCount());
    }

    private void conformTanks() {
        if (!fuel.isEmpty() && !selectedFluid.accepts(fuel.getFluid().getFluid())) fuel.setFluid(FluidStack.EMPTY);
        if (!blood.isEmpty() && !blood.getFluid().is(ModFluids.BLOOD.get())) blood.setFluid(FluidStack.EMPTY);
        if (fuel.getFluidAmount() > FUEL_CAPACITY) fuel.setFluid(fuel.getFluid().copyWithAmount(FUEL_CAPACITY));
        if (blood.getFluidAmount() > BLOOD_CAPACITY) blood.setFluid(blood.getFluid().copyWithAmount(BLOOD_CAPACITY));
        smoke = Math.clamp(smoke, 0, SMOKE_CAPACITY);
    }

    private void setBloodAmount(int amount) {
        int clamped = Math.clamp(amount, 0, BLOOD_CAPACITY);
        blood.setFluid(clamped == 0 ? FluidStack.EMPTY : new FluidStack(ModFluids.BLOOD.get(), clamped));
    }

    public IFluidHandler portFluidHandler() { return portFluidHandler; }
    public FluidTank fuelTank() { return fuel; }
    public FluidTank bloodTank() { return blood; }
    public FluidIdentifierItem.Selection selectedFluid() { return selectedFluid; }
    public int fuelAmount() { return fuel.getFluidAmount(); }
    public int bloodAmount() { return blood.getFluidAmount(); }
    public int smokeAmount() { return smoke; }
    public int afterburner() { return afterburner; }
    public boolean wasOn() { return wasOn; }
    public boolean showBlood() { return showBlood; }
    public int output() { return output; }
    public int consumption() { return consumption; }
    public float spin() { return spin; }
    public float lastSpin() { return lastSpin; }
    public int momentum() { return momentum; }

    public AABB renderBounds() {
        Direction facing = getBlockState().hasProperty(TurbofanBlock.FACING)
                ? getBlockState().getValue(TurbofanBlock.FACING) : Direction.NORTH;
        AABB bounds = new AABB(worldPosition);
        for (BlockPos part : TurbofanBlock.partPositions(worldPosition, facing)) {
            bounds = bounds.minmax(new AABB(part));
        }
        return bounds.inflate(0.25D);
    }

    public void setCustomName(Component customName) {
        this.customName = customName;
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.machineTurbofan");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new TurbofanMenu(id, inventory, this, data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putLong("powerTime", power);
        tag.putString("selectedFluid", selectedFluid.id());
        tag.put("fuel", fuel.writeToNBT(registries, new CompoundTag()));
        tag.put("blood", blood.writeToNBT(registries, new CompoundTag()));
        tag.putInt("smoke0", smoke);
        tag.putBoolean("showBlood", showBlood);
        if (customName != null) tag.putString("name", Component.Serializer.toJson(customName, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        power = Math.max(tag.contains("powerTime") ? tag.getLong("powerTime") : tag.getLong("power"), 0L);
        selectedFluid = tag.contains("selectedFluid")
                ? FluidIdentifierItem.Selection.byId(tag.getString("selectedFluid"))
                : FluidIdentifierItem.Selection.KEROSENE;
        if (tag.contains("fuel")) fuel.readFromNBT(registries, tag.getCompound("fuel"));
        if (tag.contains("blood")) blood.readFromNBT(registries, tag.getCompound("blood"));
        smoke = tag.getInt("smoke0");
        showBlood = tag.getBoolean("showBlood");
        customName = tag.contains("name") ? Component.Serializer.fromJson(tag.getString("name"), registries) : null;
        conformTanks();
        if (power > MAX_POWER) power = MAX_POWER;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("powerTime", power);
        tag.putString("selectedFluid", selectedFluid.id());
        tag.putInt("fuelAmount", fuel.getFluidAmount());
        tag.putInt("bloodAmount", blood.getFluidAmount());
        tag.putInt("smoke0", smoke);
        tag.putInt("afterburner", afterburner);
        tag.putBoolean("wasOn", wasOn);
        tag.putBoolean("showBlood", showBlood);
        tag.putInt("output", output);
        tag.putInt("consumption", consumption);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        power = Math.clamp(tag.getLong("powerTime"), 0L, MAX_POWER);
        selectedFluid = FluidIdentifierItem.Selection.byId(tag.getString("selectedFluid"));
        int fuelAmount = Math.clamp(tag.getInt("fuelAmount"), 0, FUEL_CAPACITY);
        fuel.setFluid(fuelAmount == 0 ? FluidStack.EMPTY
                : new FluidStack(selectedFluid.fluid(), fuelAmount));
        setBloodAmount(tag.getInt("bloodAmount"));
        smoke = Math.clamp(tag.getInt("smoke0"), 0, SMOKE_CAPACITY);
        afterburner = Math.max(tag.getInt("afterburner"), 0);
        wasOn = tag.getBoolean("wasOn");
        showBlood = tag.getBoolean("showBlood");
        output = Math.max(tag.getInt("output"), 0);
        consumption = Math.max(tag.getInt("consumption"), 0);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        // Update tag, not save tag. Otherwise a burning engine becomes a very loud statue.
        handleUpdateTag(packet.getTag(), registries);
    }

    @Override public int getContainerSize() { return SLOT_COUNT; }
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
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getCenter()) <= 128.0D;
    }
    @Override public void clearContent() { items.clear(); setChanged(); }

    @Override public int[] getSlotsForFace(Direction side) { return NO_SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return false;
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        // Manual insertion accepts almost anything. Shift-click remains picky.
        return slot != CONTAINER_OUTPUT;
    }

    @Override public long getPower() { return Math.max(power, 0L); }
    @Override public void setPower(long power) { this.power = Math.max(power, 0L); setChanged(); }
    @Override public long getMaxPower() { return MAX_POWER; }
    @Override public boolean isHeLoaded() { return hasLevel() && !isRemoved(); }

    // Test poking sticks.
    public void setSelectedForTest(FluidIdentifierItem.Selection selection) {
        selectedFluid = selection;
        fuel.setFluid(FluidStack.EMPTY);
    }
    public int addFuelForTest(int amount) {
        return fuel.fill(new FluidStack(selectedFluid.fluid(), amount), IFluidHandler.FluidAction.EXECUTE);
    }
    public void generateForTest(ServerLevel level) {
        serverTick(level, worldPosition, getBlockState());
    }
    public void addSmokeForTest(int amount) { smoke = Math.clamp(smoke + amount, 0, SMOKE_CAPACITY); }
    public void addBloodForTest(int amount) {
        blood.fill(new FluidStack(ModFluids.BLOOD.get(), Math.max(amount, 0)), IFluidHandler.FluidAction.EXECUTE);
    }
}
