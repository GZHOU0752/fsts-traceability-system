#Requires -Version 5.1
<#
.SYNOPSIS
    冷冻海产品溯源系统 - 一键拉起 / 停止本地开发环境（MySQL → 后端 → 前端）。

.DESCRIPTION
    按依赖顺序启动三件套，并且每一步都做真实验证，而不是"进程起来了就算成功"：
      1. 数据库：确认 MySQL 服务在运行、3306 可连接、fsts_trace 已初始化；
      2. 后端  ：Jar 缺失时先 mvn package，再以 dev profile 启动，轮询 /actuator/health 直到 UP；
      3. 前端  ：node_modules 缺失时先 npm install，再启动 Vite，轮询首页可访问。

    后端与前端以隐藏窗口后台运行，日志写入 .run\logs\，PID 写入 .run\state.json，
    由 -Action down 统一回收，不会留下"看不见的"残留进程。
    这两个目录都在 .gitignore 中，日志与 PID 不会入库。

    口令安全：MySQL 口令只注入到子进程环境变量 FSTS_DB_PASSWORD，本脚本不会把它写进
    任何受版本控制的文件。仓库是公开的，请不要把口令硬编码进脚本。

.PARAMETER Action
    up       启动（默认）
    down     停止本脚本启动的后端与前端（数据库默认保留，见 -StopDatabase）
    status   查看当前状态（数据库 / 后端 / 前端）
    restart  先 down 再 up

.PARAMETER DbPassword
    MySQL 口令。不传时按以下顺序取：FSTS_DB_PASSWORD 环境变量 → 仓库根 .env.local → 交互式输入。
    想省去每次输入，可在仓库根建一个 .env.local（已被 .gitignore 忽略），内容一行：
        FSTS_DB_PASSWORD=你的口令

.PARAMETER InitDatabase
    执行「数据库建表脚本.sql」→「V2__add_sequence_table.sql」→「demo_data.sql」。
    注意：建表脚本会 DROP 并重建 12 张业务表，演示数据会覆盖现有业务数据，属于破坏性操作。

.PARAMETER RebuildBackend
    强制重新执行 mvn clean package（源码改动后需要，否则跑的还是旧 Jar）。

.PARAMETER SkipDatabase / SkipBackend / SkipFrontend
    跳过对应环节（例如只重启前端时用 -SkipDatabase -SkipBackend）。

.PARAMETER StopDatabase
    down 时连同 MySQL 服务一起停止。默认不停止，避免影响机器上其它项目。

.EXAMPLE
    pwsh -File start-services.ps1
    pwsh -File start-services.ps1 -Action status
    pwsh -File start-services.ps1 -Action down
    pwsh -File start-services.ps1 -InitDatabase -RebuildBackend

.NOTES
    建议用 PowerShell 7（pwsh）运行，中文输出更可靠；脚本本身兼容 Windows PowerShell 5.1。
#>
[CmdletBinding()]
param(
    [ValidateSet('up', 'down', 'status', 'restart')]
    [string]$Action = 'up',

    [string]$DbPassword,
    [string]$DbUser = 'root',
    [string]$MySqlService = 'MySQL80',
    [int]$BackendPort = 8080,
    [int]$FrontendPort = 5173,
    [int]$TimeoutSeconds = 150,

    [switch]$InitDatabase,
    [switch]$RebuildBackend,
    [switch]$SkipDatabase,
    [switch]$SkipBackend,
    [switch]$SkipFrontend,
    [switch]$StopDatabase,
    [switch]$OpenBrowser,
    [switch]$PauseAtEnd
)

$ErrorActionPreference = 'Stop'
# 用 UTF-8 把 SQL 喂给 mysql 客户端；Windows PowerShell 5.1 默认按 ASCII 发送，会把中文写坏
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)

# ---------------------------------------------------------------------------
# 路径与常量
# ---------------------------------------------------------------------------
$Root = $PSScriptRoot
$BackendDir = Join-Path $Root 'fsts-backend'
$FrontendDir = Join-Path $Root 'fsts-frontend'
$RunDir = Join-Path $Root '.run'
$LogDir = Join-Path $RunDir 'logs'
$StateFile = Join-Path $RunDir 'state.json'
$EnvFile = Join-Path $Root '.env.local'
$JarPath = Join-Path $BackendDir 'target\fsts-trace-backend.jar'
$SchemaScript = Join-Path $Root '冷冻海产品溯源系统-数据库建表脚本.sql'
$SequenceScript = Join-Path $BackendDir 'sql\V2__add_sequence_table.sql'
$DemoScript = Join-Path $BackendDir 'sql\demo_data.sql'
$Database = 'fsts_trace'
$DbHostPort = '127.0.0.1:3306'

