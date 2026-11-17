package greenhouse.greenhouse;

import device.dao.Db;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.WebServlet;
import org.json.*;
import java.io.*;
import java.sql.*;
import java.math.BigDecimal;
import java.util.*;

@WebServlet("/api/greenhouses")
public class GreenhouseServlet extends HttpServlet {
 private static final Set<String> WRITES=Set.of("create","update","delete");
 protected void doGet(HttpServletRequest q,HttpServletResponse p)throws IOException {run(q,p);}
 protected void doPost(HttpServletRequest q,HttpServletResponse p)throws IOException {run(q,p);}
 private void run(HttpServletRequest q,HttpServletResponse p)throws IOException {
  q.setCharacterEncoding("UTF-8");p.setContentType("application/json;charset=UTF-8");p.setHeader("Cache-Control","no-store");
  var s=q.getSession(false);Object u=s==null?null:s.getAttribute("loginUser");
  if(!(u instanceof Map)){reply(p,401,"请先登录",null);return;}
  String role=String.valueOf(((Map<?,?>)u).get("roleId"));
  if(!Set.of("1","2","3").contains(role)){reply(p,403,"角色无效",null);return;}
  String a=param(q,"action","list");
  if(WRITES.contains(a)&&"3".equals(role)){reply(p,403,"普通用户只读",null);return;}
  if(WRITES.contains(a)&&!"POST".equals(q.getMethod())){reply(p,405,"维护使用POST",null);return;}
  try(Connection c=new Db("greenhouse_system").getConnection()) {
   if(a.equals("context")){reply(p,200,"成功",new JSONObject().put("role",role).put("users",rows(c,"SELECT id,display_name FROM todo_login_user ORDER BY id",List.of())));return;}
   if(WRITES.contains(a)){write(c,q,a);reply(p,200,"保存成功",null);return;}
   if(a.equals("detail")){JSONArray r=rows(c,select()+" WHERE g.is_deleted=0 AND g.id=?",List.of(id(q)));if(r.length()==0){reply(p,404,"记录不存在",null);return;}reply(p,200,"成功",r.get(0));return;}
   List<Object> args=new ArrayList<>();String where=" WHERE g.is_deleted=0";
   for(String k:List.of("keyword","location","manager_id","status")) {
    String v=param(q,k,"");if(v.isEmpty())continue;
    if(k.equals("keyword")){where+=" AND (g.name LIKE ? OR g.greenhouse_code LIKE ?)";args.add("%"+v+"%");args.add("%"+v+"%");}
    else if(k.equals("location")){where+=" AND g.location LIKE ?";args.add("%"+v+"%");}
    else {where+=" AND g."+k+"=?";args.add(v);}
   }
   String sort=param(q,"sort","created_at"),dir=param(q,"dir","desc");
   if(!Set.of("greenhouse_code","area","created_at").contains(sort)||!Set.of("asc","desc").contains(dir))throw new IllegalArgumentException("排序参数无效");
   String order=" ORDER BY g."+sort+" "+dir+",g.id "+dir;
   if(a.equals("stats")){reply(p,200,"成功",rows(c,"SELECT COUNT(*) total,COALESCE(SUM(g.area),0) area,COALESCE(SUM(g.status='enabled'),0) enabled,COALESCE(SUM(g.status='disabled'),0) disabled FROM greenhouse_file g"+where,args).get(0));return;}
   if(a.equals("export")){JSONArray data=rows(c,select()+where+order,args);p.setContentType("text/csv;charset=UTF-8");p.setHeader("Content-Disposition","attachment; filename=greenhouses.csv");var w=p.getWriter();w.write('\ufeff');String[] keys={"greenhouse_code","name","location","area","length","width","manager_name","status","remark","created_at","updated_at"};w.println("编号,名称,位置,面积(平方米),长度(米),宽度(米),负责人,状态,备注,创建时间,更新时间");for(int i=0;i<data.length();i++){JSONObject r=data.getJSONObject(i);for(int j=0;j<keys.length;j++){if(j>0)w.print(',');w.print(csv(r.optString(keys[j],"")));}w.print("\r\n");}return;}
   if(!a.equals("list"))throw new IllegalArgumentException("未知操作");
   int page=Integer.parseInt(param(q,"page","1")),size=Integer.parseInt(param(q,"size","10"));if(page<1||page>1000000||size<1||size>100)throw new IllegalArgumentException("分页参数无效");
   Object total=rows(c,"SELECT COUNT(*) total FROM greenhouse_file g"+where,args).getJSONObject(0).get("total");
   args.add(size);args.add((page-1)*size);
   reply(p,200,"成功",new JSONObject().put("total",total).put("items",rows(c,select()+where+order+" LIMIT ? OFFSET ?",args)));
  }catch(JSONException e){reply(p,500,"响应处理失败",null);}catch(IllegalArgumentException e){reply(p,400,e.getMessage(),null);}catch(SQLIntegrityConstraintViolationException e){reply(p,409,"编号重复或关联冲突",null);}catch(SQLException e){getServletContext().log("greenhouses",e);reply(p,500,"数据库操作失败，请检查迁移与配置",null);}
 }
 private void write(Connection c,HttpServletRequest q,String a)throws SQLException,JSONException {
  if(a.equals("delete")){
   // 动态扫描全库约定的greenhouse_id关联列，含未来采样/批次/阈值/任务表；有历史关联即保留档案。
   c.setAutoCommit(false);
   try {
    if(rows(c,"SELECT id FROM greenhouse_file WHERE id=? AND is_deleted=0 FOR UPDATE",List.of(id(q))).length()==0)throw new IllegalArgumentException("记录不存在");
    JSONArray tables=rows(c,"SELECT TABLE_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND COLUMN_NAME='greenhouse_id'",List.of());
    for(int i=0;i<tables.length();i++){String t=tables.getJSONObject(i).getString("TABLE_NAME");if(!t.matches("[A-Za-z0-9_]+"))throw new SQLException("invalid table");if(rows(c,"SELECT 1 linked FROM `"+t+"` WHERE greenhouse_id=? LIMIT 1",List.of(id(q))).length()>0)throw new IllegalArgumentException("存在业务关联，请使用停用");}
    execute(c,"UPDATE greenhouse_file SET is_deleted=1 WHERE id=?",List.of(id(q)));c.commit();
   }catch(SQLException|JSONException|RuntimeException e){c.rollback();throw e;}finally{c.setAutoCommit(true);}return;
  }
  List<Object> v=new ArrayList<>();for(String k:List.of("greenhouse_code","name","location")){String x=param(q,k,"");int max=k.equals("greenhouse_code")?64:k.equals("name")?100:255;if(x.isEmpty()||x.length()>max)throw new IllegalArgumentException(k+"必填且不能超长");v.add(x);}
  for(String k:List.of("area","length","width")){BigDecimal n=new BigDecimal(param(q,k,""));if(n.signum()<=0||n.scale()>2||n.compareTo(new BigDecimal("9999999999.99"))>0)throw new IllegalArgumentException("面积和尺寸需为正数，最多两位小数");v.add(n);}
  long manager=Long.parseLong(param(q,"manager_id","0"));
  String valid="SELECT id FROM todo_login_user WHERE id=?";
  JSONArray cols=rows(c,"SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='todo_login_user'",List.of());
  Set<String> names=new HashSet<>();for(int i=0;i<cols.length();i++)names.add(cols.getJSONObject(i).getString("COLUMN_NAME"));
  if(names.contains("is_deleted"))valid+=" AND is_deleted=0";
  if(names.contains("status"))valid+=" AND status='enabled'";
  if(rows(c,valid,List.of(manager)).length()==0)throw new IllegalArgumentException("负责人不存在或无效");v.add(manager);
  String status=param(q,"status","");if(!Set.of("enabled","disabled").contains(status))throw new IllegalArgumentException("状态无效");v.add(status);
  String remark=param(q,"remark","");if(remark.length()>1000)throw new IllegalArgumentException("备注过长");v.add(remark);
  if(a.equals("create"))execute(c,"INSERT INTO greenhouse_file(greenhouse_code,name,location,area,length,width,manager_id,status,remark) VALUES(?,?,?,?,?,?,?,?,?)",v);
  else{v.add(id(q));if(execute(c,"UPDATE greenhouse_file SET greenhouse_code=?,name=?,location=?,area=?,length=?,width=?,manager_id=?,status=?,remark=? WHERE id=? AND is_deleted=0",v)==0)throw new IllegalArgumentException("记录不存在或未变化");}
 }
 static String select(){return "SELECT g.*,u.display_name manager_name FROM greenhouse_file g JOIN todo_login_user u ON u.id=g.manager_id";}
 static String param(HttpServletRequest q,String k,String d){String v=q.getParameter(k);return v==null?d:v.trim();}
 static long id(HttpServletRequest q){long n=Long.parseLong(param(q,"id","0"));if(n<=0)throw new IllegalArgumentException("ID无效");return n;}
 static JSONArray rows(Connection c,String sql,List<Object> args)throws SQLException,JSONException{try(PreparedStatement s=c.prepareStatement(sql)){bind(s,args);try(ResultSet r=s.executeQuery()){JSONArray a=new JSONArray();while(r.next()){JSONObject o=new JSONObject();for(int i=1;i<=r.getMetaData().getColumnCount();i++){Object v=r.getObject(i);o.put(r.getMetaData().getColumnLabel(i),v==null?JSONObject.NULL:v instanceof Timestamp?v.toString():v);}a.put(o);}return a;}}}
 static int execute(Connection c,String sql,List<Object> args)throws SQLException,JSONException{try(PreparedStatement s=c.prepareStatement(sql)){bind(s,args);return s.executeUpdate();}}
 static void bind(PreparedStatement s,List<Object> a)throws SQLException,JSONException{for(int i=0;i<a.size();i++)s.setObject(i+1,a.get(i));}
 static String csv(String s){if(s.matches("(?s)^\\s*[=+@-].*"))s="'"+s;return "\""+s.replace("\"","\"\"")+"\"";}
 static void reply(HttpServletResponse p,int code,String msg,Object data)throws IOException{try{p.setStatus(code);p.getWriter().write(new JSONObject().put("result_code",code==200?0:code).put("result_msg",msg).put("data",data==null?JSONObject.NULL:data).toString());}catch(JSONException e){throw new IOException(e);}}
}

