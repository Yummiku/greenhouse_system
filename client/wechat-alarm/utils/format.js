const metrics={air_temperature:'空气温度',air_humidity:'空气湿度',soil_temperature:'土壤温度',soil_moisture:'土壤湿度',illuminance:'光照强度'};
const states={pending:'待确认',processing:'处理中',closed:'已关闭'},sources={real:'实采',simulated:'模拟',manual:'人工'};
function event(a){return {...a,metric_label:metrics[a.metric_type],state_label:states[a.status],source_label:sources[a.source],direction_label:a.direction==='low'?'低于下限':'高于上限',lower_label:a.first_lower==null?'未设置':a.first_lower,upper_label:a.first_upper==null?'未设置':a.first_upper,last_lower_label:a.last_lower==null?'未设置':a.last_lower,last_upper_label:a.last_upper==null?'未设置':a.last_upper,close_label:a.close_reason==='normal'?'正常恢复':a.close_reason==='invalid'?'失效关闭':'尚未关闭'};}
module.exports={metrics,states,sources,event};
