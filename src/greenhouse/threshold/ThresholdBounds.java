package greenhouse.threshold;

import java.math.BigDecimal;

/** 新增和编辑规则时共用的上下限校验。 */
public final class ThresholdBounds {
    private ThresholdBounds() {
    }

    public record Limits(BigDecimal lower, BigDecimal upper) {
        public String classify(BigDecimal value) {
            if (value == null) {
                throw new IllegalArgumentException("请输入试算数值");
            }
            if (lower != null && value.compareTo(lower) < 0) {
                return "低于下限";
            }
            if (upper != null && value.compareTo(upper) > 0) {
                return "高于上限";
            }
            return "正常";
        }
    }

    public static Limits parse(String lowerText, String upperText) {
        BigDecimal lower = parseLimit(lowerText);
        BigDecimal upper = parseLimit(upperText);
        if (lower == null && upper == null) {
            throw new IllegalArgumentException("至少填写一个边界");
        }
        if (lower != null && upper != null && lower.compareTo(upper) >= 0) {
            throw new IllegalArgumentException("下限必须小于上限");
        }
        return new Limits(lower, upper);
    }

    private static BigDecimal parseLimit(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(text.trim()).stripTrailingZeros();
            if (value.scale() > 6 || (long) value.precision() - value.scale() > 12) {
                throw new IllegalArgumentException("边界最多支持 12 位整数和 6 位小数");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("边界必须是数字", e);
        }
    }
}
