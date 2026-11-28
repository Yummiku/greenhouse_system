const api = require('../../api');
Page({
    data: {
        greenhouse: null,
        devices: [],
        error: '',
        loading: false
    },
    onLoad(q) {
        this.id = q.id;
        this.load();
    },
    onPullDownRefresh() {
        this.load().finally(() => wx.stopPullDownRefresh());
    },
    async load() {
        if (this.data.loading) return;
        this.setData({
            loading: true,
            error: ''
        });
        try {
            const r = await api.get({
                action: 'detail',
                id: this.id
            });
            const metrics = {
                air_temperature: '空气温度',
                air_humidity: '空气湿度',
                soil_temperature: '土壤温度',
                soil_moisture: '土壤湿度',
                illuminance: '光照强度'
            };
            const purposes = {
                ventilation: '通风',
                cooling: '降温',
                irrigation: '灌溉'
            };
            this.setData({
                ...r.data,
                devices: r.data.devices.map(d => ({
                    ...d,
                    metric_label: d.metric_type === 'control' ? (purposes[d.control_purpose] || '旧记录未采集') : (metrics[d.metric_type] || '未填写'),
                    unit_label: d.unit === '-' ? '—' : (d.unit || '—')
                }))
            });
        } catch (e) {
            this.setData({
                error: e.message,
                ...(e.status === 401 ? {
                    greenhouse: null,
                    devices: []
                } : {})
            });
        } finally {
            this.setData({
                loading: false
            });
        }
    },
    retry() {
        this.load();
    }
});
