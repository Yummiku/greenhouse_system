# w12-qt — 大棚环境数据采集上传客户端

面向 Java Web 环境监测平台的**最小可用**桌面采集端（Qt 6 / C++17 / QtWidgets + QtNetwork）。
一个采集端固定对应 **一个大棚 + 一个设备 + 该设备绑定的一个指标**，定时产生这**一个**指标的采样，按约定接口 `POST` 到服务端，
并在界面上给出逐批结果、累计条数、连接状态和失败原因。

> ## ⚠ 数据来源声明
>
> **本客户端的数据来源是“模拟源”，不是真实硬件采集。**
> 所有数值都由 [`SampleGenerator`](src/SampleGenerator.cpp) 在本地用随机游走生成，
> 上传 body 里的 `"source"` 由配置决定，默认 `"simulated"`；接入真实传感器后才改成 `"real"`。
> 真实传感器（串口 / Modbus / 网关）接入尚未实现，代码中用 `TODO(真实硬件)` 标注。

---

## 功能

- 定时上传（`QTimer`，间隔可配置，默认 2000 ms），可暂停 / 恢复，也可“立即上传一次”。
- **一台设备只采一个指标**（第13周三人约定）：`metrics` 必须恰好写设备绑定的那一个，
  可选 `air_temperature`、`air_humidity`、`soil_temperature`、`soil_moisture`、`illuminance`；
  五类指标要展示时，用五台分别绑定的设备各跑一个实例。
- `sample_key` 由客户端保证唯一：`<greenhouse_code>:<metric_type>:<yyyyMMddHHmmss>`；
  同一批内同一指标不出现在同一秒，跨批次也不会重复；**重试时复用同一份 payload**，
  让服务端按唯一键去重。
- 响应解析：显示每批 `accepted / duplicated / rejected`，`rejected` 逐条显示
  `sample_key + reason`，同时维护本次运行的累计条数。
- 失败退避重试：默认 1s / 2s / 4s，最多 3 次；仍失败显示“失败，等待下次上传”。
- 错误分类提示：网络失败、请求超时、HTTP 非 200、JSON 解析失败、服务端拒绝互不混淆；
  **绝不会把“没有响应”当作成功**。
- 网络请求严格不重叠：上一批（含退避等待）未结束时，本次 tick 直接跳过并提示。
- 配置校验提示：缺字段、类型错误、示例占位凭据、非法地址等都会在界面上列出，并禁用上传按钮。

## 目录结构

```
w12-qt/
├─ CMakeLists.txt
├─ README.md
├─ .gitignore
├─ config/
│  └─ collector.example.json      # 配置示例（占位凭据，不能直接使用）
└─ src/
   ├─ main.cpp                    # 入口：解析 --config，创建窗口
   ├─ CollectorTypes.h/.cpp       # 指标枚举、错误分类、Sample/BatchReport、时间与精度约定
   ├─ CollectorConfig.h/.cpp      # JSON 配置加载 + 校验
   ├─ SampleGenerator.h/.cpp      # 模拟数据源（TODO：真实硬件）
   ├─ CollectorClient.h/.cpp      # HTTP 上传、响应解析、退避重试状态机
   └─ CollectorWindow.h/.cpp      # 纯代码布局界面
```

> 任务书的建议结构之外增加了 `CollectorTypes` / `CollectorConfig` 两个文件：
> 错误码与枚举集中定义、配置解析与界面解耦，避免所有逻辑堆在窗口类里。

## 环境要求

- Qt **6.5 及以上**（需要 `Core`、`Gui`、`Widgets`、`Network` 模块）
- CMake 3.21+
- C++17 编译器：Windows 上 MSVC 2019/2022 或 MinGW 11+
- 无第三方依赖；不使用 `.ui` 文件（纯代码布局）

## 构建（Windows + Qt 6）

### 方式一：MSVC（推荐）

```powershell
cd E:\CGhomework\github\w12-qt
cmake -S . -B build -G "Visual Studio 17 2022" -A x64 `
      -DCMAKE_PREFIX_PATH="C:/Qt/6.8.0/msvc2022_64"
cmake --build build --config Release
```

### 方式二：MinGW

```powershell
$env:PATH = "C:\Qt\6.8.0\mingw_64\bin;C:\Qt\Tools\mingw1310_64\bin;" + $env:PATH
cd E:\CGhomework\github\w12-qt
cmake -S . -B build-mingw -G "MinGW Makefiles" `
      -DCMAKE_PREFIX_PATH="C:/Qt/6.8.0/mingw_64"
cmake --build build-mingw -j
```

