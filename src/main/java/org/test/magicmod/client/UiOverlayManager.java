package org.test.magicmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ScreenEvent;
import org.test.magicmod.ui.UiConfig;
import org.test.magicmod.ui.UiLoader;
import org.test.magicmod.ui.UiScreen;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class UiOverlayManager {
    private static final Map<Screen, OverlayEntry> OVERLAYS = new WeakHashMap<>();

    private UiOverlayManager() {
    }

    public static void register() {
        ScreenEvent.Render.Pre.BUS.addListener(UiOverlayManager::onRenderPre);
        ScreenEvent.Render.Post.BUS.addListener(UiOverlayManager::onRender);
        ScreenEvent.MouseButtonPressed.Pre.BUS.addListener(UiOverlayManager::onMousePressed);
        ScreenEvent.MouseScrolled.Pre.BUS.addListener(UiOverlayManager::onMouseScrolled);
        ScreenEvent.Closing.BUS.addListener(UiOverlayManager::onScreenClosed);
    }

    private static boolean onRenderPre(ScreenEvent.Render.Pre event) {
        UiScreen overlay = getOverlayFor(event.getScreen());
        if (overlay == null || !overlay.isReplaceVanilla()) {
            return false;
        }
        overlay.renderOverlay(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick());
        return true;
    }

    private static void onRender(ScreenEvent.Render.Post event) {
        UiScreen overlay = getOverlayFor(event.getScreen());
        if (overlay == null || overlay.isReplaceVanilla()) {
            return;
        }
        overlay.renderOverlay(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick());
    }

    private static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        UiScreen overlay = getOverlayFor(event.getScreen());
        if (overlay == null) {
            return;
        }
        overlay.handleMouseClick(event.getMouseX(), event.getMouseY(), event.getButton());
    }

    private static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        UiScreen overlay = getOverlayFor(event.getScreen());
        if (overlay == null) {
            return;
        }
        overlay.handleMouseScroll(event.getMouseX(), event.getMouseY(), event.getDeltaX(), event.getDeltaY());
    }

    private static void onScreenClosed(ScreenEvent.Closing event) {
        OverlayEntry entry = OVERLAYS.remove(event.getScreen());
        if (entry == null || entry.overlay == null) {
            return;
        }
        entry.overlay.fireCloseEvent();
    }

    public static UiScreen getOverlayFor(Screen screen) {
        UiMatch match = resolveUiMatch(screen);
        if (match == null) {
            return null;
        }
        String uiName = match.uiName;
        OverlayEntry entry = OVERLAYS.get(screen);
        if (entry == null || !entry.uiName.equals(uiName)) {
            UiConfig config;
            try {
                config = UiLoader.load(uiName);
            } catch (Exception ignored) {
                return null;
            }
            if (match.title != null && !matchesTitle(config, match.title)) {
                entry = new OverlayEntry(uiName, null);
                OVERLAYS.put(screen, entry);
                return null;
            }
            UiScreen overlay = new UiScreen(config);
            overlay.setOverlayMode(true);
            entry = new OverlayEntry(uiName, overlay);
            OVERLAYS.put(screen, entry);
        }

        if (entry.overlay == null) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        entry.overlay.init(minecraft, width, height);
        entry.overlay.setSlotProvider(buildSlotProvider(screen));
        return entry.overlay;
    }

    private static UiMatch resolveUiMatch(Screen screen) {
        if (screen instanceof InventoryScreen) {
            return new UiMatch("inventory.yml", null);
        }
        String className = screen.getClass().getSimpleName();
        if (className.contains("ChestScreen")) {
            return new UiMatch("chest.yml", screen.getTitle());
        }
        return null;
    }

    private static UiScreen.SlotProvider buildSlotProvider(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> container)) {
            return null;
        }
        List<Slot> slots = container.getMenu().slots;
        return slotIndex -> {
            if (slotIndex < 0 || slotIndex >= slots.size()) {
                return ItemStack.EMPTY;
            }
            return slots.get(slotIndex).getItem();
        };
    }

    private static boolean matchesTitle(UiConfig config, Component title) {
        if (config.matchTitles.isEmpty()) {
            return true;
        }
        if (title == null) {
            return false;
        }
        String titleText = normalizeTitle(title.getString());
        for (String raw : config.matchTitles) {
            String normalized = normalizeTitle(raw);
            if (!normalized.isEmpty() && normalized.equals(titleText)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeTitle(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().replace('&', '\u00A7');
        if (normalized.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (ch == '\u00A7' && i + 1 < normalized.length()) {
                i++;
                continue;
            }
            builder.append(ch);
        }
        return builder.toString();
    }

    private record OverlayEntry(String uiName, UiScreen overlay) {
    }

    private record UiMatch(String uiName, Component title) {
    }
}
