-- Minimal menu append for Dai to apply after review. Explicitly select target database; repeatable.
SET @alarm_parent=(SELECT item_id FROM menu_tree WHERE item_id=200 LIMIT 1);
SET @alarm_item=COALESCE((SELECT item_id FROM menu_tree WHERE item_url='/maintain/alarm/index.jsp' LIMIT 1),(SELECT COALESCE(MAX(item_id),0)+1 FROM menu_tree));
INSERT INTO menu_tree(item_id,parent_id,module_id,item_name,item_url)
SELECT @alarm_item,@alarm_parent,'alarm','报警事件','/maintain/alarm/index.jsp' WHERE @alarm_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM menu_tree WHERE item_url='/maintain/alarm/index.jsp');
INSERT IGNORE INTO menu_view(role_id,item_id) SELECT r.role_id,@alarm_item FROM (SELECT 1 role_id UNION ALL SELECT 2 UNION ALL SELECT 3) r WHERE EXISTS(SELECT 1 FROM menu_tree WHERE item_id=@alarm_item AND item_url='/maintain/alarm/index.jsp');
SET @alarm_dashboard=COALESCE((SELECT item_id FROM menu_tree WHERE item_url='/maintain/alarm/dashboard.jsp' LIMIT 1),(SELECT COALESCE(MAX(item_id),0)+1 FROM menu_tree));
INSERT INTO menu_tree(item_id,parent_id,module_id,item_name,item_url)
SELECT @alarm_dashboard,@alarm_parent,'alarm','报警驾驶舱','/maintain/alarm/dashboard.jsp' WHERE @alarm_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM menu_tree WHERE item_url='/maintain/alarm/dashboard.jsp');
INSERT IGNORE INTO menu_view(role_id,item_id) SELECT r.role_id,@alarm_dashboard FROM (SELECT 1 role_id UNION ALL SELECT 2 UNION ALL SELECT 3) r WHERE EXISTS(SELECT 1 FROM menu_tree WHERE item_id=@alarm_dashboard AND item_url='/maintain/alarm/dashboard.jsp');
