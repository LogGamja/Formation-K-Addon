package loggamja.formationk;

import loggamja.formationk.option.Config;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.resources.Identifier;

public class Overtake implements ClientModInitializer {
    static Minecraft client = Minecraft.getInstance();

    static boolean isChargingOvertake = false;
    static boolean isOvertakeActive = false;

    static int overtakeChargeTick;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            overtakeGauge();
        });

        // 바닐라 요소(경험치 레벨) 뒤에 붙이면 바닐라가 그 요소를 그릴 때만 호출된다. 경험치 레벨이 0이거나 모험 모드면 호출되지 않아서 독립 요소로 등록한다
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("formationk-official", "overtake_gauge_hud"),
                (context, tickCounter) -> render(context, 0));
    }
    public static void overtakeGauge() {
        if (!Main.isRidingKart || !Main.isPlayingInGame() || !Config.INSTANCE.useOvertakeGauge) return;

        var ridingPlayer = Main.getRidingPlayer();
        var effect = ridingPlayer.getEffect(MobEffects.WIND_CHARGED);
        var overtakeState = Main.getS2CValue(ridingPlayer, "state-overtake");

        if (effect != null && effect.getAmplifier() == 169) {
            overtakeChargeTick = effect.getDuration() - 10;

            isChargingOvertake = 0 < overtakeChargeTick && overtakeChargeTick <= 50;
            isOvertakeActive = overtakeChargeTick > 100;
        }
        else if (overtakeState != 0) {
            overtakeChargeTick--;

            if (!isChargingOvertake && (overtakeState == 1)) overtakeChargeTick = 50;
            if (!isOvertakeActive && (overtakeState == 2)) overtakeChargeTick = 390;

            isChargingOvertake = overtakeState == 1;
            isOvertakeActive = overtakeState == 2;
        }
        else {
            overtakeChargeTick = 0;
            isChargingOvertake = false;
            isOvertakeActive = false;
        }
    }
    public void render(GuiGraphicsExtractor context, float tickDelta) {
        if (!Main.isRidingKart || !Main.isPlayingInGame() || !Config.INSTANCE.useOvertakeGauge) return;

        int blueColor = 0xFF0080FF;
        int yellowColor = 0xFFFFFF00;
        if (isChargingOvertake) {
            float progress = (50 - overtakeChargeTick) / 50f;
            renderVerticalGauge(context, blueColor, progress, "");
        }
        if (isOvertakeActive) {
            if (389 <= overtakeChargeTick) {
                renderVerticalGauge(context, yellowColor, 1, "");
            }
            else if (387 <= overtakeChargeTick) {
                renderVerticalGauge(context, blueColor, 1, "");
            }
            else if (362 <= overtakeChargeTick) {
                renderVerticalGauge(context, yellowColor, 1, "OVER\nTAKE");
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

        int gaugeWidth = 4;
        int gaugeHeight = 35;
        int gaugeX = screenWidth / 2 - 35;
        int gaugeY = screenHeight / 2 - gaugeHeight / 2;

        int backgroundColor = 0x88000000;

        context.fill(gaugeX, gaugeY, gaugeX + gaugeWidth, gaugeY + gaugeHeight, backgroundColor);

        int filledHeight = (int)(gaugeHeight * Math.max(0, Math.min(1, fillRatio)));
        int fillTop = gaugeY + gaugeHeight - filledHeight;

        context.fill(gaugeX, fillTop, gaugeX + gaugeWidth, gaugeY + gaugeHeight, color);

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
    }
}