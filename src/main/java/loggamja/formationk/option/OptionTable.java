package loggamja.formationk.option;

import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

// 토글/슬라이더 옵션의 번역 키 + config 필드 getter/setter 정의 테이블
// SettingScreen(화면)과 Config(로드 시 범위 클램프), api.API(외부 모드 연동) 모두 이 테이블 하나만 보고 동작하므로
// 옵션을 추가 / 변경할 때 이 파일 한 곳만 수정하면 된다
public final class OptionTable {
    private OptionTable() {}

    public enum Category {
        BASIC("formationk.setting.category.basic"),
        VISUAL("formationk.setting.category.visual"),
        UI("formationk.setting.category.ui"),
        CAMERA("formationk.setting.category.camera");

        private final String labelKey;
        Category(String labelKey) { this.labelKey = labelKey; }
        public String labelKey() { return labelKey; }
    }

    public record ToggleDef(String id, Category category, String[] labelKeys, String tooltipKey, IntSupplier getter, IntConsumer setter) {
        public int stateCount() { return labelKeys.length; }
    }

    public record SliderDef(String id, Category category, String labelKey, String tooltipKey, double min, double max,
                      DoubleSupplier getter, Consumer<Float> setter) {}

    public static final ToggleDef[] TOGGLES = {
            new ToggleDef(
                    "steer_boost", Category.BASIC,
                    new String[]{"formationk.option.steer_boost.off", "formationk.option.steer_boost.extreme"},
                    "formationk.tooltip.steer_boost",
                    () -> Config.INSTANCE.RotationOption,
                    v -> Config.INSTANCE.RotationOption = v
            ),
            new ToggleDef(
                    "packet_boost", Category.BASIC,
                    new String[]{"formationk.option.packet_boost.off", "formationk.option.packet_boost.on"},
                    "formationk.tooltip.packet_boost",
                    () -> Config.INSTANCE.PacketAcceleration ? 1 : 0,
                    v -> Config.INSTANCE.PacketAcceleration = v != 0
            ),
            new ToggleDef(
                    "enemy_radar", Category.UI,
                    new String[]{"formationk.option.enemy_radar.off", "formationk.option.enemy_radar.on"},
                    "formationk.tooltip.enemy_radar",
                    () -> Config.INSTANCE.RadarOption,
                    v -> Config.INSTANCE.RadarOption = v
            ),
            new ToggleDef(
                    "overtake_gauge", Category.UI,
                    new String[]{"formationk.option.overtake_gauge.off", "formationk.option.overtake_gauge.on"},
                    "formationk.tooltip.overtake_gauge",
                    () -> Config.INSTANCE.useOvertakeGauge ? 1 : 0,
                    v -> Config.INSTANCE.useOvertakeGauge = v != 0
            ),
            new ToggleDef(
                    "auto_third_person", Category.CAMERA,
                    new String[]{"formationk.option.auto_third_person.off", "formationk.option.auto_third_person.on"},
                    "formationk.tooltip.auto_third_person",
                    () -> Config.INSTANCE.useAutoThirdPerson ? 1 : 0,
                    v -> Config.INSTANCE.useAutoThirdPerson = v != 0
            ),
            new ToggleDef(
                    "noclip_camera", Category.CAMERA,
                    new String[]{"formationk.option.noclip_camera.off", "formationk.option.noclip_camera.on"},
                    "formationk.tooltip.noclip_camera",
                    () -> Config.INSTANCE.useNoclipCamera ? 1 : 0,
                    v -> Config.INSTANCE.useNoclipCamera = v != 0
            ),
            new ToggleDef(
                    "camera_mode", Category.CAMERA,
                    new String[]{"formationk.option.camera_mode.default", "formationk.option.camera_mode.balance", "formationk.option.camera_mode.normal", "formationk.option.camera_mode.emphasis"},
                    "formationk.tooltip.camera_mode",
                    () -> Config.INSTANCE.cameraMode,
                    v -> Config.INSTANCE.cameraMode = v
            ),
            new ToggleDef(
                    "spectator_camera", Category.CAMERA,
                    new String[]{"formationk.option.spectator_camera.default", "formationk.option.spectator_camera.far"},
                    "formationk.tooltip.spectator_camera",
                    () -> Config.INSTANCE.spectatorCameraMode,
                    v -> Config.INSTANCE.spectatorCameraMode = v
            ),
            new ToggleDef(
                    "suspension_effect", Category.VISUAL,
                    new String[]{"formationk.option.suspension_effect.off", "formationk.option.suspension_effect.kart", "formationk.option.suspension_effect.kart_and_camera"},
                    "formationk.tooltip.suspension_effect",
                    () -> Config.INSTANCE.suspensionEffect,
                    v -> Config.INSTANCE.suspensionEffect = v
            ),
            new ToggleDef(
                    "bike_suspension", Category.VISUAL,
                    new String[]{"formationk.option.bike_suspension.default", "formationk.option.bike_suspension.four_wheel", "formationk.option.bike_suspension.extreme"},
                    "formationk.tooltip.bike_suspension",
                    () -> Config.INSTANCE.bikeSuspension,
                    v -> Config.INSTANCE.bikeSuspension = v
            ),
            new ToggleDef(
                    "player_body_lock", Category.VISUAL,
                    new String[]{"formationk.option.player_body_lock.off", "formationk.option.player_body_lock.on"},
                    "formationk.tooltip.player_body_lock",
                    () -> Config.INSTANCE.playerBodyLock ? 1 : 0,
                    v -> Config.INSTANCE.playerBodyLock = v != 0
            ),
            new ToggleDef(
                    "player_head_lock", Category.VISUAL,
                    new String[]{"formationk.option.player_head_lock.off", "formationk.option.player_head_lock.on"},
                    "formationk.tooltip.player_head_lock",
                    () -> Config.INSTANCE.playerHeadLock ? 1 : 0,
                    v -> Config.INSTANCE.playerHeadLock = v != 0
            ),
            new ToggleDef(
                    "smooth_engine_sound", Category.BASIC,
                    new String[]{"formationk.option.smooth_engine_sound.off", "formationk.option.smooth_engine_sound.on"},
                    "formationk.tooltip.smooth_engine_sound",
                    () -> Config.INSTANCE.smoothEngineSound ? 1 : 0,
                    v -> Config.INSTANCE.smoothEngineSound = v != 0
            ),
            new ToggleDef(
                    "auto_save_delay", Category.BASIC,
                    new String[]{"formationk.option.auto_save_delay.off", "formationk.option.auto_save_delay.on"},
                    "formationk.tooltip.auto_save_delay",
                    () -> Config.INSTANCE.autoSaveDelayEnabled ? 1 : 0,
                    v -> Config.INSTANCE.autoSaveDelayEnabled = v != 0
            ),
            new ToggleDef(
                    "track_minimap", Category.UI,
                    new String[]{
                            "formationk.option.track_minimap.off",
                            "formationk.option.track_minimap.bottom_left",
                            "formationk.option.track_minimap.bottom_right",
                            "formationk.option.track_minimap.left_middle",
                            "formationk.option.track_minimap.right_middle",
                            "formationk.option.track_minimap.top_left",
                            "formationk.option.track_minimap.top_right",
                            "formationk.option.track_minimap.debug"
                    },
                    "formationk.tooltip.track_minimap",
                    () -> Config.INSTANCE.useMinimap,
                    v -> Config.INSTANCE.useMinimap = v
            ),
    };

