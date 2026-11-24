package greenhouse.message.servlet;

import greenhouse.log.service.OperationLogger;
import greenhouse.message.dao.PersonalMessageDao;
import greenhouse.message.entity.MessagePage;
import greenhouse.message.entity.MessageQuery;
import greenhouse.message.entity.MessageStats;
import greenhouse.message.entity.PersonalMessage;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 个人消息接口 /api/messages。
 * LoginFilter已拦截未登录请求，本类仍自行校验登录、角色和记录归属：
 * 收件箱只按Session用户id查询，发送人只取Session，不接受前端传入的用户身份。
 */
public class MessageServlet extends HttpServlet {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String ROLE_ADMIN = "1";
    private static final String ROLE_MANAGER = "2";
    private static final String ROLE_USER = "3";

    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_SENT = "sent";

    private static final int TITLE_MAX = 200;
    private static final int TYPE_MAX = 50;
    private static final int CONTENT_MAX = 5000;

    private final PersonalMessageDao messageDao = new PersonalMessageDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        Map<?, ?> user = currentUser(request);
        if (user == null) {
            reject(response, 401, "请先登录");
            return;
        }

        String roleId = String.valueOf(user.get("roleId"));
        if (!isKnownRole(roleId)) {
            reject(response, 403, "角色无效");
            return;
        }
        int userId = parseUserId(user, response);
        if (userId <= 0) {
            return;
        }

        String action = request.getParameter("action");

        try {
            if ("listInbox".equals(action)) {
                listInbox(request, response, userId, roleId);
                return;
            }
            if ("listOutbox".equals(action)) {
                listOutbox(request, response, userId, roleId);
                return;
            }
            if ("detail".equals(action)) {
                detail(request, response, userId, roleId);
                return;
            }
            if ("stats".equals(action)) {
                stats(request, response, userId);
                return;
            }
            if ("statsOutbox".equals(action)) {
                statsOutbox(request, response, userId, roleId);
                return;
            }
            if ("exportInbox".equals(action)) {
                exportCsv(request, response, userId, roleId, true);
                return;
            }
            if ("exportOutbox".equals(action)) {
                exportCsv(request, response, userId, roleId, false);
                return;
            }
            if ("reportInbox".equals(action)) {
                report(request, response, userId, roleId, true);
                return;
            }
            if ("reportOutbox".equals(action)) {
                report(request, response, userId, roleId, false);
                return;
            }
            reject(response, 400, "未实现的业务操作");
        } catch (SQLException e) {
            reject(response, 500, "消息查询失败，请检查数据库配置");
        } catch (JSONException e) {
            reject(response, 500, "响应序列化失败");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        request.setCharacterEncoding("UTF-8");

        Map<?, ?> user = currentUser(request);
        if (user == null) {
            reject(response, 401, "请先登录");
            return;
        }

        String roleId = String.valueOf(user.get("roleId"));
        int userId = parseUserId(user, response);
        if (userId <= 0) {
            return;
        }

        String action = request.getParameter("action");

        try {
            if ("createDraft".equals(action)) {
                if (!canSend(roleId)) {
                    reject(response, 403, "您没有权限创建消息");
                    return;
                }
                createDraft(request, response, userId);
                return;
            }
            if ("sendDraft".equals(action)) {
                if (!canSend(roleId)) {
                    reject(response, 403, "您没有权限发送消息");
                    return;
                }
                sendDraft(request, response, userId, roleId);
                return;
            }
            if ("markRead".equals(action)) {
                markRead(request, response, userId);
                return;
            }
            if ("deleteReceived".equals(action)) {
                deleteReceived(request, response, userId);
                return;
            }
            if ("withdraw".equals(action)) {
                if (!canSend(roleId)) {
                    reject(response, 403, "您没有权限撤回消息");
                    return;
                }
                withdraw(request, response, userId, roleId);
                return;
            }
            if ("deleteDraft".equals(action)) {
                if (!canSend(roleId)) {
                    reject(response, 403, "您没有权限删除草稿");
                    return;
                }
                deleteDraft(request, response, userId);
                return;
            }
            if ("updateDraft".equals(action)) {
                if (!canSend(roleId)) {
                    reject(response, 403, "您没有权限编辑草稿");
                    return;
                }
                updateDraft(request, response, userId);
                return;
            }
            reject(response, 400, "未实现的业务操作");
        } catch (SQLException e) {
            reject(response, 500, "消息保存失败，请检查数据库配置");
        } catch (JSONException e) {
            reject(response, 500, "响应序列化失败");
        }
    }

