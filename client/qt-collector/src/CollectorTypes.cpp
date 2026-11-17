#include "CollectorTypes.h"

#include <cmath>

namespace collector {
namespace {

struct MetricInfo {
    MetricType type;
    const char* jsonName;
    const char* unit;
    const char* displayName;
};

const MetricInfo kMetricInfos[] = {
    {MetricType::AirTemperature, "air_temperature", "℃", "空气温度"},
    {MetricType::AirHumidity, "air_humidity", "%", "空气湿度"},
    {MetricType::SoilTemperature, "soil_temperature", "℃", "土壤温度"},
    {MetricType::SoilMoisture, "soil_moisture", "%", "土壤湿度"},
    {MetricType::Illuminance, "illuminance", "lx", "光照度"},
};

const MetricInfo* findMetricInfo(MetricType type)
{
    for (const MetricInfo& info : kMetricInfos) {
        if (info.type == type) {
            return &info;
        }
    }
    return nullptr;
}

// 上海固定 UTC+08:00，与 IANA Asia/Shanghai 在 1991 年之后完全等价。
constexpr int kShanghaiUtcOffsetSeconds = 8 * 3600;

}  // namespace

QString metricTypeName(MetricType type)
{
    const MetricInfo* info = findMetricInfo(type);
    return info != nullptr ? QString::fromUtf8(info->jsonName) : QString();
}

std::optional<MetricType> metricTypeFromName(const QString& name)
{
    for (const MetricInfo& info : kMetricInfos) {
        if (name == QString::fromUtf8(info->jsonName)) {
            return info.type;
        }
    }
    return std::nullopt;
}

QString metricDisplayName(MetricType type)
{
    const MetricInfo* info = findMetricInfo(type);
    return info != nullptr ? QString::fromUtf8(info->displayName) : QStringLiteral("未知指标");
}

QString metricUnit(MetricType type)
{
    const MetricInfo* info = findMetricInfo(type);
    return info != nullptr ? QString::fromUtf8(info->unit) : QString();
}

QString formatMetricValue(MetricType type, double value)
{
    if (type == MetricType::Illuminance) {
        return QString::number(static_cast<qint64>(std::llround(value)));
    }
    return QString::number(value, 'f', 1);
}

QVector<MetricType> allMetricTypes()
{
    QVector<MetricType> types;
    const int count = static_cast<int>(sizeof(kMetricInfos) / sizeof(kMetricInfos[0]));
    types.reserve(count);
    for (const MetricInfo& info : kMetricInfos) {
        types.append(info.type);
    }
    return types;
}

QString errorKindName(ErrorKind kind)
{
    switch (kind) {
    case ErrorKind::None:
        return QStringLiteral("正常");
    case ErrorKind::InvalidConfig:
        return QStringLiteral("配置错误");
    case ErrorKind::NetworkError:
        return QStringLiteral("网络失败");
    case ErrorKind::Timeout:
        return QStringLiteral("请求超时");
    case ErrorKind::HttpError:
        return QStringLiteral("HTTP 状态异常");
    case ErrorKind::JsonParseError:
        return QStringLiteral("响应解析失败");
    case ErrorKind::ProtocolError:
        return QStringLiteral("服务端拒绝");
    case ErrorKind::Busy:
        return QStringLiteral("本地忙");
    }
    return QStringLiteral("未知错误");
}

bool isRetryable(ErrorKind kind, int httpStatus)
{
    switch (kind) {
    case ErrorKind::NetworkError:
    case ErrorKind::Timeout:
        return true;
    case ErrorKind::HttpError:
        // 5xx、408、429 是“服务端暂时不可用”；其余 4xx 是请求/凭据问题，
        // 重发同一份 payload 只会再失败一次，还会把界面拖入无意义的等待。
        return httpStatus >= 500 || httpStatus == 408 || httpStatus == 429;
    default:
        return false;
    }
}

QDateTime currentShanghaiTime()
{
    // 先取 UTC 再固定 +08:00，避免受本机时区设置影响。
    // currentSecsSinceEpoch() 已截断到秒，保证 sample_key 的秒和 sample_time 一致。
    return QDateTime::fromSecsSinceEpoch(QDateTime::currentSecsSinceEpoch(),
                                         Qt::OffsetFromUTC,
                                         kShanghaiUtcOffsetSeconds);
}

QString formatSampleTime(const QDateTime& time)
{
    return time.toString(QStringLiteral("yyyy-MM-dd HH:mm:ss"));
}

QString formatKeyTime(const QDateTime& time)
{
    return time.toString(QStringLiteral("yyyyMMddHHmmss"));
}

double roundMetricValue(MetricType type, double raw)
{
    if (type == MetricType::Illuminance) {
        return std::round(raw);  // lx 以整数上报
    }
    return std::round(raw * 10.0) / 10.0;  // 温度/湿度保留 1 位小数
}

}  // namespace collector
