-- 补齐已有框架库的菜单；新库先执行01及对应模块迁移，不重建现有数据库。
-- 保留已有账号和业务记录，不删除、不清空、不修改原有菜单。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
USE greenhouse_system;
ALTER TABLE todo_login_user MODIFY role_id INT NOT NULL COMMENT '1系统管理员，2普通管理员，3普通用户';
INSERT INTO menu_tree (item_id,parent_id,module_id,item_name,item_url) VALUES
(100,0,'todo','工作管理',''),
(101,100,'todo','待办事项','/maintain/todo/todo_crud.jsp'),
(102,100,'todo','数据看板','/maintain/todo/todo_dashboard.jsp'),
(200,0,'greenhouse','农业管理',''),
(201,200,'greenhouse','环境监测','/maintain/environment/environment.jsp'),
(202,200,'greenhouse','大棚档案','/maintain/greenhouse/index.jsp')
ON DUPLICATE KEY UPDATE item_id=VALUES(item_id);
INSERT IGNORE INTO menu_view(role_id,item_id) VALUES
(1,100),(1,101),(1,102),(1,200),(1,201),
(2,100),(2,101),(2,102),(2,200),(2,201),
(3,100),(3,101),(3,102),(3,200),(3,201),
(1,202),(2,202),(3,202);

-- 核对大棚档案入口及三类角色授权。
SELECT m.item_id,m.parent_id,m.item_name,m.item_url,v.role_id
FROM menu_tree m JOIN menu_view v ON v.item_id=m.item_id
WHERE m.item_url='/maintain/greenhouse/index.jsp' ORDER BY v.role_id;