    public static final SliderDef[] SLIDERS = {
            new SliderDef(
                    "riding_fov", Category.CAMERA,
                    "formationk.slider.riding_fov", "formationk.slider.riding_fov.tooltip", 30.0, 110.0,
                    () -> Config.INSTANCE.FOV,
                    v -> Config.INSTANCE.FOV = Math.round(v)
            ),
            new SliderDef(
                    "riding_fov_effects", Category.CAMERA,
                    "formationk.slider.riding_fov_effects", "formationk.slider.riding_fov_effects.tooltip", 0.0, 100.0,
                    () -> Config.INSTANCE.FOVEffects,
                    v -> Config.INSTANCE.FOVEffects = Math.round(v)
            ),
    };

    public static ToggleDef[] togglesIn(Category category) {
        return java.util.Arrays.stream(TOGGLES).filter(t -> t.category() == category).toArray(ToggleDef[]::new);
    }

    public static SliderDef[] slidersIn(Category category) {
        return java.util.Arrays.stream(SLIDERS).filter(s -> s.category() == category).toArray(SliderDef[]::new);
    }

    public static ToggleDef findToggle(String id) {
        for (ToggleDef def : TOGGLES) {
            if (def.id().equals(id)) return def;
        }
        return null;
    }

    public static SliderDef findSlider(String id) {
        for (SliderDef def : SLIDERS) {
            if (def.id().equals(id)) return def;
        }
        return null;
    }

    static void clampAllToggles() {
        for (ToggleDef def : TOGGLES) {
            int current = def.getter().getAsInt();
            int clamped = Math.max(0, Math.min(current, def.stateCount() - 1));
            if (clamped != current) def.setter().accept(clamped);
        }
    }
    static void clampAllSliders() {
        for (SliderDef def : SLIDERS) {
            double current = def.getter().getAsDouble();
            double clamped = Math.max(def.min(), Math.min(current, def.max()));
            if (clamped != current) def.setter().accept((float) clamped);
        }
    }
}
