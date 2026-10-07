#Requires -Version 5.1
<#
.SYNOPSIS
    冷冻海产品溯源系统 - 一键拉起 / 停止本地开发环境（MySQL → 后端 → 前端）。

.DESCRIPTION
    按依赖顺序启动三件套，并且每一步都做真实验证，而不是"进程起来了就算成功"：
      1. 数据库：确认 MySQL 服务在运行、3306 可连接、fsts_trace 已初始化；
      2. 后端  ：Jar 缺失或源码比 Jar 新时自动 mvn package，再以 dev profile 启动，轮询 /actuator/health 直到 UP；
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
    强制重新执行 mvn clean package。后端源码比现有 Jar 新时脚本会自动重建，
    只有需要"无条件重编"时才需要手动加这个开关。

.PARAMETER Wait
    值守模式：脚本自身保持前台不退出，关闭该窗口（或按 Ctrl+C）会自动停止本次启动的后端与前端，
    不会再留下"窗口关了、进程还在"的情况。双击 start-services.bat 时默认开启。

.PARAMETER SkipDatabase / SkipBackend / SkipFrontend
    跳过对应环节（例如只重启前端时用 -SkipDatabase -SkipBackend）。

.PARAMETER StopDatabase
    down 时连同 MySQL 服务一起停止。默认不停止，避免影响机器上其它项目。

.EXAMPLE
    pwsh -File start-services.ps1
    pwsh -File start-services.ps1 -Wait
    pwsh -File start-services.ps1 -Action status
    pwsh -File start-services.ps1 -Action down
    pwsh -File start-services.ps1 -InitDatabase -RebuildBackend

.NOTES
    建议用 PowerShell 7（pwsh）运行，中文输出更可靠；脚本本身兼容 Windows PowerShell 5.1。
#>
[CmdletBinding()]
param(
    [ValidateSet('up', 'down', 'status', 'restart', 'watchdog')]
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
    [switch]$PauseAtEnd,
    [switch]$Wait,

    # 内部参数：看门狗监听的主进程 PID（见 -Action watchdog）
    [int]$ParentPid = 0
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

function Get-PortOwnerText {
    param([int]$Port)

    $ownerIds = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique)
    $names = foreach ($procId in $ownerIds) {
        $process = Get-Process -Id $procId -ErrorAction SilentlyContinue
        if ($process) { $process.ProcessName + ' (PID ' + $procId + ')' } else { 'PID ' + $procId }
    }
    return ($names -join ', ')
}

function Get-PortOwnerCommandLine {
    param([int]$Port)

    $lines = foreach ($procId in @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique)) {
        $info = Get-CimInstance Win32_Process -Filter "ProcessId = $procId" -ErrorAction SilentlyContinue
        if ($info) { [string]$info.CommandLine }
    }
    return ($lines -join "`n")
}

function Test-BackendPortOwnerIsOurs {
    # 本项目的后端命令行里一定带 fsts（jar 名，或 IDE 运行时的 com.fsts 主类/类路径）；
    # 本机 8080 上可能同时跑着别的项目（例如云墨也是 Spring Boot + actuator），
    # 只靠 /actuator/health 返回 UP 会认错人，所以这里再确认一次进程身份。
    return ((Get-PortOwnerCommandLine -Port $BackendPort) -match '(?i)fsts')
}

function Get-BackendHealthStatus {
    # 本机 8080 可能同时跑着别的项目（它们对未知路径也会返回 HTTP 200 的错误包），
    # 所以只有拿到真正的 actuator health JSON 才算数。
    # /actuator/health 的 Content-Type 是 vnd.spring-boot.actuator.v3+json，
    # Invoke-WebRequest 会把 Content 还原成字节数组，这里用 Invoke-RestMethod 直接拿对象。
    $url = 'http://localhost:' + $BackendPort + '/actuator/health'
    try {
        $health = Invoke-RestMethod -Uri $url -TimeoutSec 5
    } catch {
        return $null
    }
    if (-not $health -or -not $health.status) { return $null }
    return [string]$health.status
}

function Test-BackendUp {
    return ((Get-BackendHealthStatus) -eq 'UP')
}

function Wait-BackendUp {
    param([int]$Seconds)

    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-BackendUp) { return $true }
        Start-Sleep -Milliseconds 1500
    }
    return (Test-BackendUp)
}

