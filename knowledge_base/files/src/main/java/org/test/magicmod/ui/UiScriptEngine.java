package org.test.magicmod.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.core.Holder;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.test.magicmod.audio.AudioController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class UiScriptEngine {
    private UiScriptEngine() {
    }

    public static void execute(UiScreen screen, String script) {
        Context context = Context.enter();
        context.setOptimizationLevel(-1);
        try {
            Scriptable scope = context.initStandardObjects();
            UiScriptApi api = new UiScriptApi(screen);
            ScriptableObject.putProperty(scope, "ui", Context.javaToJS(api, scope));
            context.evaluateString(scope, script, "ui", 1, null);
        } finally {
            Context.exit();
        }
    }

    public static final class UiScriptApi {
        private final UiScreen screen;

        private UiScriptApi(UiScreen screen) {
            this.screen = screen;
        }

        public void open(String uiName) {
            screen.openUi(uiName);
        }

        public void show(String id) {
            screen.setElementVisible(id, true);
        }

        public void hide(String id) {
            screen.setElementVisible(id, false);
        }

        public void toggle(String id) {
            screen.toggleElement(id);
        }

        public void set(String id, String property, Object value) {
            screen.setElementProperty(id, property, value);
        }

        public boolean create(Object element) {
            return create(null, element);
        }

        public boolean create(String parentId, Object element) {
            Map<String, Object> data = toMap(element);
            if (data == null || data.isEmpty()) {
                return false;
            }
            return screen.addElement(parentId, data);
        }

        public void close() {
            Minecraft.getInstance().setScreen(null);
        }

        public void playMusic(String soundId) {
            AudioController.playMusic(soundId, 1.0f, false, 0);
        }

        public void playMusic(String soundId, double volume) {
            AudioController.playMusic(soundId, (float) volume, false, 0);
        }

        public void playMusic(String soundId, double volume, boolean loop) {
            AudioController.playMusic(soundId, (float) volume, loop, 0);
        }

        public void playMusic(String soundId, double volume, boolean loop, double stopAtMs) {
            AudioController.playMusic(soundId, (float) volume, loop, (int) Math.round(stopAtMs));
        }

        public void stopMusic(String soundId) {
            AudioController.stopMusic(soundId);
        }

        public void stopAllMusic() {
            AudioController.stopAllMusic();
        }

        public double getHealth() {
            var player = Minecraft.getInstance().player;
            return player == null ? 0.0 : player.getHealth();
        }

        public double getMaxHealth() {
            var player = Minecraft.getInstance().player;
            return player == null ? 0.0 : player.getMaxHealth();
        }

        public int getFood() {
            var player = Minecraft.getInstance().player;
            return player == null ? 0 : player.getFoodData().getFoodLevel();
        }

        public double getFoodSaturation() {
            var player = Minecraft.getInstance().player;
            return player == null ? 0.0 : player.getFoodData().getSaturationLevel();
        }

        public int getExperienceLevel() {
            var player = Minecraft.getInstance().player;
            return player == null ? 0 : player.experienceLevel;
        }

        public double getExperienceProgress() {
            var player = Minecraft.getInstance().player;
            return player == null ? 0.0 : player.experienceProgress;
        }

        public int getExperienceTotal() {
            var player = Minecraft.getInstance().player;
            return player == null ? 0 : player.totalExperience;
        }

        public String getTargetEntityType() {
            var entity = Minecraft.getInstance().crosshairPickEntity;
            if (entity == null) {
                return "";
            }
            return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        }

        public String getTargetEntityName() {
            var entity = Minecraft.getInstance().crosshairPickEntity;
            return entity == null ? "" : entity.getName().getString();
        }

        public double getTargetEntityHealth() {
            var entity = Minecraft.getInstance().crosshairPickEntity;
            if (entity instanceof LivingEntity living) {
                return living.getHealth();
            }
            return 0.0;
        }

        public double getAttribute(String attributeId) {
            var player = Minecraft.getInstance().player;
            if (player == null || attributeId == null || attributeId.isBlank()) {
                return 0.0;
            }
            ResourceLocation location;
            try {
                location = ResourceLocation.parse(attributeId.trim());
            } catch (Exception ignored) {
                return 0.0;
            }
            java.util.Optional<Holder.Reference<net.minecraft.world.entity.ai.attributes.Attribute>> holder =
                BuiltInRegistries.ATTRIBUTE.get(location);
            if (holder.isEmpty()) {
                return 0.0;
            }
            AttributeInstance instance = player.getAttribute(holder.get());
            return instance == null ? 0.0 : instance.getValue();
        }
    }

    private static Map<String, Object> toMap(Object value) {
        Object unwrapped = unwrapValue(value);
        if (unwrapped instanceof Map<?, ?> raw) {
            Map<String, Object> map = new HashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                map.put(entry.getKey().toString(), unwrapValue(entry.getValue()));
            }
            return map;
        }
        return null;
    }

    private static Object unwrapValue(Object value) {
        if (value == null || value instanceof Undefined) {
            return null;
        }
        if (value instanceof Scriptable scriptable) {
            if (value instanceof NativeArray array) {
                List<Object> list = new ArrayList<>();
                for (Object id : array.getIds()) {
                    int index = id instanceof Number number ? number.intValue() : -1;
                    if (index >= 0) {
                        list.add(unwrapValue(array.get(index, array)));
                    }
                }
                return list;
            }
            Map<String, Object> map = new HashMap<>();
            for (Object id : scriptable.getIds()) {
                String key = id.toString();
                Object child = ScriptableObject.getProperty(scriptable, key);
                map.put(key, unwrapValue(child));
            }
            return map;
        }
        return value;
    }
}
