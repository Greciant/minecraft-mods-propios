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
    /** Llegada horizontal: las estructuras/biomas no tienen altura fija, Lodestar mide en 3D (radio ~3). */
    private static final double ARRIVE_H = 6.0;
    private static final double SURFACE_FIX_H = 48.0;
    /** Guia en curso iniciada por una brujula (null si no hay o el jugador cambio de destino). */
    private static Place active;
    private static boolean surfaceFixed;
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
            active = null;
            return;
        }
        follow(mc, player);
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
                    new Destination(mc.level.dimension().location().toString(), new Point(f.x() + 0.5, surface(mc, f.x(), f.z(), player.getY()), f.z() + 0.5)),
                    name.length() > 48 ? name.substring(0, 48) : name,
                    f.structure() ? "explorerscompass:explorerscompass" : "naturescompass:naturescompass",
                    f.structure() ? 0xE0B040 : 0x4CC060);
            trail.guide(place);
            active = place;
            surfaceFixed = false;
            player.displayClientMessage(Component.translatable("message.wipspowers.compass_trail", name), true);
        } catch (RuntimeException e) {
            WipsPowers.LOGGER.warn("[wipspowers] No se pudo guiar con Lodestar a {}: {}", f.key(), e.toString());
        }
    }

    private static double surface(Minecraft mc, int x, int z, double fallback) {
        if (mc.level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
            return mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        }
        return fallback;
    }

    /**
     * Arreglo del hada que no desaparecia: el destino se fijaba con la altura del jugador al encontrar la estructura
     * y Lodestar solo da por llegado en 3D, asi que si el lugar estaba a otra altura nunca "llegaba". Ahora:
     * 1) al acercarse (chunk cargado) el destino se corrige a la altura real del terreno;
     * 2) al estar encima en horizontal, el destino se pone a la altura del jugador y Lodestar hace su llegada
     *    normal (efecto de llegada, el hada se va y el rastro se borra).
     */
    private static void follow(Minecraft mc, LocalPlayer player) {
        if (active == null) {
            return;
        }
        TrailClient trail = trail();
        if (trail == null || trail.book() == null || !active.equals(trail.book().activePlace())) {
            active = null; // el jugador eligio otro destino o la guia termino
            return;
        }
        Destination d = active.destination();
        if (!d.inDimension(mc.level.dimension().location().toString())) {
            return;
        }
        double dx = d.point().x() - player.getX();
        double dz = d.point().z() - player.getZ();
        double h = Math.sqrt(dx * dx + dz * dz);
        Place next = null;
        if (h <= ARRIVE_H) {
            next = new Place(new Destination(d.dimension(), new Point(d.point().x(), player.getY(), d.point().z())), active.name(), active.icon(), active.color());
        } else if (!surfaceFixed && h <= SURFACE_FIX_H) {
            int x = (int) Math.floor(d.point().x());
            int z = (int) Math.floor(d.point().z());
            if (mc.level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                surfaceFixed = true;
                double y = surface(mc, x, z, d.point().y());
                if (Math.abs(y - d.point().y()) > 1.0) {
                    next = new Place(new Destination(d.dimension(), new Point(d.point().x(), y, d.point().z())), active.name(), active.icon(), active.color());
                }
            }
        }
        if (next != null) {
            try {
                trail.guide(next);
                active = h <= ARRIVE_H ? null : next; // al llegar, Lodestar sigue solo (efecto de llegada y fin)
            } catch (RuntimeException e) {
                active = null;
            }
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
