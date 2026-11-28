/* 农业大棚系统公共请求工具：接口路径不含项目访问前缀。 */
(function (global) {
    "use strict";

    function apiError(message, status, code) {
        var error = new Error(message);
        error.status = status || 0;
        error.code = code;
        return error;
    }

    async function request(method, path, parameters) {
        if (typeof global.GH_CONTEXT_PATH !== "string") {
            throw apiError("项目访问前缀未设置，请检查公共脚本引用", 0);
        }
        if (typeof path !== "string" || !/^\/api\/[A-Za-z0-9_/-]+$/.test(path)) {
            throw apiError("接口路径须以/api/开头，查询参数请单独传入", 0);
        }
        if (path.split("/").some(function (part) { return part === ".."; })) {
            throw apiError("接口路径无效", 0);
        }

        var url = new URL(global.GH_CONTEXT_PATH + path, global.location.origin);
        var values = new URLSearchParams();
        Object.keys(parameters || {}).forEach(function (key) {
            var value = parameters[key];
            if (value !== undefined && value !== null) {
                values.set(key, String(value));
            }
        });

        var controller = new AbortController();
        var timer = setTimeout(function () { controller.abort(); }, 15000);
        var options = {
            method: method,
            credentials: "same-origin",
            cache: "no-store",
            headers: { "X-Requested-With": "XMLHttpRequest" },
            signal: controller.signal
        };

        if (method === "GET") {
            url.search = values.toString();
        } else {
            options.headers["Content-Type"] =
                    "application/x-www-form-urlencoded;charset=UTF-8";
            options.body = values.toString();
        }

        try {
            var response;
            try {
                response = await global.fetch(url.toString(), options);
            } catch (error) {
                if (error.name === "AbortError") throw error;
                throw apiError("网络请求失败，请检查后台服务和网络连接", 0);
            }

            var text = await response.text();
            var result;
            try {
                result = JSON.parse(text);
            } catch (error) {
                throw apiError(
                        "接口未返回JSON，请检查访问地址、登录状态和部署结果",
                        response.status
                );
            }

            if (!result || typeof result !== "object"
                    || !Object.prototype.hasOwnProperty.call(result, "result_code")) {
                throw apiError("接口响应缺少result_code字段", response.status);
            }
            if (!response.ok || Number(result.result_code) !== 0) {
                var message = typeof result.result_msg === "string"
                        ? result.result_msg : "接口请求失败";
                throw apiError(message, response.status, result.result_code);
            }
            if (method === "POST" && path === "/api/messages") {
                global.document.dispatchEvent(new global.Event("gh:messages-changed"));
            }
            return result;
        } catch (error) {
            if (error.name === "AbortError") {
                throw apiError("请求超时，请稍后重试", 0);
            }
            throw error;
        } finally {
            clearTimeout(timer);
        }
    }

    global.GHApi = {
        get: function (path, parameters) {
            return request("GET", path, parameters);
        },
        post: function (path, parameters) {
            return request("POST", path, parameters);
        }
    };
})(window);
