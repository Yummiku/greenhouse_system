-- XM06 农业大棚系统 第13周 环境采样演示数据（仅开发/演示库执行）
-- 前置：01、09、11、12 已执行；演示大棚（默认 GH-DEMO-01 / GH-DEMO-02）存在且 status=enabled，
--       五类指标各自有已绑定、未删除的 sensor 设备；缺一项就跳过该棚，不报错也不清数据。
--
-- 第13周整改（原脚本用 NOW() 拼唯一键，隔几秒重跑就会多出一批）：
--   1) 批次号 @demo_batch 与采样键都是固定字符串，不使用运行时刻生成唯一键；
--      同一批重复执行只跳过、不新增记录；
--   2) 需要新的一批演示数据时，显式把 @demo_batch 改成下一个批次号（例如 'w13-b2'）再执行，
--      这是唯一产生新数据的方式，不会悄悄按秒数造批次；
--   3) 数据来源固定 simulated（模拟），不是真实硬件采集，汇报和截图不得写成实采；
--   4) 脚本不 DROP、不清空、不改历史；采样键前缀 demo: 便于识别与清理演示数据。
SET NAMES utf8mb4;
USE greenhouse_system;

SET @demo_batch := 'w13-b1';

-- 每个大棚 × 每台已绑定采样设备 × 最近 10 分钟（每分钟一条），值随分钟稳定变化。
INSERT INTO `environment_sample`
  (`sample_key`, `greenhouse_id`, `device_pk`, `metric_type`, `value`, `unit`,
   `sample_time`, `source`)
SELECT
  CONCAT('demo:', @demo_batch, ':', g.greenhouse_code, ':', b.metric_type, ':', s.step),
  g.id,
  b.device_pk,
  b.metric_type,
  ROUND(CASE b.metric_type
    WHEN 'air_temperature'  THEN 22.0 + s.step * 0.30
    WHEN 'air_humidity'     THEN 60.0 - s.step * 1.20
    WHEN 'soil_temperature' THEN 18.5 + s.step * 0.15
    WHEN 'soil_moisture'    THEN 45.0 + s.step * 0.80
    ELSE 8000 + s.step * 650
  END, 2),
  b.unit,
  NOW() - INTERVAL (9 - s.step) MINUTE,
  'simulated'
FROM `greenhouse_file` g
JOIN `greenhouse_device_binding` b ON b.greenhouse_id = g.id
JOIN `device_file` d ON d.id = b.device_pk
CROSS JOIN (
  SELECT 0 AS step UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
  UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7
  UNION ALL SELECT 8 UNION ALL SELECT 9
) s
WHERE g.is_deleted = 0
  AND g.status = 'enabled'
  AND g.greenhouse_code IN ('GH-DEMO-01', 'GH-DEMO-02')
  AND (d.device_type IS NULL OR d.device_type <> 'control')
  AND NOT EXISTS (
    SELECT 1 FROM `environment_sample` e
    WHERE e.sample_key = CONCAT('demo:', @demo_batch, ':', g.greenhouse_code, ':', b.metric_type, ':', s.step)
  );

-- 跳过原因说明：大棚被停用/删除、或没有绑定采样设备时，上面的 INSERT 不会写入，这里逐棚列出。
SELECT g.greenhouse_code,
       g.name,
       g.status,
       COUNT(b.device_pk) AS bound_device_count,
       CASE
         WHEN g.is_deleted = 1 THEN '已删除，跳过'
         WHEN g.status <> 'enabled' THEN '大棚停用，跳过'
         WHEN COUNT(b.device_pk) = 0 THEN '没有绑定采样设备，跳过'
         ELSE '可写入演示数据'
       END AS demo_readiness
FROM `greenhouse_file` g
LEFT JOIN `greenhouse_device_binding` b ON b.greenhouse_id = g.id
WHERE g.greenhouse_code IN ('GH-DEMO-01', 'GH-DEMO-02')
GROUP BY g.greenhouse_code, g.name, g.status, g.is_deleted;

-- 本批次结果：重复执行时 batch_samples 不变，不会因为重跑而增长。
SELECT @demo_batch AS demo_batch,
       COUNT(*) AS batch_samples,
       SUM(source = 'simulated') AS simulated_samples,
       COUNT(DISTINCT metric_type) AS metric_kinds,
       MIN(sample_time) AS first_time,
       MAX(sample_time) AS last_time
FROM `environment_sample`
WHERE sample_key LIKE CONCAT('demo:', @demo_batch, ':%');
