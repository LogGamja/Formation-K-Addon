package loggamja.formationk.minimap;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import loggamja.formationk.option.Config;
import loggamja.formationk.Main;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;

// 플로드필 기반 트랙 미니맵 (색깔 관계 그래프 방식)
// 규칙은 ColorGraph.java 참고
public class Minimap implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("formationk");

    public static boolean isDebugColors() {
        return Config.INSTANCE.useMinimap == 7;
    }
    public static final boolean EXCLUDE_NARROW_PATHS = true;

    static Minecraft client;

    @Override
    public void onInitializeClient() {
        client = Minecraft.getInstance();

        ClientTickEvents.START_CLIENT_TICK.register(client -> onTickStart());

        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("formationk-official", "minimap_hud"),
                (context, tickCounter) -> MinimapRenderer.renderMinimap(context, tickCounter.getGameTimeDeltaPartialTick(false)));

        // 무조건 렌더 스레드로 예약
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            client.execute(Minimap::clearAllMap);
        });

        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) ->
                BlockQuery.invalidateChunkCacheAt(chunk.getPos().x(), chunk.getPos().z()));
        // CHUNK_NOT_LOADED로 보류된 셀 복구용
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            BlockQuery.invalidateChunkCacheAt(chunk.getPos().x(), chunk.getPos().z()); // 청크 교체(재전송) 시 인스턴스가 바뀔 수 있음
            FrontierSearch.notifyChunkLoaded();
        });
    }
    private static WeakReference<Level> lastWorld = new WeakReference<>(null);
    private static boolean lastDebugColors = false;

    private static void onTickStart() {
        if (Config.INSTANCE.useMinimap == 0) return;

        if (!Main.isRidingKart) return;
        if (!Main.isPlayingInGame()) return;

        if (client.level != lastWorld.get()) {
            clearAllMap();
        }
        lastWorld = new WeakReference<>(client.level);

        if (ColorGraph.actualColorCount >= 2500) {
            LOGGER.warn("[FormationK] Minimap color limit exceeded ({}), resetting map.", ColorGraph.actualColorCount);
            clearAllMap();
            lastWorld = new WeakReference<>(client.level);

            return;
        }

        boolean debugColors = isDebugColors();
        if (debugColors != lastDebugColors) {
            lastDebugColors = debugColors;
            FrontierSearch.markAllColumnsDirty();
        }

        final int playerMargin = 5;
        BlockPos start = Main.getRidingPlayer().blockPosition().above();

        int searchRange = (int) ((MinimapRenderer.MAX_DIST + playerMargin * 2) * 2);
        FrontierSearch.floodFillWithVertical(start, searchRange, FrontierSearch.STAGING_BUDGET_PER_TICK);

        // floodFill, rebuildActiveSet, ensureOriginFor, repaintDirtyColumns 순서를 지켜야 한다.
        // rebuildActiveSet은 colorGraphVersion을 캐시 키로 써서 그래프가 실제로 바뀐 경우만 재계산한다.
        FrontierSearch.rebuildActiveSet();

        MinimapRenderer.ensureOriginFor(start);
        MinimapRenderer.repaintDirtyColumns();
    }
    public static void clearAllMap() {
        lastWorld = new WeakReference<>(null);

        FrontierSearch.reset();
        ColorGraph.reset();
        MinimapRenderer.reset();
    }
}
