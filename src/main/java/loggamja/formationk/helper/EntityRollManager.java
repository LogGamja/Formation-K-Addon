package loggamja.formationk.helper;

import loggamja.formationk.Main;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EntityRollManager {
    private static final Map<UUID, RollState> rollStates = new HashMap<>();

    public static void setRoll(UUID entityId, float targetRollDeg, int durationTicks) {
        float current = getCurrentRoll(entityId);
        rollStates.put(entityId, new RollState(
                current,
                targetRollDeg,
                System.nanoTime() / 1_000_000L,
                Math.round(durationTicks * Main.getSecondsPerTick() * 1000f)
        ));
    }

    public static float getCurrentRoll(UUID entityId) {
        RollState state = rollStates.get(entityId);
        if (state == null) return 0f;

        long now = System.nanoTime() / 1_000_000L;
        long elapsed = now - state.startTime;

        if (elapsed >= state.duration) {
            return state.targetRoll;
        }

        float t = Mth.clamp((float) elapsed / state.duration, 0f, 1f);
        // smoothstep interpolation
        t = t * t * (3f - 2f * t);
        return state.startRoll + (state.targetRoll - state.startRoll) * t;
    }

    public static void remove(UUID entityId) {
        rollStates.remove(entityId);
    }
    public static void clear() {
        rollStates.clear();
    }

    public static boolean isEmpty() {
        return rollStates.isEmpty();
    }

    public record RollState(
            float startRoll,
            float targetRoll,
            long startTime,
            long duration
    ) {}
}