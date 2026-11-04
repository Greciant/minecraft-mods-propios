package wips.powers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import wips.powers.WipsPowers;

/**
 * Animaciones hechas con los fotogramas de los samples (hojas 6x6 = 36 cuadros, fondo negro, premultiplicadas;
 * las "_mask" son en gris para teñirlas con el color de cada poder; el humo es blanco con alfa y se tiñe oscuro).
 * Tres formas: mirando a la camara, plana con una normal (ondas en el suelo) o estirada entre dos
 * puntos (rayos, chorros). Se dibujan aditivas (brillan, ignoran la luz del mundo y funcionan con
 * shaders de Iris como emisivas) salvo el humo, que va con mezcla normal para que oscurezca.
 */
@EventBusSubscriber(modid = WipsPowers.MOD_ID, value = Dist.CLIENT)
public final class FlipbookFx {
    private static final int FRAMES = 36;
    private static final int COLS = 6;
    /** Relacion ancho/alto de cada hoja (celda), medida al generar los atlas. */
    private static final Map<String, Float> ASPECT = new HashMap<>();
    /** Hojas que van con mezcla normal (humo): oscurecen en vez de brillar. */
    private static final java.util.Set<String> SMOKE = java.util.Set.of("smoke_ring", "smoke_side", "smoke_puff", "ash_ring", "gas_blast");

    static {
        String[][] a = {
                {"explosion", "192", "191"}, {"smoke_puff", "150", "192"}, {"campfire", "137", "192"}, {"burst_violet", "181", "192"},
                {"burst_fire", "185", "192"}, {"burst_mask", "185", "192"}, {"ring_blue", "186", "192"}, {"ring_mask", "186", "192"},
                {"fire_ring", "192", "143"}, {"ash_ring", "192", "130"}, {"flame_jet", "192", "71"}, {"blue_jet", "192", "24"},
                {"bubbles", "105", "192"}, {"bubble_cloud", "192", "162"}, {"shield", "125", "192"}, {"shield_mask", "125", "192"},
                {"spell_pink", "192", "25"}, {"beam_blue", "192", "30"}, {"beam_mask", "192", "30"}, {"nova", "192", "115"},
                {"nova_mask", "192", "115"}, {"sorcerer_flame", "161", "192"}, {"flame_mask", "161", "192"}, {"water_spray", "192", "112"},
                {"water_circle", "166", "192"}, {"gas_blast", "179", "192"}, {"ground_fire", "192", "151"}, {"sparks", "192", "105"},
                {"spark_mask", "192", "105"}, {"teal_flame", "105", "192"}, {"portal", "192", "189"}, {"portal_mask", "192", "189"},
                {"smoke_ring", "170", "192"}, {"smoke_side", "192", "44"}, {"blue_ground", "135", "192"}, {"groundfire_mask", "135", "192"},
                {"strike_a", "192", "107"}, {"strike_mask", "192", "107"}, {"strike_b", "184", "192"}, {"bolt_arc", "147", "192"},
                {"arc_mask", "147", "192"}};
        for (String[] r : a) {
            ASPECT.put(r[0], Float.parseFloat(r[1]) / Float.parseFloat(r[2]));
        }
    }

    private enum Mode { BILLBOARD, FLAT, STRETCH }

    public static final class Inst {
        String atlas;
        Mode mode;
        Vec3 pos;
        Vec3 normal;
        Vec3 end;
        int follow = -1;
        Vec3 offset = Vec3.ZERO;
        float size;
        float life;
        float age;
        int rgb = 0xFFFFFF;
        float alpha = 1f;
        float spin;
        boolean reverse;
        float loop;
        int boost = 1;

        /** Dibuja la capa aditiva n veces (para hojas tenues como el estallido violeta o las chispas). */
        public Inst boost(int n) {
            this.boost = Math.max(1, n);
            return this;
        }

        /** Repite la animacion a 'fps' cuadros por tick en vez de reproducirla una vez en toda la vida. */
        public Inst loop(float fps) {
            this.loop = fps;
            return this;
        }
    }

    private static final List<Inst> LIVE = new ArrayList<>();

    private FlipbookFx() {
    }

