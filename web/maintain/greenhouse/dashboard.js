(function() {
    'use strict';
    const $ = id => document.getElementById(id);
    let applied = {},
        busy = false,
        lastSuccess = '',
        generation = 0,
        queued = false;
    const f = $('dashFilter');
    const now = new Date(Date.now() + 8 * 3600000);
    f.elements.date_to.value = now.toISOString().slice(0, 10);
    f.elements.date_from.value = new Date(now.getTime() - 6 * 86400000).toISOString().slice(0, 10);
    applied = Object.fromEntries(new FormData(f));

    function bars(id, rows, percent) {
        const host = $(id);
        host.replaceChildren();
        const sum = rows.reduce((s, r) => s + Number(r.count), 0),
            max = percent ? sum : Math.max(1, ...rows.map(r => Number(r.count)));
        if (!rows.length || sum === 0) {
            host.textContent = '暂无可统计数据';
            return;
        }
        rows.forEach((r, i) => {
            const label = document.createElement('p');
            label.textContent = r.name + '：' + r.count + (percent ? '（' + (100 * r.count / sum).toFixed(1) + '%）' : '');
            const bar = document.createElement('div');
            bar.className = 'bar';
            const span = document.createElement('span');
            span.style.width = (100 * r.count / max) + '%';
            span.style.background = ['#287a54', '#bd7c22', '#457ea5', '#805aa4'][i % 4];
            bar.append(span);
            host.append(label, bar);
        });
    }
    async function refresh() {
        if (busy) return;
        const current = generation;
        busy = true;
        $('dashRefresh').disabled = true;
        try {
            const r = (await GHApi.get('/api/greenhouse-dashboard', applied)).data;
            if (current !== generation) return;
            const s = r.summary;
            $('dashTotal').textContent = s.total;
            $('dashArea').textContent = s.area;
            $('dashDevices').textContent = r.devices.reduce((n, x) => n + Number(x.count), 0);
            bars('dashStatuses', [{
                name: '启用',
                count: s.enabled
            }, {
                name: '停用',
                count: s.disabled
            }], true);
            bars('dashDistribution', r.devices.map(x => ({
                name: x.name,
                count: x.count
            })), false);
            bars('dashCrops', r.crops.map(x => ({
                name: x.crop_name,
                count: x.count
            })), true);
            lastSuccess = r.updated_at;
            $('dashState').textContent = '读取成功 · 当前状态';
            $('dashState').className = '';
            $('dashTime').textContent = '上次成功：' + lastSuccess + '；统一范围：' + r.date_from + ' 00:00 至 ' + r.date_to_exclusive + ' 00:00之前';
        } catch (e) {
            if (current !== generation) return;
            $('dashState').textContent = '读取失败：' + e.message + '；已保留上次成功值';
            $('dashState').className = 'error';
            $('dashTime').textContent = '上次成功：' + (lastSuccess || '尚无');
        } finally {
            busy = false;
            $('dashRefresh').disabled = false;
            if (queued) {
                queued = false;
                refresh();
            }
        }
    }
    f.onsubmit = e => {
        e.preventDefault();
        applied = Object.fromEntries(new FormData(f));
        generation++;
        if (busy) queued = true;
        else refresh();
    };
    $('dashRefresh').onclick = refresh;
    GHApi.get('/api/greenhouses', {
        action: 'list',
        size: 100
    }).then(async r => {
        let page = 1;
        for (;;) {
            r.data.items.forEach(g => $('dashGreenhouse').append(new Option(g.name, g.id)));
            if (page * 100 >= Number(r.data.total)) break;
            r = await GHApi.get('/api/greenhouses', {
                action: 'list',
                size: 100,
                page: ++page
            });
        }
    }).catch(e => {
        $('dashState').textContent = '大棚选项读取失败：' + e.message;
    });
    refresh();
    const timer = setInterval(refresh, 30000);
    window.addEventListener('pagehide', () => clearInterval(timer));
})();
