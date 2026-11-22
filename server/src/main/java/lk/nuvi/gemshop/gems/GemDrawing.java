package lk.nuvi.gemshop.gems;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * The gem picture used when a gem has no photo (shop certificate PDF): a cut stone, or a cabochon for star / cat's
 * eye / moonstone, in the colours of the gem type – the same drawing as the screens (client gem-picture.tsx).
 */
public final class GemDrawing {

    private GemDrawing() {}

    /** Light, main and dark colour per gem type (client GEM_COLORS). */
    private static final Map<String, int[]> COLORS = Map.ofEntries(
            Map.entry("Blue Sapphire", new int[] {0x1f4fa3, 0x5b8ef0, 0x0c2a66}),
            Map.entry("Yellow Sapphire", new int[] {0xe8b923, 0xfde68a, 0xa16207}),
            Map.entry("Pink Sapphire", new int[] {0xe0559b, 0xfbcfe8, 0x9d174d}),
            Map.entry("Padparadscha", new int[] {0xf08a5d, 0xffd3b6, 0xb4502d}),
            Map.entry("Star Sapphire", new int[] {0x5b6f95, 0xc7d2e8, 0x2f3e5c}),
            Map.entry("Ruby", new int[] {0xc0172f, 0xfb7185, 0x7f0d1d}),
            Map.entry("Cat's Eye", new int[] {0xc99a2e, 0xfde7a7, 0x7a5a12}),
            Map.entry("Alexandrite", new int[] {0x1f8a70, 0xc084fc, 0x0f4d3f}),
            Map.entry("Moonstone", new int[] {0xc9d6ea, 0xffffff, 0x8aa0c4}),
            Map.entry("Spinel", new int[] {0xb4508c, 0xe9b4d6, 0x6b2251}),
            Map.entry("Garnet", new int[] {0x7a1426, 0xd04a5f, 0x430a14}),
            Map.entry("Topaz", new int[] {0x4fb3d9, 0xc8ecfa, 0x1c6f91}));
    private static final int[] OTHER = {0x64748b, 0xcbd5e1, 0x334155};

    /** PNG of the stone, {@code size} pixels square, on a light round background. */
    public static byte[] png(String gemType, int size) {
        int[] c = COLORS.getOrDefault(gemType, OTHER);
        Color main = new Color(c[0]), light = new Color(c[1]), dark = new Color(c[2]);
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            float s = size / 100f;
            g.setPaint(new RadialGradientPaint(new Point2D.Float(50 * s, 40 * s), 70 * s, new float[] {0f, 1f},
                    new Color[] {Color.WHITE, new Color(0xeef1f7)}));
            g.fillRect(0, 0, size, size);
            float cx = 50 * s, cy = 50 * s, r = 34 * s;
            RadialGradientPaint stone = new RadialGradientPaint(new Point2D.Float(cx - r * .2f, cy - r * .3f), r * 1.4f,
                    new float[] {0f, .55f, 1f}, new Color[] {light, main, dark}, MultipleGradientPaint.CycleMethod.NO_CYCLE);
            String t = gemType == null ? "" : gemType;
            if (t.contains("Star") || t.contains("Cat") || t.contains("Moonstone")) {
                Ellipse2D e = new Ellipse2D.Float(cx - r, cy - r * .82f, 2 * r, 2 * r * .82f);
                g.setPaint(stone);
                g.fill(e);
                g.setColor(dark);
                g.setStroke(new BasicStroke(s));
                g.draw(e);
                g.setColor(new Color(255, 255, 255, 150));
                g.fill(new Ellipse2D.Float(cx - r * .6f, cy - r * .52f, r * .5f, r * .28f));
                return write(img);
            }
            double[][] pts = {{0, -1}, {.72, -.62}, {1, 0}, {.72, .62}, {0, 1}, {-.72, .62}, {-1, 0}, {-.72, -.62}};
            Path2D outer = new Path2D.Float(), inner = new Path2D.Float();
            for (int i = 0; i < pts.length; i++) {
                float x = cx + (float) pts[i][0] * r, y = cy + (float) pts[i][1] * r * .86f;
                float ix = cx + (x - cx) * .5f, iy = cy + (y - cy) * .5f;
                if (i == 0) {
                    outer.moveTo(x, y);
                    inner.moveTo(ix, iy);
                } else {
                    outer.lineTo(x, y);
                    inner.lineTo(ix, iy);
                }
            }
            outer.closePath();
            inner.closePath();
            g.setPaint(stone);
            g.fill(outer);
            g.setColor(dark);
            g.setStroke(new BasicStroke(s));
            g.draw(outer);
            g.setColor(new Color(light.getRed(), light.getGreen(), light.getBlue(), 90));
            g.fill(inner);
            g.setColor(new Color(255, 255, 255, 140));
            g.setStroke(new BasicStroke(.8f * s));
            g.draw(inner);
            g.setStroke(new BasicStroke(.7f * s));
            g.setColor(new Color(255, 255, 255, 115));
            for (double[] p : pts) {
                float x = cx + (float) p[0] * r, y = cy + (float) p[1] * r * .86f;
                g.draw(new Line2D.Float(x, y, cx + (x - cx) * .5f, cy + (y - cy) * .5f));
            }
            Path2D shine = new Path2D.Float();
            shine.moveTo(cx - r * .45f, cy - r * .5f);
            shine.lineTo(cx - r * .1f, cy - r * .62f);
            shine.lineTo(cx - r * .25f, cy - r * .2f);
            shine.closePath();
            g.setColor(new Color(255, 255, 255, 180));
            g.fill(shine);
            return write(img);
        } finally {
            g.dispose();
        }
    }

    private static byte[] write(BufferedImage img) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
