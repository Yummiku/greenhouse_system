#include "CollectorWindow.h"

#include "CollectorClient.h"
#include "CollectorConfig.h"
#include "SampleGenerator.h"

#include <QDir>
#include <QFormLayout>
#include <QGridLayout>
#include <QGroupBox>
#include <QHBoxLayout>
#include <QLabel>
#include <QPlainTextEdit>
#include <QPushButton>
#include <QScrollBar>
#include <QSpinBox>
#include <QTimer>
#include <QVBoxLayout>

#include <utility>

namespace {

const QString kNeutralColor = QStringLiteral("#37474f");
const QString kOkColor = QStringLiteral("#1b5e20");
const QString kWarnColor = QStringLiteral("#a15c00");
const QString kErrorColor = QStringLiteral("#b00020");

QString joinMetricNames(const QVector<collector::MetricType>& metrics)
{
    QStringList parts;
    for (const collector::MetricType type : metrics) {
        parts.append(QStringLiteral("%1（%2，%3）")
                         .arg(collector::metricDisplayName(type))
                         .arg(collector::metricTypeName(type))
                         .arg(collector::metricUnit(type)));
    }
    return parts.isEmpty() ? QStringLiteral("（未配置）") : parts.join(QStringLiteral("、"));
}

}  // namespace

CollectorWindow::CollectorWindow(QString configPath, QWidget* parent)
    : QWidget(parent)
    , m_configPath(std::move(configPath))
    , m_generator(std::make_unique<collector::SampleGenerator>())
{
    buildUi();

    m_client = new collector::CollectorClient(collector::CollectorConfig(), this);
    m_timer = new QTimer(this);
    m_timer->setTimerType(Qt::CoarseTimer);

    connect(m_client, &collector::CollectorClient::attemptStarted,
            this, &CollectorWindow::onAttemptStarted);
    connect(m_client, &collector::CollectorClient::attemptFailed,
            this, &CollectorWindow::onAttemptFailed);
    connect(m_client, &collector::CollectorClient::batchFinished,
            this, &CollectorWindow::handleBatchFinished);
    connect(m_timer, &QTimer::timeout, this, &CollectorWindow::collectOnce);

    connect(m_reloadButton, &QPushButton::clicked, this, &CollectorWindow::loadConfiguration);
    connect(m_toggleButton, &QPushButton::clicked, this, [this]() {
        if (m_running) {
            stopPeriodicCollection();
        } else {
            startPeriodicCollection();
        }
    });
    connect(m_uploadButton, &QPushButton::clicked, this, &CollectorWindow::collectOnce);
    connect(m_intervalSpin, &QSpinBox::valueChanged, this, [this](int value) {
        if (m_running) {
            m_timer->start(value);
            appendLog(QStringLiteral("上传间隔已调整为 %1 ms").arg(value), kNeutralColor);
        }
    });

    loadConfiguration();
}

CollectorWindow::~CollectorWindow() = default;

