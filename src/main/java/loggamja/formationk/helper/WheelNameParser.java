package loggamja.formationk.helper;

import net.minecraft.world.entity.Display;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 데이터팩이 디스플레이에 붙이는 바퀴 이름 해석
// formationk-wheel[축]-steer[축]-ratio(배율)-nocounter(0 또는 1)-id(번호)
// wheel 이나 steer 는 하나만 써도 된다. ratio 는 wheel 이 있을 때, nocounter 는 steer 가 있을 때만 필요하다
// 피봇은 formationk-wheel-steer-pivot-id(번호) 처럼 축 없이 pivot 을 붙인다

public class WheelNameParser {
    public record WheelName(boolean canRoll, boolean canSteer, boolean isPivot, Vector3f wheelAxis, Vector3f steerAxis, boolean noCounter, float rollRatio, int wheelId) {}

    private static final String PREFIX = "formationk";

    // 바퀴가 아닌 이름도 null 로 담아 다시 해석하지 않는다
    private static final Map<String, WheelName> cache = new HashMap<>();

    // 바퀴 이름이 아니면 null
    public static WheelName of(Display entity) {
        return entity.hasCustomName() ? of(entity.getCustomName().getString()) : null;
    }

    public static WheelName of(String name) {
        if (!name.startsWith(PREFIX)) return null;

        if (!cache.containsKey(name)) cache.put(name, parse(name));
        return cache.get(name);
    }

    private static WheelName parse(String name) {
        boolean canRoll = false;
        boolean canSteer = false;
        boolean isPivot = false;
        Boolean noCounter = null;
        Vector3f wheelAxis = null;
        Vector3f steerAxis = null;
        float rollRatio = Float.NaN;
        Integer wheelId = null;

        var parts = splitParts(name);
        if (parts == null || !parts.getFirst().equals(PREFIX)) return null;

        // 조각은 이름 또는 이름[값]. [] 와 () 는 구분하지 않는다
        for (String part : parts.subList(1, parts.size())) {
            int valueStart = indexOfOpen(part);
            if (valueStart >= 0 && !isClose(part.charAt(part.length() - 1))) return null;

            String key = valueStart < 0 ? part : part.substring(0, valueStart);
            String value = valueStart < 0 ? null : part.substring(valueStart + 1, part.length() - 1);

            switch (key) {
                case "wheel" -> {
                    canRoll = true;
                    if (value != null && (wheelAxis = parseAxis(value)) == null) return null;
                }
                case "steer" -> {
                    canSteer = true;
                    if (value != null && (steerAxis = parseAxis(value)) == null) return null;
                }
                case "pivot" -> {
                    if (value != null) return null;

                    isPivot = true;
                }
                case "nocounter" -> {
                    if (value == null || !value.equals("0") && !value.equals("1")) return null;

                    noCounter = value.equals("1");
                }
                case "ratio" -> {
                    float ratio = value == null ? Float.NaN : parseNumber(value);
                    if (!Float.isFinite(ratio) || ratio < 0f) return null;
                    rollRatio = ratio;
                }
                case "id" -> {
                    try {
                        wheelId = Integer.parseInt(value == null ? "" : value);
                    }
                    catch (NumberFormatException e) {
                        return null;
                    }
                }
                default -> {
                    return null;
                }
            }
        }

        if (wheelId == null || !canRoll && !canSteer) return null;

        // 피봇은 축과 옵션이 없고, 파츠는 구르거나 조향하는 쪽의 축이 꼭 있어야 한다
        if (isPivot) {
            if (wheelAxis != null || steerAxis != null || !Float.isNaN(rollRatio) || noCounter != null) return null;
        }
        else {
            if (canRoll == (wheelAxis == null) || canSteer == (steerAxis == null)) return null;
            if (canRoll == Float.isNaN(rollRatio) || canSteer == (noCounter == null)) return null;
        }

        return new WheelName(canRoll, canSteer, isPivot, wheelAxis, steerAxis, noCounter != null && noCounter, Float.isNaN(rollRatio) ? 1f : rollRatio, wheelId);
    }

    // [] () 안의 -(음수 부호) 는 나누지 않고, 괄호가 안 닫히면 null
    private static List<String> splitParts(String name) {
        var parts = new ArrayList<String>();
        int depth = 0;
        int start = 0;

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (isOpen(c)) depth++;
            else if (isClose(c)) depth--;
            else if (c == '-' && depth == 0) {
                parts.add(name.substring(start, i));
                start = i + 1;
            }

            if (depth < 0) return null;
        }
        if (depth != 0) return null;

        parts.add(name.substring(start));
        return parts;
    }

    private static boolean isOpen(char c) {
        return c == '[' || c == '(';
    }

    private static boolean isClose(char c) {
        return c == ']' || c == ')';
    }

    // 값이 있는 조각이면 여는 괄호 위치, 없으면 -1
    private static int indexOfOpen(String part) {
        for (int i = 0; i < part.length(); i++) {
            if (isOpen(part.charAt(i))) return i;
        }

        return -1;
    }

    // "0, -1.0f, 0d" 같은 숫자 세 개를 길이 1 인 축으로
    private static Vector3f parseAxis(String text) {
        String[] tokens = text.split(",");
        if (tokens.length != 3) return null;

        var axis = new Vector3f();
        for (int i = 0; i < 3; i++) {
            float number = parseNumber(tokens[i]);
            if (!Float.isFinite(number)) return null;

            axis.setComponent(i, number);
        }

        return axis.lengthSquared() > 0f && axis.normalize().isFinite() ? axis : null;
    }

    // "1.5f" 같은 NBT 숫자. 읽을 수 없으면 NaN
    private static float parseNumber(String text) {
        try {
            return Float.parseFloat(text.trim().replaceAll("[bBsSlLfFdD]$", ""));
        }
        catch (NumberFormatException e) {
            return Float.NaN;
        }
    }
}
