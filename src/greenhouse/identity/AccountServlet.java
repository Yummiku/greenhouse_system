package greenhouse.identity;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.*;

@WebServlet("/api/account")
public class AccountServlet extends HttpServlet {
    protected AccountDao createDao() { return new AccountDao(); }
    @Override protected void doGet(HttpServletRequest q,HttpServletResponse p)throws IOException { run(q,p); }
    @Override protected void doPost(HttpServletRequest q,HttpServletResponse p)throws IOException { run(q,p); }
    private void run(HttpServletRequest q,HttpServletResponse p)throws IOException {
        q.setCharacterEncoding("UTF-8");p.setContentType("application/json;charset=UTF-8");
        p.setHeader("Cache-Control","no-store");p.setHeader("X-Content-Type-Options","nosniff");
        HttpSession session=q.getSession(false);
        Object value=session==null?null:session.getAttribute("loginUser");
        if(!(value instanceof Map)){reply(p,401,"请先登录",null);return;}
        Map<?,?> actor=(Map<?,?>)value;
        try {
            if(!Set.of("1","2","3").contains(String.valueOf(actor.get("roleId"))))throw new UserProblem(403,"角色无效");
            long id=UserManagementDao.positive(String.valueOf(actor.get("id")));
            String action=UserServlet.param(q,"action","context");
            AccountDao dao=createDao();
            if(action.equals("context")) {
                if(!q.getMethod().equals("GET"))throw new UserProblem(405,"资料查询使用GET");
                String token;
                synchronized(session){
                    token=(String)session.getAttribute("accountCsrfToken");
                    if(token==null){token=UUID.randomUUID().toString();session.setAttribute("accountCsrfToken",token);}
                }
                reply(p,200,"成功",new JSONObject().put("current",dao.profile(id)).put("csrf_token",token));return;
            }
            if(!Set.of("profile","password").contains(action))throw new UserProblem(400,"未注册的个人资料操作");
            if(!q.getMethod().equals("POST"))throw new UserProblem(405,"修改资料必须使用POST");
            String token=(String)session.getAttribute("accountCsrfToken"),submitted=q.getParameter("csrf_token");
            if(token==null || submitted==null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),submitted.getBytes(StandardCharsets.UTF_8)))
                throw new UserProblem(403,"页面验证失效，请刷新后重试");
            for(String forbidden:List.of("id","user_id","username","role_id","status","auth_version"))
                if(q.getParameter(forbidden)!=null)throw new UserProblem(400,"个人中心不能修改账号、角色、状态或指定他人");
            dao.update(id,String.valueOf(actor.get("authVersion")),q.getParameter("version"),action,
                    q.getParameter("display_name"),q.getParameter("old_password"),q.getParameter("new_password"),q.getParameter("confirmation"));
            if(action.equals("password")) {
                session.invalidate();reply(p,200,"密码已修改，请重新登录",null);
            } else {
                JSONObject profile=dao.profile(id);
                Map<String,Object> fresh=new HashMap<>();actor.forEach((k,v)->fresh.put(String.valueOf(k),v));
                fresh.put("displayName",profile.getString("display_name"));session.setAttribute("loginUser",fresh);
                reply(p,200,"个人资料已保存",profile);
            }
        } catch(UserProblem e){reply(p,e.status,e.getMessage(),null);}
        catch(SQLException|JSONException e){log("个人资料操作失败",e);reply(p,500,"个人资料服务暂不可用，请稍后重试",null);}
    }
    private void reply(HttpServletResponse p,int status,String text,Object data)throws IOException {
        p.setStatus(status);
        try{p.getWriter().print(new JSONObject().put("result_code",status==200?0:status).put("result_msg",text).put("data",data==null?JSONObject.NULL:data));}
        catch(JSONException e){throw new IOException(e);}
    }
}
