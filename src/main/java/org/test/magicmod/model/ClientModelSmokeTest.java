package org.test.magicmod.model;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.test.magicmod.Magicmod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ClientModelSmokeTest {
    private static final boolean ENABLED = Boolean.getBoolean("magicmod.smokeTestModel");
    private static final boolean EXIT_ON_COMPLETE = Boolean.getBoolean("magicmod.smokeTestExit");
    private static final int START_DELAY_TICKS = 80;
    private static final int STEP_DELAY_TICKS = 60;
    private static final Identifier BEDROCK_GEOMETRY = Identifier.fromNamespaceAndPath(
        Magicmod.MODID,
        "entity_models/bedrock/test_creature.geo.json"
    );
    private static final Identifier BEDROCK_TEXTURE = Identifier.fromNamespaceAndPath(
        "minecraft",
        "textures/block/copper_block.png"
    );
    private static final Identifier BEDROCK_ANIMATION_FILE = Identifier.fromNamespaceAndPath(
        Magicmod.MODID,
        "entity_models/bedrock/test_creature.animation.json"
    );
    private static final Identifier FBX_MODEL = Identifier.fromNamespaceAndPath(
        Magicmod.MODID,
        "entity_models/fbx/test_cube_ascii.fbx"
    );
    private static final Identifier MMESH_MODEL = Identifier.fromNamespaceAndPath(
        Magicmod.MODID,
        "entity_models/runtime/test_cube.mmesh"
    );
    private static final String BEDROCK_SPIN_ANIMATION = "animation.magicmod.test_creature.spin";
    private static final String SMOKE_TAG = "magicmod_smoke_target";
    private static final Path SCREENSHOT_PATH = Path.of("D:/MagicMod/docs/model_replacement_stage2_screenshot.png");

    private static int tickCount;
    private static int step = -1;
    private static int renderedBefore;
    private static boolean complete;
    private static CameraType previousCameraType;

    private ClientModelSmokeTest() {
    }

    public static void register() {
        if (!ENABLED) {
            return;
        }
        TickEvent.ClientTickEvent.Post.BUS.addListener(ClientModelSmokeTest::onClientTick);
        Magicmod.LOGGER.info("[MODEL_SMOKE] Model replacement smoke test enabled");
    }

    private static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        if (complete) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            tickCount = 0;
            return;
        }

        tickCount++;
        if (step < 0) {
            if (tickCount < START_DELAY_TICKS) {
                return;
            }
            step = 0;
            tickCount = 0;
            Magicmod.LOGGER.info("[MODEL_SMOKE] Singleplayer world detected; starting model smoke sequence");
        }
        if (tickCount < STEP_DELAY_TICKS) {
            return;
        }
        tickCount = 0;

        try {
            runStep(minecraft, step);
            step++;
        } catch (Exception error) {
            complete = true;
            restoreCamera(minecraft);
            Magicmod.LOGGER.error("[MODEL_SMOKE] FAILED at step {}", step, error);
            if (EXIT_ON_COMPLETE) {
                minecraft.stop();
            }
        }
    }

    private static void runStep(Minecraft minecraft, int currentStep) {
        Identifier playerType = ForgeRegistries.ENTITY_TYPES.getKey(EntityType.PLAYER);
        if (playerType == null) {
            throw new IllegalStateException("Cannot resolve minecraft:player entity type");
        }

        switch (currentStep) {
            case 0 -> {
                previousCameraType = minecraft.options.getCameraType();
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                renderedBefore = ModelReplacementManager.renderedReplacementCount();
                runClientCommand("magicmodel client replace target magicmod:test_cube");
                Magicmod.LOGGER.info("[MODEL_SMOKE] Command replaced target entity with test_cube");
            }
            case 1 -> {
                runClientCommand("magicmodel client animation play target spin loop");
                Magicmod.LOGGER.info("[MODEL_SMOKE] Command playing target spin animation");
            }
            case 2 -> {
                int renderedAfter = ModelReplacementManager.renderedReplacementCount();
                if (renderedAfter <= renderedBefore) {
                    throw new IllegalStateException("Replacement render event did not run; before="
                        + renderedBefore + ", after=" + renderedAfter);
                }
                Magicmod.LOGGER.info("[MODEL_SMOKE] Replacement rendered in-world; before={}, after={}", renderedBefore, renderedAfter);
            }
            case 3 -> {
                runClientCommand("magicmodel client animation play target pulse once");
                Magicmod.LOGGER.info("[MODEL_SMOKE] Command playing target pulse animation once");
            }
            case 4 -> {
                runClientCommand("magicmodel client clear target");
                renderedBefore = ModelReplacementManager.renderedReplacementCount();
                runClientCommand("magicmodel client replace target bedrock "
                    + BEDROCK_GEOMETRY + " " + BEDROCK_TEXTURE + " " + BEDROCK_ANIMATION_FILE);
                Magicmod.LOGGER.info("[MODEL_SMOKE] Command replaced target entity with Bedrock demo model");
            }
            case 5 -> {
                runClientCommand("magicmodel client animation play target " + BEDROCK_SPIN_ANIMATION + " loop");
                Magicmod.LOGGER.info("[MODEL_SMOKE] Command playing Bedrock animation {}", BEDROCK_SPIN_ANIMATION);
            }
            case 6 -> {
                int renderedAfter = ModelReplacementManager.renderedReplacementCount();
                if (renderedAfter <= renderedBefore) {
                    throw new IllegalStateException("Bedrock replacement render event did not run; before="
                        + renderedBefore + ", after=" + renderedAfter);
                }
                Magicmod.LOGGER.info("[MODEL_SMOKE] Bedrock replacement rendered in-world; before={}, after={}", renderedBefore, renderedAfter);
            }
            case 7 -> {
                runClientCommand("magicmodel client animation stop target");
                runClientCommand("magicmodel client animation reset target");
                Magicmod.LOGGER.info("[MODEL_SMOKE] Command stop/reset animation controls are available");
            }
            case 8 -> {
                runClientCommand("magicmodel client clear target");
                minecraft.player.addTag(SMOKE_TAG);
                ModelAssetSpec fbxSpec = ModelAssetSpec.fbx(
                    FBX_MODEL,
                    BEDROCK_TEXTURE,
                    null,
                    ModelRenderMode.CUTOUT
                );
                renderedBefore = ModelReplacementManager.renderedReplacementCount();
                ModelReplacementManager.replaceTag(SMOKE_TAG, fbxSpec);
                Magicmod.LOGGER.info("[MODEL_SMOKE] Tagged player and replaced tag with ASCII FBX demo model");
            }
            case 9 -> {
                if (!ModelReplacementManager.playTagAnimation(SMOKE_TAG, "spin", AnimationPlayMode.ONCE)) {
                    throw new IllegalStateException("Failed to play tag FBX spin animation once");
                }
                Magicmod.LOGGER.info("[MODEL_SMOKE] Playing tag FBX fallback animation once");
            }
            case 10 -> {
                int renderedAfter = ModelReplacementManager.renderedReplacementCount();
                if (renderedAfter <= renderedBefore) {
                    throw new IllegalStateException("FBX replacement render event did not run; before="
                        + renderedBefore + ", after=" + renderedAfter);
                }
                Magicmod.LOGGER.info("[MODEL_SMOKE] FBX replacement rendered in-world; before={}, after={}", renderedBefore, renderedAfter);
            }
            case 11 -> {
                ModelReplacementManager.clearTag(SMOKE_TAG);
                renderedBefore = ModelReplacementManager.renderedReplacementCount();
                runClientCommand("magicmodel client replace target mmesh " + MMESH_MODEL + " " + BEDROCK_TEXTURE);
                Magicmod.LOGGER.info("[MODEL_SMOKE] Command replaced target entity with MMESH runtime model");
            }
            case 12 -> {
                runClientCommand("magicmodel client animation play target spin once");
                Magicmod.LOGGER.info("[MODEL_SMOKE] Command playing MMESH fallback animation once");
            }
            case 13 -> {
                int renderedAfter = ModelReplacementManager.renderedReplacementCount();
                if (renderedAfter <= renderedBefore) {
                    throw new IllegalStateException("MMESH replacement render event did not run; before="
                        + renderedBefore + ", after=" + renderedAfter);
                }
                Magicmod.LOGGER.info("[MODEL_SMOKE] MMESH replacement rendered in-world; before={}, after={}", renderedBefore, renderedAfter);
                saveScreenshot(minecraft);
            }
            case 14 -> {
                runClientCommand("magicmodel client clear target");
                ModelReplacementManager.reloadResources();
                ModelReplacementManager.DebugStats stats = ModelReplacementManager.debugStats();
                if (stats.runtimeInstances() != 0 || stats.loadedModels() != 0 || stats.liveGpuBuffers() != 0) {
                    throw new IllegalStateException("Model cache did not release cleanly: " + stats.toLogLine());
                }
                Magicmod.LOGGER.info("[MODEL_SMOKE] Cache released cleanly: {}", stats.toLogLine());
            }
            case 15 -> {
                complete = true;
                restoreCamera(minecraft);
                Magicmod.LOGGER.info("[MODEL_SMOKE] Completed model replacement smoke sequence");
                if (EXIT_ON_COMPLETE) {
                    Magicmod.LOGGER.info("[MODEL_SMOKE] Requesting graceful client shutdown");
                    minecraft.stop();
                }
            }
            default -> complete = true;
        }
    }

    private static void runClientCommand(String command) {
        boolean handled = ClientCommandHandler.runCommand(command);
        if (!handled) {
            throw new IllegalStateException("Client command was not handled: /" + command);
        }
        Magicmod.LOGGER.info("[MODEL_SMOKE] /{}", command);
    }

    private static void saveScreenshot(Minecraft minecraft) {
        Screenshot.takeScreenshot(minecraft.getMainRenderTarget(), image -> {
            try {
                Files.createDirectories(SCREENSHOT_PATH.getParent());
                image.writeToFile(SCREENSHOT_PATH);
                Magicmod.LOGGER.info("[MODEL_SMOKE] Saved verification screenshot to {}", SCREENSHOT_PATH);
            } catch (IOException error) {
                throw new IllegalStateException("Failed to save verification screenshot", error);
            } finally {
                image.close();
            }
        });
    }

    private static void restoreCamera(Minecraft minecraft) {
        if (previousCameraType != null) {
            minecraft.options.setCameraType(previousCameraType);
        }
    }
}
