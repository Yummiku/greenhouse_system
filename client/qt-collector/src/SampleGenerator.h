#pragma once

#include "CollectorTypes.h"

#include <QHash>
#include <QRandomGenerator>
#include <QVector>

namespace collector {

// 模拟数据源：用“随机游走 + 向基线回归”的方式产生连续、量纲合理的曲线。
//
// TODO(真实硬件)：目前所有数值都由本类伪造，不是真实传感器采集。
// 接入串口/Modbus/网关后，应把 generate() 的取值部分替换成硬件读取，
// 但 Sample 结构和 sample_key 生成逻辑（唯一键）应保持不变。
class SampleGenerator
{
public:
    // seed 为 0 时使用系统随机种子，便于每次运行看到不同的模拟曲线。
    explicit SampleGenerator(quint32 seed = 0u);

    // 为指定指标各生成一条采样。同一批内不会出现重复的 sample_key。
    QVector<Sample> generate(const QVector<MetricType>& metrics, const QString& greenhouseCode);

private:
    double nextValue(MetricType type, const QDateTime& time);
    double baselineFor(MetricType type, const QDateTime& time) const;
    double maxStepFor(MetricType type, double baseline) const;
    double noise(double scale);

    QRandomGenerator m_rng;
    QHash<int, double> m_lastValue;   // 每种指标保留上一拍的数值，保证曲线连续
    qint64 m_lastUsedSecond = -1;     // 已使用的秒，用于避免同一秒生成重复键
};

}  // namespace collector
