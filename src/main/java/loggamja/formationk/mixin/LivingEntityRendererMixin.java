package loggamja.formationk.mixin;

import loggamja.formationk.Main;
import loggamja.formationk.option.Config;
import loggamja.formationk.Suspension;
import loggamja.formationk.helper.EntityRollManager;
import loggamja.formationk.interfaces.LivingEntityRenderStateAccessor;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import com.mojang.math.Axis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "solveBodyRot", at = @At(value = "HEAD"), cancellable = true)
    private static void formationk$clampBodyYaw(LivingEntity entity, float headRot, float tickProgress, CallbackInfoReturnable<Float> cir) {
        if (entity instanceof Player player && Main.isRidingKart(player)) {
            float criteria = player.getViewYRot(tickProgress);

            float delta = Mth.clamp(Mth.wrapDegrees(criteria - player.yBodyRot), -85.0F, 85.0F);
            player.setYBodyRot(criteria - delta);

            cir.setReturnValue(criteria - delta);
        }
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL")
    )
    private void formationk$captureRoll(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
        var accessor = (LivingEntityRenderStateAccessor) state;

        if (entity instanceof Player player && Main.isRidingKart(player)) {
            // 음수 부호가 적용된 것은 의도
            accessor.formationk$setRoll(-EntityRollManager.getCurrentRoll(player.getUUID()));

            if (Config.INSTANCE.playerHeadLock) {
                state.yRot = Mth.wrapDegrees(player.getViewYRot(tickDelta) - state.bodyRot);
            }

            Entity root = entity.getRootVehicle();
            accessor.formationk$setPivotOffset(player.getY() - root.getY() - Suspension.pivotYOffset);
        }
        else {
            accessor.formationk$setRoll(0f);
            accessor.formationk$setPivotOffset(0.0);
        }
    }
    @Inject(
            method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("TAIL")
    )
    private void formationk$applyRoll(LivingEntityRenderState state, PoseStack matrices, float bodyYaw, float baseHeight, CallbackInfo ci) {
        var accessor = (LivingEntityRenderStateAccessor) state;
        float rollDeg = accessor.formationk$getRoll();
        if (rollDeg == 0f) return;

        double pivotOffset = accessor.formationk$getPivotOffset();
        matrices.translate(0.0, -pivotOffset, 0.0);
        matrices.rotateDegrees(Axis.ZP, rollDeg);
        matrices.translate(0.0, pivotOffset, 0.0);
    }
}