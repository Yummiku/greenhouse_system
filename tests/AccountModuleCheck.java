package greenhouse.identity;

import java.sql.*;
import java.util.*;
import user.dao.*;
import user.security.PasswordUtil;

/** 本人资料和改密的隔离检查，仅使用H2与模拟HTTP，不读取MySQL配置。 */
public class AccountModuleCheck {
    static final String URL="jdbc:h2:mem:accountchecks;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    static final ConnectionProvider DB=()->DriverManager.getConnection(URL,"sa","");
    static final AccountDao DAO=new AccountDao(DB);
    static final UserDao AUTH=new UserDao(DB);
    static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    static void sql(String text)throws Exception{try(Connection c=DB.open();Statement s=c.createStatement()){s.execute(text);}}
    static class Servlet extends AccountServlet {
        @Override protected AccountDao createDao(){return DAO;}
        UserModuleCheck.Response call(UserModuleCheck.Session session,String method,String...pairs)throws Exception {
            var r=new UserModuleCheck.Response();var q=UserModuleCheck.request(session,method,UserModuleCheck.values(pairs),"/api/account");
            if(method.equals("GET"))doGet(q,r.proxy);else doPost(q,r.proxy);return r;
        }
    }
    static Servlet api=new Servlet();
    public static void main(String[] args)throws Exception {
        Class.forName("org.h2.Driver");
        sql("CREATE TABLE todo_login_user(id BIGINT AUTO_INCREMENT PRIMARY KEY,username VARCHAR(64) UNIQUE,password VARCHAR(255),display_name VARCHAR(100),role_id INT,status VARCHAR(16) DEFAULT 'active',created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,auth_version BIGINT DEFAULT 0,version BIGINT DEFAULT 0)");
        AUTH.addUser("admin","Original-test1","Admin",1);AUTH.addUser("manager","Original-test1","Manager",2);AUTH.addUser("worker","Original-test1","Worker",3);
        check(api.call(null,"GET","action","context").status==401,"Unauthenticated denied");
        for(String username:List.of("admin","manager","worker")) {
            var user=AUTH.login(username,"Original-test1");var session=new UserModuleCheck.Session(user);long id=Long.parseLong(user.get("id"));
            var context=api.call(session,"GET","action","context");check(context.status==200,"Every role has personal center");
            var data=context.json().getJSONObject("data");var row=data.getJSONObject("current");
            check(row.getLong("id")==id && !row.has("password") && !row.has("auth_version"),"Only safe current profile");
            String token=data.getString("csrf_token"),version=row.getString("version");
            check(api.call(session,"GET","action","profile").status==405,"Writes require POST");
            check(api.call(session,"POST","action","profile","display_name","Unsafe","version",version).status==403,"Missing CSRF rejected");
            check(api.call(session,"POST","action","profile","csrf_token","wrong","display_name","Unsafe","version",version).status==403,"Wrong CSRF rejected");
            for(String field:List.of("id","user_id","role_id","status","username","auth_version"))
                check(api.call(session,"POST","action","profile","csrf_token",token,field,"1","display_name","Unsafe","version",version).status==400,"Forbidden field "+field);
            check(api.call(session,"POST","action","profile","csrf_token",token,"display_name","   ","version",version).status==400,"Empty display name rejected");
            check(api.call(session,"POST","action","profile","csrf_token",token,"display_name",username+" <QA>","version",version).status==200,"Own display name saved");
            check(((Map<?,?>)session.data.get("loginUser")).get("displayName").equals(username+" <QA>"),"Session name refreshed");
            check(api.call(session,"POST","action","profile","csrf_token",token,"display_name","Stale","version",version).status==409,"Stale edit rejected");
            version=DAO.profile(id).getString("version");
            check(api.call(session,"POST","action","password","csrf_token",token,"version",version,"old_password","wrong","new_password","Changed-test2","confirmation","Changed-test2").status==400,"Wrong original password rejected");
            check(AUTH.login(username,"Original-test1")!=null,"Rejected change preserves password");
            check(api.call(session,"POST","action","password","csrf_token",token,"version",version,"old_password","Original-test1","new_password","Changed-test2","confirmation","different").status==400,"Confirmation checked");
            check(api.call(session,"POST","action","password","csrf_token",token,"version",version,"old_password","Original-test1","new_password","short","confirmation","short").status==400,"Short password rejected");
            check(api.call(session,"POST","action","password","csrf_token",token,"version",version,"old_password","Original-test1","new_password","测".repeat(25),"confirmation","测".repeat(25)).status==400,"BCrypt byte limit checked");
            var changed=api.call(session,"POST","action","password","csrf_token",token,"version",version,"old_password","Original-test1","new_password","Changed-test2","confirmation","Changed-test2");
            check(changed.status==200 && session.invalid,"Successful change invalidates current session");
            check(AUTH.login(username,"Original-test1")==null && AUTH.login(username,"Changed-test2")!=null,"Old password rejected, new accepted");
            check(!user.get("authVersion").equals(AUTH.findActiveById(Long.toString(id)).get("authVersion")),"Other sessions revoked by auth_version");
        }
        sql("UPDATE todo_login_user SET status='disabled' WHERE id=3");
        try{DAO.update(3,"1","2","profile","Bad",null,null,null);throw new AssertionError("Disabled update allowed");}catch(UserProblem e){check(e.status==401,"Disabled actor denied");}
        check(PasswordUtil.isEncoded(PasswordUtil.hash("Final-test3")),"Existing BCrypt retained");
        System.out.println("PASS: "+checks+" personal-center DAO/API assertions; isolated H2 and mock HTTP only.");
    }
}
