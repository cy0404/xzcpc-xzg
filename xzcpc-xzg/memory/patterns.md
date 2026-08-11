---
updated: 2026-07-03
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
