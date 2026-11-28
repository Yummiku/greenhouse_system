(function() {
    'use strict';
    let map, layer, saved, preview, tileOK = false;
    const $ = id => document.getElementById(id);
    window.GHMap = {
        show: function(g, writable) {
            if (!map) {
                map = L.map('mapCanvas').setView([0, 0], 1);
                layer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                    maxZoom: 19,
                    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                }).addTo(map);
                layer.on('tileerror', () => {
                    $('mapNetwork').textContent = tileOK ? '部分地图瓦片加载失败，可重试；已保存坐标仍可读取。' : '底图加载失败，请检查网络；这不代表大棚没有定位。';
                });
                layer.on('tileload', () => {
                    tileOK = true;
                    $('mapNetwork').textContent = '在线底图已加载，请核对已知位置后保存。';
                });
            }
            map.off('click');
            if (saved) {
                map.removeLayer(saved);
                saved = null;
            }
            if (preview) {
                map.removeLayer(preview);
                preview = null;
            }
            if (g && g.latitude != null && g.longitude != null) {
                saved = L.circleMarker([Number(g.latitude), Number(g.longitude)], {
                    radius: 9,
                    color: '#1b6745'
                }).addTo(map);
                const text = document.createElement('span');
                text.textContent = g.name + ' · 已保存位置';
                saved.bindPopup(text);
                map.setView([Number(g.latitude), Number(g.longitude)], 15);
                $('mapSaved').textContent = '已保存 WGS84：纬度 ' + g.latitude + '，经度 ' + g.longitude;
            } else {
                map.setView([0, 0], 1);
                $('mapSaved').textContent = '尚未设置位置；当前展示全球底图，不是大棚位置。';
            }
            if (g && writable) map.on('click', e => {
                const lat = e.latlng.lat,
                    lon = e.latlng.wrap().lng;
                if (lat < -90 || lat > 90) return;
                if (preview) map.removeLayer(preview);
                preview = L.circleMarker([lat, lon], {
                    radius: 9,
                    color: '#c57e18'
                }).addTo(map);
                const form = $('geoForm');
                form.elements.latitude.value = lat.toFixed(6);
                form.elements.longitude.value = lon.toFixed(6);
                $('mapPreview').textContent = '待保存预览：' + lat.toFixed(6) + ', ' + lon.toFixed(6) + '。点击“保存位置”后才写入。';
            });
            $('mapPreview').textContent = '';
            setTimeout(() => map.invalidateSize(), 0);
        }
    };
})();
