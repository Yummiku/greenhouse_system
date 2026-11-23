-- 将大棚档案调整为纯导航分类，档案列表作为其子菜单；可重复执行。
-- 不改账号及业务记录，保留三个扩展页面入口。
USE greenhouse_system;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
INSERT INTO menu_tree(item_id,parent_id,module_id,item_name,item_url) VALUES
(200,0,'greenhouse','农业管理',''),
(202,200,'greenhouse','大棚档案','')
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),item_name=VALUES(item_name),item_url=VALUES(item_url);
INSERT INTO menu_tree(item_id,parent_id,module_id,item_name,item_url) VALUES
(2020,202,'greenhouse','档案列表','/maintain/greenhouse/index.jsp'),
(2021,202,'greenhouse','大棚位置地图','/maintain/greenhouse/map.jsp'),
(2022,202,'greenhouse','设备归属管理','/maintain/greenhouse/devices.jsp'),
(2023,202,'greenhouse','种植批次管理','/maintain/greenhouse/batches.jsp')
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),item_name=VALUES(item_name),item_url=VALUES(item_url);
INSERT IGNORE INTO menu_view(role_id,item_id) VALUES
(1,200),(1,202),(1,2020),(1,2021),(1,2022),(1,2023),
(2,200),(2,202),(2,2020),(2,2021),(2,2022),(2,2023),
(3,200),(3,202),(3,2020),(3,2021),(3,2022),(3,2023);
SELECT t.item_id,t.parent_id,t.item_name,t.item_url,v.role_id
FROM menu_tree t JOIN menu_view v ON v.item_id=t.item_id
WHERE t.item_id IN (202,2020,2021,2022,2023) ORDER BY v.role_id,t.item_id;
