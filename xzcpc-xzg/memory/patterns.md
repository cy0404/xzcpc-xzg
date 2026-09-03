---
updated: 2026-08-29
importance: 4
---

# 代码模式与约定

## P001 — Controller-Service-Mapper 三层

- Controller 只做参数校验 + 调 Service + 返回 `R<T>`
- Service 接口在 `service/`，实现在 `service/impl/`
- Mapper 继承 `BaseMapper<T>`
- ❌ Controller 里写业务逻辑、Service 里拼 SQL

## P002 — 统一返回体 R\<T\>

- 成功：`R.ok(data)` 或 `R.ok()`
- 失败：`R.fail("描述")` 或 `R.fail(错误码, "描述")`
- 位置：`common/.../response/R.java`

## P003 — biz_code 业务编码

- 格式：前缀 + 8 位补齐 ID，如 `MAT00000001`
- 前缀：MAT(material)、TPL(template)、TZ(zone)、TZM(zone_material)、TASK(task)、TKZ(task_zone)、SZM(store_zone_material)

## P004 — 快照字段命名

- task_zone_material 表名已标识为快照，字段不加 `_snapshot` 后缀
- ✅ `material_name` ❌ `material_name_snapshot`
- 例外：`base_unit_snapshot`、`rule_id_snapshot`、`conversion_snapshot`、`unit_price_snapshot` — 这些是从 material 表固化来的运行时数据

## P005 — ThreadLocal 用户上下文

- 总部端：`AdminContextHolder.get()` → `AdminUser`
- 小程序端：`UserContextHolder.get()` → `LoginUser`
- ❌ 方法参数层层透传用户信息

## P006 — 逻辑删除 + 乐观锁

- 每个核心表必须有 `del_flag INT DEFAULT 0` + `version INT DEFAULT 0`
- MyBatis-Plus `@TableLogic` + `@Version` 自动处理
- ❌ 物理 DELETE（除非日志清理等特殊场景）

## P007 — 事件驱动模板同步

- 模板/物料变更 → 发布 `TemplateChangedEvent` / `MaterialChangedEvent`
- `TemplateChangeListener` 异步监听（`@Async`），2 秒防抖
- 只重建 `status='not_started'` 的任务快照

## P008 — unit_inputs JSON 格式

- 格式：`{"箱":"1","瓶":"2"}`
- ⚠️ value 是字符串不是数字，解析需 `Integer.parseInt()`

## P009 — 配置文件环境隔离

- `application.yml` — 公共配置，默认 profile=prod
- `application-dev.yml` — 开发环境
- `application-prod.yml` — 生产环境（敏感信息 `${...}` 环境变量）
- `application-local.yml` — ⚠️ 含明文密码，在 `.gitignore` 中，不可提交

## P010 — 全选框回显集合必须与操作集合一致

- 「全选」checkbox 的回显（勾/不勾）必须和全选实际操作的集合**完全相同**
- ❌ 回显用「全页面所有元素」比较，操作只影响「当前 tab/过滤后可见」子集 → 点全选后回显立刻弹回未勾选 → 第二次点击被浏览器当成"勾选"，取消分支永远不可达
- ✅ 回显按「当前可见」集合比较（`visibleCount > 0 && checkedCount === visibleCount`），toggleAll 按同一集合操作
- 例：loss-daily-confirm.html onCheckChange（2026-08-17 修复，tab 结构下其他 tab 卡片本就不该被全选）

## P011 — spring-boot:run 多模块必须带 -am

- `mvn spring-boot:run -pl server` **不带 `-am`** 时，依赖模块（task/template/mp 等）解析 **m2 仓库旧 jar**，不是工作区源码
- 旧 jar 与新源码不一致时启动崩：`Could not resolve placeholder 'material.api.url'`（template-1.0.0.jar 旧类里有废弃 @Value，源码已删除）
- ✅ 必用：`mvn spring-boot:run -pl server -am -Dspring-boot.run.profiles=local`
- 排查手段：看异常堆栈 `jar:file:/.../.m2/repository/com/xzcpc/...` 路径即旧 jar；`unzip -p jar | grep -ao 占位符` 验证

## P012 — 外部接口数据同步的匹配键与本地主键

- 外部接口（企迈/xinfo）的 `id` 是**业务编码**（如 171、353），不是本地主键
- 匹配链：接口 id ↔ `store_info.store_code`（与企迈 code 同源）；本地主键 `store_id`（cmpz/cm 开头）**本地维护**，绝不可取接口 id
- ⚠️ 新店建店时若 store_id NOT NULL 无默认值，INSERT 缺该字段报 1364/500——本地生成（`cm`+24 位查重）或逐行 UPDATE
- 督导/员工类账号用**企业稳定 ID**（飞书 userId）对账，应用维度 open_id 不可跨应用使用
- 同步写库的展示名取**本地名**（admin_permission.name），接口 displayName 仅做 mismatch 提醒；改本地名不影响权限（权限判断只用 open_id + roles）

## P013 — 飞书消息 API 踩坑（撤回 / 群消息列表）

