package week08.bind;

import device.dao.Db;
import org.json.JSONObject;
import java.sql.*;
import java.security.SecureRandom;
import java.util.HexFormat;

public class AuthDao {
    public static class AuthError extends RuntimeException {
        public final int status;
        public AuthError(int status, String msg) { super(msg); this.status=status; }
    }
    private Connection open() throws SQLException { return new Db("greenhouse_system").getConnection(); }
    public void ping() throws Exception {
        try(Connection c=open()) {
            for(String table:new String[]{"week08_user_bind","week08_login_session","week08_bind_ticket"}) {
                try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM "+table+" LIMIT 1");ResultSet r=p.executeQuery()) {}
            }
        }
    }
    private String identity(Connection c,String appid,String token,boolean lock) throws Exception {
        if(token==null || !token.matches("[a-f0-9]{64}")) throw new AuthError(401,"登录凭证无效，请重新登录");
        try(PreparedStatement p=c.prepareStatement("SELECT openid FROM week08_login_session "
                +"WHERE token_hash=? AND appid=? AND expires_at>NOW()"+(lock?" FOR UPDATE":""))) {
            p.setString(1,BindDao.hash(token));p.setString(2,appid);
            try(ResultSet r=p.executeQuery()) {
                if(!r.next()) throw new AuthError(401,"登录凭证已过期，请重新登录");
                return r.getString("openid");
            }
        }
    }
    private JSONObject user(Connection c,String appid,String openid) throws Exception {
        try(PreparedStatement p=c.prepareStatement("SELECT b.user_id,u.id,u.username,u.display_name "
                +"FROM week08_user_bind b LEFT JOIN todo_login_user u ON u.id=b.user_id "
                +"WHERE b.appid=? AND b.openid=?")) {
            p.setString(1,appid);p.setString(2,openid);
            try(ResultSet r=p.executeQuery()) {
                if(!r.next()) return null;
                if(r.getString("id")==null) throw new AuthError(409,"已绑定的平台用户不存在，请联系管理员");
                JSONObject j=new JSONObject();j.put("user_id",r.getString("user_id"));
                j.put("username",r.getString("username"));j.put("display_name",r.getString("display_name"));return j;
            }
        }
    }
    private JSONObject state(Connection c,String appid,String openid) throws Exception {
        JSONObject u=user(c,appid,openid),j=new JSONObject();
        j.put("bound",u!=null); if(u!=null) j.put("user",u);return j;
    }
    public JSONObject createSession(String appid,String openid) throws Exception {
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
        String token=HexFormat.of().formatHex(bytes);
        try(Connection c=open();PreparedStatement p=c.prepareStatement("INSERT INTO week08_login_session "
                +"(token_hash,appid,openid,identity_source,expires_at) VALUES(?,?,?,'wechat',DATE_ADD(NOW(),INTERVAL 2 HOUR))")) {
            p.setString(1,BindDao.hash(token));p.setString(2,appid);p.setString(3,openid);p.executeUpdate();
            JSONObject j=state(c,appid,openid);j.put("token",token);j.put("identity_source","wechat");return j;
        }
    }
    public JSONObject check(String appid,String token) throws Exception {
        try(Connection c=open()) {return state(c,appid,identity(c,appid,token,false));}
    }
    public JSONObject bind(String appid,String token,String text) throws Exception {
        if(text==null || !text.matches("BIND:[1-9][0-9]{0,18}:[a-f0-9]{64}"))
            throw new AuthError(400,"请扫描本平台生成的身份二维码");
        String[] parts=text.split(":");String userId=parts[1],ticketHash=BindDao.hash(parts[2]);
        try(Connection c=open()) {
            c.setAutoCommit(false);
            try {
                String openid=identity(c,appid,token,true);
                // 锁住二维码记录，使并发扫描不能重复消费同一绑定码。
                long ticketId;boolean valid;
                try(PreparedStatement p=c.prepareStatement("SELECT id,(used_at IS NULL AND expires_at>NOW()) AS valid "
                        +"FROM week08_bind_ticket WHERE ticket_hash=? AND user_id=? FOR UPDATE")) {
                    p.setString(1,ticketHash);p.setString(2,userId);
                    try(ResultSet r=p.executeQuery()) {
                        if(!r.next()) throw new AuthError(400,"绑定码不存在，请重新生成二维码");
                        ticketId=r.getLong("id");valid=r.getBoolean("valid");
                    }
                }
                JSONObject existing=user(c,appid,openid);
                if(existing!=null) {
                    if(!userId.equals(existing.getString("user_id"))) throw new AuthError(409,"当前微信已绑定其他平台用户，不能直接覆盖");
                    JSONObject j=state(c,appid,openid);j.put("already_bound",true);c.commit();return j;
                }
                if(!valid) throw new AuthError(410,"绑定码已过期或已使用，请在平台重新生成");
                try(PreparedStatement p=c.prepareStatement("SELECT id FROM todo_login_user WHERE id=?")) {
                    p.setString(1,userId);try(ResultSet r=p.executeQuery()) {
                        if(!r.next()) throw new AuthError(404,"平台用户不存在");
                    }
                }
                try(PreparedStatement p=c.prepareStatement("INSERT INTO week08_user_bind(appid,openid,user_id,identity_source) VALUES(?,?,?,'wechat')")) {
                    p.setString(1,appid);p.setString(2,openid);p.setString(3,userId);p.executeUpdate();
                }
                try(PreparedStatement p=c.prepareStatement("UPDATE week08_bind_ticket SET used_at=NOW() WHERE id=?")) {
                    p.setLong(1,ticketId);p.executeUpdate();
                }
                JSONObject j=state(c,appid,openid);j.put("already_bound",false);c.commit();return j;
            } catch(Exception e) {c.rollback();throw e;}
        }
    }
}
