# 同时启动前后端
$backendJob = Start-Job -ScriptBlock {
    Set-Location "$using:PSScriptRoot\server"
    java -jar target/server-1.0.0.jar
}

$frontendJob = Start-Job -ScriptBlock {
    Set-Location "$using:PSScriptRoot\admin"
    npm run dev
}

Write-Host "后端 PID: $($backendJob.Id)  (http://localhost:8080)"
Write-Host "前端 PID: $($frontendJob.Id)  (http://localhost:5173)"
Write-Host "按 Ctrl+C 停止所有服务"

try {
    while ($true) {
        Start-Sleep -Seconds 1
    }
}
finally {
    Stop-Job $backendJob, $frontendJob
    Remove-Job $backendJob, $frontendJob
}
