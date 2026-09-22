package com.railway.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StationAlias {

    public static final List<String> FULL_NAMES = List.of(
            "北京南", "北京西", "济南西", "南京南", "上海虹桥", "杭州东",
            "广州南", "深圳北", "虎门", "长沙南", "武汉", "郑州东");

    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        ALIASES.put("北京", "北京南");
        ALIASES.put("上海", "上海虹桥");
        ALIASES.put("广州", "广州南");
        ALIASES.put("深圳", "深圳北");
        ALIASES.put("杭州", "杭州东");
        ALIASES.put("南京", "南京南");
        ALIASES.put("长沙", "长沙南");
        ALIASES.put("郑州", "郑州东");
        ALIASES.put("济南", "济南西");
    }

    private StationAlias() {
    }

    public static String resolve(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (FULL_NAMES.contains(value)) {
            return value;
        }
        return ALIASES.getOrDefault(value, value);
    }

    public static List<String> extract(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<Match> matches = new ArrayList<>();
        for (String name : FULL_NAMES) {
            int index = text.indexOf(name);
            if (index >= 0) {
                matches.add(new Match(name, index, name.length()));
            }
        }
        for (Map.Entry<String, String> alias : ALIASES.entrySet()) {
            int index = text.indexOf(alias.getKey());
            if (index >= 0) {
                matches.add(new Match(alias.getValue(), index, alias.getKey().length()));
            }
        }
        matches.sort((left, right) -> left.index != right.index
                ? Integer.compare(left.index, right.index)
                : Integer.compare(right.length, left.length));
        List<String> result = new ArrayList<>();
        int lastEnd = -1;
        for (Match match : matches) {
            if (match.index < lastEnd) {
                continue;
            }
            if (!result.contains(match.canonical)) {
                result.add(match.canonical);
            }
            lastEnd = match.index + match.length;
        }
        return result;
    }

    private static class Match {

        private final String canonical;
        private final int index;
        private final int length;

        Match(String canonical, int index, int length) {
            this.canonical = canonical;
            this.index = index;
            this.length = length;
        }
    }
}
