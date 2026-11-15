package loggamja.formationk.helper;

import net.minecraft.world.entity.Display;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

// 데이터팩이 디스플레이에 붙이는 바퀴 이름 해석
// formationk-wheel-steer-[축]-[축]-번호, formationk-wheel-[축]-번호, formationk-steer-[축]-번호
// 피봇 엔티티: formationk-wheel-steer-pivot-번호, formationk-wheel-pivot-번호, formationk-steer-pivot-번호
// 피봇 엔티티는 canRoll / canSteer 가 어느 피봇인지를 뜻하고 축이 없다

public class WheelNameParser {
    public record WheelName(boolean canRoll, boolean canSteer, boolean isPivot, Vector3f wheelAxis, Vector3f steerAxis, int wheelId) {}

    private static final String WHEEL_NAME = "formationk-wheel-";
    private static final String STEER_NAME = "formationk-steer-";
    private static final String WHEEL_STEER_NAME = "formationk-wheel-steer-";

    private static final String WHEEL_PIVOT_NAME = "formationk-wheel-pivot-";
    private static final String STEER_PIVOT_NAME = "formationk-steer-pivot-";
    private static final String WHEEL_STEER_PIVOT_NAME = "formationk-wheel-steer-pivot-";

    private static final Pattern AXIS_PATTERN = Pattern.compile("\\[([^\\]]*)\\]");

    private static final Map<String, WheelName> cache = new HashMap<>();

    // 바퀴 이름이 아니면 null
    public static WheelName of(Display entity) {
        return entity.hasCustomName() ? of(entity.getCustomName().getString()) : null;
    }

    public static WheelName of(String name) {
        return cache.computeIfAbsent(name, WheelNameParser::parse);
    }

    private static WheelName parse(String name) {
        boolean canRoll;
        boolean canSteer;
        boolean isPivot;
        String rest;
        // 피봇 이름이 기존 이름의 접두사와 겹치므로 먼저 검사
        if (name.startsWith(WHEEL_STEER_PIVOT_NAME)) {
            canRoll = true;
            canSteer = true;
            isPivot = true;
            rest = name.substring(WHEEL_STEER_PIVOT_NAME.length());
        }
        else if (name.startsWith(WHEEL_PIVOT_NAME)) {
            canRoll = true;
            canSteer = false;
            isPivot = true;
            rest = name.substring(WHEEL_PIVOT_NAME.length());
        }
        else if (name.startsWith(STEER_PIVOT_NAME)) {
            canRoll = false;
            canSteer = true;
            isPivot = true;
            rest = name.substring(STEER_PIVOT_NAME.length());
        }
        else if (name.startsWith(WHEEL_STEER_NAME)) {
            canRoll = true;
            canSteer = true;
            isPivot = false;
            rest = name.substring(WHEEL_STEER_NAME.length());
        }
        else if (name.startsWith(WHEEL_NAME)) {
            canRoll = true;
            canSteer = false;
            isPivot = false;
            rest = name.substring(WHEEL_NAME.length());
        }
        else if (name.startsWith(STEER_NAME)) {
            canRoll = false;
            canSteer = true;
            isPivot = false;
            rest = name.substring(STEER_NAME.length());
        }
        else return null;

        var axes = new ArrayList<Vector3f>();
        var matcher = AXIS_PATTERN.matcher(rest);
        while (matcher.find()) {
            var axis = parseAxis(matcher.group(1));
            if (axis == null) return null;
            axes.add(axis);
        }

        int axisCount = isPivot ? 0 : (canRoll ? 1 : 0) + (canSteer ? 1 : 0);
        if (axes.size() != axisCount) return null;

        var wheelAxis = !isPivot && canRoll ? axes.get(0) : null;
        var steerAxis = !isPivot && canSteer ? axes.get(axes.size() - 1) : null;

        try {
            return new WheelName(canRoll, canSteer, isPivot, wheelAxis, steerAxis, Integer.parseInt(rest.substring(rest.lastIndexOf('-') + 1)));
        }
        catch (NumberFormatException e) {
            return null;
        }
    }

    // "0, -1.0f, 0d" 같은 숫자 세 개를 길이 1 인 축으로
    private static Vector3f parseAxis(String text) {
        String[] tokens = text.split(",");
        if (tokens.length != 3) return null;

        var axis = new Vector3f();
        try {
            for (int i = 0; i < 3; i++) axis.setComponent(i, Float.parseFloat(tokens[i].trim().replaceAll("[bBsSlLfFdD]$", "")));
        }
        catch (NumberFormatException e) {
            return null;
        }

        return axis.lengthSquared() > 0f && axis.normalize().isFinite() ? axis : null;
    }
}
