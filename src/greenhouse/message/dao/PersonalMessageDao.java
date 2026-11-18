package greenhouse.message.dao;

import device.dao.Db;
import greenhouse.message.entity.MessagePage;
import greenhouse.message.entity.MessageQuery;
import greenhouse.message.entity.MessageStats;
import greenhouse.message.entity.PersonalMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 个人消息数据访问：一次操作持有一条连接，结束时必须调用Db.close()。
 * 查询条件只接受白名单字段，值一律走PreparedStatement占位符。
 */
public class PersonalMessageDao {

    private static final String DB_NAME = "greenhouse_system";

    private static final String COLUMNS = "id, title, content, message_type, sender_id, "
            + "recipient_id, send_status, sent_at, read_at, withdrawn_at, "
            + "recipient_deleted, created_at, updated_at";

    /** 收件箱筛选：按关键词、类型、已读状态、时间范围过滤，支持排序与分页。 */
    public MessagePage searchInbox(int recipientId, MessageQuery query) throws SQLException {
        Condition condition = inboxCondition(recipientId, query, true);

        Db db = new Db(DB_NAME);
        try {
            Connection connection = db.getConnection();
            long total = count(connection, condition);
            List<PersonalMessage> list = fetch(connection, condition,
                    sortColumn(query, "sentAt"), sortDirection(query),
                    query.getOffset(), query.getPageSize());
            return new MessagePage(list, total, query.getPage(), query.getPageSize());
        } finally {
            db.close();
        }
    }

    /** 发件记录筛选：角色1看全量，角色2只看本人创建；支持草稿/已发送状态过滤。 */
    public MessagePage searchOutbox(int senderId, boolean onlyMine, MessageQuery query)
            throws SQLException {

        Condition condition = outboxCondition(senderId, onlyMine, query);

        Db db = new Db(DB_NAME);
        try {
            Connection connection = db.getConnection();
            long total = count(connection, condition);
            List<PersonalMessage> list = fetch(connection, condition,
                    sortColumn(query, "createdAt"), sortDirection(query),
                    query.getOffset(), query.getPageSize());
            return new MessagePage(list, total, query.getPage(), query.getPageSize());
        } finally {
            db.close();
        }
    }

    /**
     * 本人可见消息统计：总数、未读、已读及类型分布。
     * 关键词、类型、时间条件与列表一致；已读状态不参与，因为统计本身要拆分已读与未读。
     */
    public MessageStats statsInbox(int recipientId, MessageQuery query) throws SQLException {
        Condition condition = inboxCondition(recipientId, query, false);

        Db db = new Db(DB_NAME);
        try {
            Connection connection = db.getConnection();

            long total = 0;
            long unread = 0;
            long read = 0;
            String countSql = "SELECT COUNT(*) AS total, "
                    + "SUM(CASE WHEN read_at IS NULL THEN 1 ELSE 0 END) AS unread_count, "
                    + "SUM(CASE WHEN read_at IS NOT NULL THEN 1 ELSE 0 END) AS read_count "
                    + "FROM personal_message " + condition.sql;
            try (PreparedStatement statement = connection.prepareStatement(countSql)) {
                bind(statement, condition.params);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        total = resultSet.getLong("total");
                        unread = resultSet.getLong("unread_count");
                        read = resultSet.getLong("read_count");
                    }
                }
            }

