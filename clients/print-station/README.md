# H5.2 Windows Print Station

New station version: 0.1.1-alpha.h5.2. Read ../../docs/h5_2/CP9550DW_SETUP.html in the repository, or START_HERE.html in the built portable package. Three CP9550DW profiles replace the old single-profile limitation for new setup; existing configurations are preserved. Capture the exact driver's settings for each mode while paused, sync, then refresh Android permissions. One pair-copy means two strips. The software does not guarantee the cutter or Windows 11 compatibility. Full native rebuild and physical acceptance tests are required.

The historical limitations below describe H5 before this update. All non-profile restrictions still apply (unsigned portable app, no service, no physical-output proof). Run all current tests with `node --test --test-concurrency=1 clients/print-station/tests/*.test.cjs` from the repository root.

## Historical H5 notes

# LensDrop H5 Print Station - Windows source

No compiled executable is included in the source delivery. Run the manual **LensDrop Windows Print Station** GitHub workflow after uploading the full repository. It first tests the protocol core on Linux, then builds the .NET 10 Windows x64 tray application and packages a Node runtime. No physical printer test is implied.

After a successful build: fully extract the portable app, run LensDropStation.exe as the signed-in event user, pair the station with cloud Admin, configure the private IPv4 endpoint and installed printer, and begin in simulation. Closing the browser leaves the tray worker running. This is not a Windows service or an automatic-start installer. The shared Admin opens in your browser; local printer controls remain in the station dashboard.

Read the current guide at ../../docs/h5/LensDrop_H5_Setup_Guide.html in the repository, or START_HERE.html in the compiled package. Local data is separate from program files under LocalAppData/LuceStudio/LensDropPrintStation. Preserve this folder and its durable job ledger. Credentials use current-user DPAPI and the directory is restricted to the current Windows user and SYSTEM.

Port 54173 is the private HTTPS print endpoint; 54172 is loopback-only dashboard access. Firewall scripts only allow the bundled executable on Private networks/local subnets. Initial station/phone enrollment needs cloud access and Admin confirmation. Offline local printing uses a previously prepared 12-hour grant, not unlimited authorization.

Profiles: one full-photo Contain profile in the H5 UI. Physical output uses the installed driver's matching paper size. No forced borderless, cutter, frames, spooler job-ID tracking or automatic proof of output is implemented. Successful driver submission remains unconfirmed until an operator checks the paper. Unknown outcomes are never automatically retried.

Core tests require the full repo, Node 22.13+ (22.16 tested locally), cloud/sharp and OpenSSL:

```
node --test clients/print-station/tests/station.test.cjs
```

Local core tests do not compile or execute the Windows printing adapter. Do not publish this engineering alpha as a validated event product.
