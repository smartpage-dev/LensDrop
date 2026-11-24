'use strict';
// Local settings only. The nine-field H5 job contract and public profile schema stay unchanged.
const P = require('./print-protocol.cjs');
const PRESETS = Object.freeze([
 {id:'cp9550-photo-4x6',label:'CP9550DW 4x6 photo',layout:'single',cut:'none',pieces:1},
 {id:'cp9550-strip-pair-auto',label:'CP9550DW 2x6 AUTO pair (1 copy = 2 strips)',layout:'strip_pair',cut:'driver_multicut',pieces:2},
 {id:'cp9550-strip-pair-manual',label:'CP9550DW 2x6 MANUAL pair (1 copy = 2 strips)',layout:'strip_pair',cut:'manual',pieces:2}
]);
function snapshot(s, printer) {
 P.check(s && s.schema === 1 && s.printer === printer, 'recapture_selected_printer_settings');
 P.check(typeof s.devmode_base64 === 'string' && s.devmode_base64.length <= 180000 && /^[A-Za-z0-9+/]+={0,2}$/.test(s.devmode_base64),'invalid_driver_snapshot');
 const b=Buffer.from(s.devmode_base64,'base64');
 P.check(b.length>=72 && b.length<=131072 && b.toString('base64')===s.devmode_base64,'invalid_driver_snapshot');
 const size=b.readUInt16LE(68),extra=b.readUInt16LE(70);
 P.check(size>=72 && size+extra===b.length && P.hash(b)===s.sha256,'driver_snapshot_checksum_mismatch');
 P.check(P.SHA.test(s.driver_fingerprint) && typeof s.driver_name==='string' && /cp[\s_-]*9550/i.test(s.driver_name),'cp9550_driver_required');
 P.check(typeof s.paper_name==='string' && s.paper_name.length<=256 && Number.isInteger(s.raw_kind) && typeof s.landscape==='boolean','invalid_driver_paper');
 const w=s.width_hundredths,h=s.height_hundredths,short=Math.min(w,h),long=Math.max(w,h);
 P.check(Number.isInteger(w)&&Number.isInteger(h)&&short>=390&&short<=430&&long>=590&&long<=640,'capture_4x6_sheet_not_single_2x6');
 return Object.fromEntries(['schema','printer','driver_name','driver_fingerprint','paper_name','raw_kind','width_hundredths','height_hundredths','landscape','devmode_base64','sha256'].map(k=>[k,s[k]]));
}
function normalize(list, previous=[]) {
 const safe=P.profiles(list);
 for(let i=0;i<safe.length;i++){
  const b=list[i],p=safe[i],preset=PRESETS.find(x=>x.id===p.id);
  P.check(typeof b.printer==='string'&&b.printer.length>0&&b.printer.length<=256,'invalid_printer');p.printer=b.printer;
  if(preset){
   P.check(b.model==='Mitsubishi CP9550DW' && b.layout===preset.layout && b.cut===preset.cut,'invalid_cp9550_preset');
   p.model=b.model;p.layout=preset.layout;p.cut=preset.cut;
   p.driver_snapshot=snapshot(b.driver_snapshot,p.printer);
   P.check(p.width_hundredths===(p.driver_snapshot.landscape?p.driver_snapshot.height_hundredths:p.driver_snapshot.width_hundredths)&&p.height_hundredths===(p.driver_snapshot.landscape?p.driver_snapshot.width_hundredths:p.driver_snapshot.height_hundredths),'profile_paper_snapshot_mismatch');
   P.check(b.driver_mode_confirmed===true,'confirm_driver_cut_mode');p.driver_mode_confirmed=true;
  }else{
   // Existing generic/ASK-300 profiles are preserved; they cannot smuggle a new layout.
   P.check(!b.layout&&!b.cut&&!b.driver_snapshot&&!b.model,'use_builtin_cp9550_profile');
  }
  const old=previous.find(x=>x.id===p.id);
  if(old&&P.stable(old)!==P.stable(p))P.check(p.revision>old.revision,'increment_profile_revision',409);
 }
 return safe;
}
module.exports={PRESETS,snapshot,normalize};
