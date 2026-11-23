package app.lensdrop.transfer;

/** Pure geometry and quantity rules. No driver settings or extra wire fields. */
public final class PrintProfilePolicy {
    private PrintProfilePolicy() {}
    public static boolean isPair(String id) { return "cp9550-strip-pair-auto".equals(id)||"cp9550-strip-pair-manual".equals(id); }
    public static boolean isCp9550(String id) { return isPair(id)||"cp9550-photo-4x6".equals(id); }
    public static int pieces(String id,int copies) { if(copies<1||copies>5)throw new IllegalArgumentException("copies");return copies*(isPair(id)?2:1); }
    public static void validateSource(String id,int width,int height) {
        if(width<=0||height<=0)throw new IllegalArgumentException("image dimensions");
        if(isPair(id)&&Math.abs((double)Math.max(width,height)/Math.min(width,height)-3.0)>0.03)
            throw new IllegalArgumentException("Choose ONE finished 2x6 strip JPEG (1:3 ratio), not a full photo or pre-paired sheet.");
    }
    // x, y, width, height, clockwise-quarter-turn (0 or 1), in nominal paper coordinates.
    public static double[][] plan(String id,int width,int height,double pageWidth,double pageHeight) {
        validateSource(id,width,height);if(pageWidth<=0||pageHeight<=0)throw new IllegalArgumentException("paper");
        int n=isPair(id)?2:1;double[][] out=new double[n][5];
        double cw=pageWidth,ch=pageHeight;if(n==2){if(pageWidth>=pageHeight)ch/=2;else cw/=2;}
        for(int i=0;i<n;i++){
            double x=n==2&&pageWidth<pageHeight?cw*i:0,y=n==2&&pageWidth>=pageHeight?ch*i:0;
            double normal=Math.min(cw/width,ch/height),rotated=Math.min(cw/height,ch/width);boolean rotate=rotated>normal+0.00001;
            double scale=rotate?rotated:normal,w=(rotate?height:width)*scale,h=(rotate?width:height)*scale;
            out[i]=new double[]{x+(cw-w)/2,y+(ch-h)/2,w,h,rotate?1:0};
        }
        return out;
    }
    public static String copyLabel(String id,int copies) {
        return copies+" sheet copy/copies"+(isPair(id)?" = "+pieces(id,copies)+" strips (two identical per sheet)":"");
    }
}
