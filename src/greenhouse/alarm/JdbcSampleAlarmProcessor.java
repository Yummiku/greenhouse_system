package greenhouse.alarm;

import greenhouse.threshold.ThresholdBounds;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import static greenhouse.alarm.AlarmDatabase.*;

/** Transactional implementation of the frozen sample-ID callback. No client values are trusted. */
public final class JdbcSampleAlarmProcessor implements SampleAlarmProcessor {
    private final Connections connections;
    private final Clock clock;
    public JdbcSampleAlarmProcessor() { this(AlarmDatabase::open,Clock.system(ZONE)); }
    JdbcSampleAlarmProcessor(Connections connections,Clock clock) { this.connections=connections;this.clock=clock; }

    /** Run after 21 and 33. First activation time is durable, not reset by restart. */
    public void initialize() throws SQLException {
        try(var c=connections.open()) {
            one(c,"SELECT id FROM environment_sample LIMIT 1");
            one(c,"SELECT id FROM threshold_rule LIMIT 1");
            one(c,"SELECT sample_id FROM alarm_sample_task LIMIT 1");
            one(c,"SELECT id FROM alarm_action LIMIT 1");
            one(c,"SELECT id FROM alarm_event LIMIT 1");
            one(c,"SELECT greenhouse_id FROM alarm_stream LIMIT 1");
            update(c,"INSERT INTO alarm_runtime(id,accept_from) VALUES(1,?) ON DUPLICATE KEY UPDATE id=VALUES(id)",now(clock).withNano(0));
        }
    }

    private Map<String,Object> ensureTask(long id) throws SQLException {
        if(id<=0)throw new IllegalArgumentException("请提供真实入库采样编号");
        try(var c=connections.open()) {
            var existing=one(c,"SELECT * FROM alarm_sample_task WHERE sample_id=?",id);
            if(existing!=null)return existing;
            // Compatibility safety net for a committed sample. Producers still MUST enqueue atomically.
            c.setAutoCommit(false);
            try { AlarmSampleInbox.enqueue(c,id);c.commit(); }
            catch(SQLException|RuntimeException e){c.rollback();throw e;}
            return one(c,"SELECT * FROM alarm_sample_task WHERE sample_id=?",id);
        }
    }

