# 小程序端并发优化方案

## 目标

支撑 1000 个门店同时使用小程序，保证高峰期响应时间 < 2 秒。

## 阶段一：参数调优（预计 30 分钟）

### 1.1 Tomcat 线程池

```yaml
# mp-server/src/main/resources/application.yml
server:
  tomcat:
    threads:
      max: 400        # 最大工作线程（默认 200）
      min-spare: 20   # 最小空闲线程（默认 10）
    accept-count: 200 # 等待队列长度（默认 100）
    max-connections: 1000
```

### 1.2 HikariCP 连接池

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 40      # 最大连接数（默认 10）
      minimum-idle: 10           # 最小空闲连接（默认 10）
      idle-timeout: 300000       # 空闲超时 5 分钟
      connection-timeout: 10000  # 获取连接超时 10 秒
```

### 1.3 MySQL

```sql
-- 在服务器端执行
SET GLOBAL max_connections = 500;
SET GLOBAL innodb_buffer_pool_size = 256M;  -- 根据服务器内存调整
```

### 1.4 验证

```bash
# 查看当前 Tomcat 线程数
curl http://localhost:30261/storeInventory/actuator/metrics/tomcat.threads.current

# 压测
# ab -n 1000 -c 50 http://localhost:30261/storeInventory/api/mp/auth/me
```

---

## 阶段二：缓存优化（预计 2 小时）

### 2.1 本地缓存 — 支出类型

**问题**：每次查支出列表都会查 `expense_type` 表，类型数据几乎不变。

```java
// 新增 MpCacheConfig.java
@Configuration
public class MpCacheConfig {
    @Bean
    public Cache<String, List<ExpenseType>> typeCache() {
        return Caffeine.newBuilder()
            .maximumSize(10)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();
    }
}
```

```java
// 修改 MpExpenseServiceImpl.listTypes()
public List<ExpenseType> listTypes() {
    return typeCache.get("enabled", key ->
        expenseTypeMapper.selectList(new LambdaQueryWrapper<ExpenseType>()
            .eq(ExpenseType::getStatus, "enabled")
            .orderByDesc(ExpenseType::getUpdatedAt)));
}
```

**同样处理**：支出项目列表 `listEnabled()`。

### 2.2 本地缓存 — 门店信息

门店信息由外部 API 同步，不常变。

```java
Cache<String, StoreInfo> storeCache = Caffeine.newBuilder()
    .maximumSize(500)
    .expireAfterWrite(30, TimeUnit.MINUTES)
    .build();
```

### 2.3 缓存刷新策略

| 数据 | 刷新时机 | TTL |
|------|---------|-----|
| 支出类型 | 总端修改类型时清缓存 | 10 分钟 |
| 支出项目 | 总端修改项目时清缓存 | 10 分钟 |
| 门店信息 | 定时任务 / 手动刷新 | 30 分钟 |

---

## 阶段三：减少不必要的 SQL（预计 1 小时）

### 3.1 DeadlineGuard 优化

```java
// 现状：每次写操作都查 task 表
// 优化：只对任务相关的写接口生效，auth/expense/staff 的写接口跳过
// 
// 新增排除注解 @SkipDeadlineCheck，标识不需要校验截止时间的接口
```

**接口分类**：

| 接口 | 需要 Deadline 检查 |
|------|-------------------|
| `/api/mp/tasks/**` 写操作 | ✅ 需要 |
| `/api/mp/expenses/**` 写操作 | ❌ 不需要 |
| `/api/mp/auth/**` 写操作 | ❌ 不需要 |
| `/api/mp/staff/**` 写操作 | ❌ 不需要 |

### 3.2 批量查询优化

`zone-entry` 页面加载时并行查询多个分区物料，改为后端提供批量接口：

```java
// 新增 POST /api/mp/tasks/{taskId}/zones/batch-materials
// 一次请求返回所有分区的物料，减少前端请求数
```

---

## 阶段四：异步化（预计 3 小时）

### 4.1 操作日志异步

`OperationLogAspect` 目前同步写 `operation_log` 表。改为异步：

```java
@Async
public void save(OperationLog entity) {
    operationLogMapper.insert(entity);
}
```

### 4.2 图片上传优化

凭证图片上传走 Tomcat 线程，大文件会阻塞。加异步上传支持：

```java
// 新增上传进度接口，前端先上传拿到 URL，再提交记录
```

---

## 阶段五：监控告警（可选）

### 5.1 关键指标

| 指标 | 告警阈值 |
|------|---------|
| Tomcat 活跃线程数 | > 300 |
| HikariCP 活跃连接数 | > 30 |
| 单请求响应时间 | > 3 秒 |
| MySQL 连接数 | > 400 |

### 5.2 日志分析

```bash
# 统计慢请求
grep "耗时" mp-app.log | awk '{print $NF}' | sort -rn | head -20
```

---

## 优先级排序

| 优先级 | 阶段 | 投入 | 收益 |
|--------|------|------|------|
| P0 | 阶段一：参数调优 | 30 分钟 | 直接提升吞吐 |
| P1 | 阶段二：缓存优化 | 2 小时 | 减少 60%+ 数据库查询 |
| P1 | 阶段三：减少 SQL | 1 小时 | 减少无效查询 |
| P2 | 阶段四：异步化 | 3 小时 | 提升用户体验 |
| P3 | 阶段五：监控 | 1 小时 | 发现问题 |
