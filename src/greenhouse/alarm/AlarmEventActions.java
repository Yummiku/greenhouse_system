package greenhouse.alarm;

import java.sql.*;
import java.time.*;
import java.util.*;
import static greenhouse.alarm.AlarmDatabase.*;

/** Actor identity/role is reread from the database; no client-supplied role or display name. */
public final class AlarmEventActions {
    private final Connections connections;
    private final Clock clock;
    public AlarmEventActions(){this(AlarmDatabase::open,Clock.system(ZONE));}
    AlarmEventActions(Connections connections,Clock clock){this.connections=connections;this.clock=clock;}

    private Map<String,Object> lock(Connection c,long id) throws SQLException {
        var hint=one(c,"SELECT greenhouse_id,metric_type FROM alarm_event WHERE id=?",id);
        if(hint==null)throw new IllegalArgumentException("报警事件不存在");
        lockStream(c,number(hint,"greenhouse_id"),text(hint,"metric_type"));
        return hint;
    }
    public void confirm(long eventId,long actorId) throws SQLException {
        try(var c=connections.open()) {
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);c.setAutoCommit(false);
            try {
                lock(c,eventId);var actor=actor(c,actorId,false);
                var event=one(c,"SELECT * FROM alarm_event WHERE id=? FOR UPDATE",eventId);
                if(event.get("confirmed_at")!=null){c.commit();return;}
                if(!text(event,"status").equals("pending"))throw new IllegalStateException("事件状态已变化，请刷新核对");
                update(c,"UPDATE alarm_event SET status='processing',confirmed_by=?,confirmed_name=?,confirmed_at=?,updated_at=? WHERE id=?",
                        actorId,text(actor,"display_name"),now(clock),now(clock),eventId);
                action(c,eventId,"confirm","confirm",actorId,text(actor,"display_name"),"确认报警，开始处理",now(clock));
                c.commit();
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }
    }
    public void recordHandling(long eventId,long actorId,String requestKey,String note) throws SQLException {
        String explanation=note(note);
        if(requestKey==null || !requestKey.matches("[A-Za-z0-9_.:-]{8,128}"))throw new IllegalArgumentException("请提供有效的处理请求标识");
        try(var c=connections.open()) {
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);c.setAutoCommit(false);
            try {
                lock(c,eventId);var actor=actor(c,actorId,false);
                var event=one(c,"SELECT * FROM alarm_event WHERE id=? FOR UPDATE",eventId);
                var existing=one(c,"SELECT action,actor_id,note FROM alarm_action WHERE event_id=? AND request_key=?",eventId,requestKey);
                if(existing!=null) {
                    if(!text(existing,"action").equals("handling") || number(existing,"actor_id")!=actorId || !text(existing,"note").equals(explanation))
                        throw new IllegalStateException("同一处理请求标识的内容不一致，原记录未修改");
                    c.commit();return;
                }
                if(!text(event,"status").equals("processing"))throw new IllegalStateException("请先确认报警；已关闭事件不能追加处理");
                action(c,eventId,"handling",requestKey,actorId,text(actor,"display_name"),explanation,now(clock));
                update(c,"UPDATE alarm_event SET updated_at=? WHERE id=?",now(clock),eventId);
                c.commit();
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }
    }
    public void closeInvalid(long eventId,long actorId,String reason) throws SQLException {
        String explanation=note(reason);
        try(var c=connections.open()) {
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);c.setAutoCommit(false);
            try {
                var hint=lock(c,eventId);var actor=actor(c,actorId,true);
                long houseId=number(hint,"greenhouse_id");
                var house=house(c,houseId);var current=validHouse(house)?rule(c,houseId,text(hint,"metric_type")):null;
                var event=one(c,"SELECT * FROM alarm_event WHERE id=? FOR UPDATE",eventId);
                if(text(event,"status").equals("closed")) {
                    if(!text(event,"close_reason").equals("invalid"))throw new IllegalStateException("事件已经正常恢复关闭");
                    c.commit();return;
                }
                if(current!=null && text(current,"unit").equals(text(event,"unit")))throw new IllegalStateException("规则仍有效，不能用处理说明关闭仍异常事件");
                update(c,"UPDATE alarm_event SET status='closed',closed_by=?,closed_name=?,closed_at=?,close_reason='invalid',close_note=?,invalid_reason=?,updated_at=? WHERE id=?",
                        actorId,text(actor,"display_name"),now(clock),explanation,"规则或所属大棚已失效，作失效处理关闭",now(clock),eventId);
                action(c,eventId,"invalid_close","invalid_close",actorId,text(actor,"display_name"),explanation,now(clock));
                c.commit();
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }
    }
    public Map<String,Object> detail(long id) throws SQLException {
        try(var c=connections.open()) {
            var event=one(c,"""
                SELECT a.*, (g.id IS NOT NULL AND g.is_deleted=0 AND g.status='enabled' AND EXISTS(
                   SELECT 1 FROM threshold_rule r WHERE r.greenhouse_id=a.greenhouse_id AND r.metric_type=a.metric_type
                   AND r.active_slot=1 AND r.unit=a.unit)) rule_effective,
                   (e.id IS NULL OR e.is_deleted=1 OR e.value<>a.first_sample_value OR e.sample_time<>a.first_triggered_at
                    OR e.source<>a.source OR e.greenhouse_id<>a.greenhouse_id OR e.metric_type<>a.metric_type
                    OR e.unit<>a.unit OR e.device_pk<>a.first_device_pk) original_sample_changed
                FROM alarm_event a LEFT JOIN greenhouse_file g ON g.id=a.greenhouse_id
                LEFT JOIN environment_sample e ON e.id=a.first_sample_id WHERE a.id=?
                """,id);
            if(event==null)throw new IllegalArgumentException("报警事件不存在");
            event.put("actions",rows(c,"SELECT id,action,actor_id,actor_name,note,created_at FROM alarm_action WHERE event_id=? ORDER BY id",id));
            return event;
        }
    }
}
