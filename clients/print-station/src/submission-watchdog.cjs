'use strict';
/** A bounded wait, NOT cancellation of paper already submitted to a driver. */
async function bounded(work,ms,onTimeout=()=>{}){
 if(!Number.isFinite(ms)||ms<=0)throw Error('invalid_print_timeout');
 let timer;
 try{return await Promise.race([work,new Promise((_,reject)=>{
  timer=setTimeout(()=>{try{onTimeout();}catch{}reject(Object.assign(Error('Native print outcome unknown'),{code:'native_print_timeout',beforeSubmit:false}));},ms);
 })]);}finally{clearTimeout(timer);}
}
module.exports={bounded};