### 可选：打包可运行目录

```powershell
cmake --install build --config Release --prefix dist
```

安装步骤会调用 Qt 的部署脚本（Windows 上等价于 `windeployqt`），`dist\bin\w12-qt.exe` 可独立运行。
也可以直接用 Qt Creator 打开 `CMakeLists.txt` 构建/运行。

> 上面的 `6.8.0`、`msvc2022_64`、`mingw1310_64` 只是示例，请替换为本机实际的 Qt 版本和套件目录名；
> `CMAKE_PREFIX_PATH` 应指向包含 `lib/cmake/Qt6` 的那个目录。

## 运行

```powershell
# 方式 A：显式指定配置
.\build\Release\w12-qt.exe --config .\config\collector.json

# 方式 B：把配置放到 exe 同级目录后直接运行
Copy-Item .\config\collector.example.json .\build\Release\collector.json
notepad .\build\Release\collector.json        # 填入真实 base_url / collector_key 等
.\build\Release\w12-qt.exe
```

配置查找顺序：

1. `--config <path>`（`-c` 也可）；
2. exe 同级目录的 `collector.json`；
3. exe 同级目录的 `collector.example.json`（CMake 构建时自动复制）。

> 示例配置里的 `collector_key` 是占位值 `REPLACE_WITH_REAL_COLLECTOR_KEY`，
> 校验会直接判定为不可用——这是故意的：避免拿假凭据去请求服务端然后盯着 401 排查。

## 配置说明

`config/collector.example.json`：

| 字段 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `base_url` | 是 | — | 服务端根地址**含应用上下文**，如 `http://127.0.0.1:9999/greenhouse_system`，只支持 http/https |
| `collector_key` | 是 | — | 采集凭据，放进请求头 `X-Collector-Key`；**只从配置读取，不写死在代码里** |
| `greenhouse_id` | 是 | — | 大棚主键，固定值，随每批上传 |
| `greenhouse_code` | 是 | — | 大棚编码，`sample_key` 前缀；不能含冒号和空格 |
| `device_pk` | 是 | — | 设备主键，固定值，随每批上传 |
| `source` | 否 | `simulated` | 数据来源：`simulated` 模拟采集 / `real` 真实采集 |
| `metrics` | 是 | — | 只能有 **一个** 指标，必须等于该 `device_pk` 绑定的指标；多写或少写都会配置校验失败 |
| `upload_interval_ms` | 否 | `2000` | 定时上传间隔（毫秒），范围 500~3600000 |
| `retry.max_retries` | 否 | `3` | 首次失败后的最大重试次数，范围 0~10 |
| `retry.initial_delay_ms` | 否 | `1000` | 首次重试延迟，之后按倍数递增 |
| `retry.backoff_multiplier` | 否 | `2` | 退避倍数（默认得到 1s / 2s / 4s） |
| `retry.request_timeout_ms` | 否 | `10000` | 单次 HTTP 传输超时 |

## 上传协议

```
POST {base_url}/api/environment/ingest
Content-Type: application/json
X-Collector-Key: <collector_key>
```

请求体示例：

```json
{
  "device_pk": 12,
  "greenhouse_id": 3,
  "source": "simulated",
  "samples": [
    {
      "sample_key": "GH-A-001:air_temperature:20261117224500",
      "metric_type": "air_temperature",
      "value": 23.4,
      "sample_time": "2026-11-17 22:45:00"
    }
  ]
}
```

- `source` 只允许 `simulated` 或 `real`；本客户端目前仍是模拟源，接真实传感器后才写 `real`。
- 服务端会逐条核对样本 `metric_type` 与设备绑定是否一致，不一致整条拒绝并给出原因，不会静默改写指标。
- 时间统一用 **Asia/Shanghai** 本地时间，格式 `yyyy-MM-dd HH:mm:ss`。
  客户端固定使用 UTC+08:00 计算（上海无夏令时），不依赖运行机器的系统时区。
- 数值精度：温度 / 湿度保留 1 位小数，光照 `lx` 取整数。
- `sample_key` 的秒级时间戳与 `sample_time` 来自同一个瞬间；
  若同一秒内被连续触发两次采集，客户端会把时间顺延 1 秒，避免自己制造重复键（误差可忽略）。

