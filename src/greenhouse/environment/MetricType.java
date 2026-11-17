package greenhouse.environment;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 本模块固定的五类指标。顺序即页面展示顺序，单位由后台决定，
 * 客户端自报单位一律忽略：采样数据的可信度取决于服务端对单位与指标归属的校验。
 */
public enum MetricType {

    AIR_TEMPERATURE("air_temperature", "空气温度", "℃", new BigDecimal("-40"), new BigDecimal("80")),
    AIR_HUMIDITY("air_humidity", "空气湿度", "%", new BigDecimal("0"), new BigDecimal("100")),
    SOIL_TEMPERATURE("soil_temperature", "土壤温度", "℃", new BigDecimal("-40"), new BigDecimal("80")),
    SOIL_MOISTURE("soil_moisture", "土壤湿度", "%", new BigDecimal("0"), new BigDecimal("100")),
    ILLUMINANCE("illuminance", "光照强度", "lx", new BigDecimal("0"), new BigDecimal("200000"));

    /** 声明顺序固定，用于卡片和筛选下拉的稳定顺序。 */
    private static final Map<String, MetricType> BY_CODE = new LinkedHashMap<>();

    static {
        for (MetricType metric : values()) {
            BY_CODE.put(metric.code, metric);
        }
    }

    private final String code;
    private final String label;
    private final String unit;
    private final BigDecimal min;
    private final BigDecimal max;

    MetricType(String code, String label, String unit, BigDecimal min, BigDecimal max) {
        this.code = code;
        this.label = label;
        this.unit = unit;
        this.min = min;
        this.max = max;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public String unit() {
        return unit;
    }

    public BigDecimal min() {
        return min;
    }

    public BigDecimal max() {
        return max;
    }

    public static Optional<MetricType> find(String code) {
        if (code == null) return Optional.empty();
        return Optional.ofNullable(BY_CODE.get(code.trim()));
    }

    /** 取值范围校验；超出按 400 处理，不做静默截断。 */
    public boolean allows(BigDecimal value) {
        return value != null && value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }

    public String rangeText() {
        return min.stripTrailingZeros().toPlainString() + "~" + max.stripTrailingZeros().toPlainString() + unit;
    }
}
