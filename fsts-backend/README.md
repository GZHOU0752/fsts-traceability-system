# 冷冻海产品溯源系统 · 后端服务（FSTS Backend）

冷冻海产品溯源系统（FSTS，Frozen Seafood Traceability System）的后端实现，
严格实现《冷冻海产品溯源系统-前后端接口文档》定义的 **39 个接口**，
覆盖系统管理端、流通节点端、消费者端三端业务。

---

## 一、当前状态

| 项目 | 状态 | 说明 |
| --- | --- | --- |
| 代码实现 | 完成 | 154 个主代码文件 + 3 个测试类，39 个接口全部落地 |
| 编译 | 通过 | `mvn clean package` 产出可执行 Jar |
| Spring 上下文装配 | 已验证 | `ApplicationContextTest` 校验 Bean 装配、13 个 Mapper 扫描、XML 映射解析 |
| 单元测试 | 通过 | 7 项：种子密码 BCrypt、序列化契约、缓存往返、令牌唯一性 |
| 真实 MySQL 联调 | **已完成** | MySQL 8.0.42 实跑，`scripts/api-smoke-test.ps1` 45 项断言全部通过 |
| 演示数据脚本 | 已提供 | `sql/demo_data.sql`，16 家企业 / 7 个批号 / 完整溯源链 |
| 并发正确性 | 已验证 | 20 个并发请求取号：单号唯一、号段连续、零重号 |
| 性能压测 | **未进行** | 高并发设计已落地，但未做吞吐/延迟压测 |

**结论**：接口功能已端到端验证通过，可用于联调与验收。
但**尚未做性能压测**，高并发相关的设计（限流、热点写聚合、缓存）只验证了正确性，
没有实测吞吐与延迟上限，投产前应补压测。

### 联调中发现并修复的 5 个缺陷

以下问题编译期、上下文启动、单元测试都无法发现，只有连真实数据库跑接口才会暴露：

| # | 现象 | 根因 | 修复 |
| --- | --- | --- | --- |
| 1 | 企业列表返回 `500 反序列化失败` | Lombok `@Builder` 使 `@Data` 不再生成无参构造器，Jackson 无法反序列化缓存中的 VO；首次未命中不报错，第二次读缓存才炸 | 缓存用 VO 加 `@Jacksonized` |
| 2 | 重复发送确认请求返回 `2005` 而非 `409` | 校验顺序错了：先判批号状态，没走到"是否已存在待确认请求"分支 | 调整校验顺序，并补注释说明为何顺序关键 |
| 3 | 首个确认请求单号是 `-0006` 而非 `-0001`；序列为 `6,2,3,4,5,6`，**第 6 次必然撞号** | `LAST_INSERT_ID(1)` 被表的 AUTO_INCREMENT 覆盖，读回的是自增主键 | 改为 UPSERT + `SELECT seq_value`，不依赖会话隐式状态 |
| 4 | 改密码后重新登录，新令牌立刻提示"登录已失效" | 同一秒内为同一用户签发的 JWT 完全相同，黑名单按签名摘要做键会误杀新令牌 | 令牌增加随机 `jti` 声明，黑名单改用 `jti` |
| 5 | 无 Redis 时幂等窗口是 5 分钟 | 本地分支用固定的 `expireAfterWrite`，忽略了注解上的 TTL | 本地占位改为每条自带 TTL |

缺陷 1、3、4 都属于"设计时看着合理、真跑才暴露"的类型，已在
`ContractSmokeTest` 中补了对应的回归测试。

---

## 二、技术栈

| 分类 | 选型 | 版本 | 说明 |
| --- | --- | --- | --- |
| 语言 / 运行时 | Java | **17** | 编译目标锁定 17（`maven.compiler.release=17`） |
| 框架 | Spring Boot | 3.2.10 | Web、Validation、AOP、Actuator |
| 持久层 | MyBatis-Plus | 3.5.7 | 分页、逻辑删除、防全表更新 |
| 数据库 | MySQL | 8.0 | InnoDB / utf8mb4 / 递归 CTE |
| 本地缓存 | Caffeine | 由 Boot 管理 | 未启用 Redis 时的默认缓存 |
| 分布式缓存 | Redis + Lettuce | 由 Boot 管理 | 二级缓存、令牌黑名单、分布式限流、幂等 |
| 认证 | JJWT | 0.12.6 | HS256 无状态令牌 |
| 密码 | Spring Security Crypto | 由 Boot 管理 | BCrypt |
| 日志 | Logback | 由 Boot 管理 | 异步 Appender + traceId |

