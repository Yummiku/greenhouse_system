-- XM06 农业大棚系统 第12周 环境采样 演示数据（仅开发/演示库执行）
-- 前置：01、09、11、12 已执行，且存在 12 生成的演示大棚与设备归属。
-- 数据来源明确标记为 simulated（模拟），不是真实硬件采集；不得在汇报中说成实采数据。
-- 脚本可重复执行：sample_key 由大棚编号 + 指标 + 时间戳组成，重复执行不会产生重复行。
SET NAMES utf8mb4;
USE greenhouse_system;

-- 每个大棚 × 五类指标 × 最近 10 分钟（每分钟一条）。
INSERT INTO `environment_sample`
  (`sample_key`, `greenhouse_id`, `device_pk`, `metric_type`, `value`, `unit`,
   `sample_time`, `source`)
SELECT
  CONCAT(g.greenhouse_code, ':', n.metric_type, ':',
         DATE_FORMAT(NOW() - INTERVAL (9 - n.step) MINUTE, '%Y%m%d%H%i%s')),
  g.id,
  n.device_pk,
  n.metric_type,
  ROUND(CASE n.metric_type
    WHEN 'air_temperature'  THEN 22.0 + n.step * 0.30
    WHEN 'air_humidity'     THEN 60.0 - n.step * 1.20
    WHEN 'soil_temperature' THEN 18.5 + n.step * 0.15
    WHEN 'soil_moisture'    THEN 45.0 + n.step * 0.80
    ELSE 8000 + n.step * 650
  END, 2),
  n.unit,
  NOW() - INTERVAL (9 - n.step) MINUTE,
  'simulated'
FROM `greenhouse_file` g
JOIN (
  SELECT b.greenhouse_id, b.device_pk, b.metric_type, b.unit, s.step
  FROM `greenhouse_device_binding` b
  CROSS JOIN (
    SELECT 0 AS step UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
    UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7
    UNION ALL SELECT 8 UNION ALL SELECT 9
  ) s
) n ON n.greenhouse_id = g.id
WHERE g.is_deleted = 0
  AND g.greenhouse_code IN ('GH-DEMO-01', 'GH-DEMO-02')
  AND NOT EXISTS (
    SELECT 1 FROM `environment_sample` e
    WHERE e.sample_key = CONCAT(g.greenhouse_code, ':', n.metric_type, ':',
          DATE_FORMAT(NOW() - INTERVAL (9 - n.step) MINUTE, '%Y%m%d%H%i%s'))
  );

SELECT COUNT(*) AS total_samples,
       SUM(source = 'simulated') AS simulated_samples,
       COUNT(DISTINCT metric_type) AS metric_kinds
FROM `environment_sample`
WHERE is_deleted = 0;
