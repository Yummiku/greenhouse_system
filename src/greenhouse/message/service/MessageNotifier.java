package greenhouse.message.service;

import greenhouse.message.dao.PersonalMessageDao;

import java.sql.SQLException;

/**
 * 服务器端消息生成方法，供其他模块（阈值报警、待办指派、采样异常提醒等）直接调用。
 *
 * <p>调用方不需要登录会话，直接传接收人id即可，消息会以“已发送”状态落库，
 * 接收人登录后在个人消息收件箱里能看到。</p>
 *
 * <p>用法示例：</p>
 * <pre>
 *   // 给 3 号用户发一条任务消息
 *   MessageNotifier.notify(3, "3号棚湿度偏低", "当前湿度 38%，请检查喷灌设备", MessageNotifier.TYPE_TASK);
 *
 *   // 需要指定发送人时（例如报警由 2 号负责人代发）
 *   MessageNotifier.notify(2, 3, "阈值报警", "3号棚温度超过 35℃", MessageNotifier.TYPE_NOTICE);
 * </pre>
 *
 * <p>参数说明：</p>
 * <ul>
 *   <li>senderId：发送人id，可省略，默认 1（系统管理员），必须是不为空的合法账号</li>
 *   <li>recipientId：接收人id，必填，必须是 todo_login_user 中存在的账号</li>
 *   <li>title：消息标题，必填，最长 200 字</li>
 *   <li>content：消息正文，可为空，最长 5000 字</li>
 *   <li>messageType：消息类型，建议使用 TYPE_NOTICE / TYPE_TASK / TYPE_SYSTEM，
 *       传其他值或空值时按 system 处理</li>
 * </ul>
 */
public final class MessageNotifier {

    public static final String TYPE_NOTICE = "notice";
    public static final String TYPE_TASK = "task";
    public static final String TYPE_SYSTEM = "system";

    /** 未指定发送人时使用的系统账号。 */
    public static final int DEFAULT_SENDER_ID = 1;

    private static final int TITLE_MAX = 200;
    private static final int CONTENT_MAX = 5000;
    private static final int TYPE_MAX = 50;

    private static final PersonalMessageDao MESSAGE_DAO = new PersonalMessageDao();

    private MessageNotifier() {
    }

    /**
     * 以系统管理员身份发送一条消息。
     *
     * @return 新消息主键
     */
    public static long notify(int recipientId, String title, String content, String messageType)
            throws SQLException {
        return notify(DEFAULT_SENDER_ID, recipientId, title, content, messageType);
    }

    /**
     * 指定发送人发送一条消息。
     *
     * @return 新消息主键
     * @throws IllegalArgumentException 参数不合法（接收人不存在、标题为空、长度超限）
     * @throws SQLException 数据库写入失败
     */
    public static long notify(int senderId, int recipientId, String title,
                              String content, String messageType)
            throws SQLException {

        if (senderId <= 0) {
            throw new IllegalArgumentException("发送人id必须为正整数");
        }
        if (recipientId <= 0) {
            throw new IllegalArgumentException("接收人id必须为正整数");
        }
        if (!MESSAGE_DAO.userExists(recipientId)) {
            throw new IllegalArgumentException("接收人不存在：" + recipientId);
        }
        if (!MESSAGE_DAO.userExists(senderId)) {
            throw new IllegalArgumentException("发送人不存在：" + senderId);
        }

        String safeTitle = title == null ? "" : title.trim();
        if (safeTitle.isEmpty()) {
            throw new IllegalArgumentException("消息标题不能为空");
        }
        if (safeTitle.length() > TITLE_MAX) {
            safeTitle = safeTitle.substring(0, TITLE_MAX);
        }

        String safeContent = content == null ? "" : content.trim();
        if (safeContent.length() > CONTENT_MAX) {
            safeContent = safeContent.substring(0, CONTENT_MAX);
        }

        String safeType = normalType(messageType);

        return MESSAGE_DAO.createSent(senderId, recipientId, safeTitle, safeContent, safeType);
    }

    private static String normalType(String messageType) {
        if (messageType == null) {
            return TYPE_SYSTEM;
        }
        String type = messageType.trim();
        if (type.isEmpty()) {
            return TYPE_SYSTEM;
        }
        if (type.length() > TYPE_MAX) {
            type = type.substring(0, TYPE_MAX);
        }
        if (TYPE_NOTICE.equals(type) || TYPE_TASK.equals(type) || TYPE_SYSTEM.equals(type)) {
            return type;
        }
        return TYPE_SYSTEM;
    }
}
