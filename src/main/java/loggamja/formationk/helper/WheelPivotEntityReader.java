package loggamja.formationk.helper;

import loggamja.formationk.helper.WheelManager.WheelKey;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// 회전 중심을 직접 지정하는 피봇 엔티티 읽기

public class WheelPivotEntityReader {
    private record PivotPoint(Vector3f point, long seenTick) {}

    private record PivotModel(float[] modelPoints, BlockState blockState, long collectedTick) {}

    private static final Map<WheelKey, PivotPoint> rollPivotPoints = new HashMap<>();
    private static final Map<WheelKey, PivotPoint> steerPivotPoints = new HashMap<>();
    private static final Map<UUID, PivotModel> pivotModels = new HashMap<>();
    private static final WheelMeasurer wheelMeasurer = new WheelMeasurer();

    public static void scan(Iterable<Entity> entities, long now) {
        for (var entity : entities) {
            if (!(entity instanceof Display display)) continue;

            var wheelName = WheelNameParser.of(display);
            if (wheelName == null || !wheelName.isPivot()) continue;

            var point = getPivotPoint(display, now);
            if (point == null) continue;

            var wheelKey = new WheelKey(display.getRootVehicle().getUUID(), wheelName.wheelId());
            var pivotPoint = new PivotPoint(point, now);

            if (wheelName.canRoll()) rollPivotPoints.put(wheelKey, pivotPoint);
            if (wheelName.canSteer()) steerPivotPoints.put(wheelKey, pivotPoint);
        }
    }

    // 모델의 중심점. 모델이 없으면 변환의 이동값
    private static Vector3f getPivotPoint(Display display, long now) {
        var renderState = display.renderState();
        if (renderState == null) return null;

        var displayTransform = new Matrix4f(renderState.transformation().get(1f).getMatrix());
        var blockState = WheelManager.getBlockState(display);
        var pivotModel = pivotModels.get(display.getUUID());
        if (pivotModel == null || pivotModel.blockState() != blockState || now - pivotModel.collectedTick() >= WheelManager.POINTS_REFRESH_TICKS) {
            pivotModel = new PivotModel(WheelMeasurer.collectPoints(null, blockState), blockState, now);
            pivotModels.put(display.getUUID(), pivotModel);
        }

        var point = new Vector3f();
        if (!wheelMeasurer.centerOf(pivotModel.modelPoints(), displayTransform, point)) {
            displayTransform.getTranslation(point);
        }

        return point;
    }

    // 지정된 피봇이 없으면 null
    public static Vector3f getRollPivot(WheelKey wheelKey) {
        var pivotPoint = rollPivotPoints.get(wheelKey);
        return pivotPoint == null ? null : pivotPoint.point();
    }

    public static Vector3f getSteerPivot(WheelKey wheelKey) {
        var pivotPoint = steerPivotPoints.get(wheelKey);
        return pivotPoint == null ? null : pivotPoint.point();
    }

    public static void endTick(long now) {
        rollPivotPoints.values().removeIf(pivotPoint -> now - pivotPoint.seenTick() > WheelManager.CACHE_KEEP_TICKS);
        steerPivotPoints.values().removeIf(pivotPoint -> now - pivotPoint.seenTick() > WheelManager.CACHE_KEEP_TICKS);
        pivotModels.values().removeIf(pivotModel -> now - pivotModel.collectedTick() > WheelManager.CACHE_KEEP_TICKS);
    }

    public static void clear() {
        rollPivotPoints.clear();
        steerPivotPoints.clear();
        pivotModels.clear();
    }
}
