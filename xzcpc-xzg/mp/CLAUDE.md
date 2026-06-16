# mp 模块 — 小程序端后端

## 模块定位

mp 是小程序端（门店端）的后端模块，通过 mp-server 独立部署在 8081 端口，与总部端 server（8080）共享同一个 MySQL 数据库，但分开部署在不同服务器上。

## 包结构

```
mp/src/main/java/com/xzcpc/mp/
├── config/
│   ├── JwtProperties.java      # mp.jwt 配置（secret、expire-hours）
│   ├── WxMaProperties.java      # wx.miniapp 配置（appid、secret）
│   ├── WxMaConfiguration.java   # @Profile("!dev") 生产环境 WxMaService Bean
│   ├── MockConfig.java          # @Profile("dev") Mock WxMaService，绕过微信 API
│   └── MpWebMvcConfig.java      # 注册 MpLoginInterceptor，放行 /api/mp/auth/wx/login
├── context/
│   ├── LoginUser.java           # 当前登录用户（sessionId, openid, phone, storeId, storeName）
│   └── UserContextHolder.java   # ThreadLocal 存取 LoginUser
├── interceptor/
│   ├── MpLoginInterceptor.java  # 从 Header 取 Bearer token → 校验 JWT → 写 ThreadLocal
│   └── DeadlineGuardAspect.java # AOP 切面，非 GET 请求自动校验任务 deadline
├── entity/
│   └── StoreManagerSession.java # store_manager_session 表实体
├── mapper/
│   └── StoreManagerSessionMapper.java
├── dto/                         # 请求/响应 DTO
├── service/                     # 接口：MpAuthService、MpTaskService、MpZoneService、MpMaterialService
│   └── impl/                    # 实现类
└── controller/
    ├── MpAuthController.java    # /api/mp/auth/**
    ├── MpTaskController.java    # /api/mp/tasks/**
    ├── MpZoneController.java    # /api/mp/tasks/{taskId}/zones/**
    └── MpMaterialController.java # /api/mp/tasks/{taskId}/zones/{zoneId}/**
```

## 认证流程

### 1. wxLogin — `POST /api/mp/auth/wx/login`

- 入参 `{ code }`（微信 `wx.login()` 返回的临时 code）
- 调用微信 `jsCode2SessionInfo(code)` 换取 openid + sessionKey
- 用 openid 查 `store_manager_session` 表，不存在则新建
- 生成 JWT token（sub=sessionId, claims: openid, phone=null）
- 若已绑定手机号 → 返回 `bound: true` + token
- 若未绑定 → 返回 `bound: false` + 临时 token

### 2. bindPhone — `POST /api/mp/auth/wx/bind-phone`

- **此接口需要拦截器校验**（MpWebMvcConfig 只放行 wx/login，不放行此接口）
- 入参 `{ phoneCode, phone }`
  - 生产环境：传 `phoneCode`，通过微信 `getPhoneNoInfo(phoneCode)` 解密
  - 开发环境：传 `phone`（明文），直接绑定（微信测试号无法调用 getPhoneNumber）
- 校验该手机号在 task 表中是否有未提交的任务 → 没有则返回 4031
- 生成含 phone 的新 JWT，更新 session

### 3. logout / me

- logout：清空 session.token
- me：返回当前用户信息（token、bound、phone、storeName）

### Token 管理

- 算法：HMAC-SHA384（jjwt 0.12.5）
- 存储：JWT 签发后同时写入 `store_manager_session.token`，校验时比对数据库
- 过期：默认 168 小时（7 天），配置 `mp.jwt.expire-hours`
- 注销：清空 token 字段使旧 token 失效

## API 全量

### 认证（4 个）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/mp/auth/wx/login | 微信登录 |
| POST | /api/mp/auth/wx/bind-phone | 绑定手机号 |
| POST | /api/mp/auth/logout | 登出 |
| GET | /api/mp/auth/me | 当前用户信息 |

### 任务（5 个）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/mp/tasks | 任务列表（current + history） |
| GET | /api/mp/tasks/{id} | 任务详情（含分区进度） |
| POST | /api/mp/tasks/{id}/submit | 提交任务 |
| GET | /api/mp/tasks/{id}/summary | 盘点汇总 |
| GET | /api/mp/tasks/{id}/result | 提交结果 |

### 分区（6 个）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/mp/tasks/{taskId}/zones/{zoneId}/materials | 分区物料列表 |
| POST | /api/mp/tasks/{taskId}/zones/{zoneId}/save | 批量保存分区 |
| POST | /api/mp/tasks/{taskId}/zones/{zoneId}/item-save | 单条保存 |
| POST | /api/mp/tasks/{taskId}/zones | 新增分区 |
| DELETE | /api/mp/tasks/{taskId}/zones/{zoneId} | 删除分区 |
| PUT | /api/mp/tasks/{taskId}/zones/sort | 分区排序 |

