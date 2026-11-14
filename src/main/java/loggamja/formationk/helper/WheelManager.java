package loggamja.formationk.helper;

import com.mojang.blaze3d.vertex.PoseStack;
import loggamja.formationk.option.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.DisplayEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Claude Sonnet 5.5로 제작된 바퀴 회전 애니메이션 헬퍼

public class WheelManager {
    private static final String WHEEL_NAME = "formationk-wheel-";
    private static final String WHEEL_STEER_NAME = "formationk-wheel-steer-";

    private static final int POINTS_REFRESH_TICKS = 40;
    private static final int PIVOT_REFRESH_TICKS = 5;
    private static final int CACHE_KEEP_TICKS = 200;

    public record WheelKey(UUID kartId, int wheelId) {}
    public record WheelPartInfo(WheelKey wheelKey, boolean canSteer, UUID partId, BlockState blockState) {}

    private record WheelMotion(float rotateDist, float prevRotateDist, float steerAngle, float prevSteerAngle, long updatedTick) {}
    private record WheelPivot(Vector3f center, float radius, long measuredTick) {}

    private static final class WheelPart {
        WheelKey wheelKey;
        float[] modelPoints = new float[0];
        long pointsCollectedTick = -POINTS_REFRESH_TICKS;
        long lastRenderedTick;
        final Matrix4f displayTransform = new Matrix4f();
    }

    private static final Map<UUID, WheelMotion> wheelMotions = new HashMap<>();
    private static final Map<UUID, WheelPart> wheelParts = new HashMap<>();
    private static final Map<WheelKey, WheelPivot> wheelPivots = new HashMap<>();
    private static final WheelMeasurer wheelMeasurer = new WheelMeasurer();

    private static final Matrix4f scratchWheelRotation = new Matrix4f();
    private static final Vector3f scratchNegatedCenter = new Vector3f();

    public static WheelPartInfo getWheelPartInfo(Display entity) {
        if ((Config.INSTANCE.wheelRoll == 0 && Config.INSTANCE.wheelSteer == 0) || !entity.hasCustomName()) return null;

        String entityName = entity.getCustomName().getString();
        boolean isSteerPart = entityName.startsWith(WHEEL_STEER_NAME);
        if (!isSteerPart && !entityName.startsWith(WHEEL_NAME)) return null;

        int wheelId;
        try {
            wheelId = Integer.parseInt(entityName.substring((isSteerPart ? WHEEL_STEER_NAME : WHEEL_NAME).length()));
        }
        catch (NumberFormatException e) {
            return null;
        }

        var wheelKey = new WheelKey(entity.getRootVehicle().getUUID(), wheelId);
        boolean canSteer = isSteerPart && Config.INSTANCE.wheelSteer > 0;

        BlockState blockState = entity instanceof Display.BlockDisplay blockDisplay && blockDisplay.blockRenderState() != null ? blockDisplay.blockRenderState().blockState() : null;

        return new WheelPartInfo(wheelKey, canSteer, entity.getUUID(), blockState);
    }

    public static void update(UUID kartId, float speedMetersPerSecond, float targetSteerAngle) {
        var prevWheelMotion = wheelMotions.get(kartId);
        if (prevWheelMotion == null) prevWheelMotion = new WheelMotion(0f, 0f, 0f, 0f, 0L);

        float rotateDist = prevWheelMotion.rotateDist() + speedMetersPerSecond / 20f;
        float steerAngle = prevWheelMotion.steerAngle() + (targetSteerAngle - prevWheelMotion.steerAngle()) * 0.5f;

        wheelMotions.put(kartId, new WheelMotion(rotateDist, prevWheelMotion.rotateDist(), steerAngle, prevWheelMotion.steerAngle(), now()));
    }

    public static void endTick(int ridingKartCount) {
        if (wheelMotions.size() > ridingKartCount) {
            wheelMotions.entrySet().removeIf(e -> e.getValue().updatedTick() != now());
        }

        wheelPivots.entrySet().removeIf(e -> now() - e.getValue().measuredTick() > CACHE_KEEP_TICKS);
        wheelParts.entrySet().removeIf(e -> now() - e.getValue().lastRenderedTick > CACHE_KEEP_TICKS);
    }

    private static long now() {
        return Minecraft.getInstance().level.getGameTime();
    }

    public static void clear() {
        wheelMotions.clear();
        wheelParts.clear();
        wheelPivots.clear();
    }

    public static void apply(DisplayEntityRenderState renderState, WheelPartInfo partInfo, PoseStack matrices, Matrix4f displayTransform) {
        var wheelMotion = wheelMotions.get(partInfo.wheelKey().kartId());
        if (wheelMotion == null) return;
        if (Math.abs(displayTransform.determinant()) < Float.MIN_NORMAL) return;

        var wheelPart = wheelParts.computeIfAbsent(partInfo.partId(), k -> new WheelPart());
        wheelPart.wheelKey = partInfo.wheelKey();
        wheelPart.lastRenderedTick = now();
        wheelPart.displayTransform.set(displayTransform);

        if (now() - wheelPart.pointsCollectedTick >= POINTS_REFRESH_TICKS) {
            wheelPart.modelPoints = WheelMeasurer.collectPoints(renderState, partInfo.blockState());
            wheelPart.pointsCollectedTick = now();
        }

        var wheelPivot = wheelPivots.get(partInfo.wheelKey());
        if (wheelPivot == null || now() - wheelPivot.measuredTick() >= PIVOT_REFRESH_TICKS) {
            wheelPivot = measure(partInfo.wheelKey());
            if (wheelPivot == null) return;
            wheelPivots.put(partInfo.wheelKey(), wheelPivot);
        }

        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float rollAngle = 0f;
        if (Config.INSTANCE.wheelRoll == 1) {
            rollAngle = Mth.lerp(partialTick, wheelMotion.prevRotateDist(), wheelMotion.rotateDist()) / Math.max(wheelPivot.radius(), 0.05f);
            rollAngle %= Mth.TWO_PI;
        }

        float steerAngle = partInfo.canSteer() ? Mth.lerp(partialTick, wheelMotion.prevSteerAngle(), wheelMotion.steerAngle()) : 0f;
        if (rollAngle == 0f && steerAngle == 0f) return;

        scratchWheelRotation.identity()
                .translate(wheelPivot.center())
                .rotateY((float) Math.toRadians(-steerAngle))
                .rotateX(rollAngle)
                .translate(wheelPivot.center().negate(scratchNegatedCenter));

        // 호출 쪽이 역행렬(M) 과 M 을 이미 감싸고 있다
        matrices.mulPose(scratchWheelRotation);
    }

    private static WheelPivot measure(WheelKey wheelKey) {
        // 반드시 이 순서대로 불러야 함

        wheelMeasurer.beginCenter();
        for (var wheelPart : wheelParts.values()) {
            if (wheelKey.equals(wheelPart.wheelKey)) wheelMeasurer.addToCenter(wheelPart.modelPoints, wheelPart.displayTransform);
        }

        if (!wheelMeasurer.endCenter()) return null;

        for (var wheelPart : wheelParts.values()) {
            if (wheelKey.equals(wheelPart.wheelKey)) wheelMeasurer.addToRadius(wheelPart.modelPoints, wheelPart.displayTransform);
        }

        return new WheelPivot(new Vector3f(wheelMeasurer.center), wheelMeasurer.radius, now());
    }
}
