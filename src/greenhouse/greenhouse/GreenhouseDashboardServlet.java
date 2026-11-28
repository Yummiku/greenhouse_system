package greenhouse.greenhouse;

import static greenhouse.greenhouse.GreenhouseServlet.*;

import device.dao.Db;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.json.*;

@WebServlet("/api/greenhouse-dashboard")
public class GreenhouseDashboardServlet extends HttpServlet {
  protected void doGet(HttpServletRequest q, HttpServletResponse p) throws IOException {
    p.setContentType("application/json;charset=UTF-8");
    p.setHeader("Cache-Control", "no-store");
    Object user =
        q.getSession(false) == null ? null : q.getSession(false).getAttribute("loginUser");
    if (!(user instanceof Map)) {
      reply(p, 401, "请先登录", null);
      return;
    }
    if (!Set.of("1", "2", "3").contains(String.valueOf(((Map<?, ?>) user).get("roleId")))) {
      reply(p, 403, "无权限", null);
      return;
    }
    try (Connection c = new Db("greenhouse_system").getConnection()) {
      LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai")),
          start = LocalDate.parse(param(q, "date_from", today.minusDays(6).toString())),
          end = LocalDate.parse(param(q, "date_to", today.toString()));
      if (start.isAfter(end)) throw new IllegalArgumentException("日期起止倒置");
      List<Object> v = new ArrayList<>();
      String where = " WHERE g.is_deleted=0";
      if (!param(q, "greenhouse_id", "").isEmpty()) {
        where += " AND g.id=?";
        v.add(Long.parseLong(param(q, "greenhouse_id", "")));
      }
      JSONObject data =
          new JSONObject()
              .put("scope", "当前登录用户可读农业业务范围，当前状态存量")
              .put("date_from", start.toString())
              .put("date_to_exclusive", end.plusDays(1).toString())
              .put("time_scope", "日期供集成页统一显示；本页三类指标均为当前状态，不按日期伪造历史存量")
              .put(
                  "summary",
                  rows(
                          c,
                          "SELECT COUNT(*) total,COALESCE(SUM(g.area),0)"
                              + " area,COALESCE(SUM(g.status='enabled'),0)"
                              + " enabled,COALESCE(SUM(g.status='disabled'),0) disabled FROM"
                              + " greenhouse_file g"
                              + where,
                          v)
                      .get(0))
              .put(
                  "devices",
                  rows(
                      c,
                      "SELECT g.id,g.name,COUNT(b.device_pk) count FROM greenhouse_file g LEFT JOIN"
                          + " greenhouse_device_binding b ON b.greenhouse_id=g.id"
                          + where
                          + " GROUP BY g.id,g.name ORDER BY g.id",
                      v))
              .put(
                  "crops",
                  rows(
                      c,
                      "SELECT b.crop_name,COUNT(*) count FROM planting_batch b JOIN greenhouse_file"
                          + " g ON g.id=b.greenhouse_id"
                          + where
                          + " AND b.is_deleted=0 AND b.status='growing' GROUP BY b.crop_name ORDER"
                          + " BY count DESC,b.crop_name",
                      v))
              .put("crop_basis", "当前生长中批次数比例，不是面积或产量比例")
              .put("updated_at", ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).toString());
      reply(p, 200, "成功", data);
    } catch (IllegalArgumentException | DateTimeException e) {
      reply(p, 400, "筛选参数无效", null);
    } catch (SQLException | JSONException e) {
      getServletContext().log("greenhouse dashboard", e);
      reply(p, 500, "统计读取失败", null);
    }
  }
}
