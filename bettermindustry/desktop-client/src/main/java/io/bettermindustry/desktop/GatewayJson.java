package io.bettermindustry.desktop;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GatewayJson {
    private GatewayJson() {
    }

    static String string(String json, String key) {
        Matcher matcher = Pattern.compile(
            "\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
        ).matcher(json);
        return matcher.find() ? unescape(matcher.group(1)) : null;
    }

    static double number(String json, String key) {
        Matcher matcher = Pattern.compile(
            "\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?(?:[eE][-+]?[0-9]+)?)"
        ).matcher(json);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : 0;
    }

    static long longNumber(String json, String key) {
        return (long)number(json, key);
    }

    static boolean bool(String json, String key) {
        Matcher matcher = Pattern.compile(
            "\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)"
        ).matcher(json);
        return matcher.find() && Boolean.parseBoolean(matcher.group(1));
    }

    static Boolean nullableBool(String json, String key) {
        Matcher matcher = Pattern.compile(
            "\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false|null)"
        ).matcher(json);
        if (!matcher.find() || "null".equals(matcher.group(1))) return null;
        return Boolean.parseBoolean(matcher.group(1));
    }

    static List<String> objectArray(String json, String key) {
        String marker = "\"" + key + "\"";
        int keyStart = json.indexOf(marker);
        if (keyStart < 0) return List.of();
        int arrayStart = json.indexOf('[', keyStart + marker.length());
        if (arrayStart < 0) return List.of();
        int arrayEnd = matching(json, arrayStart, '[', ']');
        if (arrayEnd < 0) return List.of();

        List<String> result = new ArrayList<>();
        int index = arrayStart + 1;
        while (index < arrayEnd) {
            char c = json.charAt(index);
            if (Character.isWhitespace(c) || c == ',') {
                index++;
                continue;
            }
            if (c != '{') {
                index++;
                continue;
            }
            int objectEnd = matching(json, index, '{', '}');
            if (objectEnd < 0) break;
            result.add(json.substring(index, objectEnd + 1));
            index = objectEnd + 1;
        }
        return result;
    }

    private static int matching(String json, int start, char open, char close) {
        int depth = 0;
        boolean escaped = false;
        boolean inString = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;
            if (c == open) depth++;
            else if (c == close && --depth == 0) return i;
        }
        return -1;
    }

    private static String unescape(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!escaped) {
                if (c == '\\') escaped = true;
                else result.append(c);
            } else {
                result.append(switch (c) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case '"' -> '"';
                    case '\\' -> '\\';
                    case 'u' -> {
                        if (i + 4 >= value.length()) yield 'u';
                        String hex = value.substring(i + 1, i + 5);
                        i += 4;
                        yield (char)Integer.parseInt(hex, 16);
                    }
                    default -> c;
                });
                escaped = false;
            }
        }
        if (escaped) result.append('\\');
        return result.toString();
    }
}
