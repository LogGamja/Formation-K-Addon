package loggamja.formationk;

import loggamja.formationk.helper.WheelManager;
import loggamja.formationk.option.Config;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;

public class Wheel implements ClientModInitializer {
    private static final float MAX_STEER_DEG = 35f;
    private static final float MIN_STEER_SPEED = 0.5f;

    private static final float GRIP_STEER_SCALE = -1f;
    private static final float DRIFT_STEER_SCALE = 0.5f;


    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> onClientTick());

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(WheelManager::clear));
    }

    private void onClientTick() {
        if (!Main.isPlayingInGame()) return;

        if (Config.INSTANCE.wheelEffect == 0) {
            WheelManager.clear();
            return;
        }

        var world = Objects.requireNonNull(Minecraft.getInstance().level);

        int ridingKartCount = 0;
        for (Player player : world.players()) {
            if (!Main.isRidingKart(player)) continue;

            processPlayer(player);
            ridingKartCount++;
        }

        WheelManager.endTick(ridingKartCount);
    }

    private void processPlayer(Player player) {
        var kart = player.getRootVehicle();

        double kartDeltaX = kart.getX() - kart.xo;
        double kartDeltaZ = kart.getZ() - kart.zo;
        float positionSpeed = (float) Math.hypot(kartDeltaX, kartDeltaZ) * 20f;

        float speedMetersPerSecond = positionSpeed;
        if (player == Main.getCameraPlayer() && Camera.timeAfterLastActionbar <= 20) {
            speedMetersPerSecond = Camera.actionbarSpeed / 3.6f;
        }

        float targetSteerAngle = 0f;
        if (positionSpeed > MIN_STEER_SPEED) {
            for (var passenger : kart.getPassengers()) {
                if (!Main.hasCertainName(passenger, "formationk-datacarrier")) continue;

                float movementYaw = (float) Math.toDegrees(Math.atan2(-kartDeltaX, kartDeltaZ));
                float yawDifference = Mth.wrapDegrees(movementYaw - passenger.getYRot());

                if (Main.getS2CValue(player, "state-drift") == 1) {
                    yawDifference *= DRIFT_STEER_SCALE;
                }
                else {
                    yawDifference *= GRIP_STEER_SCALE;
                }
                targetSteerAngle = Mth.clamp(yawDifference, -MAX_STEER_DEG, MAX_STEER_DEG);
            }
        }

        WheelManager.update(kart.getUUID(), speedMetersPerSecond, targetSteerAngle);
    }
}
