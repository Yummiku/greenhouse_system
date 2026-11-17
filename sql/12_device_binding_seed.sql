-- 仅开发库执行；先运行11、账号工具。无账号时不会插入，须先检查。
USE greenhouse_system;
INSERT INTO greenhouse_file(greenhouse_code,name,location,area,length,width,manager_id,status)
SELECT 'GH-DEMO-01','一号演示棚','示范园东区',600,30,20,id,'enabled' FROM todo_login_user
WHERE username='admin' AND NOT EXISTS(SELECT 1 FROM greenhouse_file WHERE greenhouse_code='GH-DEMO-01');
INSERT INTO greenhouse_file(greenhouse_code,name,location,area,length,width,manager_id,status)
SELECT 'GH-DEMO-02','二号演示棚','示范园西区',480,24,20,id,'enabled' FROM todo_login_user
WHERE username='admin' AND NOT EXISTS(SELECT 1 FROM greenhouse_file WHERE greenhouse_code='GH-DEMO-02');
-- 只生成明确标注的模拟设备，不宣称真实采集。
INSERT INTO device_file(device_id,device_name,device_type,create_time)
SELECT CONCAT(g.greenhouse_code,'-',m.metric),CONCAT(g.name,'模拟',m.metric),'sensor',NOW()
FROM greenhouse_file g CROSS JOIN (
 SELECT 'air_temperature' metric UNION ALL SELECT 'air_humidity' UNION ALL SELECT 'soil_temperature' UNION ALL SELECT 'soil_moisture' UNION ALL SELECT 'illuminance'
) m WHERE g.greenhouse_code IN ('GH-DEMO-01','GH-DEMO-02')
AND NOT EXISTS(SELECT 1 FROM device_file d WHERE d.device_id=CONCAT(g.greenhouse_code,'-',m.metric));
INSERT INTO greenhouse_device_binding(device_pk,greenhouse_id,metric_type,unit)
SELECT d.id,g.id,SUBSTRING(d.device_id,LENGTH(g.greenhouse_code)+2),
CASE WHEN d.device_id LIKE '%temperature' THEN '℃' WHEN d.device_id LIKE '%illuminance' THEN 'lx' ELSE '%' END
FROM greenhouse_file g JOIN device_file d ON d.device_id LIKE CONCAT(g.greenhouse_code,'-%')
WHERE g.greenhouse_code IN ('GH-DEMO-01','GH-DEMO-02')
AND NOT EXISTS(SELECT 1 FROM greenhouse_device_binding b WHERE b.device_pk=d.id);
SELECT g.id greenhouse_id,g.greenhouse_code,d.id device_pk,d.device_id device_code,b.metric_type,b.unit
FROM greenhouse_device_binding b JOIN greenhouse_file g ON g.id=b.greenhouse_id JOIN device_file d ON d.id=b.device_pk;
