package greenhouse.alarm;

import device.dao.Db;
import java.sql.*;
import java.time.*;
import java.util.*;

/** JDBC helpers are scoped to the alarm module and never accept SQL from HTTP. */
final class AlarmDatabase {
    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    @FunctionalInterface public interface Connections { Connection open() throws SQLException; }
    static Connection open() throws SQLException { return new Db("greenhouse_system").getConnection(); }
    static LocalDateTime now(Clock clock) { return LocalDateTime.now(clock.withZone(ZONE)); }
    static void bind(PreparedStatement s, Object... args) throws SQLException {
        for (int i=0;i<args.length;i++) s.setObject(i+1,args[i] instanceof LocalDateTime t?Timestamp.valueOf(t):args[i]);
    }
    static int update(Connection c,String sql,Object... args) throws SQLException {
        try(var s=c.prepareStatement(sql)){bind(s,args);return s.executeUpdate();}
    }
    static List<Map<String,Object>> rows(Connection c,String sql,Object... args) throws SQLException {
        List<Map<String,Object>> rows=new ArrayList<>();
        try(var s=c.prepareStatement(sql)) {
            bind(s,args);
            try(var r=s.executeQuery()) {
                while(r.next()) {
                    Map<String,Object> row=new LinkedHashMap<>();
                    for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));
                    rows.add(row);
                }
            }
        }
        return rows;
    }
    static Map<String,Object> one(Connection c,String sql,Object... args) throws SQLException {
        var list=rows(c,sql,args);return list.isEmpty()?null:list.get(0);
    }
    static long number(Map<String,Object> row,String field) { return ((Number)row.get(field)).longValue(); }
    static String text(Map<String,Object> row,String field) { return Objects.toString(row.get(field),""); }
    static LocalDateTime time(Map<String,Object> row,String field) {
        Object value=row.get(field);return value==null?null:((Timestamp)value).toLocalDateTime();
    }
    static void lockStream(Connection c,long house,String metric) throws SQLException {
        update(c,"INSERT INTO alarm_stream(greenhouse_id,metric_type) VALUES(?,?) ON DUPLICATE KEY UPDATE greenhouse_id=VALUES(greenhouse_id)",house,metric);
        one(c,"SELECT greenhouse_id FROM alarm_stream WHERE greenhouse_id=? AND metric_type=? FOR UPDATE",house,metric);
    }
    static Map<String,Object> actor(Connection c,long actorId,boolean systemOnly) throws SQLException {
        var actor=one(c,"SELECT id,display_name,role_id FROM todo_login_user WHERE id=? AND status='active' FOR UPDATE",actorId);
        if(actor==null || (systemOnly?number(actor,"role_id")!=1:!Set.of(1L,2L).contains(number(actor,"role_id"))))
            throw new SecurityException("当前账号无权处理报警");
        return actor;
    }
    static String note(String note) {
        String value=note==null?"":note.strip();
        if(value.isEmpty() || value.length()>1000)throw new IllegalArgumentException("处理说明须为1至1000字");
        return value;
    }
    static Map<String,Object> house(Connection c,long id) throws SQLException {
        return one(c,"SELECT id,name,greenhouse_code,status,is_deleted FROM greenhouse_file WHERE id=? FOR UPDATE",id);
    }
    static boolean validHouse(Map<String,Object> house) {
        return house!=null && number(house,"is_deleted")==0 && text(house,"status").equals("enabled");
    }
    static Map<String,Object> rule(Connection c,long house,String metric) throws SQLException {
        return one(c,"SELECT id,unit,lower_limit,upper_limit FROM threshold_rule WHERE greenhouse_id=? AND metric_type=? AND active_slot=1 FOR UPDATE",house,metric);
    }
    static void action(Connection c,long event,String kind,String key,Long actor,String name,String note,LocalDateTime at) throws SQLException {
        update(c,"INSERT INTO alarm_action(event_id,action,request_key,actor_id,actor_name,note,created_at) VALUES(?,?,?,?,?,?,?)",event,kind,key,actor,name,note,at);
    }
}