    // ==================== GET 查询 ====================

    private void listInbox(HttpServletRequest request, HttpServletResponse response,
                           int userId, String roleId)
            throws IOException, SQLException, JSONException {

        MessageQuery query = parseQuery(request, response);
        if (query == null) return;

        MessagePage page = messageDao.searchInbox(userId, query);

        JSONArray rows = new JSONArray();
        for (PersonalMessage message : page.getList()) {
            rows.put(inboxRow(message));
        }

        JSONObject data = new JSONObject();
        data.put("total", page.getTotal());
        data.put("page", page.getPage());
        data.put("pageSize", page.getPageSize());
        data.put("pages", pages(page.getTotal(), page.getPageSize()));
        data.put("roleId", Integer.parseInt(roleId));
        data.put("list", rows);
        ok(response, data);
    }

    private void listOutbox(HttpServletRequest request, HttpServletResponse response,
                            int userId, String roleId)
            throws IOException, SQLException, JSONException {

        if (ROLE_USER.equals(roleId)) {
            reject(response, 403, "您没有权限查看发件记录");
            return;
        }

        MessageQuery query = parseQuery(request, response);
        if (query == null) return;

        boolean onlyMine = ROLE_MANAGER.equals(roleId);
        MessagePage page = messageDao.searchOutbox(userId, onlyMine, query);

        JSONArray rows = new JSONArray();
        for (PersonalMessage message : page.getList()) {
            JSONObject row = new JSONObject();
            row.put("id", message.getId());
            row.put("title", nullToEmpty(message.getTitle()));
            row.put("content", nullToEmpty(message.getContent()));
            row.put("messageType", nullToEmpty(message.getMessageType()));
            row.put("recipientId", message.getRecipientId());
            row.put("sendStatus", nullToEmpty(message.getSendStatus()));
            row.put("sentAt", formatTime(message.getSentAt()));
            row.put("createdAt", formatTime(message.getCreatedAt()));
            rows.put(row);
        }

        JSONObject data = new JSONObject();
        data.put("total", page.getTotal());
        data.put("page", page.getPage());
        data.put("pageSize", page.getPageSize());
        data.put("pages", pages(page.getTotal(), page.getPageSize()));
        data.put("onlyMine", onlyMine);
        data.put("roleId", Integer.parseInt(roleId));
        data.put("list", rows);
        ok(response, data);
    }

    private void detail(HttpServletRequest request, HttpServletResponse response,
                        int userId, String roleId)
            throws IOException, SQLException, JSONException {

        long id = parsePositiveLong(request.getParameter("id"));
        if (id <= 0) {
            reject(response, 400, "消息编号无效");
            return;
        }

        PersonalMessage message = messageDao.findById(id);
        if (message == null) {
            reject(response, 400, "消息不存在");
            return;
        }

        boolean visibleAsRecipient = message.getRecipientId() != null
                && message.getRecipientId() == userId
                && STATUS_SENT.equals(message.getSendStatus())
                && message.getWithdrawnAt() == null
                && (message.getRecipientDeleted() == null || message.getRecipientDeleted() == 0);

        if (!visibleAsRecipient && !canViewAsSender(message, userId, roleId)) {
            reject(response, 403, "您没有权限查看这条消息");
            return;
        }

        JSONObject data = new JSONObject();
        data.put("id", message.getId());
        data.put("title", nullToEmpty(message.getTitle()));
        data.put("content", nullToEmpty(message.getContent()));
        data.put("messageType", nullToEmpty(message.getMessageType()));
        data.put("senderId", message.getSenderId());
        data.put("recipientId", message.getRecipientId());
        data.put("sendStatus", nullToEmpty(message.getSendStatus()));
        data.put("sentAt", formatTime(message.getSentAt()));
        data.put("readAt", formatTime(message.getReadAt()));
        data.put("withdrawnAt", formatTime(message.getWithdrawnAt()));
        data.put("createdAt", formatTime(message.getCreatedAt()));
        data.put("updatedAt", formatTime(message.getUpdatedAt()));
        data.put("read", message.getReadAt() != null);
        data.put("asRecipient", visibleAsRecipient);
        ok(response, data);
    }

