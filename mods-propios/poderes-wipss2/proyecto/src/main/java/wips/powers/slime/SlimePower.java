package wips.powers.slime;

import com.awakenedmod.awakened.core.AimUtil;
import com.awakenedmod.awakened.core.Fx;
import com.awakenedmod.awakened.core.power.Ability;
import com.awakenedmod.awakened.core.power.Power;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.particles.ItemParticleOption;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import wips.powers.WipsPowers;
import wips.powers.kit.PowerKit;
import wips.powers.kit.Stun;

/**
 * Poder SLIME. Pasiva: baba curativa. Habilidades: 1 Mini slimes - 2 Recubrimiento curativo - 3 Disparo de baba
 * 4 Explosion de baba - 5 Pelota de slime.
 */
@EventBusSubscriber(modid = WipsPowers.MOD_ID)
public final class SlimePower extends Power {
    public static final String ID = "slime";
    public static final int GREEN = 0x7CCB5B;
    static final ItemStack BALL = new ItemStack(Items.SLIME_BALL);

    public SlimePower() {
        super(ID, List.<Ability>of(new Minions(), new Coating(), new Volley(), new Burst(), new BallForm()));
    }

    @Override
    public int themeColor() {
        return 0xFF000000 | GREEN;
    }

    @Override
    public int signatureSlot() {
        return 0;
    }

