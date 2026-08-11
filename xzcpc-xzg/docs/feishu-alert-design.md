# 象掌柜 — 线上异常飞书告警系统 设计方案

> 状态：已实现
> 日期：2026-07-24
> 版本：v1.0
> 项目：盘点工具 1.0（xzcpc-xzg）

---

## 一、项目目标

构建一套**线上未处理异常自动捕获 → 飞书群实时推送**的告警系统。未预期异常发生时，异步推送结构化告警消息（含环境、请求上下文、堆栈摘要）到飞书群，让团队第一时间感知线上故障。

### 解决的痛点

| 痛点 | 现状 | 目标 |
|------|------|------|
| 线上报错无感知 | 用户反馈了才知道 | 异常发生后秒级推送到群 |
| 排查信息不足 | 只有日志，缺乏请求上下文 | 告警包含 HTTP 方法/路径/用户/来源 IP |
| 日志被淹没 | 大量 INFO 掺杂 ERROR | 飞书群独立通道，不被日志噪音干扰 |
| 业务 500 错误漏报 | `BusinessException(code>=500)` 只打日志 | 同样推送到飞书群 |

---

## 二、整体架构

```
                            ┌─────────────────────────────────┐
                            │         application.yml          │
                            │  feishu.alert.enabled            │
                            │  feishu.alert.webhook-url        │
                            │  feishu.alert.app-name           │
                            └─────────────┬───────────────────┘
                                          │ 注入
                                          ▼
┌──────────────────────────────────────────────────────────────────────┐
│                         捕获层（两个入口）                              │
│                                                                      │
│  入口 A：BusinessException (code≥500)    入口 B：Exception 兜底        │
│  ┌──────────────────────────────┐       ┌────────────────────────┐   │
│  │ handleBusinessException()    │       │ handleException()       │   │
│  │                              │       │                         │   │
│  │  排除不告警：                 │       │  排除不告警（更上层已    │   │
│  │  × code < 500 的业务异常     │       │  被特定 handler 处理）： │   │
│  └──────────────┬───────────────┘       │  × BusinessException    │   │
│                 │                       │  × NoResourceFound      │   │
│                 │                       │  × TypeMismatch         │   │
│                 │                       │  × MethodNotSupported   │   │
│                 │                       └───────────┬─────────────┘
│                 │                                   │
│                 │  captureContext(request)           │
│                 │  FeishuAlertContext                │
│                 ▼                                   │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │               FeishuWebhookAlertService                         │  │
│  │                                                                │  │
│  │  notify(FeishuAlertContext ctx, Throwable e)                    │  │
│  │                                                                │  │
│  │  ① 查配置：enabled? url 非空?                                    │  │
│  │  ② buildText() → 拼接消息文本                                   │  │
│  │  ③ 序列化为飞书 msg_type: text JSON                              │  │
│  │  ④ @Async("alertTaskExecutor") → RestTemplate POST webhook      │  │
│  └──────────────────────────┬─────────────────────────────────────┘  │
│                             │                                        │
└─────────────────────────────┼────────────────────────────────────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │   飞书群自定义机器人 Webhook    │
              │   POST /open-apis/bot/v2/hook  │
              └───────────────┬───────────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │       飞书群聊消息             │
              │  【象掌柜-总部端 服务端异常】    │
              │  应用: 象掌柜-总部端            │
              │  环境: prod                   │
              │  时间: 2026-07-24 10:30:45    │
              │  请求: POST /api/materials    │
              │  来源: 10.0.1.25             │
              │  用户: 张三 (ou_xxx)          │
              │  异常: BusinessException      │
              │  【堆栈摘要】                   │
              └───────────────────────────────┘
```

### 核心设计原则

1. **异步不阻塞**：告警发送标注 `@Async("alertTaskExecutor")`，使用 DiscardPolicy，不拖慢 HTTP 响应
2. **告警通道独立**：飞书「自定义机器人」Webhook，与开放平台应用凭证无关
3. **发送失败不影响业务**：告警发送异常只打 `log.warn`，不向上层抛
4. **双入口覆盖**：`BusinessException(code>=500)` + 未预期 `Exception` 兜底
5. **非预期异常才告警**：参数校验、404 等业务预期内异常明确排除，避免告警疲劳