**无 Redis 也能完整运行**：缓存、令牌黑名单、限流、幂等四类能力均为"可降级组件"，
`fsts.redis.enabled=false` 时自动切换到本地实现（详见第六节）。

---

## 三、快速开始

### 3.1 前置条件

- JDK 17
- Maven 3.9+
- MySQL 8.0
- Redis 6+（可选）

```powershell
# 确认 JDK 版本为 17
$env:JAVA_HOME = 'D:\JAVA\Java\jdk-17'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

### 3.2 初始化数据库

按顺序执行两个脚本（第二个是纯增量，不修改原脚本的任何表）：

```powershell
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$root  = 'C:/Users/zhou/Desktop/冷冻海产品溯源系统'

# 1) 建库建表 + 基础数据（行政区划、字典、管理员）
#    脚本自带 CREATE DATABASE / USE，无需预先指定库名
& $mysql -u root -p --default-character-set=utf8mb4 `
    -e "source $root/冷冻海产品溯源系统-数据库建表脚本.sql"

# 2) 增量：新增流水号表 sys_sequence
& $mysql -u root -p --default-character-set=utf8mb4 `
    -e "source $root/fsts-backend/sql/V2__add_sequence_table.sql"

# 3) 演示数据（16 家企业 + 完整四环节溯源链，用于联调与验收）
& $mysql -u root -p --default-character-set=utf8mb4 `
    -e "source $root/fsts-backend/sql/demo_data.sql"

# 4) 验证
& $mysql -u root -p --default-character-set=utf8mb4 `
    -e "USE fsts_trace; SHOW TABLES; SELECT COUNT(*) FROM sys_region;"
```

> `source` 是 mysql 客户端内置命令，路径使用正斜杠 `/` 可避免反斜杠被当作转义符；
> 若中文路径出现乱码，改用 `Get-Content <脚本> -Encoding UTF8 | & $mysql -u root -p --default-character-set=utf8mb4`。
>
> `demo_data.sql` 会先清空业务表再写入，可重复执行；每次执行后各表的自增 ID 与
> 演示编号都会回到脚本约定的初始状态。

> `sys_sequence` 是**必须执行**的增量表。没有它，企业编码、确认请求单号、
> 溯源标识码在并发下会大量撞唯一索引。原因见第七节。

### 3.3 配置数据库连接

开发环境默认读取 `application-dev.yml`，可用环境变量覆盖，避免把密码写进仓库：

```powershell
$env:FSTS_DB_USERNAME = 'root'
$env:FSTS_DB_PASSWORD = '<你的密码>'
```

### 3.4 构建与启动

```powershell
# 构建（跳过测试可加 -DskipTests）
mvn clean package

# 开发环境启动
java -jar target/fsts-trace-backend.jar

# 生产环境启动（开 Redis、走故障转移连接串）
java -jar target/fsts-trace-backend.jar --spring.profiles.active=prod
```

启动成功后访问健康检查：

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

### 3.5 冒烟验证（推荐）

接口清单已固化为可重复执行的脚本，覆盖附录 G 的 14 条用例与鉴权/隔离/健康检查，
共 **45 项断言**：

```powershell
# 建议先重置演示数据，保证断言依赖的初始状态一致
pwsh -File scripts/api-smoke-test.ps1

# 指定服务地址
pwsh -File scripts/api-smoke-test.ps1 -BaseUrl http://localhost:8080
```

全部通过时退出码为 `0`，存在失败时退出码为 `1` 并列出失败用例，
可直接接入 CI。

> 脚本会修改数据（确认一条进场请求、新建并删除一个测试企业）。
> 重复执行前请重新导入 `sql/demo_data.sql`，否则附录 G-9 依赖的"待确认请求"
> 已被上一次运行确认掉。若只想验证只读接口，可从脚本中摘取对应片段。

### 3.6 手工验证

```powershell
# 管理员登录（默认账号 admin / 123456）
$login = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/admin/auth/login `
    -ContentType 'application/json;charset=UTF-8' `
    -Body '{"loginName":"admin","password":"123456"}'
$login | ConvertTo-Json -Depth 5

# 带令牌查询当前管理员信息
$token = $login.data.token
Invoke-RestMethod -Uri http://localhost:8080/api/admin/auth/info `
    -Headers @{ Authorization = "Bearer $token" } | ConvertTo-Json -Depth 5

# 免登录接口：省列表
Invoke-RestMethod http://localhost:8080/api/common/regions/provinces |
    ConvertTo-Json -Depth 3
