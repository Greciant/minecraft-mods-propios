package wips.powers.client;

import com.awakenedmod.awakened.content.AwakenedEntities;
import com.awakenedmod.awakened.content.pyrokinetic.FlameTone;
import com.awakenedmod.awakened.content.pyrokinetic.PyroHeatState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import wips.powers.WipsPowers;

/**
 * Estelas animadas de los proyectiles de Awakened (bola de fuego y lanza de agua), leidas del propio
 * proyectil sin paquetes. Los efectos de cada habilidad estan en AbilityFx.
 */
@EventBusSubscriber(modid = WipsPowers.MOD_ID, value = Dist.CLIENT)
public final class SampleFx {
    private SampleFx() {
    }

    private static boolean hot(Entity e) {
        try {
            return e != null && FlameTone.forHeat(PyroHeatState.visualHeat(e)).blueFraction() >= 0.5F;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused() || level.getGameTime() % 3 != 0) return;
        EntityType<?> fire = AwakenedEntities.PYRO_FIREBALL.get();
        EntityType<?> lance = AwakenedEntities.HYDRO_LANCE.get();
        for (Entity e : level.entitiesForRendering()) {
            if (e.getType() == fire) {
                boolean blue = e instanceof net.minecraft.world.entity.projectile.Projectile pr && hot(pr.getOwner());
                FlipbookFx.billboard(blue ? "sorcerer_flame" : "burst_fire", e.position().add(0, e.getBbHeight() * 0.5, 0), 0.75f, 9, 0xFFFFFF, 0.85f);
            } else if (e.getType() == lance) {
                FlipbookFx.billboard("bubbles", e.position(), 0.6f, 12, 0xCFEFFF, 0.8f);
            }
        }
    }
}
