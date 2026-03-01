package dev.ritual.ritualblade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Tick-driven active ritual orchestration.
 */
public final class RitualSequenceManager {
    private static final double LIFT_HEIGHT = 2.0;
    private static final double VIEW_DISTANCE_SQ = 64.0 * 64.0;

    private static final Map<RitualKey, ActiveRitual> ACTIVE = new HashMap<>();

    private RitualSequenceManager() {
    }

    public static void start(ServerWorld world, RitualSite site, UUID executorUuid) {
        RitualConfig cfg = RitualConfigHolder.get();
        RitualKey key = RitualKey.of(world, site.tablePos());

        Map<BlockPos, BlockState> originalDustStates = new HashMap<>();
        for (BlockPos dustPos : site.dustPositions()) {
            originalDustStates.put(dustPos, world.getBlockState(dustPos));
        }

        ItemEntity sword = spawnSword(world, site.tablePos());
        BlockPos chest = findClosestChest(world, site.tablePos(), cfg.chestSearchRadius);

        ActiveRitual ritual = new ActiveRitual(
            key,
            site,
            executorUuid,
            sword,
            originalDustStates,
            chest,
            world.getTimeOfDay(),
            site.participants().size(),
            cfg.particleCaps,
            cfg.liftTicks,
            0,
            cfg.restoreTimeAfter
        );

        ACTIVE.put(key, ritual);
    }

    public static boolean isActive(ServerWorld world, BlockPos tablePos) {
        return ACTIVE.containsKey(RitualKey.of(world, tablePos));
    }

    public static void tickServer(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }

        List<RitualKey> toRemove = new ArrayList<>();

        for (ActiveRitual ritual : ACTIVE.values()) {
            ServerWorld world = server.getWorld(ritual.key().worldKey());
            if (world == null) {
                toRemove.add(ritual.key());
                continue;
            }

            boolean done = tickRitual(world, ritual, RitualConfigHolder.get());
            if (done) {
                toRemove.add(ritual.key());
            }
        }

        for (RitualKey key : toRemove) {
            ACTIVE.remove(key);
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }

    private static boolean tickRitual(ServerWorld world, ActiveRitual ritual, RitualConfig cfg) {
        if (!ritual.restorePhase()) {
            int age = ritual.age();
            float progress = MathHelper.clamp((float) age / (float) ritual.liftTicks(), 0.0F, 1.0F);
            float eased = 1.0F - (1.0F - progress) * (1.0F - progress);

            forceDustPowered(world, ritual);
            tickSword(ritual, eased);
            tickTimeTowardNight(world, ritual, cfg, progress);
            tickParticles(world, ritual, progress);

            if (age >= ritual.liftTicks()) {
                finishLift(world, ritual, cfg);
                if (cfg.timeFadeEnabled && ritual.shouldRestoreTime()) {
                    ritual.setRestorePhase(true);
                    ritual.setAge(0);
                    return false;
                }
                return true;
            }

            ritual.incrementAge();
            return false;
        }

        int restoreAge = ritual.age();
        float restoreProgress = MathHelper.clamp((float) restoreAge / (float) ritual.liftTicks(), 0.0F, 1.0F);
        tickTimeRestore(world, ritual, restoreProgress);

        if (restoreAge >= ritual.liftTicks()) {
            world.setTimeOfDay(ritual.originalTime());
            return true;
        }

        ritual.incrementAge();
        return false;
    }

    private static void tickSword(ActiveRitual ritual, float easedProgress) {
        ItemEntity sword = ritual.sword();
        if (!sword.isAlive()) {
            return;
        }

        double x = ritual.site().tablePos().getX() + 0.5;
        double y = ritual.site().tablePos().getY() + 1.1 + easedProgress * LIFT_HEIGHT;
        double z = ritual.site().tablePos().getZ() + 0.5;

        sword.setNoGravity(true);
        sword.setInvulnerable(true);
        sword.setPickupDelay(32767);
        sword.setVelocity(0.0, 0.0, 0.0);
        sword.setPosition(x, y, z);
    }

