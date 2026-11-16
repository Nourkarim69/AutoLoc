# Sends requests to AutoLoc so that the Grafana panels move (TP step 8 "Generate traffic and observe").
# Usage (PowerShell, app running):  .\monitoring\generate-traffic.ps1            (default: 200 rounds)
#                                   .\monitoring\generate-traffic.ps1 -Rounds 500
param([int]$Rounds = 200, [string]$BaseUrl = "http://localhost:8081")

$student = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("student:student123"))
$headers = @{ Authorization = "Basic $student" }

for ($i = 1; $i -le $Rounds; $i++) {
    foreach ($path in "/api/public/ping", "/api/vehicules", "/api/agences", "/api/equipements") {
        try { Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl$path" | Out-Null } catch { }
    }
    try { Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/api/hello" -Headers $headers | Out-Null } catch { }
    try { Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/api/admin/report" -Headers $headers | Out-Null } catch { }  # 403 on purpose
    if ($i % 50 -eq 0) { Write-Host "$i / $Rounds rounds sent" }
}
Write-Host "Done. Open Grafana: http://localhost:3000 (dashboard AutoLoc - Application Monitoring)"