# ---------------------------------------------------------------------------
# 输出助手
# ---------------------------------------------------------------------------
function Write-Title {
    param([string]$Text, [string]$Color = 'Cyan')
    Write-Host ''
    Write-Host ('=== ' + $Text + ' ===') -ForegroundColor $Color
}
function Write-Step { param([string]$Text) Write-Host ('  -> ' + $Text) -ForegroundColor Gray }
function Write-Ok { param([string]$Text) Write-Host ('  [OK] ' + $Text) -ForegroundColor Green }
function Write-Warn { param([string]$Text) Write-Host ('  [!!] ' + $Text) -ForegroundColor Yellow }
function Write-Err { param([string]$Text) Write-Host ('  [XX] ' + $Text) -ForegroundColor Red }

function Ensure-Directory {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { New-Item -ItemType Directory -Force -Path $Path | Out-Null }
}

function New-LogPath {
    param([string]$Name)
    Ensure-Directory $LogDir
    return (Join-Path $LogDir ($Name + '-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log'))
}

# ---------------------------------------------------------------------------
# 通用探测助手
# ---------------------------------------------------------------------------
function Test-PortListening {
    param([int]$Port)
    $conn = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
    return [bool]$conn
}

function Get-PortOwner {
    param([int]$Port)
    $conn = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($conn) { return [int]$conn.OwningProcess }
    return 0
}

function Wait-PortListening {
    param([int]$Port, [int]$Seconds)
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-PortListening $Port) { return $true }
        Start-Sleep -Milliseconds 1000
    }
    return (Test-PortListening $Port)
}

function Wait-HttpOk {
    param([string]$Url, [int]$Seconds)
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $resp = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 5
            if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 400) { return $true }
        } catch {
            # 后端 503（数据库未就绪）也会走到这里，属于"还没好"，继续轮询
        }
        Start-Sleep -Milliseconds 1500
    }
    return $false
}

function Get-TailLines {
    param([string]$Path, [int]$Count = 12)
    if (-not (Test-Path -LiteralPath $Path)) { return @() }
    return @(Get-Content -LiteralPath $Path -Tail $Count -ErrorAction SilentlyContinue)
}

# ---------------------------------------------------------------------------
# 状态文件
# ---------------------------------------------------------------------------
function Read-RunState {
    if (Test-Path -LiteralPath $StateFile) {
        try { return (Get-Content -LiteralPath $StateFile -Raw -Encoding UTF8 | ConvertFrom-Json) } catch { return $null }
    }
    return $null
}

function Save-RunState {
    param($State)
    Ensure-Directory $RunDir
    ($State | ConvertTo-Json -Depth 5) | Set-Content -LiteralPath $StateFile -Encoding UTF8
}

function Get-ProcessTree {
    param([int[]]$RootIds)
    $snapshot = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Select-Object ProcessId, ParentProcessId)
    $seen = New-Object 'System.Collections.Generic.HashSet[int]'
    $order = New-Object 'System.Collections.Generic.List[int]'
    $queue = New-Object 'System.Collections.Generic.Queue[int]'
    foreach ($procId in $RootIds) { if ($procId -gt 0) { $queue.Enqueue($procId) } }
    while ($queue.Count -gt 0) {
        $current = $queue.Dequeue()
        if (-not $seen.Add($current)) { continue }
        $order.Add($current)
        foreach ($child in ($snapshot | Where-Object { $_.ParentProcessId -eq $current })) {
            $queue.Enqueue([int]$child.ProcessId)
        }
    }
    return $order
}

function Stop-ProcessTree {
    param([int[]]$RootIds)
    $tree = @(Get-ProcessTree -RootIds $RootIds)
    $stopped = New-Object 'System.Collections.Generic.List[int]'
    # 倒序停止：先子后父，避免父进程退出后子进程变成孤儿
    for ($i = $tree.Count - 1; $i -ge 0; $i--) {
        $procId = $tree[$i]
        try {
            Stop-Process -Id $procId -Force -ErrorAction Stop
            $stopped.Add($procId)
        } catch {
            # 进程可能已自行退出
        }
    }
    return $stopped
}

