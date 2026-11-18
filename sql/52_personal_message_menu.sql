-- ============================================================
-- 52_personal_message_menu.sql  个人消息菜单迁移（肖泽亮）
-- 增量补充菜单，不覆盖、不删除用户管理/大棚档案等既有菜单
-- 可重复执行：menu_tree 用 ON DUPLICATE KEY UPDATE 保持幂等，
--            menu_view 用 INSERT IGNORE 避免重复授权
--
-- 执行顺序：09_user_management.sql -> 51_personal_message.sql -> 本脚本
-- 菜单只控制入口显示，接口权限仍由 MessageServlet 后台校验（角色3调发件接口返回403）
--
-- 菜单编号占用 400 段（100 todo / 200 greenhouse / 300 identity 已被占用，无冲突）
-- ============================================================
SET NAMES utf8mb4;
USE greenhouse_system;

INSERT INTO menu_tree (item_id, parent_id, module_id, item_name, item_url) VALUES
(400, 0,   'message', '消息中心', ''),
(401, 400, 'message', '收件箱',   '/maintain/message/message_inbox.jsp'),
(402, 400, 'message', '发件记录', '/maintain/message/message_compose.jsp')
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    module_id = VALUES(module_id),
    item_name = VALUES(item_name),
    item_url  = VALUES(item_url);

-- 角色1 系统管理员：收件箱 + 发件记录（可查看全量发件）
-- 角色2 普通管理员：收件箱 + 发件记录（只查看本人创建的发件）
-- 角色3 普通用户：仅收件箱，不显示发件入口
INSERT IGNORE INTO menu_view (role_id, item_id) VALUES
(1, 400), (1, 401), (1, 402),
(2, 400), (2, 401), (2, 402),
(3, 400), (3, 401);

-- 迁移结果核对：三类角色各自能看到的个人消息菜单
SELECT v.role_id, m.item_id, m.item_name, m.item_url
FROM menu_tree m
JOIN menu_view v ON v.item_id = m.item_id
WHERE m.item_id IN (400, 401, 402)
ORDER BY v.role_id, m.item_id;
