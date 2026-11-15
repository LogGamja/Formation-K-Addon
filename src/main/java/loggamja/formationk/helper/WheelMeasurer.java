package loggamja.formationk.helper;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.DisplayEntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemDisplayEntityRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

// 바퀴를 이루는 파츠들의 정점으로 질량 중심과 반지름 측정
// 파츠를 중심 -> 반지름 순서로 두 번 훑어 넣기

public class WheelMeasurer {
    private static final double MIN_EXTENT = 1e-3;

    public final Vector3f center = new Vector3f();
    public float radius;

    private final Vector3f scratchPosition = new Vector3f();
    private final Vector3f boundsMin = new Vector3f();
    private final Vector3f boundsMax = new Vector3f();
    private double weightedSumX, weightedSumY, weightedSumZ, totalVolume;

    public void beginCenter() {
        weightedSumX = weightedSumY = weightedSumZ = totalVolume = 0.0;
    }

    public void addToCenter(float[] modelPoints, Matrix4f displayTransform) {
        if (!computeBounds(modelPoints, displayTransform)) return;

        double volume = Math.max(boundsMax.x - boundsMin.x, MIN_EXTENT) * Math.max(boundsMax.y - boundsMin.y, MIN_EXTENT) * Math.max(boundsMax.z - boundsMin.z, MIN_EXTENT);
        weightedSumX += volume * (boundsMin.x + boundsMax.x) / 2;
        weightedSumY += volume * (boundsMin.y + boundsMax.y) / 2;
        weightedSumZ += volume * (boundsMin.z + boundsMax.z) / 2;
        totalVolume += volume;
    }

    // 파츠 하나의 중심. 점이 없으면 false
    public boolean centerOf(float[] modelPoints, Matrix4f displayTransform, Vector3f centerOut) {
        if (!computeBounds(modelPoints, displayTransform)) return false;

        boundsMin.add(boundsMax, centerOut).mul(0.5f);
        return true;
    }

    private boolean computeBounds(float[] modelPoints, Matrix4f displayTransform) {
        if (modelPoints.length == 0) return false;

        boundsMin.set(Float.MAX_VALUE);
        boundsMax.set(-Float.MAX_VALUE);

        for (int i = 0; i < modelPoints.length; i += 3) {
            displayTransform.transformPosition(modelPoints[i], modelPoints[i + 1], modelPoints[i + 2], scratchPosition);
            boundsMin.min(scratchPosition);
            boundsMax.max(scratchPosition);
        }

        return true;
    }

    public boolean endCenter() {
        radius = 0f;
        if (totalVolume == 0.0) return false;

        center.set((float) (weightedSumX / totalVolume), (float) (weightedSumY / totalVolume), (float) (weightedSumZ / totalVolume));
        return true;
    }

    // 회전 축에 수직인 방향의 거리만 잰다
    public void addToRadius(float[] modelPoints, Matrix4f displayTransform, Vector3f rotationAxis) {
        for (int i = 0; i < modelPoints.length; i += 3) {
            displayTransform.transformPosition(modelPoints[i], modelPoints[i + 1], modelPoints[i + 2], scratchPosition);
            scratchPosition.sub(center);
            scratchPosition.fma(-scratchPosition.dot(rotationAxis), rotationAxis);
            radius = Math.max(radius, scratchPosition.length());
        }
    }

    public static float[] collectPoints(DisplayEntityRenderState renderState, BlockState blockState) {
        var modelPointsOut = new FloatArrayList();

        if (blockState != null) {
            var modelParts = new ArrayList<BlockStateModelPart>();
            Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(blockState).collectParts(RandomSource.create(0L), modelParts);

            for (var part : modelParts) {
                addQuads(modelPointsOut, part.getQuads(null));
                for (var direction : Direction.values()) addQuads(modelPointsOut, part.getQuads(direction));
            }
            if (modelPointsOut.isEmpty()) addUnitCube(modelPointsOut);
        }
        else if (renderState instanceof ItemDisplayEntityRenderState item && !item.item.isEmpty()) {
            // 아이템 디스플레이는 변환 행렬 뒤에 Y축 180도 회전이 한 번 더 걸린다
            item.item.visitExtents(v -> {
                modelPointsOut.add(-v.x());
                modelPointsOut.add(v.y());
                modelPointsOut.add(-v.z());
            });
        }

        return modelPointsOut.toFloatArray();
    }

    private static void addQuads(FloatArrayList modelPointsOut, List<BakedQuad> quads) {
        for (var quad : quads) {
            for (int i = 0; i < BakedQuad.VERTEX_COUNT; i++) {
                var position = quad.position(i);
                modelPointsOut.add(position.x());
                modelPointsOut.add(position.y());
                modelPointsOut.add(position.z());
            }
        }
    }

    // 모델이 없는 블록은 꽉 찬 블록으로 본다
    private static void addUnitCube(FloatArrayList modelPointsOut) {
        for (int corner = 0; corner < 8; corner++) {
            modelPointsOut.add(corner & 1);
            modelPointsOut.add((corner >> 1) & 1);
            modelPointsOut.add((corner >> 2) & 1);
        }
    }
}
