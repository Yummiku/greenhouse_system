#pragma once

#include "CollectorTypes.h"

#include <QString>
#include <QStringList>
#include <QUrl>
#include <QVector>

namespace collector {

// 退避重试参数。默认：1s、2s、4s 三次重试（加上首次尝试，一批最多发 4 次请求）。
struct RetryPolicy {
    int maxRetries = 3;
    int initialDelayMs = 1000;
    int backoffMultiplier = 2;
    int requestTimeoutMs = 10000;

    // retryIndex 从 0 开始，依次得到 1000、2000、4000ms。
    int delayBeforeRetry(int retryIndex) const;
};

// 一个采集端 = 一个大棚 + 一个设备，两者都在配置里固定，
// 运行期不允许在界面上修改，避免把数据写到别的大棚/设备上。
struct CollectorConfig {
    QString baseUrl;         // 服务端根地址，例如 http://127.0.0.1:8080
    QString collectorKey;    // 采集凭据，只从配置读取，不写死在代码里
    qint64 devicePk = 0;
    qint64 greenhouseId = 0;
    QString greenhouseCode;  // 大棚编码，用作 sample_key 前缀
    int uploadIntervalMs = 2000;
    QVector<MetricType> metrics;
    RetryPolicy retry;

    // {baseUrl}/api/environment/ingest
    QUrl ingestUrl() const;
};

struct ConfigLoadResult {
    CollectorConfig config;
    QString path;
    QStringList problems;  // 非空表示这份配置不能用于上传

    bool ok() const { return problems.isEmpty(); }
};

// 读取并校验配置文件。
// 即使返回 problems，config 里也会尽量填入已解析出的字段，
// 方便界面把“当前值”和“哪里错了”一起展示。
ConfigLoadResult loadConfig(const QString& path);

// 单独暴露校验，方便将来做“保存前校验”或单元测试。
QStringList validateConfig(const CollectorConfig& config);

}  // namespace collector
