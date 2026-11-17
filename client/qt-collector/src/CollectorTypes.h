#pragma once

// 采集协议相关的公共定义。
// 把指标枚举、错误分类、数据结构、时间格式集中在这里，是为了让“协议里的字符串写法”
// 和“界面展示文案”只有一个来源：改了这里，上传 body、校验逻辑和界面提示一起生效。

#include <QDateTime>
#include <QMetaType>
#include <QString>
#include <QVector>

#include <optional>

namespace collector {

// 五类环境指标。枚举顺序同时也是默认采集顺序。
enum class MetricType {
    AirTemperature,   // 空气温度，单位 ℃
    AirHumidity,      // 空气湿度，单位 %
    SoilTemperature,  // 土壤温度，单位 ℃
    SoilMoisture,     // 土壤湿度，单位 %
    Illuminance,      // 光照度，单位 lx
};

// 失败原因分类。
// 刻意把“传输层失败”和“业务层失败”拆开：前者往往值得退避重试，
// 后者（例如凭据错误、服务端按唯一键拒收）重发同一份数据只会得到同样结果。
enum class ErrorKind {
    None = 0,
    InvalidConfig,   // 本地配置不完整，请求还没发出去
    NetworkError,    // 连接失败、DNS 失败、TLS 失败等
    Timeout,         // 传输超时（由 QNetworkRequest::setTransferTimeout 触发）
    HttpError,       // 收到了 HTTP 响应，但状态码不是 200
    JsonParseError,  // 响应体不是合法 JSON
    ProtocolError,   // 合法 JSON，但字段不符协议或被服务端业务拒绝
    Busy,            // 本地状态：已有批次在传输，本次被跳过
};

QString metricTypeName(MetricType type);                  // 协议中的 metric_type 字符串
std::optional<MetricType> metricTypeFromName(const QString& name);
QString metricDisplayName(MetricType type);               // 界面展示用中文名
QString metricUnit(MetricType type);                      // ℃、%、lx
QString formatMetricValue(MetricType type, double value); // 数值格式化（不含单位）
QVector<MetricType> allMetricTypes();

QString errorKindName(ErrorKind kind);

// 是否值得用同一份 payload 重试。
// 网络错误/超时说明请求可能根本没到服务端；5xx、408、429 属于服务端暂时不可用。
// 4xx、JSON 解析失败、业务拒绝都不重试，因为重复发送不会改变结果。
bool isRetryable(ErrorKind kind, int httpStatus);

// 一条采样。sample_key 由客户端生成并保证唯一：
//   "<greenhouse_code>:<metric_type>:<yyyyMMddHHmmss>"
struct Sample {
    QString sampleKey;
    MetricType metric = MetricType::AirTemperature;
    double value = 0.0;
    QDateTime sampleTime;  // 精确到秒，带 +08:00 偏移
};

// 服务端 rejected 数组中的一项。
struct Rejection {
    QString sampleKey;
    QString reason;
};

// 服务端一次 ingest 响应中我们关心的字段。
struct IngestResponse {
    int resultCode = 0;
    QString resultMsg;
    int accepted = 0;
    int duplicated = 0;
    QVector<Rejection> rejected;
};

// 一批采样的最终结果（成功，或重试耗尽 / 不可重试之后的失败）。
struct BatchReport {
    quint64 batchId = 0;  // 客户端本地批次号，仅用于界面和日志关联
    int sampleCount = 0;
    int attempts = 0;
    int httpStatus = 0;
    ErrorKind error = ErrorKind::None;
    QString message;  // 面向用户的中文失败说明
    int accepted = 0;
    int duplicated = 0;
    QVector<Rejection> rejected;
    bool retried = false;

    bool isSuccess() const { return error == ErrorKind::None; }
};

// 时间约定：接口要求 Asia/Shanghai 本地时间 "yyyy-MM-dd HH:mm:ss"。
// 客户端固定使用 UTC+08:00（上海自 1991 年后不再使用夏令时），
// 这样既不依赖运行机器的时区设置，也不依赖 Windows 的时区数据库映射。
QDateTime currentShanghaiTime();
QString formatSampleTime(const QDateTime& time);  // yyyy-MM-dd HH:mm:ss
QString formatKeyTime(const QDateTime& time);     // yyyyMMddHHmmss（sample_key 用）

// 上报精度：温度/湿度保留 1 位小数，光照取整数。
double roundMetricValue(MetricType type, double raw);

}  // namespace collector

// 让错误类型和批次结果可以安全地穿过 Qt 的元对象系统
// （目前的信号都是同线程直连；注册后将来改成队列连接或 QSignalSpy 也不会踩坑）。
Q_DECLARE_METATYPE(collector::ErrorKind)
Q_DECLARE_METATYPE(collector::BatchReport)
