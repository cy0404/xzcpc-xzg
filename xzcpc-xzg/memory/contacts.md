---
updated: 2026-07-03
importance: 3
---

# 模块与外部系统

## 内部模块关键类

| 模块 | 文件数 | 关键类 |
|------|--------|--------|
| common | 31 | `R.java`, `GlobalExceptionHandler`, `CacheConfig`, `AdminContextHolder` |
| template | 25 | `MaterialController`, `MaterialRuleController`, `TemplateController` |
| task | 22 | `TaskController`, `StoreController`, `TemplateChangeListener` |
| expense | 23 | `ExpenseController`, `ExpenseTypeController`, `SelfPurchaseMaterialController` |
| people | 15 | `EmployeeController`, `OwnerRegistrationController` |
| server | 21 | `InventoryApplication`, `FeishuAuthController`, `AdminLoginInterceptor` |
| mp | 81 | `MpAuthController`, `MpTaskController`, `MpZoneController`（共13个控制器） |
| mp-server | 6 | `InventoryMpApplication`, `UploadResourceConfig` |

## 外部系统

| 系统 | 用途 | 集成方式 |
|------|------|----------|
| 企迈开放 API | 门店 + 物料数据同步 | 定时 `@Scheduled` 翻页拉取，upsert |
| 飞书 OAuth | 总部端登录 | 授权码流程 → JWT |
| 微信小程序 | 门店端登录 | `weixin-java-miniapp` 4.6.0 |

## 数据库

| 项 | 值 |
|----|-----|
| 库名 | `store_inventory` |
| 引擎 | MySQL 8.0 InnoDB / utf8mb4 |
| 建表 | `database/schema.sql` |
| 迁移 | `database/migration-*.sql`（28个） |
