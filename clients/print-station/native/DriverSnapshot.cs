using System.ComponentModel;
using System.Drawing.Printing;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

// Preserve the driver's own opaque settings through documented Windows/.NET APIs.
// No private Mitsubishi byte offsets or undocumented cutter commands are edited.
internal static class DriverSnapshot
{
    [StructLayout(LayoutKind.Sequential)] private struct DriverInfo2
    { public uint Version; public IntPtr Name, Environment, DriverPath, DataFile, ConfigFile; }
    [DllImport("winspool.drv",EntryPoint="OpenPrinterW",CharSet=CharSet.Unicode,SetLastError=true)]
    private static extern bool OpenPrinter(string name,out IntPtr handle,IntPtr defaults);
    [DllImport("winspool.drv",SetLastError=true)] private static extern bool ClosePrinter(IntPtr handle);
    [DllImport("winspool.drv",EntryPoint="GetPrinterDriverW",CharSet=CharSet.Unicode,SetLastError=true)]
    private static extern bool GetPrinterDriver(IntPtr printer,string? environment,uint level,IntPtr buffer,uint size,out uint needed);
    [DllImport("kernel32.dll",SetLastError=true)] private static extern IntPtr GlobalAlloc(uint flags,UIntPtr bytes);
    [DllImport("kernel32.dll",SetLastError=true)] private static extern IntPtr GlobalLock(IntPtr handle);
    [DllImport("kernel32.dll")] private static extern bool GlobalUnlock(IntPtr handle);
    [DllImport("kernel32.dll")] private static extern IntPtr GlobalFree(IntPtr handle);
    [DllImport("kernel32.dll")] private static extern UIntPtr GlobalSize(IntPtr handle);
    private static string Hex(byte[] bytes)=>Convert.ToHexString(bytes).ToLowerInvariant();
    private static string Text(IntPtr ptr)=>Marshal.PtrToStringUni(ptr)??"";
    private static string FileDigest(IntPtr ptr)
    {
        string file=Text(ptr);if(string.IsNullOrEmpty(file)||!File.Exists(file))throw new Exception("driver_file_unavailable");
        using var stream=File.OpenRead(file);return Hex(SHA256.HashData(stream));
    }
    private static (string Name,string Hash) Identity(string printer)
    {
        if(!OpenPrinter(printer,out var handle,IntPtr.Zero))throw new Win32Exception(Marshal.GetLastWin32Error());
        try {
            GetPrinterDriver(handle,null,2,IntPtr.Zero,0,out uint needed);
            if(needed<Marshal.SizeOf<DriverInfo2>()||needed>1024*1024)throw new Exception("driver_identity_unavailable");
            var buffer=Marshal.AllocHGlobal(checked((int)needed));
            try {
                if(!GetPrinterDriver(handle,null,2,buffer,needed,out _))throw new Win32Exception(Marshal.GetLastWin32Error());
                var info=Marshal.PtrToStructure<DriverInfo2>(buffer);string name=Text(info.Name);
                string material=info.Version+"\n"+name+"\n"+Text(info.Environment)+"\n"+FileDigest(info.DriverPath)+"\n"+FileDigest(info.DataFile)+"\n"+FileDigest(info.ConfigFile);
                return (name,Hex(SHA256.HashData(Encoding.UTF8.GetBytes(material))));
            }finally{Marshal.FreeHGlobal(buffer);}
        }finally{ClosePrinter(handle);}
    }
    private static void ValidateBytes(byte[] b)
    {
        if(b.Length<72||b.Length>131072)throw new Exception("invalid_driver_snapshot");
        int size=BitConverter.ToUInt16(b,68),extra=BitConverter.ToUInt16(b,70);
        if(size<72||size+extra!=b.Length)throw new Exception("invalid_devmode_length");
    }
    private static byte[] Read(IntPtr handle)
    {
        ulong available=GlobalSize(handle).ToUInt64();
        if(available<72||available>131072)throw new Exception("invalid_devmode_allocation");
        var p=GlobalLock(handle);if(p==IntPtr.Zero)throw new Win32Exception(Marshal.GetLastWin32Error());
        try{int length=(ushort)Marshal.ReadInt16(p,68)+(ushort)Marshal.ReadInt16(p,70);if(length<72||(ulong)length>available)throw new Exception("invalid_devmode_length");var b=new byte[length];Marshal.Copy(p,b,0,length);ValidateBytes(b);return b;}
        finally{GlobalUnlock(handle);}
    }
    internal static object Capture(string printer)
    {
        var settings=new PrinterSettings{PrinterName=printer,Copies=1};
        if(!settings.IsValid||settings.PrintToFile)throw new Exception("invalid_printer");
        var identity=Identity(printer);var page=settings.DefaultPageSettings;
        IntPtr h=settings.GetHdevmode(page);byte[] b;try{b=Read(h);}finally{GlobalFree(h);}
        return new {schema=1,printer,driver_name=identity.Name,driver_fingerprint=identity.Hash,paper_name=page.PaperSize.PaperName,raw_kind=page.PaperSize.RawKind,width_hundredths=page.PaperSize.Width,height_hundredths=page.PaperSize.Height,landscape=page.Landscape,devmode_base64=Convert.ToBase64String(b),sha256=Hex(SHA256.HashData(b))};
    }
    internal static void Apply(PrintDocument document,JsonElement s)
    {
        string printer=document.PrinterSettings.PrinterName;
        if(s.GetProperty("schema").GetInt32()!=1||s.GetProperty("printer").GetString()!=printer)throw new Exception("driver_snapshot_printer_mismatch");
        var identity=Identity(printer);
        if(identity.Name!=s.GetProperty("driver_name").GetString()||identity.Hash!=s.GetProperty("driver_fingerprint").GetString())throw new Exception("driver_changed_recapture_settings");
        byte[] b=Convert.FromBase64String(s.GetProperty("devmode_base64").GetString()??"");ValidateBytes(b);
        if(Hex(SHA256.HashData(b))!=s.GetProperty("sha256").GetString())throw new Exception("driver_snapshot_checksum_mismatch");
        var h=GlobalAlloc(0x0002,(UIntPtr)(uint)b.Length);if(h==IntPtr.Zero)throw new OutOfMemoryException();
        try{
            var p=GlobalLock(h);if(p==IntPtr.Zero)throw new Win32Exception(Marshal.GetLastWin32Error());
            try{Marshal.Copy(b,0,p,b.Length);}finally{GlobalUnlock(h);}
            document.PrinterSettings.SetHdevmode(h);document.DefaultPageSettings.SetHdevmode(h);
        }finally{GlobalFree(h);}
        var page=document.DefaultPageSettings;
        if(document.PrinterSettings.PrinterName!=printer||document.PrinterSettings.PrintToFile||page.PaperSize.RawKind!=s.GetProperty("raw_kind").GetInt32()||page.PaperSize.Width!=s.GetProperty("width_hundredths").GetInt32()||page.PaperSize.Height!=s.GetProperty("height_hundredths").GetInt32()||page.Landscape!=s.GetProperty("landscape").GetBoolean())throw new Exception("driver_did_not_restore_paper_settings");
    }
}
