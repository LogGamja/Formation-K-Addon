package loggamja.formationk;

import loggamja.formationk.option.Config;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 옵저버 카메라: 실제로 다른 라이더를 관전 중일 때 작동
public class ObserverCamera implements ClientModInitializer {
    static final double TELEPORT_DISTANCE_THRESHOLD = 16.0;
    static UUID lastObservedTargetUUID = null;
    static Vec3 lastObservedPos = null;

    static final float DEFAULT_PITCH = 15f;
    static final float MIN_PITCH = 10f;
    static final float MAX_PITCH = 60f;

    static final float LOCAL_PITCH_DOWN_OFFSET = 10f;

    static final int VELOCITY_WINDOW_SIZE = 15;
    static final float VERTICAL_VELOCITY_MULTIPLIER = 0.75f;

    static Double lastY = null;
    static List<Float> verticalVelocityBuffer = new ArrayList<>();

    static float pitch = DEFAULT_PITCH;
    static float pitchAtPrevTick = DEFAULT_PITCH;

    // 카트바디 모델 방향으로의 보간
    static float kartBodyYawRaw = 0f;
    static final float ANCHOR_YAW_SMOOTH_TIME = 0.3f;
    static final float ANCHOR_YAW_MAX_DT = 0.1f;
    static long anchorYawLastTimeNanos = 0L;
    static float renderedAnchorYaw = 0f;
    static boolean needsAnchorResync = true;


