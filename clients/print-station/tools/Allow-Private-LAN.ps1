# Run from an elevated Windows PowerShell only after verifying the package.
# Allows the station endpoint on Private networks and local subnets ONLY.
$ErrorActionPreference = 'Stop'
$node = Join-Path (Split-Path $PSScriptRoot -Parent) 'runtime\node.exe'
if (-not (Test-Path $node)) { throw 'Bundled runtime missing; extract the compiled Windows app first.' }
$name = 'LensDrop Print Station Private LAN'
$existing = Get-NetFirewallRule -DisplayName $name -ErrorAction SilentlyContinue
if ($existing) { $existing | Remove-NetFirewallRule }
New-NetFirewallRule -DisplayName $name -Direction Inbound -Action Allow -Protocol TCP -LocalPort 54173 -RemoteAddress LocalSubnet -Profile Private -Program $node | Out-Null
Write-Host 'Allowed TCP 54173 for this runtime on Private networks/local subnets only. No Public-network access enabled.'
