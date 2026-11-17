#pragma once

#include "CollectorConfig.h"
#include "CollectorTypes.h"

#include <QByteArray>
#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QObject>
#include <QPointer>
#include <QTimer>
#include <QVector>

namespace collector {

// 负责“一批采样从提交到出结果”的完整生命周期：发送、解析、失败退避重试。
// 界面只提交批次并监听结果，不需要关心重试状态；
// “重试必须复用同一个 sample_key”的约束因此只在这里实现一次。
class CollectorClient : public QObject
{
    Q_OBJECT

public:
    explicit CollectorClient(CollectorConfig config, QObject* parent = nullptr);
    ~CollectorClient() override;

    // 有请求在途或正在退避等待时返回 true；界面据此跳过本次定时 tick。
    bool isBusy() const;

    // 空闲时接受一批采样；忙碌时返回 false，不会产生重叠请求。
    // samples 一旦提交，重试期间不会再重新生成，payload 保持不变。
    bool submit(const QVector<Sample>& samples, quint64 batchId);

    // 只允许在空闲时替换配置，避免“批次跑到一半换了服务器/凭据”。
    bool setConfig(const CollectorConfig& config);
    const CollectorConfig& config() const { return m_config; }

signals:
    void attemptStarted(quint64 batchId, int attempt, int maxAttempts, int sampleCount);
    void attemptFailed(quint64 batchId, int attempt, int maxAttempts,
                       collector::ErrorKind kind, const QString& message, int nextDelayMs);
    void batchFinished(const collector::BatchReport& report);

private:
    enum class State {
        Idle,
        InFlight,
        WaitingRetry,
    };

    void startAttempt();
    void onReplyFinished();
    void finishBatch(BatchReport report);
    QByteArray buildPayload() const;
    int maxAttempts() const;
    void resetPendingBatch();

    CollectorConfig m_config;
    QNetworkAccessManager m_network;
    QTimer m_retryTimer;
    QPointer<QNetworkReply> m_reply;

    QVector<Sample> m_pendingSamples;
    QByteArray m_pendingPayload;
    quint64 m_pendingBatchId = 0;
    int m_attempt = 0;
    int m_retriesUsed = 0;
    State m_state = State::Idle;
};

}  // namespace collector
