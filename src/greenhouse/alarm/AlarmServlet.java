package greenhouse.alarm;

import greenhouse.greenhouse.CsrfGuard;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import org.json.*;
import static greenhouse.alarm.AlarmDatabase.*;

/** Protected business API. There is intentionally no public sample-processing or arbitrary CRUD action. */
@WebServlet("/api/alarms")
public final class AlarmServlet extends HttpServlet {
    private static final Set<String> WRITES=Set.of("confirm","handle","close_invalid","retry");
    @Override protected void doGet(HttpServletRequest q,HttpServletResponse p)throws IOException{run(q,p);}
    @Override protected void doPost(HttpServletRequest q,HttpServletResponse p)throws IOException{run(q,p);}
    private void run(HttpServletRequest q,HttpServletResponse p)throws IOException {
        q.setCharacterEncoding("UTF-8");p.setContentType("application/json;charset=UTF-8");p.setHeader("Cache-Control","no-store");
        var session=q.getSession(false);var user=session==null?null:session.getAttribute("loginUser");
        if(!(user instanceof Map<?,?> actor)){reply(p,401,"请先登录",null);return;}
        String role=String.valueOf(actor.get("roleId")),action=q.getParameter("action");
        if(!Set.of("1","2","3").contains(role)){reply(p,403,"无访问权限",null);return;}
        if(action==null)action="list";
        if(WRITES.contains(action)) {
            if(!q.getMethod().equals("POST")){reply(p,405,"维护操作必须使用POST",null);return;}
            if(role.equals("3")){reply(p,403,"普通用户只读",null);return;}
            if(!CsrfGuard.valid(q)){reply(p,403,"操作令牌失效，请刷新后重试",null);return;}
        }
        try {
            if(action.equals("status")) {
                Object status=getServletContext().getAttribute("sampleAlarmStatus");
                reply(p,200,"状态已读取",Map.of("processor_status",Objects.toString(status,"NOT_READY"),
                        "processor_registered",getServletContext().getAttribute("sampleAlarmProcessor") instanceof SampleAlarmProcessor));return;
            }
            AlarmEventActions service=new AlarmEventActions();
            if(WRITES.contains(action)) {
                long actorId=positive(String.valueOf(actor.get("id")));
                long id=positive(q.getParameter(action.equals("retry")?"sampleId":"id"));
                switch(action) {
                    case "confirm" -> service.confirm(id,actorId);
                    case "handle" -> service.recordHandling(id,actorId,q.getParameter("requestKey"),q.getParameter("note"));
                    case "close_invalid" -> service.closeInvalid(id,actorId,q.getParameter("note"));
                    case "retry" -> AlarmSampleInbox.retryFailed(id,actorId);
                    default -> throw new IllegalArgumentException("不支持的操作");
                }
                reply(p,200,"操作已处理",Map.of("id",String.valueOf(id)));return;
            }
            if(action.equals("detail")){reply(p,200,"成功",service.detail(positive(q.getParameter("id"))));return;}
            if(action.equals("tasks")) {
                if(role.equals("3")){reply(p,403,"仅管理员可查看判定任务",null);return;}
                try(var c=open()) {
                    reply(p,200,"成功",rows(c,"SELECT sample_id,greenhouse_id,metric_type,sample_time,state,outcome,event_id,attempts,failures,next_retry_at,last_error_code,completed_at FROM alarm_sample_task WHERE state<>'done' ORDER BY next_retry_at,sample_id LIMIT 200"));return;
                }
            }
            if(!Set.of("list","stats").contains(action))throw new IllegalArgumentException("不支持的操作");
            Filter filter=filter(q);
            try(var c=open()) {
                String where=filter.where();Object[] args=filter.args().toArray();
                if(action.equals("stats")) {
                    var stats=one(c,"SELECT COUNT(*) total,COALESCE(SUM(status='pending'),0) pending,COALESCE(SUM(status='processing'),0) processing,COALESCE(SUM(status='closed'),0) closed,COALESCE(SUM(close_reason='normal'),0) normal_closed FROM alarm_event"+where,args);
                    long total=number(stats,"total");
                    stats.put("close_rate",total==0?null:BigDecimal.valueOf(number(stats,"closed")).divide(BigDecimal.valueOf(total),6,RoundingMode.HALF_UP));
                    stats.put("directions",rows(c,"SELECT direction,COUNT(*) count FROM alarm_event"+where+" GROUP BY direction ORDER BY direction",args));
                    stats.put("source_basis","首次触发采样来源");stats.put("time_basis","首次触发采样时间，Asia/Shanghai");
                    reply(p,200,"成功",stats);return;
                }
                int page=integer(q,"page",1),size=integer(q,"size",10);
                if(page<1 || !Set.of(10,20,50).contains(size) || page>1000000)throw new IllegalArgumentException("分页参数无效");
                var count=one(c,"SELECT COUNT(*) total FROM alarm_event"+where,args);
                List<Object> params=new ArrayList<>(filter.args());params.add(size);params.add((page-1)*size);
                reply(p,200,"成功",Map.of("total",number(count,"total"),"items",rows(c,"SELECT * FROM alarm_event"+where+" ORDER BY first_triggered_at DESC,id DESC LIMIT ? OFFSET ?",params.toArray())));
            }
        }catch(SecurityException e){reply(p,403,e.getMessage(),null);}
        catch(IllegalArgumentException e){reply(p,400,e.getMessage(),null);}
        catch(IllegalStateException e){reply(p,409,e.getMessage(),null);}
        catch(SQLException e){getServletContext().log("Alarm API database unavailable; verify migrations 21/31/33");reply(p,503,"报警服务暂不可用，请检查数据库及21/31/33迁移",null);}
    }
    private record Filter(String where,List<Object> args){}
    private static Filter filter(HttpServletRequest q) {
        StringBuilder where=new StringBuilder(" WHERE 1=1");List<Object> values=new ArrayList<>();
        String house=q.getParameter("greenhouseId");
        if(house!=null && !house.isBlank()){where.append(" AND greenhouse_id=?");values.add(positive(house));}
        for(String[] field:new String[][]{{"metric","metric_type"},{"source","source"},{"state","status"}}) {
            String value=q.getParameter(field[0]);if(value==null || value.isBlank())continue;
            Set<String> allowed=switch(field[0]) {
                case "metric" -> Set.of("air_temperature","air_humidity","soil_temperature","soil_moisture","illuminance");
                case "source" -> Set.of("real","simulated","manual");
                default -> Set.of("pending","processing","closed");
            };
            if(!allowed.contains(value))throw new IllegalArgumentException("筛选参数无效");
            where.append(" AND ").append(field[1]).append("=?");values.add(value);
        }
        try {
            String start=q.getParameter("start"),end=q.getParameter("end");
            LocalDate first=start==null || start.isBlank()?null:LocalDate.parse(start);
            LocalDate last=end==null || end.isBlank()?null:LocalDate.parse(end);
            if(first!=null && last!=null && first.isAfter(last))throw new IllegalArgumentException("起始日期不能晚于结束日期");
            if(first!=null){where.append(" AND first_triggered_at>=?");values.add(first.atStartOfDay());}
            if(last!=null){where.append(" AND first_triggered_at<?");values.add(last.plusDays(1).atStartOfDay());}
        }catch(java.time.DateTimeException e){throw new IllegalArgumentException("日期格式应为yyyy-MM-dd");}
        return new Filter(where.toString(),values);
    }
    private static int integer(HttpServletRequest q,String name,int fallback) {
        String s=q.getParameter(name);if(s==null)return fallback;
        try{return Integer.parseInt(s);}catch(NumberFormatException e){throw new IllegalArgumentException("分页参数无效");}
    }
    private static long positive(String value) {
        try{long id=Long.parseLong(value);if(id>0)return id;}catch(NumberFormatException ignored){}
        throw new IllegalArgumentException("记录编号无效");
    }
    private static Object jsonValue(Object value) {
        if(value==null)return JSONObject.NULL;
        if(value instanceof Timestamp || value instanceof BigDecimal)return value.toString();
        if(value instanceof Map<?,?> map) {
            JSONObject result=new JSONObject();
            for(var entry:map.entrySet()) {
                String key=String.valueOf(entry.getKey());Object v=entry.getValue();
                try {result.put(key,v instanceof Number && (key.equals("id") || key.endsWith("_id") || key.endsWith("_by"))?v.toString():jsonValue(v));}
                catch(JSONException e){throw new IllegalStateException("生成响应失败");}
            }
            return result;
        }
        if(value instanceof Iterable<?> list){JSONArray array=new JSONArray();for(Object v:list)array.put(jsonValue(v));return array;}
        return value;
    }
    private static void reply(HttpServletResponse p,int status,String message,Object data)throws IOException {
        p.setStatus(status);
        try{p.getWriter().write(new JSONObject().put("result_code",status<400?0:status).put("result_msg",message).put("data",jsonValue(data)).toString());}
        catch(JSONException e){throw new IOException("生成报警响应失败",e);}
    }
}
