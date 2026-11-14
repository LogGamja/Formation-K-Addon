package loggamja.formationk;

import loggamja.formationk.helper.WheelManager;
import loggamja.formationk.option.Config;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class Wheel implements ClientModInitializer {
    private static final float MAX_STEER_DEG = 35f;
    private static final float MIN_STEER_SPEED = 0.5f;

    private static final float GRIP_STEER_SCALE = -0.75f;
    private static final float DRIFT_STEER_SCALE = 0.5f;

    // 틱당 회전량(도) 1당 바퀴가 꺾이는 각도
    private static final float ANGULAR_STEER_SCALE = 6f;

    // Config.wheelSteer: 1 기본[1](각속도), 2 카운터[2](각속도), 3 코너각[3], 4 코너각 카운터[4]
    private static final int LAST_ANGULAR_STEER = 2;

    private record PlayerYaw(float yaw, long tick) {}

    // 플레이어 회전은 틱 시작에 yRotO 로 복사되어 yRot - yRotO 가 항상 0이라 직접 기록한다
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
        if (!usesAngularVelocity(Config.INSTANCE.wheelSteer)) playerYaws.clear();

        int ridingKartCount = 0;
        for (Player player : world.players()) {
            if (!Main.isRidingKart(player)) continue;

            processPlayer(player, gameTime);
            ridingKartCount++;
        }

        if (playerYaws.size() > ridingKartCount) {
            playerYaws.entrySet().removeIf(e -> e.getValue().tick() != gameTime);
        }
        WheelManager.endTick(ridingKartCount);
    }

    private void processPlayer(Player player, long gameTime) {
        var kart = player.getRootVehicle();

        double kartDeltaX = kart.getX() - kart.xo;
        double kartDeltaZ = kart.getZ() - kart.zo;
        float positionSpeed = (float) Math.hypot(kartDeltaX, kartDeltaZ) * 20f;

        float speedMetersPerSecond = positionSpeed;
        if (player == Main.getCameraPlayer() && RidingCamera.timeAfterLastActionbar <= 20) {
            speedMetersPerSecond = RidingCamera.actionbarSpeed / 3.6f;
        }

        int wheelSteer = Config.INSTANCE.wheelSteer;

        float yawPerTick = 0f;
        if (usesAngularVelocity(wheelSteer)) {
            var prevPlayerYaw = playerYaws.put(player.getUUID(), new PlayerYaw(player.getYRot(), gameTime));
            if (prevPlayerYaw != null) yawPerTick = Mth.wrapDegrees(player.getYRot() - prevPlayerYaw.yaw());
        }

        float targetSteerAngle = 0f;
        if (wheelSteer > 0 && positionSpeed > MIN_STEER_SPEED) {
            boolean isCounterSteer = wheelSteer % 2 == 0;
            boolean usesDriftDirection = Main.getS2CValue(player, "state-drift") == 1 && isCounterSteer;

            if (usesAngularVelocity(wheelSteer)) {
                targetSteerAngle = yawPerTick * (usesDriftDirection ? -ANGULAR_STEER_SCALE : ANGULAR_STEER_SCALE);
            }
            else {
                for (var passenger : kart.getPassengers()) {
                    if (!Main.hasCertainName(passenger, "formationk-datacarrier")) continue;

                    float movementYaw = (float) Math.toDegrees(Math.atan2(-kartDeltaX, kartDeltaZ));
                    float yawDifference = Mth.wrapDegrees(movementYaw - passenger.getYRot());
                    targetSteerAngle = yawDifference * (usesDriftDirection ? DRIFT_STEER_SCALE : GRIP_STEER_SCALE);
                }
            }
            targetSteerAngle = Mth.clamp(targetSteerAngle, -MAX_STEER_DEG, MAX_STEER_DEG);
        }

        WheelManager.update(kart.getUUID(), speedMetersPerSecond, targetSteerAngle);
    }

    private static boolean usesAngularVelocity(int wheelSteer) {
        return wheelSteer >= 1 && wheelSteer <= LAST_ANGULAR_STEER;
    }
}
