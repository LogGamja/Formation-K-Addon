package loggamja.formationk.helper;

import com.mojang.logging.LogUtils;
import loggamja.formationk.mixin.SoundEngineAccessor;
import loggamja.formationk.mixin.SoundManagerAccessor;
import net.minecraft.client.Minecraft;
import org.lwjgl.openal.ALC10;
import org.slf4j.Logger;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class SoundQuantizer {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final long GRID_NS = 20_000_000L;
    // 경계 직전까지는 parkNanos 로 쉬고, 마지막 구간은 짧게 돌면서 정확히 맞춘다
    private static final long SPIN_NS = 1_500_000L;
    private static final int ALC_REFRESH = 0x1008;

    private static final ScheduledThreadPoolExecutor TIMER = createTimer();
    // 우리가 다시 넣은 재생 호출은 지연하지 않는다 (소리 스레드에서만 쓰인다)
    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);
    private static boolean refreshLogged = false;

    private SoundQuantizer() {}

    private static ScheduledThreadPoolExecutor createTimer() {
        ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "formationk-sound-quantizer");
            thread.setDaemon(true);
            return thread;
        });
        timer.setRemoveOnCancelPolicy(true);
        return timer;
    }

    public static boolean isBypassing() {
        return BYPASS.get();
    }

    /** 소리 스레드에서 호출한다. 격자 경계에 맞춰 action 을 소리 스레드에서 실행하도록 예약하고 true 를 반환한다. */
    public static boolean schedule(Runnable action) {
        Executor soundExecutor = getSoundExecutor();
        if (soundExecutor == null) return false;

        logRefreshOnce();

        long now = System.nanoTime();
        long target = Math.floorDiv(now + GRID_NS - 1, GRID_NS) * GRID_NS;
        long sleepNs = Math.max(0L, target - now - SPIN_NS);

        try {
            TIMER.schedule(() -> {
                while (System.nanoTime() - target < 0) {
                    Thread.onSpinWait();
                }
                soundExecutor.execute(() -> {
                    BYPASS.set(true);
                    try {
                        action.run();
                    } finally {
                        BYPASS.set(false);
                    }
                });
            }, sleepNs, TimeUnit.NANOSECONDS);
        } catch (RejectedExecutionException e) {
            // 종료 중이라 타이머가 닫혔다. 지연 없이 바로 재생하게 한다
            return false;
        }
        return true;
    }

    public static void shutdown() {
        TIMER.shutdownNow();
    }

    private static Executor getSoundExecutor() {
        var soundManager = Minecraft.getInstance().getSoundManager();
        var engine = ((SoundManagerAccessor) soundManager).formationk$getSoundEngine();
        return engine == null ? null : ((SoundEngineAccessor) engine).formationk$getExecutor();
    }

    // OpenAL 이 실제로 쓰는 믹싱 주기를 한 번 로그로 남긴다 (격자가 이 값의 정수배인지 확인용)
    private static void logRefreshOnce() {
        if (refreshLogged) return;
        refreshLogged = true;

        long context = ALC10.alcGetCurrentContext();
        long device = context == 0L ? 0L : ALC10.alcGetContextsDevice(context);
        if (device == 0L) return;

        int refreshHz = ALC10.alcGetInteger(device, ALC_REFRESH);
        if (refreshHz > 0) {
            LOGGER.info("[FormationK] OpenAL mixing refresh: {} Hz (~{} ms)", refreshHz, String.format("%.2f", 1000.0 / refreshHz));
        }
    }
}