---

## 三、模块布局

告警功能位于 `common` 模块，两个部署单元（server / mp-server）共享。

```
xzcpc-xzg/common/src/main/java/com/xzcpc/common/
├── feishu/alert/
│   ├── FeishuAlertProperties.java        # @ConfigurationProperties("feishu.alert")
│   ├── FeishuAlertContext.java           # 告警上下文 DTO
│   └── FeishuWebhookAlertService.java    # 核心告警服务
├── config/
│   └── AsyncConfig.java                  # alertTaskExecutor 线程池
├── exception/
│   └── GlobalExceptionHandler.java       # 两个入口触发告警
└── context/
    └── AdminContextHolder.java           # 提取当前操作用户

xzcpc-xzg/common/src/test/java/com/xzcpc/common/feishu/alert/
└── FeishuWebhookAlertServiceTest.java    # 15 个单元测试
```

### 为什么放 common 模块

`FeishuProperties`（飞书 OAuth 登录配置）在 `server` 模块，`mp-server` 访问不到。
告警配置和告警服务放在 `common` 模块，两个应用都能独立配置各自的 webhook，
可以推送到同一个群或不同群。

---

## 四、配置层

### 4.1 配置定义

```yaml
feishu:
  alert:
    enabled: ${FEISHU_ALERT_ENABLED:true}
    webhook-url: ${FEISHU_ALERT_WEBHOOK_URL:}
    app-name: 象掌柜-总部端    # 或 象掌柜-小程序端
```

### 4.2 Java 配置类

`common/.../feishu/alert/FeishuAlertProperties.java`

```java
@Data
@Component
@ConfigurationProperties(prefix = "feishu.alert")
public class FeishuAlertProperties {
    private boolean enabled = true;
    private String webhookUrl = "";
    private String appName = "象掌柜";
}
```

### 4.3 两个应用的配置

`server/src/main/resources/application.yml`：
```yaml
feishu:
  alert:
    enabled: ${FEISHU_ALERT_ENABLED:true}
    webhook-url: ${FEISHU_ALERT_WEBHOOK_URL:}
    app-name: 象掌柜-总部端
```

`mp-server/src/main/resources/application.yml`：
```yaml
feishu:
  alert:
    enabled: ${FEISHU_ALERT_ENABLED:true}
    webhook-url: ${FEISHU_ALERT_WEBHOOK_URL:}
    app-name: 象掌柜-小程序端
```

### 4.4 开关控制

| 场景 | 行为 |
|------|------|
| `enabled=false` | 不发送，即使有 URL |
| `webhook-url=""` | 不发送，即使 enabled=true |
| `enabled=true` + URL 有效 | 正常推送告警 |
| `FEISHU_ALERT_ENABLED=false` 环境变量 | 临时关闭（如本地开发） |
| 不设 `FEISHU_ALERT_WEBHOOK_URL` | 本地开发默认不发送 |

---

## 五、捕获层：两个异常入口

两个入口均在 `GlobalExceptionHandler`（`common` 模块，`@RestControllerAdvice`）。

### 5.1 入口 A：BusinessException (code ≥ 500)

```java
@ExceptionHandler(BusinessException.class)
public R<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
    if (e.getCode() >= 500) {
        log.error("业务异常 - {} {} → code={}, message={}", ...);
        // 异步推送飞书群告警
        FeishuAlertContext ctx = captureContext(request);
        feishuWebhookAlertService.notify(ctx, e);
    } else {
        log.warn("业务异常 - {} {} → code={}, message={}", ...);
    }
    return R.fail(e.getCode(), e.getMessage());
}
```

### 5.2 入口 B：Exception 兜底

