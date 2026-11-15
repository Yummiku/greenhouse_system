XM06 农业大棚系统：小组开发与启动说明

一、当前版本
项目使用 JDK 17、Tomcat 10.1、MySQL 8；不是 Spring Boot 或 Maven 工程。
打开包含 src、web、sql 和 DeviceDemo.iml 的这一层目录。
保留 DeviceDemo 模块/Artifact 名称可以正常运行；浏览器访问路径统一为 /greenhouse_system。
现有功能：登录/退出、三类角色、动态菜单、待办查询维护及报表、环境监测页面和连接检查。
环境监测尚未包含真实采样、历史曲线、阈值报警、Qt采集和设备控制。
旧设备CRUD已修正为参数化查询，它是兼容接口，还不等于完整农业设备业务模块。
旧教学SQL执行入口禁止访问；未完成的旧教学上传入口返回501。

二、每位组员独立初始化数据库
1. 在自己的MySQL中创建空库：
   CREATE DATABASE greenhouse_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
2. 在这个空库执行 sql/01_greenhouse_framework.sql。
   结果应为13张表、5条菜单、15条角色菜单关系；初始没有账号或业务记录。
3. 你已经建立13张表的数据库不重复执行01脚本，也不要删表重建。
   已有库如需补齐菜单，执行 sql/02_existing_database_menu.sql。
   原来的四个教学单表脚本已停用，避免误删数据和导入旧作业记录。
4. 修改表结构时提供新的迁移SQL给全组，不向组员发送个人数据库备份。

三、本机配置
1. 将 web/WEB-INF/greenhouse.example.properties 复制为同目录的 greenhouse.local.properties。
2. 填写自己的MySQL地址、端口、账号和密码，使用UTF-8编码。
   示例端口为3366；如果自己的MySQL使用3306，请改为3306。
   这是MySQL密码，不是网页登录密码。主机这份local文件已保留原有连接配置。
3. local文件位于WEB-INF，浏览器不能直接下载，且已加入.gitignore。
   手工打包/复制时.gitignore不会自动过滤文件，必须排除该文件和.idea/workspace.xml。
4. 也可以使用环境变量覆盖配置：
   GH_DB_HOST / GH_DB_PORT / GH_DB_USER / GH_DB_PASSWORD。
   或使用 -Dgreenhouse.db.host、port、user、password 系列JVM参数。
   可用 GH_CONFIG_FILE 或 -Dgreenhouse.configFile 指定外部私有配置文件绝对路径。
5. 默认从当前模块输出或部署输出的 WEB-INF/greenhouse.local.properties 读取配置。
   IDEA模块输出应保持为 web/WEB-INF/classes。配置变更后重新部署/重启。
6. PC开发不要求填写微信配置；原小程序接口保留 /api/week08/* 兼容路径。
   需要小程序时在local文件填写自己的 week08.apiKey、week08.wxAppId、week08.wxAppSecret。
   原有 W08_API_KEY、WEEK08_WX_APPID、WEEK08_WX_APPSECRET 环境变量仍然可用。
   这些旧小程序接口目前使用独立认证，不能把PC角色校验直接视为小程序权限实现。

四、IDEA配置与启动
1. Project SDK选择JDK 17。
2. Settings -> Appearance & Behavior -> Path Variables 新增 TOMCAT_HOME，
   值为自己安装的Tomcat 10.1目录（包含bin、lib、conf的目录）。
   项目lib库使用 $TOMCAT_HOME$/lib，不再绑定某个人的安装路径。
   如果IDEA提示未定义路径变量，按提示填写同一个目录。
3. Project Structure检查模块源目录src、Web目录web，以及WEB-INF/lib中的依赖。
4. Build -> Rebuild Project，重新生成类文件。不要继续使用旧的out或classes副本。
5. 创建Tomcat Local运行配置：
   Application Server：自己的Tomcat 10.1。
   JRE：JDK 17；HTTP port：9999（占用时可换端口）。
   Deployment：DeviceDemo:war exploded；Application context：/greenhouse_system。
   不需要部署旧教学 /upload 目录。
6. 在IDEA中运行 src/user/tools/AddUserDemo.java 的main方法，
   按提示为 admin（系统管理员）、manager（普通管理员）、worker（普通用户）设置密码。
   至少8个字符，UTF-8不超过72字节；保存BCrypt哈希。
   已存在的同名同角色账号保留原密码，不重置；同名不同角色会停止提示。
   工具与Tomcat使用同一套本机配置。
7. 运行Tomcat，打开：
   http://localhost:9999/greenhouse_system/
   若修改端口或项目访问路径，请同步调整浏览器地址。

五、交接后的启动检查
1. 未登录打开主页应进入登录页。
2. 未登录访问 /api/environment?action=ping 应返回HTTP401及JSON“请先登录”。
3. admin/manager登录后可以添加、查询、修改和删除自己的测试待办。
4. worker能查询，页面不显示维护按钮；绕过页面提交维护请求仍返回HTTP403。
5. 登录后进入农业管理 -> 环境监测，点击检查接口连接，应提示业务功能待接入。
   这个检查只证明接口可达，不表示已连接传感器或已实现采样数据库。
6. 测试待办筛选、导出、打印；新库为空时应显示无数据。
7. SQL通用入口query_record/update_record所有角色均返回403。
8. 旧设备接口 get_device_record 查询device_file；维护必须POST且只允许角色1/2。

六、小组开发约定
1. 新农业模块后端使用 src/greenhouse/<模块>，页面使用 web/maintain/<模块>。
2. 新接口在web.xml注册；LoginFilter需要识别 /api/ 接口以返回JSON401。
3. 当前BusinessPermissionFilter只覆盖两个兼容入口。
   新Servlet必须在后台校验登录、角色和数据范围，不能只隐藏页面按钮。
4. 新模块采用Servlet -> 业务处理/DAO -> MySQL流程，DAO使用PreparedStatement。
   不在Servlet成员变量中保存请求参数、当前用户或单次操作数据。
5. 公共页面引用 /home/frame/ 中的组件；前端使用GHApi.get/post。
   返回格式：{"result_code":0,"result_msg":"ok","data":{}}；错误配合正确HTTP状态码。
6. 数据库名称和表结构一致，连接配置、登录密码各自设置。
7. 页面不固定显示某个成员的学号、姓名或个人账号。
   顶部和业务记录中的当前登录用户姓名由真实账号数据生成。
8. 修改后至少由另一位组员在独立数据库中启动一次，再认定交接完成。

七、复制与发布
共享：src、web（排除WEB-INF/classes及greenhouse.local.properties）、sql、
DeviceDemo.iml、必要.idea项目配置、readme.txt和.gitignore。
排除：out、web/WEB-INF/classes、.idea/workspace.xml、私有配置和个人上传数据。
本次主机修改未连接或改动虚拟机数据库；复制回虚拟机后需要重新构建并部署。
主机未完成Java编译或Tomcat启动验收，必须在虚拟机按第五节验证。
