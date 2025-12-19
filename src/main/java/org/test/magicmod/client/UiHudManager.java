package org.test.magicmod.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import org.test.magicmod.Magicmod;
import org.test.magicmod.ui.UiConfig;
import org.test.magicmod.ui.UiLoader;
import org.test.magicmod.ui.UiScreen;

import java.io.IOException;

public final class UiHudManager {
    private static final ResourceLocation HUD_LAYER = ResourceLocation.fromNamespaceAndPath(Magicmod.MODID, "hud_layer");
    private static UiConfig hudConfig;
    private static UiScreen hudScreen;
    private static boolean loadFailed;

    private UiHudManager() {
    }

    public static void register(BusGroup modBusGroup) {
        AddGuiOverlayLayersEvent.getBus(modBusGroup).addListener(UiHudManager::onAddGuiLayers);
    }

    private static void onAddGuiLayers(AddGuiOverlayLayersEvent event) {
        ForgeLayeredDraw layeredDraw = event.getLayeredDraw();
        layeredDraw.addAbove(ForgeLayeredDraw.VANILLA_ROOT, HUD_LAYER, ForgeLayeredDraw.POST_SLEEP_STACK, UiHudManager::renderHud);
        disableVanillaHudLayers(layeredDraw);
    }

    private static void renderHud(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (!shouldRenderHud()) {
            return;
        }
        UiScreen screen = getHudScreen();
        if (screen == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int mouseX = scaleMouse(minecraft.mouseHandler.xpos(), width, minecraft.getWindow().getScreenWidth());
        int mouseY = scaleMouse(minecraft.mouseHandler.ypos(), height, minecraft.getWindow().getScreenHeight());
        screen.renderOverlay(guiGraphics, mouseX, mouseY, deltaTracker.getGameTimeDeltaPartialTick(false));
    }

    private static void disableVanillaHudLayers(ForgeLayeredDraw layeredDraw) {
        ForgeLayeredDraw pre = layeredDraw.getChild(ForgeLayeredDraw.PRE_SLEEP_STACK);
        if (pre != null) {
            pre.addConditionTo(ForgeLayeredDraw.CAMERA_OVERLAY, UiHudManager::shouldAllowVanillaHud);
            pre.addConditionTo(ForgeLayeredDraw.CROSSHAIR, UiHudManager::shouldAllowVanillaHud);
            pre.addConditionTo(ForgeLayeredDraw.CHANGE_STRATUM, UiHudManager::shouldAllowVanillaHud);
            pre.addConditionTo(ForgeLayeredDraw.HOTBAR_AND_DECOS, UiHudManager::shouldAllowVanillaHud);
            pre.addConditionTo(ForgeLayeredDraw.POTION_EFFECTS, UiHudManager::shouldAllowVanillaHud);
            pre.addConditionTo(ForgeLayeredDraw.BOSS_OVERLAY, UiHudManager::shouldAllowVanillaHud);
        }
        ForgeLayeredDraw post = layeredDraw.getChild(ForgeLayeredDraw.POST_SLEEP_STACK);
        if (post != null) {
            post.addConditionTo(ForgeLayeredDraw.DEMO_OVERLAY, UiHudManager::shouldAllowVanillaHud);
            post.addConditionTo(ForgeLayeredDraw.DEBUG_OVERLAY, UiHudManager::shouldAllowVanillaHud);
            post.addConditionTo(ForgeLayeredDraw.SCOREBOARD, UiHudManager::shouldAllowVanillaHud);
            post.addConditionTo(ForgeLayeredDraw.HOTBAR_MESSAGE, UiHudManager::shouldAllowVanillaHud);
            post.addConditionTo(ForgeLayeredDraw.TITLE_OVERLAY, UiHudManager::shouldAllowVanillaHud);
            post.addConditionTo(ForgeLayeredDraw.CHAT_OVERLAY, UiHudManager::shouldAllowVanillaHud);
            post.addConditionTo(ForgeLayeredDraw.TAB_LIST, UiHudManager::shouldAllowVanillaHud);
            post.addConditionTo(ForgeLayeredDraw.SUBTITLE_OVERLAY, UiHudManager::shouldAllowVanillaHud);
        }
    }

    private static boolean shouldRenderHud() {
        UiConfig config = getHudConfig();
        if (config == null || !config.hud) {
            return false;
        }
        if (!isInWorld()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return !minecraft.options.hideGui;
    }

    private static boolean shouldAllowVanillaHud() {
        UiConfig config = getHudConfig();
        if (config == null || !config.replaceHud) {
            return true;
        }
        if (!isInWorld()) {
            return true;
        }
        return false;
    }

    private static UiConfig getHudConfig() {
        if (hudConfig != null || loadFailed) {
            return hudConfig;
        }
        try {
            hudConfig = UiLoader.load("hud.yml");
        } catch (IOException ignored) {
            loadFailed = true;
            return null;
        }
        return hudConfig;
    }

    private static UiScreen getHudScreen() {
        UiConfig config = getHudConfig();
        if (config == null) {
            return null;
        }
        if (hudScreen == null) {
            hudScreen = new UiScreen(config);
            hudScreen.setOverlayMode(true);
            hudScreen.setSlotProvider(UiHudManager::getHudSlot);
        }
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        hudScreen.init(minecraft, width, height);
        return hudScreen;
    }

    private static ItemStack getHudSlot(int slotIndex) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return ItemStack.EMPTY;
        }
        if (slotIndex == 40) {
            return player.getOffhandItem();
        }
        if (slotIndex == 41) {
            return player.getMainHandItem();
        }
        if (slotIndex >= 0 && slotIndex < player.getInventory().getContainerSize()) {
            return player.getInventory().getItem(slotIndex);
        }
        return ItemStack.EMPTY;
    }

    private static boolean isInWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.player != null;
    }

    private static int scaleMouse(double raw, int scaledSize, int screenSize) {
        if (screenSize <= 0) {
            return 0;
        }
        return (int) Math.round(raw * scaledSize / (double) screenSize);
    }
}