            MessageStats stats = new MessageStats(total, unread, read);
            String typeSql = "SELECT message_type, COUNT(*) AS type_count "
                    + "FROM personal_message " + condition.sql
                    + " GROUP BY message_type ORDER BY type_count DESC";
            try (PreparedStatement statement = connection.prepareStatement(typeSql)) {
                bind(statement, condition.params);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        stats.putType(resultSet.getString("message_type"),
                                resultSet.getLong("type_count"));
                    }
                }
            }
            return stats;
        } finally {
            db.close();
        }
    }

    /** 按主键取一条记录，供详情、发送与已读的归属校验使用。 */
    public PersonalMessage findById(long id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM personal_message WHERE id = ?";

        Db db = new Db(DB_NAME);
        try {
            Connection connection = db.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? readRow(resultSet) : null;
                }
            }
        } finally {
            db.close();
        }
    }

    /** 新建草稿，返回自增主键。发送人由后台会话传入，不接受页面指定。 */
    public long createDraft(int senderId, int recipientId, String title,
                            String content, String messageType) throws SQLException {

        String sql = "INSERT INTO personal_message"
                + "(title, content, message_type, sender_id, recipient_id, send_status) "
                + "VALUES(?, ?, ?, ?, ?, 'draft')";

        Db db = new Db(DB_NAME);
        try {
            Connection connection = db.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, title);
                statement.setString(2, content);
                statement.setString(3, messageType);
                statement.setInt(4, senderId);
                statement.setInt(5, recipientId);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("未取得新建草稿的主键");
                    }
                    return keys.getLong(1);
                }
            }
        } finally {
            db.close();
        }
    }

    /**
     * 发送草稿：只允许把本人的草稿从draft改为sent。
     * 返回受影响行数，0表示记录不存在、不是草稿或发送人不符。
     */
    public int sendDraft(long id, int senderId) throws SQLException {
        String sql = "UPDATE personal_message "
                + "SET send_status = 'sent', sent_at = NOW() "
                + "WHERE id = ? AND sender_id = ? "
                + "AND send_status = 'draft' AND withdrawn_at IS NULL";

        Db db = new Db(DB_NAME);
        try {
            Connection connection = db.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, id);
                statement.setInt(2, senderId);
                return statement.executeUpdate();
            }
        } finally {
            db.close();
        }
    }

    /**
     * 标记已读：只允许接收人本人操作，重复调用不改变已读时间。
     * 返回受影响行数，0表示本来就已读或记录对本人不可见。
     */
    public int markRead(long id, int recipientId) throws SQLException {
        String sql = "UPDATE personal_message "
                + "SET read_at = NOW() "
                + "WHERE id = ? AND recipient_id = ? "
                + "AND send_status = 'sent' "
                + "AND withdrawn_at IS NULL "
                + "AND recipient_deleted = 0 "
                + "AND read_at IS NULL";

        Db db = new Db(DB_NAME);
        try {
            Connection connection = db.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, id);
                statement.setInt(2, recipientId);
                return statement.executeUpdate();
            }
        } finally {
            db.close();
        }
    }

    /**
     * 接收人逻辑删除：只把本人的可见标记置1，源记录保留。
     * 返回受影响行数，0表示记录对本人不可见或已经删除过。
     */
    public int deleteForRecipient(long id, int recipientId) throws SQLException {
        String sql = "UPDATE personal_message "
                + "SET recipient_deleted = 1, updated_at = NOW() "
                + "WHERE id = ? AND recipient_id = ? "
                + "AND send_status = 'sent' "
                + "AND withdrawn_at IS NULL "
                + "AND read_at IS NOT NULL "
                + "AND recipient_deleted = 0";

        Db db = new Db(DB_NAME);
        try {
            Connection connection = db.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, id);
                statement.setInt(2, recipientId);
                return statement.executeUpdate();
            }
        } finally {
            db.close();
        }
    }

    /**
     * 撤回：只允许发送人本人（或角色1代为处理）把已发送消息改为withdrawn。
     * 返回受影响行数，0表示不是已发送状态、发送人不符或已撤回。
     */
    public int withdraw(long id, int senderId, boolean allSenders) throws SQLException {
        String sql = "UPDATE personal_message "
                + "SET send_status = 'withdrawn', withdrawn_at = NOW() "
                + "WHERE id = ? "
                + (allSenders ? "" : "AND sender_id = ? ")
                + "AND send_status = 'sent' AND withdrawn_at IS NULL";

        Db db = new Db(DB_NAME);
        try {
            Connection connection = db.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, id);
                if (!allSenders) {
                    statement.setInt(2, senderId);
                }
                return statement.executeUpdate();
            }
        } finally {
            db.close();
        }
    }

    /**
     * 删除草稿：只允许创建人本人删除自己的草稿，已发送的消息不能走这里。
     * 返回受影响行数，0表示不是草稿或发送人不符。
     */
    public int deleteDraft(long id, int senderId) throws SQLException {
        String sql = "DELETE FROM personal_message "
                + "WHERE id = ? AND sender_id = ? "
                + "AND send_status = 'draft' AND withdrawn_at IS NULL";

        Db db = new Db(DB_NAME);
        try {
            Connection connection = db.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, id);
                statement.setInt(2, senderId);
                return statement.executeUpdate();
            }
        } finally {
            db.close();
        }
    }

    /**
     * 直接生成一条已发送消息，供任务、报警等模块在服务器内部调用。
     * 不做角色判断（调用方是后台代码），但调用方仍需保证接收人有效。
     * 返回自增主键。
     */
    public long createSent(int senderId, int recipientId, String title,
                           String content, String messageType) throws SQLException {

        String sql = "INSERT INTO personal_message"
                + "(title, content, message_type, sender_id, recipient_id, send_status, sent_at) "
                + "VALUES(?, ?, ?, ?, ?, 'sent', NOW())";

        Db db = new Db(DB_NAME);
        try {
            Connection connection = db.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, title);
                statement.setString(2, content);
                statement.setString(3, messageType);
                statement.setInt(4, senderId);
                statement.setInt(5, recipientId);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("未取得生成消息的主键");
                    }
                    return keys.getLong(1);
                }
            }
        } finally {
            db.close();
        }
    }

    /**
     * 编辑草稿：只有创建人本人能改，且必须是未发送的草稿。
     * 发送后正文和接收人不能再改，只能走撤回或重新建一条。
     * 返回受影响行数，0表示不是草稿或发送人不符。
     */
    public int updateDraft(long id, int senderId, String title, String content,
                           String messageType, int recipientId) throws SQLException {

        String sql = "UPDATE personal_message "
                + "SET title = ?, content = ?, message_type = ?, recipient_id = ?, "
                + "updated_at = NOW() "
                + "WHERE id = ? AND sender_id = ? "
                + "AND send_status = 'draft' AND withdrawn_at IS NULL";

        Db db = new Db(DB_NAME);
        try {
            Connection connection = db.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, title);
                statement.setString(2, content);
                statement.setString(3, messageType);
                statement.setInt(4, recipientId);
                statement.setLong(5, id);
                statement.setInt(6, senderId);
                return statement.executeUpdate();
            }
        } finally {
            db.close();
        }
    }

    /** 接收人必须是存在的有效账号。 */
    public boolean userExists(int userId) throws SQLException {        String sql = "SELECT 1 FROM todo_login_user WHERE id = ? AND role_id IN (1, 2, 3)";

        Db db = new Db(DB_NAME);
        try {
            Connection connection = db.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, userId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            }
        } finally {
            db.close();
        }
    }

    private Condition inboxCondition(int recipientId, MessageQuery query, boolean withReadState) {
        Condition condition = new Condition();
        condition.add(" WHERE recipient_id = ? AND send_status = 'sent' "
                + "AND withdrawn_at IS NULL AND recipient_deleted = 0", recipientId);
        appendCommonFilters(condition, query);

        if (withReadState) {
            if ("unread".equals(query.getReadState())) {
                condition.add(" AND read_at IS NULL");
            } else if ("read".equals(query.getReadState())) {
                condition.add(" AND read_at IS NOT NULL");
            }
        }
        return condition;
    }

    private Condition outboxCondition(int senderId, boolean onlyMine, MessageQuery query) {
        Condition condition = new Condition();
        condition.add(" WHERE withdrawn_at IS NULL");
        if (onlyMine) {
            condition.add(" AND sender_id = ?", senderId);
        }
        appendCommonFilters(condition, query);

        String status = query.getSendStatus();
        if ("draft".equals(status) || "sent".equals(status)) {
            condition.add(" AND send_status = ?", status);
        }
        return condition;
    }

    private void appendCommonFilters(Condition condition, MessageQuery query) {
        String keyword = query.getKeyword();
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim() + "%";
            condition.add(" AND (title LIKE ? OR content LIKE ?)", pattern, pattern);
        }
        String type = query.getMessageType();
        if (type != null && !type.isBlank()) {
            condition.add(" AND message_type = ?", type.trim());
        }
        if (query.getFrom() != null) {
            condition.add(" AND created_at >= ?", query.getFrom());
        }
        if (query.getTo() != null) {
            condition.add(" AND created_at < ?", query.getTo());
        }
    }

    private String sortColumn(MessageQuery query, String defaultField) {
        String field = query.getSortField();
        if ("createdAt".equals(field)) return "created_at";
        if ("sentAt".equals(field)) return "sent_at";
        if ("id".equals(field)) return "id";
        return "createdAt".equals(defaultField) ? "created_at" : "sent_at";
    }

    private String sortDirection(MessageQuery query) {
        return "asc".equalsIgnoreCase(query.getSortDir()) ? "ASC" : "DESC";
    }

    private long count(Connection connection, Condition condition) throws SQLException {
        String sql = "SELECT COUNT(*) AS total FROM personal_message " + condition.sql;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, condition.params);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong("total") : 0;
            }
        }
    }

    private List<PersonalMessage> fetch(Connection connection, Condition condition,
                                        String sortColumn, String sortDirection,
                                        int offset, int pageSize) throws SQLException {

        // 未发送草稿没有 sent_at，按发送时间排序时用创建时间兜底，保证草稿也能稳定排序。
        String orderExpression = "sent_at".equals(sortColumn)
                ? "COALESCE(sent_at, created_at)" : sortColumn;

        String sql = "SELECT " + COLUMNS + " FROM personal_message " + condition.sql
                + " ORDER BY " + orderExpression + " " + sortDirection
                + ", id " + sortDirection
                + " LIMIT ? OFFSET ?";

        List<PersonalMessage> list = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = bind(statement, condition.params);
            statement.setInt(index, pageSize);
            statement.setInt(index + 1, offset);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    list.add(readRow(resultSet));
                }
            }
        }
        return list;
    }

    private int bind(PreparedStatement statement, List<Object> params) throws SQLException {
        int index = 1;
        for (Object value : params) {
            if (value instanceof LocalDateTime) {
                statement.setTimestamp(index, Timestamp.valueOf((LocalDateTime) value));
            } else if (value instanceof Integer) {
                statement.setInt(index, (Integer) value);
            } else {
                statement.setString(index, String.valueOf(value));
            }
            index++;
        }
        return index;
    }

    private PersonalMessage readRow(ResultSet resultSet) throws SQLException {
        PersonalMessage message = new PersonalMessage();
        message.setId(resultSet.getLong("id"));
        message.setTitle(resultSet.getString("title"));
        message.setContent(resultSet.getString("content"));
        message.setMessageType(resultSet.getString("message_type"));
        message.setSenderId(resultSet.getInt("sender_id"));
        message.setRecipientId(resultSet.getInt("recipient_id"));
        message.setSendStatus(resultSet.getString("send_status"));
        message.setSentAt(toLocalDateTime(resultSet.getTimestamp("sent_at")));
        message.setReadAt(toLocalDateTime(resultSet.getTimestamp("read_at")));
        message.setWithdrawnAt(toLocalDateTime(resultSet.getTimestamp("withdrawn_at")));
        message.setRecipientDeleted(resultSet.getInt("recipient_deleted"));
        message.setCreatedAt(toLocalDateTime(resultSet.getTimestamp("created_at")));
        message.setUpdatedAt(toLocalDateTime(resultSet.getTimestamp("updated_at")));
        return message;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    /** 条件片段与其占位符参数，二者顺序一一对应。 */
    private static final class Condition {
        private String sql = "";
        private final List<Object> params = new ArrayList<>();

        private void add(String fragment, Object... values) {
            sql = sql + fragment;
            for (Object value : values) {
                params.add(value);
            }
        }
    }
}
