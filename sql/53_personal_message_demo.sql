-- ============================================================
-- 53_personal_message_demo.sql  个人消息可选演示数据（肖泽亮）
--
-- 特点：
-- 1. 不写死账号编号：按用户名查询 todo_login_user 的实际 id（只取 status='active' 的账号）
-- 2. 找不到账号时跳过插入，并通过 SELECT 给出明确提示，不产生错误的关联数据
-- 3. 可重复执行：按标题判重，不会重复插入演示消息
-- 4. 不修改任何现有账号主键，不删除、不清空业务表
--
-- 执行顺序：09_user_management.sql -> 51_personal_message.sql -> 52_..菜单 -> 本脚本
-- 演示账号约定：admin（系统管理员）发给 worker（普通用户）；账号改名时改下面两个变量即可
-- ============================================================
SET NAMES utf8mb4;
USE greenhouse_system;

SET @demo_sender    := (SELECT id FROM todo_login_user WHERE username = 'admin'  AND status = 'active' LIMIT 1);
SET @demo_recipient := (SELECT id FROM todo_login_user WHERE username = 'worker' AND status = 'active' LIMIT 1);

SELECT
    @demo_sender    AS demo_sender_id,
    @demo_recipient AS demo_recipient_id,
    CASE
        WHEN @demo_sender IS NULL    THEN '跳过：未找到状态为active的账号 admin'
        WHEN @demo_recipient IS NULL THEN '跳过：未找到状态为active的账号 worker'
        WHEN @demo_sender = @demo_recipient THEN '跳过：admin 与 worker 解析为同一账号'
        ELSE '账号解析成功，将插入演示数据（已存在则跳过）'
    END AS demo_check;

-- 演示草稿：管理员写给普通用户的草稿
INSERT INTO personal_message (title, content, message_type, sender_id, recipient_id, send_status)
SELECT '【演示】admin 发给 worker 的草稿',
       '这是个人消息模块的演示草稿，可在发件页编辑或发送。',
       'system', @demo_sender, @demo_recipient, 'draft'
FROM DUAL
WHERE @demo_sender IS NOT NULL
  AND @demo_recipient IS NOT NULL
  AND @demo_sender <> @demo_recipient
  AND NOT EXISTS (SELECT 1 FROM personal_message
                  WHERE title = '【演示】admin 发给 worker 的草稿');

-- 演示已发送消息：管理员发给普通用户的正式消息
INSERT INTO personal_message (title, content, message_type, sender_id, recipient_id, send_status, sent_at)
SELECT '【演示】admin 发送给 worker 的消息',
       '这是个人消息模块的演示已发送消息，用于验证收件箱、已读与统计。',
       'notice', @demo_sender, @demo_recipient, 'sent', NOW()
FROM DUAL
WHERE @demo_sender IS NOT NULL
  AND @demo_recipient IS NOT NULL
  AND @demo_sender <> @demo_recipient
  AND NOT EXISTS (SELECT 1 FROM personal_message
                  WHERE title = '【演示】admin 发送给 worker 的消息');

-- 核对结果：演示数据的实际关联账号与状态
SELECT p.id, p.title, p.send_status,
       p.sender_id, su.username AS sender_name, su.status AS sender_status,
       p.recipient_id, ru.username AS recipient_name, ru.status AS recipient_status
FROM personal_message p
LEFT JOIN todo_login_user su ON su.id = p.sender_id
LEFT JOIN todo_login_user ru ON ru.id = p.recipient_id
WHERE p.title LIKE '【演示】%'
ORDER BY p.id;
