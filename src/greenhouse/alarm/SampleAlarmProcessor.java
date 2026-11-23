package greenhouse.alarm;

import java.sql.SQLException;

/**
 * Frozen server-to-server Java contract for the same deployed application.
 * No event processor or HTTP alarm endpoint is implemented by this interface.
 * The implementation must reread the committed sample by its database ID and perform
 * idempotent, ordered processing; callers must retain failed IDs for retry.
 */
@FunctionalInterface
public interface SampleAlarmProcessor {
    Receipt processCommittedSample(long sampleId) throws SQLException;

    enum Outcome {
        NORMAL, CREATED, UPDATED, RECOVERED,
        NO_EFFECTIVE_RULE, INVALID_SAMPLE, OUT_OF_ORDER, HISTORICAL_SAMPLE
    }

    /** A duplicate repeats the original committed outcome without counting the sample twice. */
    record Receipt(long sampleId, Outcome outcome, Long eventId, boolean duplicate) {
        public Receipt {
            if (sampleId <= 0 || outcome == null || eventId != null && eventId <= 0)
                throw new IllegalArgumentException("判定结果编号或状态无效");
            if ((outcome == Outcome.CREATED || outcome == Outcome.UPDATED || outcome == Outcome.RECOVERED)
                    && eventId == null)
                throw new IllegalArgumentException("事件变更结果必须关联实际报警事件编号");
        }
    }
}
