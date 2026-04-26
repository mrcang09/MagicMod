package org.test.magicmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraftforge.event.TickEvent;
import org.test.magicmod.Magicmod;

public final class ClientSmokeTest {
    private static final boolean ENABLED = Boolean.getBoolean("magicmod.smokeTest");
    private static final boolean EXIT_ON_COMPLETE = Boolean.getBoolean("magicmod.smokeTestExit");
    private static final int START_DELAY_TICKS = 80;
    private static final int STEP_DELAY_TICKS = 50;
    private static int tickCount;
    private static int step = -1;
    private static boolean complete;

    private ClientSmokeTest() {
    }

    public static void register() {
        if (!ENABLED) {
            return;
        }
        TickEvent.ClientTickEvent.Post.BUS.addListener(ClientSmokeTest::onClientTick);
        Magicmod.LOGGER.info("[SMOKE] MagicMod client smoke test enabled");
    }

    public static boolean isEnabled() {
        return ENABLED;
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
            Magicmod.LOGGER.info("[SMOKE] Singleplayer world detected; starting UI smoke sequence");
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
            Magicmod.LOGGER.error("[SMOKE] FAILED at step {}", step, error);
        }
    }

    private static void runStep(Minecraft minecraft, int currentStep) throws Exception {
        switch (currentStep) {
            case 0 -> openUi("loading.yml");
            case 1 -> openUi("shader_demo.yml");
            case 2 -> openUi("perf_test_no_shader.yml");
            case 3 -> openUi("perf_test_shader_no_atlas.yml");
            case 4 -> openUi("perf_test_shader_atlas.yml");
            case 5 -> {
                minecraft.setScreen(new InventoryScreen(minecraft.player));
                Magicmod.LOGGER.info("[SMOKE] Opened vanilla InventoryScreen for overlay replacement test");
            }
            case 6 -> {
                minecraft.setScreen(new ContainerScreen(
                    ChestMenu.threeRows(1, minecraft.player.getInventory()),
                    minecraft.player.getInventory(),
                    Component.literal("Chest")));
                Magicmod.LOGGER.info("[SMOKE] Opened vanilla ContainerScreen for chest overlay replacement test");
            }
            case 7 -> {
                minecraft.setScreen(new PauseScreen(true));
                Magicmod.LOGGER.info("[SMOKE] Opened vanilla PauseScreen for ESC replacement test");
            }
            case 8 -> {
                minecraft.setScreen(null);
                complete = true;
                Magicmod.LOGGER.info("[SMOKE] Completed MagicMod client smoke sequence");
                if (EXIT_ON_COMPLETE) {
                    Magicmod.LOGGER.info("[SMOKE] Requesting graceful client shutdown");
                    minecraft.stop();
                }
            }
            default -> complete = true;
        }
    }

    private static void openUi(String uiName) throws Exception {
        ClientUiCommands.openUi(uiName);
        Magicmod.LOGGER.info("[SMOKE] Opened UI through command path: {}", uiName);
    }
}