    private void stats(HttpServletRequest request, HttpServletResponse response, int userId)
            throws IOException, SQLException, JSONException {

        MessageQuery query = parseQuery(request, response);
        if (query == null) return;

        // 统计需要拆分已读与未读，已读状态条件不参与。
        query.setReadState("");

        MessageStats stats = messageDao.statsInbox(userId, query);

        JSONArray byType = new JSONArray();
        for (Map.Entry<String, Long> entry : stats.getByType().entrySet()) {
            JSONObject row = new JSONObject();
            row.put("type", entry.getKey());
            row.put("count", entry.getValue());
            byType.put(row);
        }

        JSONObject data = new JSONObject();
        data.put("total", stats.getTotal());
        data.put("unread", stats.getUnread());
        data.put("read", stats.getRead());
        data.put("byType", byType);
        ok(response, data);
    }

    /** 发件统计：总数/草稿/已发送/已撤回 + 类型分布，范围与发件列表一致。 */
    private void statsOutbox(HttpServletRequest request, HttpServletResponse response,
                             int userId, String roleId)
            throws IOException, SQLException, JSONException {

        if (ROLE_USER.equals(roleId)) {
            reject(response, 403, "您没有权限查看发件记录");
            return;
        }
        MessageQuery query = parseQuery(request, response);
        if (query == null) {
            return;
        }
        // 统计不使用状态筛选，否则单独筛某个状态时分布与总数会被口径截断。
        query.setSendStatus("");
        boolean onlyMine = ROLE_MANAGER.equals(roleId);
        JSONObject data = messageDao.statsOutbox(userId, onlyMine, query);
        data.put("roleId", Integer.parseInt(roleId));
        data.put("onlyMine", onlyMine);
        ok(response, data);
    }

    // ==================== POST 写入 ====================

    /**
     * 发送人（当前登录账号）本身也必须是有效账号。
     * 登录后被停用/删除的账号不能继续发消息，返回 403。
     */
    private boolean requireActiveSender(HttpServletResponse response, int senderId)
            throws IOException, SQLException {

        if (!messageDao.activeUserExists(senderId)) {
            reject(response, 403, "您没有权限发送消息，账号不存在或已停用");
            return false;
        }
        return true;
    }

    private void createDraft(HttpServletRequest request, HttpServletResponse response,
                             int senderId) throws IOException, SQLException, JSONException {

        if (!requireActiveSender(response, senderId)) {
            return;
        }
        String title = trimmed(request.getParameter("title"));
        String messageType = trimmed(request.getParameter("messageType"));
        String content = request.getParameter("content") == null
                ? "" : request.getParameter("content").trim();
        int recipientId = parsePositiveInt(request.getParameter("recipientId"));

        if (title.isEmpty()) {
            reject(response, 400, "消息标题不能为空");
            return;
        }
        if (title.length() > TITLE_MAX) {
            reject(response, 400, "消息标题不能超过" + TITLE_MAX + "字");
            return;
        }
        if (messageType.isEmpty()) {
            reject(response, 400, "请选择消息类型");
            return;
        }
        if (messageType.length() > TYPE_MAX) {
            reject(response, 400, "消息类型不能超过" + TYPE_MAX + "字");
            return;
        }
        if (content.length() > CONTENT_MAX) {
            reject(response, 400, "消息正文不能超过" + CONTENT_MAX + "字");
            return;
        }
        if (recipientId <= 0) {
            reject(response, 400, "接收人无效");
            return;
        }
        if (!messageDao.activeUserExists(recipientId)) {
            reject(response, 400, "接收人账号不存在或已停用");
            return;
        }
        if (recipientId == senderId) {
            reject(response, 400, "不能给自己发送消息");
            return;
        }

        long id = messageDao.createDraft(senderId, recipientId, title, content, messageType);
        // 业务已提交，再记一条成功操作日志（日志故障不影响业务）。
        OperationLogger.success(currentUser(request), "个人消息", "新建草稿",
                String.valueOf(id), title, "接收人ID=" + recipientId);

        JSONObject data = new JSONObject();
        data.put("id", id);
        data.put("sendStatus", STATUS_DRAFT);
        ok(response, data);
    }

