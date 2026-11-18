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
    private record PivotPosition(Vector3f position, long seenTick) {}

    private record PivotModel(float[] modelPoints, BlockState blockState, long collectedTick) {}

    private static final Map<WheelKey, PivotPosition> rollPivotPositions = new HashMap<>();
    private static final Map<WheelKey, PivotPosition> steerPivotPositions = new HashMap<>();
    private static final Map<UUID, PivotModel> pivotModels = new HashMap<>();
    private static final WheelMeasurer wheelMeasurer = new WheelMeasurer();

    public static void scan(Iterable<Entity> entities, long now) {
        for (var entity : entities) {
            // 모든 피봇은 view_range가 0임
            if (!(entity instanceof Display display) || display.getViewRange() != 0f) continue;

            var wheelName = WheelNameParser.of(display);
            if (wheelName == null || !wheelName.isPivot()) continue;

            var position = getPivotPosition(display, now);
            if (position == null) continue;

            var wheelKey = new WheelKey(display.getRootVehicle().getUUID(), wheelName.wheelId());
            var pivotPosition = new PivotPosition(position, now);

            if (wheelName.canRoll()) rollPivotPositions.put(wheelKey, pivotPosition);
            if (wheelName.canSteer()) steerPivotPositions.put(wheelKey, pivotPosition);
        }
    }
    private static Vector3f getPivotPosition(Display display, long now) {
        var renderState = display.renderState();
        if (renderState == null) return null;

        var displayTransform = new Matrix4f(renderState.transformation().get(1f).getMatrix());
        var blockState = WheelManager.getBlockState(display);
        var pivotModel = pivotModels.get(display.getUUID());

        if (pivotModel == null || pivotModel.blockState() != blockState || now - pivotModel.collectedTick() >= WheelManager.POINTS_REFRESH_TICKS) {
            pivotModel = new PivotModel(WheelMeasurer.collectPoints(null, blockState), blockState, now);
            pivotModels.put(display.getUUID(), pivotModel);
        }

        var position = new Vector3f();
        if (!wheelMeasurer.centerOf(pivotModel.modelPoints(), displayTransform, position)) {
            displayTransform.getTranslation(position);
        }

        return position;
    }

    // 지정된 피봇이 없으면 null
    public static Vector3f getRollPivot(WheelKey wheelKey) {
        var pivotPosition = rollPivotPositions.get(wheelKey);
        return pivotPosition == null ? null : pivotPosition.position();
    }

    public static Vector3f getSteerPivot(WheelKey wheelKey) {
        var pivotPosition = steerPivotPositions.get(wheelKey);
        return pivotPosition == null ? null : pivotPosition.position();
    }

    public static void endTick(long now) {
        rollPivotPositions.values().removeIf(pivotPosition -> now - pivotPosition.seenTick() > WheelManager.CACHE_KEEP_TICKS);
        steerPivotPositions.values().removeIf(pivotPosition -> now - pivotPosition.seenTick() > WheelManager.CACHE_KEEP_TICKS);
        pivotModels.values().removeIf(pivotModel -> now - pivotModel.collectedTick() > WheelManager.CACHE_KEEP_TICKS);
    }

    public static void clear() {
        rollPivotPositions.clear();
        steerPivotPositions.clear();
        pivotModels.clear();
    }
}
