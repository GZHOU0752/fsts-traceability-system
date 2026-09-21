<#
.SYNOPSIS
    冷冻海产品溯源系统 - 后端接口冒烟测试（对应《前后端接口文档》附录 G 联调验证清单）。

.DESCRIPTION
    对运行中的后端服务执行端到端校验，覆盖三端鉴权、基础数据、企业注册管理、
    统计大屏、批号状态机、进场确认、溯源码与消费者溯源链。

    前置条件：
      1. 已执行「数据库建表脚本.sql」「V2__add_sequence_table.sql」「sql/demo_data.sql」；
      2. 服务已启动（默认 http://localhost:8080）。

    退出码：0 全部通过；1 存在失败用例。

.EXAMPLE
    pwsh -File scripts/api-smoke-test.ps1
    pwsh -File scripts/api-smoke-test.ps1 -BaseUrl http://localhost:8080
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$DefaultPassword = '123456'
)

$ErrorActionPreference = 'Stop'
# 失败诊断会把响应对象转成 JSON，嵌套较深；统一提高序列化深度，避免出现截断告警
$PSDefaultParameterValues['ConvertTo-Json:Depth'] = 8
$script:Passed = 0
$script:Failed = 0
$script:Failures = @()

function Write-Case {
    param([string]$Name, [bool]$Ok, [string]$Detail = '')
    if ($Ok) {
        $script:Passed++
        Write-Host ("  [PASS] " + $Name) -ForegroundColor Green
    } else {
        $script:Failed++
        $script:Failures += $Name
        Write-Host ("  [FAIL] " + $Name + "  -> " + $Detail) -ForegroundColor Red
    }
}

function Write-Section {
    param([string]$Title)
    Write-Host ""
    Write-Host ("=== " + $Title + " ===") -ForegroundColor Cyan
}

<#
    统一请求方法：返回 @{ Code; Http; Body }
    - Code  : 业务状态码（JSON 的 code），解析失败时为 -1
    - Http  : HTTP 状态码
    - Body  : 原始响应文本
    不抛异常，便于断言 4xx 场景。
#>
function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Token = $null,
        [string]$JsonBody = $null
    )
    $uri = $BaseUrl + $Path
    $params = @{ Method = $Method; Uri = $uri; UseBasicParsing = $true }
    if ($Token) { $params.Headers = @{ Authorization = "Bearer $Token" } }
    if ($JsonBody) {
        $params.ContentType = 'application/json;charset=UTF-8'
        $params.Body = [System.Text.Encoding]::UTF8.GetBytes($JsonBody)
    }
    try {
        $resp = Invoke-WebRequest @params
        $text = ConvertTo-Text $resp.Content
        $code = -1
        try { $code = ($text | ConvertFrom-Json).code } catch { }
        return @{ Code = $code; Http = [int]$resp.StatusCode; Body = $text }
    } catch {
        $http = 0
        if ($_.Exception.Response) { $http = [int]$_.Exception.Response.StatusCode }
        $text = $_.ErrorDetails.Message
        if (-not $text) { $text = $_.Exception.Message }
        $code = -1
        try { $code = ($text | ConvertFrom-Json).code } catch { }
        return @{ Code = $code; Http = $http; Body = $text }
    }
}

<#
    响应正文归一化。
    部分端点（如 /actuator/health）返回的 Content-Type 不被 PowerShell 识别，
    此时 .Content 是 byte[] 而不是字符串；直接拿去匹配会得到数组，
    进而导致布尔参数类型错误。这里统一解码为 UTF-8 字符串。
#>
function ConvertTo-Text {
    param($Content)
    if ($null -eq $Content) { return '' }
    if ($Content -is [byte[]]) { return [System.Text.Encoding]::UTF8.GetString($Content) }
    return [string]$Content
}

function Convert-Body {
    param($Response)
    $text = ConvertTo-Text $Response.Body
    return ($text | ConvertFrom-Json)
}

