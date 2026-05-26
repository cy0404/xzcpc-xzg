# 02 后端架构计划

## 1. 目标

使用 Spring Boot 实现盘点工具后端 API，重点保证：

- 分层清晰。
- 业务规则集中在 Service 层。
- 数据库事务边界明确。
- 接口返回结构统一。
- 错误码清晰。
- 物料和门店从基础数据库管理系统同步，不在盘点工具内维护主数据。
- 任务快照、提交汇总、默认清单回写逻辑可靠。

## 2. 推荐技术选型

- Java 17 或以上
- Spring Boot 3.x
- Spring Web
- Spring Validation
- Spring Transaction
- MyBatis Plus 或 Spring Data JPA
- MySQL 8.x
- Flyway 或 Liquibase
- JUnit 5

如无特殊原因，建议优先使用 MyBatis Plus，便于明确 SQL 和批量操作。

## 3. 推荐包结构

```text
com.inventory
  InventoryApplication
  common
    api
      ApiResponse
      PageResponse
      ErrorCode
    exception
      BusinessException
      GlobalExceptionHandler
    validation
    util
  security
    CurrentUser
    CurrentUserContext
    Role
  module
    material
      controller
      service
      mapper
      entity
      dto
    store
      controller
      service
      mapper
      entity
      dto
    masterdata
      controller
      service
      dto
    template
      controller
      service
      mapper
      entity
      dto
    task
      controller
      service
      mapper
      entity
      dto
    store
      controller
      service
      mapper
      entity
      dto
```

## 4. API 路径规范

总部端：

```text
/api/admin/**
```

门店端：

```text
/api/store/**
```

健康检查：

```text
/api/health
```

## 5. 统一返回结构

建议所有接口返回：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {}
}
```

失败示例：

```json
{
  "success": false,
  "code": "TASK_NOT_COMPLETED",
  "message": "存在未录入物料，暂不可提交",
  "data": null
}
```

分页返回：

```json
{
  "items": [],
  "page": 1,
  "pageSize": 20,
  "total": 100
}
```

## 6. 错误码建议

```text
OK
BAD_REQUEST
UNAUTHORIZED
FORBIDDEN
NOT_FOUND
VALIDATION_ERROR

MATERIAL_NOT_FOUND
MATERIAL_DISABLED
MATERIAL_DUPLICATED

STORE_NOT_FOUND
STORE_DISABLED

MASTER_DATA_SYNC_FAILED

TEMPLATE_NOT_FOUND
TEMPLATE_DISABLED
TEMPLATE_ZONE_NOT_FOUND
TEMPLATE_ZONE_MATERIAL_DUPLICATED

TASK_NOT_FOUND
TASK_SUBMITTED
TASK_NOT_COMPLETED
TASK_ZONE_NOT_FOUND
TASK_ZONE_MATERIAL_DUPLICATED

INPUT_QTY_INVALID
INPUT_QTY_NEGATIVE

STORE_DEFAULT_LIST_INVALID
```

## 7. 权限策略

首期可以先做简化身份注入：

- 请求头携带 `X-User-Id`。
- 请求头携带 `X-Role`。
- 请求头携带 `X-Store-Id`。

后续再替换为正式登录鉴权。

权限规则：

- `/api/admin/**` 仅总部角色访问。
- `/api/store/**` 仅门店店长访问。
- 店长只能访问自己门店的任务。
- 仅店长可提交任务。

## 8. 主数据集成原则

项目已有基础数据库管理系统。盘点工具后端必须遵守：

- 基础数据库管理系统是物料和门店的唯一主数据源。
- 盘点工具本地 `material`、`store` 表只是同步缓存。
- 后台不提供新增、编辑、删除物料和门店的业务接口。
- 模板配置和任务创建只能选择本地缓存中状态为 `enabled` 的物料和门店。
- 基础系统字段变化不影响已创建任务，因为任务表保存物料快照。
- 门店默认分区物料清单属于盘点工具业务数据，由盘点工具维护。

推荐同步方式：

```text
基础数据库管理系统
  -> 定时同步 / 增量同步 / 手动同步
盘点工具 material、store 缓存表
  -> 模板、任务、录入、汇总
```

推荐服务：

```text
MasterDataSyncService
MaterialCacheService
StoreCacheService
```

推荐内部接口：

```text
POST /api/internal/master-data/sync/materials
POST /api/internal/master-data/sync/stores
POST /api/internal/master-data/sync/all
```

首期如果基础系统接口暂未就绪，可以先用 seed 数据模拟同步结果，但代码结构仍应按同步缓存设计。

## 9. Service 设计原则

- Controller 只做参数接收和返回。
- DTO 和 Entity 不混用。
- 核心业务规则必须放在 Service。
- 涉及多表写入的流程必须使用 `@Transactional`。
- 提交任务必须单独封装为明确方法，例如 `submitTask(taskId, currentUser)`。
- 不要在前端依赖业务状态判断，后端必须二次校验。

## 10. 推荐核心服务

```text
MasterDataSyncService
MaterialService
StoreService
InventoryTemplateService
TemplateZoneService
TemplateZoneMaterialService
InventoryTaskService
TaskSnapshotService
StoreTaskService
StoreInputService
TaskSubmitService
TaskSummaryService
StoreDefaultListService
```

## 11. 事务边界

必须加事务的方法：

- 主数据同步批量写入。
- 创建月盘任务并生成快照。
- 保存分区录入。
- 调整任务分区与物料。
- 提交任务。
- 提交后更新门店默认清单。

提交任务事务必须覆盖：

- 校验任务。
- 校验录入完整性。
- 生成汇总。
- 更新任务状态。
- 写入提交信息。
- 更新门店默认清单。

## 12. 进度计算

任务进度建议实时计算：

```text
totalMaterialCount = task_zone_material count
enteredMaterialCount = input_status in (entered, zero_entered)
notEnteredMaterialCount = input_status = not_entered
```

分区进度建议实时计算：

```text
zoneTotal = count by task_zone_id
zoneEntered = input_status in (entered, zero_entered)
```

分区状态：

```text
zoneEntered = 0 -> not_started
0 < zoneEntered < zoneTotal -> in_progress
zoneEntered = zoneTotal -> completed
```

任务状态更新：

- 保存录入后，如果原状态是 `not_started` 且存在已录入物料，可更新为 `in_progress`。
- 已提交任务状态不得回退。

## 13. 测试要求

后端每个核心模块至少覆盖：

- 正常路径。
- 参数校验。
- 状态限制。
- 重复数据限制。
- 权限限制。

最高优先级测试：

- 物料和门店同步后可查询。
- 停用物料不可新加入模板。
- 停用门店不可新建任务。
- 创建任务快照后，修改模板不影响任务。
- `input_qty = null` 与 `input_qty = 0` 区分正确。
- 未录完不能提交。
- 提交汇总正确。
- 提交后默认清单回写正确。
