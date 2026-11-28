package greenhouse.identity;

import device.dao.Db;
import org.json.*;
import user.dao.ConnectionProvider;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** 权限申请仅开放普通用户升为普通管理员；身份和状态在事务内复核。 */
public class PermissionRequestDao {
    private final ConnectionProvider connections;
    public PermissionRequestDao() { this(() -> new Db("greenhouse_system").getConnection()); }
    public PermissionRequestDao(ConnectionProvider connections) { this.connections = Objects.requireNonNull(connections); }
    private static final Set<String> STATES = Set.of("pending", "approved", "rejected", "withdrawn");
    private static final String FROM = " FROM permission_request r JOIN todo_login_user u ON u.id=r.applicant_id LEFT JOIN todo_login_user v ON v.id=r.reviewer_id";
    private static final String FIELDS = "r.id,r.applicant_id,u.username,u.display_name,r.source_role,u.role_id AS applicant_role,u.status AS account_status,r.target_role,r.reason,r.status,r.review_opinion,r.reviewer_id,v.display_name AS reviewer_name,r.created_at,r.reviewed_at,r.withdrawn_at,r.version";

    public static class Query {
        final String scope, keyword, status, start, end, dir;
        final int page, size;
        public Query(String scope, String keyword, String status, String start, String end, String dir, String page, String size) {
            this.scope=scope; this.keyword=keyword.trim(); this.status=status; this.start=start; this.end=end; this.dir=dir;
            if (!Set.of("mine", "review").contains(scope) || this.keyword.length()>100 || (!status.isEmpty() && !STATES.contains(status)) || !Set.of("asc","desc").contains(dir))
                throw new UserProblem(400,"查询条件无效");
            try {
                if (!start.isEmpty()) LocalDate.parse(start);
                if (!end.isEmpty()) LocalDate.parse(end).plusDays(1);
                if (!start.isEmpty() && !end.isEmpty() && start.compareTo(end)>0) throw new IllegalArgumentException();
                this.page=Integer.parseInt(page); this.size=Integer.parseInt(size);
                if (this.page<1 || this.page>1000000 || !Set.of(10,20,50).contains(this.size)) throw new IllegalArgumentException();
            } catch (RuntimeException e) { throw new UserProblem(400,"日期或分页参数无效"); }
        }
        String where(long actorId, List<Object> args) {
            String w=" WHERE 1=1";
            if (scope.equals("mine")) { w+=" AND r.applicant_id=?"; args.add(actorId); }
            if (!status.isEmpty()) { w+=" AND r.status=?"; args.add(status); }
            if (!keyword.isEmpty()) { w+=" AND (INSTR(u.username,?)>0 OR INSTR(u.display_name,?)>0 OR INSTR(r.reason,?)>0)"; args.add(keyword);args.add(keyword);args.add(keyword); }
            if (!start.isEmpty()) { w+=" AND r.created_at>=?"; args.add(Timestamp.valueOf(LocalDate.parse(start).atStartOfDay())); }
            if (!end.isEmpty()) { w+=" AND r.created_at<?"; args.add(Timestamp.valueOf(LocalDate.parse(end).plusDays(1).atStartOfDay())); }
            return w;
        }
        String order() { return " ORDER BY CASE WHEN r.status='pending' THEN 0 ELSE 1 END,r.created_at "+dir+",r.id "+dir; }
    }

