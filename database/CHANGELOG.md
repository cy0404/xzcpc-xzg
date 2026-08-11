# 数据库变更记录 (Changelog)

> 盘点工具 1.0 (象掌柜) — 所有数据库迁移变更按时间顺序记录

---

## 变更概览

| 编号 | 日期 | 文件 | 说明 |
|------|------|------|------|
| M01 | 2025-06-01 | `migration-del-flag.sql` | 全部核心表添加逻辑删除字段 del_flag |
| M02 | 2025-06-01 | `migration-add-id-bizcode.sql` | 统一主键 id + 业务编码 biz_code，FK 切换到新 id |
| M03 | 2025-06-01 | `migration-add-version.sql` | 核心业务表添加乐观锁 version 字段 |
| M04 | 2025-06-01 | `migration-production.sql` | 生产库主键改造（阶段1-5完整版） |
| M05 | 2025-06-01 | `migration-production-resume.sql` | 生产库迁移续跑（中断恢复） |
| M06 | 2025-06-01 | `migration-production-fix.sql` | FK 切换补齐（幂等脚本） |
| M07 | 2025-06-01 | `migration-add-store-snapshot.sql` | task 表增加门店快照字段 |
| M08 | 2025-06-02 | `migration-rename-snapshot-columns.sql` | task_zone_material 去掉字段 _snapshot 后缀 |
| M09 | 2025-06-02 | `migration-log.sql` | 新建 operation_log + login_log 日志表 |
| M10 | 2025-06-03 | `migration-multi-role.sql` | admin_permission.role 支持逗号多角色 |
| M11 | 2025-06-04 | `migration-mp.sql` | 小程序端：zone_saved、store_manager_session、移除 manager_phone |
| M12 | 2025-06-04 | `migration-mp-store-bind.sql` | 小程序端 store_manager_session 增加门店绑定字段 |
| M13 | 2025-06-04 | `migration-mp-wx-nickname.sql` | store_manager_session 增加微信昵称字段 |
| M14 | 2025-06-10 | `migration-task-zone-material-unit-price-snapshot.sql` | task_zone_material 增加单价快照 |
| M15 | 2025-06-10 | `migration-add-unit-inputs.sql` | task_zone_material 增加多单位录入 JSON 字段 |
| M16 | 2025-06-10 | `migration-add-warehouse-code.sql` | task + expense_record 增加仓库编码快照 |
| M17 | 2025-06-10 | `migration-add-index-zone-material.sql` | template_zone_material 性能索引优化 |
| M18 | 2025-06-10 | `migration-add-store-info.sql` | 新建 store_info 门店信息本地表 |
| M19 | 2025-06-17 | `migration-add-indexes.sql` | 全库性能索引补充 |
| M20 | 2025-06-22 | `migration-mp-staff-registration.sql` | 员工表扩展 + 登记申请表 |
| M21 | 2025-06-22 | `migration-task-material-snapshot.sql` | task_zone_material 盘点结果快照字段（多单位换算） |
| M22 | 2025-06-26 | `migration-employee-store-openid-unique.sql` | employee 表唯一约束防并发重复 |
| M23 | 2025-06-26 | `migration-material-qrcode.sql` | material 表增加二维码路径字段 |
| M24 | 2025-06-26 | `migration-store-work-hours.sql` | 新建 store_work_hours 门店工时表 |
| M25 | 2025-06-26 | `migration-zone-timestamps.sql` | task_zone / task_zone_material 增加时间戳 |
| M26 | 2025-06-26 | `owner-register.sql` | 新建 store_contact + owner_registration 老板绑定相关表 |

---

## 详细变更

### M01 — 逻辑删除 (2025-06-01)

**文件**: `migration-del-flag.sql`

所有核心业务表增加 `del_flag INT DEFAULT 0` 字段（0=正常，1=已删除），配合 MyBatis-Plus `@TableLogic` 注解实现软删除。

**影响表**: template, template_zone, template_zone_material, task, task_zone, task_zone_material, task_material_summary, store_manager_session

---

### M02 — 统一主键 + 业务编码 (2025-06-01)

**文件**: `migration-add-id-bizcode.sql`

核心改造：每表新增 `id INT AUTO_INCREMENT PRIMARY KEY` 作为统一自增主键，新增 `biz_code VARCHAR(50)` 业务编码（前缀+补齐，如 MAT00000001），旧主键降级为普通 INT 列保留兼容。同时切换所有外键引用到新主键。

**影响表**: material, template, template_zone, template_zone_material, store_zone_material, task, task_zone, task_zone_material, task_material_summary, store_manager_session

**业务编码生成规则**:
- MAT + 8位ID → material
- TPL + 8位ID → template
- TZ + 8位ID → template_zone
- TZM + 8位ID → template_zone_material + task_zone_material
- SZM + 8位ID → store_zone_material
- TASK + 8位ID → task
- TKZ + 8位ID → task_zone
- TMS + 8位ID → task_material_summary
- SMS + 8位ID → store_manager_session