```

---

## 四、配置说明

### 4.1 环境变量

| 变量名 | 默认值 | 生效环境 | 说明 |
| --- | --- | --- | --- |
| `FSTS_DB_HOSTS` | `127.0.0.1:3306` | prod | 可写多主机实现故障转移，如 `10.0.0.1:3306,10.0.0.2:3306` |
| `FSTS_DB_USERNAME` | `root`(dev) / `fsts`(prod) | 全部 | 数据库账号 |
| `FSTS_DB_PASSWORD` | `123456`(dev) / 空(prod) | 全部 | 数据库密码 |
| `FSTS_DB_POOL_SIZE` | `50` | prod | HikariCP 最大连接数 |
| `FSTS_JWT_SECRET` | 内置默认值 | 全部 | **生产必改**，长度 ≥ 32 字节 |
| `FSTS_REDIS_HOST` | `127.0.0.1` | prod | Redis 地址 |
| `FSTS_REDIS_PORT` | `6379` | prod | Redis 端口 |
| `FSTS_REDIS_PASSWORD` | 空 | prod | Redis 密码 |
| `LOG_HOME` | `./logs` | 全部 | 日志输出目录 |

> **安全提示**：生产环境必须覆盖 `FSTS_JWT_SECRET`。若沿用默认值，
> 攻击者可自行签发任意身份的令牌，等效于系统失守。

### 4.2 两个 Profile 的差异

| 配置项 | dev | prod |
| --- | --- | --- |
| 数据源 | 单机 `127.0.0.1:3306` | 多主机故障转移 + `autoReconnect` |
| 连接池上限 | 30 | 50 |
| Redis | 关闭（用本地缓存） | 开启 |
| Redis 健康检查 | 关闭 | 开启 |
| 日志级别 | root=info，打印 SQL | root=warn，业务 info |

### 4.3 业务参数

| 配置项 | 默认值 | 含义 |
| --- | --- | --- |
| `fsts.jwt.expire-seconds` | 7200 | 令牌有效期（秒），与接口文档一致 |
| `fsts.cache.local-max-size` | 10000 | 本地缓存最大条目 |
| `fsts.cache.dict-ttl-seconds` | 1800 | 字典缓存时长 |
| `fsts.cache.region-ttl-seconds` | 86400 | 区划缓存时长 |
| `fsts.ratelimit.public-qps` / `public-burst` | 200 / 400 | 消费者端溯源查询限流 |
| `fsts.ratelimit.login-qps` / `login-burst` | 20 / 40 | 登录接口限流（防撞库） |
| `fsts.trace.qr-base-url` | `https://fsts.example.com/trace/` | 二维码内容前缀 |
| `fsts.trace.query-count-flush-interval-ms` | 5000 | 查询次数聚合落库间隔 |
| `fsts.trace.query-count-flush-batch` | 500 | 单次落库的最大标识码条数 |
| `fsts.batch.trace-code-max-retry` | 8 | 溯源码生成冲突重试次数 |

### 4.4 运维端点（Actuator）

| 端点 | 用途 |
| --- | --- |
| `/actuator/health` | 健康总览 |
| `/actuator/health/liveness` | K8s 存活探针 |
| `/actuator/health/readiness` | K8s 就绪探针（滚动发布用） |
| `/actuator/info` | 应用信息 |
| `/actuator/metrics` | 指标 |

> 生产环境应把 `/actuator/**` 限制在内网或加独立鉴权，不要直接暴露到公网。

---

## 五、接口清单

三端以路径前缀隔离：`/api/admin`（管理端）、`/api/enterprise`（节点企业）、
`/api/public` 与 `/api/common`（免登录）。

### 5.1 认证模块（7 个）

| 编号 | 方法 | 路径 | 权限 | 控制器 |
| --- | --- | --- | --- | --- |
| 4.1 | POST | `/api/admin/auth/login` | 匿名 | `AdminAuthController` |
| 4.2 | GET | `/api/admin/auth/info` | 管理员 | `AdminAuthController` |
| 4.3 | POST | `/api/admin/auth/logout` | 管理员 | `AdminAuthController` |
| 4.4 | POST | `/api/enterprise/auth/login` | 匿名 | `EnterpriseAuthController` |
| 4.5 | GET | `/api/enterprise/auth/info` | 企业 | `EnterpriseAuthController` |
| 4.6 | PUT | `/api/enterprise/auth/password` | 企业 | `EnterpriseAuthController` |
| 4.7 | POST | `/api/enterprise/auth/logout` | 企业 | `EnterpriseAuthController` |

### 5.2 公共基础数据（3 个）

