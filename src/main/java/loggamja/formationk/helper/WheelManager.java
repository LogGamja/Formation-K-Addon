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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

// Claude Sonnet 5.5로 제작된 바퀴 회전 애니메이션 헬퍼

public class WheelManager {
    private static final String WHEEL_NAME = "formationk-wheel-";
    private static final String WHEEL_STEER_NAME = "formationk-wheel-steer-";
    private static final String STEER_NAME = "formationk-steer-";

    private static final Pattern AXIS_PATTERN = Pattern.compile("\\[([^\\]]*)\\]");

    private static final int POINTS_REFRESH_TICKS = 40;
    private static final int PIVOT_REFRESH_TICKS = 5;
    private static final int CACHE_KEEP_TICKS = 200;

    public record WheelKey(UUID kartId, int wheelId) {}
    public record WheelPartInfo(WheelKey wheelKey, boolean canRoll, boolean canSteer, Vector3f wheelAxis, Vector3f steerAxis, UUID partId, BlockState blockState) {}

    private record WheelName(boolean canRoll, boolean canSteer, Vector3f wheelAxis, Vector3f steerAxis, int wheelId) {}

    private record WheelMotion(float rotateDist, float prevRotateDist, float steerAngle, float prevSteerAngle, long updatedTick) {}

    private record WheelPivot(Vector3f steerCenter, Vector3f rollCenter, float radius, long measuredTick) {}

    private static final class WheelPart {
        WheelKey wheelKey;
        boolean canRoll;
        Vector3f wheelAxis;
        float[] modelPoints = new float[0];
        long pointsCollectedTick = -POINTS_REFRESH_TICKS;
        long lastRenderedTick;
        final Matrix4f displayTransform = new Matrix4f();
    }

    private static final Map<UUID, WheelMotion> wheelMotions = new HashMap<>();
    private static final Map<UUID, WheelPart> wheelParts = new HashMap<>();
    private static final Map<WheelKey, WheelPivot> wheelPivots = new HashMap<>();
    private static final Map<String, WheelName> wheelNames = new HashMap<>();
    private static final WheelMeasurer wheelMeasurer = new WheelMeasurer();

    private static final Matrix4f scratchWheelRotation = new Matrix4f();
    private static final Vector3f scratchNegatedCenter = new Vector3f();

    public static WheelPartInfo getWheelPartInfo(Display entity) {
        if ((Config.INSTANCE.wheelRoll == 0 && Config.INSTANCE.wheelSteer == 0) || !entity.hasCustomName()) return null;

        // 조향 축 측정을 위해 옵션과 관계없이 모든 파츠를 등록
        var wheelName = wheelNames.computeIfAbsent(entity.getCustomName().getString(), WheelManager::parseWheelName);
        if (wheelName == null) return null;

        var wheelKey = new WheelKey(entity.getRootVehicle().getUUID(), wheelName.wheelId());

        BlockState blockState = entity instanceof Display.BlockDisplay blockDisplay && blockDisplay.blockRenderState() != null ? blockDisplay.blockRenderState().blockState() : null;

        return new WheelPartInfo(wheelKey, wheelName.canRoll(), wheelName.canSteer(), wheelName.wheelAxis(), wheelName.steerAxis(), entity.getUUID(), blockState);
    }

    // formationk-wheel-steer-[축]-[축]-번호, formationk-wheel-[축]-번호, formationk-steer-[축]-번호
    private static WheelName parseWheelName(String name) {
        boolean canRoll;
        boolean canSteer;
        String rest;
        if (name.startsWith(WHEEL_STEER_NAME)) {
            canRoll = true;
            canSteer = true;
            rest = name.substring(WHEEL_STEER_NAME.length());
        }
        else if (name.startsWith(WHEEL_NAME)) {
            canRoll = true;
            canSteer = false;
            rest = name.substring(WHEEL_NAME.length());
        }
        else if (name.startsWith(STEER_NAME)) {
            canRoll = false;
            canSteer = true;
            rest = name.substring(STEER_NAME.length());
        }
        else return null;

        var axes = new ArrayList<Vector3f>();
        var matcher = AXIS_PATTERN.matcher(rest);
        while (matcher.find()) {
            var axis = parseAxis(matcher.group(1));
            if (axis == null) return null;
            axes.add(axis);
        }

        int axisCount = (canRoll ? 1 : 0) + (canSteer ? 1 : 0);
        if (axes.size() != axisCount) return null;

        var wheelAxis = canRoll ? axes.get(0) : null;
        var steerAxis = canSteer ? axes.get(axes.size() - 1) : null;

        try {
            return new WheelName(canRoll, canSteer, wheelAxis, steerAxis, Integer.parseInt(rest.substring(rest.lastIndexOf('-') + 1)));
        }
        catch (NumberFormatException e) {
            return null;
        }
    }

