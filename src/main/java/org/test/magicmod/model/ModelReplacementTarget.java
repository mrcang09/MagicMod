package org.test.magicmod.model;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;
import java.util.UUID;

public record ModelReplacementTarget(
    ModelReplacementTargetKind kind,
    Identifier entityType,
    String value
) {
    private static final String UNKNOWN_SESSION = "unknown";

    public ModelReplacementTarget {
        if (kind == null) {
            throw new IllegalArgumentException("Model replacement target kind cannot be null");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Model replacement target value cannot be blank");
        }
        value = normalizeValue(kind, value);
    }

    public static ModelReplacementTarget entityType(Identifier entityType) {
        if (entityType == null) {
            throw new IllegalArgumentException("Entity type target cannot be null");
        }
        return new ModelReplacementTarget(ModelReplacementTargetKind.ENTITY_TYPE, entityType, entityType.toString());
    }

    public static ModelReplacementTarget entity(Entity entity, String sessionId) {
        Identifier type = entityTypeOf(entity);
        return new ModelReplacementTarget(
            ModelReplacementTargetKind.ENTITY_ID,
            type,
            entityScopedKey(sessionId, entity.getId())
        );
    }

    public static ModelReplacementTarget uuid(UUID uuid, Identifier entityType) {
        if (uuid == null) {
            throw new IllegalArgumentException("UUID target cannot be null");
        }
        return new ModelReplacementTarget(ModelReplacementTargetKind.UUID, entityType, uuid.toString());
    }

    public static ModelReplacementTarget playerName(String playerName) {
        return new ModelReplacementTarget(ModelReplacementTargetKind.PLAYER_NAME, null, playerName);
    }

    public static ModelReplacementTarget tag(String tag) {
        return new ModelReplacementTarget(ModelReplacementTargetKind.TAG, null, tag);
    }

    public boolean matches(Identifier renderedEntityType, Entity entity, String sessionId) {
        if (entityType != null && !entityType.equals(renderedEntityType)) {
            return false;
        }
        if (kind == ModelReplacementTargetKind.ENTITY_TYPE) {
            return entityType != null && entityType.equals(renderedEntityType);
        }
        if (entity == null) {
            return false;
        }
        return switch (kind) {
            case ENTITY_ID -> value.equals(entityScopedKey(sessionId, entity.getId()));
            case UUID -> value.equals(entity.getUUID().toString().toLowerCase(Locale.ROOT));
            case PLAYER_NAME -> value.equals(entity.getName().getString().toLowerCase(Locale.ROOT));
            case TAG -> entity.getTags().contains(value);
            case ENTITY_TYPE -> false;
        };
    }

    public String ruleKey() {
        return kind.name().toLowerCase(Locale.ROOT) + ":" + value;
    }

    public String instanceKey(Entity entity, Identifier renderedEntityType, String sessionId) {
        return ruleKey();
    }

    public String displayName() {
        return kind.name().toLowerCase(Locale.ROOT) + "=" + value;
    }

    public static String entityScopedKey(String sessionId, int entityId) {
        String safeSession = sessionId == null || sessionId.isBlank() ? UNKNOWN_SESSION : sessionId;
        return "entity:" + safeSession + ":" + entityId;
    }

    private static String normalizeValue(ModelReplacementTargetKind kind, String rawValue) {
        String trimmed = rawValue.trim();
        if (kind == ModelReplacementTargetKind.PLAYER_NAME || kind == ModelReplacementTargetKind.UUID) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        return trimmed;
    }

    private static Identifier entityTypeOf(Entity entity) {
        Identifier type = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (type == null) {
            throw new IllegalArgumentException("Cannot resolve entity type for target entity");
        }
        return type;
    }
}
