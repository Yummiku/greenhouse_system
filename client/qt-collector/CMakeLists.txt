cmake_minimum_required(VERSION 3.21)

project(w12-qt VERSION 0.1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# qt_standard_project_setup / qt_generate_deploy_app_script 自 Qt 6.3 起提供；
# 这里把最低版本定为 6.5 LTS，兼顾第三方机器上的常见安装版本。
find_package(Qt6 6.5 REQUIRED COMPONENTS Core Gui Widgets Network)

# 纯代码布局：没有 .ui 文件，AUTOUIC 不会产生任何东西；
# 打开 AUTOMOC 处理带 Q_OBJECT 的头文件。
qt_standard_project_setup()

qt_add_executable(w12_qt
    src/main.cpp
    src/CollectorTypes.h
    src/CollectorTypes.cpp
    src/CollectorConfig.h
    src/CollectorConfig.cpp
    src/SampleGenerator.h
    src/SampleGenerator.cpp
    src/CollectorClient.h
    src/CollectorClient.cpp
    src/CollectorWindow.h
    src/CollectorWindow.cpp
)

target_include_directories(w12_qt PRIVATE src)
target_link_libraries(w12_qt PRIVATE
    Qt6::Core
    Qt6::Gui
    Qt6::Widgets
    Qt6::Network
)

if(MSVC)
    # 源码含中文字符串字面量，必须显式声明 UTF-8，否则 MSVC 会按本地代码页解析。
    target_compile_options(w12_qt PRIVATE /W4 /utf-8 /permissive-)
else()
    target_compile_options(w12_qt PRIVATE -Wall -Wextra -Wpedantic)
endif()

# 默认保留控制台，便于观察 qInfo 启动日志；
# 如果希望双击运行时不出现黑色控制台窗口，把 WIN32_EXECUTABLE 改为 TRUE。
set_target_properties(w12_qt PROPERTIES
    OUTPUT_NAME "w12-qt"
    WIN32_EXECUTABLE FALSE
)

# 默认启动时会在 exe 同级目录查找 collector.json / collector.example.json，
# 这里把示例配置复制过去，首次运行就能看到“请填写凭据”的配置校验提示。
add_custom_command(TARGET w12_qt POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
        "${CMAKE_CURRENT_SOURCE_DIR}/config/collector.example.json"
        "$<TARGET_FILE_DIR:w12_qt>/collector.example.json"
    COMMENT "复制 collector.example.json 到构建输出目录"
)

include(GNUInstallDirs)
install(TARGETS w12_qt
    BUNDLE DESTINATION .
    RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR}
    LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
)

# Windows 上等价于 windeployqt：把 Qt 运行时 DLL 和插件一起装到安装目录。
qt_generate_deploy_app_script(
    TARGET w12_qt
    OUTPUT_SCRIPT deploy_script
    NO_UNSUPPORTED_PLATFORM_ERROR
)
install(SCRIPT ${deploy_script})