    public JSONObject context(long actorId, String auth) throws SQLException, JSONException {
        try (Connection c=connections.open()) {
            JSONObject actor=identity(c,actorId,auth,false);
            return new JSONObject().put("can_apply",actor.getInt("role_id")==3).put("can_review",actor.getInt("role_id")==1)
                .put("current",new JSONObject().put("id",actorId).put("username",actor.getString("username"))
                    .put("display_name",actor.getString("display_name")).put("role_id",actor.getInt("role_id")));
        }
    }
    public JSONObject list(long actorId, String auth, Query q, boolean all) throws SQLException, JSONException {
        try (Connection c=connections.open()) {
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); c.setAutoCommit(false);
            try {
                authorizeScope(identity(c,actorId,auth,false),q.scope);
                List<Object> args=new ArrayList<>(); String where=q.where(actorId,args);
                JSONArray groups=rows(c,"SELECT r.status,COUNT(*) AS count"+FROM+where+" GROUP BY r.status",args);
                long total=0; for(int i=0;i<groups.length();i++)total+=groups.getJSONObject(i).getLong("count");
                int last=(int)Math.max(1,(total+q.size-1)/q.size), page=Math.min(q.page,last);
                List<Object> paged=new ArrayList<>(args);
                String limit=""; if(!all){limit=" LIMIT ? OFFSET ?";paged.add(q.size);paged.add((page-1)*q.size);}
                JSONArray items=rows(c,"SELECT "+FIELDS+FROM+where+q.order()+limit,paged);
                c.commit(); return new JSONObject().put("items",items).put("total",total).put("groups",groups).put("page",page).put("size",q.size);
            } catch (SQLException|JSONException|RuntimeException e) { c.rollback(); throw e; }
        }
    }
    public JSONObject detail(long actorId,String auth,String scope,long id) throws SQLException,JSONException {
        try(Connection c=connections.open()) {
            authorizeScope(identity(c,actorId,auth,false),scope);
            JSONArray found=rows(c,"SELECT "+FIELDS+FROM+" WHERE r.id=?"+(scope.equals("mine")?" AND r.applicant_id=?":""),scope.equals("mine")?List.of(id,actorId):List.of(id));
            if(found.length()==0)throw new UserProblem(404,"申请不存在或不属于当前账号");
            return found.getJSONObject(0);
        }
    }
    public JSONObject dashboard(long actorId,String auth) throws SQLException,JSONException {
        try(Connection c=connections.open()) {
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setAutoCommit(false);
            try {
                JSONObject actor=identity(c,actorId,auth,false);
                boolean admin=actor.getInt("role_id")==1;
                JSONArray requests=rows(c,"SELECT status,COUNT(*) AS count FROM permission_request"+(admin?"":" WHERE applicant_id=?")+" GROUP BY status",admin?List.of():List.of(actorId));
                JSONArray users=admin?rows(c,"SELECT role_id,status,COUNT(*) AS count FROM todo_login_user GROUP BY role_id,status",List.of()):new JSONArray();
                JSONObject profile=new JSONObject().put("username",actor.getString("username")).put("display_name",actor.getString("display_name")).put("role_id",actor.getInt("role_id"));
                c.commit();return new JSONObject().put("current",profile).put("global",admin).put("requests",requests).put("users",users).put("active_users_available",false);
            }catch(SQLException|JSONException|RuntimeException e){c.rollback();throw e;}
        }
    }

    public JSONObject mutate(long actorId,String auth,String action,Map<String,String> values) throws SQLException,JSONException {
        if(!Set.of("create","approve","reject","withdraw").contains(action))throw new UserProblem(400,"未知申请操作");
        String reason=values.getOrDefault("reason","").trim(),opinion=values.getOrDefault("review_opinion","").trim();
        if(action.equals("create") && (reason.isEmpty() || reason.length()>500))throw new UserProblem(400,"申请理由须为1至500字符");
        if(action.equals("create") && !values.getOrDefault("target_role","").equals("2"))throw new UserProblem(400,"仅可申请普通管理员");
        if(opinion.length()>500 || (action.equals("reject") && opinion.isEmpty()))throw new UserProblem(400,"驳回须填写1至500字符意见；批准意见不超过500字符");
        long id=action.equals("create")?0:UserManagementDao.positive(values.getOrDefault("id","0"));
        long version=action.equals("create")?0:version(values.getOrDefault("version",""));
        try(Connection c=connections.open()) {
            c.setAutoCommit(false);
            try {
                // 与用户管理保持一致的加锁顺序；串行化管理员变更及审批，防止旧身份写入。
                rows(c,"SELECT id FROM todo_login_user WHERE role_id=1 AND status='active' ORDER BY id FOR UPDATE",List.of());
                JSONObject actor=identity(c,actorId,auth,true);
                if(action.equals("create")) {
                    if(actor.getInt("role_id")!=3)throw new UserProblem(403,"仅普通用户可以申请普通管理员");
                    if(rows(c,"SELECT id FROM permission_request WHERE applicant_id=? AND status='pending'",List.of(actorId)).length()>0)
                        throw new UserProblem(409,"已有待审批申请，请勿重复提交");
                    try(PreparedStatement s=c.prepareStatement("INSERT INTO permission_request(applicant_id,target_role,reason,status) VALUES(?,2,?,'pending')",Statement.RETURN_GENERATED_KEYS)) {
                        s.setLong(1,actorId);s.setString(2,reason);s.executeUpdate();
                        try(ResultSet rs=s.getGeneratedKeys()){if(!rs.next())throw new SQLException("No generated request id");id=rs.getLong(1);}
                    }
                } else {
                    if(!action.equals("withdraw") && actor.getInt("role_id")!=1)throw new UserProblem(403,"仅系统管理员可以审批");
                    JSONArray initial=rows(c,"SELECT applicant_id FROM permission_request WHERE id=?",List.of(id));
                    if(initial.length()==0)throw new UserProblem(404,"申请不存在");
                    long applicantId=initial.getJSONObject(0).getLong("applicant_id");
                    if(action.equals("withdraw") && applicantId!=actorId)throw new UserProblem(404,"申请不存在或不属于当前账号");
                    JSONObject applicant=account(c,applicantId,true);
                    JSONObject request=rows(c,"SELECT status,version,target_role FROM permission_request WHERE id=? FOR UPDATE",List.of(id)).getJSONObject(0);
                    if(!request.getString("status").equals("pending") || request.getLong("version")!=version)throw new UserProblem(409,"申请状态已变化，请刷新列表");
                    if(!action.equals("withdraw") && applicantId==actorId)throw new UserProblem(403,"不能审批自己的申请");
                    if(action.equals("approve")) {
                        if(!applicant.getString("status").equals("active") || applicant.getInt("role_id")!=3 || request.getInt("target_role")!=2)
                            throw new UserProblem(409,"申请人当前须为启用的普通用户；请核实后驳回或重新申请");
                        execute(c,"UPDATE todo_login_user SET role_id=2,version=version+1,auth_version=auth_version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",List.of(applicantId));
                    }
                    if(action.equals("withdraw"))execute(c,"UPDATE permission_request SET status='withdrawn',withdrawn_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",List.of(id));
                    else execute(c,"UPDATE permission_request SET status=?,review_opinion=?,reviewer_id=?,reviewed_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",List.of(action.equals("approve")?"approved":"rejected",opinion,actorId,id));
                }
                c.commit();return new JSONObject().put("id",id);
            } catch(SQLException|JSONException|RuntimeException e) {c.rollback();throw e;}
        }
    }
    private static long version(String input) {
        try {long v=Long.parseLong(input);if(v<0)throw new NumberFormatException();return v;}
        catch(NumberFormatException e){throw new UserProblem(400,"申请版本无效，请刷新页面");}
    }
    private static void authorizeScope(JSONObject actor,String scope)throws JSONException {
        if(!Set.of("mine","review").contains(scope))throw new UserProblem(400,"申请范围无效");
        if(scope.equals("review") && actor.getInt("role_id")!=1)throw new UserProblem(403,"仅系统管理员可查看审批列表");
    }
    private static JSONObject identity(Connection c,long id,String auth,boolean lock)throws SQLException,JSONException {
        JSONObject a=account(c,id,lock);
        if(!a.getString("status").equals("active") || !Long.toString(a.getLong("auth_version")).equals(auth))throw new UserProblem(401,"账号或权限已变化，请重新登录");
        return a;
    }
    private static JSONObject account(Connection c,long id,boolean lock)throws SQLException,JSONException {
        JSONArray rows=rows(c,"SELECT id,username,display_name,role_id,status,auth_version FROM todo_login_user WHERE id=?"+(lock?" FOR UPDATE":""),List.of(id));
        if(rows.length()==0)throw new UserProblem(401,"账号不存在，请重新登录");return rows.getJSONObject(0);
    }
    private static void execute(Connection c,String sql,List<?> args)throws SQLException {
        try(PreparedStatement s=c.prepareStatement(sql)){bind(s,args);s.executeUpdate();}
    }
    private static void bind(PreparedStatement s,List<?> args)throws SQLException {for(int i=0;i<args.size();i++)s.setObject(i+1,args.get(i));}
    private static JSONArray rows(Connection c,String sql,List<?> args)throws SQLException,JSONException {
        try(PreparedStatement s=c.prepareStatement(sql)) {
            bind(s,args);try(ResultSet rs=s.executeQuery()) {
                JSONArray result=new JSONArray();ResultSetMetaData meta=rs.getMetaData();
                while(rs.next()){JSONObject row=new JSONObject();for(int i=1;i<=meta.getColumnCount();i++){Object v=rs.getObject(i);row.put(meta.getColumnLabel(i).toLowerCase(Locale.ROOT),v==null?JSONObject.NULL:v instanceof Timestamp?v.toString():v);}result.put(row);}return result;
            }
        }
    }
}
