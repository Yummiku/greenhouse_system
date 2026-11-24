# 环境采样模块接口契约（第13周 / 唐昕煜）

供组长复测、肖泽亮集成驾驶舱与小程序侧只读查看使用。所有地址以部署方实际应用上下文为准，
例如本机 `http://localhost:9999/greenhouse_system`，验收环境 `{context}/greenhouse_system_test`。

## 通用约定

- 响应统一 `{"result_code": 0, "result_msg": "成功", "data": ...}`；`result_code` 非 0 表示失败，前端只用 `result_msg` 展示。
- 状态码：`200` 成功 · `400` 参数或业务不合法 · `401` 未登录/采集凭据缺失或错误 · `403` 角色无权 ·
  `405` 维护用了 GET · `409` 数据冲突 · `503` 未配置采集凭据 · `500` 数据库或服务异常。
- 页面接口用登录 Session（`loginUser`），角色 1 系统管理员 / 2 普通管理员可维护人工记录，角色 3 只读。
- 采集上传只认请求头 `X-Collector-Key`，不借用登录会话。

## 一 页面与驾驶舱（GET `/api/environment`）

| action | 关键参数 | data 主要字段 |
| --- | --- | --- |
| `context` | — | `role`、`can_manage`、`metrics[]`（code/label/unit/min/max/range）、`greenhouses[]`、`devices[]`、`operator`、`server_time` |
| `list` | `greenhouse_id`、`device_pk`、`metric_type`、`source`、`from`、`to`、`sort`、`dir`、`page`、`size` | `total`、`page`、`size`、`sort`、`dir`、`items[]` |
| `detail` | `id` | `sample`（含 `sample_key`、`created_by_name`、`updated_at` 等）、`revisions[]` |
| `latest` | `greenhouse_id`、`device_pk?` | `cards[]`（每指标 `label`、`unit`、`has_data`、`sample`、`age_seconds`、`stale`）、`server_time` |
| `stats` | 同 `list` 的筛选 | `empty`、`summary[]`（`total`/`avg_value`/`min_value`/`max_value`/`first_time`/`last_time`）、`sources[]` |
| `trend` | 筛选 + `metric_type`、`points` | `metric_type`、`label`、`unit`、`points[]`（`sample_time`/`value`/`unit`/`source`，时间正序） |
| `report` | 筛选 + `sort`/`dir` | `items[]`、`total`、`truncated` |
| `export` | 筛选 + `sort`/`dir` | `text/csv` 附件，UTF-8 BOM，全量条件一致 |
| `dashboard` | `greenhouse_id`、`metric_type`（默认 `air_temperature`）、`days`（默认 7，1~90） | 见下 |

`list` 的 `items[]` 每条含：`id`、`sample_key`、`greenhouse_id`、`greenhouse_code`、`greenhouse_name`、
`device_pk`、`device_code`、`metric_type`、`value`、`unit`、`sample_time`、`received_at`、`source`、
`created_by_name`。

### dashboard 响应示例

```json
{
  "result_code": 0, "result_msg": "成功",
  "data": {
    "greenhouse_id": 1, "greenhouse_code": "GH-DEMO-01", "greenhouse_name": "一号演示棚",
    "greenhouse_status": "enabled",
    "metric_type": "air_temperature", "metric_label": "空气温度", "unit": "℃",
    "days": 7, "from": "2026-11-18 00:00:00", "to": "2026-11-24 21:05:00",
    "empty": false,
    "total": 3, "avg_value": 20.0, "min_value": 0, "max_value": 40,
    "first_time": "2026-11-24 20:50:00", "last_time": "2026-11-24 21:00:00",
    "sources": [{"source": "simulated", "total": 3}],
    "trend": [{"sample_time": "2026-11-24 20:50:00", "value": 0, "unit": "℃", "source": "simulated"}],
    "latest": {"metric_type": "air_temperature", "label": "空气温度", "unit": "℃", "has_data": true,
               "age_seconds": 30, "stale": true,
               "sample": {"id": 12, "value": 40, "unit": "℃", "sample_time": "2026-11-24 21:00:00",
                          "source": "simulated", "device_code": "GH-DEMO-01-air_temperature"}},
    "server_time": "2026-11-24 21:05:00"
  }
}
```