```java
@ExceptionHandler(Exception.class)
public R<Void> handleException(Exception e, HttpServletRequest request) {
    log.error("系统异常 - {} {} → {}: {}", ...);
    // 异步推送飞书群告警
    FeishuAlertContext ctx = captureContext(request);
    feishuWebhookAlertService.notify(ctx, e);
    return R.fail("系统错误，请稍后重试");
}
```

### 5.3 异常分类

| 异常类型 | 处理方式 | 是否飞书告警 |
|---------|---------|:---:|
| `BusinessException` (code ≥ 500) | 返回业务错误码 | **✓** |
| `BusinessException` (code < 500) | 返回业务错误码 | ✗ 业务预期内 |
| `NoResourceFoundException` | 返回 404 | ✗ 静态资源 |
| `MethodArgumentTypeMismatchException` | 返回 400 | ✗ 参数格式错误 |
| `HttpRequestMethodNotSupportedException` | 返回 405 | ✗ 路由不匹配 |
| **其他所有 `Exception`** | `handleException()` 兜底 | **✓** |

### 5.4 请求上下文捕获（`FeishuAlertContext`）

| 字段 | 来源 | 用途 |
|------|------|------|
| `httpMethod` | `request.getMethod()` | 定位具体接口 |
| `requestPath` | `request.getRequestURI()` | 定位具体路径 |
| `queryString` | `request.getQueryString()` | 还原完整请求 |
| `remoteAddr` | `request.getRemoteAddr()` | 判断来源 IP |
| `userHint` | `AdminContextHolder.get()` | 关联操作人（总部端）；小程序端为空 |
| `appName` | `FeishuAlertProperties.appName` | 区分总部端/小程序端 |
| `environment` | `spring.profiles.active` | 区分 dev/prod |

---

## 六、加工层：`FeishuWebhookAlertService`

### 6.1 核心方法

```java
@Async("alertTaskExecutor")
public void notify(FeishuAlertContext ctx, Throwable e) {
    if (!alertProperties.isEnabled()) return;
    if (alertProperties.getWebhookUrl().isBlank()) return;
    try {
        String text = buildText(ctx, e);
        String json = buildRequestBody(text);
        restTemplate.postForEntity(alertProperties.getWebhookUrl(), entity, String.class);
    } catch (Exception ex) {
        log.warn("飞书异常告警发送失败: {}", ex.getMessage());
    }
}
```

### 6.2 异步线程池

`common/.../config/AsyncConfig.java` 新增 bean：

```java
@Bean("alertTaskExecutor")
public Executor alertTaskExecutor() {
    // corePoolSize=1, maxPoolSize=2, queueCapacity=50
    // DiscardPolicy：队列满时直接丢弃，不阻塞业务线程
}
```

### 6.3 消息格式

```
【象掌柜-总部端 服务端异常】
应用: 象掌柜-总部端
环境: prod
时间: 2026-07-24 10:30:45
请求: POST /api/materials?name=测试
来源: 10.0.1.25
用户: 张三 (ou_abc123)
异常: java.lang.RuntimeException — 更新物料失败
【堆栈摘要】
java.lang.RuntimeException: 更新物料失败
    at com.xzcpc.material.service.MaterialServiceImpl.update(MaterialServiceImpl.java:142)
    at com.xzcpc.material.controller.MaterialController.updateMaterial(MaterialController.java:58)
    at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
    ... (共 87 行，已省略 63 行)
```

### 6.4 截断策略

| 项目 | 限制 | 说明 |
|------|------|------|
| 堆栈行数 | 前 24 行 | 超出标 `(共 N 行，已省略 M 行)` |
| 堆栈字符 | 1600 字符 | 行截断后再按字符截，超出标 `...(stack truncated)` |
| 异常 message | 200 字符 | 超出截断并加 `...` |

---

## 七、异步线程池

`common/.../config/AsyncConfig.java`

