package loggamja.formationk.helper;

import com.mojang.blaze3d.vertex.PoseStack;
import loggamja.formationk.helper.WheelNameParser.WheelName;
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
    static final int POINTS_REFRESH_TICKS = 40;
    private static final int PIVOT_REFRESH_TICKS = 5;
    static final int CACHE_KEEP_TICKS = 200;

    public record WheelKey(UUID kartId, int wheelId) {}
    public record WheelPartInfo(WheelKey wheelKey, WheelName wheelName, UUID partId, BlockState blockState) {}

    // 굴러간 거리는 계속 커지므로 float 이면 오래 탈수록 각도가 끊긴다
    private record WheelMotion(double rotateDist, double prevRotateDist, float steerAngle, float prevSteerAngle, float gripSteerAngle, float prevGripSteerAngle, long updatedTick) {}

    private record WheelPivot(Vector3f steerCenter, Vector3f rollCenter, float radius, long measuredTick) {}

    private static final class WheelPart {
        WheelName wheelName;
        float[] modelPoints = new float[0];
        long pointsCollectedTick = -POINTS_REFRESH_TICKS;
        BlockState pointsBlockState;
        long lastRenderedTick;
        final Matrix4f displayTransform = new Matrix4f();
    }

    private static final Map<UUID, WheelMotion> wheelMotions = new HashMap<>();

    // 바퀴별로 묶어 두어 측정할 때 그 바퀴의 파츠만 돈다
    private static final Map<WheelKey, Map<UUID, WheelPart>> wheelParts = new HashMap<>();
    private static final Map<WheelKey, WheelPivot> wheelPivots = new HashMap<>();
    private static final WheelMeasurer wheelMeasurer = new WheelMeasurer();

    private static final Matrix4f scratchWheelRotation = new Matrix4f();
    private static final Vector3f scratchNegatedCenter = new Vector3f();

    public static WheelPartInfo getWheelPartInfo(Display entity) {
        if (Config.INSTANCE.wheelRoll == 0 && Config.INSTANCE.wheelSteer == 0) return null;

        // 조향 축 측정을 위해 옵션과 관계없이 모든 파츠를 등록
        var wheelName = WheelNameParser.of(entity);
        if (wheelName == null || wheelName.isPivot()) return null;

        var wheelKey = new WheelKey(entity.getRootVehicle().getUUID(), wheelName.wheelId());

        return new WheelPartInfo(wheelKey, wheelName, entity.getUUID(), getBlockState(entity));
    }

    static BlockState getBlockState(Display entity) {
        return entity instanceof Display.BlockDisplay blockDisplay && blockDisplay.blockRenderState() != null ? blockDisplay.blockRenderState().blockState() : null;
    }

    public static void update(UUID kartId, float speedMetersPerSecond, float targetSteerAngle, float targetGripSteerAngle) {
        var prevWheelMotion = wheelMotions.get(kartId);
        if (prevWheelMotion == null) prevWheelMotion = new WheelMotion(0f, 0f, 0f, 0f, 0f, 0f, 0L);

        double rotateDist = prevWheelMotion.rotateDist() + speedMetersPerSecond / 20.0;
        float steerAngle = prevWheelMotion.steerAngle() + (targetSteerAngle - prevWheelMotion.steerAngle()) * 0.5f;
        float gripSteerAngle = prevWheelMotion.gripSteerAngle() + (targetGripSteerAngle - prevWheelMotion.gripSteerAngle()) * 0.5f;

        wheelMotions.put(kartId, new WheelMotion(rotateDist, prevWheelMotion.rotateDist(), steerAngle, prevWheelMotion.steerAngle(), gripSteerAngle, prevWheelMotion.gripSteerAngle(), now()));
    }

    public static void endTick(int ridingKartCount) {
        if (wheelMotions.size() > ridingKartCount) {
            wheelMotions.entrySet().removeIf(motionEntry -> motionEntry.getValue().updatedTick() != now());
        }

        WheelPivotEntityReader.endTick(now());
        wheelPivots.entrySet().removeIf(pivotEntry -> now() - pivotEntry.getValue().measuredTick() > CACHE_KEEP_TICKS);
        wheelParts.values().removeIf(keyParts -> {
            keyParts.values().removeIf(wheelPart -> now() - wheelPart.lastRenderedTick > CACHE_KEEP_TICKS);
            return keyParts.isEmpty();
        });
    }

    public static void clear() {
        wheelMotions.clear();
        wheelParts.clear();
        wheelPivots.clear();
        WheelPivotEntityReader.clear();
    }

    public static void apply(DisplayEntityRenderState renderState, WheelPartInfo partInfo, PoseStack matrices, Matrix4f displayTransform) {
        var wheelName = partInfo.wheelName();
        var wheelMotion = wheelMotions.get(partInfo.wheelKey().kartId());
        if (wheelMotion == null) return;
        if (Math.abs(displayTransform.determinant()) < Float.MIN_NORMAL) return;

        registerPart(renderState, partInfo, displayTransform);

        var wheelPivot = getWheelPivot(partInfo.wheelKey());
        if (wheelPivot == null) return;

        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float rollAngle = getRollAngle(wheelName, wheelMotion, wheelPivot, partialTick);
        float steerAngle = getSteerAngle(wheelName, wheelMotion, partialTick);
        if (rollAngle == 0f && steerAngle == 0f) return;

        // 구르기 계산 후 조향 계산
        scratchWheelRotation.identity();
        if (steerAngle != 0f) {
            scratchWheelRotation
                .translate(wheelPivot.steerCenter())
                .rotate((float) Math.toRadians(steerAngle), wheelName.steerAxis())
                .translate(wheelPivot.steerCenter().negate(scratchNegatedCenter));
        }
        if (rollAngle != 0f) {
            scratchWheelRotation
                .translate(wheelPivot.rollCenter())
                .rotate(rollAngle, wheelName.wheelAxis())
                .translate(wheelPivot.rollCenter().negate(scratchNegatedCenter));
        }

        // 호출 쪽에서 이 뒤에 M 을 곱한다
        matrices.mulPose(scratchWheelRotation);
    }

    // 측정에 쓸 파츠의 이름, 변환, 모델 점을 갱신
    private static void registerPart(DisplayEntityRenderState renderState, WheelPartInfo partInfo, Matrix4f displayTransform) {
        var wheelPart = wheelParts.computeIfAbsent(partInfo.wheelKey(), wheelKey -> new HashMap<>()).computeIfAbsent(partInfo.partId(), partId -> new WheelPart());
        wheelPart.wheelName = partInfo.wheelName();
        wheelPart.lastRenderedTick = now();
        wheelPart.displayTransform.set(displayTransform);

        if (now() - wheelPart.pointsCollectedTick >= POINTS_REFRESH_TICKS || partInfo.blockState() != wheelPart.pointsBlockState) {
            wheelPart.modelPoints = WheelMeasurer.collectPoints(renderState, partInfo.blockState());
            wheelPart.pointsCollectedTick = now();
            wheelPart.pointsBlockState = partInfo.blockState();
        }
    }

    private static WheelPivot getWheelPivot(WheelKey wheelKey) {
        var wheelPivot = wheelPivots.get(wheelKey);
        if (wheelPivot != null && now() - wheelPivot.measuredTick() < PIVOT_REFRESH_TICKS) return wheelPivot;

        wheelPivot = measure(wheelKey);
        if (wheelPivot != null) wheelPivots.put(wheelKey, wheelPivot);

        return wheelPivot;
    }

    private static WheelPivot measure(WheelKey wheelKey) {
        // 반드시 이 순서대로 불러야 함
        var keyParts = wheelParts.get(wheelKey).values();

        // 조향 중심은 세트 전체
        wheelMeasurer.beginCenter();
        for (var wheelPart : keyParts) {
            wheelMeasurer.addToCenter(wheelPart.modelPoints, wheelPart.displayTransform);
        }

        if (!wheelMeasurer.endCenter()) return null;
        var steerCenter = new Vector3f(wheelMeasurer.center);

        // 피봇 엔티티가 있으면 계산한 중심 대신 그 점
        var steerPivot = WheelPivotEntityReader.getSteerPivot(wheelKey);
        if (steerPivot != null) steerCenter.set(steerPivot);

        // 구르기 중심과 반지름은 구르는 파츠만
        wheelMeasurer.beginCenter();
        for (var wheelPart : keyParts) {
            if (wheelPart.wheelName.canRoll()) wheelMeasurer.addToCenter(wheelPart.modelPoints, wheelPart.displayTransform);
        }

        if (!wheelMeasurer.endCenter()) return new WheelPivot(steerCenter, null, 0f, now());
        var rollCenter = new Vector3f(wheelMeasurer.center);

        // 반지름은 실제 구르기 피봇에서 가장 먼 점까지의 거리
        var rollPivot = WheelPivotEntityReader.getRollPivot(wheelKey);
        if (rollPivot != null) {
            rollCenter.set(rollPivot);
            wheelMeasurer.center.set(rollCenter);
        }

        for (var wheelPart : keyParts) {
            if (wheelPart.wheelName.canRoll()) wheelMeasurer.addToRadius(wheelPart.modelPoints, wheelPart.displayTransform, wheelPart.wheelName.wheelAxis());
        }

        return new WheelPivot(steerCenter, rollCenter, wheelMeasurer.radius, now());
    }

    private static float getRollAngle(WheelName wheelName, WheelMotion wheelMotion, WheelPivot wheelPivot, float partialTick) {
        if (!wheelName.canRoll() || Config.INSTANCE.wheelRoll != 1 || wheelPivot.rollCenter() == null) return 0f;

        double rotateDist = Mth.lerp(partialTick, wheelMotion.prevRotateDist(), wheelMotion.rotateDist());
        return (float) (rotateDist / Math.max(wheelPivot.radius(), 0.05f) * wheelName.rollRatio() % Mth.TWO_PI);
    }

    private static float getSteerAngle(WheelName wheelName, WheelMotion wheelMotion, float partialTick) {
        if (!wheelName.canSteer() || Config.INSTANCE.wheelSteer <= 0) return 0f;

        return wheelName.noCounter()
            ? Mth.lerp(partialTick, wheelMotion.prevGripSteerAngle(), wheelMotion.gripSteerAngle())
            : Mth.lerp(partialTick, wheelMotion.prevSteerAngle(), wheelMotion.steerAngle());
    }

    private static long now() {
        return Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
    }
}
