-- 在明确选定的目标库执行，依赖框架菜单表。可重复执行，按页面地址找节点，不覆盖已有编号。
SET @threshold_parent=(SELECT item_id FROM menu_tree WHERE item_id=200 LIMIT 1);
SET @threshold_menu=(SELECT item_id FROM menu_tree WHERE item_url='/maintain/threshold/threshold.jsp' LIMIT 1);
SET @threshold_menu=COALESCE(@threshold_menu,(SELECT COALESCE(MAX(item_id),0)+1 FROM menu_tree));
INSERT INTO menu_tree(item_id,parent_id,module_id,item_name,item_url)
SELECT @threshold_menu,@threshold_parent,'threshold','阈值规则','/maintain/threshold/threshold.jsp'
WHERE @threshold_parent IS NOT NULL
AND NOT EXISTS(SELECT 1 FROM menu_tree WHERE item_url='/maintain/threshold/threshold.jsp');
INSERT IGNORE INTO menu_view(role_id,item_id)
SELECT r.role_id,@threshold_menu FROM
(SELECT 1 role_id UNION ALL SELECT 2 UNION ALL SELECT 3) r
WHERE EXISTS(SELECT 1 FROM menu_tree WHERE item_id=@threshold_menu AND item_url='/maintain/threshold/threshold.jsp');
