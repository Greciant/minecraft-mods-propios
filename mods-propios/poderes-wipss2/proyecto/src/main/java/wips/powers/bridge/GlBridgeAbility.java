package wips.powers.bridge;

import com.awakenedmod.awakened.core.PowerService;
import com.awakenedmod.awakened.core.power.Ability;
import com.devilfruits.core.ability.AbilityContext;
import com.devilfruits.core.ability.CastType;
import com.devilfruits.core.data.PlayerFruitData;
import com.devilfruits.core.form.FormAbility;
import com.devilfruits.core.fx.ServerFx;
import wips.powers.CastSigil;
import wips.powers.FormFx;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * Habilidad de Awakened que ejecuta por dentro una habilidad de Devil Fruit: Grand Line sin tocar
 * la forma del jugador: se fabrica un contexto propio y se llama a activate/release/channel a mano.
 */
public abstract class GlBridgeAbility extends Ability {
    private static final int DEBRIS_REMOVED = 26;

    private record Run(AbilityContext ctx, long start) {}

    private final FormAbility entry;
    private final String langName;
    private final String visualForm;
    private final Map<UUID, Run> runs = this.activeState();
    /** Contextos creados por wipspowers (no por la fruta): los mixins los usan para ajustar solo nuestras tecnicas. */
    public static final java.util.Set<AbilityContext> OURS = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    protected GlBridgeAbility(String id, float stamina, int unlock, FormAbility entry, String langName) {
        this(id, stamina, unlock, entry, langName, null);
    }

    /** visualForm: forma ("hybrid"/"dragon") cuyos efectos queremos ver, sin transformar. */
    protected GlBridgeAbility(String id, float stamina, int unlock, FormAbility entry, String langName, String visualForm) {
        super(id, stamina, unlock);
        this.entry = entry;
        this.langName = langName;
        this.visualForm = visualForm;
    }

    private void showForm(ServerPlayer player, int ticks) {
        if (this.visualForm != null) {
            FormFx.send(player, this.visualForm, ticks);
        }
    }

    private AbilityContext context(ServerPlayer player, float charge) {
        PlayerFruitData data = new PlayerFruitData();
        int level = PowerService.data(player).masteryLevel();
        data.setMastery(Mth.clamp(1 + level / 2, 1, 10));
        AbilityContext ctx = new AbilityContext(this.entry, player, data, charge);
        synchronized (OURS) {
            OURS.add(ctx);
        }
        // menos escombros volando que con la fruta (40 por lanzamiento): ensuciaban la camara
        for (int i = 0; i < DEBRIS_REMOVED && ctx.budget().takeDebris(); i++) {
        }
        return ctx;
    }

    /** Enfriamiento fijo en ticks; -1 = el de Grand Line. */
    protected int fixedCooldown() {
        return -1;
    }

    /** Ticks de carga completa; por defecto los de Grand Line. */
    protected int chargeTicks() {
        return this.entry.ability().maxChargeTicks();
    }

    private int cooldownTicks(float charge) {
        int fixed = this.fixedCooldown();
        if (fixed >= 0) {
            return fixed;
        }
        return Math.round(this.entry.ability().cooldownTicks(charge) * this.entry.modifiers().cooldown());
    }

