(function () {
    "use strict";
    var toggle=document.getElementById("gh-message-toggle");
    if(!toggle || !window.GHApi)return;
    var badge=document.getElementById("gh-unread-count"),summary=document.getElementById("gh-unread-summary"),list=document.getElementById("gh-recent-messages");
    var pending=false,dirty=false,timer;
    async function refresh() {
        if(document.hidden)return;
        if(pending){dirty=true;return;}
        pending=true;
        try {
            var results=await Promise.all([
                window.GHApi.get("/api/messages",{action:"stats"}),
                window.GHApi.get("/api/messages",{action:"listInbox",page:1,pageSize:5,sortBy:"sentAt",sortDir:"desc"})
            ]);
            var count=Number(results[0].data.unread);
            if(!Number.isSafeInteger(count) || count<0)throw new Error("消息统计暂不可用");
            badge.hidden=count===0;badge.style.display=count>0?"inline-block":"none";badge.textContent=count>99?"99+":String(count);
            summary.textContent=count?"你有 "+count+" 条未读消息":"暂无未读消息";toggle.setAttribute("aria-label","我的消息，未读 "+count+" 条");
            list.textContent="";
            var rows=results[1].data.list || [];
            if(!rows.length){var empty=document.createElement("li");empty.style.padding="12px 16px";empty.textContent="暂无消息";list.appendChild(empty);}
            rows.forEach(function (row) {
                var li=document.createElement("li"),a=document.createElement("a"),time=document.createElement("small");
                a.href=window.GH_CONTEXT_PATH+"/maintain/message/message_inbox.jsp?id="+encodeURIComponent(row.id);
                a.style.cssText="display:block;padding:10px 16px;white-space:normal;overflow-wrap:anywhere";
                var title=document.createElement("span");title.textContent=(row.read?"":"未读 · ")+(row.title || "无标题消息");
                time.style.cssText="display:block;margin-top:4px;color:#666";time.textContent=row.sentAt || "";
                a.append(title,time);li.appendChild(a);list.appendChild(li);
            });
        } catch(error) {
            badge.hidden=true;badge.style.display="none";summary.textContent=error.status===401?"登录已失效，请重新登录":"消息暂不可用，点击查看全部消息重试";
            toggle.setAttribute("aria-label","我的消息，暂时无法获取未读数量");list.textContent="";
        } finally {pending=false;if(dirty){dirty=false;refresh();}}
    }
    toggle.addEventListener("click",refresh);
    document.addEventListener("gh:messages-changed",refresh);
    document.addEventListener("visibilitychange",function () {if(!document.hidden)refresh();});
    window.addEventListener("pageshow",refresh);
    window.addEventListener("pagehide",function () {window.clearInterval(timer);});
    window.addEventListener("pageshow",function () {window.clearInterval(timer);timer=window.setInterval(refresh,60000);});
    timer=window.setInterval(refresh,60000);refresh();
})();