# ---------------------------------------------------------------------------
# MySQL 相关
# ---------------------------------------------------------------------------
function Resolve-MySqlClient {
    $cmd = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $mysqlRoot = Join-Path $env:ProgramFiles 'MySQL'
    if (Test-Path -LiteralPath $mysqlRoot) {
        $found = Get-ChildItem -LiteralPath $mysqlRoot -Filter 'mysql.exe' -Recurse -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1
        if ($found) { return $found.FullName }
    }
    throw '未找到 mysql.exe，请把 MySQL 的 bin 目录加入 PATH，或安装 MySQL 8 客户端'
}

function Get-DbPasswordFromSources {
    # 只读取，不提示：供 status 这类不该打断用户的操作使用
    if ($DbPassword) { return $DbPassword }
    if ($env:FSTS_DB_PASSWORD) { return $env:FSTS_DB_PASSWORD }
    if (Test-Path -LiteralPath $EnvFile) {
        foreach ($line in (Get-Content -LiteralPath $EnvFile -Encoding UTF8 -ErrorAction SilentlyContinue)) {
            if ($line -match '^\s*FSTS_DB_PASSWORD\s*=\s*(.+?)\s*$') {
                return $Matches[1].Trim().Trim('"').Trim("'")
            }
        }
    }
    return $null
}

function Resolve-DbPassword {
    $known = Get-DbPasswordFromSources
    if ($known) { return $known }
    # stdin 被重定向（脚本调用、CI、管道）时 Read-Host 永远等不到输入，
    # 会静默挂死；这里提前失败并给出可执行的替代方案。
    if ([Console]::IsInputRedirected) {
        throw '缺少 MySQL 口令且当前无法交互输入。请改用 -DbPassword 参数、设置 FSTS_DB_PASSWORD 环境变量，或在仓库根创建 .env.local 写入 FSTS_DB_PASSWORD=你的口令'
    }
    Write-Host '  未检测到 MySQL 口令来源（参数 / 环境变量 / .env.local），请交互输入。' -ForegroundColor Yellow
    $secure = Read-Host -Prompt ('  请输入 MySQL 口令 (' + $DbUser + ')') -AsSecureString
    $credential = New-Object System.Management.Automation.PSCredential($DbUser, $secure)
    return $credential.GetNetworkCredential().Password
}

function Invoke-MySqlRaw {
    param([string]$Client, [string]$Password, [string]$Query)
    $env:MYSQL_PWD = $Password
    try {
        $output = & $Client -h 127.0.0.1 -P 3306 --protocol=TCP -u $DbUser --default-character-set=utf8mb4 -N -B -e $Query 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    }
    if ($exitCode -ne 0) { throw ('MySQL 查询失败：' + (($output | Out-String).Trim())) }
    return @($output)
}

