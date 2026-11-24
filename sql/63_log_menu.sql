-- ============================================================
-- 63_log_menu.sql  日志与驾驶舱菜单（肖泽亮 第13周）
-- 增量补菜单，不覆盖、不删除他模块菜单；可重复执行
-- 公共菜单最终由戴锦晨统一合入，本脚本为最小增量片段
--
-- 编号占用：500 段日志中心、600 段驾驶舱（100 todo / 200 greenhouse / 300 identity / 400 message 已占用）
--
-- 权限：登录日志/操作日志/个人驾驶舱 —— 三角色可见（非管理员只看本人数据，后台限制）
--       集成驾驶舱 —— 仅系统管理员可见，直接请求接口其他角色 403
-- ============================================================
SET NAMES utf8mb4;
USE greenhouse_system;

INSERT INTO menu_tree (item_id, parent_id, module_id, item_name, item_url) VALUES
(500, 0,   'log',       '日志中心',   ''),
(501, 500, 'log',       '登录日志',   '/maintain/log/login_log.jsp'),
(502, 500, 'log',       '操作日志',   '/maintain/log/operation_log.jsp'),
(600, 0,   'dashboard', '驾驶舱',     ''),
(601, 600, 'dashboard', '个人驾驶舱', '/maintain/dashboard/my_dashboard.jsp'),
(602, 600, 'dashboard', '集成驾驶舱', '/maintain/dashboard/integration.jsp')
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    module_id = VALUES(module_id),
    item_name = VALUES(item_name),
    item_url  = VALUES(item_url);

INSERT IGNORE INTO menu_view (role_id, item_id) VALUES
(1, 500), (1, 501), (1, 502),
(2, 500), (2, 501), (2, 502),
(3, 500), (3, 501), (3, 502),
(1, 600), (1, 601), (1, 602),
(2, 600), (2, 601),
(3, 600), (3, 601);

-- 核对：各角色可见的日志与驾驶舱入口
SELECT v.role_id, m.item_id, m.item_name, m.item_url
FROM menu_tree m
JOIN menu_view v ON v.item_id = m.item_id
WHERE m.item_id IN (500, 501, 502, 600, 601, 602)
ORDER BY v.role_id, m.item_id;