function Get-Token {
    param([string]$LoginName, [string]$Password)
    $body = @{ loginName = $LoginName; password = $Password } | ConvertTo-Json -Compress
    $r = Invoke-Api -Method Post -Path '/api/enterprise/auth/login' -JsonBody $body
    if ($r.Code -ne 200) { throw ("企业登录失败: " + $LoginName + " -> " + $r.Body) }
    return (Convert-Body $r).data.token
}

Write-Host "冷冻海产品溯源系统 - 接口冒烟测试" -ForegroundColor Yellow
Write-Host ("目标服务: " + $BaseUrl)

# -----------------------------------------------------------------------------
Write-Section '附录 G-1 管理员登录'
$adminLogin = Invoke-Api -Method Post -Path '/api/admin/auth/login' -JsonBody '{"loginName":"admin","password":"123456"}'
$adminBody = Convert-Body $adminLogin
Write-Case '管理员登录成功，返回 Token' ($adminLogin.Code -eq 200 -and $adminBody.data.token) $adminLogin.Body
Write-Case '主键 userId 以字符串返回（防 JS 精度丢失）' ($adminLogin.Body -match '"userId":"') $adminLogin.Body
Write-Case 'expiresIn = 7200' ($adminBody.data.expiresIn -eq 7200) $adminLogin.Body
$adminToken = $adminBody.data.token

# -----------------------------------------------------------------------------
Write-Section '附录 G-2 大屏统计'
$ov = Convert-Body (Invoke-Api -Method Get -Path '/api/admin/statistics/overview' -Token $adminToken)
Write-Case '企业总数 = 16' ($ov.data.totalCount -eq 16) ($ov | ConvertTo-Json -Compress)
Write-Case '类型分布 5/3/5/3' ($ov.data.fishingCount -eq 5 -and $ov.data.processingCount -eq 3 -and $ov.data.wholesaleCount -eq 5 -and $ov.data.retailCount -eq 3) ($ov | ConvertTo-Json -Compress)
Write-Case '覆盖省份数 = 8' ($ov.data.provinceCount -eq 8) ($ov | ConvertTo-Json -Compress)

$trend = Convert-Body (Invoke-Api -Method Get -Path '/api/admin/statistics/register-trend?months=12' -Token $adminToken)
Write-Case '趋势横轴 12 个月连续' ($trend.data.months.Count -eq 12) ($trend | ConvertTo-Json -Compress)
Write-Case '趋势无数据月份已补 0（合计等于企业总数）' (($trend.data.counts | Measure-Object -Sum).Sum -eq 16) ($trend.data.counts -join ',')

$prov = Convert-Body (Invoke-Api -Method Get -Path '/api/admin/statistics/province-distribution?top=3' -Token $adminToken)
Write-Case '省分布 top=3 且含 percent' ($prov.data.Count -eq 3 -and $prov.data[0].percent -eq 25.00) ($prov | ConvertTo-Json -Compress)

$typeDist = Convert-Body (Invoke-Api -Method Get -Path '/api/admin/statistics/type-distribution' -Token $adminToken)
Write-Case '类型分布饼图返回 4 类且占比合计 100' ((($typeDist.data | ForEach-Object { $_.percent } | Measure-Object -Sum).Sum) -eq 100) ($typeDist | ConvertTo-Json -Compress)

# -----------------------------------------------------------------------------
Write-Section '附录 G-3 企业模糊查询（分页契约）'
$page = Convert-Body (Invoke-Api -Method Get -Path '/api/admin/enterprises?enterpriseName=%E6%B0%B4%E4%BA%A7&current=1&size=10' -Token $adminToken)
Write-Case '「水产」命中 7 家企业' ($page.data.total -eq 7) ($page | ConvertTo-Json -Compress)
Write-Case '分页 total/size/current 为数值类型' ((Invoke-Api -Method Get -Path '/api/admin/enterprises?current=1&size=10' -Token $adminToken).Body -match '"total":\d+,"size":10,"current":1')

