package greenhouse.greenhouse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

public final class CsrfGuard {
    private CsrfGuard() {}
    public static String token(HttpSession session) {
        synchronized (session) {
            Object value = session.getAttribute("userCsrfToken");
            if (!(value instanceof String) || ((String) value).isEmpty()) {
                value = UUID.randomUUID().toString();
                session.setAttribute("userCsrfToken", value);
            }
            return (String) value;
        }
    }
    public static boolean valid(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return false;
        Object value = session.getAttribute("userCsrfToken");
        String supplied = request.getParameter("csrf_token");
        return value instanceof String && supplied != null && !supplied.isEmpty()
            && MessageDigest.isEqual(((String)value).getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }
}
