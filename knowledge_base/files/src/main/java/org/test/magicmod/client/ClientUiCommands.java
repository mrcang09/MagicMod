package org.test.magicmod.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import org.test.magicmod.ui.UiConfig;
import org.test.magicmod.ui.UiLoader;
import org.test.magicmod.ui.UiScreen;

import java.io.IOException;
import java.util.List;

public final class ClientUiCommands {
    private ClientUiCommands() {
    }

    public static void register() {
        RegisterClientCommandsEvent.BUS.addListener(ClientUiCommands::onRegisterClientCommands);
    }

    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("open")
            .then(Commands.argument("ui", StringArgumentType.string())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(suggestUiFiles(), builder))
                .executes(context -> {
                    String uiName = StringArgumentType.getString(context, "ui");
                    try {
                        UiConfig config = UiLoader.load(uiName);
                        Minecraft.getInstance().setScreen(new UiScreen(config));
                        context.getSource().sendSuccess(() -> Component.literal("Opened UI: " + uiName), false);
                        return 1;
                    } catch (IOException error) {
                        context.getSource().sendFailure(Component.literal("Failed to open UI: " + error.getMessage()));
                        return 0;
                    }
                })));
    }

    private static List<String> suggestUiFiles() {
        try {
            return UiLoader.listUiFiles();
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
