'use strict';
const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const vm=require('node:vm');const path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../reference/03_pipeline.patched.js'),'utf8');
test('legacy prototype patch never evicts pending or failed imports',()=>{
 const block=source.match(/const Ingest = \{[\s\S]*?\n\};/)[0],ctx={rid:(()=>{let i=0;return()=>String(++i)})(),Console:{mountedTab:''},URL:{revokeObjectURL(){}}};vm.createContext(ctx);vm.runInContext(block+'\nIngest.pump=()=>{};globalThis.queue=Ingest;',ctx);
 for(const n of [400,401,450,1000,10000]){ctx.queue.jobs=[];ctx.queue.add(Array.from({length:n},(_,i)=>({name:'IMG_'+i+'.JPG',type:'image/jpeg'})),'a');assert.equal(ctx.queue.jobs.length,n);}
 ctx.queue.jobs=[...Array.from({length:500},(_,i)=>({name:'done'+i,done:true})),{name:'failed',done:true,err:true},{name:'pending',done:false}];ctx.queue.add([],'a');assert.equal(ctx.queue.jobs.length,402);assert.equal(ctx.queue.jobs.filter(j=>j.name==='failed').length,1);
});
test('legacy edited returns respect review-first policy and preserve hidden status',async()=>{
 const body=source.slice(source.indexOf('async function replaceJob(job){'),source.indexOf('/* run a different retouch preset'));
 let write;const ctx={performance,URL:{createObjectURL:()=>'',revokeObjectURL(){}},S:{photos:new Map(),albums:new Map([['a',{settings:{publish:'review'}}]]),me:{id:'editor'}},cfg:a=>({...a.settings,cullOn:false,maxEdge:3200}),Ingest:{stage(){}},decodeFile:async()=>({img:{},url:''}),analyze:()=>({}),renderDelivery:()=>({width:100,height:100}),toBlob:async()=>({size:2}),fit:()=>({w:100,h:100}),drawScaled:()=>({}),uploadAll:async()=>[{id:'new',sizeBytes:2},{id:'thumb'}],exposureFlags:()=>({}),photoRef:()=>({update:async v=>{write=v}}),deleteAsset(){}};
 vm.createContext(ctx);vm.runInContext(body+'\nglobalThis.replace=replaceJob;',ctx);
 for(const before of ['review','editing','hidden']){ctx.S.photos.set('p',{status:before,assetId:'old'});await ctx.replace({target:'p',albumId:'a',file:{}});assert.equal(write.status,before==='hidden'?'hidden':'review');}
});