    // "0, -1.0f, 0d" 같은 숫자 세 개를 길이 1 인 축으로
    private static Vector3f parseAxis(String text) {
        String[] tokens = text.split(",");
        if (tokens.length != 3) return null;

        var axis = new Vector3f();
        try {
            for (int i = 0; i < 3; i++) axis.setComponent(i, Float.parseFloat(tokens[i].trim().replaceAll("[bBsSlLfFdD]$", "")));
        }
        catch (NumberFormatException e) {
            return null;
        }

        return axis.lengthSquared() > 0f && axis.normalize().isFinite() ? axis : null;
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
        wheelPart.canRoll = partInfo.canRoll();
        wheelPart.wheelAxis = partInfo.wheelAxis();
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
        if (partInfo.canRoll() && Config.INSTANCE.wheelRoll == 1 && wheelPivot.rollCenter() != null) {
            rollAngle = Mth.lerp(partialTick, wheelMotion.prevRotateDist(), wheelMotion.rotateDist()) / Math.max(wheelPivot.radius(), 0.05f);
            rollAngle %= Mth.TWO_PI;
        }

        float steerAngle = 0f;
        if (partInfo.canSteer() && Config.INSTANCE.wheelSteer > 0) {
            steerAngle = Mth.lerp(partialTick, wheelMotion.prevSteerAngle(), wheelMotion.steerAngle());
        }
        if (rollAngle == 0f && steerAngle == 0f) return;

        // 구르기 계산 후 조향 계산
        scratchWheelRotation.identity();
        if (steerAngle != 0f) {
            scratchWheelRotation
                .translate(wheelPivot.steerCenter())
                .rotate((float) Math.toRadians(steerAngle), partInfo.steerAxis())
                .translate(wheelPivot.steerCenter().negate(scratchNegatedCenter));
        }
        if (rollAngle != 0f) {
            scratchWheelRotation
                .translate(wheelPivot.rollCenter())
                .rotate(rollAngle, partInfo.wheelAxis())
                .translate(wheelPivot.rollCenter().negate(scratchNegatedCenter));
        }

        // 호출 쪽에서 이 뒤에 M 을 곱한다
        matrices.mulPose(scratchWheelRotation);
    }

    private static WheelPivot measure(WheelKey wheelKey) {
        // 반드시 이 순서대로 불러야 함

        // 조향 중심은 세트 전체
        wheelMeasurer.beginCenter();
        for (var wheelPart : wheelParts.values()) {
            if (wheelKey.equals(wheelPart.wheelKey)) wheelMeasurer.addToCenter(wheelPart.modelPoints, wheelPart.displayTransform);
        }

        if (!wheelMeasurer.endCenter()) return null;
        var steerCenter = new Vector3f(wheelMeasurer.center);

        // 구르기 중심과 반지름은 구르는 파츠만
        wheelMeasurer.beginCenter();
        for (var wheelPart : wheelParts.values()) {
            if (wheelKey.equals(wheelPart.wheelKey) && wheelPart.canRoll) wheelMeasurer.addToCenter(wheelPart.modelPoints, wheelPart.displayTransform);
        }

        if (!wheelMeasurer.endCenter()) return new WheelPivot(steerCenter, null, 0f, now());
        var rollCenter = new Vector3f(wheelMeasurer.center);

        for (var wheelPart : wheelParts.values()) {
            if (wheelKey.equals(wheelPart.wheelKey) && wheelPart.canRoll) wheelMeasurer.addToRadius(wheelPart.modelPoints, wheelPart.displayTransform, wheelPart.wheelAxis);
        }

        return new WheelPivot(steerCenter, rollCenter, wheelMeasurer.radius, now());
    }
}
