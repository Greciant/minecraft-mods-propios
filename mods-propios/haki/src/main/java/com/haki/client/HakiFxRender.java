package com.haki.client;

import java.util.Map;

import com.haki.HakiMod;
import com.haki.HakiVisuals;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Dibuja los rayos radiales alrededor de cada lanzador activo. */
@EventBusSubscriber(modid = HakiMod.MODID, value = Dist.CLIENT)
public final class HakiFxRender {
    private HakiFxRender() {}

    public static final ResourceLocation BOLT_TEX =
        ResourceLocation.fromNamespaceAndPath(HakiMod.MODID, "textures/fx/bolt.png");
    public static final ResourceLocation BOLT_CORE_TEX =
        ResourceLocation.fromNamespaceAndPath(HakiMod.MODID, "textures/fx/bolt_core.png");
    public static final ResourceLocation BOLT_GLOW_TEX =
        ResourceLocation.fromNamespaceAndPath(HakiMod.MODID, "textures/fx/bolt_glow.png");
    public static final ResourceLocation SPARK_TEX =
        ResourceLocation.fromNamespaceAndPath(HakiMod.MODID, "textures/fx/spark.png");
    public static final ResourceLocation BOLT_SHEET_TEX = fx("bolt_sheet"), RING_TEX = fx("ring_sheet"),
        BURST_TEX = fx("burst_sheet"), CRACK_TEX = fx("crack_sheet"), SMOKE_TEX = fx("smoke_sheet"),
        DEBRIS_TEX = fx("debris"), SIGIL_TEX = fx("sigil"), BAND_TEX = fx("band"), BEAM_TEX = fx("beam");

    private static ResourceLocation fx(String name) {
        return ResourceLocation.fromNamespaceAndPath(HakiMod.MODID, "textures/fx/" + name + ".png");
    }

    public static final ResourceLocation STAR_TEX =
        ResourceLocation.fromNamespaceAndPath(HakiMod.MODID, "textures/fx/impact_star.png");
    public static final ResourceLocation DOME_TEX =
        ResourceLocation.fromNamespaceAndPath(HakiMod.MODID, "textures/fx/dome.png");
    public static final ResourceLocation EYE_TEX =
        ResourceLocation.fromNamespaceAndPath(HakiMod.MODID, "textures/fx/eye_glow.png");
    static final int FULL_BRIGHT = 0xF000F0;
    /** Solo para medir rendimiento: HAKI_SKIP=aura,bolts,dome,ring,screen apaga esas capas. */
    private static final String SKIP = System.getenv("HAKI_SKIP") == null ? "" : System.getenv("HAKI_SKIP");
    static boolean skip(String what) { return !SKIP.isEmpty() && SKIP.contains(what); }

