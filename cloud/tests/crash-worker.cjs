'use strict';
const fs=require('node:fs');
const {createApp}=require('../src/server.cjs');
const app=createApp({dataDir:process.env.TEST_DATA,adminKey:process.env.TEST_KEY,freeReserve:0,
  faultHook(point){if(point===process.env.TEST_FAULT){fs.writeFileSync(process.env.TEST_DATA+'/fault-fired',point);process.kill(process.pid,'SIGKILL');}}
});
app.server.listen(0,'127.0.0.1',()=>console.log('READY:'+app.server.address().port));
process.on('SIGTERM',()=>app.close().then(()=>process.exit(0)));
