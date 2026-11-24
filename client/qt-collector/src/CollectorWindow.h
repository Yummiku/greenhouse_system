#pragma once

#include "CollectorConfig.h"
#include "CollectorTypes.h"

#include <QStringList>
#include <QWidget>

#include <memory>

class QLabel;
class QPlainTextEdit;
class QPushButton;
class QSpinBox;
class QTimer;

namespace collector {
class CollectorClient;
class SampleGenerator;
}  // namespace collector

// 主窗口：纯代码布局（不使用 .ui）。
// 职责只包括界面、定时器节奏和累计计数；网络与重试逻辑都在 CollectorClient 里。
class CollectorWindow : public QWidget
{
    Q_OBJECT

public:
    explicit CollectorWindow(QString configPath, QWidget* parent = nullptr);
    ~CollectorWindow() override;

private:
    void buildUi();
    void loadConfiguration();
    void updateConfigSummary();

    void startPeriodicCollection();
    void stopPeriodicCollection();
    void collectOnce();

    void onAttemptStarted(quint64 batchId, int attempt, int maxAttempts, int sampleCount);
    void onAttemptFailed(quint64 batchId, int attempt, int maxAttempts,
                         collector::ErrorKind kind, const QString& message, int nextDelayMs);
    void handleBatchFinished(const collector::BatchReport& report);

    void appendLog(const QString& message, const QString& color);
    void setConnectionState(const QString& text, const QString& color);
    void updateTotalsUi();
    void applyRunningState();
    bool configurationReady() const;

    QString m_configPath;
    collector::CollectorConfig m_config;
    QStringList m_configProblems;

    std::unique_ptr<collector::SampleGenerator> m_generator;
    collector::CollectorClient* m_client = nullptr;  // 由 Qt 父子关系负责释放
    QTimer* m_timer = nullptr;                       // 同上

    QLabel* m_configPathLabel = nullptr;
    QLabel* m_serverLabel = nullptr;
    QLabel* m_deviceLabel = nullptr;
    QLabel* m_metricsLabel = nullptr;
    QLabel* m_sourceLabel = nullptr;
    QLabel* m_configHintLabel = nullptr;
    QPushButton* m_reloadButton = nullptr;
    QPushButton* m_toggleButton = nullptr;
    QPushButton* m_uploadButton = nullptr;
    QSpinBox* m_intervalSpin = nullptr;
    QLabel* m_stateLabel = nullptr;
    QLabel* m_lastErrorLabel = nullptr;
    QLabel* m_batchAcceptedLabel = nullptr;
    QLabel* m_batchDuplicatedLabel = nullptr;
    QLabel* m_batchRejectedLabel = nullptr;
    QLabel* m_attemptsLabel = nullptr;
    QLabel* m_totalLabel = nullptr;
    QPlainTextEdit* m_logView = nullptr;

    bool m_running = false;
    bool m_skipNoticeLogged = false;  // 连续跳过时只在日志里提示一次，避免刷屏
    quint64 m_nextBatchId = 1;
    quint64 m_totalAccepted = 0;
    quint64 m_totalDuplicated = 0;
    quint64 m_totalRejected = 0;
    int m_finishedBatches = 0;
};
