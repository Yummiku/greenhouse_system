#include "CollectorWindow.h"

#include <QApplication>
#include <QCommandLineOption>
#include <QCommandLineParser>
#include <QDebug>
#include <QDir>
#include <QFileInfo>
#include <QStringList>

namespace {

// 配置查找顺序：
//   1. 命令行 --config 指定的路径（最高优先级，便于测试多份配置）；
//   2. exe 同级目录的 collector.json（放真实凭据，已被 .gitignore 忽略）；
//   3. exe 同级目录的 collector.example.json（CMake 构建时复制过去的示例）。
// 都没有时返回第一个候选路径，让加载逻辑给出“找不到配置文件”的明确提示。
QString resolveConfigPath(const QString& cliPath)
{
    if (!cliPath.trimmed().isEmpty()) {
        return cliPath;
    }

    const QDir appDir(QCoreApplication::applicationDirPath());
    const QStringList candidates = {
        appDir.filePath(QStringLiteral("collector.json")),
        appDir.filePath(QStringLiteral("collector.example.json")),
    };
    for (const QString& candidate : candidates) {
        if (QFileInfo::exists(candidate)) {
            return candidate;
        }
    }
    return candidates.first();
}

}  // namespace

int main(int argc, char* argv[])
{
    QApplication app(argc, argv);
    QApplication::setApplicationName(QStringLiteral("w12-qt-collector"));
    QApplication::setApplicationVersion(QStringLiteral("0.1.0"));
    QApplication::setOrganizationName(QStringLiteral("w12-coursework"));

    QCommandLineParser parser;
    parser.setApplicationDescription(
        QStringLiteral("大棚环境数据采集上传客户端（Qt 6，模拟源；非真实硬件采集）"));
    parser.addHelpOption();
    parser.addVersionOption();

    QCommandLineOption configOption(
        {QStringLiteral("c"), QStringLiteral("config")},
        QStringLiteral("JSON 配置文件路径；缺省时依次查找可执行文件目录下的 "
                       "collector.json、collector.example.json。"),
        QStringLiteral("path"));
    parser.addOption(configOption);
    parser.process(app);

    const QString configPath = resolveConfigPath(parser.value(configOption));
    qInfo().noquote() << QStringLiteral("使用配置文件:") << QDir::toNativeSeparators(configPath);

    CollectorWindow window(configPath);
    window.show();

    return app.exec();
}