---

### M03 — 乐观锁 (2025-06-01)

**文件**: `migration-add-version.sql`

核心业务表增加 `version INT DEFAULT 0` 字段，配合 MyBatis-Plus `@Version` 注解。

**影响表**: template, template_zone, template_zone_material, store_zone_material, task, task_zone, task_zone_material

---

### M04~M06 — 生产库迁移脚本组 (2025-06-01)

**文件**: `migration-production.sql`, `migration-production-resume.sql`, `migration-production-fix.sql`

针对已有数据的生产库执行主键切换的三阶段脚本。包含存储过程 `add_col_if_missing` 安全加列、FK 删除/切换/重建、旧主键同步为 id。

⚠️ 全新部署无需执行，仅用于存量数据库升级。

---

### M07 — 门店快照 (2025-06-01)

**文件**: `migration-add-store-snapshot.sql`

task 表增加门店快照字段，创建任务时从外部 API 获取并固化。

**新增列**:
- `task.store_name VARCHAR(200)` — 门店名称快照
- `task.store_code VARCHAR(50)` — 门店编码快照
- `task.xiaochengxuid VARCHAR(100)` — 小程序UID快照

存量数据从 store_manager_session 回填。

---

### M08 — 快照列重命名 (2025-06-02)

**文件**: `migration-rename-snapshot-columns.sql`

task_zone_material 表去掉字段名中的 `_snapshot` 后缀，因表名本身已标识为快照表。

**重命名**:
- `material_name_snapshot` → `material_name`
- `spec_snapshot` → `spec`
- `unit_snapshot` → `unit`
- `inventory_unit_snapshot` → `inventory_unit`

---

### M09 — 日志表 (2025-06-02)

**文件**: `migration-log.sql`

新建操作日志和登录日志两张表，配合 `@OpLog` AOP 和异步日志写入。

**新建表**:
- `operation_log` — 操作日志（user_id, username, module, operation, description, request_ip, status, error_msg, created_at）
- `login_log` — 登录日志（user_id, username, login_type, status, fail_reason, request_ip, user_agent, created_at）

---

### M10 — 多角色支持 (2025-06-03)

**文件**: `migration-multi-role.sql`

admin_permission 表 role 字段从单值改为逗号分隔多值，支持一个用户拥有多个管理角色。

**变更**: `admin_permission.role` VARCHAR(50) → VARCHAR(500)，值如 `headquarters_admin,finance_admin`

---

### M11~M13 — 小程序端改造 (2025-06-04)

**文件**: `migration-mp.sql`, `migration-mp-store-bind.sql`, `migration-mp-wx-nickname.sql`

| 变更 | 说明 |
|------|------|
| task_zone 加 `zone_saved` | 店长点过「保存本分区」后置 1 |
| 新建 store_manager_session | 微信 openid 登录会话表 |
| store_manager_session 加 store_id/store_name | 登录后绑定门店 |
| store_manager_session 加 wx_nickname | 微信昵称展示 |
| 移除 manager_phone | 登录不再校验手机号 |

---

### M14 — 单价快照 (2025-06-10)

**文件**: `migration-task-zone-material-unit-price-snapshot.sql`

**新增列**: `task_zone_material.unit_price_snapshot DECIMAL(10,2)` — 录入时的盘点单价快照

---

### M15 — 多单位录入 (2025-06-10)

**文件**: `migration-add-unit-inputs.sql`

**新增列**: `task_zone_material.unit_inputs TEXT` — 各单位输入拆分 JSON，如 `{"箱":"1","瓶":"2"}`

---

### M16 — 仓库编码 (2025-06-10)

**文件**: `migration-add-warehouse-code.sql`

**新增列**:
- `task.warehouse_code VARCHAR(50)` — 仓库编码快照
- `expense_record.warehouse_code VARCHAR(50)` — 仓库编码快照

---

### M17 — 分区物料索引优化 (2025-06-10)

**文件**: `migration-add-index-zone-material.sql`

**新增索引**: `template_zone_material.idx_zone_del_sort (zone_id, del_flag, sort_no)` — 覆盖 WHERE + ORDER BY，避免 filesort

---

### M18 — 门店信息本地表 (2025-06-10)

**文件**: `migration-add-store-info.sql`

**新建表**: `store_info` — 替代 Spring Cache 存储门店信息，数据来源为外部 API 定时同步。包含 store_id, store_name, store_code, xiaochengxuid, cangkuid, qr_code 等字段。

---

### M19 — 全库性能索引 (2025-06-17)

**文件**: `migration-add-indexes.sql`

**新增索引**:

