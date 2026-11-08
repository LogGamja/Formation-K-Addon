package loggamja.formationk;

import loggamja.formationk.option.Config;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Camera implements ClientModInitializer {
    static Minecraft client = Minecraft.getInstance();

    public static List<Float> targetFovBuffer = new ArrayList<>(Collections.nCopies(1, 0f));

    static boolean isUsingBooster = false;

    float filteredTargetFOV = 90;
    public static float filteredFOV = 90;
    public static float filteredFOVAtPrevTick = 90;

    static double cameraDistanceOffset = 545;
    static double cameraDistanceOffsetAtPrevTick = 545;

    static final double BASE_DISTANCE = 545;
    final double DIST_COEFFICIENT = 1.414;

    static double linearTransformTargetBaseDistance = BASE_DISTANCE;

    static Vec3 lastPos = null;
    public static float realSpeed = 0f;
    public static float realDirection = 0f;

    private static final float MIN_MOVE_FOR_DIRECTION = 0.0001f;
    public static float actionbarSpeed = 0f;
    public static int timeAfterLastActionbar = 0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            onClientTickStart();
        });
    }

    void onClientTickStart() {
        if (!Main.isPlayingInGame() || !Main.isCameraPlayerRidingKart) return;

        // 카트의 틱당 위치 차분(realSpeed/realDirection)은 카메라 연출 옵션(cameraMode)과 무관하게 항상 갱신되어야 함
        calculateSpeed();

        if (Config.INSTANCE.cameraMode == 0) return;

        detectBooster();

        int armSpeedMultiplier = 2;
        if (Config.INSTANCE.cameraMode == 1) {
            armSpeedMultiplier = 6;
            linearTransformTargetBaseDistance = 605;
            addToTargetFovBuffer(isUsingBooster ? 118f : 102f);
        }
        else if (Config.INSTANCE.cameraMode == 2) {
            armSpeedMultiplier = 5;
            linearTransformTargetBaseDistance = 600;
            addToTargetFovBuffer(isUsingBooster ? 120f : 100f);
        }
        else if (Config.INSTANCE.cameraMode == 3) {
            armSpeedMultiplier = 4;
            linearTransformTargetBaseDistance = 595;
            addToTargetFovBuffer(isUsingBooster ? 122f : 98f);
        }

        filteredFOVAtPrevTick = filteredFOV;
        if (isUsingBooster) {
            filteredFOV = interpolate(filteredFOV, filteredTargetFOV, 1f);
        }
        else {
            filteredFOV = interpolate(filteredFOV, filteredTargetFOV, 0.65f);
        }

        final double deltaTime = Main.getSecondsPerTick();

        final double ARM_SPEED_FORWARD = 0.08 * armSpeedMultiplier;
        final double ARM_SPEED_BACKWARD = 4 * armSpeedMultiplier;

        timeAfterLastActionbar++;
        float speed = (timeAfterLastActionbar > 20) ? realSpeed : actionbarSpeed;
        if (realSpeed > speed) speed++;

        final double convertedSpeed = speed * 24.83;
        var targetDistance = BASE_DISTANCE + (convertedSpeed * 0.0003284 * BASE_DISTANCE * (DIST_COEFFICIENT - 1.0));

        cameraDistanceOffsetAtPrevTick = cameraDistanceOffset;

        var rate = ARM_SPEED_BACKWARD;
        if (cameraDistanceOffset < targetDistance) {
            rate = ARM_SPEED_FORWARD;
        }
        var decay = Math.exp(-rate * DIST_COEFFICIENT * deltaTime);
        cameraDistanceOffset = targetDistance + (cameraDistanceOffset - targetDistance) * decay;

        //System.out.println(linearMap(cameraDistanceOffset, BASE_DISTANCE, 815, 600, 815));
    }
    void calculateSpeed() {
        Vec3 cur = Main.getCameraPlayer().getEyePosition(1);

        if (lastPos != null) {
            float dx = (float) (cur.x - lastPos.x);
            float dy = (float) (cur.y - lastPos.y);
            float dz = (float) (cur.z - lastPos.z);
            float distPerTick = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);

            float tickInterval = Main.getSecondsPerTick();
            if (tickInterval > 0f) {
                realSpeed = (distPerTick / tickInterval) * 3.6f;
            }
            
            // 이동방향 구하기
            if (distPerTick > MIN_MOVE_FOR_DIRECTION) {
                float yawRad = (float) Mth.atan2(-dx, dz);
                realDirection = Mth.wrapDegrees((float) Math.toDegrees(yawRad));
            }
        }
        lastPos = cur;
    }
    void addToTargetFovBuffer(float newFov) {
        targetFovBuffer.add(newFov);

        if (targetFovBuffer.size() > 5) {
            targetFovBuffer.removeFirst();
        }
        filteredTargetFOV = Collections.max(targetFovBuffer);
    }
    void detectBooster() {
        var boostState = Main.getS2CValue(Main.getCameraPlayer(), "state-boost");

        isUsingBooster = boostState > 0;
    }
    public static float interpolate(float current, float target, float temporalGradient) {
        float tickInterval = Main.getSecondsPerTick();

        // 틱레이트가 극단적으로 느려지면 alpha가 1을 넘어 목표를 지나쳐 진동
        float alpha = Mth.clamp(tickInterval * temporalGradient, 0f, 1f);
        return current + (target - current) * alpha;
    }
    // 관전 카메라 옵션에 따른 거리 증가
    static final float OBSERVER_DISTANCE_MULTIPLIER = 1.5f;

    // 바닐라 Camera#update가 clipToSpace에 넘기는 기준 거리(4.0F * entity scale).
    // originalDistance / 이 값 = 엔티티 스케일 배수이므로, 커스텀 거리에도 곱해 스케일을 보존한다
    static final float VANILLA_BASE_CLIP_DISTANCE = 4.0f;

    static boolean isObserverBoostActive() {
        return Config.INSTANCE.spectatorCameraMode != 0 && Main.isSpectatingPlayer();
    }

    public static float getCameraDistanceOffset(float originalDistance) {
        if (client.options.getCameraType() == CameraType.FIRST_PERSON)
            return originalDistance;

        boolean observerBoost = isObserverBoostActive();
        if (Config.INSTANCE.cameraMode == 0)
            return observerBoost ? originalDistance * OBSERVER_DISTANCE_MULTIPLIER : originalDistance;

        float distance = (float) linearMap(cameraDistanceOffset, BASE_DISTANCE, 815, linearTransformTargetBaseDistance, 815) / 200;
        distance *= originalDistance / VANILLA_BASE_CLIP_DISTANCE;
        return observerBoost ? distance * OBSERVER_DISTANCE_MULTIPLIER : distance;
    }
    public static float getCameraDistanceOffsetAtPrevTick(float originalDistance) {
        if (client.options.getCameraType() == CameraType.FIRST_PERSON)
            return originalDistance;

        boolean observerBoost = isObserverBoostActive();
        if (Config.INSTANCE.cameraMode == 0)
            return observerBoost ? originalDistance * OBSERVER_DISTANCE_MULTIPLIER : originalDistance;

        float distance = (float) linearMap(cameraDistanceOffsetAtPrevTick, BASE_DISTANCE, 815, linearTransformTargetBaseDistance, 815) / 200;
        distance *= originalDistance / VANILLA_BASE_CLIP_DISTANCE;
        return observerBoost ? distance * OBSERVER_DISTANCE_MULTIPLIER : distance;
    }
    // 기본 카메라 거리를 늘리기 위한 선형변환식
    static double linearMap(double x, double a, double b, double c, double d) {
        return c + ((x - a) / (b - a)) * (d - c);
    }
}