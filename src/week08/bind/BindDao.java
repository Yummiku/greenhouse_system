package week08.bind;

import device.dao.Db;
import org.json.JSONObject;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

public class BindDao {
    private Connection open() throws SQLException { return new Db("greenhouse_system").getConnection(); }

    public void ping() throws Exception {
        try (Connection c = open(); PreparedStatement p = c.prepareStatement(
                "SELECT COUNT(*) FROM week08_bind_ticket"); ResultSet r = p.executeQuery()) {
            r.next();
        }
    }

    public static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String randomCode() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    // 只从已有平台用户表取身份，不新建用户、不读取或修改密码。
    public JSONObject createTicket(String userId) throws Exception {
        try (Connection c = open()) {
            JSONObject user = new JSONObject();
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT id,username,display_name FROM todo_login_user WHERE id=?")) {
                p.setString(1, userId);
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next()) throw new IllegalArgumentException("平台用户不存在");
                    user.put("user_id", r.getString("id"));
                    user.put("username", r.getString("username"));
                    user.put("display_name", r.getString("display_name"));
                }
            }
            String code = randomCode();
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO week08_bind_ticket(ticket_hash,user_id,expires_at) "
                    + "VALUES(?,?,DATE_ADD(NOW(),INTERVAL 10 MINUTE))")) {
                p.setString(1, hash(code)); p.setString(2, userId); p.executeUpdate();
            }
            JSONObject result = new JSONObject();
            result.put("user", user);
            result.put("bind_text", "BIND:" + userId + ":" + code);
            result.put("valid_minutes", 10);
            return result;
        }
    }

    public boolean validTicket(String userId, String code) throws Exception {
        try (Connection c = open(); PreparedStatement p = c.prepareStatement(
                "SELECT id FROM week08_bind_ticket WHERE user_id=? AND ticket_hash=? "
                + "AND used_at IS NULL AND expires_at>NOW()")) {
            p.setString(1, userId); p.setString(2, hash(code));
            try (ResultSet r = p.executeQuery()) { return r.next(); }
        }
    }
}
