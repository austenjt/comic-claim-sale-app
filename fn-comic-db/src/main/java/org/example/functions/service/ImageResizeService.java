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

    private static ImageResizeService SERVICE_INSTANCE;

    public static ImageResizeService getServiceInstance() {
        if (Objects.isNull(SERVICE_INSTANCE)) {
            SERVICE_INSTANCE = new ImageResizeService();
        }
        return SERVICE_INSTANCE;
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
            BufferedImage original = Imaging.getBufferedImage(imageData);
            int targetWidth = (int) Math.round((double) original.getWidth() * targetHeight / original.getHeight());
            BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = resized.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(original, 0, 0, targetWidth, targetHeight, null);
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
