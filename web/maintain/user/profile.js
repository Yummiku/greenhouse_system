(function () {
    "use strict";
    var status = document.getElementById("account-status"), context, busy = false;
    function show(text, error) { status.textContent = text; status.className = error ? "account-error" : ""; }
    function controls(disabled) { document.querySelectorAll("#account-center input,#account-center button").forEach(function (e) { e.disabled = disabled; }); }
    function render(row) {
        var box = document.getElementById("account-details");box.textContent = "";
        var roles = {1:"系统管理员",2:"普通管理员",3:"普通用户"};
        [["账号",row.username],["角色",roles[row.role_id] || "未识别"],["账号状态","启用"],["创建时间",row.created_at],["更新时间",row.updated_at]].forEach(function (item) {
            var dt=document.createElement("dt"),dd=document.createElement("dd");dt.textContent=item[0];dd.textContent=item[1] || "—";box.append(dt,dd);
        });
        document.getElementById("account-name").value=row.display_name;
    }
    async function load() { context=(await window.GHApi.get("/api/account",{action:"context"})).data;render(context.current); }
    document.getElementById("account-profile").addEventListener("submit",async function (e) {
        e.preventDefault();if(busy || !context)return;busy=true;controls(true);
        try {
            var name=document.getElementById("account-name").value.trim();
            if(!name)throw new Error("显示名称不能为空");
            var result=await window.GHApi.post("/api/account",{action:"profile",display_name:name,version:context.current.version,csrf_token:context.csrf_token});
            context.current=result.data;render(result.data);
            var header=document.getElementById("gh-account-name");if(header)header.textContent=result.data.display_name;
            show("个人资料已保存",false);
        } catch(error) {show(error.message,true);window.alert(error.message);} finally {busy=false;controls(false);}
    });
    document.getElementById("account-password").addEventListener("submit",async function (e) {
        e.preventDefault();if(busy || !context)return;busy=true;controls(true);var changed=false;
        try {
            var old=document.getElementById("account-old").value,password=document.getElementById("account-new").value,confirmation=document.getElementById("account-confirm").value;
            if(password!==confirmation)throw new Error("两次新密码不一致");
            await window.GHApi.post("/api/account",{action:"password",old_password:old,new_password:password,confirmation:confirmation,version:context.current.version,csrf_token:context.csrf_token});
            changed=true;context=null;show("密码已修改，请重新登录",false);window.alert("密码已修改，请使用新密码重新登录");
            window.location.assign(window.GH_CONTEXT_PATH+"/home/main/login.jsp");
        } catch(error) {show(error.message,true);window.alert(error.message);} finally {e.target.reset();busy=false;controls(changed);}
    });
    load().then(function () {controls(false);show("可查看及维护本人资料",false);if(window.location.hash==="#password")document.getElementById("password").scrollIntoView();}).catch(function (error) {show(error.message,true);});
})();
