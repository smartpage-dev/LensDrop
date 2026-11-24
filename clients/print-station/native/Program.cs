using System.Diagnostics;
using System.Drawing;
using System.Windows.Forms;
using System.Drawing.Drawing2D;
using System.Drawing.Printing;
using System.Net;
using System.Net.NetworkInformation;
using System.Runtime.InteropServices;
using System.Security.AccessControl;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Security.Principal;
using System.Text;
using System.Text.Json;

internal static class Program
{
    [StructLayout(LayoutKind.Sequential)] private struct Blob { public int Length; public IntPtr Data; }
    [DllImport("crypt32.dll", SetLastError=true, CharSet=CharSet.Unicode)]
    private static extern bool CryptProtectData(ref Blob input, string description, IntPtr entropy, IntPtr reserved, IntPtr prompt, int flags, out Blob output);
    [DllImport("crypt32.dll", SetLastError=true, CharSet=CharSet.Unicode)]
    private static extern bool CryptUnprotectData(ref Blob input, IntPtr description, IntPtr entropy, IntPtr reserved, IntPtr prompt, int flags, out Blob output);
    [DllImport("kernel32.dll")] private static extern IntPtr LocalFree(IntPtr pointer);
    private static string Root => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "LuceStudio", "LensDropPrintStation");
    private static string TransformSecret(string value, bool protect)
    {
        byte[] bytes=protect?Encoding.UTF8.GetBytes(value):Convert.FromBase64String(value);
        var input=new Blob {Length=bytes.Length,Data=Marshal.AllocHGlobal(bytes.Length)}; Blob output;
        try { Marshal.Copy(bytes,0,input.Data,bytes.Length);
            bool ok=protect?CryptProtectData(ref input,"LensDrop station",IntPtr.Zero,IntPtr.Zero,IntPtr.Zero,1,out output):CryptUnprotectData(ref input,IntPtr.Zero,IntPtr.Zero,IntPtr.Zero,IntPtr.Zero,1,out output);
            if(!ok) throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
            try { byte[] result=new byte[output.Length]; Marshal.Copy(output.Data,result,0,result.Length); return protect?Convert.ToBase64String(result):Encoding.UTF8.GetString(result); }
            finally { LocalFree(output.Data); }
        } finally { Marshal.FreeHGlobal(input.Data); CryptographicOperations.ZeroMemory(bytes); }
    }
    private static void PrepareDirectory()
    {
        Directory.CreateDirectory(Root);
        var acl=new DirectorySecurity(); acl.SetAccessRuleProtection(true,false);
        var user=WindowsIdentity.GetCurrent().User ?? throw new Exception("Windows identity unavailable");
        acl.AddAccessRule(new FileSystemAccessRule(user,FileSystemRights.FullControl,InheritanceFlags.ContainerInherit|InheritanceFlags.ObjectInherit,PropagationFlags.None,AccessControlType.Allow));
        acl.AddAccessRule(new FileSystemAccessRule(new SecurityIdentifier(WellKnownSidType.LocalSystemSid,null),FileSystemRights.FullControl,InheritanceFlags.ContainerInherit|InheritanceFlags.ObjectInherit,PropagationFlags.None,AccessControlType.Allow));
        new DirectoryInfo(Root).SetAccessControl(acl);
        string certPath=Path.Combine(Root,"station-cert.pem"),keyPath=Path.Combine(Root,"station-key.pem");
        if(File.Exists(certPath)&&File.Exists(keyPath))return;
        if(File.Exists(certPath)||File.Exists(keyPath))throw new Exception("Incomplete station identity. Restore its matching certificate and key; do not silently rotate.");
        using RSA rsa=RSA.Create(3072);
        var req=new CertificateRequest("CN=LensDrop private print station",rsa,HashAlgorithmName.SHA256,RSASignaturePadding.Pkcs1);
        req.CertificateExtensions.Add(new X509BasicConstraintsExtension(false,false,0,true));
        req.CertificateExtensions.Add(new X509KeyUsageExtension(X509KeyUsageFlags.DigitalSignature|X509KeyUsageFlags.KeyEncipherment,true));
        var usages=new OidCollection();usages.Add(new Oid("1.3.6.1.5.5.7.3.1"));req.CertificateExtensions.Add(new X509EnhancedKeyUsageExtension(usages,true));
        var san=new SubjectAlternativeNameBuilder();san.AddDnsName("localhost");san.AddIpAddress(IPAddress.Loopback);
        foreach(var a in NetworkInterface.GetAllNetworkInterfaces().SelectMany(n=>n.GetIPProperties().UnicastAddresses)) if(a.Address.AddressFamily==System.Net.Sockets.AddressFamily.InterNetwork)san.AddIpAddress(a.Address);
        req.CertificateExtensions.Add(san.Build());
        using var cert=req.CreateSelfSigned(DateTimeOffset.UtcNow.AddMinutes(-5),DateTimeOffset.UtcNow.AddYears(2));
        File.WriteAllText(certPath,cert.ExportCertificatePem()); File.WriteAllText(keyPath,rsa.ExportPkcs8PrivateKeyPem());
    }
    private static void Printers()
    {
        var list=new List<object>();
        foreach(string name in PrinterSettings.InstalledPrinters){var p=new PrinterSettings{PrinterName=name};
            list.Add(new{name,papers=p.PaperSizes.Cast<PaperSize>().Select(x=>new{name=x.PaperName,width_hundredths=x.Width,height_hundredths=x.Height}).ToArray()});}
        Console.WriteLine(JsonSerializer.Serialize(list));
    }
    private static int Print(string filename)
    {
        bool invoked=false;
        try {
            using var data=JsonDocument.Parse(File.ReadAllText(filename));var j=data.RootElement;var p=j.GetProperty("profile");
            string job=j.GetProperty("id").GetString()??"",file=j.GetProperty("file").GetString()??"",printer=p.GetProperty("printer").GetString()??"";
            int copies=j.GetProperty("copies").GetInt32(),w=p.GetProperty("width_hundredths").GetInt32(),h=p.GetProperty("height_hundredths").GetInt32();
            if(!Guid.TryParse(job,out _)||copies<1||copies>5||!File.Exists(file)||new FileInfo(file).Length>32*1024*1024||p.GetProperty("fit").GetString()!="contain")throw new Exception("Invalid print descriptor");
            using Image image=Image.FromFile(file);
            if(image.RawFormat.Guid!=System.Drawing.Imaging.ImageFormat.Jpeg.Guid||(long)image.Width*image.Height>60L*1024*1024)throw new Exception("Unsupported image");
            if(image.PropertyIdList.Contains(0x112)){int orientation=BitConverter.ToUInt16(image.GetPropertyItem(0x112)!.Value!,0);
                var rotate=orientation switch{2=>RotateFlipType.RotateNoneFlipX,3=>RotateFlipType.Rotate180FlipNone,4=>RotateFlipType.Rotate180FlipX,5=>RotateFlipType.Rotate90FlipX,6=>RotateFlipType.Rotate90FlipNone,7=>RotateFlipType.Rotate270FlipX,8=>RotateFlipType.Rotate270FlipNone,_=>RotateFlipType.RotateNoneFlipNone};image.RotateFlip(rotate);}
            using var doc=new PrintDocument();doc.PrinterSettings.PrinterName=printer;
            if(!doc.PrinterSettings.IsValid)throw new Exception("Selected printer is not valid");
            bool captured=p.TryGetProperty("driver_snapshot",out var snapshot);
            bool pair=p.TryGetProperty("layout",out var layout)&&layout.GetString()=="strip_pair";
            if(captured){
                string preset=p.GetProperty("id").GetString()??"",cut=p.GetProperty("cut").GetString()??"";
                bool valid=(preset=="cp9550-photo-4x6"&&!pair&&cut=="none")||(preset=="cp9550-strip-pair-auto"&&pair&&cut=="driver_multicut")||(preset=="cp9550-strip-pair-manual"&&pair&&cut=="manual");
                if(!valid||!p.GetProperty("driver_mode_confirmed").GetBoolean())throw new Exception("Invalid driver preset");
                DriverSnapshot.Apply(doc,snapshot);
                if((doc.DefaultPageSettings.Landscape?doc.DefaultPageSettings.PaperSize.Height:doc.DefaultPageSettings.PaperSize.Width)!=w||(doc.DefaultPageSettings.Landscape?doc.DefaultPageSettings.PaperSize.Width:doc.DefaultPageSettings.PaperSize.Height)!=h)throw new Exception("Paper snapshot mismatch");
            }else{
                // Preserve previously accepted legacy profiles; no automatic printer migration.
                if(pair)throw new Exception("Strip profile requires a driver snapshot");
                PaperSize? paper=doc.PrinterSettings.PaperSizes.Cast<PaperSize>().FirstOrDefault(x=>Math.Abs(x.Width-w)<=5&&Math.Abs(x.Height-h)<=5);
                if(paper==null)throw new Exception("Driver has no matching paper size. Capture and verify the CP9550DW settings.");
                doc.DefaultPageSettings.PaperSize=paper;doc.DefaultPageSettings.Landscape=image.Width>image.Height;
            }
            // Validate the source before crossing the uncertain physical-submission boundary.
            PhotoLayout.Plan(image.Width,image.Height,new RectangleF(0,0,400,600),pair);
            doc.DocumentName="LensDrop:"+job;doc.PrinterSettings.Copies=(short)copies;
            doc.DefaultPageSettings.Margins=new Margins(0,0,0,0);doc.OriginAtMargins=false;
            doc.PrintController=new StandardPrintController();
            doc.PrintPage+=(s,e)=>{
                if(e.Graphics==null)throw new Exception("No printer graphics");var g=e.Graphics;
                g.PageUnit=GraphicsUnit.Display;g.InterpolationMode=InterpolationMode.HighQualityBicubic;g.CompositingQuality=CompositingQuality.HighQuality;
                var a=e.PageSettings.PrintableArea;
                var area=RectangleF.Intersect(new RectangleF(0,0,e.PageBounds.Width,e.PageBounds.Height),a);
                var placements=PhotoLayout.Plan(image.Width,image.Height,area,pair);
                var saved=g.Save();
                try{
                    // Printer Graphics starts at the hard margin. Work in physical-page coordinates.
                    g.TranslateTransform(-e.PageSettings.HardMarginX,-e.PageSettings.HardMarginY);
                    bool rotated=false;
                    foreach(var placement in placements){if(placement.Rotate90!=rotated){image.RotateFlip(placement.Rotate90?RotateFlipType.Rotate90FlipNone:RotateFlipType.Rotate270FlipNone);rotated=placement.Rotate90;}g.DrawImage(image,placement.Bounds);}
                    if(rotated)image.RotateFlip(RotateFlipType.Rotate270FlipNone);
                }finally{g.Restore(saved);}
                e.HasMorePages=false;
            };
            invoked=true;doc.Print();Console.WriteLine(JsonSerializer.Serialize(new{state="submitted_unconfirmed",document_name=doc.DocumentName}));return 0;
        } catch {Console.Error.WriteLine(invoked?"native_print_outcome_uncertain":"native_print_preflight_failed");return invoked?30:20;}
    }
    [STAThread] private static int Main(string[] args)
    {
        try {
            if(args.Length>0){switch(args[0]){case "--protect":Console.Write(TransformSecret(Console.In.ReadToEnd(),true));return 0;case "--unprotect":Console.Write(TransformSecret(Console.In.ReadToEnd(),false));return 0;case "--printers":Printers();return 0;case "--capture-settings":if(args.Length!=2)return 20;Console.WriteLine(JsonSerializer.Serialize(DriverSnapshot.Capture(args[1])));return 0;case "--layout-self-test":PhotoLayout.SelfTest();return 0;case "--print":return args.Length==2?Print(args[1]):20;case "--init":PrepareDirectory();return 0;}}
            using var single=new Mutex(true,"Local\\LuceStudio.LensDropPrintStation",out bool first);
            if(!first){var existing=Path.Combine(Root,"ui-session.txt");if(File.Exists(existing))Process.Start(new ProcessStartInfo(File.ReadAllText(existing).Trim()){UseShellExecute=true});return 0;}
            PrepareDirectory();ApplicationConfiguration.Initialize();using var app=new StationTray();Application.Run(app);return 0;
        }catch{if(args.Length==0)MessageBox.Show("LensDrop could not start. Check station.log in LocalAppData\\LuceStudio\\LensDropPrintStation. Do not delete the data folder or queue.","LensDrop Print Station");else Console.Error.WriteLine("station_native_error");return 1;}
    }
    private sealed class StationTray:ApplicationContext
    {
        private readonly NotifyIcon icon;private Process? worker;private readonly System.Windows.Forms.Timer timer;private bool opened;private readonly string session=Path.Combine(Root,"ui-session.txt");
        internal StationTray(){if(File.Exists(session))File.Delete(session);var menu=new ContextMenuStrip();menu.Items.Add("Open Print Station",null,(_,_)=>Open());menu.Items.Add("Open station data folder",null,(_,_)=>Process.Start(new ProcessStartInfo(Root){UseShellExecute=true}));menu.Items.Add("Exit (pause in dashboard first)",null,(_,_)=>Quit());icon=new NotifyIcon{Icon=SystemIcons.Application,Text="LensDrop Print Station H5.2",ContextMenuStrip=menu,Visible=true};icon.DoubleClick+=(_,_)=>Open();
            string node=Path.Combine(AppContext.BaseDirectory,"runtime","node.exe");if(!File.Exists(node))throw new Exception("Bundled Node runtime missing; use the GitHub-built Windows package.");
            var start=new ProcessStartInfo(node){UseShellExecute=false,CreateNoWindow=true,RedirectStandardOutput=true,RedirectStandardError=true,WorkingDirectory=AppContext.BaseDirectory};start.ArgumentList.Add(Path.Combine(AppContext.BaseDirectory,"src","main.cjs"));
            string log=Path.Combine(Root,"station.log");if(File.Exists(log)&&new FileInfo(log).Length>2*1024*1024)File.Move(log,log+".previous",true);
            worker=new Process{StartInfo=start};worker.OutputDataReceived+=(_,e)=>{if(e.Data!=null)Log(log,e.Data);};worker.ErrorDataReceived+=(_,e)=>{if(e.Data!=null)Log(log,e.Data);};worker.Start();worker.BeginOutputReadLine();worker.BeginErrorReadLine();
            timer=new System.Windows.Forms.Timer{Interval=750};timer.Tick+=(_,_)=>{if(!opened&&File.Exists(session)){opened=true;Open();}if(worker.HasExited){icon.Text="LensDrop station stopped - open log";timer.Stop();}};timer.Start();}
        private static readonly object LogLock=new();private static void Log(string file,string line){lock(LogLock){try{File.AppendAllText(file,DateTimeOffset.Now.ToString("O")+" "+line+Environment.NewLine);}catch{}}}
        private void Open(){if(File.Exists(session))Process.Start(new ProcessStartInfo(File.ReadAllText(session).Trim()){UseShellExecute=true});}
        private void Quit(){if(MessageBox.Show("Pause printing in the dashboard first. Exiting during printer submission may leave an uncertain job requiring review. Exit now?","LensDrop",MessageBoxButtons.YesNo)!=DialogResult.Yes)return;timer.Stop();if(worker!=null&&!worker.HasExited)worker.Kill(entireProcessTree:true);icon.Visible=false;icon.Dispose();worker?.Dispose();timer.Dispose();ExitThread();}
        protected override void Dispose(bool disposing){if(disposing){icon.Dispose();timer.Dispose();worker?.Dispose();}base.Dispose(disposing);}
    }
}
