# 01 数据库设计

## 1. 设计目标

数据库需要支撑以下核心能力：

- 从基础数据库管理系统同步物料和门店主数据。
- 总部维护盘点模板、分区、模板分区物料关系。
- 门店维护默认分区物料清单。
- 总部创建月盘任务。
- 创建任务时生成任务快照。
- 门店按分区录入。
- 提交时生成物料汇总。
- 提交后更新门店默认清单。

## 2. 通用字段约定

建议所有业务表包含：

```text
id            bigint primary key
created_at    datetime not null
updated_at    datetime not null
```

需要软删除时增加：

```text
deleted       tinyint not null default 0
```

需要启停时增加：

```text
status        varchar(32) not null
```

排序统一使用：

```text
sort_no       int not null default 0
```

来自基础数据库管理系统的同步缓存表需要增加：

```text
external_id       varchar(128) not null
source_system     varchar(64) not null
sync_status       varchar(32) not null
last_synced_at    datetime null
```

金额或数量不使用浮点类型，盘点数量建议：

```text
decimal(18, 4)
```

## 3. 表清单

```text
store                         # 门店同步缓存
user_account                  # 用户账号
material                      # 物料同步缓存
inventory_template            # 盘点模板
template_zone                 # 模板分区
template_zone_material        # 模板分区物料关系
store_zone_material           # 门店默认分区物料清单
inventory_task                # 月盘任务
task_zone                     # 任务分区快照
task_zone_material            # 任务分区物料快照
task_material_summary         # 任务物料汇总
```

## 4. store

用途：保存从基础数据库管理系统同步过来的门店信息。

盘点工具不在本表中直接维护门店主数据。门店名称、编码、状态以基础数据库管理系统为准。

字段：

```text
id            bigint primary key
external_store_id varchar(128) not null
store_name    varchar(128) not null
store_code    varchar(64)
status        varchar(32) not null
source_system varchar(64) not null
sync_status   varchar(32) not null
last_synced_at datetime null
created_at    datetime not null
updated_at    datetime not null
```

建议索引：

```text
idx_store_status(status)
uk_store_code(store_code)
uk_store_external_id(external_store_id)
```

## 5. user_account

用途：保存总部用户和门店店长用户。首期可以简化权限模型，但后端仍应保留角色字段。

字段：

```text
id            bigint primary key
username      varchar(128) not null
display_name  varchar(128) not null
role          varchar(32) not null
store_id      bigint null
status        varchar(32) not null
created_at    datetime not null
updated_at    datetime not null
```

角色建议：

```text
admin
store_manager
```

建议索引：

```text
idx_user_store_id(store_id)
idx_user_role(role)
uk_user_username(username)
```

## 6. material

用途：保存从基础数据库管理系统同步过来的物料信息。

盘点工具不在本表中直接维护物料主数据。物料名称、规格、单位、状态以基础数据库管理系统为准。

字段：

```text
id              bigint primary key
external_material_id varchar(128) not null
material_name   varchar(128) not null
spec            varchar(128) not null
unit            varchar(32) not null
category        varchar(64) null
status          varchar(32) not null
source_system   varchar(64) not null
sync_status     varchar(32) not null
last_synced_at  datetime null
created_at      datetime not null
updated_at      datetime not null
```

状态建议：

```text
enabled
disabled
```

建议索引：

```text
idx_material_status(status)
idx_material_name(material_name)
uk_material_external_id(external_material_id)
```

## 7. inventory_template

用途：总部维护标准盘点模板。

字段：

```text
id              bigint primary key
template_name   varchar(128) not null
status          varchar(32) not null
created_at      datetime not null
updated_at      datetime not null
```

状态建议：

```text
draft
enabled
disabled
```

建议索引：

```text
idx_template_status(status)
```

## 8. template_zone

用途：模板中的分区。

字段：

```text
id            bigint primary key
template_id   bigint not null
zone_name     varchar(128) not null
sort_no       int not null
created_at    datetime not null
updated_at    datetime not null
```

建议索引：

```text
idx_template_zone_template_id(template_id)
uk_template_zone_name(template_id, zone_name)
```

## 9. template_zone_material

用途：模板分区和物料的关系。

字段：

```text
id            bigint primary key
zone_id       bigint not null
material_id   bigint not null
sort_no       int not null
created_at    datetime not null
updated_at    datetime not null
```