    private static void tickTimeTowardNight(ServerWorld world, ActiveRitual ritual, RitualConfig cfg, float progress) {
        if (!cfg.timeFadeEnabled) {
            return;
        }

        long targetNight = 18000L;
        long start = ritual.originalTime();
        long next = (long) MathHelper.lerp(progress, (float) start, (float) targetNight);
        world.setTimeOfDay(next);
    }

    private static void tickTimeRestore(ServerWorld world, ActiveRitual ritual, float progress) {
        long targetNight = 18000L;
        long restored = (long) MathHelper.lerp(progress, (float) targetNight, (float) ritual.originalTime());
        world.setTimeOfDay(restored);
    }

    private static void forceDustPowered(ServerWorld world, ActiveRitual ritual) {
        for (BlockPos dustPos : ritual.site().dustPositions()) {
            BlockState state = world.getBlockState(dustPos);
            if (state.isOf(Blocks.REDSTONE_WIRE) && state.contains(RedstoneWireBlock.POWER)) {
                int power = state.get(RedstoneWireBlock.POWER);
                if (power != 15) {
                    world.setBlockState(dustPos, state.with(RedstoneWireBlock.POWER, 15), 3);
                }
            }
        }
    }

    private static void tickParticles(ServerWorld world, ActiveRitual ritual, float progress) {
        int participantBoost = MathHelper.clamp(ritual.participantCount(), 1, 8);
        int perTick = Math.min(ritual.particleCap(), 8 + participantBoost * 3);

        Vec3d swordPos = ritual.sword().getPos();

        if (ritual.chestPos() != null) {
            Vec3d chestCenter = Vec3d.ofCenter(ritual.chestPos());
            for (int i = 0; i < perTick; i++) {
                double t = (i + world.random.nextDouble()) / perTick;
                Vec3d point = chestCenter.lerp(swordPos, t);
                sendParticleToNearby(world, ParticleTypes.SMOKE, point, Vec3d.ZERO);
                sendParticleToNearby(world, new DustParticleEffect(0xBF0505, 1.0F), point,
                    new Vec3d(0.0, 0.005, 0.0));
            }
        }

        if (progress < 0.5F) {
            for (int i = 0; i < perTick; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2.0;
                double radius = 1.8 + world.random.nextDouble() * 1.2;
                Vec3d spawn = swordPos.add(Math.cos(angle) * radius, world.random.nextDouble() * 1.4 - 0.3,
                    Math.sin(angle) * radius);
                Vec3d velocity = swordPos.subtract(spawn).normalize().multiply(0.18);
                sendParticleToNearby(world, ParticleTypes.PORTAL, spawn, velocity);
            }
        }
    }

    private static void finishLift(ServerWorld world, ActiveRitual ritual, RitualConfig cfg) {
        restoreDust(world, ritual);

        Vec3d swordPos = ritual.sword().getPos();
        for (int i = 0; i < 80; i++) {
            Vec3d vel = new Vec3d(
                (world.random.nextDouble() - 0.5) * 0.45,
                world.random.nextDouble() * 0.35,
                (world.random.nextDouble() - 0.5) * 0.45
            );
            sendParticleToNearby(world, ParticleTypes.END_ROD, swordPos, vel);
            sendParticleToNearby(world, ParticleTypes.CRIT, swordPos, vel.multiply(0.6));
        }

        for (ServerPlayerEntity viewer : world.getPlayers(player -> player.squaredDistanceTo(swordPos) <= VIEW_DISTANCE_SQ)) {
            world.playSound(viewer, ritual.site().tablePos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
                SoundCategory.PLAYERS, 0.65F, 1.3F);
        }

        if (cfg.giveItemToExecutor) {
            ServerPlayerEntity executor = world.getServer().getPlayerManager().getPlayer(ritual.executorUuid());
            if (executor != null) {
                executor.giveItemStack(new ItemStack(Items.NETHERITE_SWORD));
            }
            ritual.sword().discard();
        } else {
            ritual.sword().setNoGravity(false);
            ritual.sword().setPickupDelay(40);
            ritual.sword().setInvulnerable(false);
        }
    }

