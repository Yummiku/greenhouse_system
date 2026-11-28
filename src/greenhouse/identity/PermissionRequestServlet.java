package greenhouse.identity;

import greenhouse.greenhouse.CsrfGuard;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.json.*;
import java.io.*;
import java.sql.*;
import java.util.*;

@WebServlet("/api/permission-requests")
public class PermissionRequestServlet extends HttpServlet {
    private static final Set<String> WRITES=Set.of("create","approve","reject","withdraw");
    protected PermissionRequestDao createDao(){return new PermissionRequestDao();}
    @Override protected void doGet(HttpServletRequest q,HttpServletResponse p)throws IOException{run(q,p);}
    @Override protected void doPost(HttpServletRequest q,HttpServletResponse p)throws IOException{run(q,p);}
    private void run(HttpServletRequest q,HttpServletResponse p)throws IOException {
        q.setCharacterEncoding("UTF-8");p.setContentType("application/json;charset=UTF-8");p.setHeader("Cache-Control","no-store");p.setHeader("X-Content-Type-Options","nosniff");
        HttpSession session=q.getSession(false);Object current=session==null?null:session.getAttribute("loginUser");
        if(!(current instanceof Map)){reply(p,401,"请先登录",null);return;}
        Map<?,?> actor=(Map<?,?>)current;
        try {
            String action=param(q,"action","context"),scope=param(q,"scope","mine");
            if(!WRITES.contains(action) && !Set.of("context","list","detail","report","export","dashboard").contains(action))throw new UserProblem(400,"未知权限申请操作");
            boolean write=WRITES.contains(action);String method=write?"POST":"GET";
            if(!method.equals(q.getMethod())){p.setHeader("Allow",method);throw new UserProblem(405,"该操作须使用"+method);}
            if(write && !CsrfGuard.valid(q))throw new UserProblem(403,"页面验证失效，请刷新后重试");
            long id=UserManagementDao.positive(String.valueOf(actor.get("id")));String auth=String.valueOf(actor.get("authVersion"));
            PermissionRequestDao dao=createDao();
            if(write) {
                Map<String,String> values=new HashMap<>();for(String name:List.of("id","version","target_role","reason","review_opinion"))values.put(name,param(q,name,""));
                reply(p,200,"create".equals(action)?"申请已提交":"操作成功",dao.mutate(id,auth,action,values));return;
            }
            if(action.equals("context")){reply(p,200,"成功",dao.context(id,auth).put("csrf_token",CsrfGuard.token(session)));return;}
            if(action.equals("dashboard")){reply(p,200,"成功",dao.dashboard(id,auth));return;}
            if(action.equals("detail")){reply(p,200,"成功",dao.detail(id,auth,scope,UserManagementDao.positive(param(q,"id","0"))));return;}
            PermissionRequestDao.Query query=new PermissionRequestDao.Query(scope,param(q,"keyword",""),param(q,"status",""),param(q,"start",""),param(q,"end",""),param(q,"dir","desc"),param(q,"page","1"),param(q,"size","10"));
            JSONObject data=dao.list(id,auth,query,!action.equals("list"));
            if(action.equals("export")){csv(p,data.getJSONArray("items"));return;}
            reply(p,200,"成功",data);
        }catch(UserProblem e){reply(p,e.status,e.getMessage(),null);}
        catch(SQLIntegrityConstraintViolationException e){reply(p,409,"已有待审批申请或关联数据发生冲突，请刷新后检查",null);}
        catch(SQLException|JSONException e){log("权限申请执行失败",e);reply(p,500,"操作失败，请检查后台日志及61号升级脚本",null);}
    }
    private static String param(HttpServletRequest q,String name,String fallback){String v=q.getParameter(name);return v==null?fallback:v.trim();}
    static String state(String s){switch(s){case "pending":return "待审批";case "approved":return "已批准";case "rejected":return "已驳回";case "withdrawn":return "已撤回";default:return s;}}
    private void csv(HttpServletResponse p,JSONArray items)throws IOException,JSONException {
        p.setContentType("text/csv;charset=UTF-8");p.setHeader("Content-Disposition","attachment; filename=permission-requests.csv");PrintWriter w=p.getWriter();w.print("\ufeff账号,名称,申请角色,申请理由,状态,申请时间,审批人,审批时间,审批意见,撤回时间\r\n");
        for(int i=0;i<items.length();i++){
            JSONObject r=items.getJSONObject(i);String[] row={r.optString("username"),r.optString("display_name"),"普通管理员",r.optString("reason"),state(r.getString("status")),r.optString("created_at"),optional(r,"reviewer_name"),optional(r,"reviewed_at"),optional(r,"review_opinion"),optional(r,"withdrawn_at")};
            for(int k=0;k<row.length;k++){if(k>0)w.print(',');w.print(UserServlet.csv(row[k]));}w.print("\r\n");
        }
    }
    private static String optional(JSONObject r,String key){return r.isNull(key)?"":r.optString(key,"");}
    private void reply(HttpServletResponse p,int status,String message,Object data)throws IOException {
        p.setStatus(status);try{p.getWriter().print(new JSONObject().put("result_code",status==200?0:status).put("result_msg",message).put("data",data==null?JSONObject.NULL:data));}catch(JSONException e){throw new IOException(e);}
    }
}
