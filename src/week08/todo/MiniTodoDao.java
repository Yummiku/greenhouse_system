package week08.todo;

import device.dao.Db;
import org.json.JSONArray;
import org.json.JSONObject;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** 本周新增DAO；不修改第六周的device.dao.TodoDao。 */
public class MiniTodoDao {
    private Connection open() throws SQLException {
        return new Db("greenhouse_system").getConnection();
    }

    public void ping() throws Exception {
        try (Connection c = open(); PreparedStatement p = c.prepareStatement("SELECT 1");
             ResultSet r = p.executeQuery()) { r.next(); }
    }

    public JSONArray list(Long id, String keyword, String photoPrefix) throws Exception {
        JSONArray rows = new JSONArray();
        String sql = "SELECT id,project_id,title,create_time,remark FROM todo WHERE 1=1";
        if (id != null) sql += " AND id=?";
        if (!keyword.isEmpty()) sql += " AND title LIKE ?";
        sql += " ORDER BY id DESC";
        try (Connection c = open(); PreparedStatement p = c.prepareStatement(sql)) {
            int i = 1;
            if (id != null) p.setLong(i++, id);
            if (!keyword.isEmpty()) p.setString(i, "%" + keyword + "%");
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    JSONObject row = new JSONObject();
                    long rowId = r.getLong("id");
                    row.put("id", rowId);
                    row.put("project_id", r.getString("project_id"));
                    row.put("title", r.getString("title"));
                    row.put("create_time", r.getDate("create_time").toString());
                    row.put("remark", Objects.toString(r.getString("remark"), ""));
                    rows.put(row);
                }
            }
            for (int iRow = 0; iRow < rows.length(); iRow++) {
                JSONObject row = rows.getJSONObject(iRow);
                row.put("photos", photos(c, row.getLong("id"), photoPrefix));
            }
        }
        return rows;
    }

    private JSONArray photos(Connection c, long id, String prefix) throws Exception {
        JSONArray photos = new JSONArray();
        try (PreparedStatement p = c.prepareStatement(
                "SELECT attachment_id,original_name FROM todo_attachment WHERE todo_id=? ORDER BY created_at,attachment_id")) {
            p.setLong(1, id);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    JSONObject photo = new JSONObject();
                    photo.put("attachment_id", r.getString(1));
                    photo.put("name", r.getString(2));
                    photo.put("url", prefix + r.getString(1));
                    photos.put(photo);
                }
            }
        }
        return photos;
    }

    public long save(Long id, String projectId, String title, String date,
                     String remark, JSONArray ids) throws Exception {
        if (projectId.isEmpty() || projectId.length() > 64) throw new IllegalArgumentException("项目编号不能为空且不超过64字");
        if (title.isEmpty() || title.length() > 200) throw new IllegalArgumentException("标题不能为空且不超过200字");
        if (remark.length() > 2000) throw new IllegalArgumentException("备注不能超过2000字");
        LocalDate selectedDate;
        try { selectedDate = LocalDate.parse(date); }
        catch (Exception e) { throw new IllegalArgumentException("请选择正确的日期"); }
        if (ids.length() > 9) throw new IllegalArgumentException("最多9张照片");
        Set<String> unique = new LinkedHashSet<>();
        for (int i = 0; i < ids.length(); i++) {
            String value = ids.getString(i);
            UUID.fromString(value);
            if (!unique.add(value)) throw new IllegalArgumentException("附件编号重复");
        }
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                if (id != null) {
                    try (PreparedStatement lock = c.prepareStatement("SELECT id FROM todo WHERE id=? FOR UPDATE")) {
                        lock.setLong(1, id);
                        try (ResultSet r = lock.executeQuery()) {
                            if (!r.next()) throw new IllegalArgumentException("记录已不存在，请刷新列表");
                        }
                    }
                }
                String sql = id == null
                        ? "INSERT INTO todo(project_id,title,create_time,remark) VALUES(?,?,?,?)"
                        : "UPDATE todo SET project_id=?,title=?,create_time=?,remark=? WHERE id=?";
                long savedId;
                try (PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    p.setString(1, projectId); p.setString(2, title);
                    p.setDate(3, java.sql.Date.valueOf(selectedDate)); p.setString(4, remark);
                    if (id != null) p.setLong(5, id);
                    p.executeUpdate();
                    if (id == null) {
                        try (ResultSet r = p.getGeneratedKeys()) {
                            if (!r.next()) throw new SQLException("未取得新记录编号");
                            savedId = r.getLong(1);
                        }
                    } else savedId = id;
                }
                // 先验证所有附件，防止关联其他记录的图片；整个保存过程在同一事务。
                for (String attachment : unique) {
                    try (PreparedStatement p = c.prepareStatement(
                            "SELECT todo_id FROM todo_attachment WHERE attachment_id=? FOR UPDATE")) {
                        p.setString(1, attachment);
                        try (ResultSet r = p.executeQuery()) {
                            if (!r.next()) throw new IllegalArgumentException("附件不存在，请重新选择照片");
                            long owner = r.getLong(1);
                            if (!r.wasNull() && owner != savedId) throw new IllegalArgumentException("附件属于其他记录");
                        }
                    }
                }
                try (PreparedStatement p = c.prepareStatement("UPDATE todo_attachment SET todo_id=NULL WHERE todo_id=?")) {
                    p.setLong(1, savedId); p.executeUpdate();
                }
                for (String attachment : unique) {
                    try (PreparedStatement p = c.prepareStatement("UPDATE todo_attachment SET todo_id=? WHERE attachment_id=?")) {
                        p.setLong(1, savedId); p.setString(2, attachment); p.executeUpdate();
                    }
                }
                c.commit();
                return savedId;
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }

    public void delete(long id) throws Exception {
        // 外键ON DELETE SET NULL解除附件关系；不删除磁盘文件，避免误删其他资料。
        try (Connection c = open(); PreparedStatement p = c.prepareStatement("DELETE FROM todo WHERE id=?")) {
            p.setLong(1, id);
            if (p.executeUpdate() != 1) throw new IllegalArgumentException("记录已不存在，请刷新列表");
        }
    }

    public void addAttachment(String id, String filename, String original, String mime) throws Exception {
        try (Connection c = open(); PreparedStatement p = c.prepareStatement(
                "INSERT INTO todo_attachment(attachment_id,stored_name,original_name,mime_type) VALUES(?,?,?,?)")) {
            p.setString(1, id); p.setString(2, filename); p.setString(3, original); p.setString(4, mime);
            p.executeUpdate();
        }
    }

    public String[] attachment(String id) throws Exception {
        UUID.fromString(id);
        try (Connection c = open(); PreparedStatement p = c.prepareStatement(
                "SELECT stored_name,mime_type FROM todo_attachment WHERE attachment_id=?")) {
            p.setString(1, id);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? new String[]{r.getString(1), r.getString(2)} : null;
            }
        }
    }
}