| 编号 | 方法 | 路径 | 权限 |
| --- | --- | --- | --- |
| 5.1 | GET | `/api/common/regions/provinces` | 匿名 |
| 5.2 | GET | `/api/common/regions/cities` | 匿名 |
| 5.3 | GET | `/api/common/dicts/{typeCode}` | 匿名 |

### 5.3 管理端 · 企业注册信息管理（6 个）

| 编号 | 方法 | 路径 |
| --- | --- | --- |
| 6.1 | GET | `/api/admin/enterprises` |
| 6.2 | GET | `/api/admin/enterprises/{id}` |
| 6.3 | POST | `/api/admin/enterprises` |
| 6.4 | PUT | `/api/admin/enterprises/{id}` |
| 6.5 | DELETE | `/api/admin/enterprises/{id}` |
| 6.6 | GET | `/api/admin/enterprises/check-name` |

### 5.4 管理端 · 可视化大屏（5 个）

| 编号 | 方法 | 路径 |
| --- | --- | --- |
| 7.1 | GET | `/api/admin/statistics/overview` |
| 7.2 | GET | `/api/admin/statistics/register-trend` |
| 7.3 | GET | `/api/admin/statistics/province-distribution` |
| 7.4 | GET | `/api/admin/statistics/province-count` |
| 7.5 | GET | `/api/admin/statistics/type-distribution` |

### 5.5 企业端 · 本企业信息与上游选择（3 个）

| 编号 | 方法 | 路径 |
| --- | --- | --- |
| 8.1 | GET | `/api/enterprise/profile` |
| 9.1 | GET | `/api/enterprise/upstream/enterprises` |
| 9.2 | GET | `/api/enterprise/upstream/batches` |

### 5.6 企业端 · 产品批号管理（10 个）

| 编号 | 方法 | 路径 |
| --- | --- | --- |
| 10.1 | GET | `/api/enterprise/batches` |
| 10.2 | GET | `/api/enterprise/batches/{id}` |
| 10.3 | POST | `/api/enterprise/batches` |
| 10.4 | PUT | `/api/enterprise/batches/{id}` |
| 10.5 | DELETE | `/api/enterprise/batches/{id}` |
| 10.6 | PUT | `/api/enterprise/batches/{id}/publish` |
| 10.7 | PUT | `/api/enterprise/batches/{id}/off-shelf` |
| 10.8 | GET | `/api/enterprise/batches/check-batch-no` |
| 10.9 | POST | `/api/enterprise/batches/{id}/confirm-request` |
| 10.10 | GET | `/api/enterprise/batches/{id}/trace-code` |

### 5.7 企业端 · 下游进场确认（4 个）

| 编号 | 方法 | 路径 |
| --- | --- | --- |
| 11.1 | GET | `/api/enterprise/confirm-requests` |
| 11.2 | GET | `/api/enterprise/confirm-requests/{id}` |
| 11.3 | PUT | `/api/enterprise/confirm-requests/{id}/confirm` |
| 11.4 | PUT | `/api/enterprise/confirm-requests/{id}/reject` |

### 5.8 消费者端（1 个）

| 编号 | 方法 | 路径 | 权限 |
| --- | --- | --- | --- |
| 12.1 | GET | `/api/public/trace/{traceCode}` | 匿名 + 限流 |

---

## 六、高并发 / 高性能 / 高可用设计

每一项都给出**问题 → 方案 → 代价**。代价部分请重点关注：
任何"高性能"都有成本，宣称零成本的方案通常是没想清楚。

### 6.1 水平扩展：无状态服务

- **问题**：状态存在单机内存或会话里，就无法起第二个实例。
- **方案**：JWT 无状态鉴权，服务端不保存会话；退出登录用黑名单而非 Session 销毁。
- **代价**：令牌签发后无法"立即"全局作废，只能靠黑名单弥补；黑名单有效期等于令牌剩余寿命。

### 6.2 读多写少：两级缓存 + 自动降级

- **问题**：字典、省市下拉是每个页面初始化都要调的接口，直连数据库会白白占用连接。
- **方案**：`CacheService` 抽象两套实现，由 `fsts.redis.enabled` 切换。
  - 关闭：Caffeine 本地缓存，每条缓存自带 TTL。
  - 开启：Redis 共享缓存，多实例一致。
- **降级**：Redis 读写异常时，读操作按"未命中"处理、写操作静默失败，
  业务退化为直连数据库，不会整体不可用。
- **代价**：缓存内容以 JSON 存取，多一次序列化开销；换来的是不会把共享可变对象漏给调用方。

### 6.3 写冲突：CAS 状态流转，不用悲观锁

