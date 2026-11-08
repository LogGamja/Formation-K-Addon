package loggamja.formationk;

import loggamja.formationk.option.Config;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class Overtake implements ClientModInitializer {
    private static final double GUI_SCALE_BASIS = 4.0;

    static int lastOvertakeState = 0;

    static int overtakeChargeTick;
    static int prevOvertakeChargeTick;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            overtakeGauge();
        });

        // 바닐라 요소(경험치 레벨) 뒤에 붙이면 바닐라가 그 요소를 그릴 때만 호출된다. 경험치 레벨이 0이거나 모험 모드면 호출되지 않아서 독립 요소로 등록한다
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("formationk-official", "overtake_gauge_hud"),
                (context, tickCounter) -> render(context, tickCounter.getGameTimeDeltaPartialTick(false)));
    }
    public static void overtakeGauge() {
        if (!Main.isCameraPlayerRidingKart || !Main.isPlayingInGame() || !Config.INSTANCE.useOvertakeGauge) return;

        var overtakeState = Main.getS2CValue(Main.getCameraPlayer(), "state-overtake");

        if (overtakeState != 0) {
            prevOvertakeChargeTick = overtakeChargeTick;
            overtakeChargeTick--;

            if (overtakeState != lastOvertakeState) {
                if (overtakeState == 1) overtakeChargeTick = prevOvertakeChargeTick = 50;
                if (overtakeState == 2) overtakeChargeTick = prevOvertakeChargeTick = 390;
            }
            lastOvertakeState = overtakeState;
        }
        else {
            resetGauge();
        }
    }
    static void resetGauge() {
        overtakeChargeTick = 0;
        prevOvertakeChargeTick = 0;
        lastOvertakeState = 0;
    }
    public void render(GuiGraphicsExtractor context, float tickDelta) {
        if (!Main.isCameraPlayerRidingKart || !Main.isPlayingInGame() || !Config.INSTANCE.useOvertakeGauge) return;

        int blueColor = 0xFF0080FF;
        int yellowColor = 0xFFFFFF00;
        if (lastOvertakeState == 1) {
            float chargeTick = Mth.lerp(tickDelta, prevOvertakeChargeTick, overtakeChargeTick);
            float progress = (50 - chargeTick) / 50f;
            renderVerticalGauge(context, blueColor, progress, "");
        }
        if (lastOvertakeState == 2) {
            if (389 <= overtakeChargeTick) {
                renderVerticalGauge(context, yellowColor, 1, "");
            }
            else if (387 <= overtakeChargeTick) {
                renderVerticalGauge(context, blueColor, 1, "");
            }
            else if (362 <= overtakeChargeTick) {
                renderVerticalGauge(context, yellowColor, 1, "OVERTAKE");
            }
            else if (360 <= overtakeChargeTick) {
                renderVerticalGauge(context, yellowColor, 0, "");
            }
        }
    }
    public void renderVerticalGauge(GuiGraphicsExtractor context, int color, float fillRatio, String text) {
        Minecraft client = Minecraft.getInstance();
        if (!Main.isPlayingInGame()) return;

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        float sizeFactor = (float) (Radar.getSizeFactor(client) * GUI_SCALE_BASIS);

        int gaugeWidth = 4;
        int gaugeHeight = 35;
        int gaugeX = -40;
        int gaugeY = -gaugeHeight / 2;

        int backgroundColor = 0x88000000;

        context.pose().pushMatrix();
        context.pose().translate(screenWidth / 2, screenHeight / 2);
        context.pose().scale(sizeFactor, sizeFactor);

        context.fill(gaugeX, gaugeY, gaugeX + gaugeWidth, gaugeY + gaugeHeight, backgroundColor);

        float filledHeight = gaugeHeight * Mth.clamp(fillRatio, 0f, 1f);
        if (filledHeight > 0f) {
            context.pose().pushMatrix();
            context.pose().translate(gaugeX, gaugeY + gaugeHeight - filledHeight);
            context.pose().scale(1f, filledHeight);
            context.fill(0, 0, gaugeWidth, 1, color);
            context.pose().popMatrix();
        }

        if (text != null && !text.isEmpty()) {
            Font renderer = client.font;
            String[] lines = text.split("\n");

            int anchorX = gaugeX + (gaugeWidth / 2);
            int anchorY = gaugeY - 3;

            float scale = 0.6f;
            {
                context.pose().pushMatrix();
                context.pose().translate(anchorX, anchorY);
                context.pose().scale(scale, scale);

                for (int i = 0; i < lines.length; i++) {
                    int localX = -renderer.width(lines[i]) / 2;
                    int localY = -renderer.lineHeight * (lines.length - i);

                    context.text(renderer, lines[i], localX, localY, color, true);
                }
                context.pose().popMatrix();
            }
        }
        context.pose().popMatrix();
    }
}