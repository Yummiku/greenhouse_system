package greenhouse.alarm;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static greenhouse.alarm.AlarmDatabase.*;
import static greenhouse.alarm.SampleAlarmProcessor.Outcome.*;

/** MySQL8 integration checks. Creates a NEW isolated database, never changes the source database. */
public final class AlarmEngineIntegrationCheck {
    static final List<String> checks=new ArrayList<>();
    static MutableClock clock;
    static JdbcSampleAlarmProcessor engine;
    static AlarmEventActions actions;
    static long admin,manager,worker;
    static LocalDateTime start;
    record World(long house,int device,long rule){}
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks.add(name);System.out.println("PASS "+name);}
    interface Task{void run()throws Exception;}
    static void rejects(Class<? extends Throwable> type,Task task,String name)throws Exception {
        boolean rejected=false;try{task.run();}catch(Exception e){if(!type.isInstance(e))throw e;rejected=true;}check(rejected,name);
    }
    static class MutableClock extends Clock {
        final AtomicReference<Instant> instant;final ZoneId zone;
        MutableClock(Instant at){this(new AtomicReference<>(at),ZONE);}
        MutableClock(AtomicReference<Instant> instant,ZoneId zone){this.instant=instant;this.zone=zone;}
        public Instant instant(){return instant.get();}public ZoneId getZone(){return zone;}
        public Clock withZone(ZoneId zone){return new MutableClock(instant,zone);}
        void advance(long seconds){instant.updateAndGet(x->x.plusSeconds(seconds));}
    }
    static long insert(Connection c,String sql,Object... values)throws SQLException {
        try(var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){bind(s,values);s.executeUpdate();try(var r=s.getGeneratedKeys()){r.next();return r.getLong(1);}}
    }
    static World world()throws SQLException {
        try(var c=open()) {
            String key=UUID.randomUUID().toString();
            long house=insert(c,"INSERT INTO greenhouse_file(greenhouse_code,name,location,area,length,width,manager_id,status) VALUES(?,?,?,?,?,?,?,'enabled')","ALARM-"+key,"报警测试棚","独立测试",100,10,10,admin);
            int device=Math.toIntExact(insert(c,"INSERT INTO device_file(device_id,device_name,device_type) VALUES(?,?,'sensor')","SENSOR-"+key,"模拟温度设备"));
            update(c,"INSERT INTO greenhouse_device_binding(device_pk,greenhouse_id,metric_type,unit) VALUES(?,?,'air_temperature','℃')",device,house);
            long rule=insert(c,"INSERT INTO threshold_rule(rule_name,greenhouse_id,metric_type,unit,lower_limit,upper_limit,enabled,created_by,updated_by) VALUES(?,?,'air_temperature','℃',0,35,1,?,?)","报警测试规则",house,admin,admin);
            return new World(house,device,rule);
        }
    }
    static long sample(World w,String value,int second)throws SQLException {return sample(w,value,start.plusSeconds(second),true);}
    static long sample(World w,String value,LocalDateTime at,boolean enqueue)throws SQLException {
        try(var c=open()) {
            c.setAutoCommit(false);
            try {
                long id=insert(c,"INSERT INTO environment_sample(sample_key,greenhouse_id,device_pk,metric_type,value,unit,sample_time,source) VALUES(?,?,?,'air_temperature',?,'℃',?,'simulated')",
                        "alarm-check-"+UUID.randomUUID(),w.house,w.device,new BigDecimal(value),at);
                if(enqueue)AlarmSampleInbox.enqueue(c,id);
                c.commit();return id;
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }
    }
    static Map<String,Object> event(long id)throws SQLException{try(var c=open()){return one(c,"SELECT * FROM alarm_event WHERE id=?",id);}}
    static Map<String,Object> task(long id)throws SQLException{try(var c=open()){return one(c,"SELECT * FROM alarm_sample_task WHERE sample_id=?",id);}}
    static void sql(String sql,Object... args)throws SQLException{try(var c=open()){update(c,sql,args);}}
    static long count(String sql,Object... args)throws SQLException{try(var c=open()){return number(one(c,sql,args),"n");}}
    static SampleAlarmProcessor.Receipt process(long id)throws SQLException{return engine.processCommittedSample(id);}
    static void script(Connection c,Path file)throws Exception {
        String text=Files.readString(file,StandardCharsets.UTF_8).replace("\uFEFF","").replaceAll("(?m)^\\s*--.*$","");
        for(String part:text.split(";")) {
            String statement=part.strip();if(statement.isEmpty()||statement.toUpperCase(Locale.ROOT).startsWith("USE "))continue;
            if(statement.toUpperCase(Locale.ROOT).contains("DROP TABLE"))throw new IllegalArgumentException("Destructive migration rejected");
            try(var s=c.createStatement()){s.execute(statement);}
        }
    }
    public static void main(String[] args)throws Exception {
        if(args.length<5)throw new IllegalArgumentException("sourceReviewDb newAlarmReviewDb privateConfig environment21.sql alarm33.sql [results.txt]");
        if(!args[0].startsWith("greenhouse_threshold_review_")||!args[1].matches("greenhouse_alarm_review_[0-9_]+"))throw new IllegalArgumentException("Only isolated review database names accepted");
        System.setProperty("greenhouse.configFile",args[2]);System.setProperty("greenhouse.db.name",args[0]);
        try(var source=open()) {
            try(var s=source.createStatement()){s.executeUpdate("CREATE DATABASE `"+args[1]+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");}
            for(String table:List.of("todo_login_user","greenhouse_file","device_file","greenhouse_device_binding","threshold_rule","menu_tree")) {
                try(var s=source.createStatement()) {
                    s.executeUpdate("CREATE TABLE `"+args[1]+"`.`"+table+"` LIKE `"+args[0]+"`.`"+table+"`");
                    String columns;
                    try(var metadata=source.prepareStatement("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME=? AND EXTRA NOT LIKE '%GENERATED%' ORDER BY ORDINAL_POSITION")) {
                        metadata.setString(1,args[0]);metadata.setString(2,table);
                        List<String> names=new ArrayList<>();
                        try(var rows=metadata.executeQuery()){while(rows.next())names.add("`"+rows.getString(1)+"`");}
                        columns=String.join(",",names);
                    }
                    s.executeUpdate("INSERT INTO `"+args[1]+"`.`"+table+"` ("+columns+") SELECT "+columns+" FROM `"+args[0]+"`.`"+table+"`");
                }
            }
        }
        System.setProperty("greenhouse.db.name",args[1]);
        try(var c=open()) {
            script(c,Path.of(args[3]));script(c,Path.of(args[4]));script(c,Path.of(args[4]));
            admin=number(one(c,"SELECT id FROM todo_login_user WHERE username='admin'"),"id");
            manager=number(one(c,"SELECT id FROM todo_login_user WHERE username='manager'"),"id");
            worker=number(one(c,"SELECT id FROM todo_login_user WHERE username='worker'"),"id");
        }
        check(true,"repeatable additive alarm migration");
        clock=new MutableClock(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));start=now(clock);
        engine=new JdbcSampleAlarmProcessor(AlarmDatabase::open,clock);actions=new AlarmEventActions(AlarmDatabase::open,clock);
        engine.initialize();clock.advance(3600);
        engine.initialize();check(count("SELECT COUNT(*) n FROM alarm_runtime WHERE accept_from=?",start)==1,"restart retains initial acceptance time");

        World w=world();long normal=sample(w,"25",1);check(process(normal).outcome()==NORMAL,"25 produces no alarm");
        long high=sample(w,"36",2);var created=process(high);long eid=created.eventId();check(created.outcome()==CREATED,"36 creates pending event");
        check(text(event(eid),"status").equals("pending")&&number(event(eid),"abnormal_count")==1,"created event is pending with one sample");
        long higher=sample(w,"37",3);check(process(higher).outcome()==UPDATED,"37 updates the same event");
        check(number(event(eid),"abnormal_count")==2&&number(event(eid),"first_sample_id")==high,"continuous abnormal count keeps immutable first sample");
        check(process(higher).duplicate()&&number(event(eid),"abnormal_count")==2,"same sample retry does not increment");
        rejects(SecurityException.class,()->actions.confirm(eid,worker),"worker cannot confirm");
        rejects(IllegalStateException.class,()->actions.recordHandling(eid,manager,"request-0001","检查风机"),"must confirm before recording handling");
        actions.confirm(eid,manager);actions.confirm(eid,admin);
        check(text(event(eid),"status").equals("processing")&&count("SELECT COUNT(*) n FROM alarm_action WHERE event_id=? AND action='confirm'",eid)==1,"confirmation idempotent with real actor");
        actions.recordHandling(eid,manager,"request-0001","检查风机");actions.recordHandling(eid,manager,"request-0001","检查风机");
        actions.recordHandling(eid,manager,"request-0002","已打开通风口");
        check(count("SELECT COUNT(*) n FROM alarm_action WHERE event_id=? AND action='handling'",eid)==2,"handling appends and retries deduplicate");
        rejects(IllegalStateException.class,()->actions.recordHandling(eid,manager,"request-0001","不同内容"),"same handling request cannot change content");
        rejects(IllegalStateException.class,()->actions.closeInvalid(eid,admin,"人为填写已恢复"),"cannot close still-effective abnormal event with a note");
        long recovery=sample(w,"25",4);check(process(recovery).outcome()==RECOVERED,"25 recovers event");
        var closed=event(eid);
        check(text(closed,"status").equals("closed")&&text(closed,"close_reason").equals("normal")&&text(closed,"closed_name").equals("系统")&&number(closed,"recovery_sample_id")==recovery,"system recovery evidence retained");
        check(number(closed,"last_sample_id")==higher&&((BigDecimal)closed.get("last_sample_value")).compareTo(new BigDecimal("37"))==0,"recovery does not overwrite last abnormal evidence");
        rejects(IllegalStateException.class,()->actions.recordHandling(eid,admin,"request-0003","不能追加"),"closed event cannot append handling");
        var again=process(sample(w,"36",5));check(again.outcome()==CREATED&&again.eventId()!=eid,"abnormal after recovery creates new event");
        check(process(sample(w,"35",6)).outcome()==RECOVERED,"equal upper boundary is normal");
        check(process(sample(w,"0",7)).outcome()==NORMAL,"zero equal lower is normal");
        World oneSided=world();sql("UPDATE threshold_rule SET lower_limit=NULL WHERE id=?",oneSided.rule);
        check(process(sample(oneSided,"-1",1)).outcome()==NORMAL,"empty lower boundary is not zero");
        var low=process(sample(w,"-1",8));check(text(event(low.eventId()),"direction").equals("low"),"low direction captured");
        sql("UPDATE threshold_rule SET upper_limit=40 WHERE id=?",w.rule);
        check(process(sample(w,"39",9)).outcome()==RECOVERED,"new sample uses changed effective rule");
        var changed=event(low.eventId());check(((BigDecimal)changed.get("first_upper")).intValue()==35&&((BigDecimal)changed.get("recovery_upper")).intValue()==40,"first and recovery threshold snapshots remain separate");
        check(process(high).outcome()==CREATED&&process(high).duplicate(),"old success retry retains original outcome after rule changes");

        World invalid=world();long invEvent=process(sample(invalid,"36",1)).eventId();sql("UPDATE threshold_rule SET enabled=0 WHERE id=?",invalid.rule);
        check(process(sample(invalid,"25",2)).outcome()==NO_EFFECTIVE_RULE&&text(event(invEvent),"status").equals("pending"),"no rule is not normal recovery");
        rejects(SecurityException.class,()->actions.closeInvalid(invEvent,manager,"旧规则停用"),"manager cannot close invalid event");
        actions.closeInvalid(invEvent,admin,"旧规则停用，按失效处理");
        check(text(event(invEvent),"close_reason").equals("invalid")&&event(invEvent).get("recovery_sample_id")==null,"invalid close separate from normal recovery");
        World inactive=world();sql("UPDATE greenhouse_file SET status='disabled' WHERE id=?",inactive.house);
        check(process(sample(inactive,"99",1)).outcome()==NO_EFFECTIVE_RULE,"disabled greenhouse does not create alarm");
        sql("UPDATE greenhouse_file SET status='enabled',is_deleted=1 WHERE id=?",inactive.house);
        check(process(sample(inactive,"99",2)).outcome()==NO_EFFECTIVE_RULE,"deleted greenhouse does not create alarm");

        World history=world();check(process(sample(history,"99",start.minusDays(1),true)).outcome()==HISTORICAL_SAMPLE,"pre-activation history cannot create current alarm");
        long recent=sample(history,"25",10);process(recent);
        check(process(sample(history,"99",5)).outcome()==OUT_OF_ORDER,"late older sample cannot reverse state");
        check(process(sample(history,"99",10)).outcome()==CREATED,"same time higher stable sample ID processed");
        World ordered=world();long earlier=sample(ordered,"36",1),later=sample(ordered,"37",2);
        rejects(SQLException.class,()->process(later),"newer callback waits for queued earlier sample");
        check(number(task(later),"failures")==0,"ordering wait is not a failed processing attempt");
        process(earlier);clock.advance(2);engine.processDueTasks(200);
        check(text(task(later),"outcome").equals("UPDATED")&&count("SELECT COUNT(*) n FROM alarm_event WHERE greenhouse_id=?",ordered.house)==1,"ordered retry creates one continuous event");

        World duplicate=world();long same=sample(duplicate,"36",1);
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch go=new CountDownLatch(1);
        List<Future<SampleAlarmProcessor.Receipt>> f=new ArrayList<>();
        for(int i=0;i<2;i++)f.add(pool.submit(()->{go.await();return process(same);}));go.countDown();
        int duplicated=0;for(var future:f)if(future.get().duplicate())duplicated++;pool.shutdown();
        check(duplicated==1&&count("SELECT COUNT(*) n FROM alarm_event WHERE greenhouse_id=? AND abnormal_count=1",duplicate.house)==1,"concurrent duplicate sample creates exactly one event and count");
        rejects(SQLException.class,()->{
            try(var c=open()) {
                var columns=rows(c,"SELECT COLUMN_NAME column_name FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='alarm_event' AND COLUMN_NAME<>'id' AND EXTRA NOT LIKE '%GENERATED%' ORDER BY ORDINAL_POSITION");
                String names=String.join(",",columns.stream().map(row->"`"+text(row,"column_name")+"`").toList());
                update(c,"INSERT INTO alarm_event("+names+") SELECT "+names+" FROM alarm_event WHERE greenhouse_id=? AND active_slot=1",duplicate.house);
            }
        },"database unique active slot rejects second live event with a new primary key");

        World malformed=world();long deleted=sample(malformed,"36",1);sql("UPDATE environment_sample SET is_deleted=1 WHERE id=?",deleted);
        check(process(deleted).outcome()==INVALID_SAMPLE,"deleted sample rejected");
        long badUnit=sample(malformed,"36",2);sql("UPDATE environment_sample SET unit='%' WHERE id=?",badUnit);
        check(process(badUnit).outcome()==INVALID_SAMPLE,"wrong sample unit rejected");
        long control=sample(malformed,"36",3);sql("UPDATE device_file SET device_type='control' WHERE id=?",malformed.device);
        check(process(control).outcome()==INVALID_SAMPLE,"control device cannot be sampling source");
        World rebind=world();long oldBinding=sample(rebind,"36",1);World newHouse=world();
        sql("UPDATE greenhouse_device_binding SET greenhouse_id=? WHERE device_pk=?",newHouse.house,rebind.device);
        long oldEvent=process(oldBinding).eventId();check(number(event(oldEvent),"greenhouse_id")==rebind.house,"rebind does not rewrite saved sample greenhouse");
        sql("UPDATE environment_sample SET value=20 WHERE id=?",oldBinding);
        check(number(actions.detail(oldEvent),"original_sample_changed")==1&&((BigDecimal)event(oldEvent).get("first_sample_value")).intValue()==36,"manual correction leaves original event snapshot and visible change flag");

        World rollback=world();long before=count("SELECT COUNT(*) n FROM environment_sample");
        try(var c=open()) {
            c.setAutoCommit(false);long id=insert(c,"INSERT INTO environment_sample(sample_key,greenhouse_id,device_pk,metric_type,value,unit,sample_time,source) VALUES(?,?,?,'air_temperature',36,'℃',?,'simulated')","rollback-"+UUID.randomUUID(),rollback.house,rollback.device,start.plusSeconds(1));
            AlarmSampleInbox.enqueue(c,id);c.rollback();check(count("SELECT COUNT(*) n FROM alarm_sample_task WHERE sample_id=?",id)==0,"sampling rollback also removes pending task");
        }
        check(count("SELECT COUNT(*) n FROM environment_sample")==before,"sampling rollback preserves sample count");
        try(var c=open()){rejects(IllegalArgumentException.class,()->AlarmSampleInbox.enqueue(c,normal),"enqueue refuses autocommit producer");}

        World failure=world();long failedSample=sample(failure,"36",1);
        sql("CREATE TRIGGER alarm_test_fail BEFORE INSERT ON alarm_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated test failure'");
        try {
            rejects(SQLException.class,()->process(failedSample),"processing fault is visible to caller");
            check(count("SELECT COUNT(*) n FROM alarm_event WHERE greenhouse_id=?",failure.house)==0&&text(task(failedSample),"state").equals("pending"),"event rollback keeps durable pending sample");
            rejects(SQLException.class,()->process(failedSample),"early repeat cannot bypass retry delay");
            check(number(task(failedSample),"attempts")==1,"early repeated callback does not burn retries");
            for(int i=0;i<5;i++){clock.advance(32);engine.processDueTasks(200);}
            check(text(task(failedSample),"state").equals("failed")&&number(task(failedSample),"attempts")==6,"five automatic retries then visible failed state");
            rejects(SecurityException.class,()->AlarmSampleInbox.retryFailed(AlarmDatabase::open,clock,failedSample,worker),"worker cannot retry failure");
        }finally{sql("DROP TRIGGER alarm_test_fail");}
        AlarmSampleInbox.retryFailed(AlarmDatabase::open,clock,failedSample,manager);engine.processDueTasks(200);
        check(text(task(failedSample),"state").equals("done")&&number(task(failedSample),"attempts")==7,"authorized retry succeeds without reuploading sample");
        check(count("SELECT COUNT(*) n FROM alarm_event WHERE greenhouse_id=?",failure.house)==1,"retry still creates only one event");
        rejects(IllegalArgumentException.class,()->AlarmSampleInbox.retryFailed(AlarmDatabase::open,clock,failedSample,admin),"completed task cannot be manually recalculated");
        sql("UPDATE todo_login_user SET status='disabled' WHERE id=?",manager);
        rejects(SecurityException.class,()->actions.confirm(oldEvent,manager),"disabled actor rejected by service");sql("UPDATE todo_login_user SET status='active' WHERE id=?",manager);
        System.out.println("Database: "+args[1]);System.out.println("Integration checks passed: "+checks.size());
        if(args.length>5)Files.write(Path.of(args[5]),checks,StandardCharsets.UTF_8);
    }
}