- **问题**：批号状态机（新建 → 待确认 → 已确认 → 已下架）在并发点击下可能被改坏；
  `SELECT ... FOR UPDATE` 锁范围大、易死锁。
- **方案**：所有状态流转都写成带前置条件的条件更新，依据影响行数判定成败。例如发布批号：

  ```sql
  UPDATE product_batch
     SET batch_status = 3, publish_time = ?, update_time = NOW()
   WHERE id = ? AND enterprise_id = ? AND batch_status = 1 AND deleted = 0
  ```

  影响行数为 0 即说明状态已被他人改变，直接返回业务码 `2005`。
- **代价**：业务层必须处理"CAS 失败"分支，不能假设更新一定成功。

### 6.4 编码生成：原子取号 + 唯一索引兜底

- **问题**：`SELECT MAX(...)+1` 在并发下必然产生重复编码，冲突后大量回滚形成活锁。
- **方案**：新增 `sys_sequence` 表，用一条 SQL 原子取号：

  ```sql
  INSERT INTO sys_sequence (seq_key, seq_value, create_time, update_time)
  VALUES (?, LAST_INSERT_ID(1), NOW(), NOW())
  ON DUPLICATE KEY UPDATE
      seq_value = LAST_INSERT_ID(seq_value + 1),
      update_time = NOW()
  ```

  取号事务用 `REQUIRES_NEW` 独立提交，让行锁立刻释放；
  业务表唯一索引作为最后一道兜底，冲突时有限次重试。
- **代价**：引入一张额外的表。`LAST_INSERT_ID` 是**连接级**会话变量，
  因此"取号 + 读回"必须在同一事务内完成——`SequenceService` 中的
  `REQUIRES_NEW` 注释已标注这一点，后续维护请勿删除。

### 6.5 热点行写：查询次数改为内存聚合

- **问题**：消费者端每次查询都要 `query_count + 1`，爆款批次的标识码会变成单行热点，
  所有查询在该行上排队等锁——读接口被写操作拖垮。
- **方案**：`TraceQueryCounter` 在读路径只做一次无锁原子自增，后台每 5 秒批量落库；
  单次落库条数可配（默认 500），避免长事务；写失败会把增量并回队列，下个周期重试。
- **代价**：进程被强杀时最多丢失一个周期的增量。对"展示关注度"这类非事务性指标可接受，
  对金额类数据则不可接受。

### 6.6 免登录接口保护：令牌桶限流

- **问题**：`/api/public/**` 与登录接口无需鉴权，是最容易被脚本刷的入口；
  刷穿后会占满数据库连接池，进而拖垮管理端（级联故障）。
- **方案**：`RateLimitInterceptor` 在最外层限流（拦截器顺序：**先限流、后鉴权**）。
  真实 IP 从 `X-Forwarded-For` 首段解析，避免 Nginx 代理后全站共用一个 IP 被一起限流。
  - 本地模式：令牌桶，单实例限流。
  - Redis 模式：Lua 脚本固定窗口计数，多实例共享配额。
- **代价**：固定窗口在边界可能瞬时放行 2 倍流量；对读多写少的本系统可接受。

### 6.7 重复提交：幂等守卫

- **问题**：双击、网络重试会产生重复写请求。
- **方案**：`@Idempotent` 注解 + AOP，按"用户 + 方法 + 参数指纹"占位，
  窗口内重复请求直接返回 `409`；业务失败立即释放占位，允许用户修正后重试。
- **代价**：幂等只是**性能与体验优化**，正确性仍由数据库唯一索引保证，两者不可互相替代。

### 6.8 可用性：优雅停机与健康探针

- **方案**：`server.shutdown=graceful` + `timeout-per-shutdown-phase=30s`，
  收到 SIGTERM 后处理完在途请求再退出；`/actuator/health/readiness` 供滚动发布判断；
  线程池配置 `waitForTasksToCompleteOnShutdown`，避免异步任务被截断。
- **代价**：停机最多慢 30 秒，换来零中断发布。

### 6.9 可观测性：traceId 贯穿

- **方案**：`TraceIdFilter` 为每个请求生成 16 位 traceId，写入 MDC 与响应头 `X-Trace-Id`，
  日志格式含 `%X{traceId}`。多实例部署时可凭该 ID 在日志平台串起全链路。
- **注意**：`afterCompletion` 与 `finally` 中必须清理 MDC 与 ThreadLocal，
  否则线程池复用会导致租户数据串号（已在 `AuthInterceptor`、`UserContext` 中处理）。

---

## 七、数据库说明

