$ErrorActionPreference = 'Stop'
Get-NetFirewallRule -DisplayName 'LensDrop Print Station Private LAN' -ErrorAction SilentlyContinue | Remove-NetFirewallRule
Write-Host 'LensDrop firewall rule removed. Program data and print queue were not changed.'
