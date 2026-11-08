package com.dimaulyana.eightblades;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class EightBladesClient implements ClientModInitializer {
    private static boolean storyStarted = false;
    private static int currentStage = 1;
    private static boolean swordCrafted = false;
    private static String roleDisplay = "§7Не выбрана (ПКМ Компасом)";
    private static boolean synergyActive = false;
    private static List<String> objectiveLines = new ArrayList<>(List.of(
            "§e1. Откройте Компас (ПКМ) и выберите Роль",
            "§a2. Введите команду §f/st §aдля старта сюжета"
    ));

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(EightBladesMod.HUD_SYNC_ID, (client, handler, buf, responseSender) -> {
            boolean started = buf.readBoolean();
            int stage = buf.readInt();
            boolean crafted = buf.readBoolean();
            String role = buf.readString();
            boolean synergy = buf.readBoolean();
            int count = buf.readInt();
            List<String> lines = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                lines.add(buf.readString());
            }

            client.execute(() -> {
                storyStarted = started;
                currentStage = stage;
                swordCrafted = crafted;
                roleDisplay = role;
                synergyActive = synergy;
                objectiveLines = lines;
            });
        });

        HudRenderCallback.EVENT.register(this::renderStoryHud);
    }

    private void renderStoryHud(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.options.debugEnabled) {
            return;
        }

        TextRenderer tr = client.textRenderer;
        int x = 8;
        int y = 8;

        List<String> renderLines = new ArrayList<>();
        String header = storyStarted
                ? (currentStage <= 8 ? "§6§l⚔ Дима и Ульяна §e[Этап " + currentStage + "/8]" : "§6§l★ Сюжет Завершен [8/8]")
                : "§6§l⚔ Дима и Ульяна §7[Ожидание /st]";
        renderLines.add(header);
        renderLines.add("§7Роль: " + roleDisplay);
        renderLines.add("§7Резонанс Дуэта: " + (synergyActive ? "§a§lАКТИВЕН (+Сила)" : "§8Нужны 2 разные роли рядом"));
        renderLines.add("§8-------------------------");
        renderLines.addAll(objectiveLines);

        int maxWidth = 170;
        for (String line : renderLines) {
            int w = tr.getWidth(line);
            if (w > maxWidth) {
                maxWidth = w;
            }
        }

        int padding = 6;
        int lineHeight = 11;
        int boxWidth = maxWidth + padding * 2;
        int boxHeight = renderLines.size() * lineHeight + padding * 2;

        // Background & border
        context.fill(x, y, x + boxWidth, y + boxHeight, 0xB80E131F);
        context.fill(x, y, x + boxWidth, y + 2, 0xFFFFAA00);
        context.fill(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, 0xFF554422);
        context.fill(x, y, x + 2, y + boxHeight, 0xFFFFAA00);

        int curY = y + padding;
        for (String line : renderLines) {
            context.drawTextWithShadow(tr, line, x + padding + 2, curY, 0xFFFFFF);
            curY += lineHeight;
        }
    }
}