### 7.1 表结构来源

| 脚本 | 作用 |
| --- | --- |
| `../冷冻海产品溯源系统-数据库建表脚本.sql` | 原脚本，12 张表 + 3 个视图 + 基础数据（行政区划、字典、管理员） |
| `sql/V2__add_sequence_table.sql` | **纯增量**，新增流水号表 `sys_sequence`，不修改原脚本 |

### 7.2 为什么必须新增 `sys_sequence`

系统有三类"前缀 + 序号"编码：

| 编码 | 格式 | 示例 |
| --- | --- | --- |
| 企业编码 | `FSTS-E-{注册年份}{4 位流水}` | `FSTS-E-20260017` |
| 确认请求单号 | `FSTS-CR-{yyyyMMdd}-{4 位流水}` | `FSTS-CR-20260921-0001` |
| 溯源标识码 | `FSTS-{yyyyMMdd}-{省份}-{4 位流水}` | `FSTS-20260915-SH-0001` |

用 `COUNT(*)` 或 `MAX(...)+1` 生成，在并发下会读到相同基数，进而撞唯一索引、
事务回滚、重试再撞，形成活锁。原脚本没有序列表，因此这是**并发正确性的必要补充**，
不是可选优化。

### 7.3 逻辑删除的坑（已在代码中处理）

`node_enterprise` 的 `login_name` / `credit_code` / `enterprise_code` 是唯一索引，
**逻辑删除的记录仍占用索引**。因此"删除后重新注册同一家企业"若直接 `INSERT` 必然失败。
`AdminEnterpriseService.create` 的处理方式是：先查被逻辑删除的原记录，
存在则覆盖数据并把 `deleted` 置回 0，复用原记录。

同源问题还有一处：消费者端溯源链必须能展示**已被逻辑删除**企业的名称与所在地
（接口 6.5 业务规则 2）。MyBatis-Plus 的 `selectBatchIds` 会被 `@TableLogic` 过滤掉，
因此 `NodeEnterpriseMapper.selectByIdsIncludeDeleted` 使用显式 SQL 绕过。

### 7.4 溯源链查询

使用 MySQL 8 递归 CTE 沿 `upstream_batch_id` 向上追溯（深度固定 ≤ 4），
再由 4 次批量 `IN` 查询取回各环节明细，总计 6 次 SQL，与链条长度无关，无 N+1。

---

## 八、目录结构

```
fsts-backend/
├── pom.xml
├── README.md
├── sql/
│   ├── V2__add_sequence_table.sql        增量 DDL（流水号表）
│   └── demo_data.sql                     演示数据（16 家企业 + 完整溯源链）
├── scripts/
│   └── api-smoke-test.ps1                接口冒烟测试（45 项断言，退出码可接 CI）
└── src/
    ├── main/
    │   ├── java/com/fsts/trace/
    │   │   ├── FstsTraceApplication.java
    │   │   ├── common/                   Result、ErrorCode、异常处理、traceId、常量、工具
    │   │   ├── config/                   MyBatis-Plus、Jackson、WebMvc、线程池、安全 Bean
    │   │   │   └── props/                各业务配置属性类
    │   │   ├── controller/               8 个控制器，对应 39 个接口
    │   │   ├── dto/
    │   │   │   ├── query/                分页与筛选入参
    │   │   │   ├── request/              请求体
    │   │   │   └── stat/                 统计查询行对象
    │   │   ├── entity/                   13 个实体，与数据表一一对应
    │   │   ├── mapper/                   13 个 Mapper
    │   │   ├── service/                  业务服务
    │   │   │   ├── support/              企业装配器、资质校验、冷链判定
    │   │   │   └── validator/            四类企业的批号校验策略
    │   │   ├── support/
    │   │   │   ├── cache/                两级缓存抽象（本地 / Redis）
    │   │   │   ├── counter/              查询次数聚合器
    │   │   │   ├── limit/                限流器、幂等守卫与切面
    │   │   │   ├── security/             JWT、令牌黑名单、企业状态校验
    │   │   │   └── sequence/             原子取号服务
    │   │   ├── vo/                       响应视图对象
    │   │   └── web/                      鉴权拦截器、限流拦截器、参数解析器
    │   └── resources/
    │       ├── application.yml           主配置
    │       ├── application-dev.yml       开发环境
    │       ├── application-prod.yml      生产环境
    │       ├── logback-spring.xml        异步日志
    │       └── mapper/*.xml              统计与 join 查询的 XML 映射
    └── test/java/com/fsts/trace/
        ├── ApplicationContextTest.java   上下文装配测试
        └── ContractSmokeTest.java        接口契约冒烟测试
```

