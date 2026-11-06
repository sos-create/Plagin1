package ru.soscreate.astralnexus.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.packet.PayloadTypeRegistry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class AstralNexusClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.playC2S().register(OpenMenuPayload.ID, OpenMenuPayload.CODEC);

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof InventoryScreen)) return;

            int buttonWidth = 112;
            int buttonHeight = 20;
            int x = (scaledWidth - buttonWidth) / 2;
            int inventoryTop = (scaledHeight - 166) / 2;
            int y = Math.max(4, inventoryTop - buttonHeight - 3);

            ButtonWidget button = ButtonWidget.builder(
                    Text.literal("✦ Дополнение").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
                    widget -> requestMenu(client)
            ).dimensions(x, y, buttonWidth, buttonHeight).build();

            Screens.getButtons(screen).add(button);
        });
    }

    private static void requestMenu(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return;

        if (!ClientPlayNetworking.canSend(OpenMenuPayload.ID)) {
            client.player.sendMessage(
                    Text.literal("На сервере не обнаружен Astral Nexus RPG 1.2.0. Обнови плагин на сервере."),
                    true
            );
            return;
        }

        ClientPlayNetworking.send(new OpenMenuPayload());
    }
}
