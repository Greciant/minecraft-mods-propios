package wips.powers.compat;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import wips.powers.WipsPowers;

/** Solo para runAudit: que hechizos de Hyper 'N Hiei y Zoltraak quedan habilitados. */
public final class AuditSpells {
    private AuditSpells() {
    }

    public static void log() {
        SpellRegistry.REGISTRY.stream()
                .filter(s -> s.getSpellResource().getNamespace().startsWith("hyper") || s.getSpellResource().getNamespace().startsWith("zoltraak"))
                .forEach(s -> WipsPowers.LOGGER.info("[wipspowers] AUDIT hechizo {} habilitado={}", s.getSpellResource(), s.isEnabled()));
    }
}