### 演示账号

导入 `demo_data.sql` 后可用以下账号登录（密码均为 `123456`）：

| 账号 | 角色 | 企业 | 可验证的关键场景 |
| --- | --- | --- | --- |
| `admin` | 系统管理员 | —— | 大屏统计、企业注册管理 |
| `qd_yuanye` | 捕捞与养殖企业 | 青岛远洋渔业有限公司 | 发布批号；无上游、无确认请求入口 |
| `yt_haizhen` | 冷冻加工企业 | 烟台海珍冷冻食品有限公司 | 上游选择、选择上游批号、处理进场确认 |
| `sh_yonghai` | 批发商 | 上海甬海水产批发有限公司 | 三温度登记、发起确认请求 |
| `sh_jiaxian` | 零售商 | 上海佳鲜生鲜超市有限公司 | 查看溯源标识码 |
| `nb_yongjiang` | 冷冻加工企业 | 宁波甬江水产加工有限公司 | 冷链温度异常提示 |

消费者端无需登录，直接访问 `/api/public/trace/FSTS-20260915-SH-0001` 即可
获取一条完整四环节溯源链。

---

## 九、部署

### 9.1 单机部署（开发 / 演示）

```powershell
mvn clean package -DskipTests
java -Xms512m -Xmx1g -jar target/fsts-trace-backend.jar --spring.profiles.active=dev
```

### 9.2 集群部署（生产）

```
                    +--------------+
   客户端  --------> |    Nginx     |  负载均衡 + TLS 终止 + 静态资源
                    +------+-------+
                           |  upstream（服务无状态，无需 ip_hash）
             +-------------+-------------+
             v             v             v
      +-----------+ +-----------+ +-----------+
      |  FSTS #1  | |  FSTS #2  | |  FSTS #3  |   可任意增减实例
      +-----+-----+ +-----+-----+ +-----+-----+
            |             |             |
            +------+------+------+------+
                   v             v
          +----------------+  +------------------+
          | Redis Sentinel |  |  MySQL 主从/MGR   |
          | 缓存-黑名单     |  |  写主库 读从库     |
          | 限流-幂等       |  |  故障自动切换      |
          +----------------+  +------------------+
```

**当前实现没有做读写分离**。`application-prod.yml` 提供的是 Connector/J 的
多主机故障转移连接串（主库不可达时自动切换），所有 SQL 仍走同一个数据源。
若需要读写分离，接入 ShardingSphere-JDBC 或 dynamic-datasource 后按数据源路由即可，
业务代码无需改动。

Nginx 参考配置：

```nginx
upstream fsts_backend {
    server 10.0.0.11:8080 max_fails=3 fail_timeout=10s;
    server 10.0.0.12:8080 max_fails=3 fail_timeout=10s;
    keepalive 64;
}

server {
    listen 80;
    server_name fsts.example.com;

    location /api/ {
        proxy_pass http://fsts_backend;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        # 限流依赖真实 IP，必须透传
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_read_timeout 30s;
    }

    # 运维端点仅内网可访问
    location /actuator/ {
        allow 10.0.0.0/8;
        deny all;
        proxy_pass http://fsts_backend;
    }
}
```

### 9.3 JVM 参数建议

```bash
java -server \
  -Xms4g -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/var/log/fsts/heapdump.hprof \
  -Dfile.encoding=UTF-8 \
  -Duser.timezone=Asia/Shanghai \
  -jar fsts-trace-backend.jar --spring.profiles.active=prod
```

连接池与线程数需要按实际压测调整，经验起点：

- 单实例 `server.tomcat.threads.max = 400`
- 单实例 `hikari.maximum-pool-size = 50`，且**不要超过** `MySQL max_connections / 实例数`

数据库连接是全局稀缺资源，盲目调大连接池只会让数据库更慢。

---

## 十、已知限制与后续工作

功能与并发正确性已验证，以下是尚未覆盖的部分，按重要性排序：

1. **未做性能压测**。限流、热点写聚合、两级缓存只验证了正确性，
   没有实测 QPS 上限、P99 延迟与连接池饱和度，投产前必须补。
2. 未验证 **Redis 模式**下的行为（缓存共享、分布式限流、跨实例令牌黑名单）。
   本次联调 `fsts.redis.enabled=false`，走的是本地降级实现；
   集群部署前应在真实 Redis 上重跑一遍冒烟测试。
3. 未做故障演练：数据库主库切换、Redis 抖动、实例被强杀时
   缓存降级与查询次数丢失的边界表现未经实测。