### 物料（4 个）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/mp/tasks/{taskId}/zones/{zoneId}/candidates | 候选物料（keyword 搜索） |
| POST | /api/mp/tasks/{taskId}/zones/{zoneId}/materials | 添加物料到分区 |
| DELETE | /api/mp/tasks/{taskId}/zones/{zoneId}/materials/{materialId} | 移除物料 |
| PUT | /api/mp/tasks/{taskId}/zones/{zoneId}/materials/sort | 物料排序 |

## 业务错误码

| 错误码 | 含义 |
|--------|------|
| 401 | 未登录 / Token 失效 / Token 校验失败 |
| 4031 | 手机号未被指派盘点任务 |
| 4032 | 任务已提交，不可修改/重复提交 |
| 4033 | 任务已过截止时间 |
| 4040 | 任务不存在（不属于当前用户） |

## 关键设计决策与注意事项

### 1. 开发模式 Mock（WxJava）

- 微信测试号不能调用 `wx.login` 和 `getPhoneNumber`
- `MockConfig` 通过 `@Profile("dev")` 创建匿名 `WxMaServiceImpl` 子类，重写 `jsCode2SessionInfo()` 返回 `mock_openid_{code}` 作为 openid
- `MpAuthServiceImpl.bindPhone()` 同时接受 `phone`（明文，开发用）和 `phoneCode`（生产用）
- **注意**：WxJava 4.6.0 的 API 方法名为 `jsCode2SessionInfo(code)` 和 `getPhoneNoInfo(phoneCode).getPhoneNumber()`，不同版本方法名可能不同

### 2. 拦截器排除规则

- `MpWebMvcConfig` 只排除 `/api/mp/auth/wx/login`
- **不可排除 `/api/mp/auth/**`**，因为 `bind-phone`、`logout`、`me` 都依赖拦截器提取 token 写入 ThreadLocal
- Controller 方法通过 `UserContextHolder.get()` 获取当前用户，phone 为空时说明仅持有临时 token

### 3. AOP 截止时间守卫

- `DeadlineGuardAspect` 拦截所有 Controller 的非 GET 请求（POST/PUT/DELETE/PATCH）
- 自动从方法参数中寻找第一个 Integer 类型参数（假定为 taskId），查 task 表的 deadline 字段
- 过期则抛出 4033，不依赖每个 Service 方法手动校验
- **局限性**：仅适用于 taskId 作为路径变量的场景；非任务相关的写接口（如 auth）不会受影响

### 4. 任务提交逻辑

- 校验所有分区 `zone_saved = 1`
- 更新 task 状态为 submitted，记录 submitted_at
- 清除旧汇总数据，重新生成 `task_material_summary`（合并跨分区同名物料的总数量，数量为 0 的不计入）

### 5. 数据隔离

- 所有任务/分区/物料接口均通过 `phone` 校验数据归属（`validateTaskOwnership`）
- phone 从已认证的 JWT token（ThreadLocal 中的 LoginUser）获取，不信任前端传参

### 6. WxMaService Bean 注入

- mp 模块不直接依赖 WxJava common 包的某些版本冲突
- 依赖通过 `weixin-java-miniapp 4.6.0` 传递引入
- `WxMaConfiguration`（生产）和 `MockConfig`（开发）通过 `@Profile` 互斥，确保只有一个 `WxMaService` Bean

### 7. Lombok 注解处理

- mp/pom.xml 必须配置 `maven-compiler-plugin` 的 `annotationProcessorPaths` 明确指定 Lombok
- 父 POM 中 Lombok 是 `<optional>true</optional>`，子模块不显式配置会导致编译失败
- mp/pom.xml 中将 Lombok 设为 `<scope>provided</scope>` 覆盖父 POM 的 optional

## 部署

```bash
# 开发模式（profile=dev，Mock 微信 API）
cd mp-server && mvn spring-boot:run   # → localhost:8081

# 生产打包
cd mp-server && mvn clean package -DskipTests
java -jar target/mp-server-1.0.0.jar
```

配置文件在 [mp-server/src/main/resources/application.yml](mp-server/src/main/resources/application.yml)，生产环境需修改：
- `wx.miniapp.appid` / `secret` — 真实小程序 AppID 和 Secret
- `mp.jwt.secret` — 生产密钥（当前是开发默认值）
- `spring.profiles.active` — 改为 `prod` 或去掉 dev
