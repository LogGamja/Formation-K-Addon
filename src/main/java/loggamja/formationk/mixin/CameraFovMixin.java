package loggamja.formationk.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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

        if (!Main.isCameraPlayerRidingKart) return;

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

        if (!Main.isCameraPlayerRidingKart) return;

        if (FlashbackDetector.isOverridingFov()) return;

        if (Config.INSTANCE.cameraMode == 0) {
            formationk$backupFov = formationk$options.fov().get();
            formationk$isFovBackupedThisFrame = true;

            final int customFov = Config.INSTANCE.FOV;
            formationk$options.fov().set(customFov);
        }
        else {
            cir.setReturnValue(formationk$getCameraModeFov(tickDelta));
        }
    }
    // Flashback은 calculateFov를 @WrapMethod로 감싸 리플레이 중엔 원본(위 HEAD 포함)을 부르지 않으므로 호출부에서 감싼다
    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;calculateFov(F)F"))
    private float formationk$calculateFovInReplay(Camera camera, float tickDelta, Operation<Float> original) {
        if (!FlashbackDetector.isInReplay()
                || !Main.isCameraPlayerRidingKart
                || FlashbackDetector.isOverridingFov()
                || camera.isPanoramicMode()) {
            return original.call(camera, tickDelta);
        }

        if (Config.INSTANCE.cameraMode == 0) {
            return Config.INSTANCE.FOV * Mth.lerp(tickDelta, oldFovModifier, fovModifier);
        }
        return formationk$getCameraModeFov(tickDelta);
    }
    @Unique
    private float formationk$getCameraModeFov(float tickDelta) {
        float interpolatedFOV = Mth.lerp(tickDelta, loggamja.formationk.Camera.filteredFOVAtPrevTick, loggamja.formationk.Camera.filteredFOV);
        if (formationk$options.getCameraType() == CameraType.FIRST_PERSON) {
            interpolatedFOV *= 0.825f;
        }
        return interpolatedFOV;
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
