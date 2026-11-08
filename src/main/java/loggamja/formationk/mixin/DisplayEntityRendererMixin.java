
package loggamja.formationk.mixin;

import loggamja.formationk.Suspension;
import loggamja.formationk.helper.EntityRollManager;
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

    // 렌더링은 클라이언트 단일 스레드에서만 도니 재사용 가능. 롤 회전 중인 엔티티마다 매 프레임
    // new Matrix4f를 할당하던 것을 없애 GC 압박을 줄인다.
    @Unique private static final Matrix4f formationk$scratchOriginal = new Matrix4f();
    @Unique private static final Matrix4f formationk$scratchInverse = new Matrix4f();
    @Unique private static final Matrix4f formationk$scratchRoll = new Matrix4f();

    // render() 호출 시 MatrixStack에 roll rotation을 주입합니다.
    // AT 타겟: setupTransforms 이후, 실제 geometry 렌더링 직전
    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/DisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/DisplayRenderer;submitInner(Lnet/minecraft/client/renderer/entity/state/DisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IF)V"
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

        UUID uuid = ((DisplayEntityRenderStateAccessor) state).formationk$getUuid();
        if (uuid == null) return;

        float rollDeg = EntityRollManager.getCurrentRoll(uuid);
        if (rollDeg == 0f) return;

        double pivotY = ((DisplayEntityRenderStateAccessor) state).formationk$getPivotY();

        Transformation affine = state.renderState.transformation().get(state.interpolationProgress);
        formationk$scratchOriginal.set(affine.getMatrix());
        formationk$scratchInverse.set(formationk$scratchOriginal).invert();
        formationk$scratchRoll.rotationZ((float) Math.toRadians(rollDeg));

        matrices.mulPose(formationk$scratchInverse);
        matrices.translate(0.0f, (float) -pivotY, 0.0f);
        matrices.mulPose(formationk$scratchRoll);
        matrices.translate(0.0f, (float) pivotY, 0.0f);
        matrices.mulPose(formationk$scratchOriginal);
    }
    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Display;Lnet/minecraft/client/renderer/entity/state/DisplayEntityRenderState;F)V",
            at = @At("TAIL")
    )
    private void formationk$captureUuid(Display entity, DisplayEntityRenderState state, float tickDelta, CallbackInfo ci) {
        DisplayEntityRenderStateAccessor accessor = (DisplayEntityRenderStateAccessor) state;
        accessor.formationk$setUuid(entity.getUUID());

        if (entity.isPassenger()) {
            double pivotY = entity.getY() - entity.getRootVehicle().getY() - Suspension.pivotYOffset;
            accessor.formationk$setPivotY(pivotY);
        } else {
            accessor.formationk$setPivotY(0.0);
        }
    }
}