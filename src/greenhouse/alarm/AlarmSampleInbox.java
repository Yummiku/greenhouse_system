package greenhouse.alarm;

import java.sql.*;
import java.time.*;
import static greenhouse.alarm.AlarmDatabase.*;

/** Producer calls enqueue on its own sampling transaction, then commits before dispatch. */
public final class AlarmSampleInbox {
    private AlarmSampleInbox() {}
    public static void enqueue(Connection samplingTransaction,long sampleId) throws SQLException {
        if(samplingTransaction.getAutoCommit())throw new IllegalArgumentException("采样和报警待处理任务必须在同一事务保存");
        if(sampleId<=0)throw new IllegalArgumentException("请提供真实入库采样编号");
        // Values come exclusively from the sampling row visible in this transaction.
        update(samplingTransaction,"""
            INSERT INTO alarm_sample_task(sample_id,greenhouse_id,metric_type,sample_time,
                next_retry_at,created_at,updated_at)
            SELECT id,greenhouse_id,metric_type,sample_time,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)
            FROM environment_sample WHERE id=?
            ON DUPLICATE KEY UPDATE sample_id=VALUES(sample_id)
            """,sampleId);
        if(one(samplingTransaction,"SELECT sample_id FROM alarm_sample_task WHERE sample_id=?",sampleId)==null)
            throw new IllegalArgumentException("采样不存在或尚未入库");
    }
    public static void retryFailed(long sampleId,long actorId) throws SQLException {
        retryFailed(AlarmDatabase::open,Clock.system(ZONE),sampleId,actorId);
    }
    static void retryFailed(Connections connections,Clock clock,long sampleId,long actorId) throws SQLException {
        try(var c=connections.open()) {
            c.setAutoCommit(false);
            try {
                actor(c,actorId,false);
                var task=one(c,"SELECT state FROM alarm_sample_task WHERE sample_id=? FOR UPDATE",sampleId);
                if(task==null)throw new IllegalArgumentException("待处理采样不存在");
                if(!text(task,"state").equals("failed"))throw new IllegalArgumentException("仅失败任务可人工重试，已成功任务不会重算");
                update(c,"UPDATE alarm_sample_task SET state='pending',failures=0,next_retry_at=?,last_error_code=NULL,last_manual_retry_by=?,last_manual_retry_at=?,manual_retries=manual_retries+1,updated_at=? WHERE sample_id=?",
                        now(clock),actorId,now(clock),now(clock),sampleId);
                c.commit();
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }
    }
}