    @Override
    public boolean activate(ServerPlayer player) {
        com.devilfruits.core.ability.Ability gl = this.entry.ability();
        CastType type = gl.castType();
        long now = player.serverLevel().getGameTime();
        AbilityContext ctx = this.context(player, type == CastType.INSTANT ? 1.0F : 0.0F);
        int window = switch (type) {
            case CHARGED -> this.chargeTicks() + 40 + 160;
            case HELD -> gl.maxChannelTicks() + 160;
            default -> 160;
        };
        this.showForm(player, window); // antes que los efectos, para que el cliente ya la tenga
        if (type == CastType.CHARGED || type == CastType.HELD) {
            CastSigil.send(player, this.id(), window, false);
        } else {
            CastSigil.send(player, this.id(), 40, true);
        }
        switch (type) {
            case CHARGED -> {
                this.runs.put(player.getUUID(), new Run(ctx, now));
                gl.onChargeStart(ctx);
                ServerFx.chargeStart(player, gl, (float) this.chargeTicks(), ctx.seed());
            }
            case HELD -> {
                this.runs.put(player.getUUID(), new Run(this.context(player, 1.0F), now));
                Run run = this.runs.get(player.getUUID());
                gl.onChannelStart(run.ctx());
                ServerFx.chargeStart(player, gl, (float) gl.maxChannelTicks(), run.ctx().seed());
            }
            default -> {
                gl.activate(this.context(player, 1.0F));
                this.startCooldown(player, this.cooldownTicks(1.0F));
            }
        }
        return true;
    }

    @Override
    public void serverTick(ServerPlayer player) {
        Run run = this.runs.get(player.getUUID());
        if (run == null) {
            return;
        }
        com.devilfruits.core.ability.Ability gl = this.entry.ability();
        int ticks = (int) (player.serverLevel().getGameTime() - run.start());
        if (!player.isAlive()) {
            this.runs.remove(player.getUUID());
            return;
        }
        if (gl.castType() == CastType.CHARGED) {
            gl.onChargeTick(run.ctx(), ticks);
            if (ticks >= this.chargeTicks() + 40) {
                this.finish(player, false);
            }
        } else if (gl.castType() == CastType.HELD) {
            if (!gl.channelTick(run.ctx(), ticks) || ticks >= gl.maxChannelTicks()) {
                this.finish(player, false);
            }
        }
    }

    @Override
    public void onRelease(ServerPlayer player) {
        Run run = this.runs.get(player.getUUID());
        if (run == null) {
            return;
        }
        com.devilfruits.core.ability.Ability gl = this.entry.ability();
        int ticks = (int) (player.serverLevel().getGameTime() - run.start());
        if (gl.castType() == CastType.HELD && ticks < gl.minChannelTicks()) {
            return; // se corta sola al llegar al minimo (serverTick no, asi que esperamos al siguiente release)
        }
        this.finish(player, true);
    }

    private void finish(ServerPlayer player, boolean released) {
        Run run = this.runs.remove(player.getUUID());
        if (run == null) {
            return;
        }
        com.devilfruits.core.ability.Ability gl = this.entry.ability();
        long now = player.serverLevel().getGameTime();
        int ticks = (int) (now - run.start());
        this.showForm(player, 160);
        CastSigil.send(player, this.id(), 16, true);
        if (gl.castType() == CastType.CHARGED) {
            float charge = Mth.clamp((float) ticks / (float) this.chargeTicks(), 0.0F, 1.0F);
            gl.release(this.context(player, charge));
            ServerFx.chargeStop(player, gl, true);
            this.startCooldown(player, this.cooldownTicks(charge));
        } else {
            gl.onChannelEnd(run.ctx(), ticks, !released);
            ServerFx.chargeStop(player, gl, released);
            this.startCooldown(player, this.cooldownTicks(1.0F));
        }
    }

    @Override
    public void clearPlayer(ServerPlayer player) {
        Run run = this.runs.remove(player.getUUID());
        if (run != null && this.entry.ability().castType() == CastType.CHARGED) {
            this.entry.ability().onChargeCancel(player);
        }
        super.clearPlayer(player);
    }

    @Override
    public String activationAnimation(ServerPlayer player) {
        return null; // la animacion la manda Grand Line (ServerFx.animate) dentro de la habilidad
    }

    @Override
    public Component displayName() {
        return Component.translatable("ability.wipspowers." + this.langName);
    }

    @Override
    public Component description() {
        return Component.translatable("ability.wipspowers." + this.langName + ".desc");
    }
}
