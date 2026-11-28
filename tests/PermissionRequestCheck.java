package greenhouse.identity;

import org.json.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static greenhouse.identity.UserModuleCheck.*;

/** 运行实际DAO、Servlet与登录过滤器；只连接隔离H2，不读取本机数据库配置。 */
public class PermissionRequestCheck {
    static final PermissionRequestDao REQUESTS=new PermissionRequestDao(DB);
    static Map<String,String> input(long id,String version,String... rest){Map<String,String> m=values(rest);m.put("id",Long.toString(id));m.put("version",version);return m;}
    static long submit(long user,String reason)throws Exception{return REQUESTS.mutate(user,"0","create",values("target_role","2","reason",reason)).getLong("id");}
    static PermissionRequestDao.Query query(String scope){return new PermissionRequestDao.Query(scope,"","","","","desc","1","10");}
    static class Api extends PermissionRequestServlet {
        @Override protected PermissionRequestDao createDao(){return REQUESTS;}
        Response invoke(Session session,String method,String... params)throws Exception{
            Response r=new Response();if(method.equals("POST"))doPost(request(session,method,values(params)),r.proxy);else doGet(request(session,method,values(params)),r.proxy);return r;
        }
    }
    static final Api API=new Api();
    public static void main(String[] args)throws Exception {
        Class.forName("org.h2.Driver");
        jdbc("CREATE TABLE todo_login_user(id BIGINT AUTO_INCREMENT PRIMARY KEY,username VARCHAR(64) UNIQUE NOT NULL,password VARCHAR(255) NOT NULL,display_name VARCHAR(100) NOT NULL,role_id INT NOT NULL,status VARCHAR(16) NOT NULL DEFAULT 'active',created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,auth_version BIGINT NOT NULL DEFAULT 0,version BIGINT NOT NULL DEFAULT 0)");
        String ddl=Files.readString(Path.of("sql/61_permission_requests.sql"),StandardCharsets.UTF_8);
        ddl=ddl.substring(ddl.indexOf("CREATE TABLE"),ddl.indexOf("-- 不建跨版本"));
        // H2不支持MySQL的STORED/ENGINE/KEY声明。保留相同列、生成表达式、检查与唯一约束。
        ddl=ddl.replace(" STORED","").replace("UNIQUE KEY uq_permission_pending", "CONSTRAINT uq_permission_pending UNIQUE")
            .replaceAll("(?m)^    KEY idx_permission_.*\\R", "").replaceAll("\\) ENGINE=InnoDB.*;", ");");
        jdbc(ddl);
        AUTH.addUser("admin","Test-only-Aa1","Administrator",1);
        long reviewer=create("reviewer","1","active"),manager=create("manager","2","active"),worker=create("worker","3","active"),other=create("other","3","active");
        Session adminSession=new Session(AUTH.login("admin","Test-only-Aa1")),workerSession=new Session(AUTH.login("worker","Test-only-Aa1"));
        problem(400,()->REQUESTS.mutate(worker,"0","create",values("target_role","1","reason","Escalation")));
        problem(400,()->submit(worker,"  "));
        problem(400,()->submit(worker,"a".repeat(501)));
        problem(403,()->submit(manager,"Already manager"));
        long first=submit(worker,"  <script>alert(1)</script>  ");
        check(REQUESTS.detail(worker,"0","mine",first).getString("reason").equals("<script>alert(1)</script>"),"Trimmed reason stored as literal data");
        problem(409,()->submit(worker,"duplicate"));
        try{jdbc("INSERT INTO permission_request(applicant_id,target_role,reason,status) VALUES("+worker+",2,'racing insert','pending')");throw new AssertionError("Unique pending expected");}catch(SQLException e){check(e.getSQLState().startsWith("23"),"Database unique pending invariant");}
        long second=submit(other,"Other application");
        check(REQUESTS.list(worker,"0",query("mine"),true).getLong("total")==1,"Own list excludes other applicant");
        problem(404,()->REQUESTS.detail(worker,"0","mine",second));
        problem(403,()->REQUESTS.list(worker,"0",query("review"),true));
        problem(403,()->REQUESTS.mutate(manager,"0","approve",input(first,"0")));
        problem(404,()->REQUESTS.mutate(other,"0","withdraw",input(first,"0")));
        problem(400,()->REQUESTS.mutate(1,"0","reject",input(first,"0","review_opinion"," ")));
        problem(400,()->REQUESTS.mutate(worker,"0","withdraw",input(first,"-1")));
        problem(409,()->REQUESTS.mutate(1,"0","approve",input(first,"9")));

        DAO.mutate(1,"0","set_status",UserModuleCheck.input(worker,"status","disabled"));
        problem(409,()->REQUESTS.mutate(1,"0","approve",input(first,"0")));
        check(REQUESTS.detail(1,"0","review",first).getString("status").equals("pending"),"Disabled approval leaves request pending");
        DAO.mutate(1,"0","set_status",UserModuleCheck.input(worker,"status","active"));
        // 强制申请更新失败，验证之前的角色更新会与申请一起回滚。
        jdbc("ALTER TABLE permission_request ADD CONSTRAINT test_rollback CHECK(status<>'approved')");
        try{REQUESTS.mutate(1,"0","approve",input(first,"0"));throw new AssertionError("Forced DB rejection expected");}catch(SQLException e){check(DAO.detail(worker).getInt("role_id")==3,"Role update rolled back on request failure");}
        jdbc("ALTER TABLE permission_request DROP CONSTRAINT test_rollback");
        Session beforeApproval=new Session(AUTH.login("worker","Test-only-Aa1"));
        REQUESTS.mutate(1,"0","approve",input(first,"0","review_opinion","Approved for daily work"));
        check(DAO.detail(worker).getInt("role_id")==2,"Approval changes actual role");
        check(REQUESTS.detail(1,"0","review",first).getString("status").equals("approved"),"Approval state committed");
        filtered(beforeApproval,false);
        check(AUTH.login("worker","Test-only-Aa1").get("roleId").equals("2"),"New login receives new role");
        problem(401,()->REQUESTS.mutate(worker,"0","create",values("target_role","2","reason","Stale identity")));
        problem(409,()->REQUESTS.mutate(1,"0","reject",input(first,"0","review_opinion","Late rejection")));
        problem(409,()->REQUESTS.mutate(worker,AUTH.findActiveById(Long.toString(worker)).get("authVersion"),"withdraw",input(first,"1")));

        REQUESTS.mutate(other,"0","withdraw",input(second,"0"));
        check(REQUESTS.detail(other,"0","mine",second).getString("status").equals("withdrawn"),"Withdraw preserves history");
        long third=submit(other,"Resubmitted");
        REQUESTS.mutate(1,"0","reject",input(third,"0","review_opinion","Please explain duties"));
        check(DAO.detail(other).getInt("role_id")==3,"Rejection does not change role");
        check(REQUESTS.detail(other,"0","mine",third).getString("review_opinion").equals("Please explain duties"),"Rejection opinion recorded");

        long concurrent=submit(other,"Concurrent decision");
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch gate=new CountDownLatch(1);
        try {
            List<Callable<Integer>> calls=List.of(()->{gate.await();try{REQUESTS.mutate(1,"0","approve",input(concurrent,"0"));return 200;}catch(UserProblem e){return e.status;}},
                ()->{gate.await();try{REQUESTS.mutate(reviewer,"0","reject",input(concurrent,"0","review_opinion","Concurrent rejection"));return 200;}catch(UserProblem e){return e.status;}});
            Future<Integer> a=pool.submit(calls.get(0)),b=pool.submit(calls.get(1));gate.countDown();
            List<Integer> outcomes=new ArrayList<>(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS)));Collections.sort(outcomes);
            check(outcomes.equals(List.of(200,409)),"Concurrent approve/reject exactly one wins");
            JSONObject finalRequest=REQUESTS.detail(1,"0","review",concurrent);
            check((DAO.detail(other).getInt("role_id")==2)==finalRequest.getString("status").equals("approved"),"Concurrent final role and request agree");
        } finally {pool.shutdownNow();}
        long fresh=create("fresh","3","active");Session freshSession=new Session(AUTH.login("fresh","Test-only-Aa1"));
        check(API.invoke(null,"GET","action","list").status==401,"Anonymous API denied");
        check(API.invoke(freshSession,"GET","action","list","scope","review").status==403,"Review read denied");
        check(API.invoke(freshSession,"GET","action","export","scope","review").status==403,"Review export denied");
        check(API.invoke(freshSession,"GET","action","report","scope","review").status==403,"Review print denied");
        check(API.invoke(freshSession,"GET","action","create").status==405,"Mutations reject GET");
        check(API.invoke(freshSession,"POST","action","create","target_role","2","reason","No CSRF").status==403,"CSRF required");
        String csrf=API.invoke(freshSession,"GET","action","context").json().getJSONObject("data").getString("csrf_token");
        Response submitted=API.invoke(freshSession,"POST","action","create","csrf_token",csrf,"target_role","2","reason","=1+1");
        check(submitted.status==200,"API submission with valid CSRF");
        long freshId=submitted.json().getJSONObject("data").getLong("id");
        check(API.invoke(freshSession,"GET","action","detail","id",Long.toString(first)).status==404,"Foreign detail hidden");
        check(API.invoke(freshSession,"GET","action","export").body.toString().contains("'=1+1"),"CSV neutralizes formulas");
        check(API.invoke(freshSession,"GET","action","report").json().getJSONObject("data").getLong("total")==1,"Print own results only");
        check(API.invoke(freshSession,"GET","action","dashboard").json().getJSONObject("data").getJSONArray("users").length()==0,"Dashboard hides global user counts from worker");
        JSONObject dashboard=API.invoke(adminSession,"GET","action","dashboard").json().getJSONObject("data");
        check(dashboard.getJSONArray("users").length()>0 && !dashboard.getBoolean("active_users_available"),"Admin counts available; active count explicitly unavailable");
        DAO.mutate(1,"0","update",UserModuleCheck.input(fresh,"display_name","Fresh","role_id","2","status","active"));
        problem(409,()->REQUESTS.mutate(1,"0","approve",input(freshId,"0")));
        problem(401,()->REQUESTS.dashboard(fresh,"0"));
        long racer=create("racer","3","active");
        ExecutorService submissions=Executors.newFixedThreadPool(2);CountDownLatch submitGate=new CountDownLatch(1);
        try {
            Callable<Integer> call=()->{submitGate.await();try{submit(racer,"Double click");return 200;}catch(UserProblem e){return e.status;}};
            Future<Integer> a=submissions.submit(call),b=submissions.submit(call);submitGate.countDown();
            List<Integer> outcomes=new ArrayList<>(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS)));Collections.sort(outcomes);
            check(outcomes.equals(List.of(200,409)),"Parallel submissions produce only one pending request");
        } finally {submissions.shutdownNow();}
        long racingId=REQUESTS.list(racer,"0",query("mine"),false).getJSONArray("items").getJSONObject(0).getLong("id");
        ExecutorService withdrawals=Executors.newFixedThreadPool(2);CountDownLatch withdrawGate=new CountDownLatch(1);
        try {
            Future<Integer> a=withdrawals.submit(()->{withdrawGate.await();try{REQUESTS.mutate(racer,"0","withdraw",input(racingId,"0"));return 200;}catch(UserProblem e){return e.status;}});
            Future<Integer> b=withdrawals.submit(()->{withdrawGate.await();try{REQUESTS.mutate(1,"0","approve",input(racingId,"0"));return 200;}catch(UserProblem e){return e.status;}});
            withdrawGate.countDown();int x=a.get(10,TimeUnit.SECONDS),y=b.get(10,TimeUnit.SECONDS);
            check((x==200 && y==409) || (y==200 && (x==401 || x==409)),"Concurrent approval/withdrawal has one success");
            JSONObject finalRequest=REQUESTS.detail(1,"0","review",racingId);
            check((DAO.detail(racer).getInt("role_id")==2)==finalRequest.getString("status").equals("approved"),"Withdrawal race leaves consistent role");
        } finally {withdrawals.shutdownNow();}
        JSONObject own=REQUESTS.list(1,"0",query("review"),false);long grouped=0;
        for(int i=0;i<own.getJSONArray("groups").length();i++)grouped+=own.getJSONArray("groups").getJSONObject(i).getLong("count");
        check(grouped==own.getLong("total"),"List and grouped statistics share snapshot");
        PermissionRequestDao.Query exact=new PermissionRequestDao.Query("review","=1+1","pending","","","desc","100","10");
        JSONObject matching=REQUESTS.list(1,"0",exact,false);
        check(matching.getLong("total")==1 && matching.getInt("page")==1,"Literal filters and stale page clamp");
        check(matching.getJSONArray("items").getJSONObject(0).getInt("source_role")==3,"Original role snapshot retained");
        Response csv=API.invoke(adminSession,"GET","action","export","scope","review");
        check(!csv.body.toString().contains("\"null\"") && !csv.body.toString().contains("Test-only-Aa1"),"CSV excludes null placeholders and credentials");
        problem(400,()->new PermissionRequestDao.Query("review","","pending","2026-11-30","2026-11-01","desc","1","10"));
        problem(400,()->new PermissionRequestDao.Query("review","","wrong","","","desc","1","10"));
        System.out.println("PASS: "+checks+" assertions; actual permission DAO/API/filter, rollback, parallel decisions, scope and CSRF. Isolated H2 only; MySQL/Tomcat acceptance remains required.");
    }
}