void CollectorWindow::buildUi()
{
    setWindowTitle(QStringLiteral("大棚环境数据采集客户端"));
    resize(980, 760);
    setMinimumSize(840, 620);

    auto* rootLayout = new QVBoxLayout(this);
    rootLayout->setContentsMargins(12, 12, 12, 12);
    rootLayout->setSpacing(10);

    // ① 配置展示区：字段全部来自 JSON，运行期只读，避免“界面改了但其实没生效”。
    auto* configGroup = new QGroupBox(
        QStringLiteral("① 运行配置（只读，来源：JSON 配置文件）"), this);
    auto* configLayout = new QVBoxLayout(configGroup);

    auto* form = new QFormLayout();
    form->setLabelAlignment(Qt::AlignRight | Qt::AlignVCenter);
    form->setFieldGrowthPolicy(QFormLayout::AllNonFixedFieldsGrow);

    m_configPathLabel = new QLabel(this);
    m_configPathLabel->setWordWrap(true);
    m_configPathLabel->setTextInteractionFlags(Qt::TextSelectableByMouse);
    form->addRow(QStringLiteral("配置文件："), m_configPathLabel);

    m_serverLabel = new QLabel(this);
    m_serverLabel->setWordWrap(true);
    m_serverLabel->setTextInteractionFlags(Qt::TextSelectableByMouse);
    form->addRow(QStringLiteral("上传地址："), m_serverLabel);

    m_deviceLabel = new QLabel(this);
    m_deviceLabel->setWordWrap(true);
    form->addRow(QStringLiteral("设备 / 大棚："), m_deviceLabel);

    m_metricsLabel = new QLabel(this);
    m_metricsLabel->setWordWrap(true);
    form->addRow(QStringLiteral("采集指标："), m_metricsLabel);

    // 来源必须和地址、设备、指标一起显示：模拟采集不能看起来像真实采集。
    m_sourceLabel = new QLabel(this);
    m_sourceLabel->setWordWrap(true);
    form->addRow(QStringLiteral("采集来源："), m_sourceLabel);

    configLayout->addLayout(form);

    auto* intervalRow = new QHBoxLayout();
    intervalRow->addWidget(new QLabel(QStringLiteral("上传间隔："), this));
    m_intervalSpin = new QSpinBox(this);
    m_intervalSpin->setRange(500, 3600000);
    m_intervalSpin->setSingleStep(500);
    m_intervalSpin->setSuffix(QStringLiteral(" ms"));
    m_intervalSpin->setValue(2000);
    m_intervalSpin->setToolTip(
        QStringLiteral("默认 2000 ms（2 秒）。定时上传运行中修改会立即生效。"));
    intervalRow->addWidget(m_intervalSpin);
    intervalRow->addSpacing(16);
    m_reloadButton = new QPushButton(QStringLiteral("重新加载配置"), this);
    intervalRow->addWidget(m_reloadButton);
    intervalRow->addStretch(1);
    configLayout->addLayout(intervalRow);

    m_configHintLabel = new QLabel(this);
    m_configHintLabel->setWordWrap(true);
    m_configHintLabel->setTextFormat(Qt::RichText);
    m_configHintLabel->setTextInteractionFlags(Qt::TextSelectableByMouse);
    configLayout->addWidget(m_configHintLabel);

    rootLayout->addWidget(configGroup);

    // ② 采集控制区。
    auto* controlGroup = new QGroupBox(QStringLiteral("② 采集控制"), this);
    auto* controlLayout = new QVBoxLayout(controlGroup);

    auto* buttonRow = new QHBoxLayout();
    m_toggleButton = new QPushButton(QStringLiteral("开始定时上传"), this);
    m_uploadButton = new QPushButton(QStringLiteral("立即上传一次"), this);
    buttonRow->addWidget(m_toggleButton);
    buttonRow->addWidget(m_uploadButton);
    buttonRow->addStretch(1);
    controlLayout->addLayout(buttonRow);

    auto* stateRow = new QHBoxLayout();
    stateRow->addWidget(new QLabel(QStringLiteral("连接状态："), this));
    m_stateLabel = new QLabel(QStringLiteral("未开始"), this);
    m_stateLabel->setWordWrap(true);
    m_stateLabel->setStyleSheet(QStringLiteral("font-weight:600;"));
    stateRow->addWidget(m_stateLabel, 1);
    controlLayout->addLayout(stateRow);

    rootLayout->addWidget(controlGroup);

    // ③ 本批结果。
    auto* resultGroup = new QGroupBox(QStringLiteral("③ 本批结果"), this);
    auto* resultGrid = new QGridLayout(resultGroup);
    resultGrid->setHorizontalSpacing(10);

    const auto makeValueLabel = [this](const QString& text) {
        auto* label = new QLabel(text, this);
        label->setStyleSheet(QStringLiteral("font-weight:600;font-size:14px;"));
        return label;
    };

    m_batchAcceptedLabel = makeValueLabel(QStringLiteral("—"));
    m_batchDuplicatedLabel = makeValueLabel(QStringLiteral("—"));
    m_batchRejectedLabel = makeValueLabel(QStringLiteral("—"));
    m_batchRejectedLabel->setStyleSheet(
        QStringLiteral("font-weight:600;font-size:14px;color:#b00020;"));
    m_attemptsLabel = makeValueLabel(QStringLiteral("—"));

    resultGrid->addWidget(new QLabel(QStringLiteral("accepted："), this), 0, 0);
    resultGrid->addWidget(m_batchAcceptedLabel, 0, 1);
    resultGrid->addWidget(new QLabel(QStringLiteral("duplicated："), this), 0, 2);
    resultGrid->addWidget(m_batchDuplicatedLabel, 0, 3);
    resultGrid->addWidget(new QLabel(QStringLiteral("rejected："), this), 0, 4);
    resultGrid->addWidget(m_batchRejectedLabel, 0, 5);
    resultGrid->addWidget(new QLabel(QStringLiteral("尝试次数："), this), 1, 0);
    resultGrid->addWidget(m_attemptsLabel, 1, 1);

    m_lastErrorLabel = new QLabel(QStringLiteral("无"), this);
    m_lastErrorLabel->setWordWrap(true);
    m_lastErrorLabel->setTextInteractionFlags(Qt::TextSelectableByMouse);
    m_lastErrorLabel->setStyleSheet(QStringLiteral("color:#37474f;"));
    resultGrid->addWidget(new QLabel(QStringLiteral("最近一次错误："), this), 2, 0, Qt::AlignTop);
    resultGrid->addWidget(m_lastErrorLabel, 2, 1, 1, 5);
    resultGrid->setColumnStretch(6, 1);

    rootLayout->addWidget(resultGroup);

    // ④ 累计统计（只保存在内存里）。
    auto* totalGroup = new QGroupBox(QStringLiteral("④ 累计统计（本次运行）"), this);
    auto* totalLayout = new QVBoxLayout(totalGroup);
    m_totalLabel = new QLabel(QStringLiteral("尚未完成任何批次"), this);
    m_totalLabel->setWordWrap(true);
    totalLayout->addWidget(m_totalLabel);
    rootLayout->addWidget(totalGroup);

    // ⑤ 事件日志：仅内存，不写磁盘。
    auto* logGroup = new QGroupBox(
        QStringLiteral("⑤ 事件日志（仅显示在内存中，不写入磁盘）"), this);
    auto* logLayout = new QVBoxLayout(logGroup);
    m_logView = new QPlainTextEdit(this);
    m_logView->setReadOnly(true);
    m_logView->setMaximumBlockCount(1000);
    m_logView->setPlaceholderText(QStringLiteral("等待采集……"));
    logLayout->addWidget(m_logView);
    rootLayout->addWidget(logGroup, 1);
}