    /** 编辑草稿：只有创建人本人、且消息仍处于草稿状态时才允许修改。 */
    private void updateDraft(HttpServletRequest request, HttpServletResponse response,
                             int userId) throws IOException, SQLException, JSONException {

        if (!requireActiveSender(response, userId)) {
            return;
        }
        long id = parsePositiveLong(request.getParameter("id"));
        if (id <= 0) {
            reject(response, 400, "消息编号无效");
            return;
        }

        String title = trimmed(request.getParameter("title"));
        String messageType = trimmed(request.getParameter("messageType"));
        String content = request.getParameter("content") == null
                ? "" : request.getParameter("content").trim();
        int recipientId = parsePositiveInt(request.getParameter("recipientId"));

        if (title.isEmpty()) {
            reject(response, 400, "消息标题不能为空");
            return;
        }
        if (title.length() > TITLE_MAX) {
            reject(response, 400, "消息标题不能超过" + TITLE_MAX + "字");
            return;
        }
        if (messageType.isEmpty()) {
            reject(response, 400, "请选择消息类型");
            return;
        }
        if (messageType.length() > TYPE_MAX) {
            reject(response, 400, "消息类型不能超过" + TYPE_MAX + "字");
            return;
        }
        if (content.length() > CONTENT_MAX) {
            reject(response, 400, "消息正文不能超过" + CONTENT_MAX + "字");
            return;
        }
        if (recipientId <= 0) {
            reject(response, 400, "接收人无效");
            return;
        }
        if (!messageDao.activeUserExists(recipientId)) {
            reject(response, 400, "接收人账号不存在或已停用");
            return;
        }
        if (recipientId == userId) {
            reject(response, 400, "不能给自己发送消息");
            return;
        }

        PersonalMessage message = messageDao.findById(id);
        if (message == null) {
            reject(response, 400, "消息不存在");
            return;
        }
        if (message.getSenderId() == null || message.getSenderId() != userId) {
            reject(response, 403, "您没有权限编辑这条草稿");
            return;
        }
        if (!STATUS_DRAFT.equals(message.getSendStatus())) {
            reject(response, 409, "已发送的消息不能再修改");
            return;
        }

        messageDao.updateDraft(id, userId, title, content, messageType, recipientId);
        OperationLogger.success(currentUser(request), "个人消息", "编辑草稿",
                String.valueOf(id), title, "接收人ID=" + recipientId);

        JSONObject data = new JSONObject();
        data.put("id", id);
        data.put("sendStatus", STATUS_DRAFT);
        ok(response, data);
    }

    private void sendDraft(HttpServletRequest request, HttpServletResponse response,
                           int userId, String roleId)
            throws IOException, SQLException, JSONException {

        if (!requireActiveSender(response, userId)) {
            return;
        }
        long id = parsePositiveLong(request.getParameter("id"));
        if (id <= 0) {
            reject(response, 400, "消息编号无效");
            return;
        }

        PersonalMessage message = messageDao.findById(id);
        if (message == null) {
            reject(response, 400, "消息不存在");
            return;
        }
        if (!messageBelongsTo(message, userId, roleId)) {
            OperationLogger.denied(currentUser(request), "个人消息", "发送消息",
                    String.valueOf(id), "非本人草稿不可发送");
            reject(response, 403, "只能发送本人创建的消息");
            return;
        }
        if (!STATUS_DRAFT.equals(message.getSendStatus())
                || message.getWithdrawnAt() != null) {
            reject(response, 409, "该消息不是草稿，不能发送");
            return;
        }
        // 发送时重新校验接收人：保存草稿后接收人可能已被停用或删除。
        if (message.getRecipientId() == null
                || !messageDao.activeUserExists(message.getRecipientId())) {
            reject(response, 409, "接收人账号不存在或已停用，无法发送");
            return;
        }

        int senderId = message.getSenderId() == null ? userId : message.getSenderId();
        int updated = messageDao.sendDraft(id, senderId);
        if (updated == 0) {
            reject(response, 409, "消息状态已变化，请刷新后重试");
            return;
        }

        JSONObject data = new JSONObject();
        data.put("id", id);
        data.put("sendStatus", STATUS_SENT);
        ok(response, data);
        OperationLogger.success(currentUser(request), "个人消息", "发送消息",
                String.valueOf(id), message.getTitle(), "接收人ID=" + message.getRecipientId());
    }

