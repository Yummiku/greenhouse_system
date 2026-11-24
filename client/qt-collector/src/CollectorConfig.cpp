#include "CollectorConfig.h"

#include <QDir>
#include <QFile>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QJsonParseError>
#include <QJsonValue>

#include <algorithm>
#include <cmath>
#include <limits>

namespace collector {
namespace {

// 读取工具只负责“类型/结构”错误；“缺字段、空值、范围”统一由 validateConfig 报，
// 这样同一条问题不会在界面上出现两次。
void readStringField(const QJsonObject& root, const QString& key,
                     QString* target, QStringList* problems)
{
    const QJsonValue value = root.value(key);
    if (value.isUndefined()) {
        return;
    }
    if (!value.isString()) {
        problems->append(QStringLiteral("字段 %1 必须是字符串").arg(key));
        return;
    }
    *target = value.toString().trimmed();
}

void readIntegerField(const QJsonObject& root, const QString& key,
                      qint64* target, QStringList* problems)
{
    const QJsonValue value = root.value(key);
    if (value.isUndefined()) {
        return;
    }
    if (!value.isDouble()) {
        problems->append(QStringLiteral("字段 %1 必须是整数").arg(key));
        return;
    }
    const double number = value.toDouble();
    if (number != std::floor(number)) {
        problems->append(QStringLiteral("字段 %1 必须是整数").arg(key));
        return;
    }
    *target = static_cast<qint64>(number);
}

// 把 qint64 收缩到 int 范围时只饱和、不“修正”。
// 越界值原样保留给 validateConfig 报错，避免配置写错后程序静默按另一个值运行。
int toIntSaturated(qint64 value)
{
    if (value < std::numeric_limits<int>::min()) {
        return std::numeric_limits<int>::min();
    }
    if (value > std::numeric_limits<int>::max()) {
        return std::numeric_limits<int>::max();
    }
    return static_cast<int>(value);
}

}  // namespace

int RetryPolicy::delayBeforeRetry(int retryIndex) const
{
    // 默认产生 1000、2000、4000ms。设上限是为了避免配置写错（例如 multiplier=10）
    // 之后界面长时间没有任何反馈。
    double delay = static_cast<double>(initialDelayMs);
    for (int i = 0; i < retryIndex; ++i) {
        delay *= std::max(1, backoffMultiplier);
    }
    return static_cast<int>(std::min(delay, 300000.0));
}

QUrl CollectorConfig::ingestUrl() const
{
    QString base = baseUrl.trimmed();
    while (base.endsWith(QLatin1Char('/'))) {
        base.chop(1);
    }
    return QUrl(base + QStringLiteral("/api/environment/ingest"));
}

ConfigLoadResult loadConfig(const QString& path)
{
    ConfigLoadResult result;
    result.path = path;

    QFile file(path);
    if (!file.exists()) {
        result.problems.append(QStringLiteral("找不到配置文件：%1").arg(QDir::toNativeSeparators(path)));
        return result;
    }
    if (!file.open(QIODevice::ReadOnly)) {
        result.problems.append(QStringLiteral("无法读取配置文件：%1（%2）")
                                   .arg(QDir::toNativeSeparators(path), file.errorString()));
        return result;
    }

    QByteArray raw = file.readAll();
    file.close();

    // Windows 记事本可能写入 UTF-8 BOM，而 QJsonDocument 不接受 BOM。
    // 这里按字节比较，避免依赖 QByteArray 对字符串字面量的隐式转换规则。
    if (raw.size() >= 3
        && static_cast<unsigned char>(raw.at(0)) == 0xEF
        && static_cast<unsigned char>(raw.at(1)) == 0xBB
        && static_cast<unsigned char>(raw.at(2)) == 0xBF) {
        raw.remove(0, 3);
    }

    QJsonParseError parseError{};
    const QJsonDocument document = QJsonDocument::fromJson(raw, &parseError);
    if (parseError.error != QJsonParseError::NoError) {
        result.problems.append(QStringLiteral("配置文件不是合法 JSON（位置 %1）：%2")
                                   .arg(parseError.offset)
                                   .arg(parseError.errorString()));
        return result;
    }
    if (!document.isObject()) {
        result.problems.append(QStringLiteral("配置文件顶层必须是 JSON 对象"));
        return result;
    }

    const QJsonObject root = document.object();
    CollectorConfig& config = result.config;

    readStringField(root, QStringLiteral("base_url"), &config.baseUrl, &result.problems);
    readStringField(root, QStringLiteral("collector_key"), &config.collectorKey, &result.problems);
    readStringField(root, QStringLiteral("greenhouse_code"), &config.greenhouseCode, &result.problems);
    readStringField(root, QStringLiteral("source"), &config.source, &result.problems);
    if (config.source.isEmpty()) {
        // 不写时按模拟采集处理：宁可把实采写成模拟，也不允许把模拟冒充实采。
        config.source = QStringLiteral("simulated");
    }

    qint64 devicePk = config.devicePk;
    readIntegerField(root, QStringLiteral("device_pk"), &devicePk, &result.problems);
    config.devicePk = devicePk;

    qint64 greenhouseId = config.greenhouseId;
    readIntegerField(root, QStringLiteral("greenhouse_id"), &greenhouseId, &result.problems);
    config.greenhouseId = greenhouseId;

    qint64 intervalMs = config.uploadIntervalMs;
    readIntegerField(root, QStringLiteral("upload_interval_ms"), &intervalMs, &result.problems);
    config.uploadIntervalMs = toIntSaturated(intervalMs);

    // 第13周约定：一台已绑定采样设备只对应一个指标，metrics 必须显式写出这一个指标。
    // 不再缺省为五类全部——那会把湿度样本发到温度设备上，服务端现在会逐条拒绝。
    const QJsonValue metricsValue = root.value(QStringLiteral("metrics"));
    if (metricsValue.isUndefined()) {
        result.problems.append(QStringLiteral(
            "缺少 metrics：一台采样设备只对应一个绑定指标，请只写该设备绑定的那一个指标"));
    } else if (!metricsValue.isArray()) {
        result.problems.append(QStringLiteral("字段 metrics 必须是字符串数组"));
    } else {
        QVector<MetricType> metrics;
        for (const QJsonValue& item : metricsValue.toArray()) {
            if (!item.isString()) {
                result.problems.append(QStringLiteral("metrics 中存在非字符串元素"));
                continue;
            }
            const std::optional<MetricType> type = metricTypeFromName(item.toString());
            if (!type.has_value()) {
                result.problems.append(
                    QStringLiteral("未知指标：%1（可选：air_temperature、air_humidity、"
                                   "soil_temperature、soil_moisture、illuminance）")
                        .arg(item.toString()));
                continue;
            }
            // 同一批内同一指标只允许出现一次，否则会生成重复的 sample_key。
            if (!metrics.contains(*type)) {
                metrics.append(*type);
            }
        }
        if (metrics.isEmpty()) {
            result.problems.append(QStringLiteral("metrics 不能为空"));
        }
        config.metrics = metrics;
    }

    // retry 可整体省略，使用默认的 1s/2s/4s 三次重试。
    const QJsonValue retryValue = root.value(QStringLiteral("retry"));
    if (retryValue.isObject()) {
        const QJsonObject retryObject = retryValue.toObject();

        qint64 maxRetries = config.retry.maxRetries;
        readIntegerField(retryObject, QStringLiteral("max_retries"), &maxRetries, &result.problems);
        config.retry.maxRetries = toIntSaturated(maxRetries);

        qint64 initialDelay = config.retry.initialDelayMs;
        readIntegerField(retryObject, QStringLiteral("initial_delay_ms"), &initialDelay, &result.problems);
        config.retry.initialDelayMs = toIntSaturated(initialDelay);

        qint64 multiplier = config.retry.backoffMultiplier;
        readIntegerField(retryObject, QStringLiteral("backoff_multiplier"), &multiplier, &result.problems);
        config.retry.backoffMultiplier = toIntSaturated(multiplier);

        qint64 timeoutMs = config.retry.requestTimeoutMs;
        readIntegerField(retryObject, QStringLiteral("request_timeout_ms"), &timeoutMs, &result.problems);
        config.retry.requestTimeoutMs = toIntSaturated(timeoutMs);
    } else if (!retryValue.isUndefined()) {
        result.problems.append(QStringLiteral("字段 retry 必须是对象"));
    }

    result.problems += validateConfig(config);
    return result;
}

QStringList validateConfig(const CollectorConfig& config)
{
    QStringList problems;

    // 服务端地址：必须是 http/https 绝对地址，避免把相对路径交给 QNetworkAccessManager。
    if (config.baseUrl.isEmpty()) {
        problems.append(QStringLiteral("base_url 不能为空"));
    } else {
        const QUrl url(config.baseUrl);
        if (!url.isValid() || url.scheme().isEmpty() || url.host().isEmpty()) {
            problems.append(QStringLiteral("base_url 必须是形如 http://127.0.0.1:8080 的绝对地址"));
        } else if (url.scheme() != QStringLiteral("http") && url.scheme() != QStringLiteral("https")) {
            problems.append(QStringLiteral("base_url 只支持 http 或 https"));
        } else if (url.path().isEmpty() || url.path() == QStringLiteral("/")) {
            // 本项目部署在 /greenhouse_system 上下文，只写到端口会请求到 /api/... 而 404。
            problems.append(QStringLiteral(
                "base_url 需要包含应用上下文，例如 http://localhost:9999/greenhouse_system"));
        }
    }

    // 采集凭据只来自配置。这里额外拦截示例占位值：
    // 直接拿示例去请求只会得到 401，提前提示比反复失败更友好。
    if (config.collectorKey.isEmpty()) {
        problems.append(
            QStringLiteral("collector_key 不能为空（采集凭据必须来自配置，不能写死在代码里）"));
    } else if (config.collectorKey.contains(QStringLiteral("REPLACE"), Qt::CaseInsensitive)
               || config.collectorKey.compare(QStringLiteral("changeme"), Qt::CaseInsensitive) == 0) {
        problems.append(
            QStringLiteral("collector_key 仍是示例占位值，请填入真实采集凭据（不要提交到仓库）"));
    }

    if (config.devicePk <= 0) {
        problems.append(QStringLiteral("device_pk 必须是正整数"));
    }
    if (config.greenhouseId <= 0) {
        problems.append(QStringLiteral("greenhouse_id 必须是正整数"));
    }

    if (config.greenhouseCode.isEmpty()) {
        problems.append(QStringLiteral("greenhouse_code 不能为空（sample_key 需要它做前缀）"));
    } else if (config.greenhouseCode.contains(QLatin1Char(':'))) {
        problems.append(QStringLiteral("greenhouse_code 不能包含冒号（sample_key 用冒号分段）"));
    } else if (config.greenhouseCode.contains(QLatin1Char(' '))) {
        problems.append(QStringLiteral("greenhouse_code 不能包含空格"));
    }

    if (config.metrics.isEmpty()) {
        problems.append(QStringLiteral("metrics 不能为空"));
    } else if (config.metrics.size() > 1) {
        problems.append(QStringLiteral(
            "metrics 只能有一个指标：一台采样设备只对应一个绑定指标，五类指标请用五台设备分别采集"));
    }

    if (config.source != QStringLiteral("simulated") && config.source != QStringLiteral("real")) {
        problems.append(QStringLiteral("source 只允许 simulated（模拟采集）或 real（真实采集）"));
    }

    if (config.uploadIntervalMs < 500 || config.uploadIntervalMs > 3600000) {
        problems.append(QStringLiteral("upload_interval_ms 必须在 500~3600000 之间"));
    }
    if (config.retry.maxRetries < 0 || config.retry.maxRetries > 10) {
        problems.append(QStringLiteral("retry.max_retries 必须在 0~10 之间"));
    }
    if (config.retry.initialDelayMs < 100 || config.retry.initialDelayMs > 60000) {
        problems.append(QStringLiteral("retry.initial_delay_ms 必须在 100~60000 之间"));
    }
    if (config.retry.backoffMultiplier < 1 || config.retry.backoffMultiplier > 10) {
        problems.append(QStringLiteral("retry.backoff_multiplier 必须在 1~10 之间"));
    }
    if (config.retry.requestTimeoutMs < 1000 || config.retry.requestTimeoutMs > 120000) {
        problems.append(QStringLiteral("retry.request_timeout_ms 必须在 1000~120000 之间"));
    }

    return problems;
}

}  // namespace collector
