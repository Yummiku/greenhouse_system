package greenhouse.environment;

import jakarta.servlet.ServletContext;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.Connection;

/**
 * 第13周 采样 → 报警的可选桥接层（唐昕煜）。
 *
 * <p>报警模块（李慧颖，branch lihuiying/w13-alarm-contract）尚未合入本模块分支。为了避免本模块
 * 因为缺类而编译不过，也避免在报警模块未部署时把“没有判定”报告成“判定成功”，这里用反射调用
 * 双方已冻结的约定入口：
 *
 * <ul>
 *   <li>{@code greenhouse.alarm.AlarmSampleInbox#enqueue(Connection, long)}：在采样写入的同一事务里
 *       登记待处理任务；调用失败必须让整个事务回滚，不能出现“采样已保存、报警没登记”；</li>
 *   <li>{@code ServletContext} 属性 {@code sampleAlarmProcessor} 的
 *       {@code processCommittedSample(long)}：提交后同步判定，返回回执（outcome / eventId / duplicate）。</li>
 * </ul>
 *
 * <p>报警模块合入 main 后，本类可以替换为直接 import 调用，行为不变。
 */
final class AlarmBridge {

    /** 报警模块未部署：采样已入库，但没有做任何判定。 */
    static final String NOT_READY = "NOT_READY";
    /** 已登记待处理任务，但本次没有拿到判定回执（例如处理器未注册或判定抛错），由报警侧重试。 */
    static final String RETRY_SCHEDULED = "RETRY_SCHEDULED";

    private static final String INBOX = "greenhouse.alarm.AlarmSampleInbox";
    private static final String PROCESSOR_ATTRIBUTE = "sampleAlarmProcessor";

    private AlarmBridge() {
    }

    /** 报警模块是否在 classpath 上（用于回执里的 alarm_ready 与日志）。 */
    static boolean available() {
        try {
            Class.forName(INBOX, false, AlarmBridge.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 在当前数据库事务里登记报警任务。
     *
     * @return true 表示已登记；false 表示报警模块未部署（不写任务，采样照常提交）
     * @throws IllegalStateException 报警模块存在但登记失败：调用方必须回滚整个事务
     */
    static boolean enqueue(Connection c, long sampleId) {
        Class<?> inbox;
        try {
            inbox = Class.forName(INBOX, true, AlarmBridge.class.getClassLoader());
        } catch (Throwable ignored) {
            return false;
        }
        Method method;
        try {
            method = inbox.getMethod("enqueue", Connection.class, long.class);
        } catch (NoSuchMethodException e) {
            return false;
        }
        if (!Modifier.isStatic(method.getModifiers())) {
            return false;
        }
        try {
            method.invoke(null, c, Long.valueOf(sampleId));
            return true;
        } catch (Throwable t) {
            Throwable cause = t.getCause() == null ? t : t.getCause();
            throw new IllegalStateException("报警任务登记失败（sample_id=" + sampleId + "）: "
                    + cause.getMessage(), cause);
        }
    }

    /**
     * 采样事务提交后同步判定。返回值写入逐条回执的 alarm_status。
     *
     * <p>采样已经提交，这里无论成败都不能回滚采样；失败时返回 {@link #RETRY_SCHEDULED}，
     * 由报警侧按 1/2/4/8/16 秒重试，不重复上传样本。
     */
    static String process(ServletContext context, long sampleId) {
        if (context == null) return NOT_READY;
        Object processor = context.getAttribute(PROCESSOR_ATTRIBUTE);
        if (processor == null) return RETRY_SCHEDULED;
        try {
            Method method = processor.getClass().getMethod("processCommittedSample", long.class);
            Object receipt = method.invoke(processor, Long.valueOf(sampleId));
            return outcome(receipt);
        } catch (Throwable t) {
            if (context != null) {
                Throwable cause = t.getCause() == null ? t : t.getCause();
                context.log("environment-ingest alarm process failed sample=" + sampleId
                        + " : " + cause.getMessage(), cause);
            }
            return RETRY_SCHEDULED;
        }
    }

    /** 回执对象按约定含 outcome()；没有该方法时退化为 toString，便于排查。 */
    private static String outcome(Object receipt) {
        if (receipt == null) return RETRY_SCHEDULED;
        String text = readMethod(receipt, "outcome");
        if (text == null) text = readMethod(receipt, "getOutcome");
        if (text == null) text = String.valueOf(receipt);
        return text;
    }

    private static String readMethod(Object receipt, String name) {
        try {
            Method method = receipt.getClass().getMethod(name);
            Object value = method.invoke(receipt);
            return value == null ? null : String.valueOf(value);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
