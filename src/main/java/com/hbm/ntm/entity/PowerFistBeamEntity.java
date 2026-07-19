package com.hbm.ntm.entity;

import com.hbm.ntm.radiation.ModDamageTypes;
import com.hbm.ntm.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/** The straight-flying mining and immolator beams fired by the Power Fist. */
public final class PowerFistBeamEntity extends Projectile {
    private static final double MINER_SPEED = 0.75D * 1.5D;
    private static final double LASER_SPEED = 1.0D * 1.5D;
    private static final double INACCURACY = 0.002499999832361937D;

    private BlockPos pendingBlock;
    private int ticksInAir;

    public PowerFistBeamEntity(EntityType<? extends PowerFistBeamEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static PowerFistBeamEntity createMiner(ServerLevel level, LivingEntity owner) {
        return create(level, owner, ModEntities.POWER_FIST_MINER_BEAM.get(), MINER_SPEED);
    }

    public static PowerFistBeamEntity createLaser(ServerLevel level, LivingEntity owner) {
        return create(level, owner, ModEntities.POWER_FIST_LASER_BEAM.get(), LASER_SPEED);
    }

    private static PowerFistBeamEntity create(ServerLevel level, LivingEntity owner,
                                               EntityType<PowerFistBeamEntity> type, double speed) {
        PowerFistBeamEntity beam = new PowerFistBeamEntity(type, level);
        beam.setOwner(owner);
        beam.setYRot(owner.getYRot());
        beam.setXRot(owner.getXRot());
        double yaw = owner.getYRot() * Mth.DEG_TO_RAD;
        beam.setPos(owner.getX() - Mth.cos((float) yaw) * 0.16D,
                owner.getEyeY() - 0.10000000149011612D,
                owner.getZ() - Mth.sin((float) yaw) * 0.16D);

        Vec3 look = owner.getLookAngle().normalize();
        Vec3 movement = new Vec3(
                look.x + beam.random.nextGaussian() * (beam.random.nextBoolean() ? -1.0D : 1.0D) * INACCURACY,
                look.y + beam.random.nextGaussian() * (beam.random.nextBoolean() ? -1.0D : 1.0D) * INACCURACY,
                look.z + beam.random.nextGaussian() * (beam.random.nextBoolean() ? -1.0D : 1.0D) * INACCURACY
        ).scale(speed);
        beam.setDeltaMovement(movement);
        beam.updateBeamRotation(movement);
        beam.yRotO = beam.getYRot();
        beam.xRotO = beam.getXRot();
        return beam;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) { }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > 100 || isInWater()) {
            discard();
            return;
        }

        Vec3 movement = getDeltaMovement();
        Vec3 end = position().add(movement);
        if (level().isClientSide) {
            if (pendingBlock != null) {
                if (!level().getBlockState(pendingBlock).isAir()) {
                    discard();
                    return;
                }
                pendingBlock = null;
            }
            BlockHitResult blockHit = level().clip(new ClipContext(
                    position(), end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (blockHit.getType() != HitResult.Type.MISS) pendingBlock = blockHit.getBlockPos();
            setPos(end);
            updateBeamRotation(movement);
            return;
        }

        ServerLevel server = (ServerLevel) level();
        if (pendingBlock != null) {
            if (!server.getBlockState(pendingBlock).isAir()) {
                if (isLaser()) igniteArea(server);
                else smeltBlock(server, pendingBlock);
                discard();
                return;
            }
            pendingBlock = null;
        }

        ticksInAir++;
        Vec3 start = position();
        BlockHitResult blockHit = server.clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 entityEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();

        EntityIntersection nearest = nearestEntity(start, entityEnd, movement);
        if (nearest != null) {
            // In 1.7 an invalid player impact nulled the complete hit result, including
            // a block hit behind that player for this tick.
            if (allowedPlayerImpact(nearest.entity())) hitEntity(nearest.entity());
        } else if (blockHit.getType() != HitResult.Type.MISS) {
            // Store the struck block and hurt it next tick, after movement finishes.
            pendingBlock = blockHit.getBlockPos();
        }

        setPos(end);
        updateBeamRotation(movement);
        if (isInWater()) discard();
        if (isInWaterRainOrBubble()) clearFire();
        checkInsideBlocks();
    }

    private EntityIntersection nearestEntity(Vec3 start, Vec3 end, Vec3 movement) {
        AABB sweep = getBoundingBox().expandTowards(movement).inflate(1.0D);
        List<Entity> candidates = level().getEntities(this, sweep, this::canCollide);
        EntityIntersection nearest = null;
        for (Entity candidate : candidates) {
            Optional<Vec3> intersection = candidate.getBoundingBox().inflate(0.3D).clip(start, end);
            if (intersection.isEmpty()) continue;
            double distance = start.distanceToSqr(intersection.get());
            if (nearest == null || distance < nearest.distanceSqr()) {
                nearest = new EntityIntersection(candidate, distance);
            }
        }
        return nearest;
    }

    private boolean canCollide(Entity entity) {
        if (!entity.isAlive() || !entity.isPickable()) return false;
        Entity owner = getOwner();
        return entity != owner || ticksInAir >= 5;
    }

    private boolean allowedPlayerImpact(Entity entity) {
        if (!(entity instanceof Player target)) return true;
        if (target.getAbilities().invulnerable) return false;
        return !(getOwner() instanceof Player owner) || owner.canHarmPlayer(target);
    }

    private void hitEntity(Entity target) {
        Entity owner = getOwner();
        var source = owner == null
                ? level().damageSources().generic()
                : level().damageSources().source(ModDamageTypes.LASER, this, owner);
        int openingDamage = Mth.ceil(getDeltaMovement().length() * 2.0D);
        if (isOnFire() && !(target instanceof EnderMan)) target.igniteForSeconds(5.0F);
        if (!target.hurt(source, openingDamage)) return;

        if (target instanceof LivingEntity living) {
            if (owner instanceof LivingEntity livingOwner) {
                EnchantmentHelper.doPostAttackEffectsWithItemSource(
                        (ServerLevel) level(), living, source, livingOwner.getWeaponItem());
            }
            if (living != owner && living instanceof Player && owner instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.send(new ClientboundGameEventPacket(
                        ClientboundGameEventPacket.ARROW_HIT_PLAYER, 0.0F));
            }
        }

        if (target instanceof LivingEntity && !(target instanceof EnderMan)) {
            target.hurt(source, 25.0F + random.nextInt(20));
            if (isLaser()) {
                igniteArea((ServerLevel) level());
            } else {
                target.igniteForSeconds(5.0F);
            }
            discard();
        }
    }

    private void smeltBlock(ServerLevel level, BlockPos position) {
        BlockState state = level.getBlockState(position);
        ItemStack input = new ItemStack(state.getBlock().asItem());
        if (input.is(Items.AIR)) return;

        SingleRecipeInput recipeInput = new SingleRecipeInput(input);
        ItemStack output = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, recipeInput, level)
                .map(holder -> holder.value().assemble(recipeInput, level.registryAccess()))
                .orElse(ItemStack.EMPTY);
        if (output.isEmpty()) return;

        level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        double x = position.getX() + random.nextFloat() * 0.8F + 0.1F;
        double y = position.getY() + random.nextFloat() * 0.8F + 0.1F;
        double z = position.getZ() + random.nextFloat() * 0.8F + 0.1F;
        ItemEntity drop = new ItemEntity(level, x, y, z, output.copy());
        drop.setDeltaMovement(random.nextGaussian() * 0.05D,
                random.nextGaussian() * 0.05D + 0.2D,
                random.nextGaussian() * 0.05D);
        level.addFreshEntity(drop);
    }

