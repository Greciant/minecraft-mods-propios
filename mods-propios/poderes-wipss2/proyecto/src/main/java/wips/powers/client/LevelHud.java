package wips.powers.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import wips.powers.WipsPowers;
import wips.powers.level.WipsLevel;

/** HUD del nivel de jugador: "Nv 12" y una barra fina de XP arriba a la izquierda. */
public final class LevelHud {
    private static int level = 1;
    private static float xp;
    private static float need = 1;
    private static int max = 50;
    private static float grit;

    public static void grit(wips.powers.fighter.FighterPower.GritSync s) {
        grit = s.value();
    }

    private LevelHud() {
    }

    public static void receive(WipsLevel.Sync s) {
        level = s.level();
        xp = s.xp();
        need = Math.max(1, s.need());
        max = s.max();
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_BAR, ResourceLocation.fromNamespaceAndPath(WipsPowers.MOD_ID, "level"), LevelHud::render);
    }

    private static void render(GuiGraphics g, net.minecraft.client.DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.player.isSpectator()) {
            return;
        }
        int x = 4;
        int y = 4;
        Component label = Component.translatable("hud.wipspowers.level", level);
        g.drawString(mc.font, label, x, y, 0x7FE3FF, true);
        int w = 60;
        int by = y + 10;
        float f = level >= max ? 1f : Math.min(1f, xp / need);
        g.fill(x, by, x + w, by + 3, 0xAA000000);
        g.fill(x, by, x + Math.round(w * f), by + 3, level >= max ? 0xFFFFD24A : 0xFF4FC3F7);
        if (grit > 0.001F) {
            int gy = by + 6;
            g.drawString(mc.font, Component.translatable("hud.wipspowers.grit", Math.round(grit * 100)), x, gy, 0xFFB070, true);
            g.fill(x, gy + 10, x + w, gy + 14, 0xAA000000);
            g.fill(x, gy + 10, x + Math.round(w * grit), gy + 14, grit > 0.66F ? 0xFFFF4A1A : 0xFFFF9A3A);
        }
    }
}
