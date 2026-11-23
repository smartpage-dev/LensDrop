package app.lensdrop.transfer;
import java.io.*;import java.awt.image.BufferedImage;import javax.imageio.*;import java.util.*;
/** Real desktop-generated JPEGs exercise framing only; not phone decoding or camera conformance. */
public final class JpegReliabilityTest {
 interface Check {void run()throws Exception;}static int passed;
 static void t(String name,Check c)throws Exception{c.run();System.out.println("PASS "+name);passed++;}
 static byte[] jpeg(boolean progressive)throws Exception{ByteArrayOutputStream o=new ByteArrayOutputStream();ImageWriter w=ImageIO.getImageWritersByFormatName("jpeg").next();ImageWriteParam p=w.getDefaultWriteParam();if(progressive)p.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);try(var stream=ImageIO.createImageOutputStream(o)){w.setOutput(stream);w.write(null,new IIOImage(new BufferedImage(60,40,BufferedImage.TYPE_INT_RGB),null,null),p);}finally{w.dispose();}return o.toByteArray();}
 static byte[] join(byte[]... items)throws IOException{ByteArrayOutputStream o=new ByteArrayOutputStream();for(byte[] a:items)o.write(a);return o.toByteArray();}
 static void good(byte[] b)throws Exception{JpegStructure.validate(new ByteArrayInputStream(b),b.length);}
 static void bad(byte[] b,String expected)throws Exception{try{good(b);}catch(JpegStructure.Invalid e){if(!e.code.equals(expected))throw new AssertionError(e.code+" != "+expected);return;}throw new AssertionError("Bad JPEG accepted");}
 static void yes(boolean b){if(!b)throw new AssertionError("Expected true");}
 static int marker(byte[] b,int value){for(int n=2;n<b.length-1;n++)if((b[n]&255)==255&&(b[n+1]&255)==value)return n;throw new AssertionError("No marker");}
 public static void main(String[] args)throws Exception{
  final byte[] base=jpeg(false),progressive=jpeg(true);
  t("Real baseline JPEG framing accepted",()->good(base));
  t("Real progressive multi-scan JPEG framing accepted",()->good(progressive));
  t("Trailer-bearing real JPEG accepted without changing original bytes",()->{byte[] b=join(base,new byte[]{1,2,3,4,0,(byte)255});byte[] copy=b.clone();good(b);yes(Arrays.equals(b,copy));yes(ImageIO.read(new ByteArrayInputStream(b))!=null);});
  t("Large appended payload does not require a full-image decode",()->good(join(base,new byte[1024*1024])));
  byte[] app={(byte)255,(byte)225,0,8,1,(byte)255,(byte)217,2,3,4};
  t("EOI inside metadata is skipped, not interpreted as image end",()->good(join(Arrays.copyOf(base,2),app,Arrays.copyOfRange(base,2,base.length))));
  t("Metadata EOI cannot rescue a truncated image",()->bad(join(Arrays.copyOf(base,2),app,Arrays.copyOfRange(base,2,base.length-2)),"jpeg_incomplete"));
  t("Truncated progressive image blocks as incomplete",()->bad(Arrays.copyOf(progressive,progressive.length-2),"jpeg_incomplete"));
  t("SOI followed by EOI without frame/scan rejected",()->bad(new byte[]{(byte)255,(byte)216,(byte)255,(byte)217},"jpeg_invalid_structure"));
  t("Non-JPEG header rejected",()->bad(new byte[]{1,2,3,4,5},"jpeg_invalid_structure"));
  t("Invalid segment length rejected",()->bad(new byte[]{(byte)255,(byte)216,(byte)255,(byte)225,0,1},"jpeg_invalid_structure"));
  t("Incomplete metadata segment rejected",()->bad(new byte[]{(byte)255,(byte)216,(byte)255,(byte)225,0,12,1,2},"jpeg_incomplete"));
  t("Oversized dimensions bounded before decoding",()->{byte[] b=base.clone();int m=marker(b,0xc0);b[m+5]=(byte)0xff;b[m+6]=(byte)0xff;b[m+7]=(byte)0xff;b[m+8]=(byte)0xff;bad(b,"jpeg_pixel_limit");});
  t("Unsupported precision is explicit",()->{byte[] b=base.clone();b[marker(b,0xc0)+4]=12;bad(b,"jpeg_unsupported_precision");});
  t("Unsupported lossless frame is explicit",()->{byte[] b=base.clone();b[marker(b,0xc0)+1]=(byte)0xc3;bad(b,"jpeg_unsupported_frame");});
  t("Byte-size guard fires without reading payload",()->{try{JpegStructure.validate(new ByteArrayInputStream(base),JpegStructure.MAX_BYTES+1);throw new AssertionError();}catch(JpegStructure.Invalid e){yes(e.code.equals("jpeg_too_large"));}});
  t("Source byte stream is bounded even when declared length is longer",()->bad(Arrays.copyOf(base,5),"jpeg_incomplete"));
  t("File-local readiness retries stop at fifth failure",()->{for(int i=0;i<4;i++)yes(UploadRetryPolicy.retry("jpeg_incomplete",true,i));yes(!UploadRetryPolicy.retry("jpeg_incomplete",true,4));});
  t("File errors never impose network cooldown",()->{for(String x:new String[]{"jpeg_incomplete","source_changed","source_read_retry","photo_permission_required","invalid_or_unsupported_jpeg"})yes(!UploadRetryPolicy.sharedCooldown(x,true));});
  t("Malformed files block immediately even on first failure",()->yes(!UploadRetryPolicy.retry("jpeg_invalid_structure",false,0)));
  t("Network backpressure retains shared cooldown without per-file retry exhaustion",()->{yes(UploadRetryPolicy.retry("http_503",true,100));yes(UploadRetryPolicy.sharedCooldown("http_503",true));});
  t("User pause is not a network outage",()->{yes(!UploadRetryPolicy.sharedCooldown("paused",true));yes(!UploadRetryPolicy.sharedCooldown("resource_or_user_pause",true));});
  System.out.println("PASSED "+passed+" JPEG/retry checks");
 }
}