响应体：

```json
{
  "result_code": 0,
  "result_msg": "ok",
  "data": {
    "accepted": 5,
    "duplicated": 0,
    "rejected": [
      { "sample_key": "GH-A-001:air_temperature:20261117224500", "reason": "value out of range" }
    ]
  }
}
```

判定规则：

- `result_code != 0` → 失败（服务端拒绝），界面红色高亮 `result_msg`；
- `data.rejected` 非空 → 失败（部分拒收），逐条显示 `sample_key` 与 `reason`；
- 只有 HTTP 200 + 合法 JSON + `result_code == 0` + `rejected` 为空才算成功。

## 重试与错误分类

| 场景 | 内部错误类型 | 界面提示 | 是否重试 |
| --- | --- | --- | --- |
| 连接失败 / DNS 失败 / TLS 失败 | `NetworkError` | 网络失败：… | 是 |
| 传输超时 | `Timeout` | 请求超时：… | 是 |
| HTTP 非 200 | `HttpError` | HTTP 401：…（附响应片段） | 5xx / 408 / 429 重试；其余 4xx 不重试 |
| HTTP 200 但响应不是合法 JSON | `JsonParseError` | 响应解析失败：… | 否 |
| JSON 合法但字段不符 / `result_code != 0` / `rejected` 非空 | `ProtocolError` | 服务端拒绝：… | 否 |
| 本地配置不完整 | `InvalidConfig` | 配置无效，无法开始采集 | 不发送 |

重试策略：`max_retries = 3` 时，一批最多 4 次请求（首次 + 3 次重试），
间隔依次 `initial_delay_ms × multiplier^n`，默认 1s / 2s / 4s，上限 5 分钟。
重试期间**不重新生成采样**，发送的 body 与首次完全一致，`sample_key` 不变。
重试耗尽后状态显示“失败，等待下次上传”，等待下一个定时 tick。

设计取舍：为什么 4xx 不重试——凭据错误、参数被服务端拒绝时，重复发送同一份数据
只会得到同样的结果，还会把界面拖进 7 秒的无意义等待；把重试留给“可能恢复”的故障
（网络、超时、5xx、限流）更符合实际运维经验。

## 界面说明

- **① 运行配置**：只读展示配置文件路径、上传地址（含应用上下文）、设备/大棚、指标、采集来源、间隔；配置有问题时
  红色列出全部原因，并禁用上传按钮。
- **② 采集控制**：开始 / 暂停定时上传、立即上传一次；修改“上传间隔”会立即作用于运行中的定时器。
- **③ 本批结果**：`accepted / duplicated / rejected`、尝试次数、最近一次错误（红色高亮）。
- **④ 累计统计**：本次运行已结束的批次数（成功/失败均计入）与三类累计条数。
- **⑤ 事件日志**：带时间戳的过程记录（成功绿色、重试橙色、失败红色），
  **只保存在内存中（最多 1000 行），不向仓库目录写任何日志文件**。

## 安全与约束

- 采集凭据只来自配置文件，代码里没有任何硬编码密钥；`.gitignore` 忽略了
  `collector.json`，避免把真实凭据提交进仓库。
- 凭据通过请求头传递，不拼进 URL；日志只记录错误信息与响应片段，不打印凭据。
- 客户端不写磁盘日志，不做本地持久化。

## 未验证事项与已知限制

1. **未编译、未运行**：本次没有在目标机器上完成构建验证，代码按 Qt 6.5+ API 编写并做了静态自查，
   因此 `cmake` 构建结果与界面实际表现仍需要在装有 Qt 6 的机器上确认。
2. **服务端接口尚未实现**：当前 Java Web 项目的 `/api/environment` 只有 GET `action=ping`
   检查入口，`/api/environment/ingest` 的业务实现待补；本客户端按任务书给出的契约实现，
   联调前请确认服务端字段名与响应结构一致。
3. **真实硬件未接入**：所有数据由 `SampleGenerator` 模拟，`source` 默认 `simulated`；界面和上传 body 都按配置标注来源，汇报时不得写成实采。
4. **真实采集凭据未配置**：示例配置只有占位值，`collector_key` 必须替换为服务端下发的凭据。
5. HTTPS 走 Qt 默认的 TLS 后端，证书/代理环境未专门验证。