void CollectorWindow::loadConfiguration()
{
    // 批次进行中（含退避等待）不允许换配置：
    // 否则同一次重试可能打到不同服务器/凭据上，语义完全混乱。
    if (m_client != nullptr && m_client->isBusy()) {
        appendLog(QStringLiteral("暂不重载配置：上一批请求仍在进行，请等它结束"), kWarnColor);
        setConnectionState(QStringLiteral("配置未重载：上一批请求进行中"), kWarnColor);
        return;
    }

    // 先停表：换掉配置后不应该继续用旧地址/旧凭据跑定时任务。
    if (m_running) {
        stopPeriodicCollection();
    }

    const collector::ConfigLoadResult result = collector::loadConfig(m_configPath);
    m_configProblems = result.problems;

    if (result.ok()) {
        // 只有客户端确认接受配置后才切换：
        // 否则界面显示的是新地址/新凭据，实际发请求的却可能还是旧配置，排查时极具误导性。
        if (!m_client->setConfig(result.config)) {
            appendLog(QStringLiteral("配置未生效：上一批请求仍在进行，请稍后再试。"), kWarnColor);
            return;
        }

        m_config = result.config;
        updateConfigSummary();

        m_intervalSpin->setValue(qBound(500, m_config.uploadIntervalMs, 3600000));
        m_toggleButton->setEnabled(true);
        m_uploadButton->setEnabled(true);

        m_configHintLabel->setText(
            QStringLiteral("<span style='color:#1b5e20;'>配置校验通过：device_pk=%1，"
                           "greenhouse_id=%2，指标 %3，来源 %4，每 %5 ms 上传一次，"
                           "重试最多 %6 次。</span>")
                .arg(m_config.devicePk)
                .arg(m_config.greenhouseId)
                .arg(joinMetricNames(m_config.metrics))
                .arg(m_config.source)
                .arg(m_config.uploadIntervalMs)
                .arg(m_config.retry.maxRetries));

        appendLog(QStringLiteral("配置加载成功：%1").arg(QDir::toNativeSeparators(m_configPath)),
                  kOkColor);
        setConnectionState(QStringLiteral("就绪：等待开始采集"), kNeutralColor);
    } else {
        // 配置不可用时禁止发请求：既避免无意义的 401，也避免把错误数据写进服务端。
        // 仍把解析出的部分字段展示出来，方便对照错误列表定位问题。
        m_config = result.config;
        updateConfigSummary();

        m_toggleButton->setEnabled(false);
        m_uploadButton->setEnabled(false);

        QString html = QStringLiteral(
            "<b style='color:#b00020;'>配置不可用，修正文件后点击“重新加载配置”：</b><ul>");
        for (const QString& problem : m_configProblems) {
            html += QStringLiteral("<li>") + problem.toHtmlEscaped() + QStringLiteral("</li>");
        }
        html += QStringLiteral(
            "</ul><span style='color:#37474f;'>提示：把 config/collector.example.json "
            "复制为 collector.json 并填写真实采集凭据。</span>");
        m_configHintLabel->setText(html);

        appendLog(QStringLiteral("配置校验失败：%1")
                      .arg(m_configProblems.join(QStringLiteral("；"))),
                  kErrorColor);
        setConnectionState(QStringLiteral("配置无效，无法开始采集"), kErrorColor);
    }
}

