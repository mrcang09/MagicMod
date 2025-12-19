package org.test.magicmod.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.test.magicmod.Magicmod;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class GifManager {
    private static final Map<ResourceLocation, GifTexture> CACHE = new ConcurrentHashMap<>();

    private GifManager() {
    }

    public static GifFrame getFrame(ResourceLocation location) {
        GifTexture texture = CACHE.computeIfAbsent(location, GifManager::load);
        if (texture == null) {
            return null;
        }
        return texture.getFrame(System.currentTimeMillis());
    }

    private static GifTexture load(ResourceLocation location) {
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        Optional<Resource> resource = resourceManager.getResource(location);
        if (resource.isEmpty()) {
            return null;
        }
        try (InputStream stream = resource.get().open(); ImageInputStream imageStream = ImageIO.createImageInputStream(stream)) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            reader.setInput(imageStream, false);
            int frameCount = reader.getNumImages(true);
            if (frameCount <= 0) {
                return null;
            }
            List<GifFrame> frames = new ArrayList<>();
            List<Integer> delays = new ArrayList<>();
            TextureManager textureManager = Minecraft.getInstance().getTextureManager();
            String key = sanitizeKey(location.toString());

            for (int i = 0; i < frameCount; i++) {
                BufferedImage image = reader.read(i);
                int delay = readDelay(reader.getImageMetadata(i));
                NativeImage nativeImage = toNativeImage(image);
                DynamicTexture dynamicTexture = new DynamicTexture(() -> "magicmod_gif", nativeImage);
                ResourceLocation frameLocation = ResourceLocation.fromNamespaceAndPath(Magicmod.MODID,
                    "gif/" + key + "/" + i);
                textureManager.register(frameLocation, dynamicTexture);
                frames.add(new GifFrame(frameLocation, image.getWidth(), image.getHeight()));
                delays.add(delay);
            }
            reader.dispose();
            return new GifTexture(frames, delays);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static NativeImage toNativeImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        NativeImage nativeImage = new NativeImage(width, height, true);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                nativeImage.setPixelABGR(x, y, argbToAbgr(argb));
            }
        }
        return nativeImage;
    }

    private static int argbToAbgr(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static int readDelay(IIOMetadata metadata) {
        if (metadata == null) {
            return 100;
        }
        try {
            String format = metadata.getNativeMetadataFormatName();
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
            IIOMetadataNode node = (IIOMetadataNode) root.getElementsByTagName("GraphicControlExtension").item(0);
            if (node == null) {
                return 100;
            }
            String delay = node.getAttribute("delayTime");
            int delayCs = Integer.parseInt(delay);
            return Math.max(20, delayCs * 10);
        } catch (Exception ignored) {
            return 100;
        }
    }

    private static String sanitizeKey(String value) {
        return value.replace(':', '_').replace('/', '_').replace('.', '_');
    }

    private static final class GifTexture {
        private final List<GifFrame> frames;
        private final List<Integer> delays;
        private final int durationMs;

        private GifTexture(List<GifFrame> frames, List<Integer> delays) {
            this.frames = frames;
            this.delays = delays;
            int total = 0;
            for (int delay : delays) {
                total += delay;
            }
            this.durationMs = Math.max(1, total);
        }

        private GifFrame getFrame(long timeMs) {
            int elapsed = (int) (timeMs % durationMs);
            int accum = 0;
            for (int i = 0; i < frames.size(); i++) {
                accum += delays.get(i);
                if (elapsed < accum) {
                    return frames.get(i);
                }
            }
            return frames.get(frames.size() - 1);
        }
    }

    public record GifFrame(ResourceLocation location, int width, int height) {
    }
}
