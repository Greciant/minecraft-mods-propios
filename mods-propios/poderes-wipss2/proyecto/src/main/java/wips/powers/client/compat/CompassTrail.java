package wips.powers.client.compat;

import dev.lodestartrail.common.Destination;
import dev.lodestartrail.common.Place;
import dev.lodestartrail.common.Point;
import dev.lodestartrail.neoforge.TrailClient;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import wips.powers.WipsPowers;

/**
 * Lodestar Trail + Explorer's Compass / Nature's Compass: cuando la brujula que tenes en la mano encuentra una
 * estructura o un bioma, Lodestar arranca solo a guiarte hasta ahi (estela de particulas, hada y flecha del HUD).
 * Solo cliente; cada hallazgo se guia una vez por sesion (si buscas otra cosa, cambia el destino).
 */
public final class CompassTrail {
    private static final java.util.Set<String> GUIDED = new java.util.HashSet<>();
    private static Field clientField;
    private static boolean explorers;
    private static boolean natures;

    private CompassTrail() {
    }

    public static void register() {
        explorers = ModList.get().isLoaded("explorerscompass");
        natures = ModList.get().isLoaded("naturescompass");
        if (explorers || natures) {
            NeoForge.EVENT_BUS.addListener(CompassTrail::tick);
        }
    }

    private static TrailClient trail() {
        try {
            if (clientField == null) {
                clientField = Class.forName("dev.lodestartrail.neoforge.LodestarTrail").getDeclaredField("client");
                clientField.setAccessible(true);
            }
            return (TrailClient) clientField.get(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private record Found(String key, int x, int z, boolean structure) {
        String signature() {
            return key + "@" + x + "," + z;
        }
    }

    private static Found read(ItemStack stack) {
        if (explorers && stack.getItem() instanceof com.chaosthedude.explorerscompass.items.ExplorersCompassItem c
                && c.getState(stack) == com.chaosthedude.explorerscompass.util.CompassState.FOUND) {
            ResourceLocation key = c.getStructureKey(stack);
            return new Found(key == null ? "?" : key.toString(), c.getFoundStructureX(stack), c.getFoundStructureZ(stack), true);
        }
        if (natures && stack.getItem() instanceof com.chaosthedude.naturescompass.items.NaturesCompassItem c
                && c.getState(stack) == com.chaosthedude.naturescompass.util.CompassState.FOUND) {
            ResourceLocation key = c.getBiomeKey(stack);
            return new Found(key == null ? "?" : key.toString(), c.getFoundBiomeX(stack), c.getFoundBiomeZ(stack), false);
        }
        return null;
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            GUIDED.clear();
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            Found f = read(player.getItemInHand(hand));
            if (f == null || !GUIDED.add(f.signature())) {
                continue; // sin hallazgo, o ese hallazgo ya se guio en esta sesion
            }
            if (player.tickCount > 40) { // no reacciona a brujulas viejas al entrar al mundo
                guide(mc, player, f);
            }
        }
    }

    private static void guide(Minecraft mc, LocalPlayer player, Found f) {
        TrailClient trail = trail();
        if (trail == null || trail.book() == null) {
            return;
        }
        try {
            String name = pretty(f.key());
            Place place = new Place(
                    new Destination(mc.level.dimension().location().toString(), new Point(f.x() + 0.5, player.getY(), f.z() + 0.5)),
                    name.length() > 48 ? name.substring(0, 48) : name,
                    f.structure() ? "explorerscompass:explorerscompass" : "naturescompass:naturescompass",
                    f.structure() ? 0xE0B040 : 0x4CC060);
            trail.guide(place);
            player.displayClientMessage(Component.translatable("message.wipspowers.compass_trail", name), true);
        } catch (RuntimeException e) {
            WipsPowers.LOGGER.warn("[wipspowers] No se pudo guiar con Lodestar a {}: {}", f.key(), e.toString());
        }
    }

    /** minecraft:village_plains -> Village Plains */
    private static String pretty(String key) {
        String path = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
        path = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder sb = new StringBuilder();
        for (String w : path.split("_")) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
    }
}