4. 企业名称模糊查询使用 `%关键字%`，无法命中索引；数据量到达百万级需引入
   Elasticsearch 或 MySQL 全文索引。
5. 限流按 IP 维度，未做基于用户 / 接口的细粒度熔断降级（可引入 Sentinel）。
6. 未接入 Prometheus 指标导出（`micrometer-registry-prometheus` 未引入）。
7. 未实现操作审计日志表（谁在何时改了哪条数据）。
8. 未做读写分离。
9. 消费者端未做缓存（每次溯源查询都要访问数据库）。当前 QPS 下无问题，
   若溯源码成为热点，可为 `trace_code` 与溯源链结果加短 TTL 缓存。

---

## 十一、常见问题

**Q：启动报 `fsts.jwt.secret 长度必须不少于 32 字节`？**

A：`FSTS_JWT_SECRET` 设置过短。HS256 要求密钥至少 256 位，请换成 ≥ 32 字节的随机串。

**Q：没有 Redis 能启动吗？**

A：能。`fsts.redis.enabled=false`（dev 默认）时全部走本地实现。
但**集群部署必须开启 Redis**，否则令牌黑名单、限流、幂等只在单实例内生效。

**Q：为什么 `sys_sequence` 必须建？不建会怎样？**

A：并发下企业编码 / 请求单号 / 溯源码会大范围撞唯一索引并回滚。
单机低并发测试可能"看起来正常"，上线后会集中爆发。

**Q：主键为什么在 JSON 里是字符串？**

A：主键为 BIGINT，超过 JavaScript 的 `Number.MAX_SAFE_INTEGER`（2^53-1），
直接返回数字会丢精度。因此只对 ID 字段标注
`@JsonSerialize(using = ToStringSerializer.class)`，**不做全局 Long 转字符串**——
否则分页 `total/size/current/pages` 与统计 `count` 也会变成字符串，
破坏接口文档 2.5 的契约。`ContractSmokeTest` 中有对应的回归测试。

**Q：`TraceQueryCounter` 的计数为什么不实时入库？**

A：见 6.5 热点行写。读取接口返回的 `queryCount` 已经包含未落库增量，
对用户是实时准确的。

**Q：企业被停用后，旧令牌还能用多久？**

A：最多 60 秒（`EnterpriseStatusChecker` 的缓存 TTL）。每次请求校验的是缓存而非数据库，
因此不会给数据库带来常量压力。

**Q：已下架的批号为什么在列表里查不到？**

A：接口 10.1 约定 `batchStatus = 4` 的记录不返回；
若显式传 `batchStatus=4`，后端会返回 `400` 而不是静默返回空列表，
以便前端尽早发现调用错误。

**Q：为什么缓存里的 VO 必须加 `@Jacksonized`？**

A：Lombok 的 `@Builder` 会生成全参构造器，从而使 `@Data` 不再生成无参构造器，
Jackson 反序列化时会报 `no Creators, like default constructor, exist`。
这个缺陷很隐蔽：首次请求缓存未命中、直接返回数据库查询结果，**不会报错**；
只有第二次命中缓存、需要把 JSON 反序列化回对象时才抛异常。
凡是会写入 `CacheService` 的 VO，都必须加 `@Jacksonized`。
`ContractSmokeTest#cachedValueObjectsShouldSurviveJsonRoundTrip` 会守住这条约束。

**Q：流水号为什么要用 UPSERT + SELECT，而不是 `LAST_INSERT_ID`？**

A：`INSERT ... VALUES (?, LAST_INSERT_ID(1)) ON DUPLICATE KEY UPDATE seq_value = LAST_INSERT_ID(seq_value + 1)`
看起来能在两条语句内完成取号，但**当 INSERT 真正插入新行时，表的 AUTO_INCREMENT 会覆盖
语句里显式设置的 `LAST_INSERT_ID`**。结果是首次取号返回该行的自增主键（例如 6），
而行里存的却是 1，序列变成 `6, 2, 3, 4, 5, 6`——第 6 次必然与第 1 次重复。
因此改为"先 UPSERT、再读回 `seq_value` 列"，不依赖任何会话级隐式状态。

**Q：同一秒内连续登录两次，第二个令牌会立刻失效吗？**

A：不会。JWT 载入了随机 `jti` 声明，两次签发的令牌互为不同值，
退出登录时只把该次登录的 `jti` 加入黑名单。
若不加 `jti`，同一秒内同用户的令牌会完全相同（连签名也相同），
黑名单就会误杀新令牌，表现为"刚登录就提示登录已失效"。
