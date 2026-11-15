package user.security;

import org.mindrot.jbcrypt.BCrypt;
import java.nio.charset.StandardCharsets;

public final class PasswordUtil {
    private PasswordUtil() {}

    private static boolean validPassword(String password) {
        return password != null && !password.isEmpty()
                && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    public static boolean isEncoded(String value) {
        return value != null && value.matches(
                "\\$2a\\$(0[4-9]|[12]\\d|30)\\$[./A-Za-z0-9]{53}");
    }

    // 新增用户或修改密码时调用，每次生成随机盐。
    public static String hash(String password) {
        if (!validPassword(password)) {
            throw new IllegalArgumentException("密码不能为空，UTF-8编码不能超过72字节");
        }
        return BCrypt.hashpw(password, BCrypt.gensalt(12));
    }

    // 登录时使用数据库中已有的盐和参数验证密码。
    public static boolean verify(String password, String storedHash) {
        if (!validPassword(password) || !isEncoded(storedHash)) return false;
        try {
            return BCrypt.checkpw(password, storedHash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
