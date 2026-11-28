package greenhouse.greenhouse;

import static greenhouse.greenhouse.GreenhouseServlet.*;

import device.dao.Db;
import greenhouse.config.LocalSettings;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import org.json.*;

/** 复用微信签发的token，不接受PC Session或客户端user_id作为移动身份。 */
@WebServlet("/api/mini/greenhouses")
public class MiniGreenhouseServlet extends HttpServlet {
  protected void doGet(HttpServletRequest q, HttpServletResponse p) throws IOException {
    p.setContentType("application/json;charset=UTF-8");
    p.setHeader("Cache-Control", "no-store");
    String appid = LocalSettings.get("week08.wxAppId", "WEEK08_WX_APPID", "");
    if (appid.isBlank()) {
      reply(p, 503, "小程序AppID尚未配置", null);
      return;
    }
    String token = q.getHeader("X-Week08-Token");
    if (token == null || !token.matches("[a-f0-9]{64}")) {
      reply(p, 401, "请重新登录小程序", null);
      return;
    }
    try (Connection c = new Db("greenhouse_system").getConnection()) {
      String prefix =
          rows(
                          c,
                          "SELECT TABLE_NAME FROM information_schema.TABLES WHERE"
                              + " TABLE_SCHEMA=DATABASE() AND TABLE_NAME='week08_login_session'",
                          List.of())
                      .length()
                  > 0
              ? "week08"
              : "wechat";
      String hash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(token.getBytes(StandardCharsets.UTF_8)));
      JSONArray users =
          rows(
              c,
              "SELECT u.id,u.role_id,u.status,u.updated_at,s.created_at issued_at FROM "
                  + prefix
                  + "_login_session s JOIN "
                  + prefix
                  + "_user_bind b ON b.appid=s.appid AND b.openid=s.openid JOIN todo_login_user u"
                  + " ON u.id=b.user_id WHERE s.token_hash=? AND s.appid=? AND s.expires_at>NOW()",
              List.of(hash, appid));
      if (users.length() != 1) {
        reply(p, 401, "登录已过期或未绑定平台账号", null);
        return;
      }
      JSONObject u = users.getJSONObject(0);
      if (!u.getString("status").equals("active")
          || !Set.of(1, 2, 3).contains(u.getInt("role_id"))
          || !Timestamp.valueOf(u.getString("issued_at"))
              .after(Timestamp.valueOf(u.getString("updated_at")))) {
        reply(p, 401, "账号状态或权限已变化，请重新登录", null);
        return;
      }
      if (q.getParameter("user_id") != null || q.getParameter("role_id") != null) {
        reply(p, 403, "不得指定其他身份", null);
        return;
      }
      String action = param(q, "action", "list");
      if (action.equals("detail")) {
        long gid = id(q);
        JSONArray data = rows(c, select() + " WHERE g.id=? AND g.is_deleted=0", List.of(gid));
        if (data.length() == 0) {
          reply(p, 404, "大棚不存在", null);
          return;
        }
        reply(
            p,
            200,
            "成功",
            new JSONObject()
                .put("greenhouse", data.get(0))
                .put(
                    "devices",
                    rows(
                        c,
                        "SELECT d.id device_pk,d.device_id"
                            + " device_code,d.device_name,d.device_type,b.metric_type,b.unit,b.control_purpose"
                            + " FROM greenhouse_device_binding b JOIN device_file d ON"
                            + " d.id=b.device_pk WHERE b.greenhouse_id=? ORDER BY d.id",
                        List.of(gid))));
        return;
      }
      if (!action.equals("list")) {
        reply(p, 403, "移动入口只允许读取", null);
        return;
      }
      int page = Integer.parseInt(param(q, "page", "1")),
          size = Integer.parseInt(param(q, "size", "10"));
      if (page < 1 || page > 1000000 || size < 1 || size > 100)
        throw new IllegalArgumentException();
      String where = " WHERE g.is_deleted=0";
      List<Object> v = new ArrayList<>();
      String keyword = param(q, "keyword", "");
      if (!keyword.isEmpty()) {
        where += " AND (g.name LIKE ? OR g.greenhouse_code LIKE ?)";
        v.add("%" + keyword + "%");
        v.add("%" + keyword + "%");
      }
      Object total =
          rows(c, "SELECT COUNT(*) total FROM greenhouse_file g" + where, v)
              .getJSONObject(0)
              .get("total");
      v.add(size);
      v.add((page - 1) * size);
      reply(
          p,
          200,
          "成功",
          new JSONObject()
              .put("total", total)
              .put("items", rows(c, select() + where + " ORDER BY g.id LIMIT ? OFFSET ?", v)));
    } catch (IllegalArgumentException e) {
      reply(p, 400, "查询参数无效", null);
    } catch (Exception e) {
      getServletContext().log("mini greenhouse", e);
      reply(p, 503, "移动查询服务暂不可用", null);
    }
  }

  protected void doPost(HttpServletRequest q, HttpServletResponse p) throws IOException {
    reply(p, 403, "小程序大棚入口只读", null);
  }
}
