package org.test.magicmod.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class UiScreen extends Screen {
    private final UiConfig config;
    private final List<UiConfig.UiElement> elements;
    private final Map<String, UiConfig.UiElement> elementById;
    private final Map<UiConfig.UiElement, Boolean> visibilityOverrides = new IdentityHashMap<>();
    private final Map<UiConfig.UiElement, ScrollState> scrollStates = new IdentityHashMap<>();
    private final long startTimeMs;
    private SlotProvider slotProvider;
    private boolean overlayMode;
    private boolean openEventFired;
    private boolean closeEventFired;
    private UiConfig.UiElement hoveredElement;

    public UiScreen(UiConfig config) {
        super(Component.literal(config.title));
        this.config = config;
        this.elements = new ArrayList<>(config.elements);
        sortElementsByZ();
        this.elementById = indexElements(this.elements);
        this.startTimeMs = System.currentTimeMillis();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderOverlay(guiGraphics, mouseX, mouseY, partialTick);
        if (!overlayMode) {
            super.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    public void renderOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateHoverState(mouseX, mouseY);
        if (config.drawBackground) {
            renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        }
        for (UiConfig.UiElement element : elements) {
            renderElement(guiGraphics, element, 0, 0, width, height, 0.0f, 0.0f, mouseX, mouseY);
        }
    }

    @Override
    protected void init() {
        fireOpenEvent();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (config.allowMove) {
            updateMovementKeys();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && handleEscape()) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        UiConfig.UiBackground background = config.background;
        if (background.gradientTo != null) {
            guiGraphics.fillGradient(0, 0, width, height, background.color, background.gradientTo);
        } else {
            guiGraphics.fill(0, 0, width, height, background.color);
        }
        if (background.texture != null && !background.texture.isBlank()) {
            ResourceLocation texture = parseLocation(background.texture);
            if (texture != null) {
                guiGraphics.blit(texture, 0, 0, 0, 0, width, height, background.textureWidth, background.textureHeight);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (handleMouseScroll(mouseX, mouseY, deltaX, deltaY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleMouseClick(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public boolean handleMouseScroll(double mouseX, double mouseY, double deltaX, double deltaY) {
        return handleScroll(elements, 0, 0, width, height, 0.0f, 0.0f, mouseX, mouseY, deltaX, deltaY);
    }

    public boolean handleMouseClick(double mouseX, double mouseY, int button) {
        UiConfig.UiElement element = findHitElement(elements, 0, 0, width, height, 0.0f, 0.0f, mouseX, mouseY);
        if (element == null) {
            return false;
        }
        String actionKey = resolveActionKey(button, Screen.hasShiftDown());
        return runElementAction(element, actionKey);
    }

    private void renderElement(GuiGraphics guiGraphics, UiConfig.UiElement element, int originX, int originY,
                               int contextWidth, int contextHeight, float scrollX, float scrollY,
                               int mouseX, int mouseY) {
        if (!isVisible(element)) {
            return;
        }
        int baseX = originX + Math.round(element.x.resolve(contextWidth, contextHeight, contextWidth) - scrollX);
        int baseY = originY + Math.round(element.y.resolve(contextWidth, contextHeight, contextHeight) - scrollY);
        switch (element.type) {
            case TEXT -> renderText(guiGraphics, element, baseX, baseY);
            case IMAGE -> renderImage(guiGraphics, element, baseX, baseY);
            case RECT -> renderRect(guiGraphics, element, baseX, baseY);
            case PROGRESS -> renderProgress(guiGraphics, element, baseX, baseY);
            case SCROLL -> renderScroll(guiGraphics, element, baseX, baseY, mouseX, mouseY);
            case SLOT -> renderSlot(guiGraphics, element, baseX, baseY, mouseX, mouseY);
        }
    }

    private void renderText(GuiGraphics guiGraphics, UiConfig.UiElement element, int x, int y) {
        String resolvedText = resolveText(element.text);
        if (resolvedText == null || resolvedText.isBlank()) {
            return;
        }
        float scale = Math.max(0.1f, element.scale);
        Component component = buildTextComponent(element, resolvedText);
        int textWidth = Math.round(font.width(component) * scale);
        int textHeight = Math.round(font.lineHeight * scale);
        int alignedX = applyTextAlign(element.align, x, textWidth);
        int drawX = element.anchor.applyX(alignedX, textWidth);
        int drawY = element.anchor.applyY(y, textHeight);

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(drawX, drawY);
        guiGraphics.pose().scale(scale, scale);
        guiGraphics.drawString(font, component, 0, 0, element.color, element.shadow);
        guiGraphics.pose().popMatrix();
    }

    private void renderImage(GuiGraphics guiGraphics, UiConfig.UiElement element, int x, int y) {
        if (element.texture == null || element.texture.isBlank()) {
            return;
        }
        if (element.width <= 0 || element.height <= 0) {
            return;
        }
        ResourceLocation texture = parseLocation(element.texture);
        if (texture == null) {
            return;
        }
        int drawX = element.anchor.applyX(x, element.width);
        int drawY = element.anchor.applyY(y, element.height);

        if (element.texture.toLowerCase().endsWith(".gif")) {
            GifManager.GifFrame frame = GifManager.getFrame(texture);
            if (frame != null) {
                guiGraphics.blit(frame.location(), drawX, drawY, element.u, element.v, element.width, element.height,
                    frame.width(), frame.height());
                return;
            }
        }

        guiGraphics.blit(texture, drawX, drawY, element.u, element.v, element.width, element.height,
            element.textureWidth, element.textureHeight);
    }

    private void renderRect(GuiGraphics guiGraphics, UiConfig.UiElement element, int x, int y) {
        if (element.width <= 0 || element.height <= 0) {
            return;
        }
        int drawX = element.anchor.applyX(x, element.width);
        int drawY = element.anchor.applyY(y, element.height);
        guiGraphics.fill(drawX, drawY, drawX + element.width, drawY + element.height, element.color);
    }

    private void renderProgress(GuiGraphics guiGraphics, UiConfig.UiElement element, int x, int y) {
        if (element.width <= 0 || element.height <= 0) {
            return;
        }
        int drawX = element.anchor.applyX(x, element.width);
        int drawY = element.anchor.applyY(y, element.height);
        if (element.backgroundColor != 0) {
            guiGraphics.fill(drawX, drawY, drawX + element.width, drawY + element.height, element.backgroundColor);
        }

        float progress = resolveProgress(element);
        int fillWidth = Math.round(element.width * progress);
        if (fillWidth > 0) {
            guiGraphics.fill(drawX, drawY, drawX + fillWidth, drawY + element.height, element.fillColor);
        }
    }

    private void renderScroll(GuiGraphics guiGraphics, UiConfig.UiElement element, int x, int y,
                              int mouseX, int mouseY) {
        if (element.width <= 0 || element.height <= 0) {
            return;
        }
        int drawX = element.anchor.applyX(x, element.width);
        int drawY = element.anchor.applyY(y, element.height);
        if (element.color != 0) {
            guiGraphics.fill(drawX, drawY, drawX + element.width, drawY + element.height, element.color);
        }

        ScrollState state = getScrollState(element);
        clampScrollState(element, state);

        guiGraphics.enableScissor(drawX, drawY, drawX + element.width, drawY + element.height);
        try {
            for (UiConfig.UiElement child : element.children) {
                renderElement(guiGraphics, child, drawX, drawY, element.width, element.height, state.x, state.y,
                    mouseX, mouseY);
            }
        } finally {
            guiGraphics.disableScissor();
        }
    }

    private void renderSlot(GuiGraphics guiGraphics, UiConfig.UiElement element, int x, int y,
                            int mouseX, int mouseY) {
        if (slotProvider == null) {
            return;
        }
        int slotWidth = element.width > 0 ? element.width : 16;
        int slotHeight = element.height > 0 ? element.height : 16;
        int drawX = element.anchor.applyX(x, slotWidth);
        int drawY = element.anchor.applyY(y, slotHeight);
        boolean hovered = element.hoverMask
            && mouseX >= drawX && mouseX <= drawX + slotWidth
            && mouseY >= drawY && mouseY <= drawY + slotHeight;
        ItemStack stack = slotProvider.getStack(element.slotIndex);
        if (stack != null && !stack.isEmpty()) {
            float scale = Math.max(0.01f, Math.min(slotWidth, slotHeight) / 16.0f);
            int renderX = drawX + Math.round((slotWidth - 16.0f * scale) * 0.5f);
            int renderY = drawY + Math.round((slotHeight - 16.0f * scale) * 0.5f);

            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().translate(renderX, renderY);
            guiGraphics.pose().scale(scale, scale);
            guiGraphics.renderItem(stack, 0, 0);
            guiGraphics.renderItemDecorations(font, stack, 0, 0);
            guiGraphics.pose().popMatrix();
        }

        if (hovered) {
            guiGraphics.fill(drawX, drawY, drawX + slotWidth, drawY + slotHeight, 0x80FFFFFF);
        }
    }

    private void updateHoverState(int mouseX, int mouseY) {
        UiConfig.UiElement current = findHitElement(elements, 0, 0, width, height, 0.0f, 0.0f, mouseX, mouseY);
        if (current == hoveredElement) {
            return;
        }
        if (hoveredElement != null) {
            runElementAction(hoveredElement, "leave");
        }
        hoveredElement = current;
        if (hoveredElement != null) {
            runElementAction(hoveredElement, "enter");
        }
    }

    private boolean runElementAction(UiConfig.UiElement element, String actionKey) {
        if (element == null || actionKey == null) {
            return false;
        }
        String script = element.actions.get(actionKey);
        if (script == null || script.isBlank()) {
            return false;
        }
        UiScriptEngine.execute(this, script);
        return true;
    }

    private boolean handleScroll(List<UiConfig.UiElement> list, int originX, int originY, int contextWidth,
                                 int contextHeight, float scrollX, float scrollY, double mouseX, double mouseY,
                                 double deltaX, double deltaY) {
        for (int i = list.size() - 1; i >= 0; i--) {
            UiConfig.UiElement element = list.get(i);
            if (!isVisible(element)) {
                continue;
            }
            if (element.type != UiConfig.UiElement.Type.SCROLL) {
                continue;
            }
            if (element.width <= 0 || element.height <= 0) {
                continue;
            }
            int baseX = originX + Math.round(element.x.resolve(contextWidth, contextHeight, contextWidth) - scrollX);
            int baseY = originY + Math.round(element.y.resolve(contextWidth, contextHeight, contextHeight) - scrollY);
            int drawX = element.anchor.applyX(baseX, element.width);
            int drawY = element.anchor.applyY(baseY, element.height);
            ScrollState state = getScrollState(element);

            if (handleScroll(element.children, drawX, drawY, element.width, element.height, state.x, state.y,
                mouseX, mouseY, deltaX, deltaY)) {
                return true;
            }

            if (mouseX >= drawX && mouseX <= drawX + element.width
                && mouseY >= drawY && mouseY <= drawY + element.height) {
                applyScrollDelta(element, state, deltaX, deltaY);
                return true;
            }
        }
        return false;
    }

    private void applyScrollDelta(UiConfig.UiElement element, ScrollState state, double deltaX, double deltaY) {
        ScrollAxis axis = ScrollAxis.from(element.scrollDirection);
        float step = element.scrollStep <= 0 ? 12.0f : element.scrollStep;
        if (axis.allowsHorizontal) {
            state.x -= (float) (deltaX * step);
        }
        if (axis.allowsVertical) {
            state.y -= (float) (deltaY * step);
        }
        clampScrollState(element, state);
    }

    private void clampScrollState(UiConfig.UiElement element, ScrollState state) {
        ScrollBounds bounds = computeScrollBounds(element);
        ScrollAxis axis = ScrollAxis.from(element.scrollDirection);
        float minX = axis.allowsHorizontal ? bounds.minX : 0.0f;
        float maxX = axis.allowsHorizontal ? Math.max(minX, bounds.maxX - element.width) : minX;
        float minY = axis.allowsVertical ? bounds.minY : 0.0f;
        float maxY = axis.allowsVertical ? Math.max(minY, bounds.maxY - element.height) : minY;
        state.x = clamp(state.x, minX, maxX);
        state.y = clamp(state.y, minY, maxY);
    }

    private ScrollBounds computeScrollBounds(UiConfig.UiElement element) {
        if (element.children.isEmpty()) {
            return new ScrollBounds(0.0f, 0.0f, 0.0f, 0.0f);
        }
        boolean hasChild = false;
        float minX = 0.0f;
        float minY = 0.0f;
        float maxX = 0.0f;
        float maxY = 0.0f;
        for (UiConfig.UiElement child : element.children) {
            if (!isVisible(child)) {
                continue;
            }
            ElementBox box = getElementBox(child, element.width, element.height, 0.0f, 0.0f);
            if (box == null) {
                continue;
            }
            if (!hasChild) {
                minX = box.x;
                minY = box.y;
                maxX = box.x + box.width;
                maxY = box.y + box.height;
                hasChild = true;
            } else {
                minX = Math.min(minX, box.x);
                minY = Math.min(minY, box.y);
                maxX = Math.max(maxX, box.x + box.width);
                maxY = Math.max(maxY, box.y + box.height);
            }
        }
        if (!hasChild) {
            return new ScrollBounds(0.0f, 0.0f, 0.0f, 0.0f);
        }
        return new ScrollBounds(minX, maxX, minY, maxY);
    }

    private ElementBox getElementBox(UiConfig.UiElement element, int contextWidth, int contextHeight,
                                     float scrollX, float scrollY) {
        int x = Math.round(element.x.resolve(contextWidth, contextHeight, contextWidth) - scrollX);
        int y = Math.round(element.y.resolve(contextWidth, contextHeight, contextHeight) - scrollY);
        switch (element.type) {
            case TEXT -> {
                if (element.text == null || element.text.isBlank()) {
                    return null;
                }
                float scale = Math.max(0.1f, element.scale);
                String resolvedText = resolveText(element.text);
                if (resolvedText == null || resolvedText.isBlank()) {
                    return null;
                }
                Component component = buildTextComponent(element, resolvedText);
                int textWidth = Math.round(font.width(component) * scale);
                int textHeight = Math.round(font.lineHeight * scale);
                int alignedX = applyTextAlign(element.align, x, textWidth);
                int drawX = element.anchor.applyX(alignedX, textWidth);
                int drawY = element.anchor.applyY(y, textHeight);
                return new ElementBox(drawX, drawY, textWidth, textHeight);
            }
            case IMAGE, RECT, PROGRESS, SCROLL, SLOT -> {
                int width = element.width > 0 ? element.width : (element.type == UiConfig.UiElement.Type.SLOT ? 16 : 0);
                int height = element.height > 0 ? element.height : (element.type == UiConfig.UiElement.Type.SLOT ? 16 : 0);
                if (width <= 0 || height <= 0) {
                    return null;
                }
                int drawX = element.anchor.applyX(x, width);
                int drawY = element.anchor.applyY(y, height);
                return new ElementBox(drawX, drawY, width, height);
            }
            default -> {
                return null;
            }
        }
    }

    private ScrollState getScrollState(UiConfig.UiElement element) {
        return scrollStates.computeIfAbsent(element, key -> new ScrollState(key.scrollX, key.scrollY));
    }

    private UiConfig.UiElement findHitElement(List<UiConfig.UiElement> list, int originX, int originY, int contextWidth,
                                              int contextHeight, float scrollX, float scrollY, double mouseX,
                                              double mouseY) {
        for (int i = list.size() - 1; i >= 0; i--) {
            UiConfig.UiElement element = list.get(i);
            if (!isVisible(element)) {
                continue;
            }
            ElementBox box = getElementBox(element, contextWidth, contextHeight, scrollX, scrollY);
            if (element.type == UiConfig.UiElement.Type.SCROLL) {
                if (box == null) {
                    continue;
                }
                if (mouseX < originX + box.x || mouseX > originX + box.x + box.width
                    || mouseY < originY + box.y || mouseY > originY + box.y + box.height) {
                    continue;
                }
                ScrollState state = getScrollState(element);
                int childOriginX = originX + Math.round(box.x);
                int childOriginY = originY + Math.round(box.y);
                UiConfig.UiElement childHit = findHitElement(element.children, childOriginX, childOriginY,
                    element.width, element.height, state.x, state.y, mouseX, mouseY);
                if (childHit != null) {
                    return childHit;
                }
                if (!element.actions.isEmpty()) {
                    return element;
                }
                continue;
            }
            if (box == null) {
                continue;
            }
            if (mouseX >= originX + box.x && mouseX <= originX + box.x + box.width
                && mouseY >= originY + box.y && mouseY <= originY + box.y + box.height) {
                return element;
            }
        }
        return null;
    }

    private float resolveProgress(UiConfig.UiElement element) {
        if ("time".equalsIgnoreCase(element.mode)) {
            long duration = Math.max(1, element.durationMs);
            long elapsed = (System.currentTimeMillis() - startTimeMs) % duration;
            return elapsed / (float) duration;
        }
        float max = element.max <= 0 ? 1.0f : element.max;
        return Math.min(1.0f, Math.max(0.0f, element.value / max));
    }

    private int applyTextAlign(String align, int x, int textWidth) {
        if (align == null) {
            return x;
        }
        return switch (align.toLowerCase()) {
            case "center" -> x - textWidth / 2;
            case "right" -> x - textWidth;
            default -> x;
        };
    }

    private Component buildTextComponent(UiConfig.UiElement element, String text) {
        net.minecraft.network.chat.MutableComponent component = Component.literal(text);
        String fontName = element.font;
        if (fontName == null || fontName.isBlank()) {
            return component;
        }
        if ("original".equalsIgnoreCase(fontName)) {
            return component;
        }
        ResourceLocation fontId = parseFontLocation(fontName);
        if (fontId == null) {
            return component;
        }
        return component.withStyle(style -> style.withFont(fontId));
    }

    private String resolveText(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        if (!raw.contains("{")) {
            return raw;
        }
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        float health = player != null ? player.getHealth() : 0.0f;
        float maxHealth = player != null ? player.getMaxHealth() : 0.0f;
        int food = player != null ? player.getFoodData().getFoodLevel() : 0;
        float saturation = player != null ? player.getFoodData().getSaturationLevel() : 0.0f;
        int level = player != null ? player.experienceLevel : 0;
        float expProgress = player != null ? player.experienceProgress : 0.0f;
        int expTotal = player != null ? player.totalExperience : 0;
        var target = minecraft.crosshairPickEntity;
        String targetType = "";
        String targetName = "";
        float targetHealth = 0.0f;
        if (target != null) {
            targetType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();
            targetName = target.getName().getString();
            if (target instanceof net.minecraft.world.entity.LivingEntity living) {
                targetHealth = living.getHealth();
            }
        }
        String result = raw;
        result = result.replace("{health}", formatNumber(health));
        result = result.replace("{max_health}", formatNumber(maxHealth));
        result = result.replace("{food}", Integer.toString(food));
        result = result.replace("{saturation}", formatNumber(saturation));
        result = result.replace("{exp_level}", Integer.toString(level));
        result = result.replace("{exp}", formatPercent(expProgress));
        result = result.replace("{exp_total}", Integer.toString(expTotal));
        result = result.replace("{target_type}", targetType);
        result = result.replace("{target_name}", targetName);
        result = result.replace("{target_health}", formatNumber(targetHealth));
        return result;
    }

    private String formatPercent(float value) {
        return formatNumber(value * 100.0f);
    }

    private String formatNumber(float value) {
        if (Math.abs(value - Math.round(value)) < 0.01f) {
            return Integer.toString(Math.round(value));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private ResourceLocation parseFontLocation(String raw) {
        String value = raw.trim();
        if (!value.contains(":")) {
            value = "magicmod:" + value;
        }
        return parseLocation(value);
    }

    private ResourceLocation parseLocation(String raw) {
        try {
            return ResourceLocation.parse(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public void setElementVisible(String id, boolean visible) {
        UiConfig.UiElement element = elementById.get(id);
        if (element != null) {
            visibilityOverrides.put(element, visible);
        }
    }

    public void toggleElement(String id) {
        UiConfig.UiElement element = elementById.get(id);
        if (element != null) {
            boolean current = isVisible(element);
            visibilityOverrides.put(element, !current);
        }
    }

    public void openUi(String uiName) {
        try {
            UiConfig config = UiLoader.load(uiName);
            Minecraft.getInstance().setScreen(new UiScreen(config));
        } catch (Exception ignored) {
            // Ignore script errors for now.
        }
    }

    public void setSlotProvider(SlotProvider slotProvider) {
        this.slotProvider = slotProvider;
    }

    public void setOverlayMode(boolean overlayMode) {
        this.overlayMode = overlayMode;
    }

    public boolean isReplaceVanilla() {
        return config.replaceVanilla;
    }

    public boolean addElement(Map<String, Object> elementData) {
        return addElement(null, elementData);
    }

    public boolean addElement(String parentId, Map<String, Object> elementData) {
        if (elementData == null || elementData.isEmpty()) {
            return false;
        }
        UiConfig.UiElement element = UiConfig.UiElement.fromMap(elementData);
        if (element.id != null && !element.id.isBlank() && elementById.containsKey(element.id)) {
            return false;
        }
        if (parentId == null || parentId.isBlank() || "root".equalsIgnoreCase(parentId)) {
            elements.add(element);
            sortElementsByZ();
            registerElement(element);
            return true;
        }
        UiConfig.UiElement parent = elementById.get(parentId);
        if (parent == null) {
            return false;
        }
        ensureMutableChildren(parent);
        parent.children.add(element);
        parent.children.sort(Comparator.comparingInt(child -> child.z));
        registerElement(element);
        return true;
    }

    public void setElementProperty(String id, String property, Object value) {
        if (id == null || property == null) {
            return;
        }
        UiConfig.UiElement element = elementById.get(id);
        if (element == null) {
            return;
        }
        String key = property.trim().toLowerCase();
        switch (key) {
            case "x" -> element.x = UiValue.of(value, element.x != null ? element.x.resolve(width, height, width) : 0.0f);
            case "y" -> element.y = UiValue.of(value, element.y != null ? element.y.resolve(width, height, height) : 0.0f);
            case "width" -> element.width = asInt(value, element.width);
            case "height" -> element.height = asInt(value, element.height);
            case "z" -> {
                element.z = asInt(value, element.z);
                sortElementsByZ();
            }
            case "visible" -> visibilityOverrides.put(element, asBoolean(value, element.visible));
            case "text" -> element.text = asString(value, element.text);
            case "scale" -> element.scale = asFloat(value, element.scale);
            case "shadow" -> element.shadow = asBoolean(value, element.shadow);
            case "align" -> element.align = asString(value, element.align);
            case "color" -> element.color = UiConfig.parseColor(value, element.color);
            case "font" -> element.font = asString(value, element.font);
            case "texture" -> element.texture = asString(value, element.texture);
            case "u" -> element.u = asInt(value, element.u);
            case "v" -> element.v = asInt(value, element.v);
            case "texture_width" -> element.textureWidth = asInt(value, element.textureWidth);
            case "texture_height" -> element.textureHeight = asInt(value, element.textureHeight);
            case "fill_color" -> element.fillColor = UiConfig.parseColor(value, element.fillColor);
            case "bg_color" -> element.backgroundColor = UiConfig.parseColor(value, element.backgroundColor);
            case "mode" -> element.mode = asString(value, element.mode);
            case "value" -> element.value = asFloat(value, element.value);
            case "max" -> element.max = asFloat(value, element.max);
            case "duration_ms" -> element.durationMs = asInt(value, element.durationMs);
            case "scroll_direction" -> element.scrollDirection = asString(value, element.scrollDirection);
            case "scroll_step" -> element.scrollStep = asFloat(value, element.scrollStep);
            case "scroll_x" -> {
                element.scrollX = asFloat(value, element.scrollX);
                ScrollState state = scrollStates.get(element);
                if (state != null) {
                    state.x = element.scrollX;
                }
            }
            case "scroll_y" -> {
                element.scrollY = asFloat(value, element.scrollY);
                ScrollState state = scrollStates.get(element);
                if (state != null) {
                    state.y = element.scrollY;
                }
            }
            case "slot" -> element.slotIndex = asInt(value, element.slotIndex);
            case "hover_mask", "slot_hover" -> element.hoverMask = asBoolean(value, element.hoverMask);
            default -> {
            }
        }
    }

    public void fireCloseEvent() {
        if (closeEventFired) {
            return;
        }
        closeEventFired = true;
        runEventScript("close");
    }

    private boolean isVisible(UiConfig.UiElement element) {
        Boolean override = visibilityOverrides.get(element);
        return override != null ? override : element.visible;
    }

    private void fireOpenEvent() {
        if (openEventFired) {
            return;
        }
        openEventFired = true;
        runEventScript("open");
    }

    private boolean runEventScript(String key) {
        if (key == null) {
            return false;
        }
        String script = config.events.get(key);
        if (script == null || script.isBlank()) {
            return false;
        }
        UiScriptEngine.execute(this, script);
        return true;
    }

    private Map<String, UiConfig.UiElement> indexElements(List<UiConfig.UiElement> list) {
        Map<String, UiConfig.UiElement> map = new HashMap<>();
        indexElementsInto(map, list);
        return map;
    }

    private void indexElementsInto(Map<String, UiConfig.UiElement> map, List<UiConfig.UiElement> list) {
        for (UiConfig.UiElement element : list) {
            if (element.id != null && !element.id.isBlank()) {
                map.put(element.id, element);
            }
            if (!element.children.isEmpty()) {
                indexElementsInto(map, element.children);
            }
        }
    }

    private void registerElement(UiConfig.UiElement element) {
        if (element.id != null && !element.id.isBlank()) {
            elementById.put(element.id, element);
        }
        if (!element.children.isEmpty()) {
            indexElementsInto(elementById, element.children);
        }
    }

    private void ensureMutableChildren(UiConfig.UiElement element) {
        if (element.children == null) {
            element.children = new ArrayList<>();
        } else if (!(element.children instanceof ArrayList<?>)) {
            element.children = new ArrayList<>(element.children);
        }
    }

    private String resolveActionKey(int button, boolean shiftDown) {
        if (shiftDown) {
            return switch (button) {
                case 0 -> "shift_left_click";
                case 1 -> "shift_right_click";
                case 2 -> "shift_middle_click";
                default -> "shift_click";
            };
        }
        return switch (button) {
            case 0 -> "left_click";
            case 1 -> "right_click";
            case 2 -> "middle_click";
            default -> "click";
        };
    }

    private boolean handleEscape() {
        if (runEventScript("esc") || runEventScript("escape")) {
            return true;
        }
        return config.overrideEsc;
    }

    private void sortElementsByZ() {
        elements.sort(Comparator.comparingInt(element -> element.z));
    }

    private void updateMovementKeys() {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        long window = minecraft.getWindow().getWindow();
        setKeyDown(minecraft.options.keyUp, window);
        setKeyDown(minecraft.options.keyDown, window);
        setKeyDown(minecraft.options.keyLeft, window);
        setKeyDown(minecraft.options.keyRight, window);
        setKeyDown(minecraft.options.keyJump, window);
        setKeyDown(minecraft.options.keyShift, window);
        setKeyDown(minecraft.options.keySprint, window);
    }

    private void resetMovementKeys() {
        if (minecraft == null) {
            return;
        }
        setKeyState(minecraft.options.keyUp, false);
        setKeyState(minecraft.options.keyDown, false);
        setKeyState(minecraft.options.keyLeft, false);
        setKeyState(minecraft.options.keyRight, false);
        setKeyState(minecraft.options.keyJump, false);
        setKeyState(minecraft.options.keyShift, false);
        setKeyState(minecraft.options.keySprint, false);
    }

    private void setKeyDown(KeyMapping mapping, long window) {
        if (mapping == null) {
            return;
        }
        boolean down = InputConstants.isKeyDown(window, mapping.getKey().getValue());
        mapping.setDown(down);
    }

    private void setKeyState(KeyMapping mapping, boolean down) {
        if (mapping != null) {
            mapping.setDown(down);
        }
    }

    private int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private float asFloat(Object value, float fallback) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value != null) {
            try {
                return Float.parseFloat(value.toString());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private boolean asBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value != null) {
            return Boolean.parseBoolean(value.toString());
        }
        return fallback;
    }

    private String asString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString();
        return text.isBlank() ? fallback : text;
    }

    @Override
    public void onClose() {
        fireCloseEvent();
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void removed() {
        fireCloseEvent();
        if (config.allowMove) {
            resetMovementKeys();
        }
    }

    public interface SlotProvider {
        ItemStack getStack(int slotIndex);
    }

    private enum ScrollAxis {
        HORIZONTAL(true, false),
        VERTICAL(false, true),
        BOTH(true, true);

        private final boolean allowsHorizontal;
        private final boolean allowsVertical;

        ScrollAxis(boolean allowsHorizontal, boolean allowsVertical) {
            this.allowsHorizontal = allowsHorizontal;
            this.allowsVertical = allowsVertical;
        }

        static ScrollAxis from(String raw) {
            if (raw == null) {
                return VERTICAL;
            }
            String value = raw.trim().toLowerCase();
            return switch (value) {
                case "horizontal" -> HORIZONTAL;
                case "both" -> BOTH;
                default -> VERTICAL;
            };
        }
    }

    private static final class ScrollState {
        private float x;
        private float y;

        private ScrollState(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class ScrollBounds {
        private final float minX;
        private final float maxX;
        private final float minY;
        private final float maxY;

        private ScrollBounds(float minX, float maxX, float minY, float maxY) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
        }
    }

    private static final class ElementBox {
        private final float x;
        private final float y;
        private final float width;
        private final float height;

        private ElementBox(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}
