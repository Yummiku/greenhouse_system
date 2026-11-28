const config = require('./config');

function request(path, data, method = 'GET', token) {
    return new Promise((resolve, reject) => wx.request({
        url: config.baseUrl + path,
        data,
        method,
        timeout: 15000,
        header: {
            'X-Week08-Token': token || wx.getStorageSync('greenhouse_wx_token'),
            'content-type': 'application/x-www-form-urlencoded'
        },
        success(response) {
            if (response.statusCode === 401) {
                wx.removeStorageSync('greenhouse_wx_token');
                const error = new Error('身份已失效，请重新登录；账号刚修改时请稍候一秒再登录');
                error.status = 401;
                return reject(error);
            }
            if (response.statusCode === 403) {
                const error = new Error('无权进行此操作，请联系管理员核对权限');
                error.status = 403;
                return reject(error);
            }
            const body = response.data;
            if (!body || typeof body !== 'object' || Array.isArray(body) || !Number.isInteger(body.result_code)) {
                return reject(new Error('服务器响应格式异常，请重试'));
            }
            if (response.statusCode !== 200 || body.result_code !== 0) {
                return reject(new Error(body.result_msg || '服务读取失败，请重试'));
            }
            if (path === '/api/mini/greenhouses') {
                const payload = body.data;
                const valid = payload && typeof payload === 'object' && (data.action === 'detail' ?
                    payload.greenhouse && typeof payload.greenhouse === 'object' && Array.isArray(payload.devices) :
                    Array.isArray(payload.items) && Number.isFinite(Number(payload.total)) && Number(payload.total) >= 0);
                if (!valid) return reject(new Error('业务数据格式异常，请重试'));
            }
            resolve(body);
        },
        fail(error) {
            reject(new Error(error && /timeout/i.test(error.errMsg || '') ? '请求超时，请重试' : '网络连接失败，请重试'));
        }
    }));
}

function login() {
    return new Promise((resolve, reject) => wx.login({
        success: r => resolve(r.code),
        fail: () => reject(new Error('微信登录失败'))
    })).then(code => request('/api/week08/auth', {
        action: 'wx_login',
        code
    }, 'POST')).then(r => {
        if (typeof r.token !== 'string' || !/^[a-f0-9]{64}$/.test(r.token)) throw new Error('登录响应格式异常，请重试');
        wx.setStorageSync('greenhouse_wx_token', r.token);
        return r;
    });
}
module.exports = {
    get: data => request('/api/mini/greenhouses', data),
    login,
    bind: text => request('/api/week08/auth', {
        action: 'bind_user',
        bind_text: text
    }, 'POST')
};