    private void markRead(HttpServletRequest request, HttpServletResponse response,
                          int userId) throws IOException, SQLException, JSONException {

        long id = parsePositiveLong(request.getParameter("id"));
        if (id <= 0) {
            reject(response, 400, "消息编号无效");
            return;
        }

        PersonalMessage message = messageDao.findById(id);
        if (message == null) {
            reject(response, 400, "消息不存在");
            return;
        }
        if (message.getRecipientId() == null || message.getRecipientId() != userId) {
            reject(response, 403, "只能操作本人的收件");
            return;
        }
        if (!STATUS_SENT.equals(message.getSendStatus())
                || message.getWithdrawnAt() != null
                || (message.getRecipientDeleted() != null && message.getRecipientDeleted() != 0)) {
            reject(response, 409, "该消息当前不可读");
            return;
        }

        // 已读操作幂等：已经是已读状态时不再写库，直接返回成功。
        if (message.getReadAt() == null) {
            messageDao.markRead(id, userId);
            message = messageDao.findById(id);
        }

        JSONObject data = new JSONObject();
        data.put("id", id);
        data.put("read", true);
        data.put("readAt", formatTime(message == null ? null : message.getReadAt()));
        ok(response, data);
    }

    /**
     * 接收人逻辑删除：只把本人的可见标记置1，源记录和其他账号的视角不受影响。
     * 重复删除按幂等处理，直接返回成功。
     */
    private void deleteReceived(HttpServletRequest request, HttpServletResponse response,
                                int userId) throws IOException, SQLException, JSONException {

        long id = parsePositiveLong(request.getParameter("id"));
        if (id <= 0) {
            reject(response, 400, "消息编号无效");
            return;
        }

        PersonalMessage message = messageDao.findById(id);
        if (message == null) {
            reject(response, 400, "消息不存在");
            return;
        }
        if (message.getRecipientId() == null || message.getRecipientId() != userId) {
            reject(response, 403, "只能删除本人的收件");            return;
        }

        // 已经删除过：保持幂等，不重复写库。
        if (message.getRecipientDeleted() != null && message.getRecipientDeleted() != 0) {
            JSONObject data = new JSONObject();
            data.put("id", id);
            data.put("deleted", true);
            ok(response, data);
            return;
        }
        if (!STATUS_SENT.equals(message.getSendStatus()) || message.getWithdrawnAt() != null) {
            reject(response, 409, "该消息当前不可删除");
            return;
        }
        // 按交互规范：只允许逻辑移除本人已读的消息，未读先标为已读。
        if (message.getReadAt() == null) {
            reject(response, 409, "请先标为已读再删除");
            return;
        }

        messageDao.deleteForRecipient(id, userId);
        OperationLogger.success(currentUser(request), "个人消息", "删除收件",
                String.valueOf(id), message.getTitle(), "接收人逻辑删除，仅影响本人可见");

        JSONObject data = new JSONObject();
        data.put("id", id);
        data.put("deleted", true);
        ok(response, data);
    }

    /**
     * 撤回：发送人本人可撤回本条已发送消息，角色1可代为处理全量发件。
     * 撤回后记录保留在发件箱，收件箱与统计不再显示。
     */
    private void withdraw(HttpServletRequest request, HttpServletResponse response,
                          int userId, String roleId) throws IOException, SQLException, JSONException {

        long id = parsePositiveLong(request.getParameter("id"));
        if (id <= 0) {
            reject(response, 400, "消息编号无效");
            return;
        }

        PersonalMessage message = messageDao.findById(id);
        if (message == null) {
            reject(response, 400, "消息不存在");
            return;
        }
        if (!canViewAsSender(message, userId, roleId)) {
            reject(response, 403, "您没有权限撤回这条消息");
            return;
        }
        if (!STATUS_SENT.equals(message.getSendStatus()) || message.getWithdrawnAt() != null) {
            reject(response, 409, "这条消息当前不能撤回");
            return;
        }

        messageDao.withdraw(id, userId, ROLE_ADMIN.equals(roleId));
        OperationLogger.success(currentUser(request), "个人消息", "撤回消息",
                String.valueOf(id), message.getTitle(), "状态 sent -> withdrawn");

        JSONObject data = new JSONObject();
        data.put("id", id);
        data.put("sendStatus", "withdrawn");
        ok(response, data);
    }