- **撤回消息**：`DELETE /open-apis/im/v1/messages/{message_id}` —— ⚠️ **POST 打过去网关直接 404「page not found」**（不是飞书错误 JSON）
- 撤回**仅 24 小时内**有效；必须用**发送方同一个应用**的 tenant token
- **群消息列表** `im/v1/messages`：`start_time` **必须与 `end_time` 成对传**，单独传报 230001「end_time is earlier than start_time」；不传则从最早消息升序返回（第一页是建群消息，要翻页）
- 按群筛卡片再撤：先拉全量消息 → 过滤 `body.content` 含目标链接 + `sender.sender_type == 'app'` → 只撤 app 消息（勿撤用户/系统消息）
- 卡片按钮 URL 带动态参数：门店模式 `?chatId=群ID`，H5 按参数过滤数据（见 issue-accept.html CHAT_ID 模式）
- 生产飞书应用凭据：`application-local.yml` 明文 FEISHU_APP_SECRET（本地直连飞书 API 可用），生产 jar 内是 `${FEISHU_APP_SECRET}` 占位

## P014 — 多 Executor bean：final + @Qualifier + Lombok 构造注入 = 启动失败

- 场景：`@Qualifier("xExecutor") private final Executor x;` + `@RequiredArgsConstructor`（Lombok 生成构造器）
- **坑**：Lombok 生成的构造参数**不带字段上的 @Qualifier**；pom 若没配 `<parameters>true</parameters>`，参数名也丢 → Spring 按类型匹配 `Executor` 命中多个（logTaskExecutor/alertTaskExecutor/taskScheduler…）→ `No qualifying bean of type Executor ... found 3` 启动失败
- **修复**：改 `@Autowired @Qualifier("xExecutor")` **字段注入**（去 final，Lombok 构造器不再包含该参数；@Qualifier 在字段上生效）
- 排查：`grep -rl "RequiredArgsConstructor" --include="*.java" <模块>` 逐个看是否同时用 @Qualifier 注解字段
- 根治可考虑：pom 配 `-parameters` + lombok.config `lombok.copyableAnnotations`（未实施）

## P015 — 多模块构建：`-pl server` 打包引用 m2 旧 jar（工作区改动不进包）

- **坑**：`mvn package -pl server`（不带 -am）时，server 依赖的 common/task/mp 等模块从**本地 m2 仓库**解析——工作区未提交改动**不会进 jar**，且新代码引用的新 bean/方法在旧 jar 里不存在 → 启动失败或 NoSuchMethodError
- **修复**：先把有改动的模块 install 到 m2：`mvn install -pl common,template,task,expense,people -DskipTests`（reactor 自动排序），再 `mvn package -pl server`
- **验证**：`unzip -l jar | grep BOOT-INF/lib/common` 看依赖 jar 时间戳是否当天；`javap -v` 验证类/注解已编译进
- mp 编译错误挡全量构建时，先修 mp 或单独 install 其他模块；mp 修好后直接 `mvn clean package -DskipTests` 全量最稳（server/mp-server 两 jar 全最新）
- 排查手段：启动异常 `jar:file:/.../.m2/repository/...` 路径 = 旧 jar；看报错 bean 名是否在工作区新增代码里


## P016 — ant-design-vue 组件根元素无父 scoped data-v：布局样式静默失效

- 场景：给 `a-row`/`a-col`/`a-card` 等 ant-design-vue 组件加 class（如 `.report-body-row{height:340px}`），scoped CSS 里 `.report-body-row[data-v-xxx]` 命中不了 → 样式**静默不生效**（组件根元素只带组件自己的 scopeId，不携带父组件 data-v）
- 后果：固定高度/display:flex 失效 → 内容多时溢出视口；数据少时视觉正常，**数据量上去才暴露**，很难排查
- **修复**：布局类样式别依赖组件根元素——改用**原生 div + flex**（`.report-body-row{display:flex;height:340px}` + 列 div flex:1/min-width:0）；或对组件根元素用 `:deep()`/内联 style
- 排查：内容少量正常、多量溢出的布局问题，优先怀疑 scoped 选择器未命中组件根


## P017 — 飞书卡片带跳转按钮：schema 2.0 不支持 action，走 1.0 + 302 中介

- **schema 2.0 卡片不支持 `action` 元素**：发送 HTTP 400 `ErrCode 200861 unsupported tag action`（2.0 的表格/markdown 都可用，唯独按钮不支持）——探针实证
- **带按钮卡必须走 schema 1.0**：`{config:{wide_screen_mode:true}, header:{...}, elements:[...]}`（无 `schema` 字段、`elements` 顶层），与 LossReportPeriodSummaryJob/周期汇总卡同款；header/markdown/hr 在 1.0/2.0 结构兼容
- **按钮结构**：`{"tag":"action","actions":[{"tag":"button","text":{"tag":"plain_text","content":"..."},"type":"primary|default","url":applink}]}`（type 有 primary/default/danger）
- **applink 不能带 # 直达**：按钮 url 若含 `/#/hash路由`，飞书报「重定向 URL 有误」→ 按钮先指向同 host **无 # 的 302 中介接口**（`/api/public/xxx/list-link?…` → `response.sendRedirect("/#/…?…")`），相对 Location 同 host 生效
- **URL 封装**：`fms.buildApplink(path)` = `https://applink.feishu.cn/client/web_app/open?appId={appId}&lk_target_url={urlencode(serverUrl+path)}`；serverUrl 来自 `app.server-url`（默认 profile=http://162.14.122.80:4026，local=127.0.0.1:4026）
- 验证：先发探针卡到测试群确认 schema 被接受，再批量发正式