业务规则：

- 同一分区不可重复添加同一物料。
- 同一物料可出现在模板的多个分区。

建议索引：

```text
idx_tzm_zone_id(zone_id)
idx_tzm_material_id(material_id)
uk_tzm_zone_material(zone_id, material_id)
```

## 10. store_zone_material

用途：门店默认分区物料清单，用于后续任务生成。

字段：

```text
id            bigint primary key
store_id      bigint not null
zone_name     varchar(128) not null
material_id   bigint not null
sort_no       int not null
status        varchar(32) not null
source_type   varchar(32) not null
created_at    datetime not null
updated_at    datetime not null
```

状态建议：

```text
enabled
disabled
```

来源建议：

```text
template
task_adjust
submit_update
```

业务规则：

- 该表不是任务历史。
- 该表属于盘点工具业务数据，不属于基础数据库管理系统。
- 提交成功后才更新。
- 录入为 0 的分区物料关系，从该表移除或置为 disabled。
- 推荐首期直接物理删除对应关系，若需要审计再改为软删除。

建议索引：

```text
idx_szm_store_id(store_id)
idx_szm_material_id(material_id)
uk_szm_store_zone_material(store_id, zone_name, material_id)
```

## 11. inventory_task

用途：月盘任务主表。

字段：

```text
id              bigint primary key
store_id        bigint not null
template_id     bigint not null
task_name       varchar(128) not null
task_month      varchar(7) not null
deadline        datetime not null
status          varchar(32) not null
created_by      bigint not null
submitted_by    bigint null
submitted_at    datetime null
created_at      datetime not null
updated_at      datetime not null
```

任务状态：

```text
not_started
in_progress
submitted
```

建议索引：

```text
idx_task_store_id(store_id)
idx_task_month(task_month)
idx_task_status(status)
idx_task_template_id(template_id)
```

## 12. task_zone

用途：任务分区快照。

字段：

```text
id            bigint primary key
task_id       bigint not null
zone_name     varchar(128) not null
sort_no       int not null
source_type   varchar(32) not null
created_at    datetime not null
updated_at    datetime not null
```

来源建议：

```text
template
store_default
task_adjust
```

建议索引：

```text
idx_task_zone_task_id(task_id)
uk_task_zone_name(task_id, zone_name)
```

## 13. task_zone_material

用途：任务分区物料快照，也是门店录入数据保存表。

字段：

```text
id                       bigint primary key
task_id                  bigint not null
task_zone_id             bigint not null
material_id              bigint not null
material_name_snapshot   varchar(128) not null
spec_snapshot            varchar(128) not null
unit_snapshot            varchar(32) not null
sort_no                  int not null
input_qty                decimal(18, 4) null
remark                   varchar(512) null
input_status             varchar(32) not null
created_at               datetime not null
updated_at               datetime not null
```

录入状态：

```text
not_entered
entered
zero_entered
```

关键规则：

- `input_qty` 必须允许 `null`。
- `input_qty = null` 表示未录入。
- `input_qty = 0` 表示已录入为 0。
- 同一任务分区内不可重复添加同一物料。

建议索引：

```text
idx_tzm_task_id(task_id)
idx_tzm_task_zone_id(task_zone_id)
idx_tzm_material_id(material_id)
uk_task_zone_material(task_zone_id, material_id)
```

## 14. task_material_summary

用途：提交后保存任务物料汇总结果。

字段：

```text
id             bigint primary key
task_id        bigint not null
material_id    bigint not null
material_name  varchar(128) not null
spec           varchar(128) not null
unit           varchar(32) not null
zone_count     int not null
total_qty      decimal(18, 4) not null
created_at     datetime not null
updated_at     datetime not null
```

建议索引：

```text
idx_summary_task_id(task_id)
uk_summary_task_material(task_id, material_id)
```

## 15. 重要约束

- 表结构应优先服务业务正确性，不要为了页面展示反向污染核心模型。
- 基础数据库管理系统是物料和门店的唯一主数据源。
- `material` 和 `store` 是盘点工具的同步缓存表，不是主数据维护表。
- 盘点工具不得直接新增、编辑、删除物料和门店主数据。
- 任务快照表保存快照字段，避免物料主数据变更影响历史任务。
- 汇总表在提交时生成，任务提交后不可再变更。
- 分区状态和任务进度可以实时计算，不必冗余存储。
