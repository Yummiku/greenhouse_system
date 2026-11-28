const api = require('../../api');
Page({
    data: {
        items: [],
        keyword: '',
        applied: '',
        page: 1,
        total: 0,
        loading: false,
        error: ''
    },
    onLoad() {
        this.load(true);
    },
    onPullDownRefresh() {
        this.load(true).finally(() => wx.stopPullDownRefresh());
    },
    onKeyword(e) {
        this.setData({
            keyword: e.detail.value
        });
    },
    search() {
        if (this.data.loading) return;
        this.setData({
            applied: this.data.keyword
        });
        this.load(true);
    },
    retry() {
        this.load(true);
    },
    next() {
        if (this.data.items.length < this.data.total) this.load(false);
    },
    async login() {
        if (this.data.loading) return;
        try {
            this.setData({
                loading: true,
                error: ''
            });
            const r = await api.login();
            if (!r.bound) {
                this.setData({
                    error: '请先在PC生成本人绑定二维码，再点击扫码绑定'
                });
            } else {
                this.setData({
                    loading: false
                });
                await this.load(true);
            }
        } catch (e) {
            this.setData({
                error: e.message
            });
        } finally {
            this.setData({
                loading: false
            });
        }
    },
    bind() {
        wx.scanCode({
            success: async r => {
                try {
                    await api.bind(r.result);
                    await this.load(true);
                } catch (e) {
                    this.setData({
                        error: e.message
                    });
                }
            }
        });
    },
    async load(reset) {
        if (this.data.loading) return;
        const page = reset ? 1 : this.data.page + 1;
        this.setData({
            loading: true,
            error: ''
        });
        try {
            const r = await api.get({
                action: 'list',
                keyword: this.data.applied,
                page,
                size: 10
            });
            this.setData({
                items: reset ? r.data.items : this.data.items.concat(r.data.items),
                page,
                total: Number(r.data.total)
            });
        } catch (e) {
            this.setData({
                error: e.message,
                ...(e.status === 401 ? {
                    items: [],
                    page: 1,
                    total: 0
                } : {})
            });
        } finally {
            this.setData({
                loading: false
            });
        }
    },
    detail(e) {
        wx.navigateTo({
            url: 'detail?id=' + encodeURIComponent(e.currentTarget.dataset.id)
        });
    }
});
