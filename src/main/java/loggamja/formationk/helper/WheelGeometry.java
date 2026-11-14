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

// 바퀴 하나의 중심(부피 가중 평균)과 반지름. 파츠를 중심 계산 -> 반지름 계산 순서로 두 번 훑어 넣는다
public class WheelGeometry {
    private static final double MIN_EXTENT = 1e-3;

    public final Vector3f center = new Vector3f();
    public float radius;

    private double sumX, sumY, sumZ, sumVolume;
    private final Vector3f scratch = new Vector3f();

    public void beginCenter() {
        sumX = sumY = sumZ = sumVolume = 0.0;
    }

    public void addToCenter(float[] points, Matrix4f transform) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < points.length; i += 3) {
            transform.transformPosition(points[i], points[i + 1], points[i + 2], scratch);
            minX = Math.min(minX, scratch.x); maxX = Math.max(maxX, scratch.x);
            minY = Math.min(minY, scratch.y); maxY = Math.max(maxY, scratch.y);
            minZ = Math.min(minZ, scratch.z); maxZ = Math.max(maxZ, scratch.z);
        }
        double volume = Math.max(maxX - minX, MIN_EXTENT) * Math.max(maxY - minY, MIN_EXTENT) * Math.max(maxZ - minZ, MIN_EXTENT);
        sumX += volume * (minX + maxX) * 0.5;
        sumY += volume * (minY + maxY) * 0.5;
        sumZ += volume * (minZ + maxZ) * 0.5;
        sumVolume += volume;
    }

    // 파츠가 하나도 없었으면 false
    public boolean endCenter() {
        radius = 0f;
        if (sumVolume <= 0.0) return false;
        center.set((float) (sumX / sumVolume), (float) (sumY / sumVolume), (float) (sumZ / sumVolume));
        return true;
    }

    // 회전 축이 X 라서 중심과의 거리는 Y, Z 평면에서만 잰다
    public void addToRadius(float[] points, Matrix4f transform) {
        for (int i = 0; i < points.length; i += 3) {
            transform.transformPosition(points[i], points[i + 1], points[i + 2], scratch);
            float dy = scratch.y - center.y;
            float dz = scratch.z - center.z;
            radius = Math.max(radius, (float) Math.sqrt(dy * dy + dz * dz));
        }
    }

    public static float[] collectPoints(DisplayEntityRenderState state) {
        FloatArrayList out = new FloatArrayList();
        try {
            if (state instanceof BlockDisplayEntityRenderState block) {
                List<BlockStateModelPart> parts = ((BlockModelRenderStateAccessor) block.blockModel).formationk$getModelParts();
                if (parts != null) {
                    for (BlockStateModelPart part : parts) {
                        addQuads(out, part.getQuads(null));
                        for (Direction direction : Direction.values()) addQuads(out, part.getQuads(direction));
                    }
                }
            }
            else if (state instanceof ItemDisplayEntityRenderState item && !item.item.isEmpty()) {
                // 아이템 디스플레이는 변환 행렬 뒤에 Y축 180도 회전이 한 번 더 걸린다
                item.item.visitExtents(v -> {
                    out.add(-v.x());
                    out.add(v.y());
                    out.add(-v.z());
                });
            }
        } catch (RuntimeException ignored) {
        }
        return out.toFloatArray();
    }

    private static void addQuads(FloatArrayList out, List<BakedQuad> quads) {
        if (quads == null) return;
        for (BakedQuad quad : quads) {
            for (int i = 0; i < BakedQuad.VERTEX_COUNT; i++) {
                var position = quad.position(i);
                out.add(position.x());
                out.add(position.y());
                out.add(position.z());
            }
        }
    }
}