    // Buffers reutilizados para no crear objetos en cada frame
    private static final RandomSource RNG = RandomSource.create();

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (HakiClientFx.CASTERS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        // En 21.1 la rotacion de la camara ya esta en el modelview; el PoseStack solo necesita la traslacion
        PoseStack pose = event.getPoseStack() != null ? event.getPoseStack() : new PoseStack();
        PoseStack.Pose last = pose.last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vector3f left = event.getCamera().getLeftVector(), up = event.getCamera().getUpVector();
        double maxDist2 = HakiVisuals.BOLT_RENDER_DISTANCE * HakiVisuals.BOLT_RENDER_DISTANCE;
        int style = HakiVisuals.STYLE;
        boolean sprites = style != HakiVisuals.STYLE_PROCEDURAL;   // estilos 1 y 3
        boolean proc = style == HakiVisuals.STYLE_PROCEDURAL;      // estilo 2
        boolean extras = style != HakiVisuals.STYLE_SPRITES;       // estilos 2 y 3
        boolean hybrid = style == HakiVisuals.STYLE_HYBRID;

        // ---- Geometria de todos los lanzadores (una vez por cuadro) ----
        HakiBolts.begin();
        HakiAura.begin();
        BURST.clear();
        int nctx = 0;
        for (Map.Entry<Integer, HakiClientFx.Caster> e : HakiClientFx.CASTERS.entrySet()) {
            Entity caster = mc.level.getEntity(e.getKey());
            HakiClientFx.Caster c = e.getValue();
            if (caster == null) continue;
            float fade = c.prevFade + (c.fade - c.prevFade) * pt;
            if (fade <= 0.001f) continue;
            Vec3 pos = caster.getPosition(pt);
            if (pos.distanceToSqr(cam) > maxDist2) continue;
            if (nctx == CTX.size()) CTX.add(new Ctx());
            Ctx k = CTX.get(nctx++);
            k.id = e.getKey();
            k.fade = fade;
            k.age = c.age + pt;
            k.x = (float) (pos.x - cam.x);
            k.z = (float) (pos.z - cam.z);
            k.feetY = (float) (pos.y - cam.y);
            if (c.rayAge != c.age) { // el suelo bajo el lanzador se recalcula una vez por tick
                BlockHitResult hit = mc.level.clip(new ClipContext(pos.add(0, 0.2, 0),
                    pos.subtract(0, HakiVisuals.STAR_RAYCAST_DOWN, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
                c.hasGround = hit.getType() == HitResult.Type.BLOCK;
                c.groundY = c.hasGround ? hit.getLocation().y : Double.NaN;
                c.rayAge = c.age;
            }
            k.hasGround = c.hasGround;
            k.gy = (float) ((k.hasGround ? c.groundY : pos.y) - cam.y);
            k.chestY = (float) (pos.y + caster.getBbHeight() * 0.6 - cam.y);
            k.headY = (float) (pos.y + caster.getBbHeight() - cam.y);

            // Los rayos propios que apuntan a la camara (o hacia donde mira el jugador en primera persona) taparian todo
            boolean own = caster == mc.player;
            boolean ownFirstPerson = own && mc.options.getCameraType().isFirstPerson();
            k.ownFirstPerson = ownFirstPerson;
            boolean far = pos.distanceToSqr(cam) > HakiVisuals.LOD_FAR_DISTANCE * HakiVisuals.LOD_FAR_DISTANCE;
            int slots = sprites ? HakiVisuals.SPRITE_BOLT_SLOTS : HakiVisuals.BOLT_SLOTS;
            if (far) slots /= 2;
            float startOffset = HakiVisuals.BOLT_START_OFFSET, widthScale = 1f;
            float avx = 0, avy = 0, avz = 0, avoidCos = 2f, maxUp = 2f;
            if (ownFirstPerson) {
                Vec3 look = caster.getViewVector(pt);
                avx = (float) look.x; avy = (float) look.y; avz = (float) look.z;
                avoidCos = HakiVisuals.BOLT_FP_MAX_FRONT;
                maxUp = HakiVisuals.BOLT_FP_MAX_UP;
                slots = sprites ? HakiVisuals.SPRITE_BOLT_FP_SLOTS : HakiVisuals.BOLT_FP_SLOTS;
                startOffset = HakiVisuals.BOLT_FP_START_OFFSET;
                widthScale = HakiVisuals.BOLT_FP_WIDTH_SCALE;
            } else if (own) {
                float l = (float) Math.sqrt(k.x * k.x + k.chestY * k.chestY + k.z * k.z);
                if (l > 1e-3f) {
                    avx = -k.x / l; avy = -k.chestY / l; avz = -k.z / l;
                    avoidCos = HakiVisuals.BOLT_TP_MAX_FRONT;
                }
            }
            if (!skip("bolts")) HakiBolts.add(k.id, k.age, k.x, k.chestY, k.z, fade, slots, startOffset, widthScale,
                avx, avy, avz, avoidCos, maxUp, sprites);
            if (extras && !far && !skip("aura")) HakiAura.add(k.id, k.age, k.x, k.gy, k.z, k.chestY, k.headY, fade, ownFirstPerson, style, c.active);
            if (sprites && !ownFirstPerson && k.age < HakiVisuals.SPRITE_BURST_TICKS) {
                float g = k.age / HakiVisuals.SPRITE_BURST_TICKS;
                BURST.add(k.x, k.chestY, k.z, HakiVisuals.SPRITE_BURST_SIZE * (0.55f + 0.45f * easeOutCubic(g)), fade,
                    0xFFFFFF, 0f, Math.min(15, (int) (g * 16f)));
            }
        }
        if (nctx == 0 || skip("all")) return;

        // ---- 1) humo oscuro (detras de todo) ----
        if (extras && HakiAura.SMOKE.n > 0) {
            RenderType t = RenderType.entityTranslucentEmissive(SMOKE_TEX);
            HakiAura.drawPool(buffers.getBuffer(t), last, HakiAura.SMOKE, left, up, 4, 4);
            buffers.endBatch(t);
        }

        // ---- 2) suelo: estrella (estilo 2) o onda, grietas y sigilo en sprites ----
        if (sprites) {
            RenderType t = RenderType.entityTranslucentEmissive(CRACK_TEX);
            VertexConsumer vc = buffers.getBuffer(t);
            for (int i = 0; i < nctx; i++) {
                Ctx k = CTX.get(i);
                if (!k.hasGround) continue;
                int frame = Math.min(15, (int) (k.age * 16f / HakiVisuals.SPRITE_CRACK_TICKS));
                float a = k.fade * (k.age < HakiVisuals.SPRITE_CRACK_TICKS ? 1f : 0.8f + 0.2f * Mth.sin(k.age * 0.2f));
                groundSheet(vc, last, k.x, k.gy + 0.035f, k.z, HakiVisuals.SPRITE_CRACK_HALF, 0f, a, frame);
            }
            buffers.endBatch(t);
            t = RenderType.entityTranslucentEmissive(RING_TEX);
            vc = buffers.getBuffer(t);
            for (int i = 0; i < nctx; i++) {
                Ctx k = CTX.get(i);
                if (!k.hasGround) continue;
                float h = HakiVisuals.SPRITE_RING_HALF;
                if (k.age < HakiVisuals.SPRITE_RING_TICKS) {
                    groundSheet(vc, last, k.x, k.gy + 0.05f, k.z, h, 0f, k.fade,
                        Math.min(15, (int) (k.age * 16f / HakiVisuals.SPRITE_RING_TICKS)));
                } else {
                    float p = (k.age % HakiVisuals.PULSE_INTERVAL) / HakiVisuals.PULSE_TICKS;
                    if (p < 1f) groundSheet(vc, last, k.x, k.gy + 0.05f, k.z, h, 0f, k.fade * 0.6f, Math.min(15, (int) (p * 12f)));
                }
            }
            buffers.endBatch(t);
        }
        if (hybrid && HakiAura.hasGroundSigil()) {
            RenderType t = RenderType.entityTranslucentEmissive(SIGIL_TEX);
            VertexConsumer vc = buffers.getBuffer(t);
            for (int i = 0; i < HakiAura.groundSigils; i++) {
                int o = i * 6;
                float[] g = HakiAura.GROUND_SIGIL;
                groundQuad(vc, last, g[o], g[o + 1], g[o + 2], g[o + 3], g[o + 4], g[o + 5]);
            }
            buffers.endBatch(t);
        }

        // ---- 3) onda de choque: cupula que crece al empezar + un pulso por cada golpe de dano ----
        if (!skip("dome")) {
            RenderType t = RenderType.entityTranslucentEmissive(DOME_TEX);
            VertexConsumer vc = buffers.getBuffer(t);
            for (int i = 0; i < nctx; i++) {
                Ctx k = CTX.get(i);
                HakiClientFx.Caster c = HakiClientFx.CASTERS.get(k.id);
                float y = k.hasGround ? k.gy : k.feetY;
                if (k.age < HakiVisuals.DOME_GROW_TICKS) {
                    float g = k.age / HakiVisuals.DOME_GROW_TICKS;
                    dome(vc, last, k.x, y, k.z, HakiVisuals.DOME_RADIUS * easeOutCubic(g),
                        HakiVisuals.DOME_ALPHA * pulseAlpha(g) * k.fade);
                } else if (c != null && c.active) {
                    float p = (k.age % HakiVisuals.PULSE_INTERVAL) / HakiVisuals.PULSE_TICKS;
                    if (p < 1f) dome(vc, last, k.x, y, k.z, HakiVisuals.DOME_RADIUS * easeOutCubic(p),
                        HakiVisuals.PULSE_ALPHA * pulseAlpha(p) * k.fade);
                }
            }
            buffers.endBatch(t);
        }

        // ---- 4) anillo negro/rojo en el suelo en el borde de la onda (estilo 2) ----
        if (proc) {
            RenderType t = RenderType.entityTranslucentEmissive(BOLT_TEX);
            VertexConsumer vc = buffers.getBuffer(t);
            for (int i = 0; i < nctx; i++) {
                Ctx k = CTX.get(i);
                if (!k.hasGround) continue;
                if (k.age < HakiVisuals.DOME_GROW_TICKS) {
                    float g = k.age / HakiVisuals.DOME_GROW_TICKS;
                    ring(vc, last, k.x, k.gy + 0.05f, k.z, HakiVisuals.DOME_RADIUS * easeOutCubic(g),
                        HakiVisuals.RING_WIDTH * (1f - 0.5f * g), pulseAlpha(g) * k.fade);
                } else { // un anillo por cada pulso de dano, sincronizado con la cupula
                    float pp = (k.age % HakiVisuals.PULSE_INTERVAL) / HakiVisuals.PULSE_TICKS;
                    if (pp < 1f) ring(vc, last, k.x, k.gy + 0.05f, k.z, HakiVisuals.DOME_RADIUS * easeOutCubic(pp),
                        HakiVisuals.RING_WIDTH * (1f - 0.6f * pp), pulseAlpha(pp) * HakiVisuals.PULSE_RING_ALPHA * k.fade);
                }
            }
            buffers.endBatch(t);
        }

        // ---- 5) anillos orbitales y columnas de luz ----
        if (extras && HakiAura.hasBands()) {
            RenderType t = RenderType.entityTranslucentEmissive(BAND_TEX);
            HakiAura.drawBands(buffers.getBuffer(t), last);
            buffers.endBatch(t);
        }
        if (extras && HakiAura.hasBeams()) {
            RenderType t = RenderType.entityTranslucentEmissive(BEAM_TEX);
            HakiAura.drawBeams(buffers.getBuffer(t), last);
            buffers.endBatch(t);
        }

        if (extras && HakiAura.meshes > 0) {
            RenderType t = RenderType.entityTranslucentEmissive(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
            HakiAura.drawMeshes(buffers.getBuffer(t), pose);
            buffers.endBatch(t);
        }

        // ---- 6) relampagos: halo, nucleo, laminas de sprites y chispas ----
        if (HakiBolts.hasBolts()) {
            RenderType t = RenderType.entityTranslucentEmissive(BOLT_GLOW_TEX);
            HakiBolts.drawRibbons(buffers.getBuffer(t), last, HakiVisuals.BOLT_GLOW_SCALE, HakiVisuals.BOLT_GLOW_MIN,
                HakiVisuals.BOLT_GLOW_ALPHA);
            buffers.endBatch(t);
            t = RenderType.entityTranslucentEmissive(BOLT_CORE_TEX);
            HakiBolts.drawRibbons(buffers.getBuffer(t), last, 1f, 0f, 1f);
            buffers.endBatch(t);
        }
        if (HakiBolts.hasSprites()) {
            RenderType t = RenderType.entityTranslucentEmissive(BOLT_SHEET_TEX);
            HakiBolts.drawSprites(buffers.getBuffer(t), last);
            buffers.endBatch(t);
        }
        if (BURST.n > 0) {
            RenderType t = RenderType.entityTranslucentEmissive(BURST_TEX);
            HakiAura.drawPool(buffers.getBuffer(t), last, BURST, left, up, 4, 4);
            buffers.endBatch(t);
        }
        {
            RenderType t = RenderType.entityTranslucentEmissive(SPARK_TEX);
            VertexConsumer vc = buffers.getBuffer(t);
            HakiBolts.drawSparks(vc, last, left, up);
            if (extras) HakiAura.drawPool(vc, last, HakiAura.EMBERS, left, up, 1, 1);
            buffers.endBatch(t);
        }

        // ---- 7) emblema sobre la cabeza y rocas flotantes ----
        if (hybrid && HakiAura.HEAD_SIGIL.n > 0) {
            RenderType t = RenderType.entityTranslucentEmissive(SIGIL_TEX);
            HakiAura.drawPool(buffers.getBuffer(t), last, HakiAura.HEAD_SIGIL, left, up, 1, 1);
            buffers.endBatch(t);
        }
        if (extras && HakiAura.DEBRIS.n > 0) {
            RenderType t = RenderType.entityTranslucentEmissive(DEBRIS_TEX);
            HakiAura.drawPool(buffers.getBuffer(t), last, HakiAura.DEBRIS, left, up, 4, 1);
            buffers.endBatch(t);
        }
    }

    /** Datos por cuadro de un lanzador visible (se reutilizan los objetos). */
    private static final class Ctx {
        int id;
        float fade, age, x, z, feetY, gy, chestY, headY;
        boolean hasGround, ownFirstPerson;
    }

    private static final java.util.ArrayList<Ctx> CTX = new java.util.ArrayList<>();
    private static final HakiAura.Pool BURST = new HakiAura.Pool(8);

    /** Cuadrado plano en el suelo con un cuadro (frame) de una hoja de 4x4. */
    static void groundSheet(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z,
                            float half, float angle, float alpha, int frame) {
        float cs = (float) Math.cos(angle) * half, sn = (float) Math.sin(angle) * half;
        float u0 = (frame & 3) * 0.25f, v0 = (frame >> 2) * 0.25f, u1 = u0 + 0.25f, v1 = v0 + 0.25f;
        vertex(vc, pose, x - cs + sn, y, z - sn - cs, u0, v0, alpha);
        vertex(vc, pose, x - cs - sn, y, z - sn + cs, u0, v1, alpha);
        vertex(vc, pose, x + cs - sn, y, z + sn + cs, u1, v1, alpha);
        vertex(vc, pose, x + cs + sn, y, z + sn - cs, u1, v0, alpha);
    }

    /** Semiesfera de radio r con base en (x, y, z). Malla baja: DOME_SEGMENTS x DOME_RINGS quads. */
    private static void dome(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float r, float alpha) {
        if (r < 0.05f || alpha <= 0.001f) return;
        int segs = HakiVisuals.DOME_SEGMENTS, rings = HakiVisuals.DOME_RINGS;
        for (int j = 0; j < rings; j++) {
            float e0 = (float) (Math.PI / 2 * j / rings), e1 = (float) (Math.PI / 2 * (j + 1) / rings);
            float c0 = Mth.cos(e0) * r, s0 = Mth.sin(e0) * r, c1 = Mth.cos(e1) * r, s1 = Mth.sin(e1) * r;
            float v0 = (float) j / rings, v1 = (float) (j + 1) / rings;
            for (int i = 0; i < segs; i++) {
                float a0 = (float) (Math.PI * 2 * i / segs), a1 = (float) (Math.PI * 2 * (i + 1) / segs);
                float ca0 = Mth.cos(a0), sa0 = Mth.sin(a0), ca1 = Mth.cos(a1), sa1 = Mth.sin(a1);
                float u0 = (float) i / segs, u1 = (float) (i + 1) / segs;
                vertex(vc, pose, x + ca0 * c0, y + s0, z + sa0 * c0, u0, v0, alpha);
                vertex(vc, pose, x + ca1 * c0, y + s0, z + sa1 * c0, u1, v0, alpha);
                vertex(vc, pose, x + ca1 * c1, y + s1, z + sa1 * c1, u1, v1, alpha);
                vertex(vc, pose, x + ca0 * c1, y + s1, z + sa0 * c1, u0, v1, alpha);
            }
        }
    }

    /** Anillo plano en el suelo (textura del rayo: v cruza el ancho). */
    private static void ring(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z,
                             float r, float width, float alpha) {
        if (r < 0.1f || alpha <= 0.001f) return;
        float ri = Math.max(0f, r - width * 0.5f), ro = r + width * 0.5f;
        int segs = HakiVisuals.RING_SEGMENTS;
        for (int i = 0; i < segs; i++) {
            float a0 = (float) (Math.PI * 2 * i / segs), a1 = (float) (Math.PI * 2 * (i + 1) / segs);
            float ca0 = Mth.cos(a0), sa0 = Mth.sin(a0), ca1 = Mth.cos(a1), sa1 = Mth.sin(a1);
            float u0 = (i % 4) / 4f * 0.5f, u1 = u0 + 0.125f;
            vertex(vc, pose, x + ca0 * ri, y, z + sa0 * ri, u0, 0f, alpha);
            vertex(vc, pose, x + ca0 * ro, y, z + sa0 * ro, u0, 1f, alpha);
            vertex(vc, pose, x + ca1 * ro, y, z + sa1 * ro, u1, 1f, alpha);
            vertex(vc, pose, x + ca1 * ri, y, z + sa1 * ri, u1, 0f, alpha);
        }
    }

    /** Cuadrado de frente a la camara, con color. */
    static void billboard(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float half,
                                  Vector3f left, Vector3f up, int rgb, float alpha) {
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        float lx = left.x() * half, ly = left.y() * half, lz = left.z() * half;
        float ux = up.x() * half, uy = up.y() * half, uz = up.z() * half;
        vertexC(vc, pose, x + lx + ux, y + ly + uy, z + lz + uz, 0f, 0f, r, g, b, alpha);
        vertexC(vc, pose, x + lx - ux, y + ly - uy, z + lz - uz, 0f, 1f, r, g, b, alpha);
        vertexC(vc, pose, x - lx - ux, y - ly - uy, z - lz - uz, 1f, 1f, r, g, b, alpha);
        vertexC(vc, pose, x - lx + ux, y - ly + uy, z - lz + uz, 1f, 0f, r, g, b, alpha);
    }

    static void vertexC(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z,
                        float u, float v, float r, float g, float b, float alpha) {
        vc.addVertex(pose, x, y, z)
            .setColor(r, g, b, alpha)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(FULL_BRIGHT)
            .setNormal(pose, 0f, 1f, 0f);
    }

    private static void groundQuad(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z,
                                   float size, float angle, float alpha) {
        float cs = (float) Math.cos(angle) * size, sn = (float) Math.sin(angle) * size;
        // esquinas rotadas: (-1,-1) (-1,1) (1,1) (1,-1)
        vertex(vc, pose, x - cs + sn, y, z - sn - cs, 0f, 0f, alpha);
        vertex(vc, pose, x - cs - sn, y, z - sn + cs, 0f, 1f, alpha);
        vertex(vc, pose, x + cs - sn, y, z + sn + cs, 1f, 1f, alpha);
        vertex(vc, pose, x + cs + sn, y, z + sn - cs, 1f, 0f, alpha);
    }

    /** 1 - (1 - x)^3: arranca rapido y frena suave. */
    static float easeOutCubic(float x) {
        float f = 1f - Mth.clamp(x, 0f, 1f);
        return 1f - f * f * f;
    }

    /** x*x*(3 - 2x): sube y baja sin saltos. */
    static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    /** Opacidad de un pulso que avanza de 0 a 1: aparece suave (primer 15 %) y se apaga suave. */
    static float pulseAlpha(float x) {
        return smooth(x / 0.15f) * (1f - smooth(x));
    }

    /**
     * Cinta en zigzag orientada hacia la camara. pts esta en coordenadas del pose;
     * (camX, camY, camZ) es la posicion de la camara en esas mismas coordenadas.
     */
    static void ribbon(VertexConsumer vc, PoseStack.Pose pose, float[] pts, int segs,
                       float w0, float w1, float camX, float camY, float camZ, float alpha, float u0) {
        for (int k = 0; k < segs; k++) {
            float x0 = pts[k * 3], y0 = pts[k * 3 + 1], z0 = pts[k * 3 + 2];
            float x1 = pts[k * 3 + 3], y1 = pts[k * 3 + 4], z1 = pts[k * 3 + 5];
            float sx = x1 - x0, sy = y1 - y0, sz = z1 - z0;
            float mx = (x0 + x1) * 0.5f - camX, my = (y0 + y1) * 0.5f - camY, mz = (z0 + z1) * 0.5f - camZ;
            // perpendicular = tramo x (punto - camara)
            float px = sy * mz - sz * my, py = sz * mx - sx * mz, pz = sx * my - sy * mx;
            float pl = (float) Math.sqrt(px * px + py * py + pz * pz);
            if (pl < 1e-5f) continue;
            px /= pl; py /= pl; pz /= pl;
            float wa = (w0 + (w1 - w0) * k / segs) * 0.5f;
            float wb = (w0 + (w1 - w0) * (k + 1) / segs) * 0.5f;
            // Las texturas de MC usan clamp: u se queda en [0,1], desplazado por u0 para variar las chispas
            float ua = (u0 + (float) k / segs) * 0.5f, ub = (u0 + (float) (k + 1) / segs) * 0.5f;
            vertex(vc, pose, x0 + px * wa, y0 + py * wa, z0 + pz * wa, ua, 0f, alpha);
            vertex(vc, pose, x0 - px * wa, y0 - py * wa, z0 - pz * wa, ua, 1f, alpha);
            vertex(vc, pose, x1 - px * wb, y1 - py * wb, z1 - pz * wb, ub, 1f, alpha);
            vertex(vc, pose, x1 + px * wb, y1 + py * wb, z1 + pz * wb, ub, 0f, alpha);
        }
    }

    static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z,
                       float u, float v, float alpha) {
        vc.addVertex(pose, x, y, z)
            .setColor(1f, 1f, 1f, alpha)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(FULL_BRIGHT)
            .setNormal(pose, 0f, 1f, 0f);
    }
}