function Wait-PortReleased {
    param([int]$Port, [int]$Seconds)

    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        if (-not (Test-PortListening $Port)) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return (-not (Test-PortListening $Port))
}

function Invoke-NativeCommand {
    param([string]$FilePath, [string[]]$ArgumentList)

    # mvn / npm 会把警告写到 stderr，而 Windows PowerShell 5.1 在 Stop 偏好下会把原生命令的
    # stderr 当成 NativeCommandError 直接中断脚本；这里临时降级，并让输出照常打到控制台。
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $FilePath @ArgumentList | Out-Host
        return $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

function Get-LatestBackendSourceTime {
    $latest = Get-ChildItem -LiteralPath $BackendDir -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object {
            $_.FullName -notmatch '[\\/]target[\\/]' -and
            ($_.Extension -in @('.java', '.xml', '.yml', '.yaml', '.sql', '.properties'))
        } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($latest) { return $latest.LastWriteTime }
    return [datetime]::MinValue
}

function Get-JarHolderProcessIds {
    param([string]$JarPath)

    # Windows 会锁住正在运行的 Jar，边跑边重打包会失败，甚至把 fat jar 覆盖成瘦 jar
    $jarName = Split-Path -Leaf $JarPath
    return @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -and $_.CommandLine -like "*$jarName*" } |
        Select-Object -ExpandProperty ProcessId)
}

function Stop-FstsBackendForRebuild {
    # 先确认占用端口的是本项目的 Jar 进程，再停止它；绝不盲目杀端口占用者
    $ownerIds = @(Get-NetTCPConnection -State Listen -LocalPort $BackendPort -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique)

    foreach ($procId in $ownerIds) {
        $info = Get-CimInstance Win32_Process -Filter "ProcessId = $procId" -ErrorAction SilentlyContinue
        $commandLine = if ($info) { [string]$info.CommandLine } else { '' }
        if (-not $info -or $info.Name -ne 'java.exe' -or $commandLine -notmatch '(?i)fsts') {
            throw ('端口 ' + $BackendPort + ' 被非本项目进程占用（' + (Get-PortOwnerText -Port $BackendPort) + '），拒绝盲目终止，请先手动停止它。')
        }
        Write-Step ('停止旧后端进程 PID ' + $procId + '，准备重新编译')
        Stop-Process -Id $procId -Force -ErrorAction Stop
    }

    if ($ownerIds.Count -gt 0 -and -not (Wait-PortReleased -Port $BackendPort -Seconds 15)) {
        throw ('旧后端没有释放端口 ' + $BackendPort)
    }
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

    # 上一次会话可能还在前台值守：本次没接管的服务（复用/跳过）要把旧记录留着，
    # 否则那个窗口关闭时就找不到自己启动的进程了。
    $previous = Read-RunState
    if ($previous) {
        foreach ($name in @('backend', 'frontend')) {
            if ($State.$name -or -not $previous.$name) { continue }
            if (Test-RecordedEntryAlive -Entry $previous.$name) { $State.$name = $previous.$name }
        }
    }

    # 记下进程启动时间（ticks），回收时用它确认 PID 没有被别的进程复用。
    # 必须存字符串：JSON 会把 ISO 时间串自动解析成 DateTime，反而不好比对。
    foreach ($entry in @($State.backend, $State.frontend)) {
        if (-not $entry -or -not $entry.pid) { continue }
        $ticks = Get-ProcessStartTicks -ProcessId ([int]$entry.pid)
        if ($ticks) {
            $entry | Add-Member -NotePropertyName startTicks -NotePropertyValue $ticks -Force
        }
    }
    ($State | ConvertTo-Json -Depth 5) | Set-Content -LiteralPath $StateFile -Encoding UTF8
}

function Get-ProcessStartTicks {
    param([int]$ProcessId)

    $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if (-not $process) { return $null }
    try { return $process.StartTime.Ticks.ToString() } catch { return $null }
}

