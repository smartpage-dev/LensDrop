package app.lensdrop.transfer;

import java.io.*;

/** Bounded marker/framing validation, not an entropy decoder. Leaves trailer bytes unchanged.
 * Supports 8-bit baseline/extended/progressive DCT JPEG. Cloud remains final decoder/receipt authority. */
final class JpegStructure {
    static final long MAX_BYTES=64L*1024*1024, MAX_PIXELS=60L*1024*1024;
    static final class Invalid extends IOException {
        final String code; final boolean incomplete;
        Invalid(String code,boolean incomplete){super(code);this.code=code;this.incomplete=incomplete;}
    }
    static void validate(File file)throws IOException {
        if(file.length()<4)throw new Invalid("jpeg_incomplete",true);
        if(file.length()>MAX_BYTES)throw new Invalid("jpeg_too_large",false);
        try(InputStream in=new BufferedInputStream(new FileInputStream(file),65536)) { validate(in,file.length()); }
    }
    static void validate(InputStream in,long size)throws IOException {
        if(size>MAX_BYTES)throw new Invalid("jpeg_too_large",false);
        Reader r=new Reader(in,size);if(r.u8()!=0xff||r.u8()!=0xd8)bad("jpeg_invalid_structure");
        boolean frame=false,scan=false;int marker=-1,segments=0;
        while(true){
            if(++segments>131072)bad("jpeg_structure_limit");
            if(marker<0){if(r.u8()!=0xff)bad("jpeg_invalid_structure");do{marker=r.u8();}while(marker==0xff);}
            if(marker==0xd9){if(!frame||!scan)bad("jpeg_invalid_structure");return;}
            if(marker==0||marker==0xd8||marker>=0xd0&&marker<=0xd7)bad("jpeg_invalid_structure");
            if(marker==0x01){marker=-1;continue;}
            int length=r.u16();if(length<2)bad("jpeg_invalid_structure");int payload=length-2;
            boolean sof=marker>=0xc0&&marker<=0xcf&&marker!=0xc4&&marker!=0xc8&&marker!=0xcc;
            if(sof){
                if(frame||!(marker==0xc0||marker==0xc1||marker==0xc2))bad("jpeg_unsupported_frame");
                if(payload<6)bad("jpeg_invalid_structure");
                int precision=r.u8(),height=r.u16(),width=r.u16(),components=r.u8();
                if(precision!=8)bad("jpeg_unsupported_precision");
                if(width==0||height==0||components<1||components>4||length!=8+3*components)bad("jpeg_invalid_structure");
                if((long)width*height>MAX_PIXELS)bad("jpeg_pixel_limit");
                r.skip(payload-6);frame=true;
            }else if(marker==0xda){
                if(!frame||payload<4)bad("jpeg_invalid_structure");
                int components=r.u8();if(components<1||components>4||length!=6+2*components)bad("jpeg_invalid_structure");
                r.skip(payload-1);scan=true;
                // Only recognize EOI/other markers outside segment payloads and stuffed entropy bytes.
                while(true){if(r.u8()!=0xff)continue;int code;do{code=r.u8();}while(code==0xff);
                    if(code==0||code>=0xd0&&code<=0xd7)continue;marker=code;break;}
                continue;
            }else r.skip(payload);
            marker=-1;
        }
    }
    private static void bad(String code)throws Invalid {throw new Invalid(code,false);}
    private static final class Reader {
        final InputStream in;final long limit;long n;
        Reader(InputStream in,long limit){this.in=in;this.limit=Math.min(MAX_BYTES,limit);}
        int u8()throws IOException{if(n>=limit)throw new Invalid("jpeg_incomplete",true);int b=in.read();if(b<0)throw new Invalid("jpeg_incomplete",true);n++;return b;}
        int u16()throws IOException{return (u8()<<8)|u8();}
        void skip(int bytes)throws IOException{for(int i=0;i<bytes;i++)u8();}
    }
}
