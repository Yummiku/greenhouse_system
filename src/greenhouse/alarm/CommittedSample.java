package greenhouse.alarm;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** Server-side snapshot of a saved sample; never construct it from an untrusted browser body. */
public record CommittedSample(long sampleId, String sampleKey, long greenhouseId, int devicePk,
                              String metricType, BigDecimal value, String unit,
                              LocalDateTime sampleTime, Source source, boolean deleted) {
    private static final Map<String, String> UNITS = Map.of(
            "air_temperature", "℃", "air_humidity", "%", "soil_temperature", "℃",
            "soil_moisture", "%", "illuminance", "lx");

    public CommittedSample {
        if (sampleId <= 0 || greenhouseId <= 0 || devicePk <= 0)
            throw new IllegalArgumentException("采样、大棚及设备编号必须来自有效入库记录");
        if (sampleKey == null || sampleKey.isBlank() || sampleKey.length() > 128)
            throw new IllegalArgumentException("采样重试键不能为空且最多128字");
        if (metricType == null || !UNITS.containsKey(metricType))
            throw new IllegalArgumentException("采样指标无效");
        if (!UNITS.get(metricType).equals(unit))
            throw new IllegalArgumentException("采样单位与指标不一致");
        if (value == null || sampleTime == null || source == null)
            throw new IllegalArgumentException("采样数值、业务时间及来源必须完整");
    }

    public enum Source {
        REAL("real"), SIMULATED("simulated"), MANUAL("manual");
        private final String code;
        Source(String code) { this.code = code; }
        public String code() { return code; }
        public static Source fromCode(String code) {
            for (Source source : values()) if (source.code.equals(code)) return source;
            throw new IllegalArgumentException("来源仅允许real、simulated或manual");
        }
    }
}