void CollectorWindow::updateConfigSummary()
{
    m_configPathLabel->setText(QDir::toNativeSeparators(m_configPath));

    const QString baseUrl = m_config.baseUrl.isEmpty() ? QStringLiteral("（未配置）")
                                                      : m_config.baseUrl;
    m_serverLabel->setText(baseUrl + QStringLiteral("\n→ POST ")
                           + m_config.ingestUrl().toString());

    m_deviceLabel->setText(QStringLiteral("device_pk=%1 ｜ greenhouse_id=%2 ｜ 大棚编码=%3")
                               .arg(m_config.devicePk)
                               .arg(m_config.greenhouseId)
                               .arg(m_config.greenhouseCode.isEmpty()
                                        ? QStringLiteral("（未配置）")
                                        : m_config.greenhouseCode));

    m_metricsLabel->setText(joinMetricNames(m_config.metrics));

    const bool real = m_config.source == QStringLiteral("real");
    const QString sourceText = real ? QStringLiteral("真实采集（real）")
                                    : QStringLiteral("模拟采集（simulated）");
    m_sourceLabel->setText(sourceText);
    setWindowTitle(QStringLiteral("大棚环境数据采集客户端（") + sourceText + QStringLiteral("）"));
}

bool CollectorWindow::configurationReady() const
{
    return m_configProblems.isEmpty();
}

void CollectorWindow::startPeriodicCollection()
{
    if (!configurationReady()) {
        setConnectionState(QStringLiteral("配置无效，无法开始采集"), kErrorColor);
        return;
    }
    if (m_running) {
        return;
    }

    m_running = true;
    m_skipNoticeLogged = false;
    m_timer->start(qBound(500, m_intervalSpin->value(), 3600000));
    applyRunningState();
    setConnectionState(QStringLiteral("已开始：等待首次上传"), kNeutralColor);
    appendLog(QStringLiteral("开始定时采集：每 %1 ms 上传一批").arg(m_timer->interval()), kOkColor);

    // 立即先发一批，避免启动后先空等一个完整间隔。
    collectOnce();
}

void CollectorWindow::stopPeriodicCollection()
{
    if (!m_running) {
        return;
    }

    m_running = false;
    m_timer->stop();
    applyRunningState();

    if (m_client->isBusy()) {
        // 已经发出的请求不强行中断：中断让服务端看到“客户端主动放弃”，
        // 而这一批的 sample_key 已经用掉。让它自然完成，只是不再起新批次。
        setConnectionState(QStringLiteral("已暂停（当前批次仍在传输/等待重试）"), kWarnColor);
    } else {
        setConnectionState(QStringLiteral("已暂停定时采集"), kNeutralColor);
    }
    appendLog(QStringLiteral("已暂停定时采集"), kNeutralColor);
}

