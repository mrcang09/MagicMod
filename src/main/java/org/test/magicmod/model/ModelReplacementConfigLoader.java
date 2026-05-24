package org.test.magicmod.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.registries.ForgeRegistries;
import org.test.magicmod.Magicmod;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ModelReplacementConfigLoader {
    private static final String RULE_DIRECTORY = "model_replacements";

    private ModelReplacementConfigLoader() {
    }

    public static List<ModelReplacementRule> load(ResourceManager resourceManager) {
        if (resourceManager == null) {
            return List.of();
        }

        Map<Identifier, Resource> resources = resourceManager.listResources(
            RULE_DIRECTORY,
            id -> id.getPath().endsWith(".json")
        );
        if (resources.isEmpty()) {
            return List.of();
        }

        List<ModelReplacementRule> rules = new ArrayList<>();
        resources.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
            .forEach(entry -> loadOne(entry.getKey(), entry.getValue(), rules));
        return List.copyOf(rules);
    }

    private static void loadOne(Identifier resourceId, Resource resource, List<ModelReplacementRule> rules) {
        try (BufferedReader reader = resource.openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!booleanValue(root, "enabled", true)) {
                return;
            }

            ModelReplacementRule rule = parseRule(resourceId, root);
            if (rule != null) {
                rules.add(rule);
            }
        } catch (Exception error) {
            Magicmod.LOGGER.warn("[MODEL] Failed to load model replacement rule {}", resourceId, error);
        }
    }

    private static ModelReplacementRule parseRule(Identifier resourceId, JsonObject root) throws IOException {
        Identifier ruleId = identifierValue(root, "id", deriveRuleId(resourceId));
        int priority = intValue(root, "priority", 0);
        JsonObject target = object(root, "target");
        JsonObject model = object(root, "model");
        if (target == null || model == null) {
            throw new IOException("Model replacement rule requires target and model objects");
        }

        ModelReplacementTarget replacementTarget = parseTarget(resourceId, target);
        if (replacementTarget == null) {
            return null;
        }

        ModelAssetSpec modelSpec = parseModelSpec(resourceId, model);
        if (modelSpec == null) {
            return null;
        }

        String defaultAnimation = stringValue(root, "default_animation", "idle");
        return new ModelReplacementRule(
            ruleId,
            replacementTarget,
            modelSpec,
            defaultAnimation,
            priority,
            "resource:" + resourceId
        );
    }

    private static ModelReplacementTarget parseTarget(Identifier resourceId, JsonObject target) {
        String targetType = stringValue(target, "type", "").toLowerCase(Locale.ROOT);
        if ("entity_type".equals(targetType)) {
            Identifier entityType = identifierValue(target, "entity", null);
            if (entityType == null || !ForgeRegistries.ENTITY_TYPES.containsKey(entityType)) {
                Magicmod.LOGGER.warn("[MODEL] Skipping {} because entity type '{}' is unknown", resourceId, entityType);
                return null;
            }
            return ModelReplacementTarget.entityType(entityType);
        }
        if ("player_name".equals(targetType) || "player".equals(targetType)) {
            String name = stringValue(target, "name", "");
            if (name.isBlank()) {
                Magicmod.LOGGER.warn("[MODEL] Skipping {} because player name target is blank", resourceId);
                return null;
            }
            return ModelReplacementTarget.playerName(name);
        }
        if ("uuid".equals(targetType)) {
            String uuidValue = stringValue(target, "uuid", "");
            try {
                return ModelReplacementTarget.uuid(UUID.fromString(uuidValue), optionalEntityType(target));
            } catch (IllegalArgumentException error) {
                Magicmod.LOGGER.warn("[MODEL] Skipping {} because uuid '{}' is invalid", resourceId, uuidValue);
                return null;
            }
        }
        if ("tag".equals(targetType)) {
            String tag = stringValue(target, "tag", "");
            if (tag.isBlank()) {
                Magicmod.LOGGER.warn("[MODEL] Skipping {} because tag target is blank", resourceId);
                return null;
            }
            return ModelReplacementTarget.tag(tag);
        }
        Magicmod.LOGGER.warn("[MODEL] Skipping {} because target type '{}' is not supported", resourceId, targetType);
        return null;
    }

    private static ModelAssetSpec parseModelSpec(Identifier resourceId, JsonObject model) throws IOException {
        String format = stringValue(model, "format", "test").toLowerCase(Locale.ROOT);
        if ("test".equals(format) || "test_cube".equals(format)) {
            return ModelAssetSpec.TEST_CUBE;
        }
        if ("mmesh".equals(format)) {
            Identifier source = identifierValue(model, "source", identifierValue(model, "model", null));
            Identifier texture = identifierValue(model, "texture", null);
            Identifier animation = identifierValue(model, "animation", identifierValue(model, "animation_file", null));
            if (source == null || texture == null) {
                throw new IOException("MMESH replacement requires source/model and texture identifiers");
            }
            ModelRenderMode renderMode = renderModeValue(model, "render_mode", ModelRenderMode.CUTOUT);
            return ModelAssetSpec.mmesh(source, texture, animation, renderMode);
        }
        if ("fbx".equals(format)) {
            Identifier source = identifierValue(model, "source", identifierValue(model, "model", null));
            Identifier texture = identifierValue(model, "texture", null);
            Identifier animation = identifierValue(model, "animation", identifierValue(model, "animation_file", null));
            if (source == null || texture == null) {
                throw new IOException("FBX replacement requires source/model and texture identifiers");
            }
            ModelRenderMode renderMode = renderModeValue(model, "render_mode", ModelRenderMode.CUTOUT);
            return ModelAssetSpec.fbx(source, texture, animation, renderMode);
        }
        if (!"bedrock".equals(format)) {
            throw new IOException("Unsupported model format: " + format);
        }

        Identifier geometry = identifierValue(model, "geometry", null);
        Identifier texture = identifierValue(model, "texture", null);
        Identifier animation = identifierValue(model, "animation", identifierValue(model, "animation_file", null));
        if (geometry == null || texture == null) {
            throw new IOException("Bedrock replacement requires geometry and texture identifiers");
        }
        ModelRenderMode renderMode = renderModeValue(model, "render_mode", ModelRenderMode.CUTOUT);
        return ModelAssetSpec.bedrock(geometry, texture, animation, renderMode);
    }

    private static Identifier deriveRuleId(Identifier resourceId) {
        String path = resourceId.getPath();
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - ".json".length());
        }
        return Identifier.fromNamespaceAndPath(resourceId.getNamespace(), path);
    }

    private static JsonObject object(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    private static String stringValue(JsonObject object, String key, String defaultValue) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        return element.getAsString();
    }

    private static int intValue(JsonObject object, String key, int defaultValue) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        return element.getAsInt();
    }

    private static boolean booleanValue(JsonObject object, String key, boolean defaultValue) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        return element.getAsBoolean();
    }

    private static Identifier identifierValue(JsonObject object, String key, Identifier defaultValue) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        return Identifier.tryParse(element.getAsString());
    }

    private static ModelRenderMode renderModeValue(JsonObject object, String key, ModelRenderMode defaultValue) {
        String value = stringValue(object, key, defaultValue.name()).toUpperCase(Locale.ROOT);
        try {
            return ModelRenderMode.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            Magicmod.LOGGER.warn("[MODEL] Unknown render mode '{}', falling back to {}", value, defaultValue);
            return defaultValue;
        }
    }

    private static Identifier optionalEntityType(JsonObject target) {
        Identifier entityType = identifierValue(target, "entity", null);
        if (entityType != null && ForgeRegistries.ENTITY_TYPES.containsKey(entityType)) {
            return entityType;
        }
        return null;
    }
}
