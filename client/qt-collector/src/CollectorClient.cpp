#include "CollectorClient.h"

#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QJsonParseError>
#include <QJsonValue>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QVariant>

#include <utility>

namespace collector {
namespace {

QString bodySnippet(const QByteArray& body, int maxChars = 200)
{
    QString text = QString::fromUtf8(body).simplified();
    if (text.size() > maxChars) {
        text = text.left(maxChars) + QChar(0x2026);  // “…”
    }
    return text;
}

// 解析成功返回 true。
// 失败时用 kind 区分“不是合法 JSON”和“JSON 合法但不符合协议”，
// 这两种情况对使用者意味着完全不同的排查方向。
bool parseIngestResponse(const QByteArray& body, IngestResponse* response,
                         ErrorKind* kind, QString* message)
{
    QJsonParseError parseError{};
    const QJsonDocument document = QJsonDocument::fromJson(body, &parseError);
    if (parseError.error != QJsonParseError::NoError) {
        *kind = ErrorKind::JsonParseError;
        *message = QStringLiteral("响应不是合法 JSON（位置 %1）：%2")
                       .arg(parseError.offset)
                       .arg(parseError.errorString());
        return false;
    }
    if (!document.isObject()) {
        *kind = ErrorKind::ProtocolError;
        *message = QStringLiteral("响应顶层不是 JSON 对象");
        return false;
    }

    const QJsonObject root = document.object();
    const QJsonValue resultCodeValue = root.value(QStringLiteral("result_code"));
    if (!resultCodeValue.isDouble()) {
        *kind = ErrorKind::ProtocolError;
        *message = QStringLiteral("响应缺少整数 result_code");
        return false;
    }

    const QJsonValue dataValue = root.value(QStringLiteral("data"));
    if (!dataValue.isObject()) {
        *kind = ErrorKind::ProtocolError;
        *message = QStringLiteral("响应缺少 data 对象");
        return false;
    }

    const QJsonObject data = dataValue.toObject();
    if (!data.value(QStringLiteral("accepted")).isDouble()
        || !data.value(QStringLiteral("duplicated")).isDouble()) {
        *kind = ErrorKind::ProtocolError;
        *message = QStringLiteral("响应 data 缺少 accepted / duplicated 计数");
        return false;
    }

    response->resultCode = resultCodeValue.toInt();
    response->resultMsg = root.value(QStringLiteral("result_msg")).toString();
    response->accepted = data.value(QStringLiteral("accepted")).toInt();
    response->duplicated = data.value(QStringLiteral("duplicated")).toInt();

    const QJsonValue rejectedValue = data.value(QStringLiteral("rejected"));
    if (rejectedValue.isUndefined() || rejectedValue.isNull()) {
        // 没有拒收项时，服务端可能省略字段或写 null；两者都按“空数组”处理更稳妥。
        return true;
    }
    if (!rejectedValue.isArray()) {
        *kind = ErrorKind::ProtocolError;
        *message = QStringLiteral("响应 data.rejected 不是数组");
        return false;
    }

    for (const QJsonValue& item : rejectedValue.toArray()) {
        if (!item.isObject()) {
            continue;
        }
        const QJsonObject itemObject = item.toObject();
        Rejection rejection;
        rejection.sampleKey = itemObject.value(QStringLiteral("sample_key")).toString();
        rejection.reason = itemObject.value(QStringLiteral("reason")).toString();
        response->rejected.append(rejection);
    }
    return true;
}

}  // namespace

CollectorClient::CollectorClient(CollectorConfig config, QObject* parent)
    : QObject(parent)
    , m_config(std::move(config))
{
    m_retryTimer.setSingleShot(true);
    connect(&m_retryTimer, &QTimer::timeout, this, &CollectorClient::startAttempt);
}

CollectorClient::~CollectorClient()
{
    m_retryTimer.stop();
    if (m_reply) {
        // 窗口关闭时断开回调再 abort，避免迟到的 finished() 调用到正在销毁的对象。
        m_reply->disconnect(this);
        m_reply->abort();
    }
}

bool CollectorClient::isBusy() const
{
    return m_state != State::Idle;
}

bool CollectorClient::submit(const QVector<Sample>& samples, quint64 batchId)
{
    if (samples.isEmpty() || isBusy()) {
        return false;
    }

    m_pendingSamples = samples;
    m_pendingBatchId = batchId;

    // payload 只在批次开始时构建一次。
    // 重试时必须发送字节级相同的 body（同一批 sample_key），
    // 服务端才能按唯一键识别并去重，而不是把它当成新数据。
    m_pendingPayload = buildPayload();

    m_attempt = 0;
    m_retriesUsed = 0;
    startAttempt();
    return true;
}

bool CollectorClient::setConfig(const CollectorConfig& config)
{
    if (isBusy()) {
        return false;
    }
    m_config = config;
    return true;
}

int CollectorClient::maxAttempts() const
{
    return m_config.retry.maxRetries + 1;
}

void CollectorClient::startAttempt()
{
    ++m_attempt;
    m_state = State::InFlight;

    QNetworkRequest request(m_config.ingestUrl());
    request.setHeader(QNetworkRequest::ContentTypeHeader, QStringLiteral("application/json"));
    // 采集凭据只从配置读取，并通过约定的请求头传递，不拼进 URL，避免被日志/代理记下来。
    request.setRawHeader(QByteArrayLiteral("X-Collector-Key"), m_config.collectorKey.toUtf8());
    request.setRawHeader(QByteArrayLiteral("Accept"), QByteArrayLiteral("application/json"));
    // 服务端半开连接时若不设超时，这批数据会永久占住“忙碌”状态，
    // 后续所有 tick 都会被跳过，界面看起来像卡死。
    request.setTransferTimeout(m_config.retry.requestTimeoutMs);

    emit attemptStarted(m_pendingBatchId, m_attempt, maxAttempts(),
                        static_cast<int>(m_pendingSamples.size()));

    QNetworkReply* reply = m_network.post(request, m_pendingPayload);
    m_reply = reply;
    connect(reply, &QNetworkReply::finished, this, &CollectorClient::onReplyFinished);
}

void CollectorClient::onReplyFinished()
{
    QNetworkReply* reply = qobject_cast<QNetworkReply*>(sender());
    if (reply == nullptr) {
        return;
    }

    const bool isCurrentReply = (reply == m_reply.data());
    reply->deleteLater();
    if (!isCurrentReply) {
        // 旧请求的迟到结果不能影响当前批次（正常流程不会出现，防御性保留）。
        return;
    }
    m_reply = nullptr;

    BatchReport report;
    report.batchId = m_pendingBatchId;
    report.sampleCount = static_cast<int>(m_pendingSamples.size());
    report.attempts = m_attempt;
    report.httpStatus = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();

    const QByteArray body = reply->readAll();
    const QNetworkReply::NetworkError networkError = reply->error();

    // 情况一：没有拿到任何 HTTP 状态码，说明连接层就失败了。
    if (report.httpStatus == 0) {
        const bool timedOut = (networkError == QNetworkReply::TimeoutError
                               || networkError == QNetworkReply::OperationCanceledError);
        report.error = timedOut ? ErrorKind::Timeout : ErrorKind::NetworkError;
        report.message = QStringLiteral("%1：%2")
                             .arg(timedOut ? QStringLiteral("请求超时")
                                           : QStringLiteral("网络连接失败"),
                                  reply->errorString());
        if (!body.isEmpty()) {
            report.message += QStringLiteral("；响应片段：") + bodySnippet(body);
        }
        finishBatch(report);
        return;
    }

    // 情况二：拿到了状态码但不是 200。即使 body 看起来像 JSON 也不能当成功。
    if (report.httpStatus != 200) {
        report.error = ErrorKind::HttpError;
        report.message = QStringLiteral("HTTP %1：%2")
                             .arg(report.httpStatus)
                             .arg(reply->errorString());
        if (!body.isEmpty()) {
            report.message += QStringLiteral("；响应片段：") + bodySnippet(body);
        }
        finishBatch(report);
        return;
    }

    // 情况三：HTTP 200，但响应体可能不是合法 JSON，或字段不符合协议。
    IngestResponse response;
    ErrorKind parseKind = ErrorKind::None;
    QString parseMessage;
    if (!parseIngestResponse(body, &response, &parseKind, &parseMessage)) {
        report.error = parseKind;
        report.message = parseMessage;
        if (!body.isEmpty()) {
            report.message += QStringLiteral("；响应片段：") + bodySnippet(body);
        }
        finishBatch(report);
        return;
    }

    report.accepted = response.accepted;
    report.duplicated = response.duplicated;
    report.rejected = response.rejected;

    if (response.resultCode != 0) {
        report.error = ErrorKind::ProtocolError;
        report.message = QStringLiteral("result_code=%1：%2")
                             .arg(response.resultCode)
                             .arg(response.resultMsg.isEmpty()
                                      ? QStringLiteral("（服务端未提供说明）")
                                      : response.resultMsg);
    } else if (!response.rejected.isEmpty()) {
        // result_code 为 0 也可能有部分拒收：必须在界面上高亮，不能当作完全成功。
        report.error = ErrorKind::ProtocolError;
        report.message = QStringLiteral("服务端拒收 %1 条：%2")
                             .arg(static_cast<int>(response.rejected.size()))
                             .arg(response.rejected.first().reason.isEmpty()
                                      ? QStringLiteral("（服务端未提供原因）")
                                      : response.rejected.first().reason);
    }

    finishBatch(report);
}

void CollectorClient::finishBatch(BatchReport report)
{
    if (report.error != ErrorKind::None
        && isRetryable(report.error, report.httpStatus)
        && m_retriesUsed < m_config.retry.maxRetries) {
        const int delayMs = m_config.retry.delayBeforeRetry(m_retriesUsed);
        ++m_retriesUsed;

        // 只切状态、不动 payload：下一次尝试发送的仍是同一批 sample_key。
        m_state = State::WaitingRetry;
        m_retryTimer.start(delayMs);
        emit attemptFailed(report.batchId, m_attempt, maxAttempts(),
                           report.error, report.message, delayMs);
        return;
    }

    report.retried = (m_retriesUsed > 0);
    m_state = State::Idle;
    m_retryTimer.stop();
    resetPendingBatch();
    emit batchFinished(report);
}

void CollectorClient::resetPendingBatch()
{
    m_pendingSamples.clear();
    m_pendingPayload.clear();
    m_pendingBatchId = 0;
    m_attempt = 0;
    m_retriesUsed = 0;
}

QByteArray CollectorClient::buildPayload() const
{
    QJsonArray samples;
    for (const Sample& sample : m_pendingSamples) {
        QJsonObject item;
        item.insert(QStringLiteral("sample_key"), sample.sampleKey);
        item.insert(QStringLiteral("metric_type"), metricTypeName(sample.metric));
        item.insert(QStringLiteral("value"), sample.value);
        item.insert(QStringLiteral("sample_time"), formatSampleTime(sample.sampleTime));
        samples.append(item);
    }

    QJsonObject root;
    root.insert(QStringLiteral("device_pk"), m_config.devicePk);
    root.insert(QStringLiteral("greenhouse_id"), m_config.greenhouseId);
    // 来源由配置决定：模拟采集写 simulated，接上真实传感器后把配置改成 real。
    // 服务端只接受 simulated / real，采集端不能自报 manual（人工来源只能在页面录入）。
    root.insert(QStringLiteral("source"),
                m_config.source.isEmpty() ? QStringLiteral("simulated") : m_config.source);
    root.insert(QStringLiteral("samples"), samples);

    return QJsonDocument(root).toJson(QJsonDocument::Compact);
}

}  // namespace collector
