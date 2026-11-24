package greenhouse.log.service;

import greenhouse.log.dao.LogDao;

import java.util.Map;
import java.util.UUID;

/**
 * 操作日志公共记录方法：各模块在**业务成功提交后**调用 success，
 * 被权限拒绝调用 denied，业务校验或数据库失败调用 failed。
 *
 * 使用示例（个人消息发送成功后）：
 * <pre>
 * OperationLogger.success(actor, "个人消息", "发送消息", String.valueOf(id), title, "草稿转已发送");
 * </pre>
 *
 * 约定：
 * 1. 操作人只能取会话身份，不接受客户端自报 actor_id；
 * 2. 摘要只写关键非敏感字段，密码重置只记“发生过”；
 * 3. 日志写入失败不抛给业务、不回滚已成功业务，只在服务端 stderr 留脱敏故障标记；
 * 4. 一次实际生效操作一条成功日志，业务未成功不得写 success。
 */
public final class OperationLogger {

    public static final String RESULT_SUCCESS = "success";
    public static final String RESULT_FAILED = "failed";
    public static final String RESULT_DENIED = "denied";

    private static final LogDao DAO = new LogDao();

    private OperationLogger() {
    }

    /** 业务成功。 */
    public static void success(Map<?, ?> actor, String module, String action,
                               String targetId, String targetName, String summary) {
        record(actor, module, action, targetId, targetName, RESULT_SUCCESS, "", summary);
    }

    /** 业务失败（校验不通过、数据库异常等）。 */
    public static void failed(Map<?, ?> actor, String module, String action,
                              String targetId, String reasonCode, String summary) {
        record(actor, module, action, targetId, "", RESULT_FAILED, reasonCode, summary);
    }

    /** 无权限被拒绝。 */
    public static void denied(Map<?, ?> actor, String module, String action,
                              String targetId, String summary) {
        record(actor, module, action, targetId, "", RESULT_DENIED, "no_permission", summary);
    }

    /** 通用入口：actor 为 session.loginUser 的 Map。 */
    public static void record(Map<?, ?> actor, String module, String action,
                              String targetId, String targetName,
                              String result, String reasonCode, String summary) {
        Integer operatorId = parseInt(actor == null ? null : actor.get("id"));
        Integer roleId = parseInt(actor == null ? null : actor.get("roleId"));
        String operatorName = actor == null ? "" : String.valueOf(actor.get("displayName"));
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        try {
            DAO.insertOperation(operatorId, operatorName, roleId, module, action,
                    targetId, targetName, result, reasonCode, summary, traceId);
        } catch (Throwable e) {
            System.err.println("[OP_LOG_FAILED] module=" + module + " action=" + action
                    + " target=" + targetId + " result=" + result
                    + " operator=" + (operatorId == null ? "-" : operatorId)
                    + " trace=" + traceId + " error=" + e.getClass().getSimpleName());
        }
    }

    private static Integer parseInt(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
