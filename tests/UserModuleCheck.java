package greenhouse.identity;

import user.dao.*;
import user.filter.LoginFilter;
import user.security.PasswordUtil;
import org.json.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.sql.*;
import java.util.*;
import java.io.*;
import java.lang.reflect.*;

/** 隔离集成检查：只使用内存H2，不读取项目本机凭据，不连接MySQL。 */
public class UserModuleCheck {
    static final String URL="jdbc:h2:mem:userchecks;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    static final ConnectionProvider DB=()->DriverManager.getConnection(URL,"sa","");
    static final UserManagementDao DAO=new UserManagementDao(DB);
    static final UserDao AUTH=new UserDao(DB);
    static int checks=0;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    static Map<String,String> values(String... pairs){Map<String,String> m=new HashMap<>();for(int i=0;i<pairs.length;i+=2)m.put(pairs[i],pairs[i+1]);return m;}
    static void problem(int expected,RunnableSql work)throws Exception{try{work.run();throw new AssertionError("Expected "+expected);}catch(UserProblem e){check(e.status==expected,"Expected "+expected+" got "+e.status);}}
    interface RunnableSql {void run()throws Exception;}
    static Map<String,String> input(long id,String... rest)throws Exception{Map<String,String> m=values(rest);m.put("id",Long.toString(id));m.put("version",DAO.detail(id).get("version").toString());return m;}
    static long create(String name,String role,String status)throws Exception{return DAO.mutate(1,"0","create",values("username",name,"display_name",name,"role_id",role,"status",status,"password","Test-only-Aa1")).getLong("id");}
    static class Session {
        Map<String,Object> data=new HashMap<>();boolean invalid;
        HttpSession proxy=(HttpSession)Proxy.newProxyInstance(UserModuleCheck.class.getClassLoader(),new Class[]{HttpSession.class},(o,m,a)->{
            switch(m.getName()){
                case "getAttribute":return data.get(a[0]);
                case "setAttribute":data.put((String)a[0],a[1]);return null;
                case "removeAttribute":data.remove(a[0]);return null;
                case "invalidate":invalid=true;data.clear();return null;
                case "getId":return "test-session";
                default:return empty(m.getReturnType());
            }
        });
        Session(Map<String,String> user){if(user!=null)data.put("loginUser",user);}
    }
    static Object empty(Class<?> type){if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;return null;}
    static class Response {
        int status=200;StringWriter body=new StringWriter();PrintWriter writer=new PrintWriter(body);String contentType="";Map<String,String> headers=new HashMap<>();
        HttpServletResponse proxy=(HttpServletResponse)Proxy.newProxyInstance(UserModuleCheck.class.getClassLoader(),new Class[]{HttpServletResponse.class},(o,m,a)->{
            switch(m.getName()){
                case "setStatus":status=(Integer)a[0];return null;
                case "getStatus":return status;
                case "getWriter":return writer;
                case "setHeader":headers.put((String)a[0],(String)a[1]);return null;
                case "setContentType":contentType=(String)a[0];return null;
                case "sendRedirect":status=302;headers.put("Location",(String)a[0]);return null;
                default:return empty(m.getReturnType());
            }
        });
        JSONObject json()throws Exception{return new JSONObject(body.toString());}
    }
    static HttpServletRequest request(Session s,String method,Map<String,String> params){
        return request(s,method,params,"/api/users");
    }
    static HttpServletRequest request(Session s,String method,Map<String,String> params,String path){
        return (HttpServletRequest)Proxy.newProxyInstance(UserModuleCheck.class.getClassLoader(),new Class[]{HttpServletRequest.class},(o,m,a)->{
            switch(m.getName()){
                case "getParameter":return params.get(a[0]);
                case "getSession":return s==null || s.invalid?null:s.proxy;
                case "getMethod":return method;
                case "getServletPath":return path;
                case "getRequestURI":return "/greenhouse_system"+path;
                case "getContextPath":return "/greenhouse_system";
                default:return empty(m.getReturnType());
            }
        });
    }
    static class TestServlet extends UserServlet {
        @Override protected UserManagementDao createDao(){return DAO;}
        Response invoke(Session s,String method,Map<String,String> p)throws Exception{Response r=new Response();if(method.equals("GET"))doGet(request(s,method,p),r.proxy);else doPost(request(s,method,p),r.proxy);return r;}
    }
    static Response call(Session s,String method,String... params)throws Exception{return new TestServlet().invoke(s,method,values(params));}
    static void filtered(Session s,boolean expected)throws Exception {
        Response r=new Response();int[] chained={0};
        LoginFilter filter=new LoginFilter(){@Override protected UserDao userDao(){return AUTH;}};
        filter.doFilter(request(s,"GET",values()),r.proxy,(q,p)->chained[0]++);
        check((chained[0]==1)==expected,"Session filter result");
        if(!expected)check(r.status==401 && s.invalid,"Revoked session must invalidate and return 401");
    }
    static void jdbc(String sql)throws Exception{try(Connection c=DB.open();Statement s=c.createStatement()){s.execute(sql);}}
    static UserManagementDao.Query query(String keyword,String role,String status){return new UserManagementDao.Query(keyword,role,status,"created_at","desc","1","10");}

