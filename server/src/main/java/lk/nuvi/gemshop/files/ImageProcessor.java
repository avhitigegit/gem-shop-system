package lk.nuvi.gemshop.files;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

/**
 * Makes uploaded photos small enough for the shop's internet and the phone app (tech stack: image handling):
 * turned the right way up (phone photos), at most {@link FileKind#maxSide()} pixels, saved as JPEG of about
 * {@value #TARGET_BYTES} bytes, plus a {@value #THUMB_SIDE}-pixel thumbnail for lists and the sales screen.
 */
@Component
public class ImageProcessor {

    /** About 300 KB (tech stack §8). */
    public static final int TARGET_BYTES = 300 * 1024;
    public static final int THUMB_SIDE = 320;
    /** Larger pictures are refused before they are opened (protects the server's memory). */
    static final long MAX_PIXELS = 60_000_000L;
    private static final float[] QUALITIES = {0.85f, 0.78f, 0.7f, 0.62f, 0.55f};
    private static final int MIN_SIDE = 900;

    public record Result(byte[] bytes, FileType type, int width, int height, byte[] thumb) {}

    public Result process(byte[] original, FileKind kind, FileType type) throws IOException {
        checkSize(original);
        BufferedImage img = Thumbnails.of(new ByteArrayInputStream(original)).scale(1.0).useExifOrientation(true)
                .asBufferedImage();
        img = fit(img, kind.maxSide());
        byte[] main;
        FileType outType;
        if (kind.keepPng() && type == FileType.PNG) {
            main = png(img);
            outType = FileType.PNG;
        } else {
            BufferedImage rgb = onWhite(img);
            main = jpegUnder(rgb, TARGET_BYTES);
            outType = FileType.JPEG;
            img = rgb;
        }
        BufferedImage small = fit(onWhite(img), THUMB_SIDE);
        byte[] thumb = jpeg(small, 0.8f);
        BufferedImage done = ImageIO.read(new ByteArrayInputStream(main));
        return new Result(main, outType, done.getWidth(), done.getHeight(), thumb);
    }

    /** JPEG of at most {@code limit} bytes: lower the quality first, then the size (not below 900 px). */
    static byte[] jpegUnder(BufferedImage img, int limit) throws IOException {
        BufferedImage cur = img;
        while (true) {
            byte[] out = null;
            for (float q : QUALITIES) {
                out = jpeg(cur, q);
                if (out.length <= limit) {
                    return out;
                }
            }
            int side = Math.max(cur.getWidth(), cur.getHeight());
            if (side <= MIN_SIDE) {
                return out;
            }
            cur = fit(cur, Math.max(MIN_SIDE, (int) (side * 0.8)));
        }
    }

    private static void checkSize(byte[] bytes) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new IOException("Unreadable picture");
            }
            ImageReader r = readers.next();
            try {
                r.setInput(in);
                if ((long) r.getWidth(0) * r.getHeight(0) > MAX_PIXELS) {
                    throw new IOException("Picture too large");
                }
            } finally {
                r.dispose();
            }
        }
    }

    /** Smaller so the longest side is at most {@code max}; never made bigger. */
    static BufferedImage fit(BufferedImage img, int max) throws IOException {
        int side = Math.max(img.getWidth(), img.getHeight());
        if (side <= max) {
            return img;
        }
        return Thumbnails.of(img).size(max, max).keepAspectRatio(true).asBufferedImage();
    }

    /** See-through parts become white (JPEG has no transparency). */
    static BufferedImage onWhite(BufferedImage img) {
        if (img.getType() == BufferedImage.TYPE_INT_RGB) {
            return img;
        }
        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return rgb;
    }

    static byte[] jpeg(BufferedImage img, float quality) throws IOException {
        ImageWriter w = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bos)) {
            w.setOutput(out);
            ImageWriteParam p = w.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(quality);
            w.write(null, new IIOImage(img, null, null), p);
        } finally {
            w.dispose();
        }
        return bos.toByteArray();
    }

    static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }
}
