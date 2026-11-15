(function () {
    "use strict";

    var button = document.getElementById("environment_check_connection");
    var status = document.getElementById("environment_connection_status");
    if (!button || !status) return;

    button.addEventListener("click", async function () {
        button.disabled = true;
        status.textContent = "正在检查连接……";
        try {
            if (!window.GHApi) {
                throw new Error("公共请求工具未加载，请检查脚本引用顺序");
            }
            var result = await window.GHApi.get("/api/environment", { action: "ping" });
            status.textContent = result.data && result.data.business_ready === false
                    ? "接口已连接，环境监测业务功能待接入。"
                    : "接口连接正常。";
        } catch (error) {
            status.textContent = error.status === 401
                    ? "登录已失效，请重新登录后检查。"
                    : "连接检查失败：" + error.message;
        } finally {
            button.disabled = false;
        }
    });
})();
