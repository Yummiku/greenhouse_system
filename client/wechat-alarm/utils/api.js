const TOKEN='xm06_alarm_token',BASE='xm06_alarm_base';
function base(){return wx.getStorageSync(BASE)||'';}
function configure(value){if(!/^https?:\/\/[^\s?#]+$/.test(value))throw new Error('请输入完整后端上下文地址，不带查询参数');wx.setStorageSync(BASE,value.replace(/\/$/,''));}
function clear(){wx.removeStorageSync(TOKEN);}
function request(action,data={},auth=false){
 return new Promise((resolve,reject)=>{
  if(!base()){reject(new Error('请先填写后端地址'));return;}
  wx.request({url:base()+'/api/week08/auth',method:auth?'POST':'GET',data:{...data,action},header:{'content-type':'application/x-www-form-urlencoded','X-Week08-Token':wx.getStorageSync(TOKEN)||''},
   success(res){const body=res.data;if(res.statusCode===401){clear();wx.reLaunch({url:'/pages/login/index'});reject(new Error('登录已失效，请重新登录'));return;}if(res.statusCode>=400||!body||body.result_code!==0){reject(new Error(body?.result_msg||(res.statusCode===403?'无权查看':'服务读取失败')));return;}resolve(auth?body:body.data);},
   fail(){reject(new Error('网络连接失败，请检查地址后重试'));}});
 });
}
async function login(){const result=await new Promise((resolve,reject)=>wx.login({success:resolve,fail:()=>reject(new Error('微信登录失败，请重试'))}));const user=await request('wx_login',{code:result.code},true);wx.setStorageSync(TOKEN,user.token);return user;}
module.exports={request,login,configure,base,clear};
