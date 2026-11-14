package loggamja.formationk.helper;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import loggamja.formationk.mixin.BlockModelRenderStateAccessor;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.BlockDisplayEntityRenderState;
import net.minecraft.client.renderer.entity.state.DisplayEntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemDisplayEntityRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

// 바퀴를 이루는 파츠들의 정점으로 질량 중심과 반지름 측정
// 파츠를 중심 -> 반지름 순서로 두 번 훑어 넣기

public class WheelMeasurer {
    private static final double MIN_EXTENT = 1e-3;

    public final Vector3f center = new Vector3f();
    public float radius;

    private final Vector3f scratchPosition = new Vector3f();
    private double weightedSumX, weightedSumY, weightedSumZ, totalVolume;

    public void beginCenter() {
        weightedSumX = weightedSumY = weightedSumZ = totalVolume = 0.0;
    }

    public void addToCenter(float[] modelPoints, Matrix4f displayTransform) {
        if (modelPoints.length == 0) return;

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        for (int i = 0; i < modelPoints.length; i += 3) {
            displayTransform.transformPosition(modelPoints[i], modelPoints[i + 1], modelPoints[i + 2], scratchPosition);
            minX = Math.min(minX, scratchPosition.x); maxX = Math.max(maxX, scratchPosition.x);
            minY = Math.min(minY, scratchPosition.y); maxY = Math.max(maxY, scratchPosition.y);
            minZ = Math.min(minZ, scratchPosition.z); maxZ = Math.max(maxZ, scratchPosition.z);
        }

        double volume = Math.max(maxX - minX, MIN_EXTENT) * Math.max(maxY - minY, MIN_EXTENT) * Math.max(maxZ - minZ, MIN_EXTENT);
        weightedSumX += volume * (minX + maxX) / 2;
        weightedSumY += volume * (minY + maxY) / 2;
        weightedSumZ += volume * (minZ + maxZ) / 2;
        totalVolume += volume;
    }

    public boolean endCenter() {
        radius = 0f;
        if (totalVolume == 0.0) return false;

        center.set((float) (weightedSumX / totalVolume), (float) (weightedSumY / totalVolume), (float) (weightedSumZ / totalVolume));
        return true;
    }

    // 회전 축이 X 라서 중심과의 거리는 Y, Z 평면에서만 잰다
    public void addToRadius(float[] modelPoints, Matrix4f displayTransform) {
        for (int i = 0; i < modelPoints.length; i += 3) {
            displayTransform.transformPosition(modelPoints[i], modelPoints[i + 1], modelPoints[i + 2], scratchPosition);
            radius = Math.max(radius, (float) Math.hypot(scratchPosition.y - center.y, scratchPosition.z - center.z));
        }
    }

    public static float[] collectPoints(DisplayEntityRenderState state) {
        var modelPointsOut = new FloatArrayList();

        if (state instanceof BlockDisplayEntityRenderState block) {
            List<BlockStateModelPart> modelParts = ((BlockModelRenderStateAccessor) block.blockModel).formationk$getModelParts();
            if (modelParts == null) return modelPointsOut.toFloatArray();

            for (var part : modelParts) {
                addQuads(modelPointsOut, part.getQuads(null));
                for (var direction : Direction.values()) addQuads(modelPointsOut, part.getQuads(direction));
            }
        }
        else if (state instanceof ItemDisplayEntityRenderState item && !item.item.isEmpty()) {
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
        if (quads == null) return;

        for (var quad : quads) {
            for (int i = 0; i < BakedQuad.VERTEX_COUNT; i++) {
                var position = quad.position(i);
                modelPointsOut.add(position.x());
                modelPointsOut.add(position.y());
                modelPointsOut.add(position.z());
            }
        }
    }
}
