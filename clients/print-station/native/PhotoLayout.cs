using System.Drawing;

// Geometry only. No device or driver dependency; exercised by --layout-self-test in Windows CI.
internal static class PhotoLayout
{
    internal readonly record struct Placement(RectangleF Bounds, bool Rotate90);
    internal static Placement[] Plan(int width, int height, RectangleF area, bool stripPair)
    {
        if (width<=0 || height<=0 || area.Width<=0 || area.Height<=0) throw new ArgumentException("invalid_layout_dimensions");
        if (stripPair && Math.Abs((double)Math.Max(width,height)/Math.Min(width,height)-3.0)>0.03)
            throw new ArgumentException("strip_source_must_be_finished_1_to_3_image");
        RectangleF[] cells;
        if (!stripPair) cells=new[]{area};
        else if (area.Width>=area.Height) cells=new[]{new RectangleF(area.X,area.Y,area.Width,area.Height/2),new RectangleF(area.X,area.Y+area.Height/2,area.Width,area.Height/2)};
        else cells=new[]{new RectangleF(area.X,area.Y,area.Width/2,area.Height),new RectangleF(area.X+area.Width/2,area.Y,area.Width/2,area.Height)};
        return cells.Select(cell=>{
            float normal=Math.Min(cell.Width/width,cell.Height/height),rotated=Math.Min(cell.Width/height,cell.Height/width);
            bool rotate=rotated>normal+0.00001f;float scale=rotate?rotated:normal;
            float dw=(rotate?height:width)*scale,dh=(rotate?width:height)*scale;
            return new Placement(new RectangleF(cell.X+(cell.Width-dw)/2,cell.Y+(cell.Height-dh)/2,dw,dh),rotate);
        }).ToArray();
    }
    internal static void SelfTest()
    {
        int count=0;void Check(bool value){count++;if(!value)throw new Exception("layout_self_test_failed_"+count);}
        var a=Plan(600,1800,new RectangleF(0,0,400,600),true);
        Check(a.Length==2);Check(a[0].Bounds==new RectangleF(0,0,200,600));Check(a[1].Bounds==new RectangleF(200,0,200,600));Check(!a[0].Rotate90);
        var b=Plan(600,1800,new RectangleF(0,0,600,400),true);
        Check(b[0].Rotate90);Check(b[0].Bounds==new RectangleF(0,0,600,200));Check(b[1].Bounds==new RectangleF(0,200,600,200));
        var c=Plan(1800,600,new RectangleF(0,0,400,600),true);Check(c[0].Rotate90);Check(c[1].Bounds==a[1].Bounds);
        var d=Plan(1800,1200,new RectangleF(5,7,390,590),false);Check(d.Length==1);Check(d[0].Rotate90);Check(d[0].Bounds.Left>=5&&d[0].Bounds.Top>=7);Check(d[0].Bounds.Right<=395.01&&d[0].Bounds.Bottom<=597.01);
        bool rejected=false;try{Plan(1200,1800,new RectangleF(0,0,400,600),true);}catch(ArgumentException){rejected=true;}Check(rejected);
        rejected=false;try{Plan(0,1,new RectangleF(0,0,400,600),false);}catch(ArgumentException){rejected=true;}Check(rejected);
        Console.WriteLine("PASS: "+count+" native geometry assertions; no printer accessed.");
    }
}
