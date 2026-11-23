import greenhouse.alarm.CommittedSample;
import greenhouse.alarm.SampleAlarmProcessor;
import greenhouse.threshold.ThresholdBounds;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Run with Java17; validates the contract only, not the pending event engine. */
public final class AlarmProtocolCheck {
    private static int count;
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        count++; System.out.println("PASS " + label);
    }
    private static void rejects(Runnable operation, String label) {
        boolean rejected = false;
        try { operation.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, label);
    }
    private static CommittedSample sample(long id, String metric, BigDecimal value, String unit) {
        return new CommittedSample(id, "protocol-sample-0001", 1, 1, metric, value, unit,
                LocalDateTime.of(2026, 11, 24, 10, 0), CommittedSample.Source.SIMULATED, false);
    }
    public static void main(String[] args) {
        check(sample(1,"air_temperature",BigDecimal.ZERO,"℃").value().signum()==0,"zero sample retained");
        for (String[] metric : new String[][]{{"air_temperature","℃"},{"air_humidity","%"},
                {"soil_temperature","℃"},{"soil_moisture","%"},{"illuminance","lx"}})
            check(sample(1,metric[0],BigDecimal.ONE,metric[1]).metricType().equals(metric[0]),"metric " + metric[0]);
        rejects(()->sample(0,"air_temperature",BigDecimal.ONE,"℃"),"unsaved ID rejected");
        rejects(()->sample(1,"control",BigDecimal.ONE,"-"),"control is not a sample metric");
        rejects(()->sample(1,"air_temperature",BigDecimal.ONE,"%"),"wrong unit rejected");
        rejects(()->sample(1,"air_temperature",null,"℃"),"missing sample value rejected");
        for (String source : new String[]{"real","simulated","manual"})
            check(CommittedSample.Source.fromCode(source).code().equals(source),"source " + source);
        rejects(()->CommittedSample.Source.fromCode("hardware"),"hardware alias rejected");
        var bounds=ThresholdBounds.parse("0","35");
        check(bounds.classify(new BigDecimal("-1")).equals("低于下限"),"below lower");
        check(bounds.classify(BigDecimal.ZERO).equals("正常"),"equal lower normal");
        check(bounds.classify(new BigDecimal("35")).equals("正常"),"equal upper normal");
        check(bounds.classify(new BigDecimal("36")).equals("高于上限"),"above upper");
        check(ThresholdBounds.parse("","35").classify(new BigDecimal("-1")).equals("正常"),"unset lower not zero");
        check(new SampleAlarmProcessor.Receipt(1,SampleAlarmProcessor.Outcome.CREATED,8L,true).duplicate(),"duplicate receipt explicit");
        rejects(()->new SampleAlarmProcessor.Receipt(1,SampleAlarmProcessor.Outcome.CREATED,null,false),"created needs actual event ID");
        System.out.println("Contract checks passed: " + count);
    }
}
