package wips.powers.ice;

import com.awakenedmod.awakened.core.AimUtil;
import com.awakenedmod.awakened.core.Fx;
import com.awakenedmod.awakened.core.PowerService;
import com.awakenedmod.awakened.core.power.Ability;
import com.awakenedmod.awakened.core.power.Power;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import wips.powers.WipsPowers;
import wips.powers.kit.PowerKit;
import wips.powers.kit.TempBlocks;
import wips.powers.level.WipsScaling;

/**
 * Poder HIELO. Pasiva: congelacion acumulativa (Frost). Habilidades:
 *  1 Punos de hielo (toggle, sin energia) - 2 Ventisca - 3 Pisadas de hielo (toggle) - 4 Tempanos teledirigidos
 *  5 Congelacion territorial.
 */
@EventBusSubscriber(modid = WipsPowers.MOD_ID)
public final class IcePower extends Power {
    public static final String ID = "ice";

    public IcePower() {
        super(ID, List.<Ability>of(new IceFists(), new Blizzard(), new IceSteps(), new IceShards(), new FrozenGround()));
    }

    @Override
    public int themeColor() {
        return 0xFF000000 | Frost.ICE;
    }

    @Override
    public int signatureSlot() {
        return 0;
    }

    abstract static class IceAbility extends Ability {
        IceAbility(String id, float cost, int unlock) {
            super(id, cost, unlock);
        }

        @Override
        public Component displayName() {
            return Component.translatable("ability.wipspowers." + this.id());
        }

        @Override
        public Component description() {
            return Component.translatable("ability.wipspowers." + this.id() + ".desc");
        }
    }

    // =====================================================================================================
    // 1. PUNOS DE HIELO: activable/desactivable libremente, sin energia. Los golpes cuerpo a cuerpo hacen dano de
    //    frio extra, ralentizan y suman cargas de la pasiva.
    // =====================================================================================================
    public static final class IceFists extends IceAbility {
        static final Set<UUID> ON = new HashSet<>();
        public static final float BONUS = 2.0F;

        public IceFists() {
            super("ice_fists", 0.0F, 0);
        }

        @Override
        public boolean activate(ServerPlayer player) {
            boolean on = !ON.remove(player.getUUID());
            if (on) {
                ON.add(player.getUUID());
            }
            player.displayClientMessage(Component.translatable(on ? "message.wipspowers.ice_fists_on" : "message.wipspowers.ice_fists_off"), true);
            Fx.sound(player.level(), player.position(), on ? SoundEvents.GLASS_PLACE : SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8F, 1.4F);
            this.startCooldown(player, 10);
            return true;
        }

        @Override
        public String activationAnimation(ServerPlayer player) {
            return "cold_snap";
        }

        public static boolean on(ServerPlayer p) {
            return ON.contains(p.getUUID()) && PowerKit.has(p, ID);
        }
    }

