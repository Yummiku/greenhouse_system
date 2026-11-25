package greenhouse.alarm;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.Set;
import org.json.JSONObject;

/** Module-owned extension on the existing independently authenticated route; no global login bypass. */
@WebFilter(urlPatterns="/api/week08/auth",dispatcherTypes=DispatcherType.REQUEST)
public final class AlarmMobileFilter implements Filter {
    @Override public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain)throws IOException,ServletException {
        var q=(HttpServletRequest)request;var p=(HttpServletResponse)response;String action=q.getParameter("action");
        if(action!=null&&action.startsWith("alarm_")) {
            p.setContentType("application/json;charset=UTF-8");p.setHeader("Cache-Control","no-store");
            try {
                AlarmMobileIdentity.require(q.getHeader("X-Week08-Token"));
                if(!q.getMethod().equals("GET")){AlarmServlet.reply(p,405,"小程序报警仅提供GET只读查询",null);return;}
                String read=action.substring(6);
                if(!Set.of("list","detail","stats","options").contains(read)){AlarmServlet.reply(p,403,"小程序报警只读，不允许维护",null);return;}
                AlarmServlet.reply(p,200,"成功",AlarmQueries.read(q,read));
            }catch(SecurityException e){AlarmServlet.reply(p,401,e.getMessage(),null);}
            catch(IllegalArgumentException e){AlarmServlet.reply(p,400,e.getMessage(),null);}
            catch(Exception e){q.getServletContext().log("Alarm mobile read unavailable; verify authentication and migrations");AlarmServlet.reply(p,503,"报警读取暂不可用，请稍后重试",null);}
            return;
        }
        if(!"wx_login".equals(action)&&!"bind_user".equals(action)){chain.doFilter(q,p);return;}
        // Observe successful existing auth responses to snapshot account version. Never return/log the token here.
        StringWriter buffer=new StringWriter();PrintWriter writer=new PrintWriter(buffer);
        var wrapper=new HttpServletResponseWrapper(p){@Override public PrintWriter getWriter(){return writer;}};
        chain.doFilter(q,wrapper);writer.flush();String body=buffer.toString();
        try {
            JSONObject result=new JSONObject(body);
            if(p.getStatus()==200&&result.optInt("result_code",-1)==0
                    && ("wx_login".equals(action)||!result.optBoolean("already_bound",true))) {
                String token="wx_login".equals(action)?result.optString("token",null):q.getHeader("X-Week08-Token");
                AlarmMobileIdentity.registerFresh(token);
            }
        }catch(Exception e){q.getServletContext().log("Alarm mobile grant not registered; re-login after checking migration 34");}
        p.getWriter().write(body);
    }
}
