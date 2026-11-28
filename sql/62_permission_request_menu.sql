-- 第13周增量菜单：请先选择目标数据库；不覆盖既有菜单编号，不写USE。
-- 执行61后执行。菜单仅控制显示，后台仍独立校验权限。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
START TRANSACTION;
SET @identity_root=(SELECT MIN(item_id) FROM menu_tree WHERE module_id='identity' AND parent_id=0);
SET @next_menu=(SELECT COALESCE(MAX(item_id),0)+1 FROM menu_tree);
INSERT INTO menu_tree(item_id,parent_id,module_id,item_name,item_url)
SELECT @next_menu,0,'identity','账号信息','' WHERE @identity_root IS NULL;
SET @identity_root=(SELECT MIN(item_id) FROM menu_tree WHERE module_id='identity' AND parent_id=0);
SET @next_menu=(SELECT COALESCE(MAX(item_id),0)+1 FROM menu_tree);
INSERT INTO menu_tree(item_id,parent_id,module_id,item_name,item_url)
SELECT @next_menu,@identity_root,'identity','我的权限申请','/maintain/permission/index.jsp'
WHERE NOT EXISTS(SELECT 1 FROM menu_tree WHERE item_url='/maintain/permission/index.jsp');
SET @next_menu=(SELECT COALESCE(MAX(item_id),0)+1 FROM menu_tree);
INSERT INTO menu_tree(item_id,parent_id,module_id,item_name,item_url)
SELECT @next_menu,@identity_root,'identity','权限审批','/maintain/permission/review.jsp'
WHERE NOT EXISTS(SELECT 1 FROM menu_tree WHERE item_url='/maintain/permission/review.jsp');
SET @next_menu=(SELECT COALESCE(MAX(item_id),0)+1 FROM menu_tree);
INSERT INTO menu_tree(item_id,parent_id,module_id,item_name,item_url)
SELECT @next_menu,@identity_root,'identity','个人驾驶舱','/maintain/permission/dashboard.jsp'
WHERE NOT EXISTS(SELECT 1 FROM menu_tree WHERE item_url='/maintain/permission/dashboard.jsp');
INSERT IGNORE INTO menu_view(role_id,item_id)
SELECT role_options.role_id,m.item_id FROM (SELECT 1 AS role_id UNION ALL SELECT 2 UNION ALL SELECT 3) role_options
JOIN menu_tree m ON m.item_id=@identity_root OR m.item_url IN('/maintain/permission/index.jsp','/maintain/permission/dashboard.jsp');
INSERT IGNORE INTO menu_view(role_id,item_id) SELECT 1,item_id FROM menu_tree WHERE item_url='/maintain/permission/review.jsp';
COMMIT;
SELECT v.role_id,m.item_name,m.item_url FROM menu_tree m JOIN menu_view v ON v.item_id=m.item_id
WHERE m.item_url LIKE '/maintain/permission/%' ORDER BY v.role_id,m.item_id;
