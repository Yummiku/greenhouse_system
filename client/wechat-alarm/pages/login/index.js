const api=require('../../utils/api');
Page({data:{base:'',error:'',busy:false,bound:false},onLoad(){this.setData({base:api.base()});},input(e){this.setData({base:e.detail.value});},
 async login(){if(this.data.busy)return;this.setData({busy:true,error:''});try{api.configure(this.data.base.trim());const result=await api.login();this.setData({bound:result.bound});if(result.bound)wx.reLaunch({url:'/pages/alarms/index'});}catch(e){this.setData({error:e.message});}finally{this.setData({busy:false});}},
 async bind(){if(this.data.busy)return;this.setData({busy:true,error:''});try{const scan=await new Promise((resolve,reject)=>wx.scanCode({onlyFromCamera:false,success:resolve,fail:()=>reject(new Error('已取消扫码，可重新扫描'))}));await api.request('bind_user',{bind_text:scan.result},true);wx.reLaunch({url:'/pages/alarms/index'});}catch(e){this.setData({error:e.message});}finally{this.setData({busy:false});}}
});
