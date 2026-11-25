package greenhouse.alarm;

import jakarta.servlet.http.HttpServletRequest;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import static greenhouse.alarm.AlarmDatabase.*;

/** Shared PC/mobile read scope: all authenticated roles may read agricultural alarm events. */
final class AlarmQueries {
    private record Filter(String where,List<Object> values) {}
    static Object read(HttpServletRequest request,String action) throws SQLException {
        if(action.equals("detail"))return new AlarmEventActions().detail(positive(request.getParameter("id")));
        try(var c=open()) {
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setReadOnly(true);c.setAutoCommit(false);
            try {
                Object result;
                if(action.equals("options"))result=rows(c,"SELECT id,name,greenhouse_code,status,is_deleted FROM greenhouse_file ORDER BY name,id");
                else {
                    Filter filter=filter(request);String where=filter.where();Object[] args=filter.values().toArray();
                    Map<String,Object> stats=one(c,"SELECT COUNT(*) total,COALESCE(SUM(status='pending'),0) pending,COALESCE(SUM(status='processing'),0) processing,COALESCE(SUM(status='closed'),0) closed,COALESCE(SUM(close_reason='normal'),0) normal_closed,COALESCE(SUM(close_reason='invalid'),0) invalid_closed FROM alarm_event"+where,args);
                    long total=number(stats,"total");
                    stats.put("close_rate",total==0?null:BigDecimal.valueOf(number(stats,"closed")).divide(BigDecimal.valueOf(total),6,RoundingMode.HALF_UP));
                    stats.put("directions",rows(c,"SELECT direction,COUNT(*) count FROM alarm_event"+where+" GROUP BY direction ORDER BY direction",args));
                    stats.put("sources",rows(c,"SELECT source,COUNT(*) count FROM alarm_event"+where+" GROUP BY source ORDER BY source",args));
                    stats.put("source_basis","首次触发采样来源");stats.put("time_basis","首次触发采样时间 Asia/Shanghai");
                    stats.put("close_rate_basis","全部已关闭事件 / 事件总数，包含正常恢复和失效关闭；另列正常恢复数");
                    stats.put("read_at",LocalDateTime.now(ZONE).withNano(0).toString());
                    if(action.equals("stats"))result=stats;
                    else {
                        int size=integer(request,"size",10),page=integer(request,"page",1);
                        if(!Set.of(10,20,50).contains(size)||page<1||page>1000000)throw new IllegalArgumentException("分页参数无效");
                        page=(int)Math.min(page,Math.max(1,(total+size-1)/size));
                        List<Object> values=new ArrayList<>(filter.values());
                        String sql="SELECT a.*, (g.id IS NOT NULL AND g.is_deleted=0 AND g.status='enabled' AND EXISTS(SELECT 1 FROM threshold_rule r WHERE r.greenhouse_id=a.greenhouse_id AND r.metric_type=a.metric_type AND r.active_slot=1 AND r.unit=a.unit)) rule_effective FROM alarm_event a LEFT JOIN greenhouse_file g ON g.id=a.greenhouse_id";
                        // Filters refer to event columns only; qualify to avoid the joined greenhouse status.
                        String qualified=where.replace("greenhouse_id","a.greenhouse_id").replace("metric_type","a.metric_type").replace("source","a.source").replace("status","a.status").replace("first_triggered_at","a.first_triggered_at");
                        sql+=qualified+" ORDER BY a.first_triggered_at DESC,a.id DESC";
                        if(!action.equals("report")){sql+=" LIMIT ? OFFSET ?";values.add(size);values.add((page-1)*size);}
                        Map<String,Object> data=new LinkedHashMap<>();data.put("total",total);data.put("items",rows(c,sql,values.toArray()));data.put("stats",stats);data.put("page",page);data.put("size",size);result=data;
                    }
                }
                c.commit();return result;
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }
    }
    private static Filter filter(HttpServletRequest q) {
        StringBuilder where=new StringBuilder(" WHERE 1=1");List<Object> values=new ArrayList<>();
        String house=q.getParameter("greenhouseId");if(house!=null&&!house.isBlank()){where.append(" AND greenhouse_id=?");values.add(positive(house));}
        for(String[] field:new String[][]{{"metric","metric_type"},{"source","source"},{"state","status"}}) {
            String value=q.getParameter(field[0]);if(value==null||value.isBlank())continue;
            Set<String> allowed=switch(field[0]){case "metric"->Set.of("air_temperature","air_humidity","soil_temperature","soil_moisture","illuminance");case "source"->Set.of("real","simulated","manual");default->Set.of("pending","processing","closed");};
            if(!allowed.contains(value))throw new IllegalArgumentException("筛选参数无效");where.append(" AND ").append(field[1]).append("=?");values.add(value);
        }
        try {
            String a=q.getParameter("start"),b=q.getParameter("end");LocalDate start=a==null||a.isBlank()?null:LocalDate.parse(a),end=b==null||b.isBlank()?null:LocalDate.parse(b);
            if(start!=null&&end!=null&&start.isAfter(end))throw new IllegalArgumentException("起始日期不能晚于结束日期");
            if(start!=null){where.append(" AND first_triggered_at>=?");values.add(start.atStartOfDay());}
            if(end!=null){where.append(" AND first_triggered_at<?");values.add(end.plusDays(1).atStartOfDay());}
        }catch(DateTimeException e){throw new IllegalArgumentException("日期格式应为yyyy-MM-dd");}
        return new Filter(where.toString(),values);
    }
    static long positive(String value){try{long id=Long.parseLong(value);if(id>0)return id;}catch(NumberFormatException ignored){}throw new IllegalArgumentException("记录编号无效");}
    private static int integer(HttpServletRequest q,String field,int fallback){try{return q.getParameter(field)==null?fallback:Integer.parseInt(q.getParameter(field));}catch(NumberFormatException e){throw new IllegalArgumentException("分页参数无效");}}
}