    private static void restoreDust(ServerWorld world, ActiveRitual ritual) {
        for (Map.Entry<BlockPos, BlockState> entry : ritual.originalDustStates().entrySet()) {
            world.setBlockState(entry.getKey(), entry.getValue(), 3);
        }
    }

    private static ItemEntity spawnSword(ServerWorld world, BlockPos tablePos) {
        ItemEntity entity = new ItemEntity(world,
            tablePos.getX() + 0.5,
            tablePos.getY() + 1.1,
            tablePos.getZ() + 0.5,
            new ItemStack(Items.NETHERITE_SWORD));
        entity.setNoGravity(true);
        entity.setInvulnerable(true);
        entity.setPickupDelay(32767);
        world.spawnEntity(entity);
        return entity;
    }

    private static BlockPos findClosestChest(ServerWorld world, BlockPos center, int radius) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.iterateOutwards(center, radius, radius, radius)) {
            BlockState state = world.getBlockState(pos);
            if (!(state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST))) {
                continue;
            }

            double dist = pos.getSquaredDistance(center);
            if (dist < bestDist) {
                bestDist = dist;
                best = pos.toImmutable();
            }
        }

        return best;
    }

    private static void sendParticleToNearby(ServerWorld world, ParticleEffect effect, Vec3d position, Vec3d velocity) {
        ParticleS2CPacket packet = new ParticleS2CPacket(effect, true, false,
            position.x, position.y, position.z,
            (float) velocity.x, (float) velocity.y, (float) velocity.z,
            1.0F, 0);

        for (ServerPlayerEntity player : world.getPlayers(p -> p.squaredDistanceTo(position) <= VIEW_DISTANCE_SQ)) {
            player.networkHandler.sendPacket(packet);
        }
    }

    private record RitualKey(RegistryKey<net.minecraft.world.World> worldKey, BlockPos tablePos) {
        static RitualKey of(ServerWorld world, BlockPos tablePos) {
            return new RitualKey(world.getRegistryKey(), tablePos.toImmutable());
        }
    }

    private static final class ActiveRitual {
        private final RitualKey key;
        private final RitualSite site;
        private final UUID executorUuid;
        private final ItemEntity sword;
        private final Map<BlockPos, BlockState> originalDustStates;
        private final BlockPos chestPos;
        private final long originalTime;
        private final int participantCount;
        private final int particleCap;
        private final int liftTicks;
        private final boolean shouldRestoreTime;
        private int age;
        private boolean restorePhase;

        private ActiveRitual(
            RitualKey key,
            RitualSite site,
            UUID executorUuid,
            ItemEntity sword,
            Map<BlockPos, BlockState> originalDustStates,
            BlockPos chestPos,
            long originalTime,
            int participantCount,
            int particleCap,
            int liftTicks,
            int age,
            boolean shouldRestoreTime
        ) {
            this.key = key;
            this.site = site;
            this.executorUuid = executorUuid;
            this.sword = sword;
            this.originalDustStates = originalDustStates;
            this.chestPos = chestPos;
            this.originalTime = originalTime;
            this.participantCount = participantCount;
            this.particleCap = particleCap;
            this.liftTicks = liftTicks;
            this.age = age;
            this.shouldRestoreTime = shouldRestoreTime;
            this.restorePhase = false;
        }

        public RitualKey key() {
            return key;
        }

        public RitualSite site() {
            return site;
        }

        public UUID executorUuid() {
            return executorUuid;
        }

        public ItemEntity sword() {
            return sword;
        }

        public Map<BlockPos, BlockState> originalDustStates() {
            return originalDustStates;
        }

        public BlockPos chestPos() {
            return chestPos;
        }

        public long originalTime() {
            return originalTime;
        }

        public int participantCount() {
            return participantCount;
        }

        public int particleCap() {
            return particleCap;
        }

        public int liftTicks() {
            return liftTicks;
        }

        public boolean shouldRestoreTime() {
            return shouldRestoreTime;
        }

        public int age() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public void incrementAge() {
            this.age++;
        }

        public boolean restorePhase() {
            return restorePhase;
        }

        public void setRestorePhase(boolean restorePhase) {
            this.restorePhase = restorePhase;
        }
    }
}
