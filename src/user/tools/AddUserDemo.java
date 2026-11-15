package user.tools;

import device.dao.Db;
import user.dao.UserDao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Scanner;

public class AddUserDemo {

    public static void main(String[] args) throws Exception {

        try (Connection connection =
                     new Db("greenhouse_system").getConnection()) {

            if (!"greenhouse_system".equals(connection.getCatalog())) {
                throw new IllegalStateException("数据库连接不正确");
            }

            UserDao userDao = new UserDao();
            Scanner input = new Scanner(System.in);

            createAccount(
                    connection, userDao, input,
                    "admin", "系统管理员", 1
            );

            createAccount(
                    connection, userDao, input,
                    "manager", "普通管理员", 2
            );

            createAccount(
                    connection, userDao, input,
                    "worker", "普通用户", 3
            );

            System.out.println("本机账号初始化完成。已有账号的密码不会被覆盖。");
        }
    }

    private static void createAccount(
            Connection connection,
            UserDao userDao,
            Scanner input,
            String username,
            String displayName,
            int roleId
    ) throws Exception {

        String sql =
                "SELECT role_id FROM todo_login_user WHERE username = ?";

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, username);

            try (ResultSet result = statement.executeQuery()) {

                if (result.next()) {
                    if (result.getInt("role_id") != roleId) {
                        throw new IllegalStateException(
                                username + "已存在，但角色不符合预期"
                        );
                    }

                    System.out.println(
                            username + "已存在，保留原密码。"
                    );
                    return;
                }
            }
        }

        System.out.println(
                "请输入" + username + "的密码，至少8个字符："
        );
        String password = input.nextLine();

        System.out.println("请再次输入密码：");
        String confirmation = input.nextLine();

        if (password.length() < 8) {
            throw new IllegalArgumentException("密码至少8个字符");
        }

        if (!password.equals(confirmation)) {
            throw new IllegalArgumentException("两次密码不一致");
        }

        userDao.addUser(
                username, password, displayName, roleId
        );

        System.out.println(
                "创建成功：" + username + "，角色：" + roleId
        );
    }
}