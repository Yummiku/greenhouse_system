package device.dao;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.sql.*;

/** 兼容原设备接口；所有查询值通过参数绑定，不接受客户端SQL。 */
public class DeviceDao {
    public void getDeviceRecord(Data data, JSONObject json) throws SQLException, JSONException {
        StringBuilder sql = new StringBuilder("SELECT id, parent_id, device_id, device_name, device_type, "
                + "check_tag, checker, check_time, used_tag, creator_id, creator, create_time FROM device_file WHERE 1=1");
        java.util.List<Object> values = new java.util.ArrayList<>();
        String id = text(data, "id");
        if (!id.isEmpty()) { sql.append(" AND id=?"); values.add(positiveId(id)); }
        String deviceId = text(data, "device_id");
        if (!deviceId.isEmpty()) { sql.append(" AND device_id=?"); values.add(deviceId); }
        String name = text(data, "device_name");
        if (!name.isEmpty()) { sql.append(" AND INSTR(device_name, ?)>0"); values.add(name); }
        sql.append(" ORDER BY create_time DESC, id DESC");
        JSONArray rows = new JSONArray();
        try (Connection connection = new Db("greenhouse_system").getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < values.size(); i++) statement.setObject(i + 1, values.get(i));
            try (ResultSet result = statement.executeQuery()) {
                ResultSetMetaData metadata = result.getMetaData();
                while (result.next()) {
                    JSONObject row = new JSONObject();
                    for (int i = 1; i <= metadata.getColumnCount(); i++) {
                        String value = result.getString(i);
                        row.put(metadata.getColumnLabel(i), value == null ? "" : value);
                    }
                    rows.put(row);
                }
            }
        }
        success(json);
        json.put("aaData", rows);
    }

    public void addDeviceRecord(Data data, JSONObject json) throws SQLException, JSONException {
        String deviceId = required(data, "device_id", "设备编号");
        String name = required(data, "device_name", "设备名称");
        String type = limited(data, "device_type", "设备类型");
        String sql = "INSERT INTO device_file(device_id, device_name, device_type, used_tag, "
                + "creator_id, creator, create_time) VALUES(?, ?, ?, b'1', ?, ?, NOW())";
        try (Connection connection = new Db("greenhouse_system").getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, deviceId);
            statement.setString(2, name);
            statement.setString(3, type);
            // 由Servlet从登录会话填入，不采用客户端提交的创建人。
            statement.setString(4, text(data, "creator_id"));
            statement.setString(5, text(data, "creator"));
            if (statement.executeUpdate() != 1) throw new SQLException("设备保存失败");
        }
        success(json);
    }

    public void modifyDeviceRecord(Data data, JSONObject json) throws SQLException, JSONException {
        long id = positiveId(text(data, "id"));
        String deviceId = required(data, "device_id", "设备编号");
        String name = required(data, "device_name", "设备名称");
        // 兼容旧页面不提交类型字段的情况，避免清空已有类型。
        boolean hasType = data.getParam().has("device_type");
        String type = limited(data, "device_type", "设备类型");
        String sql = "UPDATE device_file SET device_id=?, device_name=?"
                + (hasType ? ", device_type=?" : "") + " WHERE id=?";
        try (Connection connection = new Db("greenhouse_system").getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, deviceId);
            statement.setString(2, name);
            int index = 3;
            if (hasType) statement.setString(index++, type);
            statement.setLong(index, id);
            if (statement.executeUpdate() != 1) throw new IllegalArgumentException("设备不存在，请刷新列表");
        }
        success(json);
    }

    public void deleteDeviceRecord(Data data, JSONObject json) throws SQLException, JSONException {
        long id = positiveId(text(data, "id"));
        try (Connection connection = new Db("greenhouse_system").getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM device_file WHERE id=?")) {
            statement.setLong(1, id);
            if (statement.executeUpdate() != 1) throw new IllegalArgumentException("设备不存在，请刷新列表");
        }
        success(json);
    }

    public void getMenuRecord(String roleId, JSONObject json) throws SQLException, JSONException {
        String sql = "SELECT a.item_id, a.parent_id, a.item_name, a.item_url "
                + "FROM menu_tree a JOIN menu_view b ON a.item_id=b.item_id "
                + "WHERE b.role_id=? ORDER BY a.item_id";
        JSONArray rows = new JSONArray();
        try (Connection connection = new Db("greenhouse_system").getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Integer.parseInt(roleId));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    JSONObject row = new JSONObject();
                    row.put("item_id", result.getString("item_id"));
                    row.put("parent_id", result.getString("parent_id"));
                    row.put("item_name", result.getString("item_name"));
                    String url = result.getString("item_url");
                    row.put("item_url", url == null ? "" : url);
                    rows.put(row);
                }
            }
        }
        success(json);
        json.put("aaData", rows);
    }

    private String text(Data data, String key) { return data.getParam().optString(key, "").trim(); }
    private String limited(Data data, String key, String label) {
        String value = text(data, key);
        if (value.length() > 255) throw new IllegalArgumentException(label + "不能超过255个字符");
        return value;
    }
    private String required(Data data, String key, String label) {
        String value = limited(data, key, label);
        if (value.isEmpty()) throw new IllegalArgumentException(label + "不能为空");
        return value;
    }
    private long positiveId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id > 0) return id;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("记录编号必须是正整数");
    }
    private void success(JSONObject json) throws JSONException {
        json.put("result_code", 0);
        json.put("result_msg", "ok");
    }
}
