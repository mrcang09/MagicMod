package org.test.magicmod.model;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;
import org.test.magicmod.Magicmod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ModelReplacementManager {
    private static final String ENTITY_INSTANCE_RULE_PREFIX =
        ModelReplacementTargetKind.ENTITY_ID.name().toLowerCase(Locale.ROOT) + ":";
    private static final Map<String, ModelReplacementRule> MANUAL_RULES = new ConcurrentHashMap<>();
    private static final Map<String, ModelReplacementRule> CONFIG_RULES = new ConcurrentHashMap<>();
    private static final Map<String, ModelRuntimeInstance> RUNTIME_INSTANCES = new ConcurrentHashMap<>();
    private static final AtomicInteger RENDERED_REPLACEMENTS = new AtomicInteger();

    private ModelReplacementManager() {
    }

    public static void replaceType(Identifier entityType, Identifier modelId) {
        replaceType(entityType, ModelCache.specFor(modelId));
    }

    public static void replaceType(Identifier entityType, ModelAssetSpec modelSpec) {
        putManualRule(newManualRule(
            Identifier.fromNamespaceAndPath("magicmod", "manual/type/" + entityType.getNamespace() + "/" + entityType.getPath()),
            ModelReplacementTarget.entityType(entityType),
            modelSpec,
            "command:type"
        ));
        Magicmod.LOGGER.info("[MODEL] Replaced entity type {} with model {}", entityType, modelSpec.displayName());
    }

    public static void replaceEntity(Entity entity, ModelAssetSpec modelSpec) {
        String sessionId = currentSessionId();
        ModelReplacementTarget target = ModelReplacementTarget.entity(entity, sessionId);
        putManualRule(newManualRule(
            Identifier.fromNamespaceAndPath("magicmod", "manual/entity/" + entity.getId()),
            target,
            modelSpec,
            "command:entity"
        ));
        Magicmod.LOGGER.info("[MODEL] Replaced entity {} with model {}", target.displayName(), modelSpec.displayName());
    }

    public static void replaceUuid(UUID uuid, ModelAssetSpec modelSpec) {
        ModelReplacementTarget target = ModelReplacementTarget.uuid(uuid, null);
        putManualRule(newManualRule(
            Identifier.fromNamespaceAndPath("magicmod", "manual/uuid/" + sanitizePath(uuid.toString())),
            target,
            modelSpec,
            "command:uuid"
        ));
        Magicmod.LOGGER.info("[MODEL] Replaced uuid {} with model {}", uuid, modelSpec.displayName());
    }

    public static void replacePlayerName(String playerName, ModelAssetSpec modelSpec) {
        ModelReplacementTarget target = ModelReplacementTarget.playerName(playerName);
        putManualRule(newManualRule(
            Identifier.fromNamespaceAndPath("magicmod", "manual/player/" + sanitizePath(playerName)),
            target,
            modelSpec,
            "command:player"
        ));
        Magicmod.LOGGER.info("[MODEL] Replaced player {} with model {}", playerName, modelSpec.displayName());
    }

    public static void replaceTag(String tag, ModelAssetSpec modelSpec) {
        ModelReplacementTarget target = ModelReplacementTarget.tag(tag);
        putManualRule(newManualRule(
            Identifier.fromNamespaceAndPath("magicmod", "manual/tag/" + sanitizePath(tag)),
            target,
            modelSpec,
            "command:tag"
        ));
        Magicmod.LOGGER.info("[MODEL] Replaced tag {} with model {}", tag, modelSpec.displayName());
    }

    public static boolean clearType(Identifier entityType) {
        return clearTarget(ModelReplacementTarget.entityType(entityType), "entity type " + entityType);
    }

    public static boolean clearEntity(Entity entity) {
        return clearTarget(ModelReplacementTarget.entity(entity, currentSessionId()), "entity " + entity.getId());
    }

    public static boolean clearUuid(UUID uuid) {
        return clearTarget(ModelReplacementTarget.uuid(uuid, null), "uuid " + uuid);
    }

    public static boolean clearPlayerName(String playerName) {
        return clearTarget(ModelReplacementTarget.playerName(playerName), "player " + playerName);
    }

    public static boolean clearTag(String tag) {
        return clearTarget(ModelReplacementTarget.tag(tag), "tag " + tag);
    }

    public static void clearAllRulesAndInstances() {
        MANUAL_RULES.clear();
        CONFIG_RULES.clear();
        RUNTIME_INSTANCES.clear();
    }

    public static void reloadResources() {
        Minecraft minecraft = Minecraft.getInstance();
        reloadResources(minecraft == null ? null : minecraft.getResourceManager());
    }

    public static void reloadResources(ResourceManager resourceManager) {
        RUNTIME_INSTANCES.clear();
        ModelCache.clear();
        CONFIG_RULES.clear();
        loadConfiguredRules(resourceManager);
        Magicmod.LOGGER.info("[MODEL] Reloaded model replacement resources; configuredRules={}", CONFIG_RULES.size());
    }

    public static void clearAll() {
        clearAllRulesAndInstances();
        ModelCache.clear();
        Magicmod.LOGGER.info("[MODEL] Cleared all model replacement rules and resources");
    }

    public static void clearSession() {
        MANUAL_RULES.clear();
        RUNTIME_INSTANCES.clear();
        ModelCache.clear();
        Magicmod.LOGGER.info("[MODEL] Cleared session model replacement state; configuredRules={}", CONFIG_RULES.size());
    }

    public static boolean playTypeAnimation(Identifier entityType, String animation) {
        return playTypeAnimation(entityType, animation, AnimationPlayMode.DEFAULT);
    }

    public static boolean playTypeAnimation(Identifier entityType, String animation, AnimationPlayMode playMode) {
        return playTargetAnimation(ModelReplacementTarget.entityType(entityType), animation, playMode);
    }

    public static boolean playEntityAnimation(Entity entity, String animation, AnimationPlayMode playMode) {
        return playTargetAnimation(ModelReplacementTarget.entity(entity, currentSessionId()), animation, playMode);
    }

    public static boolean playPlayerAnimation(String playerName, String animation, AnimationPlayMode playMode) {
        return playTargetAnimation(ModelReplacementTarget.playerName(playerName), animation, playMode);
    }

    public static boolean playUuidAnimation(UUID uuid, String animation, AnimationPlayMode playMode) {
        return playTargetAnimation(ModelReplacementTarget.uuid(uuid, null), animation, playMode);
    }

    public static boolean playTagAnimation(String tag, String animation, AnimationPlayMode playMode) {
        return playTargetAnimation(ModelReplacementTarget.tag(tag), animation, playMode);
    }

    public static boolean stopTypeAnimation(Identifier entityType) {
        return stopTargetAnimation(ModelReplacementTarget.entityType(entityType));
    }

    public static boolean stopEntityAnimation(Entity entity) {
        return stopTargetAnimation(ModelReplacementTarget.entity(entity, currentSessionId()));
    }

    public static boolean stopPlayerAnimation(String playerName) {
        return stopTargetAnimation(ModelReplacementTarget.playerName(playerName));
    }

    public static boolean stopUuidAnimation(UUID uuid) {
        return stopTargetAnimation(ModelReplacementTarget.uuid(uuid, null));
    }

    public static boolean stopTagAnimation(String tag) {
        return stopTargetAnimation(ModelReplacementTarget.tag(tag));
    }

    public static boolean resetTypeAnimation(Identifier entityType) {
        return resetTargetAnimation(ModelReplacementTarget.entityType(entityType));
    }

    public static boolean resetEntityAnimation(Entity entity) {
        return resetTargetAnimation(ModelReplacementTarget.entity(entity, currentSessionId()));
    }

    public static boolean resetPlayerAnimation(String playerName) {
        return resetTargetAnimation(ModelReplacementTarget.playerName(playerName));
    }

    public static boolean resetUuidAnimation(UUID uuid) {
        return resetTargetAnimation(ModelReplacementTarget.uuid(uuid, null));
    }

    public static boolean resetTagAnimation(String tag) {
        return resetTargetAnimation(ModelReplacementTarget.tag(tag));
    }

    public static ResolvedModelReplacement resolve(EntityRenderState state) {
        return resolve(state, null);
    }

    public static ResolvedModelReplacement resolve(EntityRenderState state, Entity entity) {
        Identifier entityType = entityTypeOf(state, entity);
        if (entityType == null) {
            return null;
        }
        ModelReplacementRule rule = effectiveRule(entityType, entity);
        if (rule == null) {
            return null;
        }
        RuntimeModel model = ModelCache.getOrLoad(rule.modelSpec());
        String sessionId = currentSessionId();
        String instanceKey = rule.target().instanceKey(entity, entityType, sessionId);
        ModelRuntimeInstance instance = RUNTIME_INSTANCES.computeIfAbsent(instanceKey, ignored -> new ModelRuntimeInstance());
        RuntimeAnimationClip animation = model.animationOrDefault(instance.currentAnimation());
        instance.ensureAnimation(rule.animation());
        animation = model.animationOrDefault(instance.currentAnimation());
        instance.update(model, animation, state.ageInTicks);
        return new ResolvedModelReplacement(rule, model, instance, animation);
    }

    public static void retainRuntimeInstances(Set<String> activeEntityInstanceKeys) {
        if (activeEntityInstanceKeys == null) {
            return;
        }
        RUNTIME_INSTANCES.keySet().removeIf(key ->
            key.startsWith(ENTITY_INSTANCE_RULE_PREFIX) && !activeEntityInstanceKeys.contains(key)
        );
    }

    public static String currentSessionId() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return "no_level";
        }
        return minecraft.level.dimension().toString() + "@" + System.identityHashCode(minecraft.level);
    }

    public static void noteRenderedReplacement() {
        RENDERED_REPLACEMENTS.incrementAndGet();
    }

    public static int renderedReplacementCount() {
        return RENDERED_REPLACEMENTS.get();
    }

    public static DebugStats debugStats() {
        return new DebugStats(
            effectiveRuleCount(),
            RUNTIME_INSTANCES.size(),
            ModelCache.loadedModelCount(),
            ModelCache.liveGpuBufferCount(),
            ModelCache.reloadVersion(),
            RENDERED_REPLACEMENTS.get()
        );
    }

    public static List<String> debugDumpLines() {
        List<String> lines = new ArrayList<>();
        MANUAL_RULES.values().stream()
            .sorted(Comparator.comparing(ModelReplacementRule::targetKey))
            .map(rule -> "manual: " + rule.toDebugLine())
            .forEach(lines::add);
        CONFIG_RULES.values().stream()
            .sorted(Comparator.comparing(ModelReplacementRule::targetKey))
            .map(rule -> "config: " + rule.toDebugLine())
            .forEach(lines::add);
        if (lines.isEmpty()) {
            lines.add("No active model replacement rules");
        }
        return List.copyOf(lines);
    }

    private static ModelReplacementRule newManualRule(
        Identifier ruleId,
        ModelReplacementTarget target,
        ModelAssetSpec modelSpec,
        String source
    ) {
        RuntimeModel model = ModelCache.getOrLoad(modelSpec);
        String defaultAnimation = model.resolveAnimationName("idle");
        return new ModelReplacementRule(ruleId, target, modelSpec, defaultAnimation, Integer.MAX_VALUE, source);
    }

    private static void putManualRule(ModelReplacementRule rule) {
        MANUAL_RULES.put(rule.targetKey(), rule);
        RUNTIME_INSTANCES.remove(rule.targetKey());
    }

    private static boolean clearTarget(ModelReplacementTarget target, String label) {
        String key = target.ruleKey();
        boolean removed = MANUAL_RULES.remove(key) != null;
        removed = CONFIG_RULES.remove(key) != null || removed;
        RUNTIME_INSTANCES.remove(key);
        if (removed) {
            Magicmod.LOGGER.info("[MODEL] Cleared model replacement for {}", label);
        }
        return removed;
    }

    private static boolean playTargetAnimation(ModelReplacementTarget target, String animation, AnimationPlayMode playMode) {
        ModelReplacementRule rule = effectiveRule(target);
        if (rule == null) {
            return false;
        }
        RuntimeModel model = ModelCache.getOrLoad(rule.modelSpec());
        String resolvedAnimation = model.findAnimationName(animation);
        if (resolvedAnimation == null) {
            return false;
        }
        MANUAL_RULES.put(rule.targetKey(), rule.withAnimation(resolvedAnimation).withSource("command-animation"));
        RUNTIME_INSTANCES.computeIfAbsent(rule.targetKey(), ignored -> new ModelRuntimeInstance()).play(resolvedAnimation, playMode);
        return true;
    }

    private static boolean stopTargetAnimation(ModelReplacementTarget target) {
        ModelReplacementRule rule = effectiveRule(target);
        if (rule == null) {
            return false;
        }
        RUNTIME_INSTANCES.computeIfAbsent(rule.targetKey(), ignored -> new ModelRuntimeInstance()).stop();
        return true;
    }

    private static boolean resetTargetAnimation(ModelReplacementTarget target) {
        ModelReplacementRule rule = effectiveRule(target);
        if (rule == null) {
            return false;
        }
        RUNTIME_INSTANCES.computeIfAbsent(rule.targetKey(), ignored -> new ModelRuntimeInstance()).reset();
        return true;
    }

    private static void loadConfiguredRules(ResourceManager resourceManager) {
        List<ModelReplacementRule> rules = ModelReplacementConfigLoader.load(resourceManager);
        for (ModelReplacementRule rule : rules) {
            CONFIG_RULES.compute(rule.targetKey(), (ignored, existing) -> {
                if (existing == null || rule.priority() >= existing.priority()) {
                    return rule;
                }
                return existing;
            });
        }
        if (!rules.isEmpty()) {
            Magicmod.LOGGER.info("[MODEL] Loaded {} model replacement rule file(s)", rules.size());
        }
    }

    private static ModelReplacementRule effectiveRule(ModelReplacementTarget target) {
        ModelReplacementRule manualRule = MANUAL_RULES.get(target.ruleKey());
        if (manualRule != null) {
            return manualRule;
        }
        return CONFIG_RULES.get(target.ruleKey());
    }

    private static ModelReplacementRule effectiveRule(Identifier entityType, Entity entity) {
        String sessionId = currentSessionId();
        ModelReplacementRule manualRule = bestMatchingRule(MANUAL_RULES.values(), entityType, entity, sessionId);
        if (manualRule != null) {
            return manualRule;
        }
        return bestMatchingRule(CONFIG_RULES.values(), entityType, entity, sessionId);
    }

    private static ModelReplacementRule bestMatchingRule(
        Iterable<ModelReplacementRule> rules,
        Identifier entityType,
        Entity entity,
        String sessionId
    ) {
        ModelReplacementRule best = null;
        for (ModelReplacementRule rule : rules) {
            if (!rule.target().matches(entityType, entity, sessionId)) {
                continue;
            }
            if (best == null || rule.priority() > best.priority()) {
                best = rule;
            }
        }
        return best;
    }

    private static Identifier entityTypeOf(EntityRenderState state, Entity entity) {
        if (entity != null) {
            Identifier entityType = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
            if (entityType != null) {
                return entityType;
            }
        }
        return ForgeRegistries.ENTITY_TYPES.getKey(state.entityType);
    }

    private static int effectiveRuleCount() {
        Set<String> keys = new HashSet<>(CONFIG_RULES.keySet());
        keys.addAll(MANUAL_RULES.keySet());
        return keys.size();
    }

    private static String sanitizePath(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9_./-]", "_");
    }

    public record DebugStats(
        int typeRules,
        int runtimeInstances,
        int loadedModels,
        int liveGpuBuffers,
        int reloadVersion,
        int renderedReplacements
    ) {
        public String toLogLine() {
            return "typeRules=" + typeRules
                + ", runtimeInstances=" + runtimeInstances
                + ", loadedModels=" + loadedModels
                + ", liveGpuBuffers=" + liveGpuBuffers
                + ", reloadVersion=" + reloadVersion
                + ", renderedReplacements=" + renderedReplacements;
        }
    }
}
