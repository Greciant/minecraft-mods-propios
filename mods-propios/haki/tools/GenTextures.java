import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Genera las texturas de efectos del Haki (sin editor de imagenes).
 * Uso (JDK 21):  java tools/GenTextures.java src/main/resources/assets/haki/textures/fx
 * La semilla es fija: volver a ejecutarlo produce exactamente los mismos PNG.
 */
public class GenTextures {

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "src/main/resources/assets/haki/textures/fx");
        out.mkdirs();
        ImageIO.write(bolt(), "png", new File(out, "bolt.png"));
        ImageIO.write(impactStar(), "png", new File(out, "impact_star.png"));
        ImageIO.write(vignette(), "png", new File(out, "vignette.png"));
        ImageIO.write(eyeGlow(), "png", new File(out, "eye_glow.png"));
        ImageIO.write(dome(), "png", new File(out, "dome.png"));
        ImageIO.write(streaks(), "png", new File(out, "streaks.png"));
        ImageIO.write(boltCore(), "png", new File(out, "bolt_core.png"));
        ImageIO.write(boltGlow(), "png", new File(out, "bolt_glow.png"));
        ImageIO.write(spark(), "png", new File(out, "spark.png"));
        System.out.println("Texturas escritas en " + out.getAbsolutePath());
    }

    private static int argb(double a, double r, double g, double b) {
        int ia = clamp(a), ir = clamp(r), ig = clamp(g), ib = clamp(b);
        return ia << 24 | ir << 16 | ig << 8 | ib;
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v * 255)));
    }

    /** Rayo: u = largo (64 px, se repite), v = ancho (16 px). Nucleo negro, borde rojo, chispas blancas. */
    static BufferedImage bolt() {
        int w = 64, h = 16;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Random rnd = new Random(1234);
        // grosor del nucleo variable a lo largo (bordes dentados)
        double[] core = new double[w];
        for (int x = 0; x < w; x++) core[x] = 0.30 + 0.18 * rnd.nextDouble();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double d = Math.abs((y + 0.5) - h / 2.0) / (h / 2.0); // 0 centro .. 1 borde
                double c = core[x];
                int px;
                if (d < c) {
                    px = argb(1, 0.02, 0, 0);                       // nucleo negro
                } else if (d < c + 0.22) {
                    double t = (d - c) / 0.22;                      // borde rojo brillante
                    px = argb(1, 1.0, 0.25 * (1 - t), 0.12 * (1 - t));
                } else {
                    double t = (d - c - 0.22) / Math.max(0.01, 1 - c - 0.22);
                    double a = Math.pow(1 - Math.min(1, t), 2) * 0.85; // halo rojo que se desvanece
                    px = argb(a, 0.9, 0.05, 0.02);
                }
                img.setRGB(x, y, px);
            }
        }
        // chispas blancas en el borde
        for (int i = 0; i < 7; i++) {
            int x = rnd.nextInt(w);
            int y = rnd.nextBoolean() ? 3 + rnd.nextInt(2) : h - 5 + rnd.nextInt(2);
            img.setRGB(x, y, argb(1, 1, 1, 1));
            if (x + 1 < w) img.setRGB(x + 1, y, argb(0.8, 1, 0.9, 0.9));
        }
        return img;
    }

    /** Estrella de impacto vista desde arriba: puntas negras dentadas con borde rojo, fondo transparente. */
    static BufferedImage impactStar() {
        int s = 256;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Random rnd = new Random(98765);
        int spikes = 16;
        double[] len = new double[spikes];
        double[] off = new double[spikes];
        for (int i = 0; i < spikes; i++) {
            len[i] = 0.55 + 0.45 * rnd.nextDouble();
            off[i] = (rnd.nextDouble() - 0.5) * 0.25;
        }
        double half = s / 2.0;
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                double dx = (x + 0.5 - half) / half, dy = (y + 0.5 - half) / half;
                double r = Math.sqrt(dx * dx + dy * dy);
                double ang = (Math.atan2(dy, dx) / (2 * Math.PI) + 1) % 1.0 * spikes;
                int i = (int) ang;
                double f = ang - i + off[i];                        // posicion dentro de la punta
                double tri = 1 - Math.min(1, Math.abs(f - 0.5) * 2); // perfil triangular
                // dientes pequenos para que el borde no sea liso
                double teeth = 0.04 * Math.sin(r * 60 + i * 3.1);
                double edge = 0.16 + (len[i] * 0.95 - 0.16) * Math.pow(tri, 1.6) + teeth;
                int px;
                if (r < edge - 0.05) {
                    px = argb(1, 0.03, 0, 0);
                } else if (r < edge) {
                    px = argb(1, 1.0, 0.18, 0.08);
                } else if (r < edge + 0.06) {
                    double t = (r - edge) / 0.06;
                    px = argb((1 - t) * 0.7, 0.9, 0.05, 0.02);
                } else {
                    px = 0;
                }
                if (r > 0.98) px = 0;
                img.setRGB(x, y, px);
            }
        }
        return img;
    }

    /** Brillo de ojo: nucleo blanco, halo rojo redondo y un destello horizontal. Se dibuja aditivo. */
    static BufferedImage eyeGlow() {
        int s = 32;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                double dx = (x + 0.5 - s / 2.0) / (s / 2.0), dy = (y + 0.5 - s / 2.0) / (s / 2.0);
                double r = Math.sqrt(dx * dx + dy * dy);
                double halo = Math.pow(Math.max(0, 1 - r), 2.2);
                double flare = Math.max(0, 1 - Math.abs(dy) * 7) * Math.max(0, 1 - Math.abs(dx)) * 0.8;
                double core = Math.max(0, 1 - r * 3.5);
                double a = Math.min(1, halo + flare);
                img.setRGB(x, y, argb(a, 1.0, 0.12 + 0.88 * core, 0.1 + 0.9 * core));
            }
        }
        return img;
    }

    /** Cupula: u alrededor, v de abajo (0) hacia arriba (1). Rojo con vetas onduladas, mas opaco abajo. */
    static BufferedImage dome() {
        int w = 128, h = 32;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Random rnd = new Random(4242);
        double[] ph = new double[6];
        for (int i = 0; i < ph.length; i++) ph[i] = rnd.nextDouble() * Math.PI * 2;
        for (int y = 0; y < h; y++) {
            double v = (y + 0.5) / h;                  // 0 abajo .. 1 arriba (el renderer usa v=0 en el borde)
            for (int x = 0; x < w; x++) {
                double u = (x + 0.5) / w * Math.PI * 2;
                double streak = 0.5 + 0.5 * Math.sin(u * 9 + Math.sin(v * 6 + ph[0]) * 1.5 + ph[1])
                    * Math.sin(u * 5 + v * 3 + ph[2]);
                double bright = 0.35 + 0.65 * Math.pow(streak, 3);
                double a = (0.25 + 0.6 * Math.pow(1 - v, 1.5)) * (0.55 + 0.45 * streak);
                img.setRGB(x, y, argb(a, 0.55 + 0.45 * bright, 0.03 * bright, 0.02));
            }
        }
        return img;
    }

    /** Vetas radiales rojas para la entrada cinematografica: centro transparente, vetas hacia el borde. */
    static BufferedImage streaks() {
        int s = 256;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Random rnd = new Random(777);
        int n = 90;
        double[] width = new double[n], power = new double[n], start = new double[n];
        for (int i = 0; i < n; i++) {
            width[i] = 0.15 + rnd.nextDouble() * 0.45;
            power[i] = 0.4 + rnd.nextDouble() * 0.6;
            start[i] = 0.1 + rnd.nextDouble() * 0.35;
        }
        double half = s / 2.0;
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                double dx = (x + 0.5 - half) / half, dy = (y + 0.5 - half) / half;
                double r = Math.sqrt(dx * dx + dy * dy);
                double ang = (Math.atan2(dy, dx) / (2 * Math.PI) + 1) % 1.0 * n;
                int i = (int) ang;
                double f = Math.abs(ang - i - 0.5) * 2;           // 0 centro de la veta .. 1 borde
                double line = Math.max(0, 1 - f / width[i]) * power[i];
                double ramp = Math.max(0, Math.min(1, (r - start[i]) / 0.5));
                double a = Math.min(1, line * ramp * 1.3);
                img.setRGB(x, y, argb(a, 0.95, 0.06 + 0.2 * line * line, 0.04));
            }
        }
        return img;
    }

    /** Vineta: oscura arriba y abajo (y un poco en los lados), centro transparente. */
    static BufferedImage vignette() {
        int s = 256;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                double ny = (y + 0.5) / s, nx = (x + 0.5) / s;
                double top = Math.max(0, 1 - ny / 0.30), bottom = Math.max(0, 1 - (1 - ny) / 0.30);
                double sides = Math.max(0, 1 - Math.min(nx, 1 - nx) / 0.18) * 0.45;
                double a = Math.max(Math.pow(Math.max(top, bottom), 1.5), sides * sides);
                img.setRGB(x, y, argb(Math.min(1, a), 0.05, 0, 0));
            }
        }
        return img;
    }

    /** Nucleo del relampago: u = largo, v = ancho. Negro con borde rojo vivo muy fino (el halo va en otra capa). */
    static BufferedImage boltCore() {
        int w = 32, h = 16;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Random rnd = new Random(2468);
        double[] core = new double[w];
        for (int x = 0; x < w; x++) core[x] = 0.46 + 0.12 * rnd.nextDouble();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double d = Math.abs((y + 0.5) - h / 2.0) / (h / 2.0);
                double c = core[x];
                int px = 0;
                if (d < c) {
                    px = argb(1, 0.015, 0, 0);
                } else if (d < c + 0.30) {
                    double t = (d - c) / 0.30;
                    px = argb(1 - t * t * 0.6, 1.0, 0.10 + 0.35 * (1 - t), 0.06 + 0.25 * (1 - t));
                }
                img.setRGB(x, y, px);
            }
        }
        return img;
    }

    /** Halo rojo suave para detras del nucleo: alfa que cae desde el centro. */
    static BufferedImage boltGlow() {
        int s = 16;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < s; y++) {
            double d = Math.abs((y + 0.5) - s / 2.0) / (s / 2.0);
            double a = Math.pow(Math.max(0, 1 - d), 2.0) * 0.9;
            for (int x = 0; x < s; x++) img.setRGB(x, y, argb(a, 0.95, 0.05 + 0.1 * (1 - d), 0.03));
        }
        return img;
    }

    /** Chispa: punto blanco con cuatro destellos finos y un tinte rosado en el borde. */
    static BufferedImage spark() {
        int s = 16;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                double dx = (x + 0.5 - s / 2.0) / (s / 2.0), dy = (y + 0.5 - s / 2.0) / (s / 2.0);
                double r = Math.sqrt(dx * dx + dy * dy);
                double core = Math.exp(-r * r * 22);
                double armH = Math.max(0, 1 - Math.abs(dy) * 9) * Math.max(0, 1 - Math.abs(dx));
                double armV = Math.max(0, 1 - Math.abs(dx) * 9) * Math.max(0, 1 - Math.abs(dy));
                double a = Math.min(1, core + 0.85 * Math.max(armH, armV));
                img.setRGB(x, y, argb(a, 1, 0.55 + 0.45 * core, 0.5 + 0.5 * core));
            }
        }
        return img;
    }
}
