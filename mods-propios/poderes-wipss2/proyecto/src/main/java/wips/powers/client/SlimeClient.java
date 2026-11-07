package wips.powers.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import wips.powers.kit.WipsEntities;
import wips.powers.slime.SlimePower;

/**
 * Cliente del Slime: render de mini slimes, gotas y proyectiles; la Pelota de slime (otros jugadores la ven como un
 * slime grande que rebota) y su fisica elastica para el propio jugador: rebota al caer y contra paredes, conserva
 * el impulso al rodar y salta mas alto aprovechandolo. La caja de colision del jugador no cambia, asi nunca
 * atraviesa bloques ni queda trabado.
 */
public final class SlimeClient {
    private static final Set<Integer> BALLS = new HashSet<>();
    private static final Map<Integer, Slime> LOOKS = new HashMap<>();
    private static Vec3 prevVel = Vec3.ZERO;
    private static boolean prevGround = true;

    private SlimeClient() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SlimeClient::tick);
        NeoForge.EVENT_BUS.addListener(SlimeClient::renderPlayer);
        NeoForge.EVENT_BUS.addListener(SlimeClient::renderHand);
    }

    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(WipsEntities.MINI_SLIME.get(), SlimeRenderer::new);
        event.registerEntityRenderer(WipsEntities.SLIME_GLOB.get(), ctx -> new ThrownItemRenderer<>(ctx, 1.6F, false));
        event.registerEntityRenderer(WipsEntities.SLIME_SHOT.get(), ctx -> new ThrownItemRenderer<>(ctx, 1.0F, false));
    }

    public static void form(SlimePower.BallForm.Form f) {
        if (f.on()) {
            BALLS.add(f.entity());
        } else {
            BALLS.remove(f.entity());
            LOOKS.remove(f.entity());
        }
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            BALLS.clear();
            LOOKS.clear();
            return;
        }
        if (!BALLS.contains(p.getId())) {
            prevVel = p.getDeltaMovement();
            prevGround = p.onGround();
            return;
        }
        Vec3 v = p.getDeltaMovement();
        double vx = v.x;
        double vy = v.y;
        double vz = v.z;
        // rebote contra paredes: la componente que choco se invierte y amortigua
        if (p.horizontalCollision) {
            if (Math.abs(vx) < 0.02 && Math.abs(prevVel.x) > 0.08) {
                vx = -prevVel.x * 0.85;
            }
            if (Math.abs(vz) < 0.02 && Math.abs(prevVel.z) > 0.08) {
                vz = -prevVel.z * 0.85;
            }
        }
        // rebote contra el suelo
        if (p.onGround() && !prevGround && prevVel.y < -0.28) {
            vy = Math.min(1.4, -prevVel.y * 0.8);
        }
        // salto con impulso
        if (prevGround && !p.onGround() && vy > 0.3 && vy < 0.6) {
            vy *= 1.45;
            vx *= 1.2;
            vz *= 1.2;
        }
        // rodar: conserva el impulso horizontal (sin parar en seco)
        double h = Math.sqrt(vx * vx + vz * vz);
        double ph = Math.sqrt(prevVel.x * prevVel.x + prevVel.z * prevVel.z);
        if (p.onGround() && h < ph * 0.93 && ph > 0.05 && vx * prevVel.x + vz * prevVel.z > 0) {
            double k = ph * 0.93 / Math.max(h, 1.0E-4);
            vx *= k;
            vz *= k;
        }
        Vec3 out = new Vec3(vx, vy, vz);
        if (!out.equals(v)) {
            p.setDeltaMovement(out);
        }
        prevVel = out;
        prevGround = p.onGround();
    }

    @SuppressWarnings("unchecked")
    private static void renderPlayer(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        if (!BALLS.contains(p.getId())) {
            return;
        }
        event.setCanceled(true);
        Slime look = LOOKS.computeIfAbsent(p.getId(), id -> {
            Slime s = new Slime(EntityType.SLIME, p.level());
            s.setSize(4, false);
            return s;
        });
        float partial = event.getPartialTick();
        look.setPos(p.getX(), p.getY(), p.getZ());
        look.tickCount = p.tickCount;
        look.yBodyRot = look.yBodyRotO = p.getYRot();
        look.setYRot(p.getYRot());
        double vy = p.getDeltaMovement().y;
        look.oSquish = look.squish;
        look.squish = (float) Math.max(-0.6, Math.min(0.6, vy * 1.4));
        EntityRenderer<Slime> r = (EntityRenderer<Slime>) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(look);
        event.getPoseStack().pushPose();
        r.render(look, p.getYRot(), partial, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
        event.getPoseStack().popPose();
    }

    private static void renderHand(RenderHandEvent event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && BALLS.contains(p.getId())) {
            event.setCanceled(true);
        }
    }
}
