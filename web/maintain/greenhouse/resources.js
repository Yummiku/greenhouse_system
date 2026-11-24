(function(){
 'use strict';
 const $=id=>document.getElementById(id),labels={planned:'计划',growing:'生长中',harvested:'已收获',canceled:'已取消'};
 const isMap=!!$('mapChoice'),isDevices=!!$('deviceFilter'),isBatches=!!$('batchFilter');
 const get=(action,data={})=>GHApi.get('/api/greenhouse-resources',{action,...data});
 let csrfToken='', editingBatch=null;
 const post=(action,data={})=>GHApi.post('/api/greenhouse-resources',{...data,action,csrf_token:csrfToken});
 let writable=false,operator='',greenhouses=[],filter={},page=1,total=0,version=0;
 const notice=t=>{$('notice').textContent=t;};
 const protect=fn=>async function(e){if(e)e.preventDefault();try{notice('加载中…');await fn(e);notice('操作成功');}catch(err){notice(err.message);}};
 const values=form=>Object.fromEntries(new FormData(form));
 function cell(tr,text){const td=document.createElement('td');td.textContent=text??'';tr.append(td);return td;}
 function button(td,label,fn){const b=document.createElement('button');b.type='button';b.textContent=label;b.onclick=protect(fn);td.append(b);}
 async function mapLoad(){greenhouses=(await get('map')).data;if(isMap){$('mapChoice').replaceChildren();greenhouses.forEach(g=>$('mapChoice').append(new Option(g.name+(g.latitude==null?'（未定位）':''),g.id)));showMap();}}
 function showMap(){
  if(!isMap)return;
  const g=greenhouses.find(x=>String(x.id)===$('mapChoice').value);$('mapFrame').hidden=true;$('mapFrame').removeAttribute('src');$('mapLink').hidden=true;$('geoForm').hidden=!writable||!g;
  $('mapInfo').textContent=g?'大棚名称：'+g.name+'\n大棚编号：'+g.greenhouse_code+'\n所在位置：'+g.location+'\n占地面积：'+g.area+' 平方米  ·  使用状态：'+(g.status==='enabled'?'启用':'停用'):'';
  $('mapEmpty').hidden=!!g&&g.latitude!=null&&g.longitude!=null;
  $('mapEmpty').textContent=g?'这座大棚还没有地图位置。管理员保存坐标后，即可查看定位。':'暂无大棚档案，请先在大棚档案页面新增大棚。';
  $('clearGeo').disabled=!g||g.latitude==null||g.longitude==null;
  if(!g)return;
  $('geoForm').elements.namedItem('latitude').value=g.latitude??'';$('geoForm').elements.namedItem('longitude').value=g.longitude??'';
  if(g.latitude==null||g.longitude==null)return;
  const lat=Number(g.latitude),lon=Number(g.longitude),bbox=[Math.max(-180,lon-.01),Math.max(-90,lat-.01),Math.min(180,lon+.01),Math.min(90,lat+.01)].join(',');
  $('mapFrame').src='https://www.openstreetmap.org/export/embed.html?'+new URLSearchParams({bbox,layer:'mapnik',marker:lat+','+lon});$('mapFrame').hidden=false;
  $('mapLink').href='https://www.openstreetmap.org/?'+new URLSearchParams({mlat:lat,mlon:lon})+'#map=16/'+lat+'/'+lon;$('mapLink').hidden=false;
 }
 const metricLabels={air_temperature:'空气温度',air_humidity:'空气湿度',soil_temperature:'土壤温度',soil_moisture:'土壤湿度',illuminance:'光照强度',control:'设备控制'};
 const metricUnits={air_temperature:'℃',air_humidity:'%',soil_temperature:'℃',soil_moisture:'%',illuminance:'lx',control:'—'};
 const deviceTypeLabels={sensor:'传感器',control:'控制设备'};
 let unboundDevices=[],deviceVersion=0;
 function readableDeviceName(d){return String(d.device_name||'未命名设备').replace(/air_temperature|air_humidity|soil_temperature|soil_moisture|illuminance/g,key=>metricLabels[key]);}
 function updateBinding(){
  const selected=unboundDevices.find(d=>String(d.device_pk)===$('unboundDevice').value);
  const target=$('bindForm').elements.namedItem('greenhouse_id');
  const metric=$('bindingMetric'),previous=metric.value;metric.replaceChildren();
  const keys=selected?.device_type==='sensor'?Object.keys(metricLabels).filter(k=>k!=='control'):selected?.device_type==='control'?['control']:[];
  keys.forEach(k=>metric.append(new Option(metricLabels[k]+' '+metricUnits[k],k)));
  if(!keys.length)metric.append(new Option(selected?'暂无可用指标':'先选择设备',''));
  if(keys.includes(previous))metric.value=previous;
  $('bindFields').disabled=!writable||unboundDevices.length===0||target.options.length===0;
  $('bindDevice').disabled=!selected||!target.value||keys.length===0;
  $('bindingHint').textContent=unboundDevices.length===0?'当前没有可绑定设备。已有归属的设备无需再次绑定；新增设备档案后，可在此分配大棚。':target.options.length===0?'当前没有启用的大棚，请先在大棚档案中新增或启用大棚。':!selected?'请选择一台未绑定设备，再选择目标大棚和采集指标。':keys.length===0?'该设备类型暂不支持绑定，请核对设备档案类型。':'确认后，该设备的归属将记录为所选大棚；采集指标表示它测量的环境数据。';
 }
 function deviceMessageRow(message){const tr=document.createElement('tr'),td=cell(tr,message);td.colSpan=7;td.className='device-empty';$('devices').replaceChildren(tr);}
 async function devicesLoad(){
  const current=++deviceVersion;$('bindFields').disabled=true;$('deviceNotice').textContent='正在查询设备…';
  try{
   const [r,free]=await Promise.all([get('devices',values($('deviceFilter'))),get('devices',{unbound:1})]);
   if(current!==deviceVersion)return;
   $('devices').replaceChildren();
   r.data.forEach(d=>{
    const tr=document.createElement('tr'),name=cell(tr,readableDeviceName(d));
    const code=document.createElement('span');code.className='device-code';code.textContent='设备编号：'+(d.device_code||'未填写');name.append(code);
    const bound=d.greenhouse_id!=null;
    cell(tr,deviceTypeLabels[d.device_type]||'其他设备');cell(tr,bound?(d.greenhouse_name||'大棚名称暂不可用'):'尚未分配');
    cell(tr,bound?(metricLabels[d.metric_type]||'其他指标'):'绑定时选择');cell(tr,bound?(d.unit==='-'?'—':d.unit||'—'):'—');
    const state=cell(tr,''),badge=document.createElement('span');badge.className='device-state'+(bound?'':' unbound');badge.textContent=bound?'已绑定':'未绑定';state.append(badge);
    const actions=cell(tr,'');actions.className='controls';
    button(actions,'历史归属',async()=>{try{const history=(await get('device_history',{device_pk:d.device_pk})).data;$('deviceHistoryTitle').textContent=readableDeviceName(d);$('deviceHistoryRows').replaceChildren();history.forEach(h=>{const row=document.createElement('tr');[h.greenhouse_name,metricLabels[h.metric_type]||'其他指标',h.unbound_at,h.operator_name||'—'].forEach(v=>cell(row,v));$('deviceHistoryRows').append(row);});if(!history.length){const row=document.createElement('tr'),empty=cell(row,'尚无解除绑定记录。');empty.colSpan=4;$('deviceHistoryRows').append(row);}$('deviceHistoryDialog').showModal();}catch(err){$('deviceNotice').textContent=err.message;}});
    if(writable&&bound)button(actions,'解除绑定',async()=>{if(!confirm('确认将“'+readableDeviceName(d)+'”从“'+d.greenhouse_name+'”解除绑定？原归属将保留在历史记录中。'))return;try{await post('unbind',{device_pk:d.device_pk,greenhouse_id:d.greenhouse_id});await devicesLoad();$('deviceNotice').textContent='已解除绑定，可重新选择设备并分配大棚。';}catch(err){$('deviceNotice').textContent=err.message;}});
    $('devices').append(tr);
   });
   if(!r.data.length)deviceMessageRow('没有符合条件的设备，请调整查询条件。');
   const boundCount=r.data.filter(d=>d.greenhouse_id!=null).length;
   $('deviceNotice').textContent='当前查询 '+r.data.length+' 台设备 · 已绑定 '+boundCount+' 台 · 未绑定 '+(r.data.length-boundCount)+' 台';
   unboundDevices=free.data;$('unboundDevice').replaceChildren(new Option(free.data.length?'请选择未绑定设备':'暂无未绑定设备',''));
   free.data.forEach(d=>$('unboundDevice').append(new Option(readableDeviceName(d)+'（'+(d.device_code||'未填写编号')+'）',d.device_pk)));
   updateBinding();
  }catch(err){if(current!==deviceVersion)return;unboundDevices=[];$('unboundDevice').replaceChildren(new Option('设备加载失败',''));updateBinding();$('bindingHint').textContent='设备列表加载失败，请重新查询后再绑定。';$('deviceNotice').textContent=err.message;deviceMessageRow('设备加载失败，请重新查询。');throw err;}
 }
 function renderBatchDetail(data){
  const names={batch_code:'批次编号',greenhouse_name:'所属大棚',crop_name:'作物名称',variety:'品种',planting_date:'种植日期',growth_days:'生长周期 天',status:'种植状态',remark:'备注',created_at:'创建时间',updated_at:'更新时间'};
  $('resourceDetailText').replaceChildren();Object.entries(names).forEach(([key,name])=>{const dt=document.createElement('dt'),dd=document.createElement('dd');dt.textContent=name;dd.textContent=key==='status'?(labels[data[key]]||'未知状态'):(data[key]??'—');$('resourceDetailText').append(dt,dd);});
 }
 function renderBatches(items){$('batches').replaceChildren();if(!items.length){const tr=document.createElement('tr'),td=cell(tr,'暂无符合条件的种植批次，可调整筛选条件或新增批次。');td.colSpan=8;td.className='device-empty';$('batches').append(tr);}items.forEach(b=>{const tr=document.createElement('tr');['batch_code','greenhouse_name','crop_name','variety','planting_date','growth_days','status'].forEach(k=>cell(tr,k==='status'?labels[b[k]]:b[k]));const td=cell(tr,'');td.className='controls';
  button(td,'详情',async()=>{const d=(await get('batch_detail',{id:b.id})).data;renderBatchDetail(d);$('resourceDetail').showModal();});
  if(writable&&['planned','growing'].includes(b.status))button(td,'编辑',async()=>{const d=(await get('batch_detail',{id:b.id})).data;openBatch(d);});
  if(writable&&b.status==='planned')button(td,'删除',async()=>{if(!confirm('确认逻辑删除此计划批次？'))return;await post('batch_delete',{id:b.id});await batchesLoad();});$('batches').append(tr);
 });}
 async function batchesLoad(){const current=++version;$('batchSummary').textContent='批次加载中…';const [r,s]=await Promise.all([get('batches',{...filter,page}),get('batch_stats',filter)]);if(current!==version)return;total=Number(r.data.total);renderBatches(r.data.items);$('batchSummary').textContent='当前条件共 '+total+' 个批次';$('pageLabel').textContent='第 '+page+' 页';$('previous').disabled=page<=1;$('following').disabled=page*10>=total;
  $('batchStats').textContent='作物分布（按批次数）：'+(s.data.crops.map(x=>x.crop_name+' '+x.count).join('；')||'暂无数据')+'\n状态分布：'+(s.data.statuses.map(x=>(labels[x.status]||x.status)+' '+x.count).join('；')||'暂无数据');
 }
 function batchConditions(){const names={greenhouse_id:'所属大棚',crop_name:'作物',variety:'品种',status:'状态',date_from:'种植日期从',date_to:'种植日期到',sort:'排序依据',dir:'顺序'};return Object.entries(filter).filter(([k,v])=>v&&names[k]).map(([k,v])=>names[k]+'：'+(k==='greenhouse_id'?(greenhouses.find(g=>String(g.id)===v)?.name||'所选大棚'):k==='status'?(labels[v]||v):k==='sort'?({planting_date:'种植日期',batch_code:'批次编号',created_at:'创建时间'}[v]||v):k==='dir'?(v==='desc'?'降序':'升序'):v)).join('；')||'全部批次';}
 function openBatch(data={}){
 $('batchDialogTitle').textContent=data.id?'编辑种植批次':'新增种植批次';
 editingBatch=data.id?data:null;const form=$('batchForm');form.reset();
 Array.from(form.elements).forEach(input=>{input.disabled=false;input.readOnly=false;});
 const select=form.elements.namedItem('greenhouse_id');select.replaceChildren();
 greenhouses.filter(g=>g.status==='enabled'||String(g.id)===String(data.greenhouse_id)).forEach(g=>select.append(new Option(g.name+(g.status==='disabled'?'（已停用）':''),g.id)));
 form.elements.namedItem('status').replaceChildren();Object.entries(labels).forEach(([k,v])=>form.elements.namedItem('status').append(new Option(v,k)));
 form.elements.namedItem('id').value='';Object.entries(data).forEach(([k,v])=>{const input=form.elements.namedItem(k);if(input)input.value=v??'';});
 const greenhouse=greenhouses.find(g=>String(g.id)===String(data.greenhouse_id));
 if(editingBatch&&data.status==='growing')select.disabled=true;
 if(editingBatch&&greenhouse&&greenhouse.status==='disabled'){
  Array.from(form.elements).forEach(input=>{if(input.name&&input.name!=='status'&&input.name!=='id')input.disabled=true;});
  const status=form.elements.namedItem('status');status.replaceChildren();
  (data.status==='growing'?['harvested','canceled']:['canceled']).forEach(k=>status.append(new Option(labels[k],k)));
 }
 $('batchHint').textContent=editingBatch&&greenhouse?.status==='disabled'?'所属大棚已停用，本次只能登记收获或取消，其他历史信息保持不变。':editingBatch&&data.status==='growing'?'本批次已开始种植，所属大棚保持不变。':'填写本次种植安排；批次编号必须唯一，新增批次需选择启用大棚。';
 $('batchError').textContent='';$('batchDialog').showModal();
 }
 if(isMap){
 $('mapChoice').onchange=showMap;
 $('geoForm').onsubmit=protect(async()=>{const id=$('mapChoice').value;await post('geo_save',{...values($('geoForm')),greenhouse_id:id});await mapLoad();$('mapChoice').value=id;showMap();});
 $('clearGeo').onclick=protect(async()=>{if(!confirm('确认清除所选大棚定位？档案及业务记录保留。'))return;const id=$('mapChoice').value;await post('geo_clear',{greenhouse_id:id});await mapLoad();$('mapChoice').value=id;showMap();});
 }
 if(isDevices){
 $('newDevice').onclick=()=>{$('deviceForm').reset();$('deviceError').textContent='';$('deviceDialog').showModal();};
 $('closeDevice').onclick=()=>$('deviceDialog').close();$('closeDeviceHistory').onclick=()=>$('deviceHistoryDialog').close();
 $('deviceForm').onsubmit=async e=>{e.preventDefault();const save=e.target.querySelector('button[type="submit"],button:not([type])');save.disabled=true;try{await post('device_create',values(e.target));$('deviceDialog').close();$('deviceFilter').reset();await devicesLoad();$('deviceNotice').textContent='设备已新增，请在下方选择设备并绑定大棚。';}catch(err){$('deviceError').textContent=err.message;}finally{save.disabled=false;}};
 $('unboundDevice').onchange=updateBinding;
 $('bindForm').elements.namedItem('greenhouse_id').onchange=updateBinding;
 $('deviceFilter').onsubmit=async e=>{e.preventDefault();try{await devicesLoad();}catch(err){notice(err.message);}};
 $('bindForm').onsubmit=async e=>{e.preventDefault();if(!writable||$('bindFields').disabled||$('bindDevice').disabled)return;
  $('bindDevice').disabled=true;
  try{await post('bind',values($('bindForm')));await devicesLoad();$('deviceNotice').textContent='绑定成功。'+$('deviceNotice').textContent;}catch(err){$('deviceNotice').textContent=err.message;updateBinding();}
 };
 }
 if(isBatches){
 $('resetBatch').onclick=protect(async()=>{$('batchFilter').reset();filter={};page=1;await batchesLoad();});
 $('batchFilter').onsubmit=protect(async()=>{filter=values($('batchFilter'));page=1;await batchesLoad();});
 $('newBatch').onclick=()=>openBatch();$('closeBatch').onclick=()=>$('batchDialog').close();$('closeDetail').onclick=()=>$('resourceDetail').close();
 $('batchForm').onsubmit=async e=>{e.preventDefault();try{const submitted=values(e.target);if(editingBatch){for(const key of ['greenhouse_id','batch_code','crop_name','variety','planting_date','growth_days','remark'])if(!Object.prototype.hasOwnProperty.call(submitted,key))submitted[key]=editingBatch[key];}await post('batch_save',submitted);$('batchDialog').close();await batchesLoad();notice('批次已保存');}catch(err){$('batchError').textContent=err.message;}};
 $('previous').onclick=protect(async()=>{if(page>1)page--;await batchesLoad();});$('following').onclick=protect(async()=>{if(page*10<total)page++;await batchesLoad();});
 $('batchExport').onclick=()=>{location.href=GH_CONTEXT_PATH+'/api/greenhouse-resources?'+new URLSearchParams({action:'batch_export',...filter});};
 $('batchPrint').onclick=protect(async()=>{let items=[],p=1,r;do{r=await get('batches',{...filter,page:p++,size:100});items.push(...r.data.items);if(r.data.items.length===0)break;}while(items.length<Number(r.data.total));renderBatches(items);$('printMeta').textContent='种植批次清单；条件：'+batchConditions()+'；总数：'+items.length+'；操作人：'+operator+'；时间：'+new Date().toLocaleString('zh-CN',{timeZone:'Asia/Shanghai'});window.print();await batchesLoad();});
 }
 protect(async()=>{const c=(await GHApi.get('/api/greenhouses',{action:'context'})).data;csrfToken=c.csrf_token||'';writable=c.role!=='3'&&!!csrfToken;operator=c.operator;if(isDevices){$('bindForm').hidden=!writable;$('newDevice').hidden=!writable;}if(isBatches)$('newBatch').hidden=!writable;await mapLoad();document.querySelectorAll('select.greenhouse').forEach(s=>{greenhouses.filter(g=>!s.classList.contains('active')||g.status==='enabled').forEach(g=>s.append(new Option(g.name,g.id)));});
 const selected=new URLSearchParams(location.search).get('greenhouse_id');if(selected&&greenhouses.some(g=>String(g.id)===selected)){
  if(isMap){$('mapChoice').value=selected;showMap();}document.querySelectorAll('select.greenhouse').forEach(s=>{if(Array.from(s.options).some(o=>o.value===selected))s.value=selected;});filter={greenhouse_id:selected};
 }
 if(isDevices)await devicesLoad();if(isBatches)await batchesLoad();})().catch(err=>notice(err.message));
})();
