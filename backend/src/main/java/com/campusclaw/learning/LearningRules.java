package com.campusclaw.learning;

import com.campusclaw.common.BadRequestException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static com.campusclaw.learning.LearningRepository.number;

/** 规则v1：准确比较有限数值，掌握度只由可追溯成绩计算。 */
public final class LearningRules {
    public static final String VERSION = "recent-20-mean-v1";

    private LearningRules() { }

    public static String normalizeAnswer(String kind, String raw) {
        String value = raw == null ? "" : raw.trim();
        if ("CHOICE".equals(kind)) {
            value = value.toUpperCase(Locale.ROOT);
            if (!value.matches("[A-D]")) throw new BadRequestException("请选择 A、B、C 或 D");
        } else if ("NUMBER".equals(kind)) {
            fraction(value);
        } else throw new BadRequestException("题型应为单选题或数值题");
        return value;
    }

    public static boolean correct(String kind, String actual, String expected) {
        normalizeAnswer(kind, actual);
        normalizeAnswer(kind, expected);
        if ("CHOICE".equals(kind)) return actual.trim().equalsIgnoreCase(expected.trim());
        BigInteger[] a = fraction(actual.trim());
        BigInteger[] b = fraction(expected.trim());
        return a[0].multiply(b[1]).equals(b[0].multiply(a[1]));
    }

    private static BigInteger[] fraction(String value) {
        String[] pair = value.split("/", -1);
        if (pair.length > 2 || pair.length == 0) throw new BadRequestException("请输入数值或分数，例如 0.75 或 3/4");
        BigInteger[] top = decimal(pair[0]);
        if (pair.length == 1) return top;
        BigInteger[] bottom = decimal(pair[1]);
        if (bottom[0].signum() == 0) throw new BadRequestException("分母不能为零");
        return new BigInteger[] {top[0].multiply(bottom[1]), top[1].multiply(bottom[0])};
    }

    private static BigInteger[] decimal(String value) {
        if (!value.matches("[+-]?\\d{1,18}(?:\\.\\d{1,12})?")) {
            throw new BadRequestException("请输入不超过18位整数和12位小数的数值，可使用分数");
        }
        BigDecimal decimal = new BigDecimal(value);
        return new BigInteger[] {decimal.unscaledValue(), BigInteger.TEN.pow(decimal.scale())};
    }

    public static List<Map<String, Object>> profile(List<Map<String, Object>> points, List<Map<String, Object>> evidence) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (var point : points) {
            long id = number(point, "id");
            var source = evidence.stream().filter(e -> number(e, "pointId") == id).toList();
            var recent = source.subList(Math.max(0, source.size() - 20), source.size());
            Double mastery = recent.isEmpty() ? null : mean(recent);
            Map<String, Object> row = new LinkedHashMap<>(point);
            row.remove("createdBy");
            row.remove("classId");
            row.put("mastery", mastery);
            row.put("sampleCount", recent.size());
            row.put("totalCount", source.size());
            row.put("lastStudiedAt", source.isEmpty() ? null : source.get(source.size() - 1).get("occurredAt"));
            row.put("state", mastery == null ? "UNKNOWN" : mastery < 60 ? "WEAK" : mastery < 80 ? "DEVELOPING" : "MASTERED");
            row.put("ruleVersion", VERSION);
            List<Map<String, Object>> trend = new ArrayList<>();
            for (int i = Math.max(0, source.size() - 10); i < source.size(); i++) {
                trend.add(Map.of("at", source.get(i).get("occurredAt"),
                        "mastery", mean(source.subList(Math.max(0, i - 19), i + 1))));
            }
            row.put("trend", trend);
            result.add(row);
        }
        return result;
    }

    public static int difficulty(Map<String, Object> point) {
        if (point == null || point.get("mastery") == null) return 1;
        double value = ((Number) point.get("mastery")).doubleValue();
        return value < 60 ? 1 : value < 80 ? 2 : 3;
    }

    public static List<Map<String, Object>> priorities(List<Map<String, Object>> profile) {
        return profile.stream().filter(p -> Boolean.TRUE.equals(p.get("enabled")))
                .sorted(Comparator.<Map<String, Object>>comparingInt(p -> switch (p.get("state").toString()) {
                    case "WEAK" -> 0;
                    case "UNKNOWN" -> 1;
                    case "DEVELOPING" -> 2;
                    default -> 3;
                }).thenComparingDouble(p -> p.get("mastery") == null ? 0 : ((Number) p.get("mastery")).doubleValue())
                        .thenComparingLong(p -> number(p, "id")))
                .toList();
    }

    private static double mean(List<Map<String, Object>> evidence) {
        BigDecimal sum = BigDecimal.ZERO;
        for (var item : evidence) {
            sum = sum.add(BigDecimal.valueOf(number(item, "score")).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(number(item, "maxScore")), 20, RoundingMode.HALF_UP));
        }
        return sum.divide(BigDecimal.valueOf(evidence.size()), 1, RoundingMode.HALF_UP).doubleValue();
    }
}
