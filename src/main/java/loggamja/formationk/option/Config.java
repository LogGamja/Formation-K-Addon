package loggamja.formationk.option;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class Config {
    private static final Logger LOGGER = LoggerFactory.getLogger("formationk");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Config.class, (InstanceCreator<Config>) type -> new Config())
            .create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "formationk-config.json");

    public int RotationOption = 1;
    public boolean PacketAcceleration = true;
    public int RadarOption = 1;
    public int useMinimap = 0;
    public boolean useNoclipCamera = true;
    public boolean useOvertakeGauge = true;
    public boolean useAutoThirdPerson = true;
    public int cameraMode = 2;
    public int spectatorCameraMode = 0;

    public int suspensionEffect = 0;
    public int bikeSuspension = 0;
    public boolean playerBodyLock = true;
    public boolean playerHeadLock = false;
    public boolean smoothEngineSound = true;
    public boolean teleportBugFix = true;

    public int FOV = 90;
    public int FOVEffects = 80;

    @SerializedName("serverside.autoSaveDelayEnabled")
    public boolean autoSaveDelayEnabled = true;

    // 싱글톤 (load()가 역직렬화된 인스턴스로 통째로 교체하므로 final이 아님)
    public static Config INSTANCE = new Config();

    public void load() {
        if (!CONFIG_FILE.exists()) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE.toPath(), StandardCharsets.UTF_8)) {
            Config loaded = GSON.fromJson(reader, Config.class);
            if (loaded != null) {
                INSTANCE = loaded;
                OptionTable.clampAllToggles();
                OptionTable.clampAllSliders();
            }
        } catch (IOException e) {
            LOGGER.error("[FormationK] 설정 파일을 불러오는 데 실패했습니다.", e);
        } catch (com.google.gson.JsonParseException e) {
            LOGGER.error("[FormationK] 설정 파일 형식이 올바르지 않아 기본값으로 대체합니다.", e);
            INSTANCE = new Config();
        }
    }

    public void save() {
        try (Writer writer = Files.newBufferedWriter(CONFIG_FILE.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            LOGGER.error("[FormationK] 설정 파일을 저장하는 데 실패했습니다.", e);
        }
    }
}
