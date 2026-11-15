package week08.location;

import jakarta.servlet.http.*;
import org.json.JSONObject;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 独立接口，不替换第一题的MiniTodoServlet。 */
public class LocationServlet extends HttpServlet {
    private final LocationDao dao = new LocationDao();
    private String text(HttpServletRequest r, String name) {
        String value = r.getParameter(name); return value == null ? "" : value.trim();
    }
    @Override protected void doGet(HttpServletRequest r,HttpServletResponse s) throws IOException { handle(r,s); }
    @Override protected void doPost(HttpServletRequest r,HttpServletResponse s) throws IOException { handle(r,s); }
    private void handle(HttpServletRequest r,HttpServletResponse s) throws IOException {
        s.setContentType("application/json;charset=UTF-8"); s.setHeader("Cache-Control","no-store");
        try {
            r.setCharacterEncoding("UTF-8");
            String action=text(r,"action");
            String key=greenhouse.config.LocalSettings.get("week08.apiKey","W08_API_KEY",null);
            JSONObject result=new JSONObject(); result.put("result_code",0); result.put("result_msg","ok");
            if ("ping".equals(action)) {
                dao.ping(); result.put("module","week08-location"); result.put("key_configured",key!=null && key.length()>=16);
            } else {
                if (key==null || key.length()<16) throw new IllegalStateException("请配置Tomcat的week08.apiKey参数");
                if (!key.equals(r.getHeader("X-Week08-Key"))) {
                    s.setStatus(401); throw new IllegalArgumentException("位置接口访问码不一致");
                }
                if ("list_locations".equals(action)) result.put("aaData",dao.list());
                else if ("report_location".equals(action)) {
                    if (!"POST".equals(r.getMethod())) throw new IllegalArgumentException("位置上报必须POST");
                    // 第2题使用设备标识；第3题接入时改从后端已校验的登录凭证取得用户标识。
                    dao.report(text(r,"user_id"),text(r,"display_name"),Double.parseDouble(text(r,"latitude")),
                            Double.parseDouble(text(r,"longitude")),text(r,"collected_at"),text(r,"source"));
                } else throw new IllegalArgumentException("未知action");
            }
            result.put("server_time",LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            s.getWriter().write(result.toString());
        } catch (Exception e) {
            if (s.getStatus()<400) s.setStatus(e instanceof IllegalArgumentException?400:500);
            log("位置接口失败",e);
            try {
                JSONObject error=new JSONObject(); error.put("result_code",1);
                error.put("result_msg", e instanceof IllegalArgumentException || e instanceof IllegalStateException
                        ? String.valueOf(e.getMessage()) : "位置接口异常，请查看IDEA控制台");
                s.getWriter().write(error.toString());
            } catch (Exception nested) { throw new IOException(nested); }
        }
    }
}