    @Override
    public void onInitializeClient() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> onClientTickStart());
    }
    void onClientTickStart() {
        if (!isObserverLogicActive()) {
            if (lastObservedTargetUUID != null) reset();
            return;
        }

        Player target = Main.getCameraPlayer();
        float newYaw = resolveYaw(target, 1f);
        Vec3 pos = target.position();

        // 대상 전환 감지와 리싱크는 헬퍼가 단독으로 처리한다. 틱은 헬퍼가 보지 않는 텔레포트만 담당
        boolean targetChanged = syncToTargetIfChanged(target, newYaw);
        boolean teleported = !targetChanged && lastObservedPos != null && pos.distanceTo(lastObservedPos) > TELEPORT_DISTANCE_THRESHOLD;

        if (teleported) resync(newYaw);

        lastObservedTargetUUID = target.getUUID();
        lastObservedPos = pos;

        updatePitch(target);
        kartBodyYawRaw = newYaw;
    }
    // 관전 대상이 바뀌면 화면에 다음 프레임이 그려지기 전에 앵커를 새 대상 방향으로 맞춰야 한다.
    // 틱 핸들러와 렌더 경로(updateAnchorYaw) 양쪽에서 호출되므로, 어느 쪽이 먼저 대상 전환을
    // 감지하더라도 나머지 한쪽이 중복 리싱크하지 않도록 UUID 갱신까지 여기서 함께 처리한다
    static boolean syncToTargetIfChanged(Player target, float newYaw) {
        if (target.getUUID().equals(lastObservedTargetUUID)) return false;

        resync(newYaw);
        lastObservedTargetUUID = target.getUUID();
        lastObservedPos = target.position();
        return true;
    }
    static void resync(float newYaw) {
        lastY = null;
        verticalVelocityBuffer.clear();
        syncAnchor(newYaw);

        pitch = DEFAULT_PITCH;
        pitchAtPrevTick = DEFAULT_PITCH;
    }
    static void syncAnchor(float newYaw) {
        kartBodyYawRaw = newYaw;
        renderedAnchorYaw = newYaw;

        anchorYawLastTimeNanos = 0L;
        needsAnchorResync = false;
    }
    void updatePitch(Player target) {
        double curY = target.getY();
        float tickInterval = Main.getSecondsPerTick();

        if (lastY != null && tickInterval > 0f) {
            float verticalVelocity = (float) ((curY - lastY) / tickInterval);
            addToVelocityBuffer(verticalVelocity);
        }
        lastY = curY;

        pitchAtPrevTick = pitch;
        float targetPitch = allowsModelRotation(target)
                ? DEFAULT_PITCH - averageVelocity() * VERTICAL_VELOCITY_MULTIPLIER
                : target.getViewXRot(1f);
        pitch = Mth.clamp(targetPitch, MIN_PITCH, MAX_PITCH);
    }
    // 정지상태, 루프에서는 유저의 시선을 따라감
    static boolean allowsModelRotation(Player target) {
        return Main.getAllowModelRotation(target);
    }
    static float resolveYaw(Player target, float tickDelta) {
        return allowsModelRotation(target)
                ? Main.getKartBodyYaw(target, tickDelta)
                : target.getViewYRot(tickDelta);
    }
    static void addToVelocityBuffer(float velocity) {
        verticalVelocityBuffer.add(velocity);
        if (verticalVelocityBuffer.size() > VELOCITY_WINDOW_SIZE) {
            verticalVelocityBuffer.removeFirst();
        }
    }
    static float averageVelocity() {
        if (verticalVelocityBuffer.isEmpty()) return 0f;

        float sum = 0f;
        for (float v : verticalVelocityBuffer) sum += v;
        return sum / verticalVelocityBuffer.size();
    }
    static void reset() {
        lastObservedTargetUUID = null;
        lastObservedPos = null;

        lastY = null;
        verticalVelocityBuffer.clear();
        pitch = DEFAULT_PITCH;
        pitchAtPrevTick = DEFAULT_PITCH;

        kartBodyYawRaw = 0f;
        anchorYawLastTimeNanos = 0L;
        renderedAnchorYaw = 0f;
        needsAnchorResync = true;
    }
    public static boolean isObserverLogicActive() {
        if (!Main.isPlayingInGame() || !Main.isCameraPlayerRidingKart) return false;
        if (Config.INSTANCE.spectatorCameraMode == 0) return false;

        return Main.isSpectatingPlayer();
    }
    public static float getPitchOverride(float vanillaPitch, float tickDelta) {
        if (!isObserverLogicActive()) return vanillaPitch;
        return Mth.lerp(tickDelta, pitchAtPrevTick, pitch);
    }
    // 1인칭이나 2인칭 상태에서 호출
    public static void requestAnchorResync() {
        needsAnchorResync = true;
    }
    // 앵커 위치 계산에 쓰이는 yaw. 카트바디 방향의 지수평균으로 대체한다.
    // Camera#setRotation의 @ModifyVariable에서만 프레임당 정확히 한 번 호출해야 한다
    public static float updateAnchorYaw(float vanillaYaw) {
        if (!isObserverLogicActive()) return vanillaYaw;

        // 대상 전환도 틱을 기다리면 그 사이 프레임들이 이전 대상의 앵커로 그려진다
        Player target = Main.getCameraPlayer();
        syncToTargetIfChanged(target, resolveYaw(target, 1f));

        // 틱을 기다리면 그 사이 프레임들이 초기화된 방향을 그대로 그린다
        if (needsAnchorResync) syncAnchor(resolveYaw(Main.getCameraPlayer(), 1f));

        long now = System.nanoTime();
        float dt = (anchorYawLastTimeNanos == 0L) ? 0f : (now - anchorYawLastTimeNanos) / 1.0e9f;
        anchorYawLastTimeNanos = now;
        dt = Mth.clamp(dt, 0f, ANCHOR_YAW_MAX_DT);

        float alpha = 1f - (float) Math.exp(-dt / ANCHOR_YAW_SMOOTH_TIME);
        float step = Mth.wrapDegrees(kartBodyYawRaw - renderedAnchorYaw) * alpha;
        renderedAnchorYaw = Mth.wrapDegrees(renderedAnchorYaw + step);


        return renderedAnchorYaw;
    }
    public static float getLocalPitchOffset() {
        if (!isObserverLogicActive()) return 0f;
        return LOCAL_PITCH_DOWN_OFFSET;
    }
}
