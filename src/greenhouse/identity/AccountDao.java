package greenhouse.identity;

import device.dao.Db;
import org.json.*;
import user.dao.ConnectionProvider;
import user.security.PasswordUtil;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** 仅维护会话确定的本人资料，不接受客户端指定的目标账号。 */
public class AccountDao {
    private final ConnectionProvider connections;
    public AccountDao() { this(() -> new Db("greenhouse_system").getConnection()); }
    public AccountDao(ConnectionProvider connections) { this.connections = connections; }

    public JSONObject profile(long id) throws SQLException, JSONException {
        try (Connection c = connections.open(); PreparedStatement s = c.prepareStatement(
                "SELECT id,username,display_name,role_id,status,created_at,updated_at,version " +
                "FROM todo_login_user WHERE id=? AND status='active' AND role_id IN (1,2,3)")) {
            s.setLong(1,id);
            try (ResultSet r=s.executeQuery()) {
                if(!r.next()) throw new UserProblem(401,"账号已失效，请重新登录");
                JSONObject data=new JSONObject();
                for(String key:new String[]{"id","username","display_name","role_id","status","created_at","updated_at","version"})
                    data.put(key,r.getObject(key)==null?JSONObject.NULL:r.getString(key));
                return data;
            }
        }
    }

    public void update(long id,String auth,String version,String action,String name,
                       String oldPassword,String newPassword,String confirmation) throws SQLException {
        long expected;
        try { expected=Long.parseLong(version); }
        catch(NumberFormatException e) { throw new UserProblem(400,"资料版本无效，请刷新后重试"); }
        if(expected<0)throw new UserProblem(400,"资料版本无效，请刷新后重试");
        if(action.equals("profile")) {
            if(name==null || name.trim().isEmpty() || name.trim().length()>100)
                throw new UserProblem(400,"显示名称须为1至100字符");
            name=name.trim();
        } else if(action.equals("password")) {
            if(newPassword==null || newPassword.length()<8 || newPassword.getBytes(StandardCharsets.UTF_8).length>72)
                throw new UserProblem(400,"新密码至少8字符，UTF-8编码不超过72字节");
            if(!newPassword.equals(confirmation)) throw new UserProblem(400,"两次新密码不一致");
            if(newPassword.equals(oldPassword)) throw new UserProblem(400,"新密码不能与原密码相同");
        } else throw new UserProblem(400,"未知个人资料操作");
        try(Connection c=connections.open()) {
            c.setAutoCommit(false);
            try {
                String hash;
                try(PreparedStatement s=c.prepareStatement("SELECT password,role_id,status,auth_version,version FROM todo_login_user WHERE id=? FOR UPDATE")) {
                    s.setLong(1,id);
                    try(ResultSet r=s.executeQuery()) {
                        if(!r.next() || !"active".equals(r.getString("status")) || !Set.of(1,2,3).contains(r.getInt("role_id"))
                                || !String.valueOf(r.getLong("auth_version")).equals(auth))
                            throw new UserProblem(401,"账号或权限已变化，请重新登录");
                        if(r.getLong("version")!=expected) throw new UserProblem(409,"资料已变化，请刷新后重试");
                        hash=r.getString("password");
                    }
                }
                if(action.equals("password") && !PasswordUtil.verify(oldPassword,hash))
                    throw new UserProblem(400,"原密码不正确");
                String sql=action.equals("profile")
                    ? "UPDATE todo_login_user SET display_name=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?"
                    : "UPDATE todo_login_user SET password=?,version=version+1,auth_version=auth_version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?";
                try(PreparedStatement s=c.prepareStatement(sql)) {
                    s.setString(1,action.equals("profile")?name:PasswordUtil.hash(newPassword));
                    s.setLong(2,id);s.executeUpdate();
                }
                c.commit();
            } catch(SQLException|RuntimeException e) { c.rollback();throw e; }
        }
    }
}
