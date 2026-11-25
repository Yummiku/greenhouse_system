// Exercises mini-program page state with a fake wx runtime. This is not a WeChat device test.
const vm=require('node:vm'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.join(__dirname,'../client/wechat-alarm');let tests=0;
function check(name,condition){assert.ok(condition,name);console.log('PASS '+name);tests++;}
let nextResult,failed=false,calls=[];
const api={request:async(action,args)=>{calls.push({action,args});if(failed)throw Error('network failed');return nextResult;},clear(){}};
function load(relative){let definition;const context={Page:p=>definition=p,require:name=>name.includes('/api')?api:require(path.join(root,'utils/format.js')),wx:{stopPullDownRefresh(){},navigateTo(){},reLaunch(){}},console,Map};vm.runInNewContext(fs.readFileSync(path.join(root,relative),'utf8'),context,{filename:relative});const page={...definition,data:structuredClone(definition.data),setData(data){for(const [key,val] of Object.entries(data)){const keys=key.split('.');let obj=this.data;for(const part of keys.slice(0,-1))obj=obj[part];obj[keys.at(-1)]=val;}}};return page;}
(async()=>{
 const page=load('pages/alarms/index.js');page.applied={source:'manual'};
 nextResult={items:[{id:'1',metric_type:'air_temperature',status:'pending',source:'manual'}],total:2,page:1};await page.load(1);
 check('first page displays backend data',page.data.items.length===1&&page.data.items[0].metric_label==='空气温度');
 nextResult={items:[{id:'1',status:'pending'},{id:'2',status:'closed'}],total:2,page:2};await page.load(2);await page.load(2);
 check('repeated page cannot append duplicate events',page.data.items.length===2);
 check('next page uses applied conditions',calls.at(-1).args.source==='manual'&&calls.at(-1).args.page===2);
 page.data.draft.source='real';nextResult={items:[{id:'3',status:'pending'}],total:1,page:1};await page.refresh();
 check('pull refresh uses applied filter not unqueried draft',calls.at(-1).args.source==='manual'&&page.data.items[0].id==='3');
 failed=true;await page.refresh();check('network failure differs from empty success',page.data.error==='network failed'&&page.data.loaded===false&&page.data.items.length===0);
 failed=false;nextResult={items:[],total:0,page:1};await page.refresh();check('empty result is loaded with zero total',page.data.loaded&&page.data.total===0&&!page.data.error);
 page.data.busy=true;const count=calls.length;await page.refresh();check('request in flight prevents overlap',calls.length===count);page.data.busy=false;
 page.data.draft.start='2026-11-26';page.data.draft.end='2026-11-25';page.query();check('reversed dates rejected without request',calls.length===count&&page.data.error.includes('起始日期'));
 const detail=load('pages/detail/index.js');detail.id='5';nextResult={id:'5',metric_type:'air_temperature',status:'closed',source:'manual',first_lower:0,first_upper:35,actions:[]};await detail.refresh();check('zero lower bound and closed state displayed',detail.data.event.lower_label===0&&detail.data.event.state_label==='已关闭');
 failed=true;await detail.refresh();check('detail failure clears stale record and permits retry',detail.data.event===null&&!detail.data.busy&&detail.data.error==='network failed');
 console.log('Mini client state checks passed: '+tests);
})().catch(e=>{console.error(e);process.exitCode=1;});
