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

public class Wheel implements ClientModInitializer {
    private static final float MAX_STEER_DEG = 35f;
    private static final float GRIP_STEER_SCALE = 1f;
    private static final float DRIFT_STEER_SCALE = 0.5f;
    private static final float MIN_STEER_SPEED = 0.5f;

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

        var world = Minecraft.getInstance().level;
        if (world == null) return;

        WheelManager.tick();
        for (Player player : world.players()) {
            if (!Main.isRidingKart(player)) continue;
            processPlayer(player);
        }
    }

    private void processPlayer(Player player) {
        Entity kart = player.getRootVehicle();

        double dx = kart.getX() - kart.xo;
        double dz = kart.getZ() - kart.zo;
        double moved = Math.sqrt(dx * dx + dz * dz) * 20.0;

        double speed = moved;
        if (player == Main.getCameraPlayer() && Camera.timeAfterLastActionbar <= 20) {
            speed = Camera.actionbarSpeed / 3.6;
        }

        float steerTarget = 0f;
        if (moved > MIN_STEER_SPEED) {
            float steerDirection = 0f;
            boolean hasCarrier = false;
            for (var i : kart.getPassengers()) {
                if (Main.hasCertainName(i, "formationk-datacarrier")) {
                    steerDirection = i.getYRot();
                    hasCarrier = true;
                }
            }

            if (hasCarrier) {
                float heading = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float delta = Mth.wrapDegrees(heading - steerDirection);
                // 그립 주행은 기준 방향이 먼저 돌고 이동 방향이 늦게 따라오므로 핸들을 반대로 꺾는다
                if (Main.getS2CValue(player, "state-drift") == 1) {
                    delta *= DRIFT_STEER_SCALE;
                } else {
                    delta *= -GRIP_STEER_SCALE;
                }
                steerTarget = Mth.clamp(delta, -MAX_STEER_DEG, MAX_STEER_DEG);
            }
        }

        WheelManager.update(kart.getUUID(), (float) speed, steerTarget);
    }
}
