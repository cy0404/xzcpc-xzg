# 重新打包后端
$mvnPath = "$env:LOCALAPPDATA\Temp\apache-maven-3.9.6\bin\mvn"

Write-Host ">>> 检查并停止旧后端进程..."
$process = Get-Process -Name "java" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like "*server-1.0.0.jar*" }
if ($process) {
    Stop-Process -Id $process.Id -Force
    Write-Host "已停止旧后端进程 (PID: $($process.Id))"
    Start-Sleep 1
}
else {
    Write-Host "没有运行中的后端进程"
}

Write-Host ">>> 开始打包..."
Set-Location "$PSScriptRoot"
& $mvnPath clean package -DskipTests

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n打包完成，执行 .\start-backend.ps1 启动后端"
}
else {
    Write-Host "`n打包失败，请检查上方错误信息"
    exit 1
}
