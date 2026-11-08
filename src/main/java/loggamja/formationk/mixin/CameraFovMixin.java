package loggamja.formationk.mixin;

import loggamja.formationk.Main;
import loggamja.formationk.helper.FlashbackDetector;
import loggamja.formationk.option.Config;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 1000 미만: calculateFov HEAD에서 Flashback보다 먼저 짜여야 아래 리플레이 분기가 산다
// 인젝션 포인트는 weaving 전에 일괄 해석되므로 먼저 짜인 쪽이 먼저 실행된다
@Mixin(value = Camera.class, priority = 900)
public abstract class CameraFovMixin {
    // Flashback용
    @Shadow private float fovModifier;
    @Shadow private float oldFovModifier;

    @Unique final Options formationk$options = Minecraft.getInstance().options;

    @Unique boolean formationk$isFovBackupedThisFrame;
    @Unique boolean formationk$isFovEffectScaleBackupedThisFrame;

    @Unique int formationk$backupFov;
    @Unique double formationk$backupFovEffectScale;

    @Inject(method = "tickFov", at = @At(value = "HEAD"))
    private void formationk$beforeFovMultiplierUpdate(CallbackInfo ci) {
        formationk$restoreFovEffectScale();

        if (!Main.isRidingKart) return;

        formationk$backupFovEffectScale = formationk$options.fovEffectScale().get();
        formationk$isFovEffectScaleBackupedThisFrame = true;

        double customEffectScale = Config.INSTANCE.FOVEffects / 100f;
        if (formationk$options.getCameraType() == CameraType.FIRST_PERSON) {
            customEffectScale /= 3;
        }
        formationk$options.fovEffectScale().set(customEffectScale);
    }

    @Inject(method = "tickFov", at = @At(value = "TAIL"))
    private void formationk$afterFovUpdate(CallbackInfo ci) {
        formationk$restoreFovEffectScale();
    }
    @Unique
    private void formationk$restoreFovEffectScale() {
        if (!formationk$isFovEffectScaleBackupedThisFrame) return;

        formationk$options.fovEffectScale().set(formationk$backupFovEffectScale);
        formationk$isFovEffectScaleBackupedThisFrame = false;
    }
    @Inject(method = "calculateFov", at = @At(value = "HEAD"), cancellable = true)
    private void formationk$beforeGetFov(float tickDelta, CallbackInfoReturnable<Float> cir) {
        // 옵션 오염 방지 안전망
        formationk$restoreFov();

        if (!Main.isRidingKart) return;

        if (FlashbackDetector.isOverridingFov()) return;

        if (Config.INSTANCE.cameraMode == 0) {
            if (FlashbackDetector.isInReplay()) {
                float multiplier = Mth.lerp(tickDelta, oldFovModifier, fovModifier);

                cir.setReturnValue(Config.INSTANCE.FOV * multiplier);
                return;
            }

            formationk$backupFov = formationk$options.fov().get();
            formationk$isFovBackupedThisFrame = true;

            final int customFov = Config.INSTANCE.FOV;
            formationk$options.fov().set(customFov);
        }
        else {
            var interpolatedFOV  = Mth.lerp(tickDelta, loggamja.formationk.Camera.filteredFOVAtPrevTick, loggamja.formationk.Camera.filteredFOV);
            if (formationk$options.getCameraType() == CameraType.FIRST_PERSON) {
                interpolatedFOV  *= 0.825f;
            }
            cir.setReturnValue(interpolatedFOV);
        }
    }
    @Inject(method = "calculateFov", at = @At(value = "TAIL"))
    private void formationk$afterGetFov(float tickDelta, CallbackInfoReturnable<Float> cir) {
        formationk$restoreFov();
    }
    @Unique
    private void formationk$restoreFov() {
        if (!formationk$isFovBackupedThisFrame) return;

        formationk$options.fov().set(formationk$backupFov);
        formationk$isFovBackupedThisFrame = false;
    }
}
