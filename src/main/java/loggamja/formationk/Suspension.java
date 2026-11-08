package loggamja.formationk;

import loggamja.formationk.helper.EntityRollManager;
import loggamja.formationk.helper.SpringSimulator;
import loggamja.formationk.option.Config;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Suspension implements ClientModInitializer {
    public static float pivotYOffset = 0.1f;

    private static final double DT  = 0.05;
    private static final double FREQ  = 1.66; // 공진주파수 (Hz)
    private static final double Q     = 1;    // 공진 팩터

    private static final class SuspensionState {
        final SpringSimulator.SpringState spring = new SpringSimulator.SpringState();
        long lastSeenTick = 0L;
    }

    private static final Map<UUID, SuspensionState> states = new HashMap<>();
    private static long tickCounter = 0L;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> onClientTick());

        // 무조건 렌더 스레드로 예약
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            client.execute(() -> {
                EntityRollManager.clear();
                clearStates();
            });
        });
    }

    private void onClientTick() {
        if (!Main.isPlayingInGame()) return;

        var world = Minecraft.getInstance().level;
        if (world == null) return;

        if (Config.INSTANCE.suspensionEffect == 0) {
            // 대상별 0 되돌리기 순회 대신 통째로 비우기
            if (!states.isEmpty() || !EntityRollManager.isEmpty()) {
                states.clear();
                EntityRollManager.clear();
            }
            return;
        }

        tickCounter++;
        int processed = 0;
        for (Player player : world.players()) {
            if (!Main.isRidingKart(player)) continue;
            if (Main.getS2CValue(player, "data-engine-real") == 3) {
                applyRoll(player, player.getRootVehicle().getPassengers(), 0f);
                continue;
            }

            SuspensionState st = states.computeIfAbsent(player.getUUID(), k -> new SuspensionState());
            st.lastSeenTick = tickCounter;
            processPlayer(player, st);
            processed++;
        }

        // 이번 틱에 카트를 타지 않은 플레이어의 상태 제거
        if (states.size() > processed) {
            final long tc = tickCounter;
            states.entrySet().removeIf(e -> {
                if (e.getValue().lastSeenTick != tc) {
                    EntityRollManager.remove(e.getKey());
                    return true;
                }
                return false;
            });
        }
    }

    private void processPlayer(Player player, SuspensionState st) {
        Entity kart = player.getRootVehicle();
        List<Entity> passengers = kart.getPassengers();

        int isBike = Main.getS2CValue(player, "data-is-bike");
        boolean isModelRotateAllowed = Main.getAllowModelRotation(player);

        float moveDirection = 0f;
        float steerDirection = 0f;
        for (var i : passengers) {
            if (Main.hasCertainName(i, "formationk-direction")) {
                moveDirection = i.getYRot();
            }
            else if (Main.hasCertainName(i, "formationk-datacarrier")) {
                steerDirection = i.getYRot();
            }
        }
        var clampedDriftAngle = Mth.degreesDifference(moveDirection, steerDirection);

        if (clampedDriftAngle > 90) clampedDriftAngle = 180 - clampedDriftAngle;
        if (clampedDriftAngle < -90) clampedDriftAngle = -180 - clampedDriftAngle;

        final double a = (50 * 2 / Math.PI);
        final double b = 0.5;
        clampedDriftAngle = (float) (a * Math.atan(b / a * clampedDriftAngle));

        if (isBike == 1) {
            if (Config.INSTANCE.bikeSuspension == 2) {
                clampedDriftAngle *= 5;
            }
        }

        if (!isModelRotateAllowed) clampedDriftAngle = 0;

        SpringSimulator.step(st.spring, DT, FREQ, Q, -(clampedDriftAngle / 2f));
        double modelRollValue = Math.toDegrees(st.spring.x);

        // 4륜 카트 옵션
        if (isBike == 1 && Config.INSTANCE.bikeSuspension != 1) {
            modelRollValue *= -1;
        }

        applyRoll(player, passengers, (float) modelRollValue);
    }

    private static void applyRoll(Player player, List<Entity> passengers, float roll) {
        for (var i : passengers) {
            if (Main.hasCertainName(i, "formationk-modelsaddle")) {
                for (var j : i.getPassengers()) {
                    EntityRollManager.setRoll(j.getUUID(), roll, 1);
                }
            }
        }
        EntityRollManager.setRoll(player.getUUID(), roll, 1);
    }

    public static void clearStates() {
        states.clear();
    }
}
