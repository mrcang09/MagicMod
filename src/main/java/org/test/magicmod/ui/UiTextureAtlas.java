package org.test.magicmod.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.test.magicmod.Magicmod;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Texture atlas for packing multiple UI textures into a single texture.
 * Uses a simple shelf bin packing algorithm.
 */
public class UiTextureAtlas {
    private final int atlasSize;
    private final Map<String, AtlasRegion> regions = new HashMap<>();
    private NativeImage atlasImage;
    private DynamicTexture dynamicTexture;
    private Identifier atlasLocation;
    private boolean built = false;

    public UiTextureAtlas(int atlasSize) {
        this.atlasSize = atlasSize;
    }

    /**
     * Build the atlas from a list of texture paths
     */
    public boolean build(List<String> texturePaths) {
        if (built) {
            return true;
        }

        try {
            atlasImage = new NativeImage(atlasSize, atlasSize, true);
            // Fill with transparent black
            for (int x = 0; x < atlasSize; x++) {
                for (int y = 0; y < atlasSize; y++) {
                    atlasImage.setPixel(x, y, 0x00000000);
                }
            }

            // Simple shelf packing algorithm
            int currentX = 0;
            int currentY = 0;
            int rowHeight = 0;

            for (String texturePath : texturePaths) {
                if (texturePath == null || texturePath.isBlank()) {
                    continue;
                }

                // Skip GIF files (they need special handling)
                if (texturePath.toLowerCase().endsWith(".gif")) {
                    continue;
                }

                Identifier location = parseLocation(texturePath);
                if (location == null) {
                    continue;
                }

                NativeImage sourceImage = loadImage(location);
                if (sourceImage == null) {
                    continue;
                }

                try {
                    int width = sourceImage.getWidth();
                    int height = sourceImage.getHeight();

                    // Check if we need to move to next row
                    if (currentX + width > atlasSize) {
                        currentX = 0;
                        currentY += rowHeight;
                        rowHeight = 0;
                    }

                    // Check if we have space
                    if (currentY + height > atlasSize) {
                        continue; // Skip this texture, atlas is full
                    }

                    // Copy pixels to atlas
                    for (int x = 0; x < width; x++) {
                        for (int y = 0; y < height; y++) {
                            int pixel = sourceImage.getPixel(x, y);
                            atlasImage.setPixel(currentX + x, currentY + y, pixel);
                        }
                    }

                    // Store region info
                    regions.put(texturePath, new AtlasRegion(currentX, currentY, width, height));

                    // Update position
                    currentX += width;
                    rowHeight = Math.max(rowHeight, height);
                } finally {
                    sourceImage.close();
                }
            }

            // Upload to GPU
            Minecraft minecraft = Minecraft.getInstance();
            dynamicTexture = new DynamicTexture(() -> "ui_atlas_" + System.currentTimeMillis(), atlasImage);
            atlasLocation = Identifier.fromNamespaceAndPath(Magicmod.MODID, "ui_atlas_" + System.currentTimeMillis());
            minecraft.getTextureManager().register(atlasLocation, dynamicTexture);

            built = true;
            return true;
        } catch (Exception e) {
            Magicmod.LOGGER.error("Failed to build texture atlas", e);
            cleanup();
            return false;
        }
    }

    /**
     * Get the atlas texture location
     */
    public Identifier getAtlasLocation() {
        return atlasLocation;
    }

    /**
     * Get region for a texture path
     */
    public AtlasRegion getRegion(String texturePath) {
        return regions.get(texturePath);
    }

    /**
     * Check if a texture is in the atlas
     */
    public boolean hasTexture(String texturePath) {
        return regions.containsKey(texturePath);
    }

    /**
     * Get atlas size
     */
    public int getAtlasSize() {
        return atlasSize;
    }

    /**
     * Cleanup resources
     */
    public void cleanup() {
        Minecraft minecraft = Minecraft.getInstance();
        if (atlasLocation != null && minecraft != null) {
            minecraft.getTextureManager().release(atlasLocation);
            atlasLocation = null;
            dynamicTexture = null;
            atlasImage = null;
        } else if (dynamicTexture != null) {
            dynamicTexture.close();
            dynamicTexture = null;
            atlasImage = null;
        } else if (atlasImage != null) {
            atlasImage.close();
            atlasImage = null;
        }
        regions.clear();
        built = false;
    }

    private NativeImage loadImage(Identifier location) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            Resource resource = minecraft.getResourceManager().getResource(location).orElse(null);
            if (resource == null) {
                return null;
            }
            try (InputStream stream = resource.open()) {
                return NativeImage.read(stream);
            }
        } catch (IOException e) {
            return null;
        }
    }

    private Identifier parseLocation(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        if (path.contains(":")) {
            return Identifier.parse(path);
        }
        return Identifier.fromNamespaceAndPath(Magicmod.MODID, path);
    }

    /**
     * Represents a region in the texture atlas
     */
    public static class AtlasRegion {
        public final int x;
        public final int y;
        public final int width;
        public final int height;

        public AtlasRegion(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        /**
         * Get normalized UV coordinates (0-1 range)
         */
        public float getU(int atlasSize) {
            return (float) x / atlasSize;
        }

        public float getV(int atlasSize) {
            return (float) y / atlasSize;
        }

        public float getU2(int atlasSize) {
            return (float) (x + width) / atlasSize;
        }

        public float getV2(int atlasSize) {
            return (float) (y + height) / atlasSize;
        }
    }
}
