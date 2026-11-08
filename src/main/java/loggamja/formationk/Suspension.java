package loggamja.formationk;

import loggamja.formationk.helper.EntityRollManager;
import loggamja.formationk.helper.SpringSimulator;
import loggamja.formationk.option.Config;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Suspension implements ClientModInitializer {
    public static float pivotYOffset = 0.1f;

    private static final double DT  = 0.05;
    private static final double FREQ  = 1.66; // 공진주파수 (Hz)
    private static final double Q     = 1;    // 공진 팩터

    private static final int SWING_ANIMATION_TICKS = 6;
    private static final int DRIFT_START_TICKS = 10;

    private static final class SuspensionState {
        final List<Float> steerGradientBuffer = new ArrayList<>();
        float prevPlayerYaw = 0f;
        boolean isDrifting = false;
        int swingAnimationTicks = 0;
        int driftJustStartedTicks = 0;
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
        if (Main.kartEngine == 3) return;

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

            SuspensionState st = states.computeIfAbsent(player.getUUID(), k -> {
                SuspensionState s = new SuspensionState();
                s.prevPlayerYaw = player.getYRot(); // 재진입 시 가짜 steerGradient 스파이크 방지
                return s;
            });
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
        boolean isModelRotateAllowed = Main.getAllowModelRotation(kart, player);

        float playerYaw = player.getYRot();

        float wrappedDegree = Math.abs(Mth.wrapDegrees(st.prevPlayerYaw - playerYaw));
        st.steerGradientBuffer.add(wrappedDegree);
        if (st.steerGradientBuffer.size() > 5) st.steerGradientBuffer.removeFirst();
        st.prevPlayerYaw = playerYaw;

        boolean wasPlayingSwingAnimation = st.swingAnimationTicks > 0;
        if (st.swingAnimationTicks > 0) st.swingAnimationTicks--;
        if (st.driftJustStartedTicks > 0) st.driftJustStartedTicks--;

        double steerGradientSum = 0.0;
        for (int i = 0; i < st.steerGradientBuffer.size(); i++) {
            steerGradientSum += st.steerGradientBuffer.get(i);
        }
        double filteredSteerGradient = steerGradientSum / st.steerGradientBuffer.size();

        boolean disableSwingAnimation = isBike == 1 && Config.INSTANCE.bikeSuspension >= 2;
        boolean isDriftingTemp = detectDriftState(kart);
        if (isDriftingTemp != st.isDrifting) {
            st.isDrifting = isDriftingTemp;

            if (st.isDrifting) {
                st.driftJustStartedTicks = DRIFT_START_TICKS;
            }
            else {
                if (filteredSteerGradient > 2 && !wasPlayingSwingAnimation && !disableSwingAnimation) {
                    st.swingAnimationTicks = SWING_ANIMATION_TICKS;
                }
            }
        }

        boolean isPlayingSwingAnimation = st.swingAnimationTicks > 0;

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
            if (Config.INSTANCE.bikeSuspension == 3) {
                clampedDriftAngle *= 5;
            }
        }

        if (isPlayingSwingAnimation || !isModelRotateAllowed) clampedDriftAngle = 0;

        SpringSimulator.step(st.spring, DT, FREQ, Q, -(clampedDriftAngle / 2f));
        double modelRollValue = Math.toDegrees(st.spring.x);

        if (isBike == 1) {
            if (Config.INSTANCE.bikeSuspension == 0) {
                modelRollValue = 0;
            }
            else if (Config.INSTANCE.bikeSuspension == 1) {
                // 4-Wheel: 바이크가 아닌 것처럼 처리 (카트와 동일)
            }
            else {
                modelRollValue *= -1;
            }
        }

        for (var i : passengers) {
            if (Main.hasCertainName(i, "formationk-modelsaddle")) {
                for (var j : i.getPassengers()) {
                    EntityRollManager.setRoll(j.getUUID(), (float) modelRollValue, 1);
                }
            }
        }
        EntityRollManager.setRoll(player.getUUID(), (float) modelRollValue, 1);
    }

    static boolean detectDriftState(Entity kart) {
        if (kart instanceof Player) return false;

        List<Entity> passengers = kart.getPassengers();
        for (var i : passengers) {
            if (!Main.hasCertainName(i, "formationk-modelsaddle")) continue;

            for (var j : i.getPassengers()) {
                if (!Main.hasCertainName(j, "formationk-drift-effect") || !isDisplayEntity(j)) continue;

                return ((Display) j).getViewRange() > 0;
            }
        }
        return false;
    }
    static boolean isDisplayEntity(Entity entity) {
        return entity instanceof Display.ItemDisplay
                || entity instanceof Display.BlockDisplay
                || entity instanceof Display.TextDisplay;
    }
    public static void clearStates() {
        states.clear();
    }
}
