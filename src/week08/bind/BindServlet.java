package week08.bind;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.json.JSONObject;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

// 本文件负责平台侧二维码；下一阶段再接小程序身份认证接口。
public class BindServlet extends HttpServlet {
    private final BindDao dao = new BindDao();

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp);
    }
    @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp);
    }

    private void handle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("X-Content-Type-Options", "nosniff");
        String action = req.getParameter("action");
        try {
            if ("ping".equals(action)) {
                dao.ping();
                JSONObject j = ok(); j.put("module", "week08-bind"); json(resp, 200, j); return;
            }
            // 平台侧生成和查看身份二维码需要原 Web 系统管理员登录。
            HttpSession session = req.getSession(false);
            Object loginUser = session == null ? null : session.getAttribute("loginUser");
            if (!(loginUser instanceof Map)) {
                error(resp, 401, "请先在本浏览器登录原Web系统，再打开二维码页面"); return;
            }
            Map<?, ?> user = (Map<?, ?>) loginUser;
            if (!"1".equals(String.valueOf(user.get("roleId")))) {
                error(resp, 403, "请使用管理员账号生成身份二维码"); return;
            }
            if ("create_ticket".equals(action)) {
                if (!"POST".equals(req.getMethod())) { error(resp, 405, "请使用POST"); return; }
                if (!"XMLHttpRequest".equals(req.getHeader("X-Requested-With"))) {
                    error(resp, 403, "请通过本系统二维码页面生成"); return;
                }
                String id = req.getParameter("user_id");
                if (id == null || !id.matches("[1-9][0-9]{0,18}"))
                    throw new IllegalArgumentException("请填写平台用户表中的数字id");
                JSONObject j = dao.createTicket(id);
                j.put("result_code", 0); j.put("result_msg", "ok"); json(resp, 200, j); return;
            }
            if ("qr".equals(action)) {
                String text = req.getParameter("text");
                if (text == null || !text.matches("BIND:[1-9][0-9]{0,18}:[a-f0-9]{64}"))
                    throw new IllegalArgumentException("二维码内容无效");
                String[] parts = text.split(":");
                if (!dao.validTicket(parts[1], parts[2])) {
                    error(resp, 410, "绑定码已过期或已使用，请重新生成"); return;
                }
                Map<EncodeHintType, Object> hints = new HashMap<>();
                hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
                hints.put(EncodeHintType.MARGIN, 4);
                BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 360, 360, hints);
                BufferedImage img = new BufferedImage(360, 360, BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < 360; y++) for (int x = 0; x < 360; x++)
                    img.setRGB(x, y, matrix.get(x, y) ? 0xff000000 : 0xffffffff);
                resp.setContentType("image/png");
                ImageIO.write(img, "png", resp.getOutputStream()); return;
            }
            error(resp, 400, "未知操作");
        } catch (IllegalArgumentException e) {
            error(resp, 400, e.getMessage());
        } catch (Exception e) {
            log("week08-bind接口异常", e);
            error(resp, 500, "后端执行失败，请查看Tomcat日志中的week08-bind异常");
        }
    }

    private static JSONObject ok() throws Exception {
        JSONObject j = new JSONObject(); j.put("result_code", 0); j.put("result_msg", "ok"); return j;
    }
    private static void json(HttpServletResponse resp, int status, JSONObject j) throws IOException {
        resp.setStatus(status); resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().print(j.toString());
    }
    private static void error(HttpServletResponse resp, int status, String message) throws IOException {
        try {
            JSONObject j = new JSONObject(); j.put("result_code", status); j.put("result_msg", message);
            json(resp, status, j);
        } catch (Exception e) { throw new IOException(e); }
    }
}
