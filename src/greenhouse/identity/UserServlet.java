package greenhouse.identity;

import jakarta.servlet.http.*;
import org.json.*;
import java.io.*;
import java.sql.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 用户管理只开放给系统管理员；其他已登录角色可查本人资料和有效负责人选项。 */
public class UserServlet extends HttpServlet {
    private static final Set<String> READS=Set.of("context","me","options","list","detail","stats","report","export");
    private static final Set<String> WRITES=Set.of("create","update","set_status","delete","reset_password");
    @Override protected void doGet(HttpServletRequest q,HttpServletResponse p)throws IOException{run(q,p);}
    @Override protected void doPost(HttpServletRequest q,HttpServletResponse p)throws IOException{run(q,p);}

    protected UserManagementDao createDao(){return new UserManagementDao();}

    private void run(HttpServletRequest q,HttpServletResponse p)throws IOException {
        q.setCharacterEncoding("UTF-8");p.setContentType("application/json;charset=UTF-8");
        p.setHeader("Cache-Control","no-store");p.setHeader("X-Content-Type-Options","nosniff");
        HttpSession session=q.getSession(false);
        Object current=session==null?null:session.getAttribute("loginUser");
        if(!(current instanceof Map)){reply(p,401,"请先登录",null);return;}
        Map<?,?> actor=(Map<?,?>)current;String role=String.valueOf(actor.get("roleId"));
        if(!Set.of("1","2","3").contains(role)){reply(p,403,"角色无效",null);return;}
        try {
            String action=param(q,"action","context");
            if(!READS.contains(action) && !WRITES.contains(action))throw new UserProblem(400,"未注册的用户操作");
            boolean write=WRITES.contains(action);
            if(write && !"POST".equals(q.getMethod())){p.setHeader("Allow","POST");throw new UserProblem(405,"维护操作必须使用POST");}
            if(!write && !"GET".equals(q.getMethod())){p.setHeader("Allow","GET");throw new UserProblem(405,"查询操作必须使用GET");}
            boolean personal=Set.of("context","me","options","detail").contains(action);
            if(!personal && !"1".equals(role))throw new UserProblem(403,"仅系统管理员可访问用户管理");
            long actorId=UserManagementDao.positive(String.valueOf(actor.get("id")));
            UserManagementDao dao=createDao();
            if(action.equals("context")) {
                String token;
                synchronized(session) {
                    token=(String)session.getAttribute("userCsrfToken");
                    if(token==null){token=UUID.randomUUID().toString();session.setAttribute("userCsrfToken",token);}
                }
                reply(p,200,"成功",new JSONObject().put("can_manage","1".equals(role)).put("csrf_token",token).put("current",dao.detail(actorId)));
                return;
            }
            if(action.equals("options")){reply(p,200,"成功",new JSONObject().put("items",dao.options()));return;}
            if(action.equals("me") || action.equals("detail")) {
                long id=action.equals("me")?actorId:UserManagementDao.positive(param(q,"id","0"));
                if(!"1".equals(role) && id!=actorId)throw new UserProblem(403,"只能查看本人资料");
                reply(p,200,"成功",dao.detail(id));return;
            }
            if(write) {
                Object token=session.getAttribute("userCsrfToken");
                String submitted=q.getParameter("csrf_token");
                if(token==null || submitted==null || !MessageDigest.isEqual(token.toString().getBytes(StandardCharsets.UTF_8),submitted.getBytes(StandardCharsets.UTF_8)))
                    throw new UserProblem(403,"页面验证已失效，请刷新后重试");
                Map<String,String> values=new HashMap<>();
                for(String name:List.of("id","version","username","password","display_name","role_id","status")) {
                    String value=q.getParameter(name);if(value!=null)values.put(name,value);
                }
                JSONObject result=dao.mutate(actorId,String.valueOf(actor.get("authVersion")),action,values);
                reply(p,200,"保存成功",result);return;
            }
            UserManagementDao.Query query=new UserManagementDao.Query(param(q,"keyword",""),param(q,"role_id",""),param(q,"status",""),param(q,"sort","created_at"),param(q,"dir","desc"),param(q,"page","1"),param(q,"size","10"));
            switch(action) {
                case "list":reply(p,200,"成功",dao.list(query));break;
                case "stats":reply(p,200,"成功",dao.stats(query));break;
                case "report": {
                    JSONArray items=dao.all(query);
                    reply(p,200,"成功",new JSONObject().put("items",items));break;
                }
                case "export":export(p,dao.all(query));break;
                default:throw new UserProblem(400,"未实现的操作");
            }
        } catch(UserProblem e){reply(p,e.status,e.getMessage(),null);}
        catch(SQLIntegrityConstraintViolationException e){reply(p,409,"账号已存在或关联约束冲突",null);}
        catch(SQLException|JSONException e){log("用户管理执行失败",e);reply(p,500,"操作失败，请检查后台日志和09升级脚本",null);}
    }
    static String param(HttpServletRequest q,String key,String fallback){String v=q.getParameter(key);return v==null?fallback:v.trim();}
    private void export(HttpServletResponse p,JSONArray items)throws IOException,JSONException {
        p.setContentType("text/csv;charset=UTF-8");p.setHeader("Content-Disposition","attachment; filename=users.csv");
        PrintWriter w=p.getWriter();w.write('\ufeff');w.print("账号,显示名称,角色,状态,创建时间,更新时间\r\n");
        String[] keys={"username","display_name","role_id","status","created_at","updated_at"};
        for(int i=0;i<items.length();i++) {
            JSONObject r=items.getJSONObject(i);
            for(int k=0;k<keys.length;k++) {
                if(k>0)w.print(',');String v=r.optString(keys[k],"");
                if(keys[k].equals("role_id"))v=roleName(v);
                if(keys[k].equals("status"))v=statusName(v);
                w.print(csv(v));
            }
            w.print("\r\n");
        }
    }
    static String roleName(String role){return role.equals("1")?"系统管理员":role.equals("2")?"普通管理员":"普通用户";}
    static String statusName(String status){return status.equals("active")?"启用":status.equals("disabled")?"停用":"已删除";}
    static String csv(String value){
        if(!value.isEmpty() && ("=+-@\t\r\n".indexOf(value.charAt(0))>=0 || value.stripLeading().matches("^[=+\\-@].*")))value="'"+value;
        return "\""+value.replace("\"","\"\"")+"\"";
    }
    private void reply(HttpServletResponse p,int status,String message,Object data)throws IOException {
        p.setStatus(status);
        try{p.getWriter().print(new JSONObject().put("result_code",status==200?0:status).put("result_msg",message).put("data",data==null?JSONObject.NULL:data).toString());}
        catch(JSONException e){throw new IOException(e);}
    }
}
