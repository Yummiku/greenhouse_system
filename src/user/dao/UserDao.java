package user.dao;

import device.dao.Db;
import user.security.PasswordUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class UserDao {

    public Map<String, String> login(String username, String password)
            throws SQLException {

        String sql = "SELECT id, username, password, display_name, role_id "
                + "FROM todo_login_user WHERE username = ?";

        try (Connection connection = new Db("greenhouse_system").getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, username);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return null;

                // 按账号取出哈希，再验证输入的密码。
                String storedHash = resultSet.getString("password");
                if (!PasswordUtil.verify(password, storedHash)) return null;

                // Session 只保存基本信息和角色，不保存密码或哈希。
                Map<String, String> user = new HashMap<>();
                user.put("id", resultSet.getString("id"));
                user.put("username", resultSet.getString("username"));
                user.put("displayName", resultSet.getString("display_name"));
                user.put("roleId", resultSet.getString("role_id"));
                return user;
            }
        }
    }

    // 添加用户：先加密，再写入数据库。
    public void addUser(String username, String password,
                        String displayName, int roleId) throws SQLException {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("账号不能为空");
        }
        if (roleId != 1 && roleId != 2 && roleId != 3) {
            throw new IllegalArgumentException(
                    "角色只能为1系统管理员、2普通管理员或3普通用户"
            );
        }

        String passwordHash = PasswordUtil.hash(password);
        String sql = "INSERT INTO todo_login_user "
                + "(username, password, display_name, role_id) VALUES (?, ?, ?, ?)";

        try (Connection connection = new Db("greenhouse_system").getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username.trim());
            statement.setString(2, passwordHash);
            statement.setString(3, displayName);
            statement.setInt(4, roleId);
            statement.executeUpdate();
        }
    }
}