function Test-RecordedEntryAlive {
    param($Entry)

    if (-not $Entry -or -not $Entry.pid) { return $false }
    $process = Get-Process -Id ([int]$Entry.pid) -ErrorAction SilentlyContinue
    if (-not $process) { return $false }
    if ($Entry.startTicks) {
        $actualTicks = Get-ProcessStartTicks -ProcessId ([int]$Entry.pid)
        if ($actualTicks -and $actualTicks -ne ([string]$Entry.startTicks)) { return $false }
    }
    return $true
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
    Write-Step '执行 mvn clean package -DskipTests（输出较多，请稍候）'
    Push-Location $BackendDir
    try {
        $buildExitCode = Invoke-NativeCommand -FilePath $mvn -ArgumentList @('clean', 'package', '-DskipTests', '-q')
        if ($buildExitCode -ne 0) { throw '后端构建失败，请单独执行 mvn clean package -DskipTests 查看完整报错' }
    } finally {
        Pop-Location
    }
    if (-not (Test-Path -LiteralPath $JarPath)) { throw ('后端构建结束但没有生成 Jar：' + $JarPath) }
    $jarInfo = Get-Item -LiteralPath $JarPath
    Write-Ok ('后端 Jar 已重新生成（' + $jarInfo.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss') +
        '，' + [math]::Round($jarInfo.Length / 1MB, 1) + ' MB）')
}

