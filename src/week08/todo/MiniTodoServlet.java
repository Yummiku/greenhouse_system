package week08.todo;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.json.JSONArray;
import org.json.JSONObject;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** 路由、multipart大小限制在web.xml片段中配置。 */
public class MiniTodoServlet extends HttpServlet {
    private final MiniTodoDao dao = new MiniTodoDao();
    private Path uploadRoot;

    @Override public void init() throws ServletException {
        String configured = System.getProperty("week08.uploadDir", System.getenv("W08_UPLOAD_DIR"));
        uploadRoot = Paths.get(configured == null || configured.isBlank()
                ? System.getProperty("user.home") + "/week08-uploads" : configured).toAbsolutePath().normalize();
        try { Files.createDirectories(uploadRoot); }
        catch (IOException e) { throw new ServletException("无法创建本周照片目录", e); }
    }

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException { handle(req, resp); }
    @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException { handle(req, resp); }

    private String text(HttpServletRequest req, String key) {
        String value = req.getParameter(key);
        return value == null ? "" : value.trim();
    }

    private long positiveId(String value) {
        long id = Long.parseLong(value);
        if (id <= 0) throw new IllegalArgumentException("记录编号错误");
        return id;
    }

    private void handle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        try {
            req.setCharacterEncoding("UTF-8");
            String action = text(req, "action");
            String prefix = req.getContextPath() + "/api/week08/todo?action=photo&id=";
            if ("photo".equals(action)) { sendPhoto(req, resp); return; }
            JSONObject result = new JSONObject();
            result.put("result_code", 0); result.put("result_msg", "ok");
            if ("ping".equals(action)) {
                dao.ping();
                String key = apiKey();
                result.put("key_configured", key != null && key.length() >= 16);
                result.put("module", "week08-todo");
            } else {
                String key = apiKey();
                if (key == null || key.length() < 16) throw new IllegalStateException("请先配置Tomcat的-Dweek08.apiKey参数，至少16字符");
                if (!key.equals(req.getHeader("X-Week08-Key"))) {
                    resp.setStatus(401);
                    throw new IllegalArgumentException("本周接口访问码不一致，请检查week08-api.js和Tomcat环境变量");
                }
                switch (action) {
                    case "get_todo_record":
                        String rawId = text(req, "id");
                        result.put("aaData", dao.list(rawId.isEmpty() ? null : positiveId(rawId), text(req, "keyword"), prefix));
                        break;
                    case "add_todo_record":
                    case "modify_todo_record":
                        requirePost(req);
                        Long id = "modify_todo_record".equals(action) ? positiveId(text(req, "id")) : null;
                        String raw = text(req, "attachment_ids");
                        result.put("id", dao.save(id, text(req, "project_id"), text(req, "title"),
                                text(req, "create_time"), text(req, "remark"), new JSONArray(raw.isEmpty() ? "[]" : raw)));
                        break;
                    case "delete_todo_record":
                        requirePost(req); dao.delete(positiveId(text(req, "id"))); break;
                    case "upload_todo_photo":
                        requirePost(req); upload(req, result, prefix); break;
                    default: throw new IllegalArgumentException("未知action");
                }
            }
            json(resp, result);
        } catch (Exception e) {
            if (resp.isCommitted()) { log("响应输出后发生异常", e); return; }
            if (resp.getStatus() < 400) resp.setStatus(e instanceof IllegalArgumentException ? 400 : 500);
            log("第八周接口请求失败", e);
            try {
                JSONObject error = new JSONObject();
                error.put("result_code", 1);
                String message = e instanceof IllegalArgumentException || e instanceof IllegalStateException
                        ? e.getMessage() : "后端处理失败，请查看IDEA控制台的具体异常";
                error.put("result_msg", message == null ? "请求失败" : message);
                json(resp, error);
            } catch (Exception nested) { throw new IOException(nested); }
        }
    }

    private void requirePost(HttpServletRequest req) {
        if (!"POST".equals(req.getMethod())) throw new IllegalArgumentException("本操作必须POST提交");
    }

    private String apiKey() {
        return greenhouse.config.LocalSettings.get("week08.apiKey", "W08_API_KEY", null);
    }

    private void json(HttpServletResponse resp, JSONObject result) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write(result.toString());
    }

    private void upload(HttpServletRequest req, JSONObject result, String prefix) throws Exception {
        Part part = req.getPart("file");
        if (part == null || part.getSize() == 0) throw new IllegalArgumentException("请选择照片");
        if (part.getSize() > 5 * 1024 * 1024) throw new IllegalArgumentException("每张照片最多5MB");
        byte[] bytes;
        try (InputStream in = part.getInputStream()) { bytes = in.readAllBytes(); }
        String ext;
        if (bytes.length > 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216) ext = "jpg";
        else if (bytes.length > 8 && (bytes[0] & 255) == 137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71) ext = "png";
        else if (bytes.length > 6 && bytes[0] == 71 && bytes[1] == 73 && bytes[2] == 70) ext = "gif";
        else throw new IllegalArgumentException("本练习支持JPG、PNG、GIF照片，请先转换其他格式");
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) throw new IllegalArgumentException("照片文件无法解析");
        String mime = "jpg".equals(ext) ? "image/jpeg" : "image/" + ext;
        String id = UUID.randomUUID().toString();
        String filename = id + "." + ext;
        String original = Objects.toString(part.getSubmittedFileName(), "photo");
        original = original.replace('\\', '/');
        original = original.substring(original.lastIndexOf('/') + 1);
        if (original.length() > 200) original = original.substring(original.length() - 200);
        Path target = uploadRoot.resolve(filename);
        Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        try { dao.addAttachment(id, filename, original, mime); }
        catch (Exception e) { Files.deleteIfExists(target); throw e; }
        result.put("attachment_id", id); result.put("url", prefix + id);
    }

    private void sendPhoto(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String[] info = dao.attachment(text(req, "id"));
        if (info == null) { resp.sendError(404); return; }
        Path file = uploadRoot.resolve(info[0]).normalize();
        if (!file.getParent().equals(uploadRoot) || !Files.isRegularFile(file)) { resp.sendError(404); return; }
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setContentType(info[1]); resp.setContentLengthLong(Files.size(file));
        Files.copy(file, resp.getOutputStream());
    }
}
