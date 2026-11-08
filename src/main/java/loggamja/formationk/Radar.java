package loggamja.formationk;

import loggamja.formationk.option.Config;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2fStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Radar implements ClientModInitializer {
    private static final double GUI_SCALE_BASIS = 4.0;

    final double baseDist = 25.0;
    final double distScale = 0.75;
    final double maxDist = baseDist * distScale;

    private static final Identifier ARROW_ICON =
            Identifier.fromNamespaceAndPath("formationk-official", "textures/hud/arrow_icon.png");

    private static final float IMAGE_CORRECTION_TRICK = 0.001f;
    private static final int TEX_SIZE = 16;
    private static final float ARROW_SIZE = (float) (10f * GUI_SCALE_BASIS);
    private static final double ARC_RADIUS_BASE = 46.0 * GUI_SCALE_BASIS;

    private static final int ENEMY_ARROW_RGB = 0xFF0000;
    private static final int SHADOW_ARROW_RGB = 0x808080;   // 고스트는 미니맵과 같은 회색

    public static final String SHADOW_ENTITY_NAME = "formationk-shadow";
    private static final List<Entity> shadowEntities = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        // 바닐라 경험치 레벨 요소가 그려질 때만 호출되는 것을 피하려고 독립 요소로 등록한다
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("formationk-official", "radar_hud"),
                (context, tickCounter) -> renderRadar(context, tickCounter.getGameTimeDeltaPartialTick(false)));

        ClientTickEvents.END_CLIENT_TICK.register(client -> collectShadowEntities(client));
    }
    private static void collectShadowEntities(Minecraft client) {
        shadowEntities.clear();
        if (!Main.isPlayingInGame()) return;
        if (Config.INSTANCE.RadarOption != 1 && Config.INSTANCE.useMinimap == 0) return;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity.isRemoved()) continue;
            if (!(entity instanceof Display)) continue;
            if (!Main.hasCertainName(entity, SHADOW_ENTITY_NAME)) continue;

            shadowEntities.add(entity);
        }
    }
    public static List<Entity> getShadowEntities() {
        return shadowEntities;
    }
    private void renderRadar(GuiGraphicsExtractor context, float tickDelta) {
        if (!Main.isCameraPlayerRidingKart) return;
        if (Config.INSTANCE.RadarOption != 1) return;

        Minecraft client = Minecraft.getInstance();
        if (!Main.isPlayingInGame()) return;

        final double sizeFactor = getSizeFactor(client);

        final int screenWidth  = client.getWindow().getGuiScaledWidth();
        final int screenHeight = client.getWindow().getGuiScaledHeight();

        final int centerX = screenWidth  / 2;
        final int centerY = screenHeight / 2;

        final double arcRadius = ARC_RADIUS_BASE * sizeFactor;
        final float arrowSize = (float) (ARROW_SIZE * sizeFactor);

        final float yawDeg = client.gameRenderer.mainCamera().yRot();
        final double yawRad = Math.toRadians(yawDeg);

        final double fx = -Math.sin(yawRad);
        final double fz =  Math.cos(yawRad);

        final Vec3 p = Main.getCameraPlayer().getEyePosition(tickDelta);

        // 적
        for (Player other : Objects.requireNonNull(client.level).players()) {
            if (other == Main.getCameraPlayer()) continue;
            if (other == other.getRootVehicle()) continue;

            drawArrowIfBehind(context, other.getEyePosition(tickDelta), p, fx, fz, yawRad,
                    centerX, centerY, arcRadius, arrowSize, ENEMY_ARROW_RGB);
        }

        // 고스트
        for (Entity entity : getShadowEntities()) {
            drawArrowIfBehind(context, entity.getPosition(tickDelta), p, fx, fz, yawRad,
                    centerX, centerY, arcRadius, arrowSize, SHADOW_ARROW_RGB);
        }
    }
    private void drawArrowIfBehind(GuiGraphicsExtractor context, Vec3 q, Vec3 p, double fx, double fz, double yawRad,
                                   int centerX, int centerY, double arcRadius, float arrowSize, int rgb) {
        final double dx = q.x - p.x;
        final double dz = q.z - p.z;

        final double dist = Math.sqrt(dx * dx + dz * dz);
        if (!(3 < dist && dist < (maxDist + 3))) return;

        final double dotProduct = dx * fx + dz * fz;
        if (dotProduct > 0) return;

        final double angle = Math.atan2(dz, dx) - yawRad;

        final float arrowX = (float)(centerX - Math.cos(angle) * arcRadius);
        final float arrowY = (float)(centerY - Math.sin(angle) * arcRadius);

        final double minAlpha = 0;
        int a = (int)((1.0 - (dist / (maxDist + 3)) * (1.0 - minAlpha)) * 255);

        // angle은 좌표계 기준이므로 degrees로 변환, -90도 보정(텍스처 위쪽=앞)
        final float arrowRotDeg = (float)Math.toDegrees(angle) - 90f + IMAGE_CORRECTION_TRICK;

        drawArrowIcon(context, arrowX, arrowY, arrowSize, arrowRotDeg, a, rgb);
    }
    public static double getSizeFactor(Minecraft client) {
        // getFramebufferHeight()를 써야 운영체제 디스플레이 배율을 무시함
        final double physicalScale = client.getWindow().getHeight() / 1080.0;
        final double scaleFactor = client.getWindow().getGuiScale();
        return physicalScale / scaleFactor;
    }
    private void drawArrowIcon(GuiGraphicsExtractor context, float cx, float cy,
                               float size, float rotationDeg, int alpha, int rgb) {
        Matrix3x2fStack matrices = context.pose();
        matrices.pushMatrix();

        matrices.translate(cx, cy);
        matrices.rotate((float) Math.toRadians(rotationDeg));
        matrices.translate(-size / 2f, -size / 2f);

        int color = (alpha << 24) | rgb;

        context.blit(
                RenderPipelines.GUI_TEXTURED,
                ARROW_ICON,
                0, 0,
                0f, 0f,
                Math.round(size), Math.round(size),
                TEX_SIZE, TEX_SIZE,
                TEX_SIZE, TEX_SIZE,
                color
        );
        matrices.popMatrix();
    }
}