function Start-Backend {
    param([string]$Password)

    Write-Title '2/3 后端（Spring Boot）'

    $portListening = Test-PortListening -Port $BackendPort
    $backendUp = $false

    if ($portListening) {
        # 端口被别的东西占着（例如另一个项目的后端也在 8080），先给结论，免得白跑一次编译
        if (-not (Test-BackendPortOwnerIsOurs)) {
            throw ('端口 ' + $BackendPort + ' 已被 ' + (Get-PortOwnerText -Port $BackendPort) +
                ' 占用，且该进程命令行里没有 fsts，不是本项目的后端。请先停止它再重试。')
        }
        $backendUp = Test-BackendUp
        if (-not $backendUp) {
            throw ('端口 ' + $BackendPort + ' 已被本项目的后端进程占用（' + (Get-PortOwnerText -Port $BackendPort) +
                '），但 /actuator/health 还不是 UP。请稍后重试，或先停止它。')
        }
    }

    $rebuildReason = $null
    if ($RebuildBackend) {
        $rebuildReason = '已指定 -RebuildBackend'
    } elseif (-not (Test-Path -LiteralPath $JarPath)) {
        $rebuildReason = '后端 Jar 不存在'
    } else {
        $sourceTime = Get-LatestBackendSourceTime
        if ($sourceTime -gt (Get-Item -LiteralPath $JarPath).LastWriteTime) {
            $rebuildReason = '后端源码比现有 Jar 新'
        }
    }

    if ($rebuildReason) {
        Write-Warn ($rebuildReason + '，本次自动重新编译 Jar')
        if ($portListening) { Stop-FstsBackendForRebuild }
        $jarHolders = @(Get-JarHolderProcessIds -JarPath $JarPath)
        if ($jarHolders.Count -gt 0) {
            throw ('Jar 仍被进程 ' + ($jarHolders -join ', ') +
                ' 占用（Windows 会锁住运行中的 Jar，重打包会失败）。请先停止这些进程再重试。')
        }
        Build-BackendJar
        $backendUp = $false
    } else {
        Write-Ok '后端 Jar 已是最新，跳过重新编译'
    }

    if ($backendUp) {
        # 端口上是本项目的健康后端：复用即可，但不纳入本脚本管理（down 不会去杀它）
        Write-Ok ('复用已运行的后端实例：http://localhost:' + $BackendPort + '/actuator/health 返回 UP（PID ' +
            (Get-PortOwner -Port $BackendPort) + '）')
        return $null
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
    if (-not (Wait-BackendUp -Seconds $TimeoutSeconds)) {
        if ($proc.HasExited) {
            Write-Err ('后端进程已退出（退出码 ' + $proc.ExitCode + '），日志末尾：')
        } else {
            Write-Err '后端健康检查未通过，错误日志末尾：'
        }
        foreach ($line in (Get-TailLines -Path $errLog -Count 15)) { Write-Host ('     ' + $line) -ForegroundColor DarkRed }
        foreach ($line in (Get-TailLines -Path $outLog -Count 15)) { Write-Host ('     ' + $line) -ForegroundColor DarkRed }
        throw ('后端启动失败，完整日志：' + $outLog + ' / ' + $errLog)
    }

    Write-Ok ('后端就绪：' + $healthUrl + ' 返回 UP')
    return [pscustomobject]@{
        pid    = $proc.Id
        port   = $BackendPort
        log    = $outLog
        errLog = $errLog
    }
}

# ---------------------------------------------------------------------------
# 前端
# ---------------------------------------------------------------------------
function Start-Frontend {
    Write-Title '3/3 前端（Vite + Vue 3）'

    if (Test-PortListening -Port $FrontendPort) {
        Write-Warn ('端口 ' + $FrontendPort + ' 已被 ' + (Get-PortOwnerText -Port $FrontendPort) +
            ' 占用，跳过启动（该进程不纳入本脚本管理）')
        return $null
    }

    $npm = Get-Command npm.cmd -ErrorAction SilentlyContinue
    if (-not $npm) { $npm = Get-Command npm -ErrorAction SilentlyContinue }
    if (-not $npm) { throw '未找到 npm，请安装 Node.js 20+ 并加入 PATH' }

    if (-not (Test-Path -LiteralPath (Join-Path $FrontendDir 'node_modules'))) {
        Write-Step 'node_modules 缺失，执行 npm install（首次约需 1 分钟）'
        Push-Location $FrontendDir
        try {
            $npmExitCode = Invoke-NativeCommand -FilePath $npm.Source -ArgumentList @('install', '--no-audit', '--no-fund')
            if ($npmExitCode -ne 0) { throw ('npm install 失败（退出码 ' + $npmExitCode + '）') }
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
    return [pscustomobject]@{
        pid    = $proc.Id
        port   = $FrontendPort
        log    = $outLog
        errLog = $errLog
    }
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
    if ($Wait) {
        Write-Host '  日志：.run\logs\   停止：关闭本窗口（或 Ctrl+C）会自动停止后端与前端' -ForegroundColor DarkGray
    } else {
        Write-Host '  日志：.run\logs\   停止：pwsh -File start-services.ps1 -Action down' -ForegroundColor DarkGray
    }
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
    $ownerText = ''
    if ($backendListening) {
        $ownerText = Get-PortOwnerText -Port $BackendPort
        $healthText = Get-BackendHealthStatus
        if (-not $healthText) {
            # 有响应但不是本项目的 actuator health（例如另一个项目也占着 8080）
            try {
                $resp = Invoke-WebRequest -UseBasicParsing -Uri ('http://localhost:' + $BackendPort + '/actuator/health') -TimeoutSec 5
                $healthText = 'HTTP ' + $resp.StatusCode + '，但不是本项目后端'
            } catch {
                $statusCode = $null
                if ($_.Exception.Response) { $statusCode = [int]$_.Exception.Response.StatusCode }
                if ($statusCode) { $healthText = 'HTTP ' + $statusCode + '（服务在跑，但依赖未就绪）' } else { $healthText = '无法连接' }
            }
        }
    }
    $backendLine = '  后端      端口' + $BackendPort + '=' + $backendListening + '  健康=' + $healthText
    if ($ownerText) { $backendLine += '  占用者=' + $ownerText }
    Write-Host $backendLine -ForegroundColor White

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
    $rootIds = @(Get-ManagedRootIds -State $state)

    if ($rootIds.Count -eq 0) {
        if ($state) {
            Write-Warn '记录的进程都已退出（或被 PID 复用检查跳过）；为避免误杀手工启动的服务，这里不做端口清理'
        } else {
            Write-Warn '没有本脚本记录的进程（.run\state.json 不存在）；为避免误杀手工启动的服务，这里不做端口清理'
        }
    } else {
        $stopped = Stop-ProcessTree -RootIds ($rootIds | Sort-Object -Unique)
        Write-Ok ('已停止进程 ' + (($stopped | Sort-Object -Unique) -join ', '))
    }

    foreach ($port in @($BackendPort, $FrontendPort)) {
        if (Test-PortListening -Port $port) {
            Write-Warn ('端口 ' + $port + ' 仍被 ' + (Get-PortOwnerText -Port $port) + ' 占用，可能是手工启动的进程，请自行确认后停止')
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
# 值守模式（关闭窗口即停止服务）
# ---------------------------------------------------------------------------
function Get-ManagedRootIds {
    param($State)

    # state.json 里的 PID 可能已经被其它进程复用（本机同时跑多个项目时很常见），
    # 所以只回收"启动时间也对得上"的进程，宁可漏杀也不误杀。
    $rootIds = New-Object 'System.Collections.Generic.List[int]'
    if (-not $State) { return $rootIds }

    foreach ($entry in @($State.backend, $State.frontend)) {
        if (-not $entry -or -not $entry.pid) { continue }
        $procId = [int]$entry.pid
        $process = Get-Process -Id $procId -ErrorAction SilentlyContinue
        if (-not $process) { continue }

        if ($entry.startTicks) {
            $actualTicks = Get-ProcessStartTicks -ProcessId $procId
            if ($actualTicks -and $actualTicks -ne ([string]$entry.startTicks)) {
                Write-Warn ('记录的 PID ' + $procId + ' 已被其它进程复用，本次跳过（不误杀）')
                continue
            }
        }
        $rootIds.Add($procId)
    }
    return $rootIds
}

function Start-ServiceWatchdog {
    param([int]$ParentProcessId)

    # 服务都在各自隐藏的控制台里，主窗口被直接关掉时 PowerShell 来不及清理，
    # 所以另开一个隐藏进程盯着主进程，它消失后执行同一套 -Action down 逻辑。
    # 看门狗窗口是隐藏的，把它的输出落到日志里，出问题时能查。
    $hostPath = (Get-Process -Id $PID).Path
    $arguments = @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $PSCommandPath,
        '-Action', 'watchdog', '-ParentPid', "$ParentProcessId"
    )
    $watchdogOutLog = New-LogPath 'watchdog-out'
    $watchdogErrLog = New-LogPath 'watchdog-err'
    return Start-Process -FilePath $hostPath -ArgumentList $arguments -WindowStyle Hidden `
        -RedirectStandardOutput $watchdogOutLog -RedirectStandardError $watchdogErrLog -PassThru
}

function Wait-ParentExit {
    param([int]$ParentProcessId)

    $parent = Get-Process -Id $ParentProcessId -ErrorAction SilentlyContinue
    if (-not $parent) { return }
    $parentStartTime = $null
    try { $parentStartTime = $parent.StartTime } catch { $parentStartTime = $null }

    while ($true) {
        Start-Sleep -Seconds 2
        $current = Get-Process -Id $ParentProcessId -ErrorAction SilentlyContinue
        if (-not $current) { return }
        if ($parentStartTime) {
            # PID 会被复用，用启动时间确认还是同一个进程
            $currentStartTime = $null
            try { $currentStartTime = $current.StartTime } catch { $currentStartTime = $null }
            if ($currentStartTime -and $currentStartTime -ne $parentStartTime) { return }
        }
    }
}

function Open-FrontendPage {
    $url = 'http://localhost:' + $FrontendPort + '/'
    Write-Host ('  正在打开浏览器：' + $url) -ForegroundColor Gray
    Start-Process $url
}

function Wait-UntilWindowClosed {
    Write-Host ''
    Write-Host '  值守模式：本窗口就是这次服务的生命周期' -ForegroundColor Yellow
    Write-Host '    关闭本窗口（或按 Ctrl+C）会自动停止本次启动的后端与前端' -ForegroundColor DarkGray
    Write-Host '    MySQL 属于系统服务，默认保持运行（需要停止用 -Action down -StopDatabase）' -ForegroundColor DarkGray

    $watchdog = $null
    try {
        $watchdog = Start-ServiceWatchdog -ParentProcessId $PID
    } catch {
        Write-Warn ('看门狗进程启动失败，直接关闭窗口时可能来不及停止服务：' + $_.Exception.Message)
    }

    try {
        while ($true) { Start-Sleep -Seconds 2 }
    } finally {
        if ($watchdog) { Stop-Process -Id $watchdog.Id -Force -ErrorAction SilentlyContinue }
        Stop-Services
    }
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

    if ($Wait) {
        # 先把浏览器开出来再值守，否则要等窗口关掉才会打开
        if ($OpenBrowser) { Open-FrontendPage }
        Wait-UntilWindowClosed
    }
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
    # 值守模式下浏览器已经在进入等待前打开过了，这里不再重复
    if ($OpenBrowser -and -not $Wait -and $ExitCode -eq 0) { Open-FrontendPage }
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
        'watchdog' {
            if (-not $ParentPid) { throw 'watchdog 动作需要 -ParentPid（由 -Wait 值守模式自动传递）。' }
            Wait-ParentExit -ParentProcessId $ParentPid
            Stop-Services
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
