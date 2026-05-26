# 03 主数据同步与查询模块

## 1. 目标

接入现有基础数据库管理系统，将物料和门店作为外部主数据引用到盘点工具中。

基础数据库管理系统是物料和门店的唯一主数据源。盘点工具不拥有物料和门店的主数据维护权，只保留本地同步缓存，用于模板配置、任务创建、任务快照和查询展示。

本模块只实现主数据同步与查询，不实现盘点模板、任务、盘点录入。

## 2. 涉及表

```text
material
store
```

字段以 `01-database-design.md` 为准。

## 3. 业务规则

- 基础数据库管理系统是物料和门店的唯一数据源。
- 盘点工具不新增、不编辑、不删除物料和门店。
- 盘点工具本地 `material` 和 `store` 表是同步缓存。
- 本地缓存必须保存外部系统 ID，用于稳定关联和增量同步。
- 模板配置、任务创建和门店默认清单使用盘点工具本地缓存 ID。
- 物料名称、规格、单位、物料状态以基础数据库管理系统为准。
- 门店名称、门店状态以基础数据库管理系统为准。
- 停用物料不可被新添加到模板或任务分区。
- 停用门店不可创建新的月盘任务。
- 已存在历史任务中的物料快照不受基础系统后续修改影响。
- 门店默认分区物料清单属于盘点业务数据，仍由盘点工具维护。

## 4. API

### 4.1 查询物料缓存列表

```text
GET /api/admin/materials
```

查询参数：

```text
keyword     按物料名称或规格模糊查询，可选
status      enabled/disabled，可选
page        页码
pageSize    每页数量
```

返回字段：

```text
id
externalMaterialId
materialName
spec
unit
category
status
sourceSystem
lastSyncedAt
createdAt
updatedAt
```

### 4.2 查询物料缓存详情

```text
GET /api/admin/materials/{id}
```

### 4.3 查询门店缓存列表

```text
GET /api/admin/stores
```

查询参数：

```text
keyword     按门店名称或编码模糊查询，可选
status      enabled/disabled，可选
page        页码
pageSize    每页数量
```

返回字段：

```text
id
externalStoreId
storeName
storeCode
status
sourceSystem
lastSyncedAt
createdAt
updatedAt
```

### 4.4 查询门店缓存详情

```text
GET /api/admin/stores/{id}
```

### 4.5 手动同步物料

```text
POST /api/internal/master-data/sync/materials
```

注意：

- 该接口仅供内部系统或管理员触发。
- 首期也可以不暴露 HTTP 接口，改为定时任务。

### 4.6 手动同步门店

```text
POST /api/internal/master-data/sync/stores
```

### 4.7 手动同步全部主数据

```text
POST /api/internal/master-data/sync/all
```

## 5. 后端建议类

```text
MasterDataSyncController
AdminMaterialQueryController
AdminStoreQueryController
MasterDataSyncService
MaterialService
StoreService
MaterialMapper
StoreMapper
MaterialEntity
StoreEntity

MaterialQueryRequest
MaterialResponse
StoreQueryRequest
StoreResponse
MasterDataSyncResponse
```

## 6. 校验

- `external_material_id` 不能为空且唯一。
- `external_store_id` 不能为空且唯一。
- `materialName`、`spec`、`unit` 来自基础系统，同步时不能为空。
- `storeName` 来自基础系统，同步时不能为空。
- `status` 需要从基础系统状态映射为 `enabled` 或 `disabled`。
- 查询不存在的物料返回 `MATERIAL_NOT_FOUND`。
- 查询不存在的门店返回 `STORE_NOT_FOUND`。

## 7. 不做范围

- 不在盘点工具内新增物料。
- 不在盘点工具内编辑物料名称、规格、单位。
- 不在盘点工具内启停物料。
- 不在盘点工具内新增门店。
- 不在盘点工具内编辑门店。
- 不在盘点工具内启停门店。
- 不做库存数量。
- 不做供应商。
- 不做价格。
- 不做扫码条码。

## 8. 验收标准

- 可以从基础数据库管理系统同步物料。
- 可以从基础数据库管理系统同步门店。
- 可以按关键字查询物料缓存。
- 可以按关键字查询门店缓存。
- 停用物料不可新加入模板。
- 停用门店不可新建月盘任务。
- 停用物料仍可在历史任务结果中展示快照信息。
- 修改基础系统物料后，已创建任务快照不变化。
