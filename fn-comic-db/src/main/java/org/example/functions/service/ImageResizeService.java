package org.example.functions.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.imaging.ImageFormats;
import org.apache.commons.imaging.Imaging;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

@Slf4j
public class ImageResizeService {

    /** Thread-safe lazy singleton via the initialization-on-demand holder idiom. */
    private static class Holder {
        private static final ImageResizeService INSTANCE = new ImageResizeService();
    }

    public static ImageResizeService getServiceInstance() {
        return Holder.INSTANCE;
    }

    /** Resizes only if the image exceeds maxHeight. Returns original bytes unchanged if already small enough. */
    public byte[] resizeIfTooTall(byte[] imageData, int maxHeight) throws IOException {
        System.setProperty("java.awt.headless", "true");
        try {
            BufferedImage original = Imaging.getBufferedImage(imageData);
            if (original.getHeight() <= maxHeight) return imageData;
            return resizeToHeight(imageData, maxHeight);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Image size check failed: " + e.getMessage(), e);
        }
    }

    public byte[] resizeToHeight(byte[] imageData, int targetHeight) throws IOException {
        System.setProperty("java.awt.headless", "true");
        try {
            BufferedImage current = Imaging.getBufferedImage(imageData);

            // Progressive downscaling: halve dimensions repeatedly until within 2x of target.
            // Each pass stays within a 2:1 ratio so bilinear never has to skip pixels.
            while (current.getHeight() > targetHeight * 2) {
                int stepWidth  = current.getWidth()  / 2;
                int stepHeight = current.getHeight() / 2;
                BufferedImage step = new BufferedImage(stepWidth, stepHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = step.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,   RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING,        RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,     RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING,  RenderingHints.VALUE_COLOR_RENDER_QUALITY);
                g.drawImage(current, 0, 0, stepWidth, stepHeight, null);
                g.dispose();
                current = step;
            }

            // Final pass to exact target dimensions.
            int targetWidth = (int) Math.round((double) current.getWidth() * targetHeight / current.getHeight());
            BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = resized.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,  RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING,       RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,    RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            g2d.drawImage(current, 0, 0, targetWidth, targetHeight, null);
            g2d.dispose();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Imaging.writeImage(resized, outputStream, ImageFormats.PNG);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Image resize failed: " + e.getMessage(), e);
        }
    }

}