    abstract static class SlimeAbility extends Ability {
        SlimeAbility(String id, float cost, int unlock) {
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

    static ItemParticleOption goo() {
        return new ItemParticleOption(ParticleTypes.ITEM, BALL);
    }

    // =====================================================================================================
    // PASIVA: cada golpe a un enemigo (basico o de habilidad, como mucho uno por tick) suma; a los 3 cae una gota de
    // baba recogible que cura un % de la vida maxima. Sin cooldown; como mucho 3 gotas sueltas por jugador.
    // =====================================================================================================
    public static final class Passive {
        static final Map<UUID, int[]> COUNT = new HashMap<>(); // [golpes, ultimo tick]
        public static final int HITS = 3;
        public static final int MAX_GLOBS = 3;

        public static void onHit(ServerPlayer owner, LivingEntity target) {
            if (!PowerKit.has(owner, ID)) {
                return;
            }
            int now = owner.server.getTickCount();
            int[] c = COUNT.computeIfAbsent(owner.getUUID(), k -> new int[]{0, -1});
            if (c[1] == now) {
                return; // un golpe en area a varios enemigos cuenta una vez
            }
            c[1] = now;
            if (++c[0] < HITS) {
                return;
            }
            c[0] = 0;
            ServerLevel level = owner.serverLevel();
            List<SlimeGlobEntity> mine = new ArrayList<>();
            for (SlimeGlobEntity g : level.getEntitiesOfClass(SlimeGlobEntity.class, owner.getBoundingBox().inflate(48))) {
                if (owner.getUUID().equals(g.ownerId())) {
                    mine.add(g);
                }
            }
            if (mine.size() >= MAX_GLOBS) {
                mine.sort((a, b) -> Integer.compare(b.tickCount, a.tickCount));
                mine.get(0).discard(); // la mas vieja deja lugar
            }
            SlimeGlobEntity.spawn(level, owner, target.position());
            Fx.sound(level, target.position(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.8F, 1.3F);
        }
    }

    /** Golpe basico del jugador con el poder: tambien cuenta para la pasiva. */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (event.getSource().getEntity() instanceof ServerPlayer p && event.getNewDamage() > 0 && event.getEntity() != p
                && PowerKit.enemy(p, event.getEntity())) {
            Passive.onHit(p, event.getEntity());
        }
    }

    // ---- ralentizacion del 70 % (modificador propio: se renueva, no se acumula) ----
    public static final class Slow {
        static final ResourceLocation ID_MOD = ResourceLocation.fromNamespaceAndPath(WipsPowers.MOD_ID, "slime_slow");
        static final Map<UUID, Object[]> UNTIL = new HashMap<>(); // entidad, tick

        public static void apply(LivingEntity e, int ticks) {
            AttributeInstance speed = e.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed == null) {
                return;
            }
            if (!speed.hasModifier(ID_MOD)) {
                speed.addTransientModifier(new AttributeModifier(ID_MOD, -0.70, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            UNTIL.put(e.getUUID(), new Object[]{e, e.level().getGameTime() + ticks});
        }

        public static boolean slowed(LivingEntity e) {
            AttributeInstance speed = e.getAttribute(Attributes.MOVEMENT_SPEED);
            return speed != null && speed.hasModifier(ID_MOD);
        }

        static void tick() {
            Iterator<Object[]> it = UNTIL.values().iterator();
            while (it.hasNext()) {
                Object[] o = it.next();
                LivingEntity e = (LivingEntity) o[0];
                if (e.isRemoved() || !e.isAlive() || e.level().getGameTime() >= (long) o[1]) {
                    AttributeInstance speed = e.getAttribute(Attributes.MOVEMENT_SPEED);
                    if (speed != null) {
                        speed.removeModifier(ID_MOD);
                    }
                    it.remove();
                } else if (e.tickCount % 6 == 0 && e.level() instanceof ServerLevel sl) {
                    sl.sendParticles(goo(), e.getX(), e.getY() + 0.2, e.getZ(), 1, 0.2, 0.05, 0.2, 0.0);
                }
            }
        }
    }

    // =====================================================================================================
    // 1. MINI SLIMES: invoca 3 aliados (maximo 3). Si ya estan, pulsar de nuevo cambia su objetivo al que miras.
    // =====================================================================================================
    public static final class Minions extends SlimeAbility {
        static final Map<UUID, LivingEntity> CHOSEN = new HashMap<>();

        public Minions() {
            super("slime_minions", 30.0F, 0);
        }

        @Override
        public String activationAnimation(ServerPlayer player) {
            return "summon";
        }

        @Override
        public boolean activate(ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            List<MiniSlimeEntity> mine = MiniSlimeEntity.owned(player);
            LivingEntity aimed = AimUtil.aimedLivingEntity(player, 32.0, e -> PowerKit.enemy(player, e));
            if (aimed != null) {
                CHOSEN.put(player.getUUID(), aimed);
                for (MiniSlimeEntity m : mine) {
                    m.setTarget(aimed);
                }
            }
            if (mine.size() >= 3) {
                if (aimed == null) {
                    return this.fail(player, "message.wipspowers.slime_no_target");
                }
                player.displayClientMessage(Component.translatable("message.wipspowers.slime_retarget"), true);
                Fx.sound(level, player.position(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0F, 1.5F);
                return true; // solo cambia el objetivo: no crea mas
            }
            Vec3 side = AimUtil.lookFlat(player);
            for (int i = mine.size(); i < 3; i++) {
                double a = (i - 1) * 0.9;
                Vec3 at = player.position().add(side.yRot((float) a).scale(1.8));
                MiniSlimeEntity m = MiniSlimeEntity.spawn(level, player, at.x, at.y + 0.2, at.z);
                if (aimed != null) {
                    m.setTarget(aimed);
                }
                level.sendParticles(goo(), at.x, at.y + 0.4, at.z, 10, 0.3, 0.3, 0.3, 0.05);
            }
            Fx.sound(level, player.position(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.2F, 0.9F);
            this.startCooldown(player, 40);
            return true;
        }

        @Override
        public void clearPlayer(ServerPlayer player) {
            CHOSEN.remove(player.getUUID());
            super.clearPlayer(player);
        }
    }

    static LivingEntity chosenTarget(ServerPlayer owner) {
        LivingEntity t = Minions.CHOSEN.get(owner.getUUID());
        if (t != null && (!t.isAlive() || t.isRemoved() || t.level() != owner.level())) {
            Minions.CHOSEN.remove(owner.getUUID());
            return null;
        }
        return t;
    }

    // =====================================================================================================
    // 2. RECUBRIMIENTO CURATIVO: te cubres de baba y recuperas el 25 % de la vida maxima (sin pasarte del maximo).
    // =====================================================================================================
    public static final class Coating extends SlimeAbility {
        public static final float HEAL = 0.25F;

        public Coating() {
            super("slime_coating", 30.0F, 2);
        }

        @Override
        public String activationAnimation(ServerPlayer player) {
            return "life_graft";
        }

        @Override
        public boolean activate(ServerPlayer player) {
            float max = player.getMaxHealth();
            player.setHealth(Math.min(max, player.getHealth() + max * HEAL));
            ServerLevel level = player.serverLevel();
            for (int i = 0; i < 4; i++) {
                level.sendParticles(goo(), player.getX(), player.getY() + 0.3 + i * 0.5, player.getZ(), 12, 0.35, 0.15, 0.35, 0.02);
            }
            level.sendParticles(Fx.dust(GREEN, 1.6F), player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.8, 0.4, 0.0);
            level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 2.1, player.getZ(), 4, 0.4, 0.2, 0.4, 0.0);
            Fx.sound(level, player.position(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1.2F, 0.8F);
            Fx.sound(level, player.position(), SoundEvents.HONEY_DRINK, SoundSource.PLAYERS, 1.0F, 0.9F);
            this.startCooldown(player, 400);
            return true;
        }
    }

    // =====================================================================================================
    // 3. DISPARO DE BABA: 4 s disparando gotas hacia donde miras (cada 4 ticks): dano y ralentizacion del 70 %.
    // =====================================================================================================
    public static final class Volley extends SlimeAbility {
        public static final int DURATION = 80;
        private final Map<UUID, int[]> firing = this.activeState();

        public Volley() {
            super("slime_volley", 30.0F, 5);
        }

        @Override
        public String activationAnimation(ServerPlayer player) {
            return null;
        }

        @Override
        public boolean activate(ServerPlayer player) {
            if (this.firing.containsKey(player.getUUID())) {
                return this.failSilently(player);
            }
            this.firing.put(player.getUUID(), new int[]{0});
            this.startCooldown(player, 240);
            return true;
        }

        @Override
        public void serverTick(ServerPlayer player) {
            int[] t = this.firing.get(player.getUUID());
            if (t == null) {
                return;
            }
            if (!player.isAlive() || t[0]++ >= DURATION) {
                this.firing.remove(player.getUUID());
                return;
            }
            if (t[0] % 4 == 1) {
                SlimeShotEntity.fire(player.serverLevel(), player);
                Fx.sound(player.level(), player.position(), SoundEvents.SLIME_JUMP_SMALL, SoundSource.PLAYERS, 0.8F, 1.6F);
            }
        }

        @Override
        public boolean occupiesHands(ServerPlayer player) {
            return false;
        }
    }

    // =====================================================================================================
    // 4. EXPLOSION DE BABA: estallido circular con dano en area donde apuntas; despues caen gotas de baba en varios
    //    puntos del radio que quedan en el suelo un tiempo. Solo quien toca un charco queda pegado y recibe dano.
    // =====================================================================================================
    public static final class Burst extends SlimeAbility {
        public static final double RADIUS = 6.0;
        public static final float DAMAGE = 7.0F;
        public static final int PUDDLES = 8;
        /** Tiempo que dura cada charco en el suelo (configurable en este valor). */
        public static int puddleTicks = 160;
        public static final double PUDDLE_RADIUS = 1.4;
        public static final float PUDDLE_DOT = 1.0F;
        static final List<Puddle> PUDDLES_ALIVE = new CopyOnWriteArrayList<>();

        static final class Puddle {
            final ServerPlayer owner;
            final ServerLevel level;
            final Vec3 at;
            final int delay;
            int age;

            Puddle(ServerPlayer owner, Vec3 at, int delay) {
                this.owner = owner;
                this.level = owner.serverLevel();
                this.at = at;
                this.delay = delay;
            }
        }

        public Burst() {
            super("slime_burst", 45.0F, 8);
        }

        @Override
        public String activationAnimation(ServerPlayer player) {
            return "cluster_burst";
        }

        @Override
        public boolean activate(ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            Vec3 c = AimUtil.groundedPosition(player, 16.0, 8);
            if (c == null) {
                c = player.position();
            }
            for (LivingEntity e : PowerKit.enemies(player, level, c.add(0, 1, 0), RADIUS, 40)) {
                if (PowerKit.hit(player, e, DAMAGE)) {
                    Vec3 away = e.position().subtract(c).normalize();
                    e.push(away.x * 0.6, 0.35, away.z * 0.6);
                    e.hurtMarked = true;
                }
            }
            level.sendParticles(goo(), c.x, c.y + 0.6, c.z, 120, RADIUS * 0.4, 0.6, RADIUS * 0.4, 0.25);
            level.sendParticles(Fx.dust(GREEN, 2.2F), c.x, c.y + 0.5, c.z, 80, RADIUS * 0.45, 0.5, RADIUS * 0.45, 0.0);
            level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.8, c.z, 2, 0.5, 0.2, 0.5, 0.0);
            Fx.sound(level, c, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 2.0F, 0.5F);
            Fx.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0F, 1.4F);
            var rnd = level.random;
            for (int i = 0; i < PUDDLES; i++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                double r = 1.0 + rnd.nextDouble() * (RADIUS - 1.0);
                Vec3 p = AimUtil.groundedPosition(level, c.add(Math.cos(a) * r, 2.0, Math.sin(a) * r));
                PUDDLES_ALIVE.add(new Puddle(player, p, 8 + rnd.nextInt(12)));
            }
            this.startCooldown(player, 300);
            return true;
        }

        static boolean tick(Puddle p) {
            p.age++;
            if (p.age < p.delay) {
                // la gota cae
                double y = p.at.y + 5.0 * (1.0 - p.age / (double) p.delay);
                p.level.sendParticles(goo(), p.at.x, y, p.at.z, 2, 0.05, 0.05, 0.05, 0.0);
                return true;
            }
            if (p.age == p.delay) {
                p.level.playSound(null, p.at.x, p.at.y, p.at.z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 1.0F, 0.8F);
            }
            if (p.age > p.delay + puddleTicks || p.owner.isRemoved()) {
                return false;
            }
            if (p.age % 4 == 0) {
                for (int i = 0; i < 10; i++) {
                    double a = i * (Math.PI * 2 / 10) + p.age * 0.1;
                    double r = PUDDLE_RADIUS * (0.4 + 0.6 * ((i % 2) + 1) / 2.0);
                    p.level.sendParticles(Fx.dust(GREEN, 1.3F), p.at.x + Math.cos(a) * r, p.at.y + 0.08, p.at.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
                p.level.sendParticles(goo(), p.at.x, p.at.y + 0.1, p.at.z, 2, PUDDLE_RADIUS * 0.4, 0.02, PUDDLE_RADIUS * 0.4, 0.0);
            }
            AABB box = new AABB(p.at.x - PUDDLE_RADIUS, p.at.y - 0.5, p.at.z - PUDDLE_RADIUS, p.at.x + PUDDLE_RADIUS, p.at.y + 0.6,
                    p.at.z + PUDDLE_RADIUS);
            for (LivingEntity e : p.level.getEntitiesOfClass(LivingEntity.class, box, e -> PowerKit.enemy(p.owner, e))) {
                double dx = e.getX() - p.at.x;
                double dz = e.getZ() - p.at.z;
                if (dx * dx + dz * dz > PUDDLE_RADIUS * PUDDLE_RADIUS) {
                    continue;
                }
                Stun.apply(p.owner, e, 12, true); // pegado al suelo mientras siga encima (se renueva)
                if (p.age % 10 == 0) {
                    e.invulnerableTime = 0;
                    PowerKit.hit(p.owner, e, PUDDLE_DOT);
                }
            }
            return true;
        }
    }

    // =====================================================================================================
    // 5. PELOTA DE SLIME: 15 s exactos convertido en una gran pelota. Fisica elastica del lado del jugador (rebota en
    //    el suelo y en paredes, conserva impulso), sin dano de caida. Al pasar sobre un enemigo: dano y aturdimiento
    //    de 2 s. Al terminar (o al morir, desconectarse o cambiar de dimension) todo vuelve a la normalidad.
    // =====================================================================================================
    public static final class BallForm extends SlimeAbility {
        public static final int DURATION = 300;
        public static final float CONTACT_DAMAGE = 6.0F;
        public static final int STUN = 40;
        static final ResourceLocation SPEED = ResourceLocation.fromNamespaceAndPath(WipsPowers.MOD_ID, "slime_ball_speed");
        static final Map<UUID, Ball> BALLS = new HashMap<>();
        /** Autotest. */
        public static int contacts;

        static final class Ball {
            final ServerPlayer player;
            int age;
            final Map<UUID, Long> grace = new HashMap<>();

            Ball(ServerPlayer p) {
                this.player = p;
            }
        }

        public BallForm() {
            super("slime_ball", 60.0F, 10);
        }

        @Override
        public String activationAnimation(ServerPlayer player) {
            return null;
        }

        public static boolean active(ServerPlayer p) {
            return BALLS.containsKey(p.getUUID());
        }

        @Override
        public boolean activate(ServerPlayer player) {
            if (BALLS.containsKey(player.getUUID())) {
                return this.failSilently(player);
            }
            BALLS.put(player.getUUID(), new Ball(player));
            AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null && !speed.hasModifier(SPEED)) {
                speed.addTransientModifier(new AttributeModifier(SPEED, 0.6, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new Form(player.getId(), true));
            ServerLevel level = player.serverLevel();
            level.sendParticles(goo(), player.getX(), player.getY() + 1, player.getZ(), 60, 0.6, 0.6, 0.6, 0.2);
            Fx.sound(level, player.position(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1.5F, 0.6F);
            this.startCooldown(player, 600);
            return true;
        }

        static void end(ServerPlayer p) {
            if (BALLS.remove(p.getUUID()) == null) {
                return;
            }
            AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) {
                speed.removeModifier(SPEED);
            }
            p.resetFallDistance();
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new Form(p.getId(), false));
            if (!p.isRemoved()) {
                p.serverLevel().sendParticles(goo(), p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 0.6, 0.5, 0.15);
                Fx.sound(p.level(), p.position(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.2F, 1.0F);
            }
        }

        @Override
        public void clearPlayer(ServerPlayer player) {
            end(player);
            super.clearPlayer(player);
        }

        static void tick(Ball b) {
            ServerPlayer p = b.player;
            b.age++;
            p.resetFallDistance();
            ServerLevel level = p.serverLevel();
            if (b.age % 3 == 0) {
                level.sendParticles(goo(), p.getX(), p.getY() + 0.2, p.getZ(), 2, 0.6, 0.1, 0.6, 0.0);
            }
            long now = level.getGameTime();
            AABB reach = p.getBoundingBox().inflate(0.9, 0.4, 0.9);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, reach, e -> PowerKit.enemy(p, e))) {
                Long g = b.grace.get(e.getUUID());
                if (g != null && now - g < 20) {
                    continue; // 1 s de gracia por objetivo
                }
                b.grace.put(e.getUUID(), now);
                e.invulnerableTime = 0;
                if (PowerKit.hit(p, e, CONTACT_DAMAGE)) {
                    contacts++;
                    Stun.apply(p, e, STUN, true);
                    level.sendParticles(goo(), e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 16, 0.3, 0.3, 0.3, 0.15);
                    Fx.sound(level, e.position(), SoundEvents.SLIME_ATTACK, SoundSource.PLAYERS, 1.3F, 0.7F);
                }
            }
        }

        /** servidor -> clientes: la forma de pelota de un jugador (para el render y la fisica del propio jugador). */
        public record Form(int entity, boolean on) implements CustomPacketPayload {
            public static final Type<Form> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WipsPowers.MOD_ID, "slime_ball"));
            public static final StreamCodec<ByteBuf, Form> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Form::entity, ByteBufCodecs.BOOL,
                    Form::on, Form::new);

            @Override
            public Type<? extends CustomPacketPayload> type() {
                return TYPE;
            }
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && BallForm.active(p)) {
            event.setCanceled(true);
        }
    }

    // ---------------- tick global ----------------

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Slow.tick();
        List<Burst.Puddle> dead = new ArrayList<>();
        for (Burst.Puddle p : Burst.PUDDLES_ALIVE) {
            if (!Burst.tick(p)) {
                dead.add(p);
            }
        }
        Burst.PUDDLES_ALIVE.removeAll(dead);
        if (!BallForm.BALLS.isEmpty()) {
            for (BallForm.Ball b : new ArrayList<>(BallForm.BALLS.values())) {
                if (b.player.isRemoved() || !b.player.isAlive() || b.age >= BallForm.DURATION) {
                    BallForm.end(b.player);
                } else {
                    BallForm.tick(b);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            BallForm.end(p);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            BallForm.end(p);
            Passive.COUNT.remove(p.getUUID());
            Minions.CHOSEN.remove(p.getUUID());
        }
    }

    /** Un jugador que empieza a ver a otro recibe si esta en forma de pelota. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && BallForm.active(target) && event.getEntity() instanceof ServerPlayer viewer) {
            PacketDistributor.sendToPlayer(viewer, new BallForm.Form(target.getId(), true));
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        for (BallForm.Ball b : new ArrayList<>(BallForm.BALLS.values())) {
            BallForm.end(b.player);
        }
        Burst.PUDDLES_ALIVE.clear();
    }
}
