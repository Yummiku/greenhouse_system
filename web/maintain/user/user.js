(function () {
    "use strict";
    const $ = id => document.getElementById(id);
    const roles = {"1":"系统管理员","2":"普通管理员","3":"普通用户"};
    const states = {active:"启用",disabled:"停用",deleted:"已删除"};
    const labels = {id:"记录ID",username:"账号",display_name:"显示名称",role_id:"角色",status:"状态",created_at:"创建时间",updated_at:"更新时间"};
    let context, applied = {}, page = 1, total = 0, sequence = 0, passwordTarget;
    const get = (action, params={}) => window.GHApi.get("/api/users", {action,...params});
    const post = (action, params) => window.GHApi.post("/api/users", {action,...params,csrf_token:context.csrf_token});
    const element = (tag,text) => {const e=document.createElement(tag);if(text!==undefined)e.textContent=text;return e;};
    function show(message) {$("user-message").textContent=message;}
    function describe(key,value) {return key==="role_id" ? roles[String(value)] : key==="status" ? states[value] : value ?? "";}
    function details(container,row) {
        container.replaceChildren();
        Object.keys(labels).forEach(key => {container.append(element("dt",labels[key]),element("dd",describe(key,row[key])));});
    }
    function button(text,action,disabled=false) {
        const e=element("button",text);e.type="button";e.className="btn btn-default btn-xs";e.disabled=disabled;
        e.addEventListener("click",async()=>{e.disabled=true;try{await action();}catch(error){show(error.message);}finally{e.disabled=disabled;}});return e;
    }
    function renderRows(items) {
        const body=$("user-rows");body.replaceChildren();
        if(!items.length){const tr=element("tr"),td=element("td","暂无匹配的用户");td.colSpan=6;tr.append(td);body.append(tr);return;}
        items.forEach(row=>{
            const tr=element("tr");
            ["username","display_name","role_id","status","created_at"].forEach(key=>tr.append(element("td",describe(key,row[key]))));
            const actions=element("td"),self=String(row.id)===String(context.current.id);
            actions.append(button("详情",async()=>{details($("user-detail-body"),(await get("detail",{id:row.id})).data);$("user-detail").showModal();}));
            if(row.status!=="deleted") {
                actions.append(button("编辑",()=>openEditor(row.id)));
                actions.append(button(row.status==="active"?"停用":"启用",async()=>{
                    if(!window.confirm("确认变更账号状态？该账号现有会话将失效。"))return;
                    await post("set_status",{id:row.id,version:row.version,status:row.status==="active"?"disabled":"active"});await load();
                },self));
                actions.append(button("重置密码",async()=>{passwordTarget=row;$("user-password-form").reset();$("user-password-target").textContent="账号："+row.username;$("user-password-message").textContent="";$("user-password-dialog").showModal();}));
                actions.append(button("删除",async()=>{
                    if(!window.confirm("确认逻辑删除该账号？账号将不能登录，关联业务保留。"))return;
                    await post("delete",{id:row.id,version:row.version});await load();
                },self));
            }
            tr.append(actions);body.append(tr);
        });
    }
    function counts(groups) {
        const role={"1":0,"2":0,"3":0},status={active:0,disabled:0,deleted:0};
        groups.forEach(g=>{role[String(g.role_id)]+=Number(g.count);status[g.status]+=Number(g.count);});return {role,status};
    }
    function charts(container,groups) {
        container.replaceChildren();const values=counts(groups);
        [["角色分布",values.role,roles],["状态分布",values.status,states]].forEach(([title,data,names])=>{
            const section=element("section");section.append(element("h4",title));
            const maximum=Math.max(1,...Object.values(data));
            Object.keys(data).forEach(key=>{
                const row=element("div"),track=element("div"),bar=element("div");row.className="user-bar";track.className="user-bar-track";bar.className="user-bar-fill";
                bar.style.width=(data[key]/maximum*100)+"%";bar.setAttribute("aria-label",names[key]+"："+data[key]+"人");
                track.append(bar);row.append(element("span",names[key]),track,element("span",data[key]+"人"));section.append(row);
            });container.append(section);
        });
    }
    async function load() {
        const request=++sequence,filters={...applied},requestedPage=page;
        show("正在查询……");
        const [list,stats]=await Promise.all([get("list",{...filters,page:requestedPage,size:10}),get("stats",filters)]);
        if(request!==sequence)return;
        total=Number(list.data.total);
        const pages=Math.max(1,Math.ceil(total/10));
        if(page>pages){page=pages;return load();}
        renderRows(list.data.items);charts($("user-statistics"),stats.data.groups);
        $("user-summary").textContent="当前筛选共 "+stats.data.total+" 人；统计和输出沿用已点击查询的条件。";
        $("user-pages").textContent="第 "+page+" / "+pages+" 页，共 "+total+" 条";
        $("user-prev").disabled=page<=1;$("user-next").disabled=page>=pages;
        show(total?"查询成功":"暂无匹配用户");
    }
    async function openEditor(id) {
        const f=$("user-form");f.reset();const field=name=>f.elements.namedItem(name);
        field("username").disabled=Boolean(id);field("role_id").disabled=false;field("status").disabled=false;
        field("password").required=!id;field("password").disabled=Boolean(id);$("user-initial-password").hidden=Boolean(id);
        field("id").value="";field("version").value="";
        if(id) {
            const row=(await get("detail",{id})).data;
            Object.entries(row).forEach(([key,value])=>{const input=field(key);if(input)input.value=value??"";});
            if(String(id)===String(context.current.id)){field("role_id").disabled=true;field("status").disabled=true;}
        }
        $("user-editor-title").textContent=id?"编辑账号资料":"新增账号";$("user-form-message").textContent="";$("user-editor").showModal();
    }
    $("user-filter").addEventListener("submit",event=>{
        event.preventDefault();applied=Object.fromEntries(new FormData(event.target));page=1;load().catch(error=>show(error.message));
    });
    $("user-reset").onclick=()=>{$("user-filter").reset();applied=Object.fromEntries(new FormData($("user-filter")));page=1;load().catch(error=>show(error.message));};
    $("user-prev").onclick=()=>{if(page>1){page--;load().catch(error=>show(error.message));}};
    $("user-next").onclick=()=>{if(page*10<total){page++;load().catch(error=>show(error.message));}};
    $("user-add").onclick=()=>openEditor().catch(error=>show(error.message));
    $("user-form").onsubmit=async event=>{
        event.preventDefault();const f=event.target,data=Object.fromEntries(new FormData(f));
        data.role_id=f.elements.namedItem("role_id").value;data.status=f.elements.namedItem("status").value;
        const save=$("user-save");save.disabled=true;
        try {
            await post(data.id?"update":"create",data);$("user-editor").close();
            context=(await get("context")).data;details($("user-profile"),context.current);await load();
        } catch(error){$("user-form-message").textContent=error.message;show(error.message);}
        finally{save.disabled=false;f.elements.namedItem("password").value="";}
    };
    $("user-password-form").onsubmit=async event=>{
        event.preventDefault();const f=event.target,password=f.elements.namedItem("password").value;
        if(password!==f.elements.namedItem("confirmation").value){$("user-password-message").textContent="两次密码不一致";return;}
        $("user-password-save").disabled=true;
        try {
            await post("reset_password",{id:passwordTarget.id,version:passwordTarget.version,password});$("user-password-dialog").close();
            if(String(passwordTarget.id)===String(context.current.id)) {show("密码已重置，正在返回登录页……");window.location.assign(window.GH_CONTEXT_PATH+"/home/main/login.jsp");return;}
            await load();show("密码已重置，该账号需要重新登录。");
        } catch(error){$("user-password-message").textContent=error.message;}
        finally{$("user-password-save").disabled=false;f.reset();}
    };
    document.querySelectorAll("[data-close]").forEach(e=>{e.onclick=()=>{const dialog=$(e.dataset.close);dialog.querySelectorAll('input[type="password"]').forEach(p=>{p.value="";});dialog.close();};});
    $("user-export").onclick=()=>{window.location.assign(window.GH_CONTEXT_PATH+"/api/users?"+new URLSearchParams({...applied,action:"export"}));};
    $("user-print").onclick=async()=>{
        const b=$("user-print");b.disabled=true;show("正在准备全部筛选记录……");
        try {
            const filters={...applied},r=await get("report",filters),items=r.data.items,area=$("user-print-area");area.replaceChildren();
            area.append(element("h2","农业大棚系统用户清单"));
            const readable="关键词："+(filters.keyword||"全部")+"；角色："+(roles[filters.role_id]||"全部")+"；状态："+(states[filters.status]||"未删除账号")+"；排序："+({username:"账号",created_at:"创建时间",updated_at:"更新时间",role_id:"角色",status:"状态"}[filters.sort]||"创建时间")+" "+(filters.dir==="asc"?"升序":"降序");
            area.append(element("p",readable),element("p","打印人："+context.current.display_name+"；时间："+new Date().toLocaleString("zh-CN",{timeZone:"Asia/Shanghai"})+"；共 "+items.length+" 人"));
            const groups=[];const tally=new Map();items.forEach(row=>{const k=row.role_id+":"+row.status;const g=tally.get(k)||{role_id:row.role_id,status:row.status,count:0};g.count++;tally.set(k,g);});tally.forEach(g=>groups.push(g));
            const graph=element("div");graph.className="user-charts";charts(graph,groups);area.append(graph);
            const table=element("table"),head=element("thead"),hr=element("tr"),body=element("tbody"),keys=["username","display_name","role_id","status","created_at","updated_at"];
            keys.forEach(k=>hr.append(element("th",labels[k])));head.append(hr);table.append(head);
            items.forEach(row=>{const tr=element("tr");keys.forEach(k=>tr.append(element("td",describe(k,row[k]))));body.append(tr);});
            if(!items.length){const tr=element("tr"),td=element("td","暂无数据");td.colSpan=keys.length;tr.append(td);body.append(tr);}
            table.append(body);area.append(table);window.print();show("打印预览已准备。");
        } catch(error){show(error.message);}finally{b.disabled=false;}
    };
    (async()=>{try{context=(await get("context")).data;details($("user-profile"),context.current);
        $("user-management").hidden=!context.can_manage;
        if(context.can_manage){applied=Object.fromEntries(new FormData($("user-filter")));await load();}
        else show("当前仅显示本人资料。用户管理维护权限属于系统管理员。");
    }catch(error){show(error.message);}})();
})();