    private void igniteArea(ServerLevel level) {
        int x = (int) getX();
        int y = (int) getY();
        int z = (int) getZ();
        igniteEverySurface(level, x, y, z, 2);
        igniteFlammableSurfaces(level, x, y, z, 5);
    }

    private static void igniteEverySurface(ServerLevel level, int x, int y, int z, int radius) {
        int limit = radius * radius / 2;
        BlockPos.MutableBlockPos ground = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx < radius; dx++) {
            for (int dy = -radius; dy < radius; dy++) {
                for (int dz = -radius; dz < radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz >= limit) continue;
                    ground.set(x + dx, y + dy, z + dz);
                    BlockState base = level.getBlockState(ground);
                    BlockPos above = ground.above();
                    BlockState top = level.getBlockState(above);
                    if (!base.isAir() && (top.isAir() || top.is(Blocks.SNOW))) {
                        level.setBlock(above, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    private static void igniteFlammableSurfaces(ServerLevel level, int x, int y, int z, int radius) {
        int limit = radius * radius / 2;
        BlockPos.MutableBlockPos ground = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx < radius; dx++) {
            for (int dy = -radius; dy < radius; dy++) {
                for (int dz = -radius; dz < radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz >= limit) continue;
                    ground.set(x + dx, y + dy, z + dz);
                    BlockState base = level.getBlockState(ground);
                    BlockPos above = ground.above();
                    if (base.isFlammable(level, ground, Direction.UP)
                            && level.getBlockState(above).isAir()) {
                        level.setBlock(above, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    private boolean isLaser() {
        return getType() == ModEntities.POWER_FIST_LASER_BEAM.get();
    }

    private void updateBeamRotation(Vec3 movement) {
        setYRot((float) (Mth.atan2(movement.x, movement.z) * Mth.RAD_TO_DEG));
        setXRot((float) (Mth.atan2(movement.y, Math.hypot(movement.x, movement.z)) * Mth.RAD_TO_DEG));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        BlockPos saved = pendingBlock == null ? new BlockPos(-1, -1, -1) : pendingBlock;
        tag.putShort("xTile", (short) saved.getX());
        tag.putShort("yTile", (short) saved.getY());
        tag.putShort("zTile", (short) saved.getZ());
        tag.putDouble("damage", 2.0D);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        int x = tag.getShort("xTile");
        int y = tag.getShort("yTile");
        int z = tag.getShort("zTile");
        pendingBlock = x == -1 && y == -1 && z == -1 ? null : new BlockPos(x, y, z);
        ticksInAir = 0;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 102_400.0D;
    }

    private record EntityIntersection(Entity entity, double distanceSqr) { }
}
