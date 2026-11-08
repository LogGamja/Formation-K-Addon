package loggamja.formationk.mixin;

import loggamja.formationk.ObserverCamera;
import loggamja.formationk.option.Config;
import loggamja.formationk.Main;
import loggamja.formationk.helper.EntityRollManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public class CameraMixin {

    @Shadow private Quaternionf rotation; // final 이라도 객체 자체는 변경 가능
    @Shadow private Entity entity;
    @Shadow private boolean detached;

    // 카메라 롤 지수 평활(EMA) 상태
    @Unique private long formationk$lastTime = 0L;
    @Unique private float formationk$smoothRoll = 0f;

    @Unique private static final float formationk$MAX_DT      = 0.1f;
    @Unique private static final float formationk$SMOOTH_TIME = 0.15f;
    @Unique private static final float formationk$ROLL_MULTIPLIER = 0.25f;
    @Unique private static final float formationk$MIN_ROLL = 1.0e-4f;

    @Unique
    private boolean formationk$canApplyRoll() {
        return Main.kartEngine != 3
                && Config.INSTANCE.suspensionEffect == 2
                && this.entity instanceof Player;
    }

    // 기본 카메라이면 null을 반환해 vanilla 거리를 그대로 사용
    @Unique
    private static Float formationk$computeCustomDistance(float f) {
        var newDistance = loggamja.formationk.Camera.getCameraDistanceOffset(f);
        if (newDistance == f) return null;

        var delta = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var newDistanceAtPrevTick = loggamja.formationk.Camera.getCameraDistanceOffsetAtPrevTick(f);
        return Mth.lerp(delta, newDistanceAtPrevTick, newDistance);
    }
    // 카메라 모드에 무관하게 노클립
    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void formationk$clipToSpace(float f, CallbackInfoReturnable<Float> cir) {
        if (!Main.isCameraPlayerRidingKart || !Config.INSTANCE.useNoclipCamera) return;

        Float distance = formationk$computeCustomDistance(f);
        cir.setReturnValue(distance != null ? distance : f);
    }
    // 바닐라가 커스텀 카메라 거리로 벽 충돌검사를 하도록 바꿔치기한다
    @ModifyVariable(method = "getMaxZoom", at = @At("HEAD"), argsOnly = true)
    private float formationk$overrideClipDistance(float f) {
        if (!Main.isCameraPlayerRidingKart || Config.INSTANCE.useNoclipCamera) return f;

        Float distance = formationk$computeCustomDistance(f);
        return distance != null ? distance : f;
    }

    @Inject(method = "setRotation(FF)V", at = @At("TAIL"))
    private void formationk$setRotation(float yaw, float pitch, CallbackInfo ci) {
        formationk$applyObserverPitchOffset();

        if (!formationk$canApplyRoll()) return;
        Player player = (Player) this.entity;

        long now = System.nanoTime();
        float dt = (formationk$lastTime == 0L) ? 0f : (now - formationk$lastTime) / 1.0e9f;
        formationk$lastTime = now;

        if (dt < 0f) dt = 0f;
        if (dt > formationk$MAX_DT) dt = formationk$MAX_DT;

        // 0이어도 그대로 반영해야 평균이 0으로 수렴한다
        float raw = EntityRollManager.getCurrentRoll(player.getUUID()) * formationk$ROLL_MULTIPLIER;
        var isBike = Main.getS2CValue(player, "data-is-bike");
        if (isBike == 1 && Config.INSTANCE.bikeSuspension == 3) {
            raw /= 4;
        }

        // alpha는 항상 [0,1)이라 튀거나 발산하지 않음
        float alpha = 1f - (float) Math.exp(-dt / formationk$SMOOTH_TIME);
        formationk$smoothRoll += (raw - formationk$smoothRoll) * alpha;

        if (!Float.isFinite(formationk$smoothRoll)) formationk$smoothRoll = raw;

        if (Math.abs(formationk$smoothRoll) < formationk$MIN_ROLL) return;
        this.rotation.rotateZ((float) Math.toRadians(formationk$smoothRoll));
    }

    // 시점 행렬은 update()가 setRotation 직후 캐시하므로 오프셋도 여기서 넣어야 화면에 반영된다.
    // 앵커 위치는 setRotation 안에서 이미 계산된 방향 벡터로 정해지므로 화면 방향만 바뀐다.
    @Unique
    private void formationk$applyObserverPitchOffset() {
        if (!this.detached || Minecraft.getInstance().options.getCameraType().isMirrored()) return;

        float pitchOffset = ObserverCamera.getLocalPitchOffset();
        if (pitchOffset == 0f) return;

        this.rotation.rotateX((float) Math.toRadians(-pitchOffset));
    }

    // 앵커(카메라 위치) 계산에 쓰일 yaw를 카트바디(formationk-modelsaddle) 방향의 지수평균으로 대체한다.
    @ModifyVariable(method = "setRotation(FF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float formationk$overrideObserverAnchorYaw(float yaw) {
        if (Minecraft.getInstance().options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            ObserverCamera.requestAnchorResync();
            return yaw;
        }

        return ObserverCamera.updateAnchorYaw(yaw);
    }

    // pitch는 옵저버 카메라 로직(수직 속도 기반)으로 대체한다
    @ModifyVariable(method = "setRotation(FF)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float formationk$overrideObserverPitch(float pitch) {
        if (Minecraft.getInstance().options.getCameraType() != CameraType.THIRD_PERSON_BACK) return pitch;
        if (!ObserverCamera.isObserverLogicActive()) return pitch;

        float delta = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return ObserverCamera.getPitchOverride(pitch, delta);
    }
}