function Invoke-SqlFile {
    param([string]$Client, [string]$Password, [string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { throw ('SQL 脚本不存在：' + $Path) }
    $env:MYSQL_PWD = $Password
    try {
        # 走标准输入而不是 source 命令：路径含中文时更稳，也不受客户端命令解析影响
        $sql = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
        $output = $sql | & $Client -h 127.0.0.1 -P 3306 --protocol=TCP -u $DbUser --default-character-set=utf8mb4 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    }
    if ($exitCode -ne 0) { throw ('SQL 脚本执行失败：' + (Split-Path $Path -Leaf) + "`n" + (($output | Out-String).Trim())) }
    return @($output)
}

function Start-Database {
    param([string]$Password)

    Write-Title '1/3 数据库（MySQL）'

    $service = Get-Service -Name $MySqlService -ErrorAction SilentlyContinue
    if (-not $service) {
        Write-Warn ('未找到 MySQL 服务 ' + $MySqlService + '，跳过服务检查，直接探测端口 ' + $DbHostPort)
    } elseif ($service.Status -ne 'Running') {
        Write-Step ('MySQL 服务当前为 ' + $service.Status + '，尝试启动')
        try {
            Start-Service -Name $MySqlService -ErrorAction Stop
            Write-Ok 'MySQL 服务已启动'
        } catch {
            throw ('无法启动 MySQL 服务：' + $_.Exception.Message + '。请以管理员身份运行 PowerShell，或在「服务」中手动启动 ' + $MySqlService)
        }
    } else {
        Write-Ok ('MySQL 服务 ' + $MySqlService + ' 已在运行')
    }

    if (-not (Wait-PortListening -Port 3306 -Seconds 45)) {
        throw ('MySQL 端口 3306 在超时内未进入监听状态')
    }
    Write-Ok '端口 3306 已监听'

    $client = Resolve-MySqlClient
    # 注意：PowerShell 会把单元素数组解包成标量，这里必须用 @() 包住再取 [0]，
    # 否则 "16" 会被当成字符串索引，取到第一个字符 "1"。
    $tableRows = @(Invoke-MySqlRaw -Client $client -Password $Password -Query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Database';")
    $tableCount = $tableRows[0].ToString().Trim()
    if ($tableCount -eq '0') {
        Write-Warn ('数据库 ' + $Database + ' 不存在或没有任何表，请加 -InitDatabase 执行建表脚本')
    } else {
        Write-Ok ('数据库 ' + $Database + ' 就绪，共 ' + $tableCount + ' 张表/视图')
    }
    return $client
}

function Initialize-Database {
    param([string]$Client, [string]$Password)

    Write-Title '初始化数据库（建表 + 增量 + 演示数据）' 'Magenta'
    Write-Warn '该操作会 DROP 重建 12 张业务表，并用演示数据覆盖现有业务数据'

    Write-Step '执行 数据库建表脚本.sql'
    Invoke-SqlFile -Client $Client -Password $Password -Path $SchemaScript | Out-Null
    Write-Ok '建表完成（12 张表 + 3 个视图 + 基础数据）'

    Write-Step '执行 V2__add_sequence_table.sql'
    Invoke-SqlFile -Client $Client -Password $Password -Path $SequenceScript | Out-Null
    Write-Ok '流水号表 sys_sequence 就绪'

    Write-Step '执行 demo_data.sql'
    $demoOutput = @(Invoke-SqlFile -Client $Client -Password $Password -Path $DemoScript)
    Write-Ok '演示数据导入完成'
    foreach ($line in $demoOutput) { Write-Host ('     ' + $line) -ForegroundColor DarkGray }
}

# ---------------------------------------------------------------------------
# 后端
# ---------------------------------------------------------------------------
function Resolve-Maven {
    $mvn = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if (-not $mvn) { $mvn = Get-Command mvn -ErrorAction SilentlyContinue }
    if (-not $mvn) { throw '未找到 Maven（mvn），请安装 Maven 3.9+ 并加入 PATH' }
    return $mvn.Source
}

function Build-BackendJar {
    $mvn = Resolve-Maven
    Write-Step '后端 Jar 缺失或要求重建，执行 mvn clean package -DskipTests'
    Push-Location $BackendDir
    try {
        & $mvn 'clean' 'package' '-DskipTests' '-q'
        if ($LASTEXITCODE -ne 0) { throw '后端构建失败，请单独执行 mvn clean package -DskipTests 查看完整报错' }
    } finally {
        Pop-Location
    }
    Write-Ok '后端 Jar 构建完成'
}

function Start-Backend {
    param([string]$Password)

    Write-Title '2/3 后端（Spring Boot）'

    if (Test-PortListening -Port $BackendPort) {
        Write-Warn ('端口 ' + $BackendPort + ' 已被 PID ' + (Get-PortOwner -Port $BackendPort) + ' 占用，跳过启动')
        return $null
    }

    if ($RebuildBackend -or -not (Test-Path -LiteralPath $JarPath)) {
        Build-BackendJar
    } else {
        $newestSource = Get-ChildItem -LiteralPath (Join-Path $BackendDir 'src') -Recurse -File -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($newestSource -and (Get-Item -LiteralPath $JarPath).LastWriteTime -lt $newestSource.LastWriteTime) {
            Write-Warn '后端源码比现有 Jar 新，本次仍使用旧 Jar；需要生效请加 -RebuildBackend'
        }
    }

    $java = (Get-Command java -ErrorAction Stop).Source
    $outLog = New-LogPath 'backend-out'
    $errLog = New-LogPath 'backend-err'

    # 口令只通过环境变量传给子进程，不落盘、不入库
    $env:FSTS_DB_PASSWORD = $Password
    $arguments = @('-jar', $JarPath, '--spring.profiles.active=dev', ('--server.port=' + $BackendPort))
    $proc = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $BackendDir `
        -WindowStyle Hidden -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru
    Write-Step ('后端进程已拉起 PID ' + $proc.Id + '，等待健康检查通过（最多 ' + $TimeoutSeconds + ' 秒）')

    $healthUrl = 'http://localhost:' + $BackendPort + '/actuator/health'
    if (-not (Wait-HttpOk -Url $healthUrl -Seconds $TimeoutSeconds)) {
        Write-Err '后端健康检查未通过，错误日志末尾：'
        foreach ($line in (Get-TailLines -Path $errLog -Count 15)) { Write-Host ('     ' + $line) -ForegroundColor DarkRed }
        foreach ($line in (Get-TailLines -Path $outLog -Count 15)) { Write-Host ('     ' + $line) -ForegroundColor DarkRed }
        throw ('后端启动失败，完整日志：' + $outLog + ' / ' + $errLog)
    }

    Write-Ok ('后端就绪：' + $healthUrl + ' 返回 UP')
    return [pscustomobject]@{ pid = $proc.Id; port = $BackendPort; log = $outLog; errLog = $errLog }
}

# ---------------------------------------------------------------------------
# 前端
# ---------------------------------------------------------------------------
function Start-Frontend {
    Write-Title '3/3 前端（Vite + Vue 3）'

    if (Test-PortListening -Port $FrontendPort) {
        Write-Warn ('端口 ' + $FrontendPort + ' 已被 PID ' + (Get-PortOwner -Port $FrontendPort) + ' 占用，跳过启动')
        return $null
    }

    $npm = Get-Command npm.cmd -ErrorAction SilentlyContinue
    if (-not $npm) { $npm = Get-Command npm -ErrorAction SilentlyContinue }
    if (-not $npm) { throw '未找到 npm，请安装 Node.js 20+ 并加入 PATH' }

    if (-not (Test-Path -LiteralPath (Join-Path $FrontendDir 'node_modules'))) {
        Write-Step 'node_modules 缺失，执行 npm install（首次约需 1 分钟）'
        Push-Location $FrontendDir
        try {
            & $npm.Source 'install' '--no-audit' '--no-fund'
            if ($LASTEXITCODE -ne 0) { throw 'npm install 失败' }
        } finally {
            Pop-Location
        }
        Write-Ok '前端依赖安装完成'
    }

    $outLog = New-LogPath 'frontend-out'
    $errLog = New-LogPath 'frontend-err'
    # --strictPort：端口被占时直接失败，而不是悄悄换到 5174 让后面的探测落空
    $arguments = @('run', 'dev', '--', '--port', $FrontendPort, '--strictPort')
    $proc = Start-Process -FilePath $npm.Source -ArgumentList $arguments -WorkingDirectory $FrontendDir `
        -WindowStyle Hidden -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru
    Write-Step ('前端进程已拉起 PID ' + $proc.Id + '，等待首页可访问')

    $url = 'http://localhost:' + $FrontendPort + '/'
    if (-not (Wait-HttpOk -Url $url -Seconds 90)) {
        Write-Err '前端未在超时内就绪，日志末尾：'
        foreach ($line in (Get-TailLines -Path $outLog -Count 15)) { Write-Host ('     ' + $line) -ForegroundColor DarkRed }
        foreach ($line in (Get-TailLines -Path $errLog -Count 15)) { Write-Host ('     ' + $line) -ForegroundColor DarkRed }
        throw ('前端启动失败，完整日志：' + $outLog + ' / ' + $errLog)
    }

    Write-Ok ('前端就绪：' + $url)
    return [pscustomobject]@{ pid = $proc.Id; port = $FrontendPort; log = $outLog; errLog = $errLog }
}

# ---------------------------------------------------------------------------
# 汇总 / 状态 / 停止
# ---------------------------------------------------------------------------
function Show-Summary {
    param($Backend, $Frontend, [string]$Password)

    $state = Read-RunState
    $backendPid = if ($Backend) { $Backend.pid } elseif ($state -and $state.backend) { $state.backend.pid } else { '外部进程' }
    $frontendPid = if ($Frontend) { $Frontend.pid } elseif ($state -and $state.frontend) { $state.frontend.pid } else { '外部进程' }

    Write-Title '服务已就绪' 'Green'
    Write-Host ('  数据库   ' + $Database + ' @ ' + $DbHostPort + '  服务 ' + $MySqlService) -ForegroundColor White
    Write-Host ('  后端     http://localhost:' + $BackendPort + '   (PID ' + $backendPid + ', /actuator/health)') -ForegroundColor White
    Write-Host ('  前端     http://localhost:' + $FrontendPort + '        (PID ' + $frontendPid + ')') -ForegroundColor White
    Write-Host ''
    Write-Host '  页面入口：' -ForegroundColor White
    Write-Host ('    管理端登录   http://localhost:' + $FrontendPort + '/admin/login        admin / 123456') -ForegroundColor Gray
    Write-Host ('    企业端登录   http://localhost:' + $FrontendPort + '/enterprise/login   sh_yonghai / 123456 等') -ForegroundColor Gray
    Write-Host ('    消费者溯源   http://localhost:' + $FrontendPort + '/trace/FSTS-20260915-SH-0001') -ForegroundColor Gray
    Write-Host ''
    Write-Host '  日志：.run\logs\   停止：pwsh -File start-services.ps1 -Action down' -ForegroundColor DarkGray
}

function Show-Status {
    Write-Title '当前服务状态'

    # 数据库
    $service = Get-Service -Name $MySqlService -ErrorAction SilentlyContinue
    $dbState = if ($service) { $service.Status.ToString() } else { '未安装' }
    Write-Host ('  数据库    服务=' + $dbState + '  端口3306=' + (Test-PortListening -Port 3306)) -ForegroundColor White

    $password = Get-DbPasswordFromSources
    if ($password -and (Test-PortListening -Port 3306)) {
        try {
            $client = Resolve-MySqlClient
            $countRows = @(Invoke-MySqlRaw -Client $client -Password $password -Query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Database';")
            $count = $countRows[0].ToString().Trim()
            Write-Host ('             ' + $Database + ' 表/视图数量=' + $count) -ForegroundColor Gray
        } catch {
            Write-Warn ('数据库探测失败：' + $_.Exception.Message)
        }
    } else {
        Write-Host '             （未提供口令，跳过库内探测）' -ForegroundColor DarkGray
    }

    # 后端
    $backendListening = Test-PortListening -Port $BackendPort
    $healthText = '未启动'
    if ($backendListening) {
        try {
            # /actuator/health 的 Content-Type 是 vnd.spring-boot.actuator.v3+json，
            # Invoke-WebRequest 会把 Content 还原成字节数组，这里用 Invoke-RestMethod 直接拿对象
            $health = Invoke-RestMethod -Uri ('http://localhost:' + $BackendPort + '/actuator/health') -TimeoutSec 5
            $healthText = $health.status
        } catch {
            $statusCode = $null
            if ($_.Exception.Response) { $statusCode = [int]$_.Exception.Response.StatusCode }
            if ($statusCode) { $healthText = 'HTTP ' + $statusCode + '（服务在跑，但依赖未就绪）' } else { $healthText = '无法连接' }
        }
    }
    Write-Host ('  后端      端口' + $BackendPort + '=' + $backendListening + '  健康=' + $healthText) -ForegroundColor White

    # 前端
    $frontendListening = Test-PortListening -Port $FrontendPort
    $frontText = '未启动'
    if ($frontendListening) {
        try {
            $resp = Invoke-WebRequest -UseBasicParsing -Uri ('http://localhost:' + $FrontendPort + '/') -TimeoutSec 5
            $frontText = 'HTTP ' + $resp.StatusCode
        } catch {
            $frontText = '响应异常'
        }
    }
    Write-Host ('  前端      端口' + $FrontendPort + '=' + $frontendListening + '  ' + $frontText) -ForegroundColor White

    $state = Read-RunState
    if ($state) { Write-Host ('  上次启动  ' + $state.startedAt) -ForegroundColor DarkGray }
}

function Stop-Services {
    Write-Title '停止服务'

    $state = Read-RunState
    $rootIds = @()
    if ($state) {
        if ($state.backend -and $state.backend.pid) { $rootIds += [int]$state.backend.pid }
        if ($state.frontend -and $state.frontend.pid) { $rootIds += [int]$state.frontend.pid }
    }

    if ($rootIds.Count -eq 0) {
        Write-Warn '没有本脚本记录的进程（.run\state.json 不存在）；为避免误杀手工启动的服务，这里不做端口清理'
    } else {
        $stopped = Stop-ProcessTree -RootIds ($rootIds | Sort-Object -Unique)
        Write-Ok ('已停止进程 ' + (($stopped | Sort-Object -Unique) -join ', '))
    }

    foreach ($port in @($BackendPort, $FrontendPort)) {
        if (Test-PortListening -Port $port) {
            Write-Warn ('端口 ' + $port + ' 仍被 PID ' + (Get-PortOwner -Port $port) + ' 占用，可能是手工启动的进程，请自行确认后停止')
        }
    }

    if ($StopDatabase) {
        $service = Get-Service -Name $MySqlService -ErrorAction SilentlyContinue
        if ($service -and $service.Status -eq 'Running') {
            try {
                Stop-Service -Name $MySqlService -Force -ErrorAction Stop
                Write-Ok ('MySQL 服务 ' + $MySqlService + ' 已停止')
            } catch {
                Write-Warn ('停止 MySQL 服务失败（通常需要管理员权限）：' + $_.Exception.Message)
            }
        }
    } else {
        Write-Host '  数据库    保持运行（如需一并停止请加 -StopDatabase）' -ForegroundColor DarkGray
    }

    if (Test-Path -LiteralPath $StateFile) { Remove-Item -LiteralPath $StateFile -Force }
}

# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------
function Invoke-Up {
    Assert-Prerequisites

    $password = $null
    $client = $null
    if (-not $SkipDatabase) {
        $password = Resolve-DbPassword
        $client = Start-Database -Password $password
        if ($InitDatabase) { Initialize-Database -Client $client -Password $password }
    } else {
        Write-Step '按参数要求跳过数据库检查'
        # 后端仍需要口令：优先取已有来源，取不到就提示
        $password = Get-DbPasswordFromSources
        if (-not $password -and -not $SkipBackend) { throw '跳过数据库检查时，后端仍需 MySQL 口令：请用 -DbPassword 或设置 FSTS_DB_PASSWORD' }
    }

    $state = [pscustomobject]@{
        startedAt = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
        backend   = $null
        frontend  = $null
    }

    if (-not $SkipBackend) {
        $state.backend = Start-Backend -Password $password
    } else {
        Write-Step '按参数要求跳过后端'
    }

    if (-not $SkipFrontend) {
        $state.frontend = Start-Frontend
    } else {
        Write-Step '按参数要求跳过前端'
    }

    Save-RunState -State $state
    Show-Summary -Backend $state.backend -Frontend $state.frontend -Password $password
}

function Assert-Prerequisites {
    if (-not (Test-Path -LiteralPath $BackendDir)) { throw ('未找到后端目录：' + $BackendDir) }
    if (-not (Test-Path -LiteralPath $FrontendDir)) { throw ('未找到前端目录：' + $FrontendDir) }
    if (-not (Get-Command java -ErrorAction SilentlyContinue) -and -not $SkipBackend) {
        throw '未找到 java，请安装 JDK 17+ 并加入 PATH'
    }
}

function Complete-Run {
    param([int]$ExitCode)
    if ($OpenBrowser -and $ExitCode -eq 0) {
        $url = 'http://localhost:' + $FrontendPort + '/'
        Write-Host ('  正在打开浏览器：' + $url) -ForegroundColor Gray
        Start-Process $url
    }
    if ($PauseAtEnd) {
        Write-Host ''
        # 没有控制台（例如被 CI 或重定向调用）时 Read-Host 会抛错，这里降级为短暂停顿
        try { [void](Read-Host '  按回车键关闭本窗口') } catch { Start-Sleep -Seconds 1 }
    }
    exit $ExitCode
}

# 统一兜底：把异常转成一行可读的中文提示 + 退出码，而不是甩一段 PowerShell 堆栈
try {
    switch ($Action) {
        'up' { Invoke-Up }
        'down' { Stop-Services }
        'status' { Show-Status }
        'restart' {
            Stop-Services
            Start-Sleep -Seconds 2
            Invoke-Up
        }
    }
    Complete-Run -ExitCode 0
} catch {
    Write-Host ''
    Write-Err $_.Exception.Message
    $firstFrame = ($_.ScriptStackTrace -split "`n" | Select-Object -First 1)
    if ($firstFrame) { Write-Host ('    出错位置：' + $firstFrame.Trim()) -ForegroundColor DarkGray }
    Write-Host '    服务日志：.run\logs\' -ForegroundColor DarkGray
    Complete-Run -ExitCode 1
}