| 表 | 索引名 | 列 |
|---|--------|-----|
| employee | idx_employee_openid_status | (openid, status) |
| employee | idx_employee_store_id | (store_id) |
| material | idx_material_material_id | (material_id) |
| material_inventory_rule | idx_rule_material_id | (material_id) |
| task | idx_task_store_id | (store_id) |
| task_zone_material | idx_tzm_task_id | (task_id) |

---

### M20 — 员工登记 (2025-06-22)

**文件**: `migration-mp-staff-registration.sql`

**employee 表新增列**: openid, emergency_contact_name, emergency_contact_phone, remark

**新建表**: `employee_registration_application` — 门店员工登记申请表，含审批流程字段（status, reject_reason, approver_openid, approved_at 等）

---

### M21 — 盘点结果快照 (2025-06-22)

**文件**: `migration-task-material-snapshot.sql`

task_zone_material 增加盘点结果快照字段，固化管理端录入时的原始数据和多单位换算规则。

**新增列**:
| 列名 | 类型 | 说明 |
|------|------|------|
| input_mode | VARCHAR(20) | 录入模式：unit / weight |
| input_original_qty | DECIMAL(18,4) | 用户原始录入数量 |
| input_original_unit | VARCHAR(50) | 用户原始录入单位 |
| base_unit_snapshot | VARCHAR(50) | 基础盘点单位快照 |
| rule_id_snapshot | VARCHAR(50) | 物料盘点规则ID快照 |
| base_qty | DECIMAL(18,4) | 折算后的基础单位数量 |
| conversion_snapshot | TEXT | 本次换算规则 JSON 快照 |

存量数据兼容：把旧 `input_qty` 视为基础单位数量回填 `base_qty`。

---

### M22 — 员工唯一约束 (2025-06-26)

**文件**: `migration-employee-store-openid-unique.sql`

**新增约束**: `employee.uk_employee_store_openid (store_id, openid)` — 防并发重复插入

执行前先清理重复数据（保留 id 最小的一条）。

---

### M23 — 物料二维码 (2025-06-26)

**文件**: `migration-material-qrcode.sql`

**新增列**: `material.qr_code VARCHAR(500)` — 物料二维码图片路径

---

### M24 — 门店工时 (2025-06-26)

**文件**: `migration-store-work-hours.sql`

**新建表**: `store_work_hours` — 门店月度工时录入（record_id, store_id, store_name, record_time, hours, employee_id, employee_name, created_at, updated_at, del_flag）

---

### M25 — 分区时间戳 (2025-06-26)

**文件**: `migration-zone-timestamps.sql`

**新增列**:
- `task_zone_material.entered_at DATETIME` — 物料录入时间
- `task_zone.saved_at DATETIME` — 分区保存时间

---

### M26 — 老板注册表 (2025-06-26)

**文件**: `owner-register.sql`

**新建表**:
- `store_contact` — 门店联系人信息（总部录入，用于匹配老板身份）
- `owner_registration` — 老板扫码登记表（openid, bind_code, name, phone, store_id, status）

---

## 数据库当前总览

**总表数**: 20 张
**总索引数**: 40+

### 核心业务表 (10张)

| 表名 | 说明 | 关键特性 |
|------|------|----------|
| material | 物料主数据 | 外部同步 + 本地字段 |
| material_inventory_rule | 物料盘点规则 | 基础单位/盘点单位串/单价 |
| material_conversion_rule | 物料换算关系 | unit/weight 两类换算链 |
| template | 盘点模板 | 状态：启用/停用/草稿 |
| template_zone | 模板分区 | sort_no 排序 |
| template_zone_material | 模板分区-物料 | 物料快照 |
| task | 月盘任务 | 状态机 + 门店快照 |
| task_zone | 任务分区快照 | source_type + zone_saved |
| task_zone_material | 任务分区物料快照 | 多单位录入 + 换算快照 |
| task_material_summary | 跨分区汇总 | 提交时自动生成 |
| store_zone_material | 门店默认分区物料 | 任务提交后更新 |

### 门店与权限表 (4张)

| 表名 | 说明 |
|------|------|
| store_info | 门店信息本地存储 |
| store_manager_session | 小程序店长会话 |
| admin_permission | 总部飞书用户权限 |
| owner_bind_application | 老板微信绑定申请 |

### 支出子系统 (3张)

| 表名 | 说明 |
|------|------|
| expense_type | 支出类型（一级分类） |
| expense_item | 支出项目（二级分类） |
| expense_record | 支出记录 |

### 人员子系统 (3张)

| 表名 | 说明 |
|------|------|
| employee | 员工主数据 |
| employee_registration_application | 员工登记申请 |
| owner_registration | 老板扫码登记 |

### 门店运营 (3张)

| 表名 | 说明 |
|------|------|
| store_work_hours | 门店月度工时 |
| store_contact | 门店联系人 |

### 日志 (2张)

| 表名 | 说明 |
|------|------|
| operation_log | 操作日志 |
| login_log | 登录日志 |