| 线程池 | 核心/最大线程 | 队列 | 拒绝策略 | 用途 |
|--------|:-----------:|:----:|---------|------|
| `logTaskExecutor` | 2/4 | 200 | CallerRunsPolicy | 操作日志、登录日志 |
| `alertTaskExecutor` | 1/2 | 50 | **DiscardPolicy** | 飞书异常告警 |

`alertTaskExecutor` 用 DiscardPolicy 而非 CallerRunsPolicy：告警队列满了说明飞书接口异常或网络不通，此时让调用线程执行反而会阻塞 HTTP 响应。

---

## 八、覆盖范围

| 应用 | 端口 | context-path | 告警来源标识 |
|------|------|-------------|-------------|
| 总部端 (server) | 4026 | `/` | 象掌柜-总部端 |
| 小程序端 (mp-server) | 30261 | `/storeInventory` | 象掌柜-小程序端 |

两个应用共享 `common` 模块的 `GlobalExceptionHandler` 和 `FeishuWebhookAlertService`，
各自配置独立的 webhook URL。

---

## 九、设计决策

### 9.1 为什么用自定义机器人 Webhook

| 因素 | 自定义机器人 Webhook | 开放平台 API（发送消息）|
|------|:---:|:---:|
| 依赖 | 仅需一个 Webhook URL | 需要 App ID + Secret + tenant token |
| 权限 | 无需应用审核 | 需要 `im:message:send` 权限 |
| Token 管理 | 无需 | 需维护 access_token 刷新 |
| 结论 | **告警场景首选** | 业务消息场景 |

### 9.2 为什么不做 LLM 诊断

- LLM 服务可能不可用，不能因为它阻塞核心告警
- Token 有成本
- 堆栈摘要 + 请求上下文已足够初步定位

### 9.3 告警发两次？

不会。`BusinessException` 继承自 `RuntimeException`，本会进入 catch-all `Exception` handler，
但 Spring MVC 的 `@ExceptionHandler` 会优先匹配更具体的类型。所以 `BusinessException` 只会被
`handleBusinessException()` 处理一次，不会重复进入 `handleException()`。

---

## 十、单元测试

15 个单元测试，覆盖消息构建、堆栈截断、开关控制、正常发送四个维度。

```bash
cd xzcpc-xzg && mvn test -pl common -Dtest=FeishuWebhookAlertServiceTest
```

| 分类 | 用例数 | 覆盖内容 |
|------|:-----:|---------|
| 消息构建 | 6 | 完整字段、null 处理、空字符串、超长消息截断 |
| 堆栈截断 | 3 | ≤24 行完整、>24 行截断、>1600 字符二次截断 |
| 开关控制 | 4 | enabled=false、url=null、url=""、url=空白 |
| 正常发送 | 2 | 验证 HTTP Body 结构、发送失败不抛异常 |

---

## 十一、部署清单

### 11.1 飞书群准备

1. 在目标飞书群中添加**自定义机器人**，复制 Webhook URL
2. 设置环境变量 `FEISHU_ALERT_WEBHOOK_URL`

### 11.2 重新打包部署

```bash
# 整体打包（common 模块变更，两个 fat jar 都需重新构建）
cd xzcpc-xzg && mvn clean package -DskipTests

# 部署总部端
java -jar server/target/server-1.0.0.jar

# 部署小程序端
java -jar mp-server/target/mp-server-1.0.0.jar
```

### 11.3 验证

部署后在任意接口构造一个 500 错误，确认飞书群收到告警消息。
本地开发时可不设 `FEISHU_ALERT_WEBHOOK_URL`，URL 为空时自动跳过发送。

---

## 十二、配置速查表

| 配置项 | 环境变量 | 默认值 | 说明 |
|--------|---------|--------|------|
| 告警开关 | `FEISHU_ALERT_ENABLED` | `true` | 全局关闭后所有告警静默 |
| Webhook URL | `FEISHU_ALERT_WEBHOOK_URL` | 空 | 为空时不发送，本地开发无需配置 |
| 应用名称 | yml 写死 | `象掌柜-总部端` / `象掌柜-小程序端` | 区分告警来源 |
