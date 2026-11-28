package greenhouse.greenhouse;

import static greenhouse.greenhouse.GreenhouseServlet.*;

import device.dao.Db;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import org.json.*;

@WebServlet("/api/greenhouse-resources")
public class GreenhouseExtensionServlet extends HttpServlet {
  private static final Map<String, String> UNITS =
      Map.of(
          "air_temperature",
          "℃",
          "air_humidity",
          "%",
          "soil_temperature",
          "℃",
          "soil_moisture",
          "%",
          "illuminance",
          "lx",
          "control",
          "-");
  private static final Set<String> WRITE =
      Set.of(
          "device_create", "unbind", "bind", "batch_save", "batch_delete", "geo_save", "geo_clear");

  protected void doGet(HttpServletRequest q, HttpServletResponse p) throws IOException {
    run(q, p);
  }

  protected void doPost(HttpServletRequest q, HttpServletResponse p) throws IOException {
    run(q, p);
  }

  private void run(HttpServletRequest q, HttpServletResponse p) throws IOException {
    q.setCharacterEncoding("UTF-8");
    p.setContentType("application/json;charset=UTF-8");
    p.setHeader("Cache-Control", "no-store");
    var s = q.getSession(false);
    Object user = s == null ? null : s.getAttribute("loginUser");
    if (!(user instanceof Map)) {
      reply(p, 401, "请先登录", null);
      return;
    }
    String role = String.valueOf(((Map<?, ?>) user).get("roleId")),
        a = param(q, "action", "batches");
    if (!Set.of("1", "2", "3").contains(role) || WRITE.contains(a) && role.equals("3")) {
      reply(p, 403, "无维护权限", null);
      return;
    }
    if (WRITE.contains(a) && !q.getMethod().equals("POST")) {
      reply(p, 405, "维护必须POST", null);
      return;
    }
    if (WRITE.contains(a) && !CsrfGuard.valid(q)) {
      reply(p, 403, "页面验证已失效，请刷新后重试", null);
      return;
    }
    try (Connection c = new Db("greenhouse_system").getConnection()) {
      if (a.equals("map")) {
        reply(
            p,
            200,
            "成功",
            rows(
                c,
                "SELECT"
                    + " g.id,g.greenhouse_code,g.name,g.location,g.area,g.status,x.latitude,x.longitude"
                    + " FROM greenhouse_file g LEFT JOIN greenhouse_geo x ON x.greenhouse_id=g.id"
                    + " WHERE g.is_deleted=0 ORDER BY g.greenhouse_code,g.id",
                List.of()));
        return;
      }
      if (a.equals("geo_save") || a.equals("geo_clear")) {
        long gid = positive(q, "greenhouse_id");
        c.setAutoCommit(false);
        try {
          validGreenhouse(c, gid, false);
          if (a.equals("geo_clear"))
            execute(c, "DELETE FROM greenhouse_geo WHERE greenhouse_id=?", List.of(gid));
          else {
            double lat = Double.parseDouble(param(q, "latitude", "")),
                lon = Double.parseDouble(param(q, "longitude", ""));
            if (!Double.isFinite(lat)
                || !Double.isFinite(lon)
                || lat < -90
                || lat > 90
                || lon < -180
                || lon > 180) throw new IllegalArgumentException("经纬度超出范围");
            execute(
                c,
                "INSERT INTO greenhouse_geo(greenhouse_id,latitude,longitude) VALUES(?,?,?) ON"
                    + " DUPLICATE KEY UPDATE latitude=VALUES(latitude),longitude=VALUES(longitude)",
                List.of(gid, lat, lon));
          }
          c.commit();
        } catch (SQLException | JSONException | RuntimeException e) {
          c.rollback();
          throw e;
        } finally {
          c.setAutoCommit(true);
        }
        reply(p, 200, a.equals("geo_clear") ? "定位已清除" : "坐标已保存", null);
        return;
      }
      if (a.equals("device_create")) {
        String code = required(q, "device_code", 255),
            name = required(q, "device_name", 255),
            type = required(q, "device_type", 32);
        if (!Set.of("sensor", "control").contains(type))
          throw new IllegalArgumentException("请选择传感器或控制设备");
        execute(
            c,
            "INSERT INTO"
                + " device_file(device_id,device_name,device_type,creator_id,creator,create_time)"
                + " VALUES(?,?,?,?,?,NOW())",
            List.of(
                code,
                name,
                type,
                String.valueOf(((Map<?, ?>) user).get("id")),
                String.valueOf(((Map<?, ?>) user).get("displayName"))));
        reply(p, 200, "设备档案已新增，请选择大棚进行绑定", null);
        return;
      }
      if (a.equals("device_history")) {
        reply(
            p,
            200,
            "成功",
            rows(
                c,
                "SELECT h.greenhouse_id,COALESCE(h.greenhouse_name,g.name)"
                    + " greenhouse_name,h.greenhouse_code,h.device_code,h.device_name,h.metric_type,h.unit,h.control_purpose,h.bound_at,h.unbound_at,u.display_name"
                    + " operator_name FROM greenhouse_device_binding_history h JOIN greenhouse_file"
                    + " g ON g.id=h.greenhouse_id LEFT JOIN todo_login_user u ON u.id=h.operator_id"
                    + " WHERE h.device_pk=? ORDER BY h.id DESC LIMIT 100",
                List.of(positive(q, "device_pk"))));
        return;
      }
      if (a.equals("unbind")) {
        long did = positive(q, "device_pk"), expected = positive(q, "greenhouse_id");
        c.setAutoCommit(false);
        try {
          // 与绑定统一按大棚→设备加锁，避免与档案删除及重复绑定交错。
          validGreenhouse(c, expected, false);
          if (rows(c, "SELECT id FROM device_file WHERE id=? FOR UPDATE", List.of(did)).length()
              == 0) throw new IllegalArgumentException("设备不存在");
          JSONArray found =
              rows(
                  c,
                  "SELECT * FROM greenhouse_device_binding WHERE device_pk=? FOR UPDATE",
                  List.of(did));
          if (found.length() == 0 || found.getJSONObject(0).getLong("greenhouse_id") != expected) {
            c.rollback();
            reply(p, 409, "设备归属已变化，请刷新后重试", null);
            return;
          }
          JSONObject current = found.getJSONObject(0);
          Object actor = ((Map<?, ?>) user).get("id");
          if (actor == null) throw new IllegalArgumentException("登录信息不完整，请重新登录");
          execute(
              c,
              "INSERT INTO"
                  + " greenhouse_device_binding_history(device_pk,greenhouse_id,metric_type,unit,unbound_at,operator_id,control_purpose,bound_at,device_code,device_name,greenhouse_code,greenhouse_name)"
                  + " SELECT"
                  + " b.device_pk,b.greenhouse_id,b.metric_type,b.unit,NOW(),?,b.control_purpose,b.bound_at,d.device_id,d.device_name,g.greenhouse_code,g.name"
                  + " FROM greenhouse_device_binding b JOIN device_file d ON d.id=b.device_pk JOIN"
                  + " greenhouse_file g ON g.id=b.greenhouse_id WHERE b.device_pk=? AND"
                  + " b.greenhouse_id=?",
              List.of(Long.parseLong(String.valueOf(actor)), did, expected));
          execute(
              c,
              "DELETE FROM greenhouse_device_binding WHERE device_pk=? AND greenhouse_id=?",
              List.of(did, expected));
          c.commit();
        } catch (SQLException | JSONException | RuntimeException e) {
          c.rollback();
          throw e;
        } finally {
          c.setAutoCommit(true);
        }
        reply(p, 200, "已解除绑定，原归属已保存到历史记录", null);
        return;
      }
      if (a.equals("devices")) {
        List<Object> v = new ArrayList<>();
        String where = " WHERE 1=1";
        if (!param(q, "greenhouse_id", "").isEmpty()) {
          where += " AND b.greenhouse_id=?";
          v.add(positive(q, "greenhouse_id"));
        }
        if (param(q, "unbound", "").equals("1")) where += " AND b.device_pk IS NULL";
        if (!param(q, "keyword", "").isEmpty()) {
          where += " AND (d.device_id LIKE ? OR d.device_name LIKE ?)";
          v.add("%" + param(q, "keyword", "") + "%");
          v.add(v.get(v.size() - 1));
        }
        String from =
            " FROM device_file d LEFT JOIN greenhouse_device_binding b ON b.device_pk=d.id LEFT"
                + " JOIN greenhouse_file g ON g.id=b.greenhouse_id";
        String columns =
            "SELECT d.id device_pk,d.device_id"
                + " device_code,d.device_name,d.device_type,b.greenhouse_id,g.name"
                + " greenhouse_name,b.metric_type,b.unit,b.control_purpose";
        // 旧调用不传page时保留数组；页面分页返回全条件统计，避免以当页冒充总数。
        if (q.getParameter("page") == null) {
          reply(p, 200, "成功", rows(c, columns + from + where + " ORDER BY d.id", v));
          return;
        }
        int page = Integer.parseInt(param(q, "page", "1")),
            size = Integer.parseInt(param(q, "size", "10"));
        if (page < 1 || page > 1000000 || !Set.of(10, 20, 50).contains(size))
          throw new IllegalArgumentException("分页参数无效");
        JSONObject stats =
            rows(
                    c,
                    "SELECT COUNT(*) total,COALESCE(SUM(b.device_pk IS NOT NULL),0) bound"
                        + from
                        + where,
                    v)
                .getJSONObject(0);
        List<Object> paged = new ArrayList<>(v);
        paged.add(size);
        paged.add((page - 1) * size);
        reply(
            p,
            200,
            "成功",
            new JSONObject()
                .put("total", stats.get("total"))
                .put("bound", stats.get("bound"))
                .put(
                    "items",
                    rows(c, columns + from + where + " ORDER BY d.id LIMIT ? OFFSET ?", paged)));
        return;
      }
      if (a.equals("bind")) {
        long gid = positive(q, "greenhouse_id"), did = positive(q, "device_pk");
        String metric = param(q, "metric_type", "");
        if (!UNITS.containsKey(metric)) throw new IllegalArgumentException("指标无效");
        String purpose = param(q, "control_purpose", "");
        if (metric.equals("control")
            && !Set.of("ventilation", "cooling", "irrigation").contains(purpose))
          throw new IllegalArgumentException("请选择通风、降温或灌溉用途");
        if (!metric.equals("control")) purpose = "";
        c.setAutoCommit(false);
        try {
          validGreenhouse(c, gid, true);
          JSONArray d =
              rows(c, "SELECT id,device_type FROM device_file WHERE id=? FOR UPDATE", List.of(did));
          if (d.length() == 0) throw new IllegalArgumentException("设备不存在");
          String type = d.getJSONObject(0).optString("device_type");
          if (!type.equals(metric.equals("control") ? "control" : "sensor"))
            throw new IllegalArgumentException("设备类型与指标不匹配：sensor仅传感器指标，control仅控制用途");
          if (rows(
                      c,
                      "SELECT device_pk FROM greenhouse_device_binding WHERE device_pk=?",
                      List.of(did))
                  .length()
              > 0) {
            reply(p, 409, "设备已有绑定，请先解除原绑定", null);
            c.rollback();
            return;
          }
          execute(
              c,
              "INSERT INTO"
                  + " greenhouse_device_binding(device_pk,greenhouse_id,metric_type,unit,control_purpose,bound_at)"
                  + " VALUES(?,?,?,?,?,NOW())",
              List.of(did, gid, metric, UNITS.get(metric), purpose));
          c.commit();
        } catch (SQLException | JSONException | RuntimeException e) {
          c.rollback();
          throw e;
        } finally {
          c.setAutoCommit(true);
        }
        reply(p, 200, "绑定成功", null);
        return;
      }
      if (a.equals("batch_save")) {
        if (q.getParameter("started_at") != null || q.getParameter("harvested_at") != null)
          throw new IllegalArgumentException("开始与收获时间由服务器动作确定，不接受客户端指定");
        long gid = positive(q, "greenhouse_id");
        String code = required(q, "batch_code", 64),
            crop = required(q, "crop_name", 100),
            variety = param(q, "variety", "");
        LocalDate date = LocalDate.parse(required(q, "planting_date", 10));
        int days = Integer.parseInt(param(q, "growth_days", "0"));
        if (variety.length() > 100) throw new IllegalArgumentException("品种最多100字");
        if (days < 1 || days > 3650) throw new IllegalArgumentException("生长周期应为1—3650天");
        String status = param(q, "status", "");
        if (!Set.of("planned", "growing", "harvested", "canceled").contains(status))
          throw new IllegalArgumentException("种植状态无效");
        String remark = param(q, "remark", "");
        if (remark.length() > 1000) throw new IllegalArgumentException("备注过长");
        c.setAutoCommit(false);
        try {
          String oldBatchStatus = null;
          List<Object> v =
              new ArrayList<>(
                  List.of(
                      gid, code, crop, variety, java.sql.Date.valueOf(date), days, status, remark));
          if (param(q, "id", "").isEmpty()) {
            validGreenhouse(c, gid, true);
            if (!status.equals("planned")) throw new IllegalArgumentException("新增批次必须为计划，再执行开始动作");
            execute(
                c,
                "INSERT INTO"
                    + " planting_batch(greenhouse_id,batch_code,crop_name,variety,planting_date,growth_days,status,remark)"
                    + " VALUES(?,?,?,?,?,?,?,?)",
                v);
          } else {
            long bid = positive(q, "id");
            JSONArray hint =
                rows(
                    c,
                    "SELECT greenhouse_id FROM planting_batch WHERE id=? AND is_deleted=0",
                    List.of(bid));
            if (hint.length() == 0) throw new IllegalArgumentException("批次不存在");
            long original = hint.getJSONObject(0).getLong("greenhouse_id");
            // 所有保存采用大棚ID升序→批次锁；获取锁后再次核对提示归属。
            for (long locked : new TreeSet<Long>(List.of(original, gid)))
              validGreenhouse(c, locked, false);
            JSONArray old =
                rows(
                    c,
                    "SELECT * FROM planting_batch WHERE id=? AND is_deleted=0 FOR UPDATE",
                    List.of(bid));
            if (old.length() == 0) throw new IllegalArgumentException("批次不存在");
            JSONObject previous = old.getJSONObject(0);
            String oldStatus = previous.getString("status");
            oldBatchStatus = oldStatus;
            if (previous.getLong("greenhouse_id") != original)
              throw new IllegalArgumentException("批次归属已变化，请刷新");
            if ((oldStatus.equals("harvested") || oldStatus.equals("canceled")))
              throw new IllegalArgumentException("已结束批次保留历史，不可修改");
            if (oldStatus.equals("planned") && status.equals("harvested"))
              throw new IllegalArgumentException("计划必须先开始再收获");
            if (oldStatus.equals("growing") && status.equals("planned"))
              throw new IllegalArgumentException("生长中不能退回计划");
            if (previous.getLong("greenhouse_id") != gid && !oldStatus.equals("planned"))
              throw new IllegalArgumentException("已开始种植的批次不可更换大棚");
            String greenhouseStatus =
                rows(c, "SELECT status FROM greenhouse_file WHERE id=?", List.of(gid))
                    .getJSONObject(0)
                    .getString("status");
            if (original == gid && greenhouseStatus.equals("disabled")) {
              if (!(status.equals("canceled")
                  || oldStatus.equals("growing") && status.equals("harvested")))
                throw new IllegalArgumentException("停用棚仅允许已有批次收获或取消");
              if (!code.equals(previous.getString("batch_code"))
                  || !crop.equals(previous.getString("crop_name"))
                  || !variety.equals(previous.getString("variety"))
                  || !date.toString().equals(previous.get("planting_date").toString())
                  || days != previous.getInt("growth_days")
                  || !remark.equals(previous.getString("remark")))
                throw new IllegalArgumentException("停用棚收尾不得改写历史字段");
              execute(c, "UPDATE planting_batch SET status=? WHERE id=?", List.of(status, bid));
            } else {
              validGreenhouse(c, gid, true);
              v.add(bid);
              execute(
                  c,
                  "UPDATE planting_batch SET"
                      + " greenhouse_id=?,batch_code=?,crop_name=?,variety=?,planting_date=?,growth_days=?,status=?,remark=?"
                      + " WHERE id=?",
                  v);
            }
          }
          if (!param(q, "id", "").isEmpty()) {
            long bid = positive(q, "id");
            if (status.equals("growing") && "planned".equals(oldBatchStatus))
              execute(
                  c,
                  "UPDATE planting_batch SET started_at=COALESCE(started_at,NOW()) WHERE id=? AND"
                      + " status='growing'",
                  List.of(bid));
            if (status.equals("harvested")) {
              JSONArray clock =
                  rows(c, "SELECT started_at FROM planting_batch WHERE id=?", List.of(bid));
              Object start = clock.getJSONObject(0).opt("started_at");
              if (start != null
                  && start != JSONObject.NULL
                  && Timestamp.valueOf(start.toString())
                      .after(new Timestamp(System.currentTimeMillis())))
                throw new IllegalArgumentException("收获时间不能早于开始时间");
              execute(
                  c,
                  "UPDATE planting_batch SET harvested_at=NOW() WHERE id=? AND status='harvested'",
                  List.of(bid));
            }
          }
          c.commit();
        } catch (SQLException | JSONException | RuntimeException e) {
          c.rollback();
          throw e;
        } finally {
          c.setAutoCommit(true);
        }
        reply(p, 200, "种植批次已保存", null);
        return;
      }
      if (a.equals("batch_delete")) {
        if (execute(
                c,
                "UPDATE planting_batch SET is_deleted=1 WHERE id=? AND is_deleted=0 AND"
                    + " status='planned'",
                List.of(positive(q, "id")))
            == 0) throw new IllegalArgumentException("仅允许逻辑删除未开始的计划批次；历史记录须保留");
        reply(p, 200, "已逻辑删除", null);
        return;
      }
      if (a.equals("batch_detail")) {
        JSONArray r =
            rows(
                c,
                "SELECT b.*,g.name greenhouse_name FROM planting_batch b JOIN greenhouse_file g ON"
                    + " g.id=b.greenhouse_id WHERE b.id=? AND b.is_deleted=0",
                List.of(positive(q, "id")));
        if (r.length() == 0) {
          reply(p, 404, "批次不存在", null);
          return;
        }
        reply(p, 200, "成功", r.get(0));
        return;
      }
      if (!Set.of("batches", "batch_stats", "batch_export").contains(a))
        throw new IllegalArgumentException("未知操作");
      List<Object> v = new ArrayList<>();
      String where = " WHERE b.is_deleted=0 AND g.is_deleted=0";
      for (String key :
          List.of("greenhouse_id", "status", "crop_name", "variety", "date_from", "date_to")) {
        String value = param(q, key, "");
        if (value.isEmpty()) continue;
        if (key.startsWith("date_")) {
          where += " AND b.planting_date" + (key.equals("date_from") ? ">=" : "<=") + "?";
          v.add(java.sql.Date.valueOf(LocalDate.parse(value)));
        } else if (key.equals("crop_name") || key.equals("variety")) {
          where += " AND b." + key + " LIKE ?";
          v.add("%" + value + "%");
        } else {
          where += " AND b." + key + "=?";
          v.add(value);
        }
      }
      if (!param(q, "date_from", "").isEmpty()
          && !param(q, "date_to", "").isEmpty()
          && LocalDate.parse(param(q, "date_from", ""))
              .isAfter(LocalDate.parse(param(q, "date_to", ""))))
        throw new IllegalArgumentException("起始日期不能晚于结束日期");
      String from = " FROM planting_batch b JOIN greenhouse_file g ON g.id=b.greenhouse_id";
      if (a.equals("batch_stats")) {
        reply(
            p,
            200,
            "成功",
            new JSONObject()
                .put(
                    "crops",
                    rows(
                        c,
                        "SELECT b.crop_name,COUNT(*) count"
                            + from
                            + where
                            + " GROUP BY b.crop_name ORDER BY count DESC,b.crop_name",
                        v))
                .put(
                    "statuses",
                    rows(
                        c,
                        "SELECT b.status,COUNT(*) count"
                            + from
                            + where
                            + " GROUP BY b.status ORDER BY b.status",
                        v)));
        return;
      }
      String sort = param(q, "sort", "planting_date"), dir = param(q, "dir", "desc");
      if (!Set.of("planting_date", "batch_code", "created_at").contains(sort)
          || !Set.of("asc", "desc").contains(dir)) throw new IllegalArgumentException("排序无效");
      String sql =
          "SELECT b.*,g.name greenhouse_name"
              + from
              + where
              + " ORDER BY b."
              + sort
              + " "
              + dir
              + ",b.id "
              + dir;
      if (a.equals("batch_export")) {
        JSONArray data = rows(c, sql, v);
        p.setContentType("text/csv;charset=UTF-8");
        p.setHeader("Content-Disposition", "attachment; filename=planting-batches.csv");
        var w = p.getWriter();
        w.write('\ufeff');
        w.println("批次,大棚,作物,品种,种植日期,周期(天),状态,备注");
        for (int i = 0; i < data.length(); i++) {
          JSONObject r = data.getJSONObject(i);
          StringJoiner j = new StringJoiner(",");
          for (String k :
              List.of(
                  "batch_code",
                  "greenhouse_name",
                  "crop_name",
                  "variety",
                  "planting_date",
                  "growth_days",
                  "status",
                  "remark")) j.add(csv(r.optString(k, "")));
          w.println(j);
        }
        return;
      }
      int page = Integer.parseInt(param(q, "page", "1")),
          size = Integer.parseInt(param(q, "size", "10"));
      if (page < 1 || page > 1000000 || size < 1 || size > 100)
        throw new IllegalArgumentException("分页无效");
      Object total =
          rows(c, "SELECT COUNT(*) total" + from + where, v).getJSONObject(0).get("total");
      v.add(size);
      v.add((page - 1) * size);
      reply(
          p,
          200,
          "成功",
          new JSONObject().put("total", total).put("items", rows(c, sql + " LIMIT ? OFFSET ?", v)));
    } catch (java.time.DateTimeException | IllegalArgumentException e) {
      reply(p, 400, "参数错误：" + e.getMessage(), null);
    } catch (SQLIntegrityConstraintViolationException e) {
      reply(p, 409, "编号重复或关联冲突，请检查设备或批次编号", null);
    } catch (SQLException | JSONException e) {
      getServletContext().log("greenhouse resources", e);
      reply(p, 500, "操作失败，请确认所需迁移已执行（14号扩展、16号设备操作）", null);
    }
  }

  static long positive(HttpServletRequest q, String key) {
    long n = Long.parseLong(param(q, key, "0"));
    if (n <= 0) throw new IllegalArgumentException(key + "无效");
    return n;
  }

  static String required(HttpServletRequest q, String k, int max) {
    String v = param(q, k, "");
    if (v.isEmpty() || v.length() > max) throw new IllegalArgumentException(k + "必填且不能超长");
    return v;
  }

  static void validGreenhouse(Connection c, long id, boolean active)
      throws SQLException, JSONException {
    if (rows(
                c,
                "SELECT id FROM greenhouse_file WHERE id=? AND is_deleted=0"
                    + (active ? " AND status='enabled'" : "")
                    + " FOR UPDATE",
                List.of(id))
            .length()
        == 0) throw new IllegalArgumentException("大棚不存在、已删除或未启用");
  }
}
