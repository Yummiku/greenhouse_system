package device.dao;

import greenhouse.config.LocalSettings;
import java.sql.*;
import java.util.Properties;

/** 一次DAO操作持有一条连接，由调用方的try-with-resources关闭。 */
public class Db {
    private final Connection connection;
    private Statement statement;

    public Db(String dbName) throws SQLException {
        if (dbName == null || !dbName.matches("[A-Za-z0-9_]+")) {
            throw new SQLException("数据库名称无效");
        }
        String host = setting("host", "GH_DB_HOST", "localhost");
        String port = setting("port", "GH_DB_PORT", "3366");
        String user = setting("user", "GH_DB_USER", "root");
        String password = setting("password", "GH_DB_PASSWORD", null);
        if (!host.matches("[A-Za-z0-9_.:-]+") || !port.matches("[0-9]{1,5}")) {
            throw new SQLException("数据库地址或端口配置无效");
        }
        if (password == null) {
            throw new SQLException("请配置WEB-INF/greenhouse.local.properties中的数据库密码，或设置GH_DB_PASSWORD");
        }
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("未找到MySQL JDBC驱动，请检查WEB-INF/lib", e);
        }
        String url = "jdbc:mysql://" + host + ":" + port + "/" + dbName
                + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
                + "&connectTimeout=5000&socketTimeout=15000";
        Properties credentials = new Properties();
        credentials.setProperty("user", user);
        credentials.setProperty("password", password);
        connection = DriverManager.getConnection(url, credentials);
    }

    private String setting(String key, String environment, String fallback) {
        return LocalSettings.get("greenhouse.db." + key, environment, fallback);
    }

    // 仅供尚未迁移的内部旧DAO使用；Web入口不接受客户端SQL。
    public ResultSet executeQuery(String sql) throws SQLException {
        return statement().executeQuery(sql);
    }

    public void executeUpdate(String sql) throws SQLException {
        statement().executeUpdate(sql);
    }

    private Statement statement() throws SQLException {
        if (statement == null || statement.isClosed()) statement = connection.createStatement();
        return statement;
    }

    public Connection getConnection() throws SQLException {
        if (connection.isClosed()) throw new SQLException("数据库连接已关闭");
        return connection;
    }

    public void close() {
        try {
            if (statement != null) statement.close();
        } catch (SQLException ignored) {
            // 无论Statement是否关闭成功，都继续释放连接。
        } finally {
            try { connection.close(); } catch (SQLException ignored) { }
        }
    }
}
