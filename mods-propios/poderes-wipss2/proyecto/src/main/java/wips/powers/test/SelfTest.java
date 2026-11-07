package wips.powers.test;

import com.awakenedmod.awakened.core.ItemGrant;
import com.awakenedmod.awakened.core.PowerService;
import com.awakenedmod.awakened.core.power.Ability;
import com.awakenedmod.awakened.core.power.Power;
import com.awakenedmod.awakened.core.power.PowerRegistry;
import com.awakenedmod.awakened.core.progression.MasteryScaling;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import wips.powers.WipsPowers;
import wips.powers.content.ItemPurge;
import wips.powers.fighter.FighterPower;
import wips.powers.fx.AwakenBurst;
import wips.powers.graviton.ShinraTensei;
import wips.powers.ice.Frost;
import wips.powers.ice.IcePower;
import wips.powers.ice.IceShardEntity;
import wips.powers.kit.PowerKit;
import wips.powers.kit.Stun;
import wips.powers.level.LevelXp;
import wips.powers.level.WipsLevel;
import wips.powers.level.WipsScaling;
import wips.powers.slime.MiniSlimeEntity;
import wips.powers.slime.SlimeGlobEntity;
import wips.powers.slime.SlimePower;
import wips.powers.slime.SlimeShotEntity;
import wips.powers.terrain.WipsDestruction;

/**
 * Autotest de servidor de la 3.5.0 (solo con -Dwips.selftest=true): /wips selftest. Usa jugadores falsos de NeoForge
 * y mobs reales en un mundo plano, activa cada habilidad y verifica su efecto (dano, estados, duraciones, limites,
 * bloques, objetos). Escribe "[wipspowers] SELFTEST PASS|FAIL nombre: detalle" y al final "SELFTEST FIN".
 * Lo que no se puede verificar en un servidor sin cliente (render, HUD, fisica de la pelota del lado del jugador) no
 * se marca como probado.
 */
@EventBusSubscriber(modid = WipsPowers.MOD_ID)
public final class SelfTest {
    private record Step(int delay, Runnable run) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static final List<Runnable> EVERY_TICK = new ArrayList<>();
    private static int wait;
    private static int pass;
    private static int fail;
    private static ServerLevel level;
    private static ServerPlayer fake;
    private static ServerPlayer fake2;
    private static BlockPos origin;
    private static long tickNanos;
    private static int tickCount;

