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
 *   <li>senderId：发送人id，可省略；省略时取库中第一个状态为 active 的系统管理员账号，
 *       不写死固定编号，发送人必须是有效账号</li>
 *   <li>recipientId：接收人id，必填，必须是 todo_login_user 中存在且状态为 active 的账号</li>
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

    private static final int TITLE_MAX = 200;
    private static final int CONTENT_MAX = 5000;
    private static final int TYPE_MAX = 50;

    private static final PersonalMessageDao MESSAGE_DAO = new PersonalMessageDao();

    private MessageNotifier() {
    }

    /**
     * 以默认系统发送账号发送一条消息。
     * 默认发送人不写死编号，而是取当前库中第一个状态为 active 的系统管理员账号；
     * 找不到时抛 IllegalStateException，调用方需显式指定发送人。
     *
     * @return 新消息主键
     */
    public static long notify(int recipientId, String title, String content, String messageType)
            throws SQLException {
        Integer systemSender = MESSAGE_DAO.findSystemSenderId();
        if (systemSender == null) {
            throw new IllegalStateException("未找到状态为active的系统管理员账号，无法确定默认发送人");
        }
        return notify(systemSender.intValue(), recipientId, title, content, messageType);
    }

    /**
     * 指定发送人发送一条消息，发送人与接收人都必须是状态为 active 的有效账号。
     *
     * @return 新消息主键
     * @throws IllegalArgumentException 参数不合法（账号不存在/已停用、标题为空、长度超限）
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
        if (!MESSAGE_DAO.activeUserExists(recipientId)) {
            throw new IllegalArgumentException("接收人不存在或已停用：" + recipientId);
        }
        if (!MESSAGE_DAO.activeUserExists(senderId)) {
            throw new IllegalArgumentException("发送人不存在或已停用：" + senderId);
        }
        if (recipientId == senderId) {
            throw new IllegalArgumentException("不能给自己发送消息");
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
