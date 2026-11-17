#include "SampleGenerator.h"

#include <QSet>
#include <QTime>

#include <algorithm>
#include <cmath>

namespace collector {
namespace {

constexpr double kPi = 3.14159265358979323846;

struct MetricBounds {
    double minValue;
    double maxValue;
};

MetricBounds boundsFor(MetricType type)
{
    switch (type) {
    case MetricType::AirTemperature:
        return {8.0, 40.0};
    case MetricType::AirHumidity:
        return {20.0, 98.0};
    case MetricType::SoilTemperature:
        return {8.0, 35.0};
    case MetricType::SoilMoisture:
        return {5.0, 80.0};
    case MetricType::Illuminance:
        return {0.0, 120000.0};
    }
    return {0.0, 100.0};
}

}  // namespace

SampleGenerator::SampleGenerator(quint32 seed)
    // 显式初始化而不是默认构造 + seed()：QRandomGenerator 的默认种子是固定值，
    // 用它会让每次运行都产生完全相同的“模拟数据”。
    : m_rng(seed != 0u ? seed : QRandomGenerator::securelySeeded().generate())
{
}

QVector<Sample> SampleGenerator::generate(const QVector<MetricType>& metrics,
                                          const QString& greenhouseCode)
{
    QVector<Sample> samples;
    if (metrics.isEmpty() || greenhouseCode.isEmpty()) {
        return samples;
    }
    samples.reserve(metrics.size());

    QDateTime sampleTime = currentShanghaiTime();
    qint64 second = sampleTime.toSecsSinceEpoch();
    if (second <= m_lastUsedSecond) {
        // 同一秒内被连续触发（例如“立即上传”刚好跟在定时器 tick 后面）时，
        // 复用这一秒会生成重复的 sample_key。这里顺延到下一个未使用的秒：
        // 误差最多 1 秒，但保证客户端自身不制造重复键。
        second = m_lastUsedSecond + 1;
        sampleTime = QDateTime::fromSecsSinceEpoch(second, Qt::OffsetFromUTC, 8 * 3600);
    }
    m_lastUsedSecond = second;

    const QString keyTime = formatKeyTime(sampleTime);
    QSet<int> emittedMetrics;  // 防御：即使调用方传入重复指标，同一批也不生成重复 key
    for (const MetricType type : metrics) {
        if (emittedMetrics.contains(static_cast<int>(type))) {
            continue;
        }
        emittedMetrics.insert(static_cast<int>(type));

        Sample sample;
        sample.metric = type;
        sample.sampleTime = sampleTime;
        sample.sampleKey = greenhouseCode + QLatin1Char(':')
                           + metricTypeName(type) + QLatin1Char(':') + keyTime;
        sample.value = roundMetricValue(type, nextValue(type, sampleTime));
        samples.append(sample);
    }
    return samples;
}

double SampleGenerator::nextValue(MetricType type, const QDateTime& time)
{
    const double baseline = baselineFor(type, time);
    const double step = maxStepFor(type, baseline);
    const MetricBounds bounds = boundsFor(type);

    const auto found = m_lastValue.constFind(static_cast<int>(type));
    double value = (found == m_lastValue.constEnd()) ? baseline + noise(step * 1.2)
                                                     : found.value();

    // 随机游走 + 向基线轻微回归：
    // 纯随机游走会慢慢漂到量程边缘，纯随机数又会让曲线跳变，这里取两者的折中。
    value += noise(step) + (baseline - value) * 0.12;
    value = std::clamp(value, bounds.minValue, bounds.maxValue);

    m_lastValue.insert(static_cast<int>(type), value);
    return value;
}

double SampleGenerator::baselineFor(MetricType type, const QDateTime& time) const
{
    switch (type) {
    case MetricType::AirTemperature:
        return 23.0;
    case MetricType::AirHumidity:
        return 62.0;
    case MetricType::SoilTemperature:
        return 20.5;
    case MetricType::SoilMoisture:
        return 38.0;
    case MetricType::Illuminance: {
        // 简易日光曲线：6 点日出、12 点最强、18 点日落；夜间只剩微弱补光。
        const QTime wallClock = time.time();
        const double hour = wallClock.hour() + wallClock.minute() / 60.0
                            + wallClock.second() / 3600.0;
        const double daylight = std::max(0.0, std::sin(kPi * (hour - 6.0) / 12.0));
        return 120.0 + 52000.0 * daylight;
    }
    }
    return 0.0;
}

double SampleGenerator::maxStepFor(MetricType type, double baseline) const
{
    switch (type) {
    case MetricType::AirTemperature:
        return 0.35;  // 每拍最多波动约 0.35℃
    case MetricType::AirHumidity:
        return 1.8;   // 每拍最多波动约 1.8%
    case MetricType::SoilTemperature:
        return 0.18;  // 土壤热惯性大，变化应比气温慢
    case MetricType::SoilMoisture:
        return 0.7;
    case MetricType::Illuminance:
        return 150.0 + baseline * 0.03;  // 光照噪声随亮度增大
    }
    return 1.0;
}

double SampleGenerator::noise(double scale)
{
    return (m_rng.generateDouble() * 2.0 - 1.0) * scale;
}

}  // namespace collector