# -----------------------------------------------------------------------------
Write-Section '附录 G-4 企业新建与唯一性'
$suffix = Get-Random -Minimum 100000 -Maximum 999999
$creditCode = '91310115MA9SMK' + $suffix
$createJson = '{"enterpriseName":"冒烟测试生鲜超市' + $suffix + '","enterpriseType":4,"loginName":"smoke_' + $suffix + '","creditCode":"' + $creditCode + '","provinceCode":"310000","cityCode":"310100","foodBusinessLicenseNo":"JY1310115SMK' + $suffix + '"}'
$created = Convert-Body (Invoke-Api -Method Post -Path '/api/admin/enterprises' -Token $adminToken -JsonBody $createJson)
Write-Case '新建企业返回 FSTS-E- 编码' ($created.code -eq 200 -and $created.data.enterpriseCode -match '^FSTS-E-\d{4}\d{4}$') ($created | ConvertTo-Json -Compress)
Write-Case '未传密码时回显初始密码 123456' ($created.data.initialPassword -eq '123456') ($created | ConvertTo-Json -Compress)

# 双击提交：同一报文在幂等窗口内再次提交，须被幂等守卫拦下
$doubleSubmit = Invoke-Api -Method Post -Path '/api/admin/enterprises' -Token $adminToken -JsonBody $createJson
Write-Case '完全相同的重复提交被幂等守卫拦截（409）' ($doubleSubmit.Code -eq 409) $doubleSubmit.Body

<#
    信用代码唯一约束：这里刻意换了企业名称与登录账号，只保留相同的信用代码。
    若报文完全相同，会先被幂等守卫拦成 409（双击提交场景），
    那样就测不到"信用代码全局唯一"这条业务规则了。
#>
$dupJson = '{"enterpriseName":"冒烟测试另一家超市' + $suffix + '","enterpriseType":3,"loginName":"smoke_dup_' + $suffix + '","creditCode":"' + $creditCode + '","provinceCode":"310000","cityCode":"310100","foodBusinessLicenseNo":"JY1310115DUP' + $suffix + '"}'
$dup = Invoke-Api -Method Post -Path '/api/admin/enterprises' -Token $adminToken -JsonBody $dupJson
Write-Case '复用已有信用代码返回 2002' ($dup.Code -eq 2002) $dup.Body

$check = Convert-Body (Invoke-Api -Method Get -Path ('/api/admin/enterprises/check-name?field=creditCode&value=91310115MA1H3M8P7C') -Token $adminToken)
Write-Case '唯一性校验接口 available=false' ($check.data.available -eq $false) ($check | ConvertTo-Json -Compress)

$badType = Invoke-Api -Method Post -Path '/api/admin/enterprises' -Token $adminToken -JsonBody '{"enterpriseName":"缺资质企业","enterpriseType":2,"loginName":"smoke_bad_type","creditCode":"91310115MA9BADTYPE1","provinceCode":"310000","cityCode":"310100"}'
Write-Case '冷冻加工企业缺生产许可证返回 400' ($badType.Code -eq 400) $badType.Body

# 清理：删除本次新建的测试企业
Invoke-Api -Method Delete -Path ('/api/admin/enterprises/' + $created.data.id) -Token $adminToken | Out-Null

# -----------------------------------------------------------------------------
Write-Section '基础数据接口'
$cities = Convert-Body (Invoke-Api -Method Get -Path '/api/common/regions/cities?provinceCode=370000')
Write-Case '市列表不返回 shortName（与文档示例一致）' ($cities.data[0].PSObject.Properties.Name -notcontains 'shortName') ($cities | ConvertTo-Json -Compress)
$dict = Convert-Body (Invoke-Api -Method Get -Path '/api/common/dicts/process_form')
Write-Case '字典按 sortNo 升序返回' ($dict.data[0].itemValue -eq '整鱼') ($dict | ConvertTo-Json -Compress)

