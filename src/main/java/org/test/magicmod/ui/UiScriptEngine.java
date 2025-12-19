package org.test.magicmod.ui;

import net.minecraft.client.Minecraft;
import org.test.magicmod.audio.AudioController;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

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
    }
}