    public static void main(String[] args)throws Exception {
        Class.forName("org.h2.Driver");
        jdbc("CREATE TABLE todo_login_user(id BIGINT AUTO_INCREMENT PRIMARY KEY,username VARCHAR(64) UNIQUE NOT NULL,password VARCHAR(255) NOT NULL,display_name VARCHAR(100) NOT NULL,role_id INT NOT NULL,status VARCHAR(16) NOT NULL DEFAULT 'active',created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,auth_version BIGINT NOT NULL DEFAULT 0,version BIGINT NOT NULL DEFAULT 0)");
        AUTH.addUser("admin","Test-only-Aa1","Administrator",1);
        Session admin=new Session(AUTH.login("admin","Test-only-Aa1"));
        check(AUTH.login("admin","wrong-password")==null,"Wrong password rejected");
        check(!((Map<?,?>)admin.data.get("loginUser")).containsKey("password"),"Session excludes password");
        long manager=create("manager","2","active"),worker=create("worker","3","active"),disabled=create("disabled","3","disabled");
        check(AUTH.login("disabled","Test-only-Aa1")==null,"Disabled login rejected");
        check(DAO.options().length()==3,"Only active options");
        check(!DAO.detail(worker).has("password") && !DAO.detail(worker).has("auth_version"),"Detail excludes credentials and auth version");
        check(DAO.list(query("work","3","active")).getLong("total")==1,"Combined filters");
        check(DAO.list(query("' OR 1=1 --","","" )).getLong("total")==0,"Literal query input");
        problem(400,()->new UserManagementDao.Query("","","","id;DROP TABLE x","asc","1","10"));
        problem(400,()->new UserManagementDao.Query("","","","username","asc","0","10"));
        problem(400,()->create("bad username","3","active"));
        problem(403,()->DAO.mutate(manager,"0","delete",input(worker)));
        problem(400,()->DAO.mutate(1,"0","delete",input(1)));
        problem(400,()->DAO.mutate(1,"0","update",input(1,"display_name","Administrator","role_id","3","status","active")));
        Map<String,String> oldInput=input(worker,"display_name","Worker renamed","role_id","3","status","active");
        Session workerSession=new Session(AUTH.login("worker","Test-only-Aa1"));
        DAO.mutate(1,"0","update",oldInput);
        filtered(workerSession,true);
        check(((Map<?,?>)workerSession.data.get("loginUser")).get("displayName").equals("Worker renamed"),"Session profile refreshed");
        problem(409,()->DAO.mutate(1,"0","update",oldInput));
        DAO.mutate(1,"0","set_status",input(worker,"status","disabled"));
        filtered(workerSession,false);
        DAO.mutate(1,"0","set_status",input(worker,"status","active"));
        Session oldVersion=new Session(values("id",Long.toString(worker),"roleId","3","authVersion","0"));filtered(oldVersion,false);
        Session promotedOld=new Session(AUTH.login("worker","Test-only-Aa1"));
        DAO.mutate(1,"0","update",input(worker,"display_name","Worker renamed","role_id","2","status","active"));filtered(promotedOld,false);
        Session passwordOld=new Session(AUTH.login("worker","Test-only-Aa1"));
        DAO.mutate(1,"0","reset_password",input(worker,"password","New-test-only-Bb2"));
        filtered(passwordOld,false);check(AUTH.login("worker","Test-only-Aa1")==null,"Old password rejected");
        check(AUTH.login("worker","New-test-only-Bb2")!=null,"Reset password works");
        jdbc("CREATE TABLE sample_business(id INT PRIMARY KEY,user_id BIGINT REFERENCES todo_login_user(id))");
        jdbc("INSERT INTO sample_business VALUES(1,"+worker+")");
        DAO.mutate(1,"0","delete",input(worker));
        check(DAO.detail(worker).getString("status").equals("deleted"),"Logical delete preserves account row");
        check(AUTH.findActiveById(Long.toString(worker))==null,"Deleted identity rejected");
        problem(409,()->DAO.mutate(1,"0","set_status",input(worker,"status","active")));
        check(DAO.all(query("","","deleted")).length()==1,"Deleted filter explicit");
        check(DAO.stats(query("","","")).getLong("total")==DAO.all(query("","","")).length(),"Stats and full filtered records agree");
        Response ctx=call(admin,"GET","action","context");check(ctx.status==200,"Context works");
        String csrf=ctx.json().getJSONObject("data").getString("csrf_token");
        Session normal=new Session(AUTH.login("manager","Test-only-Aa1"));
        check(call(null,"GET","action","list").status==401,"Anonymous rejected");
        check(call(normal,"GET","action","list").status==403,"Non-admin list denied");
        check(call(normal,"GET","action","stats").status==403,"Non-admin stats denied");
        check(call(normal,"GET","action","export").status==403,"Non-admin export denied");
        check(call(normal,"GET","action","report").status==403,"Non-admin full report denied");
        check(call(normal,"GET","action","me").status==200,"Own profile available");
        check(call(normal,"GET","action","detail","id","1").status==403,"Other profile denied");
        check(call(normal,"GET","action","options").status==200,"Shared active options available");
        long readerId=create("reader","3","active");
        Session reader=new Session(AUTH.login("reader","Test-only-Aa1"));
        check(call(reader,"GET","action","list").status==403,"Role3 list denied");
        check(call(reader,"GET","action","detail","id",Long.toString(readerId)).status==200,"Role3 own detail allowed");
        check(call(reader,"GET","action","detail","id","1").status==403,"Role3 other detail denied");
        check(call(reader,"GET","action","options").status==200,"Role3 sanitized options allowed");
        check(call(reader,"POST","action","delete","csrf_token",csrf,"id","1","version","0").status==403,"Role3 write denied");

        check(call(admin,"GET","action","create").status==405,"GET write denied");
        check(call(admin,"POST","action","create").status==403,"Missing CSRF denied");
        check(call(normal,"POST","action","delete","csrf_token",csrf,"id","1","version","0").status==403,"Role escalation via write denied");
        check(call(admin,"GET","action","unknown").status==400,"Unknown action rejected");
        Response created=call(admin,"POST","action","create","csrf_token",csrf,"username","api_created","display_name","=1+1","role_id","3","status","active","password","Test-only-Aa1");
        check(created.status==200,"HTTP create succeeds with CSRF");
        check(call(admin,"POST","action","create","csrf_token",csrf,"username","api_created","display_name","Duplicate","role_id","3","status","active","password","Test-only-Aa1").status==409,"Duplicate account returns 409");
        Response csv=call(admin,"GET","action","export");
        check(csv.status==200 && csv.contentType.startsWith("text/csv") && csv.body.toString().startsWith("\ufeff"),"CSV header and BOM");
        check(csv.body.toString().contains("'=1+1"),"CSV formula protection");
        check(!csv.body.toString().contains("Test-only-Aa1") && !csv.body.toString().contains("$2a$"),"CSV excludes secrets");
        Response report=call(admin,"GET","action","report");check(report.status==200 && report.json().getJSONObject("data").getJSONArray("items").length()>0,"Full filtered print snapshot");
        check(UserServlet.csv(" \t=1+1").startsWith("\"'"),"Whitespace formula protected");
        Session revokedBind=new Session(values("id",Long.toString(worker),"roleId","1","authVersion","0"));
        Response bindResponse=new Response();int[] bindChained={0};
        new LoginFilter(){@Override protected UserDao userDao(){return AUTH;}}.doFilter(
            request(revokedBind,"POST",values("action","create_ticket"),"/api/week08/bind"),bindResponse.proxy,(q,p)->bindChained[0]++);
        check(bindResponse.status==401,"Revoked PC bind session denied");
        check(bindChained[0]==0 && revokedBind.invalid,"Revoked bind does not reach servlet");

        System.out.println("PASS: "+checks+" assertions; actual Java DAO/API/filter code with isolated H2 and mock HTTP. No production MySQL accessed.");
    }
}
