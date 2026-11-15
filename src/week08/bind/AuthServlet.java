package week08.bind;

import jakarta.servlet.http.*;
import org.json.JSONObject;
import week08.location.LocationDao;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import static week08.bind.AuthDao.AuthError;

public class AuthServlet extends HttpServlet {
    private final AuthDao dao=new AuthDao();
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private String config(String property,String env) {
        String v=greenhouse.config.LocalSettings.get(property,env,"");
        return v==null?"":v.trim();
    }
    private String appid(){return config("week08.wxAppId","WEEK08_WX_APPID");}
    private String secret(){return config("week08.wxAppSecret","WEEK08_WX_APPSECRET");}
    private static String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    private String exchange(String code) throws Exception {
        if(appid().isEmpty()||secret().isEmpty())throw new AuthError(503,"请先给Tomcat配置自己的小程序AppID和AppSecret");
        if(code==null||code.isBlank()||code.length()>512)throw new AuthError(400,"缺少本次wx.login取得的code");
        URI uri=URI.create("https://api.weixin.qq.com/sns/jscode2session?appid="+enc(appid())
                +"&secret="+enc(secret())+"&js_code="+enc(code)+"&grant_type=authorization_code");
        HttpResponse<String> remote;
        try {
            remote=client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),HttpResponse.BodyHandlers.ofString());
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();throw new AuthError(502,"连接微信服务被中断，请重试");
        } catch(IOException e) {throw new AuthError(502,"虚拟机无法连接微信服务，请检查虚拟机联网");}
        if(remote.statusCode()!=200)throw new AuthError(502,"微信服务返回HTTP "+remote.statusCode());
        JSONObject j=new JSONObject(remote.body());
        if(!j.has("openid"))throw new AuthError(502,"微信换取OpenID失败，errcode="+j.optInt("errcode",-1)+"，"+j.optString("errmsg","请核对AppID、AppSecret及本次code"));
        return j.getString("openid");
    }
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws IOException {handle(req,resp);}
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse resp)throws IOException {handle(req,resp);}
    private void handle(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        req.setCharacterEncoding("UTF-8");resp.setHeader("Cache-Control","no-store");
        resp.setHeader("X-Content-Type-Options","nosniff");
        try {
            String action=req.getParameter("action");JSONObject result;
            if("ping".equals(action)) {
                dao.ping();result=new JSONObject();result.put("module","week08-auth");
                result.put("appid_configured",!appid().isEmpty());result.put("secret_configured",!secret().isEmpty());
            } else {
                if(!"POST".equals(req.getMethod()))throw new AuthError(405,"认证操作请使用POST");
                if(appid().isEmpty())throw new AuthError(503,"Tomcat未配置小程序AppID");
                String token=req.getHeader("X-Week08-Token");
                if("wx_login".equals(action))result=dao.createSession(appid(),exchange(req.getParameter("code")));
                else if("check_session".equals(action))result=dao.check(appid(),token);
                else if("bind_user".equals(action))result=dao.bind(appid(),token,req.getParameter("bind_text"));
                else if("report_location".equals(action)) {
                    JSONObject state=dao.check(appid(),token);
                    if(!state.getBoolean("bound"))throw new AuthError(403,"请先扫码绑定平台用户");
                    JSONObject user=state.getJSONObject("user");
                    // 平台用户id和姓名从已校验凭证中取得，不接受前端指定身份。
                    new LocationDao().report(user.getString("user_id"),user.getString("display_name"),
                            Double.parseDouble(req.getParameter("latitude")),Double.parseDouble(req.getParameter("longitude")),
                            req.getParameter("collected_at"),req.getParameter("source"));
                    result=new JSONObject();result.put("user",user);result.put("saved",true);
                } else throw new AuthError(400,"未知认证操作");
            }
            result.put("result_code",0);result.put("result_msg","ok");write(resp,200,result);
        } catch(AuthError e) {error(resp,e.status,e.getMessage());}
        catch(IllegalArgumentException e) {error(resp,400,"请求参数无效："+e.getMessage());}
        catch(Exception e) {log("week08-auth异常",e);error(resp,500,"认证接口执行失败，请查看Tomcat日志中的week08-auth异常");}
    }
    private void write(HttpServletResponse resp,int status,JSONObject j)throws IOException {
        resp.setStatus(status);resp.setContentType("application/json;charset=UTF-8");resp.getWriter().print(j.toString());
    }
    private void error(HttpServletResponse resp,int status,String msg)throws IOException {
        try {JSONObject j=new JSONObject();j.put("result_code",status);j.put("result_msg",msg);write(resp,status,j);}
        catch(Exception e){throw new IOException(e);}
    }
}