    /** Golpe basico (M1 real, no habilidad) con los punos activos. */
    @SubscribeEvent
    public static void onMelee(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || event.getSource().getDirectEntity() != p
                || WipsScaling.isPowerHit(event.getSource()) || !IceFists.on(p)) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (!PowerKit.enemy(p, target)) {
            return;
        }
        event.setAmount(event.getAmount() + IceFists.BONUS * WipsScaling.multiplier(p));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1), p);
        ServerLevel level = p.serverLevel();
        level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
        Frost.charge(p, target, 1);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && p.tickCount % 6 == 0) {
            if (IceFists.ON.contains(p.getUUID()) && PowerKit.has(p, ID)) {
                Vec3 hand = AimUtil.handPosition(p);
                p.serverLevel().sendParticles(ParticleTypes.SNOWFLAKE, hand.x, hand.y, hand.z, 2, 0.08, 0.08, 0.08, 0.0);
            }
        }
    }

    // =====================================================================================================
    // 2. VENTISCA: 10 s en el punto apuntado. Atrae con fuerza hacia el centro, ralentiza y pega cada 0,5 s;
    //    cada pulso suma una carga de la pasiva.
    // =====================================================================================================
    public static final class Blizzard extends IceAbility {
        public static final int DURATION = 200;
        public static final double RADIUS = 9.0;
        public static final float PULSE_DAMAGE = 1.5F;
        static final List<Zone> ZONES = new CopyOnWriteArrayList<>();

        static final class Zone {
            final ServerPlayer owner;
            final ServerLevel level;
            final Vec3 center;
            int age;

            Zone(ServerPlayer owner, Vec3 center) {
                this.owner = owner;
                this.level = owner.serverLevel();
                this.center = center;
            }
        }

        public Blizzard() {
            super("ice_blizzard", 35.0F, 2);
        }

        @Override
        public String activationAnimation(ServerPlayer player) {
            return "frost_nova";
        }

        @Override
        public boolean activate(ServerPlayer player) {
            Vec3 at = AimUtil.groundedPosition(player, 20.0, 8);
            if (at == null) {
                at = player.position().add(AimUtil.lookFlat(player).scale(8));
            }
            ZONES.removeIf(z -> z.owner == player); // una por jugador: la nueva reemplaza a la anterior
            ZONES.add(new Zone(player, at));
            Fx.sound(player.level(), at, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 1.2F, 0.5F);
            this.startCooldown(player, 300);
            return true;
        }

        @Override
        public void clearPlayer(ServerPlayer player) {
            ZONES.removeIf(z -> z.owner == player);
            super.clearPlayer(player);
        }

        static void tick(Zone z) {
            z.age++;
            Vec3 c = z.center;
            var rnd = z.level.random;
            // remolino de nieve
            for (int i = 0; i < 26; i++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                double r = rnd.nextDouble() * RADIUS;
                double y = rnd.nextDouble() * 4.5;
                double sw = 0.35;
                z.level.sendParticles(ParticleTypes.SNOWFLAKE, c.x + Math.cos(a) * r, c.y + y, c.z + Math.sin(a) * r, 0,
                        -Math.sin(a) * sw - Math.cos(a) * 0.08, -0.02, Math.cos(a) * sw - Math.sin(a) * 0.08, 1.0);
            }
            if (z.age % 4 == 0) {
                z.level.sendParticles(ParticleTypes.WHITE_ASH, c.x, c.y + 1.5, c.z, 30, RADIUS * 0.5, 1.5, RADIUS * 0.5, 0.02);
            }
            if (z.age % 40 == 0) {
                Fx.sound(z.level, c, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.9F, 0.4F);
            }
            boolean pulse = z.age % 10 == 0;
            for (LivingEntity e : PowerKit.enemies(z.owner, z.level, c.add(0, 1, 0), RADIUS + 1.5, 40)) {
                if (Frost.isFrozen(e)) {
                    continue;
                }
                Vec3 to = new Vec3(c.x - e.getX(), 0, c.z - e.getZ());
                double d = to.length();
                if (d > 0.6) {
                    Vec3 pull = to.scale(0.16 / d);
                    e.setDeltaMovement(e.getDeltaMovement().multiply(0.6, 1.0, 0.6).add(pull.x, 0.0, pull.z));
                    e.hurtMarked = true;
                }
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 1, false, false), z.owner);
                if (pulse) {
                    e.invulnerableTime = 0;
                    if (PowerKit.hit(z.owner, e, PULSE_DAMAGE)) {
                        Frost.charge(z.owner, e, 1);
                    }
                }
            }
        }
    }

    // =====================================================================================================
    // 3. PISADAS DE HIELO: activable/desactivable. Saltar en el aire crea una plataforma de hielo de 3x3 bajo los
    //    pies (dura 4 s) y te impulsa: saltos consecutivos suben como escalones. El salto en el aire lo detecta el
    //    cliente (tecla de salto sin tocar el suelo) y lo valida el servidor.
    // =====================================================================================================
    public static final class IceSteps extends IceAbility {
        static final Set<UUID> ON = new HashSet<>();
        static final Map<UUID, Long> LAST = new HashMap<>();
        static IceSteps INSTANCE;
        public static final int PLATFORM_TICKS = 80;
        /** Autotest. */
        public static int platforms;

        public IceSteps() {
            super("ice_steps", 0.0F, 5);
            INSTANCE = this;
        }

        @Override
        public boolean activate(ServerPlayer player) {
            boolean on = !ON.remove(player.getUUID());
            if (on) {
                ON.add(player.getUUID());
            }
            PacketDistributor.sendToPlayer(player, new State(on));
            player.displayClientMessage(Component.translatable(on ? "message.wipspowers.ice_steps_on" : "message.wipspowers.ice_steps_off"), true);
            this.startCooldown(player, 10);
            return true;
        }

        @Override
        public String activationAnimation(ServerPlayer player) {
            return null;
        }

        /** El cliente avisa que salto en el aire. */
        public static void onAirJump(ServerPlayer p) {
            if (!ON.contains(p.getUUID()) || !PowerKit.has(p, ID) || p.onGround() || p.isInWater() || p.isFallFlying()
                    || p.getAbilities().flying || p.isPassenger() || INSTANCE == null) {
                return;
            }
            long now = p.serverLevel().getGameTime();
            Long last = LAST.get(p.getUUID());
            if (last != null && now - last < 5) {
                return;
            }
            if (!PowerService.charge(p, INSTANCE, 2.0F)) {
                return;
            }
            LAST.put(p.getUUID(), now);
            ServerLevel level = p.serverLevel();
            int y = Mth.floor(p.getY() - 0.05) - 1;
            int cx = p.getBlockX();
            int cz = p.getBlockZ();
            AABB body = p.getBoundingBox();
            int placed = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = new BlockPos(cx + dx, y, cz + dz);
                    if (new AABB(pos).intersects(body)) {
                        continue; // nunca dentro del jugador
                    }
                    if (TempBlocks.placeInAir(level, pos, Blocks.PACKED_ICE.defaultBlockState(), PLATFORM_TICKS)) {
                        placed++;
                    }
                }
            }
            if (placed > 0) {
                platforms++;
            }
            p.setDeltaMovement(p.getDeltaMovement().x, 0.55, p.getDeltaMovement().z);
            p.hurtMarked = true;
            p.resetFallDistance();
            level.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), y + 1.0, p.getZ(), 16, 0.8, 0.05, 0.8, 0.02);
            Fx.sound(level, p.position(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 0.7F, 1.6F);
        }

        /** cliente -> servidor: salto en el aire. */
        public record Jump() implements CustomPacketPayload {
            public static final Jump INSTANCE_PAYLOAD = new Jump();
            public static final Type<Jump> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WipsPowers.MOD_ID, "ice_jump"));
            public static final StreamCodec<ByteBuf, Jump> CODEC = StreamCodec.unit(INSTANCE_PAYLOAD);

            @Override
            public Type<? extends CustomPacketPayload> type() {
                return TYPE;
            }
        }

        /** servidor -> cliente: si las pisadas estan activas (para escuchar la tecla de salto). */
        public record State(boolean on) implements CustomPacketPayload {
            public static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WipsPowers.MOD_ID, "ice_steps"));
            public static final StreamCodec<ByteBuf, State> CODEC = ByteBufCodecs.BOOL.map(State::new, State::on);

            @Override
            public Type<? extends CustomPacketPayload> type() {
                return TYPE;
            }
        }
    }

    // =====================================================================================================
    // 4. TEMPANOS TELEDIRIGIDOS: hasta 3 orbitan al jugador; cuando hay un enemigo valido cerca salen disparados,
    //    lo persiguen y al impactar hacen dano y suman cargas. Ver IceShardEntity.
    // =====================================================================================================
    public static final class IceShards extends IceAbility {
        public static final int MAX = 3;

        public IceShards() {
            super("ice_shards", 30.0F, 8);
        }

        @Override
        public String activationAnimation(ServerPlayer player) {
            return "glacial_rampart";
        }

        @Override
        public boolean activate(ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            List<IceShardEntity> mine = IceShardEntity.owned(player);
            int add = MAX - mine.size();
            if (add <= 0) {
                return this.fail(player, "message.wipspowers.ice_shards_full");
            }
            boolean[] used = new boolean[MAX];
            for (IceShardEntity s : mine) {
                used[Math.floorMod(s.slot(), MAX)] = true;
            }
            for (int slot = 0; slot < MAX && add > 0; slot++) {
                if (!used[slot]) {
                    IceShardEntity.spawn(level, player, slot);
                    add--;
                }
            }
            Fx.sound(level, player.position(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 1.5F);
            this.startCooldown(player, 160);
            return true;
        }

        @Override
        public void clearPlayer(ServerPlayer player) {
            IceShardEntity.owned(player).forEach(IceShardEntity::discard);
            super.clearPlayer(player);
        }
    }

    // =====================================================================================================
    // 5. CONGELACION TERRITORIAL: zona circular de hielo de radio 14 por 10 s. Todo enemigo que entre (tambien
    //    despues de creada) queda congelado 5 s, sin pasar por la pasiva. No afecta al jugador ni a sus mascotas.
    // =====================================================================================================
    public static final class FrozenGround extends IceAbility {
        public static final double RADIUS = 14.0;
        public static final int DURATION = 200;
        public static final int FREEZE = 100;
        static final List<Field> FIELDS = new CopyOnWriteArrayList<>();

        static final class Field {
            final ServerPlayer owner;
            final ServerLevel level;
            final Vec3 center;
            final Map<UUID, Long> lastFrozen = new HashMap<>();
            int age;

            Field(ServerPlayer owner) {
                this.owner = owner;
                this.level = owner.serverLevel();
                this.center = owner.position();
            }
        }

        public FrozenGround() {
            super("ice_frozen_ground", 70.0F, 10);
        }

        @Override
        public String activationAnimation(ServerPlayer player) {
            return "frozen_solid";
        }

        @Override
        public boolean activate(ServerPlayer player) {
            Field f = new Field(player);
            FIELDS.removeIf(x -> x.owner == player);
            FIELDS.add(f);
            ServerLevel level = f.level;
            // suelo de hielo (temporal): la capa de arriba de cada columna del circulo
            int r = Mth.ceil(RADIUS);
            BlockPos o = player.blockPosition();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > RADIUS * RADIUS) {
                        continue;
                    }
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, o.getX() + dx, o.getZ() + dz) - 1;
                    if (Math.abs(top - (o.getY() - 1)) <= 4) {
                        TempBlocks.swap(level, new BlockPos(o.getX() + dx, top, o.getZ() + dz), Blocks.PACKED_ICE.defaultBlockState(), DURATION);
                    }
                }
            }
            Fx.sound(level, f.center, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2.0F, 0.5F);
            Fx.sound(level, f.center, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 2.0F, 0.6F);
            Fx.shake(level, f.center, 24.0, 0.2F, 6, 10);
            this.startCooldown(player, 600);
            return true;
        }

        @Override
        public void clearPlayer(ServerPlayer player) {
            FIELDS.removeIf(x -> x.owner == player);
            super.clearPlayer(player);
        }

        static void tick(Field f) {
            f.age++;
            long now = f.level.getGameTime();
            if (f.age % 3 == 0) {
                var rnd = f.level.random;
                for (int i = 0; i < 24; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2;
                    double rr = Math.sqrt(rnd.nextDouble()) * RADIUS;
                    f.level.sendParticles(ParticleTypes.SNOWFLAKE, f.center.x + Math.cos(a) * rr, f.center.y + 0.2, f.center.z + Math.sin(a) * rr,
                            1, 0.0, 0.05, 0.0, 0.0);
                }
                for (int i = 0; i < 48; i++) {
                    double a = i * (Math.PI * 2 / 48);
                    f.level.sendParticles(Fx.dust(Frost.ICE, 1.4F), f.center.x + Math.cos(a) * RADIUS, f.center.y + 0.3,
                            f.center.z + Math.sin(a) * RADIUS, 1, 0, 0.05, 0, 0);
                }
            }
            if (f.age % 2 != 0) {
                return;
            }
            for (LivingEntity e : PowerKit.enemies(f.owner, f.level, f.center, RADIUS + 4, 60)) {
                double dx = e.getX() - f.center.x;
                double dz = e.getZ() - f.center.z;
                if (dx * dx + dz * dz > RADIUS * RADIUS || Math.abs(e.getY() - f.center.y) > 5 || Frost.isFrozen(e)) {
                    continue;
                }
                Long last = f.lastFrozen.get(e.getUUID());
                if (last != null && now - last < FREEZE + 30) {
                    continue; // recien descongelado: 1,5 s de margen antes de volver a congelarlo
                }
                f.lastFrozen.put(e.getUUID(), now);
                Frost.freeze(f.owner, e, FREEZE);
                PowerKit.hit(f.owner, e, 3.0F);
            }
        }
    }

    // ---------------- tick global de zonas (no depende de que el jugador siga con el poder) ----------------

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        List<Object> done = new ArrayList<>();
        for (Blizzard.Zone z : Blizzard.ZONES) {
            if (z.owner.isRemoved() || z.age >= Blizzard.DURATION) {
                done.add(z);
            } else {
                Blizzard.tick(z);
            }
        }
        Blizzard.ZONES.removeAll(done);
        done.clear();
        for (FrozenGround.Field f : FrozenGround.FIELDS) {
            if (f.owner.isRemoved() || f.age >= FrozenGround.DURATION) {
                done.add(f);
            } else {
                FrozenGround.tick(f);
            }
        }
        FrozenGround.FIELDS.removeAll(done);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        IceFists.ON.remove(id);
        IceSteps.ON.remove(id);
        IceSteps.LAST.remove(id);
        Blizzard.ZONES.removeIf(z -> z.owner.getUUID().equals(id));
        FrozenGround.FIELDS.removeIf(f -> f.owner.getUUID().equals(id));
    }
}