void CollectorWindow::applyRunningState()
{
    m_toggleButton->setText(m_running ? QStringLiteral("暂停定时上传")
                                      : QStringLiteral("开始定时上传"));
}

void CollectorWindow::collectOnce()
{
    if (!configurationReady()) {
        setConnectionState(QStringLiteral("配置无效，未发送请求"), kErrorColor);
        return;
    }

    if (m_client->isBusy()) {
        // 上一批还在路上或正在退避等待：跳过本次 tick，保证请求绝不重叠。
        setConnectionState(QStringLiteral("跳过本次采集：上一批尚未完成"), kWarnColor);
        if (!m_skipNoticeLogged) {
            appendLog(QStringLiteral("跳过本次采集：上一批请求尚未结束（退避等待也算忙碌）"),
                      kWarnColor);
            m_skipNoticeLogged = true;
        }
        return;
    }
    m_skipNoticeLogged = false;

    const QVector<collector::Sample> samples =
        m_generator->generate(m_config.metrics, m_config.greenhouseCode);
    if (samples.isEmpty()) {
        appendLog(QStringLiteral("未生成任何采样，请检查配置中的 metrics"), kErrorColor);
        return;
    }

    const quint64 batchId = m_nextBatchId;
    if (!m_client->submit(samples, batchId)) {
        setConnectionState(QStringLiteral("提交被拒绝：上一批尚未完成"), kWarnColor);
        return;
    }
    ++m_nextBatchId;

    QStringList descriptions;
    for (const collector::Sample& sample : samples) {
        descriptions.append(QStringLiteral("%1=%2%3")
                                .arg(collector::metricDisplayName(sample.metric))
                                .arg(collector::formatMetricValue(sample.metric, sample.value))
                                .arg(collector::metricUnit(sample.metric)));
    }
    appendLog(QStringLiteral("第 %1 批已提交：%2 条（%3）")
                  .arg(batchId)
                  .arg(static_cast<int>(samples.size()))
                  .arg(descriptions.join(QStringLiteral("，"))),
              kNeutralColor);
}

void CollectorWindow::onAttemptStarted(quint64 batchId, int attempt, int maxAttempts,
                                       int sampleCount)
{
    if (attempt <= 1) {
        setConnectionState(QStringLiteral("上传中：第 %1 批，第 1/%2 次尝试（%3 条）")
                               .arg(batchId)
                               .arg(maxAttempts)
                               .arg(sampleCount),
                           kNeutralColor);
    } else {
        setConnectionState(QStringLiteral("重试中：第 %1 批，第 %2/%3 次尝试（%4 条）")
                               .arg(batchId)
                               .arg(attempt)
                               .arg(maxAttempts)
                               .arg(sampleCount),
                           kWarnColor);
    }
}

void CollectorWindow::onAttemptFailed(quint64 batchId, int attempt, int maxAttempts,
                                      collector::ErrorKind kind, const QString& message,
                                      int nextDelayMs)
{
    const QString detail = QStringLiteral("第 %1 批第 %2/%3 次尝试失败（%4）：%5")
                               .arg(batchId)
                               .arg(attempt)
                               .arg(maxAttempts)
                               .arg(collector::errorKindName(kind))
                               .arg(message);

    m_lastErrorLabel->setText(detail);
    m_lastErrorLabel->setStyleSheet(QStringLiteral("color:#b00020;font-weight:600;"));
    setConnectionState(QStringLiteral("%1 ms 后自动重试（第 %2 次尝试）")
                           .arg(nextDelayMs)
                           .arg(attempt + 1),
                       kWarnColor);
    appendLog(detail + QStringLiteral("；%1 ms 后重试").arg(nextDelayMs), kWarnColor);
}