# -----------------------------------------------------------------------------
Write-Section '附录 G-5/6 捕捞企业批号与上游选择'
$fishToken = Get-Token 'qd_yuanye' $DefaultPassword
$fishNew = Convert-Body (Invoke-Api -Method Get -Path '/api/enterprise/batches?batchStatus=1' -Token $fishToken)
Write-Case '捕捞企业「新建」分组为空' ($fishNew.data.total -eq 0) ($fishNew | ConvertTo-Json -Compress)
$fishPub = Convert-Body (Invoke-Api -Method Get -Path '/api/enterprise/batches?batchStatus=3' -Token $fishToken)
Write-Case '捕捞企业已发布分组仅 YY20260901001' ($fishPub.data.total -eq 1 -and $fishPub.data.records[0].batchNo -eq 'YY20260901001') ($fishPub | ConvertTo-Json -Compress)
$fishOff = Invoke-Api -Method Get -Path '/api/enterprise/batches?batchStatus=4' -Token $fishToken
Write-Case '已下架分组被拒绝（400）' ($fishOff.Code -eq 400) $fishOff.Body

$noUpstream = Invoke-Api -Method Get -Path '/api/enterprise/upstream/enterprises' -Token $fishToken
Write-Case '捕捞企业查上游返回 400' ($noUpstream.Code -eq 400) $noUpstream.Body

# -----------------------------------------------------------------------------
Write-Section '附录 G-7 批号唯一性校验'
$procToken = Get-Token 'yt_haizhen' $DefaultPassword
$dupBatch = Convert-Body (Invoke-Api -Method Get -Path '/api/enterprise/batches/check-batch-no?batchNo=JG20260905001' -Token $procToken)
Write-Case '本企业已存在批号 available=false' ($dupBatch.data.available -eq $false) ($dupBatch | ConvertTo-Json -Compress)
$freeBatch = Convert-Body (Invoke-Api -Method Get -Path ('/api/enterprise/batches/check-batch-no?batchNo=SMOKE' + $suffix) -Token $procToken)
Write-Case '未使用批号 available=true' ($freeBatch.data.available -eq $true) ($freeBatch | ConvertTo-Json -Compress)

$upList = Convert-Body (Invoke-Api -Method Get -Path '/api/enterprise/upstream/enterprises?provinceCode=370000&cityCode=370200' -Token $procToken)
Write-Case '按省市筛选上游企业命中青岛远洋' ($upList.data.total -eq 1 -and $upList.data.records[0].enterpriseName -eq '青岛远洋渔业有限公司') ($upList | ConvertTo-Json -Compress)
$upBatches = Convert-Body (Invoke-Api -Method Get -Path '/api/enterprise/upstream/batches?upstreamEnterpriseId=1' -Token $procToken)
Write-Case '上游企业批号下拉仅返回已发布批号' ($upBatches.data.total -eq 1 -and $upBatches.data.records[0].batchNo -eq 'YY20260901001') ($upBatches | ConvertTo-Json -Compress)

# -----------------------------------------------------------------------------
Write-Section '附录 G-8 重复发送确认请求'
$wholeToken = Get-Token 'sh_yonghai' $DefaultPassword
$dupSend = Invoke-Api -Method Post -Path '/api/enterprise/batches/8/confirm-request' -Token $wholeToken
Write-Case '对已待确认批号重复发送返回 409' ($dupSend.Code -eq 409) $dupSend.Body

# -----------------------------------------------------------------------------
Write-Section '附录 G-9 确认进场'
$pending = Convert-Body (Invoke-Api -Method Get -Path '/api/enterprise/confirm-requests?requestStatus=1' -Token $procToken)
Write-Case '烟台海珍看到上海甬海的待确认请求' ($pending.data.total -eq 1 -and $pending.data.records[0].requestNo -eq 'FSTS-CR-20260919-0001') ($pending | ConvertTo-Json -Compress)

$confirmed = Convert-Body (Invoke-Api -Method Put -Path '/api/enterprise/confirm-requests/2/confirm' -Token $procToken -JsonBody '{"handleRemark":"冒烟测试：同意确认"}')
Write-Case '确认后请求状态置 2' ($confirmed.code -eq 200 -and $confirmed.data.requestStatus -eq 2) ($confirmed | ConvertTo-Json -Compress)
Write-Case '确认后下游批号状态置 3' ($confirmed.data.batchStatus -eq 3) ($confirmed | ConvertTo-Json -Compress)

