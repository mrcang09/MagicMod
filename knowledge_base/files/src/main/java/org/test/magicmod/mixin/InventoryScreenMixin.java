package org.test.magicmod.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.test.magicmod.client.UiOverlayManager;
import org.test.magicmod.ui.UiScreen;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void magicmod$replaceRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick,
                                        CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        UiScreen overlay = UiOverlayManager.getOverlayFor(self);
        if (overlay == null || !overlay.isReplaceVanilla()) {
            return;
        }
        overlay.renderOverlay(guiGraphics, mouseX, mouseY, partialTick);
        ci.cancel();
    }
}
