package loggamja.formationk;

import loggamja.formationk.minimap.Minimap;
import loggamja.formationk.option.Config;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import loggamja.formationk.helper.EntityRollManager;
import loggamja.formationk.helper.SoundQuantizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.*;

public class Main implements ClientModInitializer {

    public static int kartEngine = 0;
    public static String currentSaddleType = "none";

    public static boolean isRidingKart = false;

    // 프레임 내부 보간용
    private static float prevTickDirectionYaw = 0f;
    private static float tickDirectionYaw = 0f;
    private static boolean hasDirectionYawSamples = false;

    static Minecraft client = Minecraft.getInstance();

    @Override
    public void onInitializeClient() {
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) ->
            EntityRollManager.remove(entity.getUUID()));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            onClientTickEnd();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            EntityRollManager.clear();
            Suspension.clearStates();
            SoundQuantizer.shutdown();
        });

        // 무조건 렌더 스레드로 예약
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            client.execute(() -> {
                isRidingKart = false;
                hasDirectionYawSamples = false;
            });
        });
    }
    //Minecraft mc = Minecraft.getInstance();
    //mc.getWindow().setWindowedSize(1024, 768);

    void onClientTickEnd() {
        if (!isPlayingInGame()) return;

        Entity vehicle = getRidingPlayer().getVehicle();
        currentSaddleType = getSaddleType(vehicle);
        kartEngine = Main.getS2CValue(Main.getRidingPlayer(), "data-engine-real");

        updateRidingState();

        boolean isSampleSucceeded = false;
        if (Config.INSTANCE.RotationOption > 0) {
            if (isRidingKart && getRidingPlayer() == client.player) {
                Entity kartMobil = getRidingPlayer().getRootVehicle();

                if (getAllowModelRotation(kartMobil)) {
                    sampleKartRotation(kartMobil);
                    isSampleSucceeded = true;
                }
            }
        }
        if (!isSampleSucceeded) hasDirectionYawSamples = false;
    }
    public static boolean isRidingKart(Player player) {
        var kartSaddle = player.getVehicle();
        if (kartSaddle == null) return false;

        return !getSaddleType(kartSaddle).equals("none");
    }
    public static boolean getAllowModelRotation(Entity kartMobil) {
        return getAllowModelRotation(kartMobil, getRidingPlayer());
    }
    public static boolean getAllowModelRotation(Entity kartMobil, Player player) {
        return getS2CValue(player, "state-allow-model-rotation") == 1;
    }
    public static boolean isPlayingInGame() {
        Minecraft client = Minecraft.getInstance();
        return client.level != null && client.player != null && client.getCameraEntity() != null && !client.isPaused();
    }
    public static Player getRidingPlayer() {
        Minecraft client = Minecraft.getInstance();
        var cameraEntity = client.getCameraEntity();

        if (cameraEntity != null && cameraEntity instanceof Player) {
            return (Player) cameraEntity;
        }
        return client.player;
    }

    public static boolean isSpectatingPlayer() {
        Minecraft client = Minecraft.getInstance();
        var cameraEntity = client.getCameraEntity();

        return cameraEntity != null && cameraEntity instanceof Player && cameraEntity != client.player;
    }

    public static float getKartBodyYaw(Player player, float tickDelta) {
        Entity kart = player.getRootVehicle();
        if (kart != null && kart != player) {
            for (Entity passenger : kart.getPassengers()) {
                if (hasCertainName(passenger, "formationk-modelsaddle")) {
                    return passenger.getViewYRot(tickDelta);
                }
            }
        }
        return player.getViewYRot(tickDelta);
    }
    static String getSaddleType(Entity saddle) {
        if (hasCertainName(saddle, "formationk-saddle-common")) {
            return "common";
        }
        else {
            return "none";
        }
    }
    public static boolean hasCertainName(Entity entity, String saddleName) {
        if (entity != null && entity.getCustomName() != null) {
            return entity.getCustomName().getString().equals(saddleName);
        }
        else {
            return false;
        }
    }
    void updateRidingState() {
        if (currentSaddleType.equals("none") == isRidingKart) {
            isRidingKart = !isRidingKart;

            Camera.lastPos = null;
            hasDirectionYawSamples = false;
            Minimap.clearAllMap();
            autoThirdPerson();
        }
    }
    void autoThirdPerson() {
        if (Config.INSTANCE.useAutoThirdPerson) {
            if (isRidingKart) {
                client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            else {
                client.options.setCameraType(CameraType.FIRST_PERSON);
            }
        }
    }
    void sampleKartRotation(Entity kartMobil) {
        var player = getRidingPlayer();
        if (kartMobil == player) return;

        List<Entity> passengers = kartMobil.getPassengers();
        for (var i: passengers) {
            if (hasCertainName(i, "formationk-direction")) {
                prevTickDirectionYaw = hasDirectionYawSamples ? tickDirectionYaw : i.getYRot();
                tickDirectionYaw = i.getYRot();
                hasDirectionYawSamples = true;
                break;
            }
        }
    }
    public static void onFrameRender(float partialTick) {
        if (!isPlayingInGame()) return;

        if (isRidingKart) {
            applyKartRotationThisFrame(partialTick);
        }
        // 상체 고정은 내가 카트를 타지 않았을 때도 다른 사람에게 적용된다
        if (Config.INSTANCE.playerBodyLock) {
            fixAllPlayersBodyToKart(partialTick);
        }
    }
    static void applyKartRotationThisFrame(float partialTick) {
        var player = getRidingPlayer();
        if (Config.INSTANCE.RotationOption > 0 && player == client.player) {
            Entity kartMobil = player.getRootVehicle();

            if (kartMobil != player && getAllowModelRotation(kartMobil)) {
                List<Entity> passengers = kartMobil.getPassengers();
                for (var i : passengers) {
                    if (!hasCertainName(i, "formationk-direction")) continue;

                    // directionYaw는 직전 틱 값과 보간해야 함
                    float directionYaw = hasDirectionYawSamples
                            ? Mth.rotLerp(partialTick, prevTickDirectionYaw, tickDirectionYaw)
                            : i.getYRot();

                    float rotation = calculateRotation(directionYaw);
                    for (var j : passengers) {
                        if (hasCertainName(j, "formationk-modelsaddle")) {
                            rotateKartModel(j, rotation);
                            break;
                        }
                    }
                    break;
                }
            }
        }
    }
    static void fixAllPlayersBodyToKart(float partialTick) {
        for (Player other : Objects.requireNonNull(client.level).players()) {
            if (!isRidingKart(other)) continue;

            fixPlayerBodyToKart(other, other.getRootVehicle(), partialTick);
        }
    }
    static void fixPlayerBodyToKart(Player player, Entity kartMobil, float partialTick) {
        if (kartMobil == player) return;

        List<Entity> passengers = kartMobil.getPassengers();
        for (var i: passengers) {
            if (hasCertainName(i, "formationk-direction")) {
                for (var j : passengers) {
                    if (hasCertainName(j, "formationk-modelsaddle")) {
                        player.setYBodyRot(j.getYRot(partialTick));
                        break;
                    }
                }
                break;
            }
        }
    }
    static float calculateRotation(float directionYaw) {
        float playerYaw = getRidingPlayer().getYRot();

        if (kartEngine == 1002) {
            int driftState = Main.getS2CValue(Main.getRidingPlayer(), "state-drift");
            if (driftState == 1) {
                return Mth.wrapDegrees(playerYaw);
            }
            else {
                return Mth.wrapDegrees(directionYaw);
            }
        }
        else if (kartEngine == 1003) {
            return Mth.wrapDegrees(playerYaw);
        }

        var deltaAngle = Mth.wrapDegrees(playerYaw - directionYaw);
        var overShootAngle = getOverShootAngle(deltaAngle);

        return Mth.wrapDegrees(playerYaw + overShootAngle);
    }
    static void rotateKartModel(Entity entity, float angle) {
        List<Entity> models = entity.getPassengers();

        entity.setYRot(angle);
        entity.yRotO = angle;

        for (var j: models) {
            j.setYRot(angle);
            j.yRotO = angle;
        }
    }
    static float getOverShootAngle(float deltaAngle) {
        var overShootAngle = deltaAngle;

        // ALPHA유닛은 상한 클램프 구현하지 않음
        if (kartEngine == 3) {
            if (deltaAngle <= -55) overShootAngle = -110f - deltaAngle;
            if (55 <= deltaAngle) overShootAngle = 110f - deltaAngle;
        }
        else {
            if (-110 <= deltaAngle && deltaAngle <= -55) overShootAngle = -110f - deltaAngle;
            if (55 <= deltaAngle && deltaAngle <= 110) overShootAngle = 110f - deltaAngle;
            if ((deltaAngle < -110 || deltaAngle > 110)) overShootAngle = 0f;
        }
        return overShootAngle / 2;
    }

    private static final Map<String, Identifier> s2cIdCache = new HashMap<>();
    public static int getS2CValue(Player player, String name) {
        Identifier id = s2cIdCache.computeIfAbsent(name, n -> Identifier.fromNamespaceAndPath("formationk", n));

        Entity saddle = player.getVehicle();
        if (saddle == null || !(saddle instanceof LivingEntity)) return 0;

        AttributeInstance inst = ((LivingEntity)saddle).getAttribute(Attributes.ARMOR);
        if (inst == null) return 0;

        AttributeModifier mod = inst.getModifier(id);
        return (mod == null) ? 0 : (int)mod.amount();
    }
}