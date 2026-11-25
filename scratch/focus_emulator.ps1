Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public class WinUtil {
    [DllImport("user32.dll")]
    public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")]
    public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
}
'@

$procs = Get-Process -Name qemu-system-x86_64, emulator -ErrorAction SilentlyContinue
foreach ($p in $procs) {
    Write-Output ("Process: " + $p.ProcessName + " ID: " + $p.Id + " MainWindowTitle: '" + $p.MainWindowTitle + "' MainWindowHandle: " + $p.MainWindowHandle)
    if ($p.MainWindowHandle -ne 0) {
        [WinUtil]::ShowWindow($p.MainWindowHandle, 9) # SW_RESTORE
        [WinUtil]::SetForegroundWindow($p.MainWindowHandle)
    }
}
