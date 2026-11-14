package loggamja.formationk;

import loggamja.formationk.helper.WheelManager;
import loggamja.formationk.option.Config;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class Wheel implements ClientModInitializer {
    private static final float MAX_STEER_DEG = 35f;
    private static final float MIN_STEER_SPEED = 0.5f;

    // 그립주행과 카운터 스티어 가중치
    private static final float GRIP_STEER_SCALE = -0.75f;
    private static final float DRIFT_STEER_SCALE = 0.5f;

    private static final float STEER_WEIGHT = 0.8f;
    private static final float YAW_PER_TICK_SCALE = -8f;

    // 각속도 계열
    private record PlayerYaw(float yaw, long tick) {}
    private static final Map<UUID, PlayerYaw> playerYaws = new HashMap<>();

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> onClientTick());

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            WheelManager.clear();
            playerYaws.clear();
        }));
    }

    private void onClientTick() {
        if (!Main.isPlayingInGame()) return;

        if (Config.INSTANCE.wheelRoll == 0 && Config.INSTANCE.wheelSteer == 0) {
            WheelManager.clear();
            playerYaws.clear();
            return;
        }

        var world = Objects.requireNonNull(Minecraft.getInstance().level);
        long gameTime = world.getGameTime();

        int ridingKartCount = 0;
        for (Player player : world.players()) {
            if (!Main.isRidingKart(player)) continue;

            processPlayer(player, gameTime);
            ridingKartCount++;
        }

        // 이번 틱에 갱신되지 않은 기록 삭제
        playerYaws.values().removeIf(playerYaw -> playerYaw.tick() != gameTime);
        WheelManager.endTick(ridingKartCount);
    }

    private void processPlayer(Player player, long gameTime) {
        var kart = player.getRootVehicle();

        double kartDeltaX = kart.getX() - kart.xo;
        double kartDeltaZ = kart.getZ() - kart.zo;
        float positionSpeed = (float) Math.hypot(kartDeltaX, kartDeltaZ) * 20f;

        float speedMetersPerSecond = positionSpeed;
        if (player == Main.getCameraPlayer() && RidingCamera.isActionbarSpeedFresh()) {
            speedMetersPerSecond = RidingCamera.actionbarSpeed / 3.6f;
        }

        int wheelSteer = Config.INSTANCE.wheelSteer;
        boolean usesAngularVelocity = wheelSteer == 1 || wheelSteer == 2;
        boolean isCounterSteer = wheelSteer == 2 || wheelSteer == 4;

        float targetSteerAngle = 0f;
        if (wheelSteer > 0) {
            float steerInput = usesAngularVelocity
                ? getSteerSpeed(player, gameTime) * YAW_PER_TICK_SCALE
                : getDriftAngle(kart, kartDeltaX, kartDeltaZ);

            if (usesAngularVelocity || positionSpeed > MIN_STEER_SPEED) {
                boolean usesDriftDirection = isCounterSteer && Main.getS2CValue(player, "state-drift") == 1;
                float steerScale = usesDriftDirection ? DRIFT_STEER_SCALE : GRIP_STEER_SCALE;

                targetSteerAngle = Mth.clamp(steerInput * steerScale * STEER_WEIGHT, -MAX_STEER_DEG, MAX_STEER_DEG);
            }
        }

        WheelManager.update(kart.getUUID(), speedMetersPerSecond, targetSteerAngle);
    }

    private static float getSteerSpeed(Player player, long gameTime) {
        var prevPlayerYaw = playerYaws.put(player.getUUID(), new PlayerYaw(player.getYRot(), gameTime));
        return prevPlayerYaw == null ? 0f : Mth.wrapDegrees(player.getYRot() - prevPlayerYaw.yaw());
    }

    private static float getDriftAngle(Entity kart, double kartDeltaX, double kartDeltaZ) {
        float movementYaw = (float) Math.toDegrees(Math.atan2(-kartDeltaX, kartDeltaZ));

        for (var passenger : kart.getPassengers()) {
            if (!Main.hasCertainName(passenger, "formationk-datacarrier")) continue;

            return Mth.wrapDegrees(movementYaw - passenger.getYRot());
        }
        return 0f;
    }
}
