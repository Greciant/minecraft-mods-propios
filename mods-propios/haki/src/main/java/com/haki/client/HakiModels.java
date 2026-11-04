package com.haki.client;

import com.haki.HakiMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * Modelos 3D de los efectos (rocas, pinchos de obsidiana, corona), hechos con tools/gen_models.py y editables en
 * Blockbench (art/*.bbmodel). Se registran como modelos adicionales y se dibujan quad a quad, a todo brillo
 * (emisivos) y con un sombreado por cara para que se lea el volumen.
 */
@EventBusSubscriber(modid = HakiMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HakiModels {
    private HakiModels() {}

    public static final int ROCKS = 4, SHARDS = 3;
    public static final int ROCK0 = 0, SHARD0 = ROCKS, CROWN = ROCKS + SHARDS;
    private static final String[] NAMES = {"rock_0", "rock_1", "rock_2", "rock_3", "shard_0", "shard_1", "shard_2", "crown"};
    private static final ModelResourceLocation[] MRL = new ModelResourceLocation[NAMES.length];
    static {
        for (int i = 0; i < NAMES.length; i++)
            MRL[i] = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(HakiMod.MODID, "fx/" + NAMES[i]));
    }
    /** true si el origen del modelo esta en el centro del bloque (rocas); false si esta en la base (pinchos, corona). */
    static boolean centered(int model) { return model < ROCKS; }

    private static final RandomSource RNG = RandomSource.create(0);
    // Sombreado fijo por cara (arriba, abajo, N, S, O, E): da relieve sin depender de la luz del mundo
    private static final float[] SHADE = {0.5f, 1.0f, 0.78f, 0.78f, 0.62f, 0.62f};

    @SubscribeEvent
    public static void register(ModelEvent.RegisterAdditional event) {
        for (ModelResourceLocation m : MRL) event.register(m);
    }

    /** Quads de cada modelo con su sombreado ya calculado (se rehace al recargar recursos). */
    private static final java.util.List<net.minecraft.client.renderer.block.model.BakedQuad>[] QUADS = newCache();
    private static final float[][] QSHADE = new float[NAMES.length][];

    @SuppressWarnings("unchecked")
    private static java.util.List<net.minecraft.client.renderer.block.model.BakedQuad>[] newCache() {
        return new java.util.List[NAMES.length];
    }

    @SubscribeEvent
    public static void baked(ModelEvent.BakingCompleted event) {
        java.util.Arrays.fill(QUADS, null);
    }

    private static void build(int model) {
        BakedModel m = Minecraft.getInstance().getModelManager().getModel(MRL[model]);
        java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> all = new java.util.ArrayList<>();
        for (Direction d : Direction.values()) all.addAll(m.getQuads(null, d, RNG));
        all.addAll(m.getQuads(null, null, RNG));
        float[] sh = new float[all.size()];
        for (int i = 0; i < sh.length; i++) sh[i] = SHADE[all.get(i).getDirection().ordinal()];
        QSHADE[model] = sh;
        QUADS[model] = all;
    }

    static void draw(VertexConsumer vc, PoseStack.Pose pose, int model, float alpha, float tint) {
        if (QUADS[model] == null) build(model);
        java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> qs = QUADS[model];
        float[] sh = QSHADE[model];
        for (int i = 0; i < sh.length; i++) {
            float s = sh[i] * tint;
            vc.putBulkData(pose, qs.get(i), s, s, s, alpha, HakiFxRender.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
    }
}