    private static Inst make(String atlas, Mode mode, Vec3 pos, float size, int life, int rgb, float alpha) {
        Inst i = new Inst();
        i.atlas = atlas;
        i.mode = mode;
        i.pos = pos;
        i.size = size;
        i.life = Math.max(2, life);
        i.rgb = rgb;
        i.alpha = alpha;
        LIVE.add(i);
        return i;
    }

    /** Cuadro animado mirando a la camara. size = mitad del alto en bloques. */
    public static Inst billboard(String atlas, Vec3 pos, float size, int life, int rgb, float alpha) {
        return make(atlas, Mode.BILLBOARD, pos, size, life, rgb, alpha);
    }

    /** Igual, pero pegado a una entidad (se mueve con ella). */
    public static Inst billboardOn(Entity e, Vec3 offset, String atlas, float size, int life, int rgb, float alpha) {
        Inst i = make(atlas, Mode.BILLBOARD, e.position().add(offset), size, life, rgb, alpha);
        i.follow = e.getId();
        i.offset = offset;
        return i;
    }

    /** Plano con la normal dada (ondas de suelo, anillos). spin en grados por tick. reverse = se reproduce al reves (implosion). */
    public static Inst flat(String atlas, Vec3 pos, Vec3 normal, float size, int life, int rgb, float alpha, float spin, boolean reverse) {
        Inst i = make(atlas, Mode.FLAT, pos, size, life, rgb, alpha);
        i.normal = normal.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : normal.normalize();
        i.spin = spin;
        i.reverse = reverse;
        return i;
    }

    /** Estirado entre dos puntos, de cara a la camara alrededor del eje (rayos, chorros). width = medio ancho. */
    public static Inst stretch(String atlas, Vec3 from, Vec3 to, float width, int life, int rgb, float alpha) {
        Inst i = make(atlas, Mode.STRETCH, from, width, life, rgb, alpha);
        i.end = to;
        return i;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            LIVE.clear();
            return;
        }
        if (mc.isPaused()) return;
        Iterator<Inst> it = LIVE.iterator();
        while (it.hasNext()) {
            Inst i = it.next();
            if (++i.age >= i.life) {
                it.remove();
            } else if (i.follow >= 0) {
                Entity e = mc.level.getEntity(i.follow);
                if (e != null) i.pos = e.position().add(i.offset);
            }
        }
        if (LIVE.size() > 600) {
            LIVE.subList(0, LIVE.size() - 600).clear();
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || LIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        org.joml.Vector3f left = event.getCamera().getLeftVector();
        org.joml.Vector3f up = event.getCamera().getUpVector();
        PoseStack pose = event.getPoseStack() != null ? event.getPoseStack() : new PoseStack();
        PoseStack.Pose last = pose.last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        // agrupado por hoja para no cambiar de textura en cada cuadro
        Map<String, List<Inst>> byAtlas = new HashMap<>();
        for (Inst i : LIVE) byAtlas.computeIfAbsent(i.atlas, k -> new ArrayList<>()).add(i);
        for (Map.Entry<String, List<Inst>> en : byAtlas.entrySet()) {
            String atlas = en.getKey();
            boolean smoke = SMOKE.contains(atlas);
            ResourceLocation tex = ResourceLocation.fromNamespaceAndPath(WipsPowers.MOD_ID, "textures/fx/fb/" + atlas + ".png");
            RenderType type = smoke ? RenderType.entityTranslucentEmissive(tex) : RenderType.eyes(tex);
            VertexConsumer vc = buffers.getBuffer(type);
            float aspect = ASPECT.getOrDefault(atlas, 1f);
            for (Inst i : en.getValue()) {
                float t = Mth.clamp((i.age + pt) / i.life, 0f, 0.9999f);
                int frame = i.loop > 0 ? (int) ((i.age + pt) * i.loop) % FRAMES : (int) ((i.reverse ? 1f - t : t) * FRAMES);
                frame = Mth.clamp(frame, 0, FRAMES - 1);
                float u0 = (frame % COLS) / (float) COLS, v0 = (frame / COLS) / (float) COLS;
                float u1 = u0 + 1f / COLS, v1 = v0 + 1f / COLS;
                // entra y sale suave
                float fade = Math.min(1f, Math.min(t * 8f, (1f - t) * 5f));
                float a = i.alpha * fade;
                float r = (i.rgb >> 16 & 255) / 255f, g = (i.rgb >> 8 & 255) / 255f, b = (i.rgb & 255) / 255f;
                if (!smoke) { r *= a; g *= a; b *= a; }
                float va = smoke ? a : 1f;
                double x = i.pos.x - cam.x, y = i.pos.y - cam.y, z = i.pos.z - cam.z;
                for (int pass = 0; pass < (smoke ? 1 : i.boost); pass++)
                switch (i.mode) {
                    case BILLBOARD -> {
                        float hh = i.size, hw = i.size * aspect;
                        Vec3 L = new Vec3(left.x() * hw, left.y() * hw, left.z() * hw);
                        Vec3 U = new Vec3(up.x() * hh, up.y() * hh, up.z() * hh);
                        quad(vc, last, x, y, z, L, U, u0, v0, u1, v1, r, g, b, va);
                    }
                    case FLAT -> {
                        Vec3[] bs = ElementalFx.basis(i.normal);
                        double ang = Math.toRadians(i.spin * (i.age + pt));
                        Vec3 a1 = bs[0].scale(Math.cos(ang)).add(bs[1].scale(Math.sin(ang)));
                        Vec3 a2 = i.normal.cross(a1);
                        Vec3 L = a1.scale(i.size * aspect);
                        Vec3 U = a2.scale(i.size);
                        quad(vc, last, x, y, z, L, U, u0, v0, u1, v1, r, g, b, va);
                    }
                    case STRETCH -> {
                        Vec3 from = i.pos, to = i.end;
                        Vec3 mid = from.add(to).scale(0.5);
                        Vec3 axis = to.subtract(from);
                        Vec3 toCam = cam.subtract(mid);
                        Vec3 side = axis.cross(toCam);
                        if (side.lengthSqr() < 1e-6) side = new Vec3(0, 1, 0);
                        side = side.normalize().scale(i.size);
                        Vec3 half = axis.scale(0.5);
                        double mx = mid.x - cam.x, my = mid.y - cam.y, mz = mid.z - cam.z;
                        // el eje largo del atlas va a lo largo del rayo (en hojas verticales, la parte de arriba hacia 'to')
                        if (aspect >= 1f) quad(vc, last, mx, my, mz, half, side, u0, v0, u1, v1, r, g, b, va);
                        else quad(vc, last, mx, my, mz, side, half, u0, v0, u1, v1, r, g, b, va);
                    }
                }
            }
            buffers.endBatch(type);
        }
    }

