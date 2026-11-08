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
import net.minecraft.client.DeltaTracker;
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

    // 카메라 롤 지수 평활(EMA) 상태
    @Unique private long formationk$lastTime = 0L;
    @Unique private float formationk$smoothRoll = 0f;

    @Unique private static final float formationk$MAX_DT      = 0.1f;
    @Unique private static final float formationk$SMOOTH_TIME = 0.15f;
    @Unique private static final float formationk$ROLL_MULTIPLIER = 0.25f;

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
        if (!Main.isRidingKart || !Config.INSTANCE.useNoclipCamera) return;

        Float distance = formationk$computeCustomDistance(f);
        cir.setReturnValue(distance != null ? distance : f);
    }
    // 바닐라가 커스텀 카메라 거리로 벽 충돌검사를 하도록 바꿔치기한다
    @ModifyVariable(method = "getMaxZoom", at = @At("HEAD"), argsOnly = true)
    private float formationk$overrideClipDistance(float f) {
        if (!Main.isRidingKart || Config.INSTANCE.useNoclipCamera) return f;

        Float distance = formationk$computeCustomDistance(f);
        return distance != null ? distance : f;
    }

    @Inject(method = "setRotation(FF)V", at = @At("TAIL"))
    private void formationk$setRotation(float yaw, float pitch, CallbackInfo ci) {
        if (Main.kartEngine == 3) return;
        if (Config.INSTANCE.suspensionEffect != 2) return;

        long now = System.nanoTime();
        float dt = (formationk$lastTime == 0L) ? 0f : (now - formationk$lastTime) / 1.0e9f;
        formationk$lastTime = now;

        if (dt < 0f) dt = 0f;
        if (dt > formationk$MAX_DT) dt = formationk$MAX_DT;

        Entity focused = ((Camera) (Object) this).entity();
        if (!(focused instanceof Player player)) return;

        // 0이어도 그대로 반영해야 평균이 0으로 수렴한다
        float raw = EntityRollManager.getCurrentRoll(player.getUUID()) * formationk$ROLL_MULTIPLIER;
        var isBike = Main.getS2CValue(player, "data-is-bike");
        if (isBike == 1 && Config.INSTANCE.bikeSuspension == 3) {
            raw /= 4;
        }

        // alpha는 항상 (0,1]이라 튀거나 발산하지 않음
        float alpha = (formationk$SMOOTH_TIME <= 0f) ? 1f
                : 1f - (float) Math.exp(-dt / formationk$SMOOTH_TIME);
        formationk$smoothRoll += (raw - formationk$smoothRoll) * alpha;

        if (!Float.isFinite(formationk$smoothRoll)) formationk$smoothRoll = raw;

        if (Math.abs(formationk$smoothRoll) < 1.0e-4f) return;
        this.rotation.rotateZ((float) Math.toRadians(formationk$smoothRoll));
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

    // TAIL이라 앵커 위치는 그대로 두고 화면 방향만 바뀐다.
    // 서스펜션 롤과 축이 섞이지 않도록 롤을 떼었다가 yaw(월드축 premul)/pitch(로컬축)를 적용한 뒤 롤을 다시 붙인다.
    @Inject(method = "update", at = @At("TAIL"))
    private void formationk$applyObserverOffsets(DeltaTracker deltaTracker, CallbackInfo ci) {
        boolean thirdPerson = ((Camera) (Object) this).isDetached();
        boolean inverseView = Minecraft.getInstance().options.getCameraType().isMirrored();
        if (!thirdPerson || inverseView) return;

        float pitchOffset = ObserverCamera.getLocalPitchOffset();
        if (pitchOffset == 0f) return;

        boolean hasRoll = Config.INSTANCE.suspensionEffect == 2 && Math.abs(formationk$smoothRoll) >= 1.0e-4f;
        float rollRad = hasRoll ? (float) Math.toRadians(formationk$smoothRoll) : 0f;

        if (hasRoll) this.rotation.rotateZ(-rollRad);

        if (pitchOffset != 0f) {
            this.rotation.rotateX((float) Math.toRadians(-pitchOffset));
        }

        if (hasRoll) this.rotation.rotateZ(rollRad);
    }
}