口径：按 `sample_time` 统计；默认最近 7 个自然日（含今天），起日 `00:00` 到结束日次日 `00:00` 之前；
数量/均值/最小/最大只用该 `metric_type` 的行，不同单位不混算；`0` 是有效数值，`empty=true` 才是无数据。
驾驶舱页面每 30 秒刷新已应用条件，不与上一请求重叠；失败保留上次数值并提示上次成功时间。

## 二 人工记录维护（POST `/api/environment`）

| action | 参数 | 说明 |
| --- | --- | --- |
| `create` | `greenhouse_id`、`device_pk`、`metric_type`、`value`、`sample_time` | 来源固定 `manual`；指标必须与设备绑定一致；值范围按指标校验，最多三位小数 |
| `update` | `id`、`value`、`sample_time`、`reason` | 只允许 `source=manual`；`reason` 必填（≤255），写入更正留痕 |
| `delete` | `id`、`reason`（**可省略**） | 只允许 `source=manual`，逻辑删除并记录操作人与时间 |

角色 3 调维护接口返回 403；实采/模拟记录不可改删。

## 三 采集上传（POST `/api/environment/ingest`）

请求头：`X-Collector-Key: <本机配置的采集凭据>`（未配置返回 503，凭据错误 401）。

```json
{
  "greenhouse_id": 1,
  "device_pk": 2,
  "source": "simulated",
  "samples": [
    {"sample_key": "GH-DEMO-01:air_temperature:20261124210000",
     "metric_type": "air_temperature",
     "value": 36,
     "sample_time": "2026-11-24 21:00:00"}
  ]
}
```

服务端逐条规则：

1. `source` 只允许 `real` / `simulated`（人工来源只能在页面录入）。
2. 大棚必须存在、未删除且 `status=enabled`；设备必须已绑定该棚且 `device_type` 不是 `control`。
3. **每条样本的 `metric_type` 必须等于该设备绑定指标**，不一致整条拒绝并说明设备只接受哪个指标，不再静默改写。
4. `sample_key` 幂等：同键同内容（含 `source`）算 `duplicated` 不重复入库；同键异内容拒绝覆盖；
   并发唯一键冲突会重读原记录再比较，其他数据库约束失败按真实失败上报。

响应（成功样例）：

```json
{
  "result_code": 0, "result_msg": "已处理",
  "data": {
    "accepted": 1, "duplicated": 0, "rejected": 0, "alarm_ready": false,
    "items": [
      {"input_index": 0, "sample_key": "GH-DEMO-01:air_temperature:20261124210000",
       "sample_id": 21, "ingest_status": "accepted", "alarm_status": "NOT_READY"}
    ]
  }
}
```

- `ingest_status`：`accepted` / `duplicated` / `rejected`；被拒绝的条目带 `reason`，`sample_id` 为 `null`。
- `alarm_status`：报警判定结果（`NORMAL` / `CREATED` / `UPDATED` / `RECOVERED` / `NO_EFFECTIVE_RULE` /
  `OUT_OF_ORDER` / `HISTORICAL_SAMPLE` 等），报警模块未部署时为 `NOT_READY`，处理器未就绪或抛错时为 `RETRY_SCHEDULED`。
- 整批全部未入库时返回 `400`，`data` 仍带逐条原因，便于采集端展示，不把拒绝当成功。

## 四 已知未落实项

- 本模块维护接口尚未接入统一的 `csrf_token` 校验（第12周 PR 已说明），建议与戴锦晨的公共令牌机制统一处理；
  当前依赖登录会话 + 后台角色校验。
- 驾驶舱菜单入口未合入，页面路径可用但需戴统一加菜单。
- 小程序侧复用 `latest` / `list` / `trend` / `dashboard` 的认证与权限仍在现有微信入口方案内讨论，本期不改认证。