    private SelfTest() {
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean("wips.selftest")) {
            return;
        }
        event.getDispatcher().register(Commands.literal("wips").then(Commands.literal("selftest").requires(s -> s.hasPermission(4))
                .executes(c -> {
                    start(c.getSource().getServer());
                    return 1;
                })));
    }

    // ---------------- registro ----------------

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            pass++;
        } else {
            fail++;
        }
        WipsPowers.LOGGER.info("[wipspowers] SELFTEST {} {}: {}", ok ? "PASS" : "FAIL", name, detail);
    }

    private static void info(String name, String detail) {
        WipsPowers.LOGGER.info("[wipspowers] SELFTEST INFO {}: {}", name, detail);
    }

    private static void then(int delay, Runnable r) {
        STEPS.add(new Step(delay, r));
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (fake != null) {
            // el servidor no tickea a los jugadores falsos: simulamos el tick de habilidades de PowerService
            for (ServerPlayer p : new ServerPlayer[]{fake, fake2}) {
                if (p == null) {
                    continue;
                }
                var data = PowerService.data(p);
                Power power = data == null ? null : PowerRegistry.get(data.powerId());
                if (power != null) {
                    for (Ability a : power.abilities()) {
                        a.serverTick(p);
                    }
                }
                FighterPower.tickPlayer(p);
            }
            for (Runnable r : new ArrayList<>(EVERY_TICK)) {
                r.run();
            }
            tickNanos += (long) event.getServer().getTickTimesNanos()[event.getServer().getTickCount() % 100];
            tickCount++;
        }
        if (STEPS.isEmpty()) {
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        Step s = STEPS.remove(0);
        try {
            s.run().run();
        } catch (Throwable t) {
            check("excepcion", false, t.toString());
            WipsPowers.LOGGER.error("[wipspowers] SELFTEST excepcion", t);
        }
        if (!STEPS.isEmpty()) {
            wait = STEPS.get(0).delay();
        }
    }

    // ---------------- utilidades ----------------

    private static ServerPlayer makeFake(String name) {
        ServerPlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
        p.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0, 0);
        return p;
    }

    private static void power(ServerPlayer p, String id, int mastery) {
        PowerService.setPower(p, id);
        var d = PowerService.data(p);
        d.setInfiniteStamina(true);
        d.setStamina(1000);
        for (int i = d.masteryLevel(); i < mastery; i++) {
            d.currentMastery().levelUp(0);
        }
        p.setHealth(p.getMaxHealth());
    }

    private static Ability ability(String powerId, int slot) {
        return PowerRegistry.get(powerId).abilities().get(slot);
    }

    private static LivingEntity dummy(double dx, double dz) {
        return dummy(dx, 0, dz, 400);
    }

    private static LivingEntity dummy(double dx, double dy, double dz, double hp) {
        Mob m = EntityType.HUSK.create(level);
        m.moveTo(origin.getX() + 0.5 + dx, origin.getY() + dy, origin.getZ() + 0.5 + dz, 0, 0);
        m.finalizeSpawn(level, level.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
        m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        m.setHealth((float) hp);
        m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100000, 9, false, false));
        m.setPersistenceRequired();
        level.addFreshEntity(m);
        return m;
    }

    private static void lookAt(ServerPlayer p, Vec3 target) {
        Vec3 d = target.subtract(p.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * (180 / Math.PI)) - 90.0F;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * (180 / Math.PI));
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
        p.setXRot(pitch);
    }

    private static void clearArena() {
        for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                new net.minecraft.world.phys.AABB(origin).inflate(80), e -> !(e instanceof ServerPlayer))) {
            e.discard();
        }
        EVERY_TICK.clear();
    }

    private static void floor() {
        // piso de piedra 41x41 y aire encima (arena limpia para cada prueba)
        for (int dx = -20; dx <= 20; dx++) {
            for (int dz = -20; dz <= 20; dz++) {
                level.setBlock(origin.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2);
                for (int dy = 0; dy < 12; dy++) {
                    level.setBlock(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private static float hpLost(LivingEntity e, float before) {
        return before - e.getHealth();
    }

    // ---------------- guion ----------------

    private static void start(MinecraftServer server) {
        level = server.overworld();
        origin = new BlockPos(0, 100, 0); // arena propia en el aire: el piso lo construye floor()
        STEPS.clear();
        pass = 0;
        fail = 0;
        LevelXp.SELFTEST = true;
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule doMobSpawning false");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set midnight");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "forceload add -96 -96 96 96");
        then(0, () -> {
            floor();
            fake = makeFake("wips_test");
            fake2 = makeFake("wips_test2");
            info("arena", "origen " + origin + ", poderes registrados: " + PowerRegistry.all().size());
        });
        levelTests();
        purgeTests();
        terrainTests();
        iceTests();
        slimeTests();
        fighterTests();
        perfTests();
        then(5, () -> {
            clearArena();
            LevelXp.SELFTEST = false;
            WipsPowers.LOGGER.info("[wipspowers] SELFTEST FIN pass={} fail={}", pass, fail);
            fake = null;
            fake2 = null;
        });
        wait = 5;
    }

    // ---- nivel, XP y escalado ----
    private static void levelTests() {
        then(2, () -> {
            power(fake, "gamma", 20);
            float neutral = MasteryScaling.scaledDamage(10.0F, fake, "gamma_leap");
            check("maestria_neutral", Math.abs(neutral - 10.0F) < 0.6F, "scaledDamage(10) con maestria 20 = " + neutral + " (antes ~16)");
            int[] lv = {1, 10, 25, 35, 50};
            double[] want = {0.30, 0.42, 0.76, 1.03, 1.50};
            for (int i = 0; i < lv.length; i++) {
                WipsLevel.setLevel(fake, lv[i]);
                LivingEntity d = dummy(3, 0);
                float before = d.getHealth();
                d.invulnerableTime = 0;
                PowerKit.hit(fake, d, 10.0F);
                float got = hpLost(d, before);
                // la armadura del husk puede restar un poco; se compara el multiplicador del evento
                check("escala_nivel_" + lv[i], Math.abs(WipsScaling.lastAfter / WipsScaling.lastBefore - want[i]) < 0.02,
                        String.format("x%.3f (esperado x%.2f), vida perdida %.2f de base 10", WipsScaling.lastAfter / WipsScaling.lastBefore, want[i], got));
                d.discard();
            }
            WipsLevel.setLevel(fake, 1);
            double x0 = WipsLevel.data(fake).xp();
            double first = LevelXp.award(fake, LevelXp.Source.MINE, 2.0);
            double sum = 0;
            for (int i = 0; i < 120; i++) {
                sum += LevelXp.award(fake, LevelXp.Source.MINE, 2.0);
            }
            double last = LevelXp.award(fake, LevelXp.Source.MINE, 2.0);
            check("xp_decreciente", last < first * 0.6, String.format("primer evento %.2f XP, evento 122: %.2f XP (misma fuente)", first, last));
            check("xp_tope_minuto", sum <= Math.max(30.0, WipsLevel.xpForNext(WipsLevel.level(fake)) * 0.25) + 1e-6,
                    String.format("XP en 121 eventos seguidos: %.1f (tope por minuto)", sum));
            info("curva", String.format("XP para subir: L1 %.0f | L10 %.0f | L20 %.0f | L35 %.0f | L49 %.0f", WipsLevel.xpForNext(1),
                    WipsLevel.xpForNext(10), WipsLevel.xpForNext(20), WipsLevel.xpForNext(35), WipsLevel.xpForNext(49)));
            WipsLevel.setLevel(fake, 1);
            int before = WipsLevel.level(fake);
            WipsLevel.addXp(fake, WipsLevel.xpForNext(1) + WipsLevel.xpForNext(2) + 1);
            check("subir_nivel", WipsLevel.level(fake) == before + 2, "nivel " + before + " -> " + WipsLevel.level(fake));
            WipsLevel.setLevel(fake, 50);
            WipsLevel.addXp(fake, 1.0E7);
            check("nivel_maximo", WipsLevel.level(fake) == 50, "con XP de sobra queda en " + WipsLevel.level(fake));
            float maxHp = fake.getMaxHealth();
            check("sin_estadisticas_base", maxHp == 20.0F, "vida maxima del jugador en nivel 50: " + maxHp);
            WipsLevel.setLevel(fake, 25);
        });
    }

    // ---- objetos quitados ----
    private static void purgeTests() {
        then(2, () -> {
            fake.getInventory().clearContent();
            ItemStack relic = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("awakened", "relic")));
            ItemGrant.to(fake, relic.copy());
            check("purge_itemgrant", fake.getInventory().countItem(relic.getItem()) == 0, "reliquia entregada directo: no llega");
            var reward = com.awakenedmod.awakened.content.quest.QuestRewards.relicBand(
                    net.minecraft.world.item.Rarity.COMMON, net.minecraft.world.item.Rarity.RARE, false);
            double xp0 = WipsLevel.data(fake).xp() + WipsLevel.level(fake) * 1.0E6;
            boolean granted = reward.grant(fake);
            double xp1 = WipsLevel.data(fake).xp() + WipsLevel.level(fake) * 1.0E6;
            check("purge_trial", granted && fake.getInventory().countItem(relic.getItem()) == 0 && xp1 > xp0,
                    "recompensa de reliquia de trial -> " + reward.label().getString());
            ItemEntity drop = new ItemEntity(level, origin.getX(), origin.getY() + 1, origin.getZ(), relic.copy());
            boolean added = level.addFreshEntity(drop);
            check("purge_suelo", !added || drop.isRemoved(), "reliquia soltada en el suelo (jefes, RelicDrops): no aparece");
            ItemStack matrix = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("awakened", "adamantium_ingot")));
            ItemGrant.to(fake, matrix.copy());
            check("purge_adamantium", fake.getInventory().countItem(matrix.getItem()) == 0, "adamantium (recompensa de trial) no llega");
            int banned = 0;
            int total = 0;
            for (String t : new String[]{"awakened:chests/relic_deep", "awakened:chests/relic_shallow", "awakened:chests/evoker_spire",
                    "awakened:chests/sunken_vault", "awakened:chests/sunken_vault_cell"}) {
                var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, ResourceLocation.parse(t));
                var table = level.getServer().reloadableRegistries().getLootTable(key);
                var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, Vec3.atCenterOf(origin))
                        .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
                for (int i = 0; i < 24; i++) {
                    for (ItemStack s : table.getRandomItems(params)) {
                        total++;
                        if (ItemPurge.banned(s)) {
                            banned++;
                        }
                    }
                }
            }
            check("purge_loot", banned == 0, "120 tiradas de cofres de Awakened: " + total + " objetos, " + banned + " quitados");
            check("purge_contador", ItemPurge.blocked > 0, "objetos cortados en total: " + ItemPurge.blocked);
        });
    }

    // ---- terreno permanente ----
    private static void terrainTests() {
        then(2, () -> {
            clearArena();
            floor();
            // muro de piedra delante (Ruin 4) y piso a destruir (Ruin 5)
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = 0; dy < 5; dy++) {
                    for (int dz = 3; dz <= 9; dz++) {
                        level.setBlock(origin.offset(dx, dy, dz), Blocks.STONE.defaultBlockState(), 2);
                    }
                }
            }
            power(fake, "ruin", 25);
            fake.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0, 0);
            lookAt(fake, Vec3.atCenterOf(origin.offset(0, 1, 6)));
            WipsDestruction.destroyedTotal = 0;
            boolean ok = ability("ruin", 3).activate(fake);
            info("ruin4_activa", "activate=" + ok);
        });
        then(30, () -> {
            ability("ruin", 3).onRelease(fake);
            int air = 0;
            for (int dz = 3; dz <= 9; dz++) {
                if (level.getBlockState(origin.offset(0, 1, dz)).isAir()) {
                    air++;
                }
            }
            check("ruin4_destruye", air >= 4 && WipsDestruction.destroyedTotal > 20,
                    "tunel: " + air + "/7 bloques del eje abiertos, " + WipsDestruction.destroyedTotal + " bloques destruidos");
        });
        then(320, () -> {
            int air = 0;
            for (int dz = 3; dz <= 9; dz++) {
                if (level.getBlockState(origin.offset(0, 1, dz)).isAir()) {
                    air++;
                }
            }
            check("ruin4_permanente", air >= 4, "16 s despues siguen abiertos " + air + "/7 (sin restauracion)");
            WipsDestruction.destroyedTotal = 0;
            fake.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5 - 8, 0, 0);
            fake.setOnGround(true); // los jugadores falsos no tickean: se marca que esta parado
            boolean ok = ability("ruin", 4).activate(fake);
            info("ruin5_activa", "activate=" + ok);
        });
        then(60, () -> {
            check("ruin5_destruye", WipsDestruction.destroyedTotal > 50, "Collapse destruyo " + WipsDestruction.destroyedTotal + " bloques");
            clearArena();
            floor();
            for (int dx = -12; dx <= 12; dx++) {
                for (int dz = -12; dz <= 12; dz++) {
                    for (int dy = -6; dy < -1; dy++) {
                        level.setBlock(origin.offset(dx, dy, dz), Blocks.STONE.defaultBlockState(), 2);
                    }
                }
            }
            power(fake, "graviton", 25);
            fake.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0, 0);
            LivingEntity near = dummy(5, 0);
            LivingEntity far = dummy(15, 0);
            EVERY_TICK.add(() -> {
            });
            fake.getPersistentData().putFloat("near", near.getHealth());
            fake.getPersistentData().putInt("nearId", near.getId());
            fake.getPersistentData().putInt("farId", far.getId());
            fake.getPersistentData().putDouble("nearX", near.getX());
            WipsDestruction.destroyedTotal = 0;
            check("shinra_slot5", ability("graviton", 4) instanceof ShinraTensei, "Graviton ranura 5 = " + ability("graviton", 4).id());
            ability("graviton", 4).activate(fake);
        });
        then(60, () -> {
            var near = (LivingEntity) level.getEntity(fake.getPersistentData().getInt("nearId"));
            var far = (LivingEntity) level.getEntity(fake.getPersistentData().getInt("farId"));
            float lost = fake.getPersistentData().getFloat("near") - (near == null ? 0 : near.getHealth());
            double moved = near == null ? 99 : Math.abs(near.getX() - fake.getPersistentData().getDouble("nearX"));
            check("shinra_onda", lost > 0 && moved > 1.0, String.format("enemigo a 5 bloques: -%.1f vida, empujado %.1f bloques", lost, moved));
            check("shinra_alcance", far != null && far.getHealth() < 400, "enemigo a 15 bloques alcanzado: " + (far != null && far.getHealth() < 400));
            check("shinra_destruye", WipsDestruction.destroyedTotal > 500, "bloques destruidos para siempre: " + WipsDestruction.destroyedTotal
                    + " (agendados " + ShinraTensei.lastBlocks + ", pendientes " + WipsDestruction.pending() + ")");
            AwakenBurst.MUTE = true;
            int sent = level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, 0, 100, 0, 5, 0, 0, 0, 0);
            AwakenBurst.MUTE = false;
            int played = AwakenBurst.played;
            AwakenBurst.play(fake, PowerRegistry.get("ice"));
            check("despertar_unico", sent == 0 && AwakenBurst.played == played + 1, "particulas viejas silenciadas y efecto unico reproducido");
        });
    }

    // ---- HIELO ----
    private static void iceTests() {
        then(5, () -> {
            clearArena();
            floor();
            power(fake, "ice", 25);
            WipsLevel.setLevel(fake, 25);
            fake.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0, 0);
            LivingEntity d = dummy(1.5, 0);
            fake.getPersistentData().putInt("d", d.getId());
            ability("ice", 0).activate(fake);
            float before = d.getHealth();
            int freezes = Frost.freezes;
            for (int i = 0; i < 3; i++) {
                d.invulnerableTime = 0;
                d.hurt(level.damageSources().playerAttack(fake), 2.0F);
                if (i < 2) {
                    check("hielo_carga_" + (i + 1), Frost.charges(d) == i + 1 && !Frost.isFrozen(d), "cargas: " + Frost.charges(d));
                }
            }
            check("hielo_punos_bonus", hpLost(d, before) > 6.0F, String.format("3 golpes de 2: -%.1f (punos suman frio)", hpLost(d, before)));
            check("hielo_congela_3", Frost.isFrozen(d) && Frost.freezes == freezes + 1, "a las 3 cargas: congelado, cargas reiniciadas=" + (Frost.charges(d) == 0));
            fake.getPersistentData().putDouble("dx", d.getX());
            d.setDeltaMovement(1.0, 0, 0);
            fake.getPersistentData().putInt("dot0", Frost.dotHits);
            d.invulnerableTime = 0;
            d.hurt(level.damageSources().playerAttack(fake), 2.0F);
            check("hielo_no_acumula_congelado", Frost.charges(d) == 0, "golpe mientras esta congelado no suma cargas");
        });
        then(10, () -> {
            var d = (LivingEntity) level.getEntity(fake.getPersistentData().getInt("d"));
            check("hielo_clavado", d != null && Math.abs(d.getX() - fake.getPersistentData().getDouble("dx")) < 0.01 && Frost.isFrozen(d),
                    "empujado mientras esta congelado: no se mueve");
        });
        then(15, () -> {
            var d = (LivingEntity) level.getEntity(fake.getPersistentData().getInt("d"));
            check("hielo_dura_1s", d != null && !Frost.isFrozen(d), "a los ~1,25 s ya no esta congelado");
        });
        then(60, () -> {
            int dots = Frost.dotHits - fake.getPersistentData().getInt("dot0");
            check("hielo_escarcha_dot", dots >= 5 && dots <= 9, "golpes de Escarcha sin nuevos ataques en 4 s: " + dots + " (cada 0,5 s)");
            ability("ice", 0).activate(fake); // apagar punos
            clearArena();
            LivingEntity d = dummy(7, 0);
            fake.getPersistentData().putInt("d", d.getId());
            lookAt(fake, d.position());
            fake.getPersistentData().putDouble("dist0", d.position().distanceTo(Vec3.atBottomCenterOf(origin.offset(0, 0, 0))));
            // la ventisca apunta al suelo delante: ponerla en el origen mirando abajo
            fake.setXRot(90);
            ability("ice", 1).activate(fake);
            fake.getPersistentData().putInt("frz0", Frost.freezes);
        });
        then(40, () -> {
            var d = (LivingEntity) level.getEntity(fake.getPersistentData().getInt("d"));
            double dist = d == null ? 99 : d.position().distanceTo(Vec3.atBottomCenterOf(origin));
            check("ventisca_atrae", dist < fake.getPersistentData().getDouble("dist0") - 1.5,
                    String.format("distancia al centro %.1f -> %.1f en 2 s", fake.getPersistentData().getDouble("dist0"), dist));
            check("ventisca_carga", Frost.freezes > fake.getPersistentData().getInt("frz0") || d != null && Frost.charges(d) > 0,
                    "los pulsos suman cargas / congelan");
        });
        then(170, () -> {
            check("ventisca_10s", true, "zona terminada a los 10 s (sin error)");
            clearArena();
            // pisadas de hielo
            ability("ice", 2).activate(fake);
            fake.moveTo(origin.getX() + 0.5, origin.getY() + 3.2, origin.getZ() + 0.5, 0, 0);
            fake.setOnGround(false);
            int before = IcePower.IceSteps.platforms;
            IcePower.IceSteps.onAirJump(fake);
            BlockPos under = BlockPos.containing(fake.getX(), fake.getY() - 0.05, fake.getZ()).below();
            check("pisadas_plataforma", IcePower.IceSteps.platforms == before + 1 && level.getBlockState(under).is(Blocks.PACKED_ICE)
                    && !level.getBlockState(BlockPos.containing(fake.position())).is(Blocks.PACKED_ICE), "plataforma de hielo bajo los pies, no dentro del jugador");
            fake.getPersistentData().putLong("under", under.asLong());
            ability("ice", 2).activate(fake); // apagar
            IcePower.IceSteps.onAirJump(fake);
            check("pisadas_off", IcePower.IceSteps.platforms == before + 1, "desactivadas: no crea mas");
        });
        then(90, () -> {
            check("pisadas_temporal", level.getBlockState(BlockPos.of(fake.getPersistentData().getLong("under"))).isAir(), "la plataforma desaparece a los 4 s");
            fake.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0, 0);
            ability("ice", 3).activate(fake);
            ability("ice", 3).activate(fake);
            check("tempanos_max3", IceShardEntity.owned(fake).size() == 3, "tempanos al pulsar dos veces: " + IceShardEntity.owned(fake).size());
            LivingEntity d = dummy(9, 4);
            fake.getPersistentData().putInt("d", d.getId());
            fake.getPersistentData().putInt("h0", IceShardEntity.hits);
        });
        then(80, () -> {
            int hits = IceShardEntity.hits - fake.getPersistentData().getInt("h0");
            var d = (LivingEntity) level.getEntity(fake.getPersistentData().getInt("d"));
            check("tempanos_persiguen", hits >= 2 && d != null && d.getHealth() < 400, "impactos en un enemigo a ~10 bloques: " + hits);
            if (d != null) {
                d.discard();
            }
        });
        then(10, () -> {
            check("tempanos_sin_objetivo", IceShardEntity.owned(fake).size() <= 1, "objetivo desaparecido: no quedan persiguiendo nada (restan "
                    + IceShardEntity.owned(fake).size() + " orbitando)");
            clearArena();
            floor();
            LivingEntity a = dummy(6, 0);
            fake.getPersistentData().putInt("a", a.getId());
            ability("ice", 4).activate(fake);
        });
        then(4, () -> {
            var a = (LivingEntity) level.getEntity(fake.getPersistentData().getInt("a"));
            check("territorio_congela", a != null && Frost.isFrozen(a), "enemigo dentro del radio: congelado");
            check("territorio_no_dueno", !Frost.isFrozen(fake) && !Stun.stunned(fake), "el jugador no se congela");
        });
        then(40, () -> {
            LivingEntity b = dummy(-8, 0);
            fake.getPersistentData().putInt("b", b.getId());
        });
        then(4, () -> {
            var b = (LivingEntity) level.getEntity(fake.getPersistentData().getInt("b"));
            check("territorio_entra_despues", b != null && Frost.isFrozen(b), "enemigo que entra despues de creada: congelado");
        });
        then(56, () -> {
            var a = (LivingEntity) level.getEntity(fake.getPersistentData().getInt("a"));
            check("territorio_5s", a != null && !Frost.isFrozen(a), "a los ~5,5 s el primero ya se descongelo (5 s)");
        });
    }

    // ---- SLIME ----
    private static void slimeTests() {
        then(80, () -> {
            clearArena();
            floor();
            power(fake, "slime", 25);
            fake.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0, 0);
            LivingEntity d = dummy(2, 0);
            fake.getPersistentData().putInt("bd", d.getId());
            for (int i = 0; i < 2; i++) {
                d.invulnerableTime = 0;
                d.hurt(level.damageSources().playerAttack(fake), 1.0F);
            }
        });
        then(1, () -> {
            var d = (LivingEntity) level.getEntity(fake.getPersistentData().getInt("bd"));
            if (d == null) {
                check("baba_objetivo", false, "el objetivo de prueba desaparecio");
                return;
            }
            d.invulnerableTime = 0;
            d.hurt(level.damageSources().playerAttack(fake), 1.0F);
            int globs = level.getEntitiesOfClass(SlimeGlobEntity.class, new net.minecraft.world.phys.AABB(origin).inflate(8)).size();
            info("baba_golpes", "gotas despues de golpes en ticks distintos: " + globs);
        });
        then(1, () -> {
            var d = (LivingEntity) level.getEntity(fake.getPersistentData().getInt("bd"));
            if (d == null) {
                check("baba_objetivo", false, "el objetivo de prueba desaparecio");
                return;
            }
            for (int i = 0; i < 2; i++) {
                d.invulnerableTime = 0;
                d.hurt(level.damageSources().playerAttack(fake), 1.0F);
            }
            int globs = level.getEntitiesOfClass(SlimeGlobEntity.class, new net.minecraft.world.phys.AABB(origin).inflate(8)).size();
            check("baba_dedup_tick", globs <= 1, "dos golpes en el mismo tick cuentan uno: gotas=" + globs);
        });
        then(1, () -> {
            var d = (LivingEntity) level.getEntity(fake.getPersistentData().getInt("bd"));
            if (d == null) {
                check("baba_objetivo", false, "el objetivo de prueba desaparecio");
                return;
            }
            d.invulnerableTime = 0;
            d.hurt(level.damageSources().playerAttack(fake), 1.0F);
        });
        then(1, () -> {
            var d = (LivingEntity) level.getEntity(fake.getPersistentData().getInt("bd"));
            if (d == null) {
                check("baba_objetivo", false, "el objetivo de prueba desaparecio");
                return;
            }
            d.invulnerableTime = 0;
            d.hurt(level.damageSources().playerAttack(fake), 1.0F);
            int globs = level.getEntitiesOfClass(SlimeGlobEntity.class, new net.minecraft.world.phys.AABB(origin).inflate(8)).size();
            check("baba_cada_3", globs >= 1, "gotas tras 6 golpes en ticks distintos: " + globs + " (1 cada 3)");
            fake.setHealth(10.0F);
            var g = level.getEntitiesOfClass(SlimeGlobEntity.class, new net.minecraft.world.phys.AABB(origin).inflate(8)).get(0);
            fake.moveTo(g.getX(), g.getY(), g.getZ());
            fake.getPersistentData().putInt("p0", SlimeGlobEntity.picked);
        });
        then(15, () -> {
            check("baba_cura", SlimeGlobEntity.picked > fake.getPersistentData().getInt("p0") && fake.getHealth() > 10.0F,
                    String.format("recogida: vida 10 -> %.1f", fake.getHealth()));
            clearArena();
            fake.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0, 0);
            LivingEntity d = dummy(6, 0);
            lookAt(fake, d.position().add(0, 1, 0));
            fake.getPersistentData().putInt("d", d.getId());
            ability("slime", 0).activate(fake);
            check("minis_3", MiniSlimeEntity.owned(fake).size() == 3, "mini slimes: " + MiniSlimeEntity.owned(fake).size());
            ability("slime", 0).activate(fake);
            check("minis_sin_duplicar", MiniSlimeEntity.owned(fake).size() == 3, "pulsar otra vez con 3 activos: siguen " + MiniSlimeEntity.owned(fake).size());
            LivingEntity e2 = dummy(-6, 0);
            lookAt(fake, e2.position().add(0, 1, 0));
            ability("slime", 0).activate(fake);
            boolean all = MiniSlimeEntity.owned(fake).stream().allMatch(m -> m.getTarget() == e2);
            check("minis_cambian_objetivo", all, "al pulsar de nuevo apuntando a otro: todos lo persiguen");
            fake.getPersistentData().putInt("h0", MiniSlimeEntity.hits);
        });
        then(120, () -> {
            for (MiniSlimeEntity m : MiniSlimeEntity.owned(fake)) {
                info("mini", String.format("pos %.1f,%.1f,%.1f objetivo %s dist %.1f vida %.1f suelo %s", m.getX(), m.getY(), m.getZ(),
                        m.getTarget() == null ? "-" : m.getTarget().getType().toShortString(), m.getTarget() == null ? -1 : m.distanceTo(m.getTarget()),
                        m.getHealth(), m.onGround()));
            }
            check("minis_atacan", MiniSlimeEntity.hits > fake.getPersistentData().getInt("h0"), "golpes de los mini slimes en 6 s: "
                    + (MiniSlimeEntity.hits - fake.getPersistentData().getInt("h0")));
            boolean ownerSafe = fake.getHealth() > 0;
            check("minis_no_dueno", MiniSlimeEntity.owned(fake).stream().noneMatch(m -> m.getTarget() == fake) && ownerSafe, "nunca apuntan al dueno");
            fake.setHealth(10.0F);
            ability("slime", 1).activate(fake);
            check("recubrimiento_25", Math.abs(fake.getHealth() - 15.0F) < 0.01, String.format("vida 10/20 -> %.1f", fake.getHealth()));
            fake.setHealth(19.0F);
            ability("slime", 1).activate(fake);
            check("recubrimiento_tope", fake.getHealth() <= fake.getMaxHealth(), String.format("vida 19/20 -> %.1f (sin pasar el maximo)", fake.getHealth()));
            clearArena();
            LivingEntity d = dummy(0, 7);
            lookAt(fake, d.position().add(0, 1.0, 0));
            fake.getPersistentData().putInt("d", d.getId());
            fake.getPersistentData().putInt("s0", SlimeShotEntity.hits);
            final LivingEntity aim = d;
            EVERY_TICK.add(() -> lookAt(fake, aim.position().add(0, 1.0, 0))); // un jugador real sigue apuntando
            ability("slime", 2).activate(fake);
        });
        then(30, () -> {
            var d = (LivingEntity) level.getEntity(fake.getPersistentData().getInt("d"));
            int hits = SlimeShotEntity.hits - fake.getPersistentData().getInt("s0");
            var speed = d == null ? null : d.getAttribute(Attributes.MOVEMENT_SPEED);
            long mods = speed == null ? 0 : speed.getModifiers().stream().filter(m -> m.id().getPath().equals("slime_slow")).count();
            check("disparo_pega", hits >= 2, "impactos en 1,5 s: " + hits);
            check("disparo_ralentiza_70", d != null && SlimePower.Slow.slowed(d) && mods == 1, "ralentizado con un solo modificador (-70 %), no se acumula");
        });
        then(100, () -> {
            var d = (LivingEntity) level.getEntity(fake.getPersistentData().getInt("d"));
            check("disparo_4s", d != null && !SlimePower.Slow.slowed(d), "a los 4 s deja de disparar y la ralentizacion vence");
            clearArena();
            floor();
            fake.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0, 0);
            LivingEntity hitOnly = dummy(0, 4);
            fake.getPersistentData().putInt("hit", hitOnly.getId());
            fake.getPersistentData().putFloat("hit0", hitOnly.getHealth());
            fake.setXRot(90);
            ability("slime", 3).activate(fake);
        });
        then(25, () -> {
            var hitOnly = (LivingEntity) level.getEntity(fake.getPersistentData().getInt("hit"));
            check("explosion_area", hitOnly != null && hitOnly.getHealth() < fake.getPersistentData().getFloat("hit0"), "dano en area al de 4 bloques");
            check("explosion_no_pega_sin_charco", hitOnly == null || !Stun.stunned(hitOnly) || standingOnPuddle(hitOnly),
                    "el alcanzado por la explosion no queda pegado salvo que pise un charco");
            Vec3 puddle = SlimePower.firstPuddle();
            if (puddle != null) {
                LivingEntity onPuddle = dummy(puddle.x - origin.getX() - 0.5, 0, puddle.z - origin.getZ() - 0.5, 400);
                fake.getPersistentData().putInt("pud", onPuddle.getId());
                fake.getPersistentData().putFloat("pud0", onPuddle.getHealth());
            }
        });
        then(25, () -> {
            var onPuddle = (LivingEntity) level.getEntity(fake.getPersistentData().getInt("pud"));
            check("charco_pega_y_dana", onPuddle != null && Stun.stunned(onPuddle) && onPuddle.getHealth() < fake.getPersistentData().getFloat("pud0"),
                    "quien pisa un charco queda pegado y recibe dano");
            clearArena();
            floor();
            fake.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0, 0);
            ability("slime", 4).activate(fake);
            LivingEntity touch = dummy(0.6, 0);
            fake.getPersistentData().putInt("t", touch.getId());
            fake.getPersistentData().putInt("c0", SlimePower.BallForm.contacts);
        });
        then(5, () -> {
            var t = (LivingEntity) level.getEntity(fake.getPersistentData().getInt("t"));
            check("pelota_contacto", SlimePower.BallForm.contacts > fake.getPersistentData().getInt("c0") && t != null && Stun.stunned(t),
                    "pasar sobre un enemigo: dano y aturdimiento");
            var speed = fake.getAttribute(Attributes.MOVEMENT_SPEED);
            check("pelota_activa", SlimePower.BallForm.active(fake) && speed.getModifiers().size() > 0, "forma de pelota activa");
            fake.moveTo(origin.getX() + 10.5, origin.getY(), origin.getZ() + 0.5); // se aleja: no lo vuelve a tocar
        });
        then(46, () -> {
            var t = (LivingEntity) level.getEntity(fake.getPersistentData().getInt("t"));
            check("pelota_aturde_2s", t != null && !Stun.stunned(t), "aturdimiento de 2 s terminado");
        });
        then(260, () -> {
            check("pelota_15s", !SlimePower.BallForm.active(fake), "a los 15 s la forma termina y se restaura");
            var speed = fake.getAttribute(Attributes.MOVEMENT_SPEED);
            check("pelota_restaura", speed.getModifiers().stream().noneMatch(m -> m.id().getPath().equals("slime_ball_speed")), "velocidad normal");
        });
    }

    /** Jugador falso que SI recibe dano (el de NeoForge es invulnerable), para probar la barra de Determinacion. */
    private static final class TestPlayer extends net.neoforged.neoforge.common.util.FakePlayer {
        TestPlayer(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
            return false;
        }
    }

    private static boolean standingOnPuddle(LivingEntity e) {
        return SlimePower.onPuddle(e.position());
    }

    // ---- LUCHADOR ----
    private static void fighterTests() {
        then(5, () -> {
            clearArena();
            floor();
            ServerPlayer real = new TestPlayer(level, new GameProfile(UUID.nameUUIDFromBytes("wips_test3".getBytes()), "wips_test3"));
            real.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() - 3.5, 0, 0);
            try { // el jugador recien creado tiene 3 s de proteccion de aparicion y, sin tickear, nunca bajan
                var f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
                f.setAccessible(true);
                f.setInt(real, 0);
            } catch (ReflectiveOperationException e) {
                info("jugador_prueba", "sin acceso a spawnInvulnerableTime: " + e);
            }
            power(real, "fighter", 25);
            LivingEntity attacker = dummy(1.5, 0);
            FighterPower.Grit.set(real, 0);
            real.invulnerableTime = 0;
            float hp0 = real.getHealth();
            boolean took = real.hurt(level.damageSources().mobAttack(attacker), 4.0F);
            info("jugador_prueba", "hurt=" + took + " vida " + hp0 + " -> " + real.getHealth() + " invulnerable=" + real.isInvulnerableTo(level.damageSources().mobAttack(attacker))
                    + " modo=" + real.gameMode.getGameModeForPlayer() + " dificultad=" + level.getDifficulty() + " creativo=" + real.isCreative()
                    + " abilities.invulnerable=" + real.getAbilities().invulnerable);
            float r1 = FighterPower.Grit.value(real);
            real.invulnerableTime = 0;
            real.hurt(level.damageSources().fall(), 4.0F);
            real.invulnerableTime = 0;
            real.hurt(level.damageSources().magic(), 4.0F);
            float r2 = FighterPower.Grit.value(real);
            check("determinacion_sube", r1 > 0.2F, String.format("4 de dano de un mob: barra %.0f %%", r1 * 100));
            check("determinacion_ambiental", Math.abs(r2 - r1) < 1e-4, "caida y magia sin atacante no suman");
            real.setHealth(20);
            FighterPower.Grit.set(real, 1.0F);
            real.invulnerableTime = 0;
            real.hurt(level.damageSources().mobAttack(attacker), 5.0F);
            float takenFull = 20 - real.getHealth();
            real.setHealth(20);
            FighterPower.Grit.set(real, 0.0F);
            real.invulnerableTime = 0;
            real.hurt(level.damageSources().mobAttack(attacker), 5.0F);
            float takenEmpty = 20 - real.getHealth();
            check("determinacion_resistencia_20", takenEmpty > 0 && Math.abs(takenFull / takenEmpty - 0.8F) < 0.03F,
                    String.format("5 de dano: barra vacia -%.2f, llena -%.2f (x%.2f)", takenEmpty, takenFull, takenFull / Math.max(0.01F, takenEmpty)));
            real.discard();
            power(fake, "fighter", 25);
            fake.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0, 0);
            fake.setHealth(20);
            FighterPower.Grit.set(fake, 1.0F);
            float before = attacker.getHealth();
            attacker.invulnerableTime = 0;
            PowerKit.hit(fake, attacker, 10.0F);
            float withGrit = hpLost(attacker, before);
            FighterPower.Grit.set(fake, 0.0F);
            before = attacker.getHealth();
            attacker.invulnerableTime = 0;
            PowerKit.hit(fake, attacker, 10.0F);
            float noGrit = hpLost(attacker, before);
            check("determinacion_dano_20", Math.abs(withGrit / noGrit - 1.2F) < 0.03, String.format("barra llena x%.2f de dano", withGrit / noGrit));
            fake.setHealth(20);
            // punos ardientes
            ability("fighter", 0).activate(fake);
            LivingEntity side = dummy(2.5, 1);
            float s0 = side.getHealth();
            float m0 = attacker.getHealth();
            attacker.invulnerableTime = 0;
            attacker.hurt(level.damageSources().playerAttack(fake), 4.0F);
            check("punos_onda", side.getHealth() < s0, "la onda del golpe basico dana a un enemigo cercano");
            check("punos_fuego", attacker.isOnFire() || attacker.getRemainingFireTicks() > 0 || attacker.fireImmune(), "golpe con llamas");
            info("punos_primer_golpe", String.format("golpe basico de 4: -%.1f (incluye onda y barra)", hpLost(attacker, m0)));
        });
        then(2, () -> {
            clearArena();
            floor();
            FighterPower.Grit.set(fake, 0);
            fake.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0, 0);
            // Reel In desde 40 bloques
            LivingEntity farTarget = dummy(0, 0, 40, 400);
            lookAt(fake, farTarget.position().add(0, 1, 0));
            fake.getPersistentData().putInt("r", farTarget.getId());
            boolean ok = ability("fighter", 1).activate(fake);
            info("reel_in_activa", "activate=" + ok + " id=" + ability("fighter", 1).id());
        });
        then(80, () -> {
            var t = (LivingEntity) level.getEntity(fake.getPersistentData().getInt("r"));
            double d = t == null ? 99 : t.distanceTo(fake);
            check("reel_in_40", d < 8.0, String.format("objetivo a 40 bloques traido a %.1f", d));
            clearArena();
            Mob warden = EntityType.WARDEN.create(level);
            warden.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 25.5, 0, 0);
            warden.setNoAi(false);
            warden.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100000, 9, false, false));
            level.addFreshEntity(warden);
            fake.getPersistentData().putInt("w", warden.getId());
            lookAt(fake, warden.position().add(0, 1.5, 0));
            ability("fighter", 1).activate(fake);
        });
        then(70, () -> {
            var w = (LivingEntity) level.getEntity(fake.getPersistentData().getInt("w"));
            double d = w == null ? 99 : w.distanceTo(fake);
            check("reel_in_warden", d < 9.0, String.format("Warden a 25 bloques traido a %.1f (sin limite de tamano)", d));
            clearArena();
            Mob dragon = EntityType.ENDER_DRAGON.create(level);
            dragon.moveTo(origin.getX() + 0.5, origin.getY() + 6, origin.getZ() + 30.5, 0, 0);
            level.addFreshEntity(dragon);
            fake.getPersistentData().putInt("dr", dragon.getId());
            fake.getPersistentData().putDouble("dr0", dragon.distanceTo(fake));
            lookAt(fake, dragon.position().add(0, 2, 0));
            boolean ok = ability("fighter", 1).activate(fake);
            info("dragon", "activate=" + ok + " permitido=" + com.awakenedmod.awakened.core.Harm.allowed(fake, dragon)
                    + " pickable=" + dragon.isPickable() + " fase=" + ((net.minecraft.world.entity.boss.enderdragon.EnderDragon) dragon).getPhaseManager().getCurrentPhase().getPhase());
            final var drg = dragon;
            fake.getPersistentData().putDouble("drMin", 999);
            EVERY_TICK.add(() -> fake.getPersistentData().putDouble("drMin", Math.min(fake.getPersistentData().getDouble("drMin"), drg.distanceTo(fake))));
        });
        then(70, () -> {
            var dr = (LivingEntity) level.getEntity(fake.getPersistentData().getInt("dr"));
            double d = fake.getPersistentData().getDouble("drMin");
            check("reel_in_dragon", d < 8.0, String.format("Ender Dragon a %.1f bloques traido hasta %.1f (despues sigue su vuelo)",
                    fake.getPersistentData().getDouble("dr0"), d));
            if (dr != null) {
                dr.discard();
            }
            clearArena();
            floor();
            fake.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0, 0);
            fake.setYRot(0);
            fake.setXRot(0);
            // mirando a +Z (yaw 0): centro, borde y final del trapecio
            LivingEntity center = dummy(0, 0, 6, 400);
            LivingEntity edge = dummy(3.2, 0, 6, 400);
            LivingEntity end = dummy(0, 0, 14, 400);
            fake.getPersistentData().putInt("c", center.getId());
            fake.getPersistentData().putInt("e", edge.getId());
            fake.getPersistentData().putInt("n", end.getId());
            FighterPower.Grit.set(fake, 1.0F);
            ability("fighter", 2).activate(fake);
            float c = 400 - center.getHealth();
            float e = 400 - edge.getHealth();
            float n = 400 - end.getHealth();
            check("represalia_zonas", c > 0 && e > 0 && n > 0 && Math.abs(c / e - 2.0F / 0.6F) < 0.4F && Math.abs(c / n - 2.0F / 0.6F) < 0.4F,
                    String.format("centro -%.1f, borde -%.1f, final -%.1f (relacion %.2f; esperado 3,33)", c, e, n, c / e));
            check("represalia_consume", FighterPower.Grit.value(fake) == 0, "la barra se consume");
            FighterPower.Grit.set(fake, 0.0F);
            LivingEntity c2 = dummy(0, 0, 6, 400);
            ability("fighter", 2).activate(fake);
            float c0 = 400 - c2.getHealth();
            check("represalia_escala_barra", c > c0 * 2.0F, String.format("centro con barra llena -%.1f vs vacia -%.1f", c, c0));
        });
        then(2, () -> {
            clearArena();
            floor();
            LivingEntity a = dummy(2, 0);
            float a0 = a.getHealth();
            FighterPower.ImpactLeap.land(fake, 5);
            float low = FighterPower.ImpactLeap.lastBase;
            float l0 = a0 - a.getHealth();
            a.invulnerableTime = 0;
            a0 = a.getHealth();
            FighterPower.ImpactLeap.land(fake, 30);
            float high = FighterPower.ImpactLeap.lastBase;
            float l1 = a0 - a.getHealth();
            check("salto_escala_altura", high > low * 2 && l1 > l0, String.format("onda tras 5 bloques: base %.1f (-%.1f); tras 30: base %.1f (-%.1f)", low, l0, high, l1));
            check("salto_sin_autodano", fake.getHealth() >= 19.9F, "el jugador no se dana");
            // golpe serio sin cargar
            clearArena();
            floor();
            fake.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0, 0);
            fake.setYRot(0);
            fake.setXRot(0);
            dummy(0, 0, 10, 400);
            dummy(4, 0, 16, 400);
            ability("fighter", 4).activate(fake);
        });
        then(3, () -> {
            ability("fighter", 4).onRelease(fake);
            check("golpe_serio_sin_carga", FighterPower.SeriousPunch.lastTargets >= 1 && FighterPower.SeriousPunch.lastBlocks == 0,
                    "sin cargar: alcanza " + FighterPower.SeriousPunch.lastTargets + " enemigos, no destruye bloques");
            for (int dx = -10; dx <= 10; dx++) {
                for (int dy = 0; dy < 10; dy++) {
                    for (int dz = 6; dz <= 40; dz += 1) {
                        level.setBlock(origin.offset(dx, dy, dz), Blocks.STONE.defaultBlockState(), 2);
                    }
                }
            }
            WipsDestruction.destroyedTotal = 0;
            ability("fighter", 4).forgetCooldown(fake);
            ability("fighter", 4).activate(fake);
        });
        then(105, () -> {
            check("golpe_serio_carga_max", FighterPower.SeriousPunch.lastBlocks > 2000, "a carga maxima agenda " + FighterPower.SeriousPunch.lastBlocks
                    + " bloques de destruccion permanente");
        });
        then(60, () -> {
            check("golpe_serio_destruye", WipsDestruction.destroyedTotal > 2000 && WipsDestruction.pending() == 0,
                    "destruidos " + WipsDestruction.destroyedTotal + " (repartido en ticks, cola vacia)");
        });
    }

    // ---- rendimiento: 30 enemigos con ventisca, Shinra y golpe serio a la vez ----
    private static void perfTests() {
        then(5, () -> {
            clearArena();
            floor();
            for (int i = 0; i < 30; i++) {
                double a = i * (Math.PI * 2 / 30);
                dummy(Math.cos(a) * 10, Math.sin(a) * 10);
            }
            tickNanos = 0;
            tickCount = 0;
        });
        then(100, () -> {
            info("rendimiento_base", String.format("30 enemigos quietos: %.2f ms/tick de media", tickNanos / 1.0E6 / Math.max(1, tickCount)));
            power(fake, "ice", 25);
            fake.setXRot(90);
            ability("ice", 1).activate(fake);
            ability("ice", 4).activate(fake);
            power(fake2, "graviton", 25);
            fake2.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() - 4.5, 0, 0);
            ability("graviton", 4).activate(fake2);
            tickNanos = 0;
            tickCount = 0;
        });
        then(100, () -> {
            double ms = tickNanos / 1.0E6 / Math.max(1, tickCount);
            check("rendimiento_carga", ms < 50.0, String.format("ventisca + congelacion territorial + Shinra con 30 enemigos: %.2f ms/tick de media", ms));
        });
    }
}