    /** 删除草稿：只有创建人本人可删，已发送的消息不允许删除。 */
    private void deleteDraft(HttpServletRequest request, HttpServletResponse response,
                             int userId) throws IOException, SQLException, JSONException {

        long id = parsePositiveLong(request.getParameter("id"));
        if (id <= 0) {
            reject(response, 400, "消息编号无效");
            return;
        }

        PersonalMessage message = messageDao.findById(id);
        if (message == null) {
            reject(response, 400, "消息不存在");
            return;
        }
        if (message.getSenderId() == null || message.getSenderId() != userId) {
            reject(response, 403, "您没有权限删除这条草稿");
            return;
        }
        if (!STATUS_DRAFT.equals(message.getSendStatus())) {
            reject(response, 409, "只有草稿可以删除");
            return;
        }

        messageDao.deleteDraft(id, userId);
        OperationLogger.success(currentUser(request), "个人消息", "删除草稿",
                String.valueOf(id), message.getTitle(), "草稿物理删除");

        JSONObject data = new JSONObject();
        data.put("id", id);
        data.put("deleted", true);
        ok(response, data);
    }

    /**
     * 导出 CSV：浏览器直接下载（不经过前端 GHApi），带 UTF-8 BOM 保证 Excel 不乱码。
     * 筛选条件与列表页一致，分页按每批100条循环取全量。
     */
    private void exportCsv(HttpServletRequest request, HttpServletResponse response,
                           int userId, String roleId, boolean inbox)
            throws IOException, SQLException, JSONException {

        if (!inbox && ROLE_USER.equals(roleId)) {
            reject(response, 403, "您没有权限导出发件记录");
            return;
        }

        MessageQuery query = parseQuery(request, response);
        if (query == null) {
            return;
        }

        boolean onlyMine = ROLE_MANAGER.equals(roleId);
        List<PersonalMessage> rows = collectAll(query, userId, inbox, onlyMine);

        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("编号,标题,类型,发送人ID,接收人ID,状态,发送时间,阅读时间,正文\r\n");
        for (PersonalMessage message : rows) {
            csv.append(csvText(message.getId())).append(',')
                    .append(csvText(message.getTitle())).append(',')
                    .append(csvText(typeLabel(message.getMessageType()))).append(',')
                    .append(csvText(message.getSenderId())).append(',')
                    .append(csvText(message.getRecipientId())).append(',')
                    .append(csvText(statusLabel(message))).append(',')
                    .append(csvText(formatTime(message.getSentAt()))).append(',')
                    .append(csvText(formatTime(message.getReadAt()))).append(',')
                    .append(csvText(message.getContent())).append("\r\n");
        }

        String stamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = (inbox ? "收件箱" : "发件箱") + "_" + stamp + ".csv";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name())
                .replaceAll("\\+", "%20");

