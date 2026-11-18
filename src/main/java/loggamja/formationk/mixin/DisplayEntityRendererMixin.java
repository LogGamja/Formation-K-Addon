
package loggamja.formationk.mixin;

import loggamja.formationk.Main;
import loggamja.formationk.Suspension;
import loggamja.formationk.helper.EntityRollManager;
import loggamja.formationk.helper.WheelManager;
import loggamja.formationk.interfaces.DisplayEntityRenderStateAccessor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.entity.state.DisplayEntityRenderState;
import com.mojang.math.Transformation;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import net.minecraft.client.renderer.entity.DisplayRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(DisplayRenderer.class)
public class DisplayEntityRendererMixin {

    // GC 완화용 스크래치
    @Unique private static final Matrix4f formationk$scratchOriginal = new Matrix4f();
    @Unique private static final Matrix4f formationk$scratchRoll = new Matrix4f();

    @Unique private static final float formationk$MIN_DETERMINANT = Float.MIN_NORMAL;

    // render() 호출 시 MatrixStack에 roll rotation을 주입합니다.
    // AT 타겟: 디스플레이 변환 행렬(M)을 곱하기 직전 — 회전이 M 앞에 곱해져서 역행렬이 필요 없다
    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/DisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lcom/mojang/math/Transformation;)V"
            )
    )
    private void formationk$injectRollRotation(
            DisplayEntityRenderState state,
            PoseStack matrices,
            SubmitNodeCollector collector,
            CameraRenderState camera,
            CallbackInfo ci
    ) {
        if (state.renderState == null) return;

        DisplayEntityRenderStateAccessor acc = (DisplayEntityRenderStateAccessor) state;
        UUID rollKey = acc.formationk$getRollKey();

        float rollDeg = rollKey == null ? 0f : EntityRollManager.getCurrentRoll(rollKey);
        WheelManager.WheelPartInfo wheel = acc.formationk$getWheel();
        if (rollDeg == 0f && wheel == null) return;

        Transformation affine = state.renderState.transformation().get(state.interpolationProgress);
        formationk$scratchOriginal.set(affine.getMatrix());

        // 작은 디스플레이 검사하지 않음
        if (Math.abs(formationk$scratchOriginal.determinant()) < formationk$MIN_DETERMINANT) return;

        if (rollDeg != 0f) {
            double pivotY = acc.formationk$getPivotY();
            formationk$scratchRoll.rotationZ((float) Math.toRadians(rollDeg));

            matrices.translate(0.0f, (float) -pivotY, 0.0f);
            matrices.mulPose(formationk$scratchRoll);
            matrices.translate(0.0f, (float) pivotY, 0.0f);
        }

        // 서스펜션 롤 안에서 돌도록 반드시 롤 적용 뒤에 호출
        if (wheel != null) {
            WheelManager.apply(state, wheel, matrices, formationk$scratchOriginal);
        }
    }
    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Display;Lnet/minecraft/client/renderer/entity/state/DisplayEntityRenderState;F)V",
            at = @At("TAIL")
    )
    private void formationk$captureRollKey(Display entity, DisplayEntityRenderState state, float tickDelta, CallbackInfo ci) {
        DisplayEntityRenderStateAccessor accessor = (DisplayEntityRenderStateAccessor) state;

        // 롤 데이터를 modelSaddle에서 읽어와야 함
        var vehicle = entity.getVehicle();
        accessor.formationk$setRollKey(Main.hasCertainName(vehicle, "formationk-modelsaddle") ? vehicle.getUUID() : null);
        accessor.formationk$setWheel(WheelManager.getWheelPartInfo(entity));

        if (entity.isPassenger()) {
            double pivotY = entity.getY() - entity.getRootVehicle().getY() - Suspension.pivotYOffset;
            accessor.formationk$setPivotY(pivotY);
        }
        else {
            accessor.formationk$setPivotY(0.0);
        }
    }
}