    /** Cuadrado centrado en (x,y,z) con medio-ejes L (horizontal de la textura) y U (vertical). */
    private static void quad(VertexConsumer vc, PoseStack.Pose pose, double x, double y, double z, Vec3 L, Vec3 U,
                             float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
        v(vc, pose, x - L.x + U.x, y - L.y + U.y, z - L.z + U.z, u0, v0, r, g, b, a);
        v(vc, pose, x - L.x - U.x, y - L.y - U.y, z - L.z - U.z, u0, v1, r, g, b, a);
        v(vc, pose, x + L.x - U.x, y + L.y - U.y, z + L.z - U.z, u1, v1, r, g, b, a);
        v(vc, pose, x + L.x + U.x, y + L.y + U.y, z + L.z + U.z, u1, v0, r, g, b, a);
        // cara de atras: RenderType.eyes descarta las caras traseras y el billboard/rayo quedaba invisible
        v(vc, pose, x + L.x + U.x, y + L.y + U.y, z + L.z + U.z, u1, v0, r, g, b, a);
        v(vc, pose, x + L.x - U.x, y + L.y - U.y, z + L.z - U.z, u1, v1, r, g, b, a);
        v(vc, pose, x - L.x - U.x, y - L.y - U.y, z - L.z - U.z, u0, v1, r, g, b, a);
        v(vc, pose, x - L.x + U.x, y - L.y + U.y, z - L.z + U.z, u0, v0, r, g, b, a);
    }

    private static void v(VertexConsumer vc, PoseStack.Pose pose, double x, double y, double z, float u, float w,
                          float r, float g, float b, float a) {
        vc.addVertex(pose, (float) x, (float) y, (float) z).setColor(r, g, b, a).setUv(u, w)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0f, 1f, 0f);
    }
}