    @Override public Receipt processCommittedSample(long sampleId) throws SQLException {
        var hint=ensureTask(sampleId);
        if(text(hint,"state").equals("done"))return receipt(hint,true);
        long house=number(hint,"greenhouse_id");String metric=text(hint,"metric_type");
        try(var c=connections.open()) {
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            c.setAutoCommit(false);
            try {
                lockStream(c,house,metric);
                var task=one(c,"SELECT * FROM alarm_sample_task WHERE sample_id=? FOR UPDATE",sampleId);
                if(text(task,"state").equals("done")){c.commit();return receipt(task,true);}
                if(text(task,"state").equals("failed"))throw new IllegalStateException("任务已达到自动重试次数，请管理员核对后重试");
                if(number(task,"failures")>0 && time(task,"next_retry_at").isAfter(now(clock)))throw new RetryNotDue();
                var stream=one(c,"SELECT * FROM alarm_stream WHERE greenhouse_id=? AND metric_type=?",house,metric);
                var runtime=one(c,"SELECT accept_from FROM alarm_runtime WHERE id=1");
                if(runtime==null)throw new SQLException("报警处理器尚未初始化","45000");
                var sampleRow=one(c,"SELECT * FROM environment_sample WHERE id=? FOR UPDATE",sampleId);
                CommittedSample sample;
                try {
                    if(sampleRow==null)throw new IllegalArgumentException("采样不存在");
                    sample=new CommittedSample(sampleId,text(sampleRow,"sample_key"),number(sampleRow,"greenhouse_id"),
                            Math.toIntExact(number(sampleRow,"device_pk")),text(sampleRow,"metric_type"),
                            (BigDecimal)sampleRow.get("value"),text(sampleRow,"unit"),time(sampleRow,"sample_time"),
                            CommittedSample.Source.fromCode(text(sampleRow,"source")),number(sampleRow,"is_deleted")!=0);
                    if(sample.deleted() || sample.greenhouseId()!=house || !sample.metricType().equals(metric))
                        throw new IllegalArgumentException("采样已删除或原关联被改动");
                    var device=one(c,"SELECT id,device_id,device_type FROM device_file WHERE id=?",sample.devicePk());
                    if(device==null || !text(device,"device_type").equals("sensor"))throw new IllegalArgumentException("不是有效采样设备");
                }catch(IllegalArgumentException|ArithmeticException e) {
                    var result=complete(c,sampleId,Outcome.INVALID_SAMPLE,null);c.commit();return result;
                }
                if(sample.sampleTime().isBefore(time(runtime,"accept_from"))) {
                    var result=complete(c,sampleId,Outcome.HISTORICAL_SAMPLE,null);c.commit();return result;
                }
                LocalDateTime watermark=time(stream,"watermark_time");
                if(watermark!=null && (sample.sampleTime().isBefore(watermark) || sample.sampleTime().equals(watermark)
                        && sampleId<=number(stream,"watermark_sample_id"))) {
                    var result=complete(c,sampleId,Outcome.OUT_OF_ORDER,null);c.commit();return result;
                }
                // A newer callback cannot overtake an already committed pending/failed older sample.
                if(one(c,"""
                    SELECT sample_id FROM alarm_sample_task WHERE greenhouse_id=? AND metric_type=?
                    AND state IN ('pending','failed') AND (sample_time<? OR (sample_time=? AND sample_id<?))
                    ORDER BY sample_time,sample_id LIMIT 1
                    """,house,metric,sample.sampleTime(),sample.sampleTime(),sampleId)!=null)
                    throw new Deferred();
                var greenhouse=house(c,house);
                var effective=validHouse(greenhouse)?rule(c,house,metric):null;
                var active=one(c,"SELECT * FROM alarm_event WHERE greenhouse_id=? AND metric_type=? AND active_slot=1 FOR UPDATE",house,metric);
                Long event=active==null?null:number(active,"id");
                Outcome outcome;
                if(effective==null) {
                    if(event!=null)update(c,"UPDATE alarm_event SET invalid_reason=?,updated_at=? WHERE id=?",
                            validHouse(greenhouse)?"当前无有效启用规则，事件未作正常恢复":"所属大棚已停用、删除或不存在",now(clock),event);
                    outcome=Outcome.NO_EFFECTIVE_RULE;
                }else {
                    if(!text(effective,"unit").equals(sample.unit()))throw new SQLException("规则单位与采样不一致","45000");
                    var limits=new ThresholdBounds.Limits((BigDecimal)effective.get("lower_limit"),(BigDecimal)effective.get("upper_limit"));
                    String classification=limits.classify(sample.value());
                    if(classification.equals("正常")) {
                        if(event==null)outcome=Outcome.NORMAL;
                        else {
                            update(c,"""
                                UPDATE alarm_event SET status='closed',invalid_reason=NULL,closed_name='系统',closed_at=?,
                                close_reason='normal',close_note='后续有效采样恢复正常',recovery_sample_id=?,recovery_value=?,recovery_time=?,
                                recovery_rule_id=?,recovery_lower=?,recovery_upper=?,updated_at=? WHERE id=?
                                """,now(clock),sampleId,sample.value(),sample.sampleTime(),number(effective,"id"),
                                    effective.get("lower_limit"),effective.get("upper_limit"),now(clock),event);
                            action(c,event,"normal_close","recovery:"+sampleId,null,"系统","后续有效采样恢复正常",now(clock));
                            outcome=Outcome.RECOVERED;
                        }
                    }else {
                        String direction=classification.equals("低于下限")?"low":"high";
                        if(event==null) {
                            String sql="""
                                INSERT INTO alarm_event(greenhouse_id,greenhouse_name,greenhouse_code,metric_type,unit,source,direction,
                                first_sample_id,first_device_pk,first_device_code,first_sample_value,first_triggered_at,first_rule_id,first_lower,first_upper,
                                last_sample_id,last_device_pk,last_source,last_direction,last_sample_value,last_triggered_at,last_rule_id,last_lower,last_upper,
                                created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                                """;
                            var device=one(c,"SELECT device_id FROM device_file WHERE id=?",sample.devicePk());
                            try(var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)) {
                                bind(s,house,text(greenhouse,"name"),text(greenhouse,"greenhouse_code"),metric,sample.unit(),sample.source().code(),direction,
                                        sampleId,sample.devicePk(),text(device,"device_id"),sample.value(),sample.sampleTime(),number(effective,"id"),effective.get("lower_limit"),effective.get("upper_limit"),
                                        sampleId,sample.devicePk(),sample.source().code(),direction,sample.value(),sample.sampleTime(),number(effective,"id"),effective.get("lower_limit"),effective.get("upper_limit"),now(clock),now(clock));
                                s.executeUpdate();try(var ids=s.getGeneratedKeys()){ids.next();event=ids.getLong(1);}
                            }
                            outcome=Outcome.CREATED;
                        }else {
                            update(c,"""
                                UPDATE alarm_event SET last_sample_id=?,last_device_pk=?,last_source=?,last_direction=?,last_sample_value=?,
                                last_triggered_at=?,last_rule_id=?,last_lower=?,last_upper=?,abnormal_count=abnormal_count+1,
                                invalid_reason=NULL,updated_at=? WHERE id=?
                                """,sampleId,sample.devicePk(),sample.source().code(),direction,sample.value(),sample.sampleTime(),number(effective,"id"),
                                    effective.get("lower_limit"),effective.get("upper_limit"),now(clock),event);
                            outcome=Outcome.UPDATED;
                        }
                    }
                }
                update(c,"UPDATE alarm_stream SET watermark_time=?,watermark_sample_id=? WHERE greenhouse_id=? AND metric_type=?",sample.sampleTime(),sampleId,house,metric);
                var result=complete(c,sampleId,outcome,event);c.commit();return result;
            }catch(SQLException|RuntimeException e) {
                c.rollback();
                if(!(e instanceof RetryNotDue)) {
                    try { failure(sampleId,e instanceof Deferred); }catch(SQLException recordingFailure){e.addSuppressed(recordingFailure);}
                }
                throw e;
            }
        }
    }

    private Receipt complete(Connection c,long id,Outcome outcome,Long event) throws SQLException {
        update(c,"UPDATE alarm_sample_task SET state='done',outcome=?,event_id=?,attempts=attempts+1,last_error_code=NULL,completed_at=?,updated_at=? WHERE sample_id=?",
                outcome.name(),event,now(clock),now(clock),id);
        return new Receipt(id,outcome,event,false);
    }
    private static Receipt receipt(Map<String,Object> task,boolean duplicate) {
        return new Receipt(number(task,"sample_id"),Outcome.valueOf(text(task,"outcome")),
                task.get("event_id")==null?null:number(task,"event_id"),duplicate);
    }
    private void failure(long id,boolean deferred) throws SQLException {
        try(var c=connections.open()) {
            c.setAutoCommit(false);
            try {
                var task=one(c,"SELECT * FROM alarm_sample_task WHERE sample_id=? FOR UPDATE",id);
                if(task!=null && text(task,"state").equals("pending")) {
                    int failures=Math.toIntExact(number(task,"failures"))+(deferred?0:1);
                    int delay=deferred?1:1<<Math.min(4,Math.max(0,failures-1));
                    update(c,"UPDATE alarm_sample_task SET state=?,failures=?,attempts=attempts+?,last_error_code=?,next_retry_at=?,updated_at=? WHERE sample_id=?",
                            failures>=6?"failed":"pending",failures,deferred?0:1,deferred?"WAITING_EARLIER_SAMPLE":"ALARM_PROCESSING_FAILED",
                            now(clock).plusSeconds(delay),now(clock),id);
                }
                c.commit();
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }
    }
    /** Single scheduler tick; overlapping workers remain safe through stream/task locks. */
    public int processDueTasks(int limit) throws SQLException {
        if(limit<1 || limit>200)throw new IllegalArgumentException("重试批次须为1至200");
        List<Map<String,Object>> due;
        try(var c=connections.open()) {
            due=rows(c,"SELECT sample_id FROM alarm_sample_task WHERE state='pending' AND next_retry_at<=? ORDER BY sample_time,sample_id LIMIT ?",now(clock),limit);
        }
        int completed=0;
        for(var task:due) {
            try { processCommittedSample(number(task,"sample_id"));completed++; }
            catch(SQLException|IllegalStateException expected){ /* Task retains its failure status; do not reupload. */ }
        }
        return completed;
    }
    private static final class Deferred extends SQLException {
        Deferred(){super("请先处理同棚同指标的较早采样，当前采样保持待处理","45000");}
    }
    private static final class RetryNotDue extends SQLException {
        RetryNotDue(){super("报警任务等待既定重试时间，请勿重复上传采样","45000");}
    }
}
