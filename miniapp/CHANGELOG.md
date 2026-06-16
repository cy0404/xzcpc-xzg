# 小程序端变更记录

## 2026-05-30

### 🐛 Bug 修复

- **物料排序保存失败**：`zone-adjust` 页面排序接口传参错误，`sortMaterials` 之前传的是 `materialId`，后端 `selectById` 按主键 `taskZoneMaterialId` 查询导致永远查不到。已修复为传 `taskZoneMaterialId`。
- **保存分区阻断问题**：`zone-entry` 页面点击「保存本分区」时，前端强制要求所有物料数量必须填写完毕才能保存，导致部分录入场景无法保存。已改为：未填的物料跳过，仅保存已录入数据。

### ✨ UI 优化

- **去掉所有 `→` 箭头**：以下页面的底部按钮和卡片中的 `→`/`›` 箭头已全部移除：
  - `store-pick` — 确认门店选择按钮
  - `task/list` — 继续盘点按钮、查看结果链接、底部固定按钮
  - `task/detail` — 查看盘点结果、继续录入下一分区、查看盘点汇总
- **按钮居中**：`task/list`、`task/detail`、`task/result` 底部操作按钮统一改为居中自适应宽度（`min-width: 480rpx`）
- **按钮固定右侧**：
  - `store-pick` — 确认按钮固定右侧，左侧显示已选门店/占位文字，不再上下跳动
  - `task/list` — 「切换」按钮固定右侧，门店名过长时自动截断，不再随名称长度左右移动
- **任务卡片间距**：`task/list` 当前任务/历史任务卡片之间增加 20rpx 间距
- **盘点单位字体**：`zone-entry`、`zone-adjust`、`material-pick` 中盘点单位的值（如"个""箱"）改为黑色字体 `#1A1A1A`，与灰色标签区分

### 🏗 后端逻辑调整（mp 模块）

- **保存分区 (`saveZone`)**：移除"所有物料必须填写完毕"的校验，允许部分录入后保存。有值的物料更新 `inputStatus = entered`，空的跳过。
- **完成分区统计**：`getDetail`、`summary`、`submit` 三个接口中，已完成分区数从按 `zoneSaved=1` 统计改为**按分区进度 100%**（所有物料均已录入）统计。每个分区新增 `isComplete` 字段。
- **提交校验**：`submit` 从校验 `zoneSaved` 改为校验分区内所有物料是否均已录入。

### 🔧 配置变更

- `constants.ts` — `BASE_URL` 调整为 `http://119.45.162.160:30261/storeInventory/api/mp`（临时开发用，备案完成后切回 HTTPS 域名）
- `vite.config.ts` — proxy target 同步更新