# -----------------------------------------------------------------------------
Write-Section '附录 G-10/11 溯源码与消费者溯源链'
$retailToken = Get-Token 'sh_jiaxian' $DefaultPassword
$traceCode = Convert-Body (Invoke-Api -Method Get -Path '/api/enterprise/batches/4/trace-code' -Token $retailToken)
Write-Case '零售商获取到 FSTS-20260915-SH-0001' ($traceCode.data.traceCode -eq 'FSTS-20260915-SH-0001') ($traceCode | ConvertTo-Json -Compress)

$trace = Convert-Body (Invoke-Api -Method Get -Path '/api/public/trace/FSTS-20260915-SH-0001')
Write-Case '溯源链返回 4 个环节' ($trace.data.links.Count -eq 4) ($trace | ConvertTo-Json -Compress)
Write-Case '环节顺序为 1->2->3->4' ((($trace.data.links | ForEach-Object { $_.stageCode }) -join ',') -eq '1,2,3,4') ($trace.data.links | ConvertTo-Json -Compress)
Write-Case '冷链结论为未断链' ($trace.data.coldChainQualified -eq $true) ($trace.data.coldChainConclusion)
Write-Case '温度曲线含 4 个数据点与阈值' ($trace.data.temperatureCurve.Count -eq 4 -and $trace.data.temperatureCurve[0].threshold -eq -18) ($trace.data.temperatureCurve | ConvertTo-Json -Compress)
Write-Case '不含企业联系电话（最小数据暴露）' (-not ($trace.Body -match 'contactPhone|contactPerson|loginName|password')) ''

# -----------------------------------------------------------------------------
Write-Section '附录 G-12 冷链异常'
$badTemp = Invoke-Api -Method Post -Path '/api/enterprise/batches' -Token $procToken -JsonBody ('{"batchNo":"SMOKE-TEMP-' + $suffix + '","upstreamEnterpriseId":"1","upstreamBatchId":"1","processForm":"鱼片","inspectionNo":"CYJ-SMOKE","quickFreezeTemp":-36.00,"factoryTemp":-16.50}')
Write-Case '出厂温度 -16.5 触发 2004' ($badTemp.Code -eq 2004) $badTemp.Body

# -----------------------------------------------------------------------------
Write-Section '附录 G-13/14 错误码与数据隔离'
$notFound = Invoke-Api -Method Get -Path '/api/public/trace/FSTS-0000'
Write-Case '错误标识码返回 3001' ($notFound.Code -eq 3001) $notFound.Body
$cross = Invoke-Api -Method Get -Path '/api/enterprise/batches/4' -Token $wholeToken
Write-Case '批发商查零售商批号返回 403' ($cross.Code -eq 403) $cross.Body
$noToken = Invoke-Api -Method Get -Path '/api/admin/enterprises'
Write-Case '无令牌返回 HTTP 401' ($noToken.Http -eq 401) $noToken.Body
$crossRole = Invoke-Api -Method Get -Path '/api/admin/enterprises' -Token $wholeToken
Write-Case '企业令牌访问管理端返回 HTTP 403' ($crossRole.Http -eq 403) $crossRole.Body
$noUpstreamTrace = Invoke-Api -Method Get -Path '/api/enterprise/batches/3/trace-code' -Token $procToken
Write-Case '非零售商查询溯源码返回 403' ($noUpstreamTrace.Code -eq 403) $noUpstreamTrace.Body

# -----------------------------------------------------------------------------
Write-Section '健康检查'
$health = Invoke-Api -Method Get -Path '/actuator/health'
Write-Case '服务健康状态 UP' ($health.Body -match '"status":"UP"') $health.Body

# -----------------------------------------------------------------------------
Write-Host ""
Write-Host ("总计: 通过 " + $script:Passed + " / 失败 " + $script:Failed) -ForegroundColor Yellow
if ($script:Failed -gt 0) {
    Write-Host "失败用例:" -ForegroundColor Red
    $script:Failures | ForEach-Object { Write-Host ("  - " + $_) -ForegroundColor Red }
    exit 1
}
Write-Host "全部用例通过。" -ForegroundColor Green
exit 0
