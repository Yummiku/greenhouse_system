package user.tools;

import device.dao.Db;
import user.security.PasswordUtil;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

// 在IDEA中手动运行，把原来的明文密码转成BCrypt哈希。
public class MigratePasswords {
    public static void main(String[] args) throws SQLException {
        try (Connection connection = new Db("greenhouse_system").getConnection()) {
            connection.setAutoCommit(false);
            try {
                List<String[]> updates = new ArrayList<>();
                int skipped = 0;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT id, password FROM todo_login_user ORDER BY id FOR UPDATE");
                     ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        String oldPassword = rs.getString("password");
                        if (PasswordUtil.isEncoded(oldPassword)) {
                            skipped++;
                            continue;
                        }
                        if (oldPassword != null && oldPassword.startsWith("$2")) {
                            throw new IllegalArgumentException("发现不支持或损坏的哈希，请先核对用户表");
                        }
                        updates.add(new String[]{rs.getString("id"),
                                PasswordUtil.hash(oldPassword)});
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE todo_login_user SET password = ? WHERE id = ?")) {
                    for (String[] row : updates) {
                        statement.setString(1, row[1]);
                        statement.setString(2, row[0]);
                        if (statement.executeUpdate() != 1) {
                            throw new SQLException("密码迁移未成功，请检查用户记录");
                        }
                    }
                }
                connection.commit();
                System.out.println("迁移完成：转换 " + updates.size()
                        + " 个账号，跳过已有BCrypt哈希的 " + skipped + " 个账号。");
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }
}
