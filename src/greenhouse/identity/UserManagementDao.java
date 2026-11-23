package greenhouse.identity;

import device.dao.Db;
import user.security.PasswordUtil;
import org.json.*;
import java.sql.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

public class UserManagementDao {
    private final user.dao.ConnectionProvider connections;
    public UserManagementDao(){this(() -> new Db("greenhouse_system").getConnection());}
    public UserManagementDao(user.dao.ConnectionProvider connections){this.connections=java.util.Objects.requireNonNull(connections);}

    private static final String FIELDS = "id,username,display_name,role_id,status,created_at,updated_at,version";

    public static class Query {
        public final String keyword, role, status, sort, dir;
        public final int page, size;
        public Query(String keyword, String role, String status, String sort, String dir, String page, String size) {
            this.keyword=keyword.trim(); this.role=role; this.status=status; this.sort=sort; this.dir=dir;
            if(this.keyword.length()>100 || (!role.isEmpty() && !Set.of("1","2","3").contains(role))
                    || (!status.isEmpty() && !Set.of("active","disabled","deleted").contains(status)))
                throw new UserProblem(400,"查询条件无效");
            if(!Set.of("username","created_at","updated_at","role_id","status").contains(sort)
                    || !Set.of("asc","desc").contains(dir)) throw new UserProblem(400,"排序条件无效");
            try { this.page=Integer.parseInt(page); this.size=Integer.parseInt(size); }
            catch(NumberFormatException e){throw new UserProblem(400,"分页参数无效");}
            if(this.page<1 || this.page>1000000 || this.size<1 || this.size>100) throw new UserProblem(400,"分页范围无效");
        }
        String where(List<Object> args) {
            String sql=" WHERE 1=1";
            if(status.isEmpty()) sql+=" AND status<>'deleted'";
            else {sql+=" AND status=?";args.add(status);}
            if(!role.isEmpty()){sql+=" AND role_id=?";args.add(Integer.parseInt(role));}
            if(!keyword.isEmpty()){sql+=" AND (INSTR(username,?)>0 OR INSTR(display_name,?)>0)";args.add(keyword);args.add(keyword);}
            return sql;
        }
        String order(){return " ORDER BY "+sort+" "+dir+",id "+dir;}
    }

    public JSONObject list(Query q) throws SQLException,JSONException {
        try(Connection c=open()) {
            List<Object> args=new ArrayList<>(); String where=q.where(args);
            long total=rows(c,"SELECT COUNT(*) total FROM todo_login_user"+where,args).getJSONObject(0).getLong("total");
            args.add(q.size); args.add((q.page-1)*q.size);
            return new JSONObject().put("total",total).put("items",rows(c,"SELECT "+FIELDS+" FROM todo_login_user"+where+q.order()+" LIMIT ? OFFSET ?",args));
        }
    }
    public JSONArray all(Query q) throws SQLException,JSONException {
        try(Connection c=open()) {List<Object> args=new ArrayList<>();String where=q.where(args);
            return rows(c,"SELECT "+FIELDS+" FROM todo_login_user"+where+q.order(),args);}
    }
    public JSONObject detail(long id) throws SQLException,JSONException {
        try(Connection c=open()) {
            JSONArray r=rows(c,"SELECT "+FIELDS+" FROM todo_login_user WHERE id=?",List.of(id));
            if(r.length()==0) throw new UserProblem(404,"用户不存在");
            return r.getJSONObject(0);
        }
    }
    public JSONArray options() throws SQLException,JSONException {
        try(Connection c=open()) {return rows(c,"SELECT id,display_name FROM todo_login_user WHERE status='active' AND role_id IN (1,2,3) ORDER BY display_name,id",List.of());}
    }
    public JSONObject stats(Query q) throws SQLException,JSONException {
        // 单条分组查询形成一致的统计快照，再计算角色和状态合计。
        try(Connection c=open()) {
            List<Object> args=new ArrayList<>();String where=q.where(args);
            JSONArray groups=rows(c,"SELECT role_id,status,COUNT(*) count FROM todo_login_user"+where+" GROUP BY role_id,status ORDER BY role_id,status",args);
            long total=0;for(int i=0;i<groups.length();i++)total+=groups.getJSONObject(i).getLong("count");
            return new JSONObject().put("total",total).put("groups",groups);
        }
    }

