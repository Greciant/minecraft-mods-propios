package dev.grec.packtest;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod de pruebas automaticas. Solo cliente. Sin -Dpacktest.script=<ruta> no registra nada.
 */
@Mod(PackTest.MODID)
public final class PackTest {
    public static final String MODID = "packtest";
    public static final String LOGGER_NAME = "packtest";
    public static final Logger LOG = LoggerFactory.getLogger(LOGGER_NAME);

    public PackTest() {
        String script = System.getProperty("packtest.script");
        if (script == null || script.isBlank()) return;
        if (FMLEnvironment.dist != Dist.CLIENT) {
            LOG.warn("[packtest] packtest.script definido pero esto no es un cliente; no hago nada");
            return;
        }
        ClientRunner.start(script.trim());
    }
}
