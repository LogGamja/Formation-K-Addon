package loggamja.formationk.helper;

import com.mojang.blaze3d.vertex.PoseStack;
import loggamja.formationk.option.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.DisplayEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Claude Sonnet 5.5로 제작된 바퀴 회전 애니메이션 헬퍼

public class WheelManager {
    private static final String PREFIX = "formationk-wheel-";
    private static final String PREFIX_STEER = "formationk-wheel-steer-";

    private static final int GEOM_REFRESH_TICKS = 40;
    private static final int SET_REFRESH_TICKS = 5;
    private static final int KEEP_TICKS = 200;

    public record WheelKey(UUID kart, int id) {}
    public record WheelPartInfo(WheelKey key, boolean steer, UUID part) {}

    private static final class WheelPart {
        WheelKey key;
        float[] points = new float[0];
        int collectTick = -9999;
        int seenTick;
        final Matrix4f transform = new Matrix4f();
    }

    private static final class WheelState {
        int seenTick;
        int measureTick = -9999;
        boolean measured;
        final WheelGeometry geometry = new WheelGeometry();
    }

    private static final class KartMotion {
        int seenTick;
        float dist, prevDist;
        float steer, prevSteer;
    }

    private static final Map<UUID, WheelPart> parts = new HashMap<>();
    private static final Map<WheelKey, WheelState> wheels = new HashMap<>();
    private static final Map<UUID, KartMotion> kartMotions = new HashMap<>();
    private static int tick = 0;

    private static final Matrix4f scratchW = new Matrix4f();
    private static final Vector3f scratchV = new Vector3f();

    public static WheelPartInfo capture(Display entity) {
        if (Config.INSTANCE.wheelEffect == 0) return null;
        if (!entity.hasCustomName()) return null;
        Component name = entity.getCustomName();
        if (name == null) return null;
        String s = name.getString();
        if (!s.startsWith(PREFIX)) return null;

        boolean steerName = s.startsWith(PREFIX_STEER);
        boolean steer = steerName && Config.INSTANCE.wheelEffect >= 2;
        String num = s.substring(steerName ? PREFIX_STEER.length() : PREFIX.length());
        int id;
        try {
            id = Integer.parseInt(num);
        }
        catch (NumberFormatException e) {
            return null;
        }

        return new WheelPartInfo(new WheelKey(entity.getRootVehicle().getUUID(), id), steer, entity.getUUID());
    }

    public static void clear() {
        wheels.clear();
        parts.clear();
        kartMotions.clear();
    }

    public static void tick() {
        tick++;
        kartMotions.entrySet().removeIf(e -> e.getValue().seenTick < tick - 1);
        wheels.entrySet().removeIf(e -> tick - e.getValue().seenTick > KEEP_TICKS);
        parts.entrySet().removeIf(e -> tick - e.getValue().seenTick > KEEP_TICKS);
    }

    public static void update(UUID kart, float speed, float steerTarget) {
        KartMotion motion = kartMotions.computeIfAbsent(kart, k -> new KartMotion());
        motion.seenTick = tick;
        motion.prevSteer = motion.steer;
        motion.steer += (steerTarget - motion.steer) * 0.5f;
        motion.prevDist = motion.dist;
        motion.dist += speed / 20f;
    }

    public static void apply(DisplayEntityRenderState state, WheelPartInfo info, PoseStack matrices, Matrix4f original) {
        KartMotion motion = kartMotions.get(info.key().kart());
        if (motion == null) return;
        if (Math.abs(original.determinant()) < Float.MIN_NORMAL) return;

        WheelPart part = parts.computeIfAbsent(info.part(), k -> new WheelPart());
        part.key = info.key();
        part.seenTick = tick;
        part.transform.set(original);
        if (tick - part.collectTick >= GEOM_REFRESH_TICKS) {
            part.points = WheelGeometry.collectPoints(state);
            part.collectTick = tick;
        }

        WheelState wheel = wheels.computeIfAbsent(info.key(), k -> new WheelState());
        wheel.seenTick = tick;
        if (tick - wheel.measureTick >= SET_REFRESH_TICKS) {
            wheel.measured = measure(info.key(), wheel.geometry);
            wheel.measureTick = tick;
        }
        if (!wheel.measured) return;

        float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float roll = Mth.lerp(partial, motion.prevDist, motion.dist) / Math.max(wheel.geometry.radius, 0.05f);
        roll = roll % Mth.TWO_PI;
        float steer = info.steer() ? Mth.lerp(partial, motion.prevSteer, motion.steer) : 0f;
        if (roll == 0f && steer == 0f) return;

        scratchW.identity()
                .translate(wheel.geometry.center)
                .rotateY((float) Math.toRadians(-steer))
                .rotateX(roll)
                .translate(wheel.geometry.center.negate(scratchV));

        // 호출 쪽이 M⁻¹ 과 M 을 이미 감싸고 있다
        matrices.mulPose(scratchW);
    }

    private static boolean measure(WheelKey key, WheelGeometry geometry) {
        geometry.beginCenter();
        for (WheelPart part : parts.values()) {
            if (key.equals(part.key) && part.points.length > 0) geometry.addToCenter(part.points, part.transform);
        }
        if (!geometry.endCenter()) return false;

        for (WheelPart part : parts.values()) {
            if (key.equals(part.key) && part.points.length > 0) geometry.addToRadius(part.points, part.transform);
        }
        return true;
    }
}
