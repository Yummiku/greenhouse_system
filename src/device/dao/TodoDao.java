package device.dao;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TodoDao {

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 查询、导出、打印共用字段和筛选条件。
    public void getTodoRecord(Data data, JSONObject json)
            throws SQLException, JSONException {
        device.report.TodoReportDao.Query query = new device.report.TodoReportDao.Query(
                text(data, "id"), text(data, "title"), text(data, "create_date"),
                "create_time", "desc");
        JSONArray rows = new device.report.TodoReportDao().records(query);
        success(json);
        json.put("aaData", rows);
    }

    // 添加。
    public void addTodoRecord(Data data, JSONObject json)
            throws SQLException, JSONException {
        save(data, json, false);
    }

    // 修改。
    public void modifyTodoRecord(Data data, JSONObject json)
            throws SQLException, JSONException {
        save(data, json, true);
    }

    private void save(Data data, JSONObject json, boolean modifying)
            throws SQLException, JSONException {

        String title = text(data, "title");
        if (title.isEmpty() || title.length() > 255) {
            throw new IllegalArgumentException("标题不能为空，且不能超过255个字符");
        }

        Timestamp createTime;
        try {
            String value = text(data, "create_time").replace(' ', 'T');
            createTime = Timestamp.valueOf(LocalDateTime.parse(value));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("请选择正确的创建时间");
        }

        long id = modifying ? positiveId(text(data, "id")) : 0;

        String sql = modifying
                ? "UPDATE project_todo SET title=?, create_time=? WHERE id=?"
                : "INSERT INTO project_todo(title, create_time) VALUES(?, ?)";

        try (Connection connection = new Db("greenhouse_system").getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, title);
            statement.setTimestamp(2, createTime);

            if (modifying) {
                statement.setLong(3, id);
            }

            int affected = statement.executeUpdate();
            if (affected != 1) {
                throw new SQLException("记录不存在或未能保存，请刷新后重试");
            }
        }

        success(json);
    }

    // 删除：只删除指定 id 的一条记录。
    public void deleteTodoRecord(Data data, JSONObject json)
            throws SQLException, JSONException {

        long id = positiveId(text(data, "id"));

        try (Connection connection = new Db("greenhouse_system").getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM project_todo WHERE id=?")) {

            statement.setLong(1, id);

            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("该记录不存在，请刷新列表");
            }
        }

        success(json);
    }

    private String text(Data data, String name) {
        return data.getParam().optString(name, "").trim();
    }

    private long positiveId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("记录编号必须是正整数");
        }
    }

    private void success(JSONObject json) throws JSONException {
        json.put("result_code", 0);
        json.put("result_msg", "ok");
    }
}