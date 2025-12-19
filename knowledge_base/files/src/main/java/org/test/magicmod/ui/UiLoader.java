package org.test.magicmod.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.fml.loading.FMLPaths;
import org.test.magicmod.Magicmod;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class UiLoader {
    private UiLoader() {
    }

    public static UiConfig load(String uiName) throws IOException {
        String fileName = normalizeName(uiName);
        Path diskPath = resolveDiskPath(fileName);
        if (diskPath != null && Files.isRegularFile(diskPath)) {
            try (Reader reader = Files.newBufferedReader(diskPath, StandardCharsets.UTF_8)) {
                return loadFromReader(reader);
            }
        }
        ResourceLocation location = ResourceLocation.parse(Magicmod.MODID + ":ui/" + fileName);
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        try (Reader reader = resourceManager.openAsReader(location)) {
            return loadFromReader(reader);
        }
    }

    public static List<String> listUiFiles() {
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        Map<ResourceLocation, ?> resources = resourceManager.listResources("ui",
            id -> id.getNamespace().equals(Magicmod.MODID)
                && (id.getPath().endsWith(".yml") || id.getPath().endsWith(".yaml")));
        return resources.keySet().stream()
            .map(ResourceLocation::getPath)
            .map(path -> path.startsWith("ui/") ? path.substring(3) : path)
            .sorted(Comparator.naturalOrder())
            .collect(Collectors.toList());
    }

    private static UiConfig loadFromReader(Reader reader) throws IOException {
        Yaml yaml = new Yaml();
        Object data = yaml.load(reader);
        if (!(data instanceof Map<?, ?> raw)) {
            throw new IOException("UI yaml root must be a map");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) raw;
        return UiConfig.fromMap(root);
    }

    private static String normalizeName(String name) throws IOException {
        String normalized = name == null ? "" : name.trim().replace('\\', '/');
        if (normalized.isEmpty()) {
            throw new IOException("UI name is empty");
        }
        if (normalized.startsWith("ui/")) {
            normalized = normalized.substring(3);
        }
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.contains("..")) {
            throw new IOException("UI path is not allowed");
        }
        if (!normalized.endsWith(".yml") && !normalized.endsWith(".yaml")) {
            normalized = normalized + ".yml";
        }
        return normalized;
    }

    private static Path resolveDiskPath(String fileName) {
        Path gameDir = FMLPaths.GAMEDIR.get();
        Path projectRoot = gameDir.getParent();
        if (projectRoot == null) {
            return null;
        }
        return projectRoot.resolve("src/main/resources/assets")
            .resolve(Magicmod.MODID)
            .resolve("ui")
            .resolve(fileName);
    }
}