    public JSONObject mutate(long actorId, String actorVersion, String action, Map<String,String> input) throws SQLException,JSONException {
        if(!Set.of("create","update","set_status","delete","reset_password").contains(action))throw new UserProblem(400,"未知维护动作");
        // 密码验证及BCrypt计算在取得事务行锁之前完成。
        String hash=null;
        if(action.equals("create") || action.equals("reset_password")) hash=passwordHash(input.getOrDefault("password",""));
        String username="",name="",status="";int role=0;
        if(action.equals("create") || action.equals("update")) {
            name=input.getOrDefault("display_name","").trim();
            if(name.isEmpty() || name.length()>100)throw new UserProblem(400,"显示名称必填且不超过100字符");
            try {role=Integer.parseInt(input.getOrDefault("role_id","0"));}catch(NumberFormatException e){throw new UserProblem(400,"角色无效");}
            if(role<1 || role>3)throw new UserProblem(400,"角色无效");
            status=input.getOrDefault("status","active");
            if(!Set.of("active","disabled").contains(status))throw new UserProblem(400,"账号状态无效");
        }
        if(action.equals("create")) {
            username=input.getOrDefault("username","").trim();
            if(!username.matches("[A-Za-z0-9_.@-]{3,64}")) throw new UserProblem(400,"账号须为3至64位字母、数字或_.@-");
        }
        if(action.equals("set_status")) {
            status=input.getOrDefault("status","");
            if(!Set.of("active","disabled").contains(status))throw new UserProblem(400,"账号状态无效");
        }
        try(Connection c=open()) {
            c.setAutoCommit(false);
            try {
                // 管理员行按固定顺序加锁；避免并发操作破坏有效管理员约束。
                JSONArray admins=rows(c,"SELECT id FROM todo_login_user WHERE role_id=1 AND status='active' ORDER BY id FOR UPDATE",List.of());
                JSONArray actorRows=rows(c,"SELECT role_id,status,auth_version FROM todo_login_user WHERE id=? FOR UPDATE",List.of(actorId));
                if(actorRows.length()==0)throw new UserProblem(401,"登录已失效，请重新登录");
                JSONObject actor=actorRows.getJSONObject(0);
                if(!actor.getString("status").equals("active") || !String.valueOf(actor.getLong("auth_version")).equals(actorVersion))
                    throw new UserProblem(401,"账号或权限已变化，请重新登录");
                if(actor.getInt("role_id")!=1)throw new UserProblem(403,"仅系统管理员可维护用户");
                long resultId;
                if(action.equals("create")) {
                    try(PreparedStatement s=c.prepareStatement("INSERT INTO todo_login_user(username,password,display_name,role_id,status) VALUES(?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
                        bind(s,List.of(username,hash,name,role,status));s.executeUpdate();
                        try(ResultSet keys=s.getGeneratedKeys()){if(!keys.next())throw new SQLException("Missing generated user ID");resultId=keys.getLong(1);}
                    }
                } else {
                    resultId=positive(input.getOrDefault("id","0"));
                    long version=nonnegative(input.getOrDefault("version",""));
                    JSONArray targetRows=rows(c,"SELECT role_id,status,version,auth_version FROM todo_login_user WHERE id=? FOR UPDATE",List.of(resultId));
                    if(targetRows.length()==0)throw new UserProblem(404,"用户不存在");
                    JSONObject target=targetRows.getJSONObject(0);
                    if(target.getString("status").equals("deleted"))throw new UserProblem(409,"已删除账号不可维护");
                    if(target.getLong("version")!=version)throw new UserProblem(409,"记录已被修改，请刷新后重试");
                    int oldRole=target.getInt("role_id");String oldStatus=target.getString("status");
                    if(action.equals("delete"))status="deleted";
                    if(action.equals("set_status") || action.equals("delete"))role=oldRole;
                    if(action.equals("reset_password")){role=oldRole;status=oldStatus;}
                    if(actorId==resultId && (role!=oldRole || !status.equals("active")))
                        throw new UserProblem(400,"不能更改自己的角色、停用或删除自己");
                    if(oldRole==1 && oldStatus.equals("active") && (role!=1 || !status.equals("active")) && admins.length()<=1)
                        throw new UserProblem(409,"必须保留至少一个有效系统管理员");
                    boolean invalidate=role!=oldRole || !status.equals(oldStatus) || action.equals("reset_password");
                    String sql="UPDATE todo_login_user SET role_id=?,status=?,version=version+1,auth_version=auth_version+?";
                    List<Object> values=new ArrayList<>(List.of(role,status,invalidate?1:0));
                    if(action.equals("update")){sql+=",display_name=?";values.add(name);}
                    if(action.equals("reset_password")){sql+=",password=?";values.add(hash);}
                    sql+=" WHERE id=? AND version=?";values.add(resultId);values.add(version);
                    try(PreparedStatement s=c.prepareStatement(sql)){bind(s,values);if(s.executeUpdate()!=1)throw new UserProblem(409,"记录发生变化，请刷新");}
                }
                c.commit();return new JSONObject().put("id",resultId);
            } catch(SQLException|JSONException|RuntimeException e) {c.rollback();throw e;}
            finally {c.setAutoCommit(true);}
        }
    }
    public static long positive(String text){long n=nonnegative(text);if(n==0)throw new UserProblem(400,"记录编号必须为正整数");return n;}
    private static long nonnegative(String text){try{long n=Long.parseLong(text);if(n>=0)return n;}catch(NumberFormatException ignored){}throw new UserProblem(400,"编号或版本无效");}
    private static String passwordHash(String p){
        if(p.length()<8 || p.getBytes(StandardCharsets.UTF_8).length>72)throw new UserProblem(400,"密码至少8字符，UTF-8不超过72字节");
        return PasswordUtil.hash(p);
    }
    private Connection open() throws SQLException{return connections.open();}
    private static void bind(PreparedStatement s,List<?> values)throws SQLException{for(int i=0;i<values.size();i++)s.setObject(i+1,values.get(i));}
    private static JSONArray rows(Connection c,String sql,List<?> values)throws SQLException,JSONException {
        try(PreparedStatement s=c.prepareStatement(sql)){bind(s,values);try(ResultSet r=s.executeQuery()){
            JSONArray rows=new JSONArray();ResultSetMetaData meta=r.getMetaData();
            while(r.next()){JSONObject row=new JSONObject();for(int i=1;i<=meta.getColumnCount();i++){
                Object value=r.getObject(i);if(value instanceof Timestamp)value=value.toString();
                row.put(meta.getColumnLabel(i),value==null?JSONObject.NULL:value);
            }rows.put(row);}return rows;
        }}
    }
}
