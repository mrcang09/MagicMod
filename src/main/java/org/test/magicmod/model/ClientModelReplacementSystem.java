package org.test.magicmod.model;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.ForgeRegistries;
import org.test.magicmod.Magicmod;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ClientModelReplacementSystem {
    private static final List<String> SIMPLE_MODEL_SUGGESTIONS = List.of("test_cube", "magicmod:test_cube");
    private static final List<String> BEDROCK_GEOMETRY_SUGGESTIONS = List.of("magicmod:entity_models/bedrock/test_creature.geo.json");
    private static final List<String> BEDROCK_TEXTURE_SUGGESTIONS = List.of(
        "minecraft:textures/block/copper_block.png"
    );
    private static final List<String> BEDROCK_ANIMATION_SUGGESTIONS = List.of(
        "magicmod:entity_models/bedrock/test_creature.animation.json"
    );
    private static final List<String> FBX_MODEL_SUGGESTIONS = List.of("magicmod:entity_models/fbx/test_cube_ascii.fbx");
    private static final List<String> MMESH_MODEL_SUGGESTIONS = List.of("magicmod:entity_models/runtime/test_cube.mmesh");
    private static final List<String> ANIMATION_SUGGESTIONS = List.of(
        "idle",
        "spin",
        "pulse",
        "animation.magicmod.test_creature.idle",
        "animation.magicmod.test_creature.spin",
        "animation.magicmod.test_creature.pulse"
    );
    private static long lastRenderErrorAt;
    private static int pruneTick;

    private ClientModelReplacementSystem() {
    }

    public static void register(BusGroup modBusGroup) {
        RegisterClientCommandsEvent.BUS.addListener(ClientModelReplacementSystem::onRegisterClientCommands);
        RegisterClientReloadListenersEvent.getBus(modBusGroup).addListener(ClientModelReplacementSystem::onRegisterReloadListeners);
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> ModelReplacementManager.clearSession());
        TickEvent.ClientTickEvent.Post.BUS.addListener(ClientModelReplacementSystem::onClientTick);
        RenderLivingEvent.Pre.BUS.addListener(ClientModelReplacementSystem::onRenderLivingPre);
        ClientModelSmokeTest.register();
    }

    private static boolean onRenderLivingPre(RenderLivingEvent.Pre<?, ?, ?> event) {
        ResolvedModelReplacement replacement;
        try {
            replacement = ModelReplacementManager.resolve(event.getState(), findRenderedEntity(event.getState()));
        } catch (Exception error) {
            logRenderError("resolve", error);
            return false;
        }
        if (replacement == null) {
            return false;
        }

        try {
            CustomEntityModelRenderer.submit(replacement, event.getState(), event.getPoseStack(), event.getNodeCollector());
            ModelReplacementManager.noteRenderedReplacement();
            return true;
        } catch (Exception error) {
            logRenderError("render", error);
            return false;
        }
    }

    private static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        pruneTick++;
        if (pruneTick < 100) {
            return;
        }
        pruneTick = 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        Set<String> activeKeys = new HashSet<>();
        String sessionId = ModelReplacementManager.currentSessionId();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            activeKeys.add(ModelReplacementTarget.entity(entity, sessionId).ruleKey());
        }
        ModelReplacementManager.retainRuntimeInstances(activeKeys);
    }

    private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) ModelReplacementManager::reloadResources);
    }

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("magicmodel")
            .then(Commands.literal("client")
                .then(Commands.literal("replace")
                    .then(replaceTargetCommand())
                    .then(replaceTypeCommand()))
                .then(Commands.literal("animation")
                    .then(Commands.literal("play")
                        .then(Commands.literal("target")
                            .then(Commands.argument("animation", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(ANIMATION_SUGGESTIONS, builder))
                                .executes(context -> playTargetAnimation(
                                    context.getSource(),
                                    StringArgumentType.getString(context, "animation"),
                                    AnimationPlayMode.DEFAULT))
                                .then(Commands.literal("loop")
                                    .executes(context -> playTargetAnimation(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "animation"),
                                        AnimationPlayMode.LOOP)))
                                .then(Commands.literal("once")
                                    .executes(context -> playTargetAnimation(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "animation"),
                                        AnimationPlayMode.ONCE)))))
                        .then(Commands.literal("type")
                            .then(Commands.argument("entity_type", IdentifierArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(suggestEntityTypes(), builder))
                                .then(Commands.argument("animation", StringArgumentType.word())
                                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(ANIMATION_SUGGESTIONS, builder))
                                    .executes(context -> playTypeAnimation(
                                        context.getSource(),
                                        IdentifierArgument.getId(context, "entity_type"),
                                        StringArgumentType.getString(context, "animation"),
                                        AnimationPlayMode.DEFAULT))
                                    .then(Commands.literal("loop")
                                        .executes(context -> playTypeAnimation(
                                            context.getSource(),
                                            IdentifierArgument.getId(context, "entity_type"),
                                            StringArgumentType.getString(context, "animation"),
                                            AnimationPlayMode.LOOP)))
                                    .then(Commands.literal("once")
                                        .executes(context -> playTypeAnimation(
                                            context.getSource(),
                                            IdentifierArgument.getId(context, "entity_type"),
                                            StringArgumentType.getString(context, "animation"),
                                            AnimationPlayMode.ONCE)))))))
                    .then(Commands.literal("stop")
                        .then(Commands.literal("target")
                            .executes(context -> stopTargetAnimation(context.getSource())))
                        .then(Commands.literal("type")
                            .then(Commands.argument("entity_type", IdentifierArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(suggestEntityTypes(), builder))
                                .executes(context -> stopTypeAnimation(
                                    context.getSource(),
                                    IdentifierArgument.getId(context, "entity_type"))))))
                    .then(Commands.literal("reset")
                        .then(Commands.literal("target")
                            .executes(context -> resetTargetAnimation(context.getSource())))
                        .then(Commands.literal("type")
                            .then(Commands.argument("entity_type", IdentifierArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(suggestEntityTypes(), builder))
                                .executes(context -> resetTypeAnimation(
                                    context.getSource(),
                                    IdentifierArgument.getId(context, "entity_type")))))))
                .then(Commands.literal("clear")
                    .then(Commands.literal("target")
                        .executes(context -> clearTarget(context.getSource())))
                    .then(Commands.literal("type")
                        .then(Commands.argument("entity_type", IdentifierArgument.id())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(suggestEntityTypes(), builder))
                            .executes(context -> clearType(context.getSource(), IdentifierArgument.getId(context, "entity_type")))))
                    .then(Commands.literal("all")
                        .executes(context -> {
                            ModelReplacementManager.clearAll();
                            context.getSource().sendSuccess(() -> Component.literal("Cleared all client model replacements"), false);
                            return 1;
                        }))))
            .then(Commands.literal("reload")
                .executes(context -> {
                    ModelReplacementManager.reloadResources();
                    context.getSource().sendSuccess(() -> Component.literal("Reloaded model replacement resources"), false);
                    return 1;
                }))
            .then(Commands.literal("debug")
                .then(Commands.literal("cache")
                    .executes(context -> {
                        String stats = ModelReplacementManager.debugStats().toLogLine();
                        context.getSource().sendSuccess(() -> Component.literal(stats), false);
                        Magicmod.LOGGER.info("[MODEL] {}", stats);
                        return 1;
                    }))
                .then(Commands.literal("dump")
                    .executes(context -> {
                        List<String> lines = ModelReplacementManager.debugDumpLines();
                        for (String line : lines) {
                            context.getSource().sendSuccess(() -> Component.literal(line), false);
                            Magicmod.LOGGER.info("[MODEL] {}", line);
                        }
                        return lines.size();
                    })))
            .then(Commands.literal("server")
                .executes(context -> {
                    context.getSource().sendFailure(Component.literal("Server-side model sync commands are planned but not implemented yet."));
                    return 0;
                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> replaceTargetCommand() {
        return Commands.literal("target")
            .executes(context -> replaceTarget(context.getSource()))
            .then(Commands.argument("model", IdentifierArgument.id())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(SIMPLE_MODEL_SUGGESTIONS, builder))
                .executes(context -> replaceTarget(
                    context.getSource(),
                    IdentifierArgument.getId(context, "model"))))
            .then(Commands.literal("bedrock")
                .then(Commands.argument("geometry", IdentifierArgument.id())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(BEDROCK_GEOMETRY_SUGGESTIONS, builder))
                    .then(Commands.argument("texture", IdentifierArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(BEDROCK_TEXTURE_SUGGESTIONS, builder))
                        .executes(context -> replaceTargetBedrock(
                            context.getSource(),
                            IdentifierArgument.getId(context, "geometry"),
                            IdentifierArgument.getId(context, "texture"),
                            null))
                        .then(Commands.argument("animation_file", IdentifierArgument.id())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(BEDROCK_ANIMATION_SUGGESTIONS, builder))
                            .executes(context -> replaceTargetBedrock(
                                context.getSource(),
                                IdentifierArgument.getId(context, "geometry"),
                                IdentifierArgument.getId(context, "texture"),
                                IdentifierArgument.getId(context, "animation_file")))))))
            .then(Commands.literal("fbx")
                .then(Commands.argument("model", IdentifierArgument.id())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(FBX_MODEL_SUGGESTIONS, builder))
                    .then(Commands.argument("texture", IdentifierArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(BEDROCK_TEXTURE_SUGGESTIONS, builder))
                        .executes(context -> replaceTargetFbx(
                            context.getSource(),
                            IdentifierArgument.getId(context, "model"),
                            IdentifierArgument.getId(context, "texture"),
                            null))
                        .then(Commands.argument("animation_file", IdentifierArgument.id())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(BEDROCK_ANIMATION_SUGGESTIONS, builder))
                            .executes(context -> replaceTargetFbx(
                                context.getSource(),
                                IdentifierArgument.getId(context, "model"),
                                IdentifierArgument.getId(context, "texture"),
                                IdentifierArgument.getId(context, "animation_file")))))))
            .then(Commands.literal("mmesh")
                .then(Commands.argument("model", IdentifierArgument.id())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(MMESH_MODEL_SUGGESTIONS, builder))
                    .then(Commands.argument("texture", IdentifierArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(BEDROCK_TEXTURE_SUGGESTIONS, builder))
                        .executes(context -> replaceTargetMmesh(
                            context.getSource(),
                            IdentifierArgument.getId(context, "model"),
                            IdentifierArgument.getId(context, "texture"),
                            null))
                        .then(Commands.argument("animation_file", IdentifierArgument.id())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(BEDROCK_ANIMATION_SUGGESTIONS, builder))
                            .executes(context -> replaceTargetMmesh(
                                context.getSource(),
                                IdentifierArgument.getId(context, "model"),
                                IdentifierArgument.getId(context, "texture"),
                                IdentifierArgument.getId(context, "animation_file")))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> replaceTypeCommand() {
        return Commands.literal("type")
            .then(Commands.argument("entity_type", IdentifierArgument.id())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(suggestEntityTypes(), builder))
                .executes(context -> replaceType(context.getSource(), IdentifierArgument.getId(context, "entity_type")))
                .then(Commands.argument("model", IdentifierArgument.id())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(SIMPLE_MODEL_SUGGESTIONS, builder))
                    .executes(context -> replaceType(
                        context.getSource(),
                        IdentifierArgument.getId(context, "entity_type"),
                        IdentifierArgument.getId(context, "model"))))
                .then(Commands.literal("bedrock")
                    .then(Commands.argument("geometry", IdentifierArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(BEDROCK_GEOMETRY_SUGGESTIONS, builder))
                        .then(Commands.argument("texture", IdentifierArgument.id())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(BEDROCK_TEXTURE_SUGGESTIONS, builder))
                            .executes(context -> replaceTypeBedrock(
                                context.getSource(),
                                IdentifierArgument.getId(context, "entity_type"),
                                IdentifierArgument.getId(context, "geometry"),
                                IdentifierArgument.getId(context, "texture"),
                                null))
                            .then(Commands.argument("animation_file", IdentifierArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(BEDROCK_ANIMATION_SUGGESTIONS, builder))
                                .executes(context -> replaceTypeBedrock(
                                    context.getSource(),
                                    IdentifierArgument.getId(context, "entity_type"),
                                    IdentifierArgument.getId(context, "geometry"),
                                    IdentifierArgument.getId(context, "texture"),
                                    IdentifierArgument.getId(context, "animation_file")))))))
                .then(Commands.literal("fbx")
                    .then(Commands.argument("model", IdentifierArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(FBX_MODEL_SUGGESTIONS, builder))
                        .then(Commands.argument("texture", IdentifierArgument.id())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(BEDROCK_TEXTURE_SUGGESTIONS, builder))
                            .executes(context -> replaceTypeFbx(
                                context.getSource(),
                                IdentifierArgument.getId(context, "entity_type"),
                                IdentifierArgument.getId(context, "model"),
                                IdentifierArgument.getId(context, "texture"),
                                null))
                            .then(Commands.argument("animation_file", IdentifierArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(BEDROCK_ANIMATION_SUGGESTIONS, builder))
                                .executes(context -> replaceTypeFbx(
                                    context.getSource(),
                                    IdentifierArgument.getId(context, "entity_type"),
                                    IdentifierArgument.getId(context, "model"),
                                    IdentifierArgument.getId(context, "texture"),
                                    IdentifierArgument.getId(context, "animation_file")))))))
                .then(Commands.literal("mmesh")
                    .then(Commands.argument("model", IdentifierArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(MMESH_MODEL_SUGGESTIONS, builder))
                        .then(Commands.argument("texture", IdentifierArgument.id())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(BEDROCK_TEXTURE_SUGGESTIONS, builder))
                            .executes(context -> replaceTypeMmesh(
                                context.getSource(),
                                IdentifierArgument.getId(context, "entity_type"),
                                IdentifierArgument.getId(context, "model"),
                                IdentifierArgument.getId(context, "texture"),
                                null))
                            .then(Commands.argument("animation_file", IdentifierArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(BEDROCK_ANIMATION_SUGGESTIONS, builder))
                                .executes(context -> replaceTypeMmesh(
                                    context.getSource(),
                                    IdentifierArgument.getId(context, "entity_type"),
                                    IdentifierArgument.getId(context, "model"),
                                    IdentifierArgument.getId(context, "texture"),
                                    IdentifierArgument.getId(context, "animation_file"))))))));
    }

    private static int replaceTarget(CommandSourceStack source) {
        return replaceTarget(source, "test_cube");
    }

    private static int replaceTarget(CommandSourceStack source, String rawModel) {
        ModelAssetSpec modelSpec = parseSimpleModel(rawModel);
        if (modelSpec == null) {
            source.sendFailure(Component.literal("Unsupported model: " + rawModel + ". Available: test_cube"));
            return 0;
        }
        return replaceTarget(source, modelSpec);
    }

    private static int replaceTarget(CommandSourceStack source, Identifier model) {
        ModelAssetSpec modelSpec = parseSimpleModel(model);
        if (modelSpec == null) {
            source.sendFailure(Component.literal("Unsupported model: " + model + ". Available: test_cube"));
            return 0;
        }
        return replaceTarget(source, modelSpec);
    }

    private static int replaceTargetBedrock(
        CommandSourceStack source,
        Identifier geometry,
        Identifier texture,
        Identifier animation
    ) {
        ModelAssetSpec modelSpec = ModelAssetSpec.bedrock(geometry, texture, animation, ModelRenderMode.CUTOUT);
        return replaceTarget(source, modelSpec);
    }

    private static int replaceTargetFbx(
        CommandSourceStack source,
        Identifier model,
        Identifier texture,
        Identifier animation
    ) {
        ModelAssetSpec modelSpec = ModelAssetSpec.fbx(model, texture, animation, ModelRenderMode.CUTOUT);
        return replaceTarget(source, modelSpec);
    }

    private static int replaceTargetMmesh(
        CommandSourceStack source,
        Identifier model,
        Identifier texture,
        Identifier animation
    ) {
        ModelAssetSpec modelSpec = ModelAssetSpec.mmesh(model, texture, animation, ModelRenderMode.CUTOUT);
        return replaceTarget(source, modelSpec);
    }

    private static int replaceTarget(CommandSourceStack source, ModelAssetSpec modelSpec) {
        Entity target = targetEntity();
        if (target == null) {
            source.sendFailure(Component.literal("No client player or visible target entity."));
            return 0;
        }
        try {
            ModelReplacementManager.replaceEntity(target, modelSpec);
        } catch (Exception error) {
            Magicmod.LOGGER.warn("[MODEL] Failed to replace target entity with {}", modelSpec.displayName(), error);
            source.sendFailure(Component.literal("Failed to load model: " + modelSpec.displayName()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Replaced target entity with " + modelSpec.displayName()), false);
        return 1;
    }

    private static int replaceType(CommandSourceStack source, String rawEntityType) {
        return replaceType(source, rawEntityType, "test_cube");
    }

    private static int replaceType(CommandSourceStack source, Identifier entityType) {
        return replaceType(source, entityType, ModelAssetSpec.TEST_CUBE, "entity type");
    }

    private static int replaceType(CommandSourceStack source, String rawEntityType, String rawModel) {
        Identifier entityType = parseEntityType(rawEntityType);
        if (entityType == null) {
            source.sendFailure(Component.literal("Unknown entity type: " + rawEntityType));
            return 0;
        }
        ModelAssetSpec modelSpec = parseSimpleModel(rawModel);
        if (modelSpec == null) {
            source.sendFailure(Component.literal("Unsupported model: " + rawModel + ". Available: test_cube"));
            return 0;
        }
        return replaceType(source, entityType, modelSpec, "entity type");
    }

    private static int replaceType(CommandSourceStack source, Identifier entityType, Identifier model) {
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(entityType)) {
            source.sendFailure(Component.literal("Unknown entity type: " + entityType));
            return 0;
        }
        ModelAssetSpec modelSpec = parseSimpleModel(model);
        if (modelSpec == null) {
            source.sendFailure(Component.literal("Unsupported model: " + model + ". Available: test_cube"));
            return 0;
        }
        return replaceType(source, entityType, modelSpec, "entity type");
    }

    private static int replaceTypeBedrock(
        CommandSourceStack source,
        Identifier entityType,
        Identifier geometry,
        Identifier texture,
        Identifier animation
    ) {
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(entityType)) {
            source.sendFailure(Component.literal("Unknown entity type: " + entityType));
            return 0;
        }
        ModelAssetSpec modelSpec = ModelAssetSpec.bedrock(geometry, texture, animation, ModelRenderMode.CUTOUT);
        return replaceType(source, entityType, modelSpec, "entity type");
    }

    private static int replaceTypeFbx(
        CommandSourceStack source,
        Identifier entityType,
        Identifier model,
        Identifier texture,
        Identifier animation
    ) {
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(entityType)) {
            source.sendFailure(Component.literal("Unknown entity type: " + entityType));
            return 0;
        }
        ModelAssetSpec modelSpec = ModelAssetSpec.fbx(model, texture, animation, ModelRenderMode.CUTOUT);
        return replaceType(source, entityType, modelSpec, "entity type");
    }

    private static int replaceTypeMmesh(
        CommandSourceStack source,
        Identifier entityType,
        Identifier model,
        Identifier texture,
        Identifier animation
    ) {
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(entityType)) {
            source.sendFailure(Component.literal("Unknown entity type: " + entityType));
            return 0;
        }
        ModelAssetSpec modelSpec = ModelAssetSpec.mmesh(model, texture, animation, ModelRenderMode.CUTOUT);
        return replaceType(source, entityType, modelSpec, "entity type");
    }

    private static int replaceType(CommandSourceStack source, Identifier entityType, ModelAssetSpec modelSpec, String label) {
        try {
            ModelReplacementManager.replaceType(entityType, modelSpec);
        } catch (Exception error) {
            Magicmod.LOGGER.warn("[MODEL] Failed to replace {} {} with {}", label, entityType, modelSpec.displayName(), error);
            source.sendFailure(Component.literal("Failed to load model: " + modelSpec.displayName()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Replaced " + label + " with " + modelSpec.displayName() + ": " + entityType), false);
        return 1;
    }

    private static int playTargetAnimation(CommandSourceStack source, String animation, AnimationPlayMode playMode) {
        Entity target = targetEntity();
        if (target == null) {
            source.sendFailure(Component.literal("No visible target entity."));
            return 0;
        }
        if (!ModelReplacementManager.playEntityAnimation(target, animation, playMode)) {
            source.sendFailure(Component.literal("No replacement or animation for target entity."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Playing target animation: " + animation + " " + playMode.name().toLowerCase()), false);
        return 1;
    }

    private static int playTypeAnimation(CommandSourceStack source, Identifier entityType, String animation, AnimationPlayMode playMode) {
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(entityType)
            || !ModelReplacementManager.playTypeAnimation(entityType, animation, playMode)) {
            source.sendFailure(Component.literal("No replacement or animation for entity type: " + entityType));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Playing animation " + animation + " for " + entityType + " " + playMode.name().toLowerCase()), false);
        return 1;
    }

    private static int stopTargetAnimation(CommandSourceStack source) {
        Entity target = targetEntity();
        if (target == null) {
            source.sendFailure(Component.literal("No visible target entity."));
            return 0;
        }
        if (!ModelReplacementManager.stopEntityAnimation(target)) {
            source.sendFailure(Component.literal("No replacement for target entity."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Stopped target animation"), false);
        return 1;
    }

    private static int stopTypeAnimation(CommandSourceStack source, Identifier entityType) {
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(entityType) || !ModelReplacementManager.stopTypeAnimation(entityType)) {
            source.sendFailure(Component.literal("No replacement for entity type: " + entityType));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Stopped animation for " + entityType), false);
        return 1;
    }

    private static int resetTargetAnimation(CommandSourceStack source) {
        Entity target = targetEntity();
        if (target == null) {
            source.sendFailure(Component.literal("No visible target entity."));
            return 0;
        }
        if (!ModelReplacementManager.resetEntityAnimation(target)) {
            source.sendFailure(Component.literal("No replacement for target entity."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Reset target animation"), false);
        return 1;
    }

    private static int resetTypeAnimation(CommandSourceStack source, Identifier entityType) {
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(entityType) || !ModelReplacementManager.resetTypeAnimation(entityType)) {
            source.sendFailure(Component.literal("No replacement for entity type: " + entityType));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Reset animation for " + entityType), false);
        return 1;
    }

    private static int clearTarget(CommandSourceStack source) {
        Entity target = targetEntity();
        if (target == null) {
            source.sendFailure(Component.literal("No visible target entity."));
            return 0;
        }
        if (!ModelReplacementManager.clearEntity(target)) {
            source.sendFailure(Component.literal("No replacement for target entity."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Cleared target replacement"), false);
        return 1;
    }

    private static int clearType(CommandSourceStack source, Identifier entityType) {
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(entityType) || !ModelReplacementManager.clearType(entityType)) {
            source.sendFailure(Component.literal("No replacement for entity type: " + entityType));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Cleared type replacement: " + entityType), false);
        return 1;
    }

    private static Entity targetEntity() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return null;
        }
        if (minecraft.crosshairPickEntity != null) {
            return minecraft.crosshairPickEntity;
        }
        return minecraft.player;
    }

    private static Entity findRenderedEntity(LivingEntityRenderState state) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || state == null) {
            return null;
        }
        Entity best = null;
        double bestDistance = 4.0D;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity.getType() != state.entityType) {
                continue;
            }
            double distance = entity.distanceToSqr(state.x, state.y, state.z);
            if (distance < bestDistance) {
                best = entity;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static Identifier parseEntityType(String rawEntityType) {
        Identifier id = Identifier.tryParse(rawEntityType);
        if (id == null || !ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
            return null;
        }
        return id;
    }

    private static ModelAssetSpec parseSimpleModel(String rawModel) {
        if (rawModel == null || rawModel.isBlank() || "test_cube".equals(rawModel)) {
            return ModelAssetSpec.TEST_CUBE;
        }
        Identifier id = Identifier.tryParse(rawModel);
        if (ModelCache.TEST_CUBE_ID.equals(id)) {
            return ModelAssetSpec.TEST_CUBE;
        }
        return null;
    }

    private static ModelAssetSpec parseSimpleModel(Identifier model) {
        if (model == null) {
            return ModelAssetSpec.TEST_CUBE;
        }
        if (ModelCache.TEST_CUBE_ID.equals(model)) {
            return ModelAssetSpec.TEST_CUBE;
        }
        if ("minecraft".equals(model.getNamespace()) && "test_cube".equals(model.getPath())) {
            return ModelAssetSpec.TEST_CUBE;
        }
        return null;
    }

    private static List<String> suggestEntityTypes() {
        return ForgeRegistries.ENTITY_TYPES.getKeys().stream()
            .map(Identifier::toString)
            .sorted()
            .toList();
    }

    private static void logRenderError(String stage, Exception error) {
        long now = System.currentTimeMillis();
        if (now - lastRenderErrorAt < 1000L) {
            return;
        }
        lastRenderErrorAt = now;
        Magicmod.LOGGER.error("[MODEL] Failed to {} custom entity model; falling back to vanilla render", stage, error);
    }
}