void CollectorWindow::handleBatchFinished(const collector::BatchReport& report)
{
    ++m_finishedBatches;
    m_totalAccepted += static_cast<quint64>(report.accepted);
    m_totalDuplicated += static_cast<quint64>(report.duplicated);
    m_totalRejected += static_cast<quint64>(report.rejected.size());

    const int maxAttempts = m_client->config().retry.maxRetries + 1;
    m_batchAcceptedLabel->setText(QString::number(report.accepted));
    m_batchDuplicatedLabel->setText(QString::number(report.duplicated));
    m_batchRejectedLabel->setText(QString::number(static_cast<int>(report.rejected.size())));
    m_attemptsLabel->setText(QStringLiteral("%1 / %2").arg(report.attempts).arg(maxAttempts));

    const QString pauseSuffix = m_running ? QString()
                                          : QStringLiteral("（定时采集已暂停）");

    if (report.isSuccess()) {
        setConnectionState(QStringLiteral("连接正常：第 %1 批上传成功%2")
                               .arg(report.batchId)
                               .arg(pauseSuffix),
                           kOkColor);
        appendLog(QStringLiteral("第 %1 批成功：accepted=%2，duplicated=%3，rejected=0，"
                                 "共尝试 %4 次")
                      .arg(report.batchId)
                      .arg(report.accepted)
                      .arg(report.duplicated)
                      .arg(report.attempts),
                  kOkColor);
    } else {
        // 失败必须显式高亮；没有响应、HTTP 异常、解析失败都不会被当成“已上传”。
        const QString errorName = collector::errorKindName(report.error);
        m_lastErrorLabel->setText(QStringLiteral("第 %1 批失败（%2）：%3")
                                      .arg(report.batchId)
                                      .arg(errorName)
                                      .arg(report.message));
        m_lastErrorLabel->setStyleSheet(QStringLiteral("color:#b00020;font-weight:600;"));

        setConnectionState(QStringLiteral("失败，等待下次上传：%1%2")
                               .arg(errorName)
                               .arg(pauseSuffix),
                           kErrorColor);
        appendLog(QStringLiteral("第 %1 批失败（%2）：%3")
                      .arg(report.batchId)
                      .arg(errorName)
                      .arg(report.message),
                  kErrorColor);

        for (const collector::Rejection& rejection : report.rejected) {
            appendLog(QStringLiteral("拒收 %1：%2")
                          .arg(rejection.sampleKey)
                          .arg(rejection.reason.isEmpty()
                                   ? QStringLiteral("（服务端未给出原因）")
                                   : rejection.reason),
                      kErrorColor);
        }
        if (report.attempts > 1) {
            appendLog(QStringLiteral("已尝试 %1 次，退避重试未成功").arg(report.attempts),
                      kErrorColor);
        }
    }

    updateTotalsUi();
}

void CollectorWindow::updateTotalsUi()
{
    m_totalLabel->setText(
        QStringLiteral("已结束 %1 批（成功/失败均计入） ｜ accepted 累计 %2 条 ｜ "
                       "duplicated 累计 %3 条 ｜ rejected 累计 %4 条")
            .arg(m_finishedBatches)
            .arg(m_totalAccepted)
            .arg(m_totalDuplicated)
            .arg(m_totalRejected));
}

void CollectorWindow::appendLog(const QString& message, const QString& color)
{
    // 只写进内存控件（最多 1000 行），项目约定不向仓库目录写日志文件。
    const QString timeText =
        collector::formatSampleTime(collector::currentShanghaiTime()).mid(11);
    const QString html = QStringLiteral("<span style='color:%1;'>[%2] %3</span>")
                             .arg(color.isEmpty() ? kNeutralColor : color)
                             .arg(timeText.toHtmlEscaped())
                             .arg(message.toHtmlEscaped());
    m_logView->appendHtml(html);
    // 追加后滚动到底部，保证最新一条始终可见。
    m_logView->verticalScrollBar()->setValue(m_logView->verticalScrollBar()->maximum());
}

void CollectorWindow::setConnectionState(const QString& text, const QString& color)
{
    m_stateLabel->setStyleSheet(QStringLiteral("font-weight:600;color:%1;").arg(color));
    m_stateLabel->setText(text);
}
