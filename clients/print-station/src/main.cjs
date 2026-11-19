'use strict';
const path=require('node:path'),fs=require('node:fs');
const {createStation}=require('./station.cjs');
const root=process.env.LENSDROP_STATION_DATA_DIR||path.join(process.env.LOCALAPPDATA||process.env.HOME,'LuceStudio','LensDropPrintStation');
(async()=>{const s=createStation({root});const info=await s.start();fs.writeFileSync(path.join(root,'ui-session.txt'),info.ui,{mode:0o600});console.log('LensDrop station ready. Use the Windows tray menu to open its local dashboard.');for(const signal of ['SIGINT','SIGTERM'])process.on(signal,()=>s.close().then(()=>process.exit(0)));})().catch(()=>{console.error('Print Station startup failed. Check permissions, port 54172/54173, and the station log. Existing queue is not deleted.');process.exitCode=1;});