        response.setStatus(200);
        response.setContentType("text/csv;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"messages.csv\"; filename*=UTF-8''" + encoded);

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        response.setContentLength(bytes.length);
        OutputStream output = response.getOutputStream();
        output.write(bytes);
        output.flush();
    }

    /**
     * 按同一筛选条件和排序分批取全量结果（上限5000条），列表导出与打印共用，
     * 保证 CSV、打印与筛选结果的数据范围一致。
     */
    private List<PersonalMessage> collectAll(MessageQuery query, int userId,
                                             boolean inbox, boolean onlyMine)
            throws SQLException {

        List<PersonalMessage> rows = new ArrayList<PersonalMessage>();
        int page = 1;
        int pageSize = 100;
        while (true) {
            query.setPage(page);
            query.setPageSize(pageSize);
            MessagePage result = inbox
                    ? messageDao.searchInbox(userId, query)
                    : messageDao.searchOutbox(userId, onlyMine, query);
            long total = result.getTotal();
            if (result.getList().isEmpty()) {
                break;
            }
            rows.addAll(result.getList());
            if ((long) page * pageSize >= total || rows.size() >= 5000) {
                break;
            }
            page++;
        }
        return rows;
    }

    /**
     * 打印用报表：返回当前筛选条件下的**全部**结果，与当前页码、每页条数无关。
     * 收件箱/发件箱权限范围与列表、CSV 完全一致。
     */
    private void report(HttpServletRequest request, HttpServletResponse response,
                        int userId, String roleId, boolean inbox)
            throws IOException, SQLException, JSONException {

        if (!inbox && ROLE_USER.equals(roleId)) {
            reject(response, 403, "您没有权限查看发件记录");
            return;
        }

        MessageQuery query = parseQuery(request, response);
        if (query == null) {
            return;
        }

        boolean onlyMine = ROLE_MANAGER.equals(roleId);
        List<PersonalMessage> rows = collectAll(query, userId, inbox, onlyMine);

        JSONArray list = new JSONArray();
        for (PersonalMessage message : rows) {
            list.put(inbox ? inboxRow(message) : outboxRow(message));
        }

        JSONObject data = new JSONObject();
        data.put("total", rows.size());
        data.put("roleId", Integer.parseInt(roleId));
        data.put("list", list);
        ok(response, data);
    }

    private JSONObject outboxRow(PersonalMessage message) throws JSONException {
        JSONObject row = new JSONObject();
        row.put("id", message.getId());
        row.put("title", nullToEmpty(message.getTitle()));
        row.put("content", nullToEmpty(message.getContent()));
        row.put("messageType", nullToEmpty(message.getMessageType()));
        row.put("recipientId", message.getRecipientId());
        row.put("sendStatus", nullToEmpty(message.getSendStatus()));
        row.put("sentAt", formatTime(message.getSentAt()));
        row.put("createdAt", formatTime(message.getCreatedAt()));
        return row;
    }

    private String csvText(Object value) {
        if (value == null) {
            return "\"\"";
        }
        String text = String.valueOf(value).replace("\r\n", " ").replace("\n", " ")
                .replace("\r", " ");
        if (text.length() > 0) {
            char first = text.charAt(0);
            // 防止导出后，以 = + - @ 开头的正文被 Excel 当作公式执行。
            if (first == '=' || first == '+' || first == '-' || first == '@') {
                text = "'" + text;
            }
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private String statusLabel(PersonalMessage message) {
        if (message.getWithdrawnAt() != null) {
            return "已撤回";
        }
        if (STATUS_DRAFT.equals(message.getSendStatus())) {
            return "草稿";
        }
        return "已发送";
    }

    private String typeLabel(String messageType) {
        if (messageType == null || messageType.isBlank()) {
            return "未分类";
        }
        if ("notice".equals(messageType)) {
            return "通知";
        }
        if ("task".equals(messageType)) {
            return "任务";
        }
        if ("system".equals(messageType)) {
            return "系统";
        }
        return messageType;
    }

    // ==================== 辅助 ====================

    private JSONObject inboxRow(PersonalMessage message) throws JSONException {
        JSONObject row = new JSONObject();
        row.put("id", message.getId());
        row.put("title", nullToEmpty(message.getTitle()));
        row.put("messageType", nullToEmpty(message.getMessageType()));
        row.put("senderId", message.getSenderId());
        row.put("sentAt", formatTime(message.getSentAt()));
        row.put("readAt", formatTime(message.getReadAt()));
        row.put("read", message.getReadAt() != null);
        row.put("preview", preview(message.getContent()));
        return row;
    }

    private String preview(String content) {
        if (content == null || content.isBlank()) return "";
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() <= 40 ? flat : flat.substring(0, 40) + "…";
    }

    /** 角色1可查看全量发件，角色2只能查看本人创建的，角色3不具备发件视角。 */
    private boolean canViewAsSender(PersonalMessage message, int userId, String roleId) {
        if (ROLE_ADMIN.equals(roleId)) {
            return true;
        }
        if (ROLE_MANAGER.equals(roleId)) {
            return message.getSenderId() != null && message.getSenderId() == userId;
        }
        return false;
    }

    /** 角色1可处理全量发件，角色2只能处理本人创建的发件。 */
    private boolean messageBelongsTo(PersonalMessage message, int userId, String roleId) {
        if (ROLE_ADMIN.equals(roleId)) {
            return true;
        }
        return message.getSenderId() != null && message.getSenderId() == userId;
    }

    private MessageQuery parseQuery(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        MessageQuery query = new MessageQuery();
        query.setKeyword(trimmed(request.getParameter("keyword")));
        query.setMessageType(trimmed(request.getParameter("messageType")));

        String readState = trimmed(request.getParameter("readState")).toLowerCase();
        query.setReadState("unread".equals(readState) || "read".equals(readState) ? readState : "");

        String sendStatus = trimmed(request.getParameter("sendStatus")).toLowerCase();
        query.setSendStatus("draft".equals(sendStatus) || "sent".equals(sendStatus)
                ? sendStatus : "");

        String sortField = trimmed(request.getParameter("sortBy"));
        query.setSortField("createdAt".equals(sortField)
                || "sentAt".equals(sortField)
                || "id".equals(sortField) ? sortField : "");

        String sortDir = trimmed(request.getParameter("sortDir")).toLowerCase();
        query.setSortDir("asc".equals(sortDir) ? "asc" : "desc");

        int page = parsePositiveInt(request.getParameter("page"));
        query.setPage(page > 0 ? page : 1);

        int pageSize = parsePositiveInt(request.getParameter("pageSize"));
        query.setPageSize(pageSize == 5 || pageSize == 10 || pageSize == 20 || pageSize == 50
                ? pageSize : MessageQuery.DEFAULT_PAGE_SIZE);

        try {
            query.setFrom(parseDate(request.getParameter("startDate"), false));
            query.setTo(parseDate(request.getParameter("endDate"), true));
        } catch (DateTimeParseException e) {
            reject(response, 400, "日期格式应为 yyyy-MM-dd");
            return null;
        }
        return query;
    }

    /** 起始日期取当天0点；结束日期取次日0点，作为左闭右开的上界。 */
    private LocalDateTime parseDate(String value, boolean endOfRange) {
        String text = trimmed(value);
        if (text.isEmpty()) return null;
        LocalDate date = LocalDate.parse(text);
        return endOfRange ? date.plusDays(1).atStartOfDay() : date.atStartOfDay();
    }

    private int pages(long total, int pageSize) {
        if (pageSize <= 0) return 0;
        return (int) ((total + pageSize - 1) / pageSize);
    }

    private boolean canSend(String roleId) {
        return ROLE_ADMIN.equals(roleId) || ROLE_MANAGER.equals(roleId);
    }

    private boolean isKnownRole(String roleId) {
        return ROLE_ADMIN.equals(roleId)
                || ROLE_MANAGER.equals(roleId)
                || ROLE_USER.equals(roleId);
    }

    private Map<?, ?> currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object loginUser = session == null ? null : session.getAttribute("loginUser");
        return loginUser instanceof Map ? (Map<?, ?>) loginUser : null;
    }

    /** 会话必须带合法用户id；否则直接返回401并返回-1表示已处理。 */
    private int parseUserId(Map<?, ?> user, HttpServletResponse response) throws IOException {
        try {
            int userId = Integer.parseInt(String.valueOf(user.get("id")));
            if (userId <= 0) throw new NumberFormatException();
            return userId;
        } catch (NumberFormatException e) {
            reject(response, 401, "登录信息无效，请重新登录");
            return -1;
        }
    }

    private int parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(String.valueOf(value).trim());
            return parsed > 0 ? parsed : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private long parsePositiveLong(String value) {
        try {
            long parsed = Long.parseLong(String.valueOf(value).trim());
            return parsed > 0 ? parsed : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "" : TIME_FORMAT.format(time);
    }

    private void ok(HttpServletResponse response, JSONObject data) throws IOException {
        writeBody(response, 200, 0, "ok", data);
    }

    private void reject(HttpServletResponse response, int status, String message)
            throws IOException {
        writeBody(response, status, status, message, new JSONObject());
    }

    private void writeBody(
            HttpServletResponse response,
            int httpStatus,
            int resultCode,
            String resultMessage,
            JSONObject data) throws IOException {

        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");

        String body;
        try {
            JSONObject json = new JSONObject();
            json.put("result_code", resultCode);
            json.put("result_msg", resultMessage);
            json.put("data", data);
            body = json.toString();
        } catch (JSONException e) {
            body = "{\"result_code\":500,\"result_msg\":\"响应序列化失败\",\"data\":{}}";
        }
        response.getWriter().write(body);
    }
}
