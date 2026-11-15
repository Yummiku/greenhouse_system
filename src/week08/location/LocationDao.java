package week08.location;

import device.dao.Db;
import org.json.JSONArray;
import org.json.JSONObject;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LocationDao {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private Connection open() throws SQLException { return new Db("greenhouse_system").getConnection(); }
    public void ping() throws Exception {
        try (Connection c = open(); PreparedStatement p = c.prepareStatement("SELECT 1"); ResultSet r = p.executeQuery()) { r.next(); }
    }
    public void report(String userId, String name, double latitude, double longitude, String time, String source) throws Exception {
        if (!userId.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("用户/设备编号使用1至64位英文、数字、下划线或短横线");
        if (userId.startsWith("DEMO_")) throw new IllegalArgumentException("DEMO_前缀保留给标注为模拟的数据");
        if (name.isEmpty() || name.length() > 100) throw new IllegalArgumentException("请填写100字以内的名称");
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90 || !Double.isFinite(longitude) || longitude < -180 || longitude > 180)
            throw new IllegalArgumentException("经纬度无效");
        LocalDateTime collected;
        try { collected = LocalDateTime.parse(time, TIME); }
        catch (Exception e) { throw new IllegalArgumentException("采集时间格式应为yyyy-MM-dd HH:mm:ss"); }
        if (!"devtools".equals(source) && !"device".equals(source)) throw new IllegalArgumentException("采集来源无效");
        String sql = "INSERT INTO member_location(user_id,display_name,latitude,longitude,collected_at,source,is_simulated) VALUES(?,?,?,?,?,?,0) "
                + "ON DUPLICATE KEY UPDATE display_name=VALUES(display_name),latitude=VALUES(latitude),longitude=VALUES(longitude),"
                + "collected_at=VALUES(collected_at),source=VALUES(source),updated_at=CURRENT_TIMESTAMP";
        try (Connection c = open(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1,userId); p.setString(2,name); p.setDouble(3,latitude); p.setDouble(4,longitude);
            p.setTimestamp(5,Timestamp.valueOf(collected)); p.setString(6,source); p.executeUpdate();
        }
    }
    public JSONArray list() throws Exception {
        JSONArray result = new JSONArray();
        try (Connection c = open(); PreparedStatement p = c.prepareStatement(
                "SELECT id,user_id,display_name,latitude,longitude,collected_at,updated_at,source,is_simulated FROM member_location ORDER BY id");
             ResultSet r = p.executeQuery()) {
            while (r.next()) {
                JSONObject row = new JSONObject();
                row.put("id",r.getInt("id")); row.put("user_id",r.getString("user_id"));
                row.put("display_name",r.getString("display_name")); row.put("latitude",r.getDouble("latitude")); row.put("longitude",r.getDouble("longitude"));
                row.put("collected_at",r.getTimestamp("collected_at").toLocalDateTime().format(TIME));
                row.put("updated_at",r.getTimestamp("updated_at").toLocalDateTime().format(TIME));
                row.put("source",r.getString("source")); row.put("is_simulated",r.getBoolean("is_simulated")); result.put(row);
            }
        }
        return result;
    }
}
