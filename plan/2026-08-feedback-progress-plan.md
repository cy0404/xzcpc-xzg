# 扫码反馈「顾客查处理进度」功能设计

**版本**：v0.1（评审稿）
**日期**：2026-08-27
**状态**：待评审（产品决策已确认，未动代码）

---

## 1. 需求背景

顾客扫码提交问题反馈后，目前是「提交即结束」：顾客没有任何渠道看到反馈的处理进度，后台也只能查看、不能标记处理状态。本次新增：

1. 提交反馈时**必填手机号**（只存后 4 位，不存全号）
2. 顾客凭**手机尾号 + 门店**查询自己反馈的处理进度：状态 + 处理说明 + 时间线
3. 后台可**流转状态**（待处理 → 处理中 → 已处理/已关闭）并填写处理说明

不碰小程序、不碰飞书通知系统，全部在现有扫码反馈功能上增量开发。

---

## 2. 现状盘点（已实现，不要重复做）

| 组件 | 位置 | 说明 |
|------|------|------|
| 反馈表 | `issue_feedback`（`database/migration-add-feedback-record.sql`） | id/feedback_type/store_id/store_name/content/images/created_at/updated_at/del_flag/version |
| 公开接口 | `FeedbackPublicController`（server 模块） | `GET /api/xzg/feedback/options`、`/stores`、`POST /submit`、`POST /upload`；mobile UA 校验 + IP 限频（3次/分钟、20次/天）；图片上传代理 mp-server |
| 业务实现 | `IssueFeedbackServiceImpl` | 攒批入库（200条/10分钟）、@PreDestroy 停机 flush、失败重入队、门店名归一、IP 滑窗限频 |
| 后台接口 | `FeedbackManageController`（server 模块） | 列表筛选/详情/导出（admin 登录保护） |
| H5 页面 | `xzcpc-xzg/upload/h5/feedback.html` | 单文件自包含，类型胶囊 + 门店搜索抽屉 + 内容 + 上传≤3张 + 提交 |
| 后台台账 | `admin/src/views/feedback/FeedbackList.vue` + `admin/src/api/feedback.ts` | 筛选 + 表格 + 抽屉详情 + 图片预览 + Excel 导出 |

---

## 3. 产品决策（已确认）

| # | 决策 | 说明 |
|---|------|------|
| 1 | 手机号**必填** | 11 位大陆手机号格式校验（`1[3-9]\d{9}`）；不填不能提交 |
| 2 | **只存后 4 位** | 库里只有 `phone_tail`，不存全号，不碰敏感数据；代价：将来无法直接回访（如需回访再另加字段加密存全号） |
| 3 | 查询凭「手机尾号 + 门店」 | 尾号 4 位 + 选择门店；**匹配多条时按提交时间列出，顾客点自己的那条**（门店反馈量小，撞车概率低） |
| 4 | 详情防遍历 | 点开详情时**再验一次尾号**，防止同门店同尾号撞车场景下看错、也防翻别人反馈 |
| 5 | 状态枚举 | `pending`（待处理）→ `processing`（处理中）→ `done`（已处理）/ `closed`（已关闭）；后台手动流转，无自动节点 |
| 6 | 处理说明 | 后台改状态时填写，顾客查询页展示 |
| 7 | 时间线 | 提交时间 `created_at` → 处理中 `processing_at` → 完成 `processed_at`，三节点展示 |
| 8 | 查询防刷 | 查询接口复用 IP 滑窗限频，放宽到 **10 次/分钟**（提交保持 3 次/分钟） |

---

## 4. 数据模型改动

迁移 SQL：`database/migration-add-feedback-phone-status.sql`（用户手动执行，新增字段，不动已有数据）

```sql
ALTER TABLE issue_feedback
    ADD COLUMN phone_tail     CHAR(4)      NOT NULL DEFAULT ''  COMMENT '手机号后4位（必填，不存全号）',
    ADD COLUMN status         VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending待处理|processing处理中|done已处理|closed已关闭',
    ADD COLUMN process_note   VARCHAR(500) DEFAULT NULL COMMENT '处理说明',
    ADD COLUMN processing_at  DATETIME     DEFAULT NULL COMMENT '进入处理中时间',
    ADD COLUMN processed_at   DATETIME     DEFAULT NULL COMMENT '处理完成时间';

ALTER TABLE issue_feedback
    ADD KEY idx_phone_status (phone_tail, status);
```

---

## 5. 接口设计

### 5.1 公开接口（H5 查询用，`FeedbackPublicController` 新增）

| 方法 | 路径 | 入参 | 返回 | 说明 |
|------|------|------|------|------|
| GET | `/api/xzg/feedback/query` | `phoneTail`(4位) + `storeId` | 匹配记录列表：`[{id, createdAt, status}]` | **只返回 id/时间/状态**，不返回内容，防撞车场景先选人 |
| GET | `/api/xzg/feedback/query/{id}` | `phoneTail`(必传) | 详情：`{id, feedbackType, storeName, content, images, status, processNote, createdAt, processingAt, processedAt}` | **校验 phoneTail 与记录一致**，否则 404；图片 URL 做内网 IP → 生产域名替换（照 admin 端逻辑） |

- 查询接口**不限制 UA**（后台也方便自测），用 IP 限频 10 次/分钟防刷
- 列表与详情均可走现有 `issue_feedback` 查询，无需新表

### 5.2 后台接口（`FeedbackManageController` 新增）

| 方法 | 路径 | 入参 | 说明 |
|------|------|------|------|
| PUT | `/api/admin/feedback/{id}/status` | `{status, processNote}` | 流转状态 + 填说明；置 processing 记 `processing_at`，置 done/closed 记 `processed_at`；已关闭不可再改（可选约束） |
| GET | `/api/admin/feedback/list` | 筛选增加 `status` | 现有列表接口加 status 筛选参数 |
| GET | `/api/admin/feedback/{id}` | — | 详情返回值加 `phoneTail/status/processNote/processingAt/processedAt` |

---

## 6. H5 页面改动（`upload/h5/feedback.html`）

1. **提交表单**：反馈内容上方加「手机号」输入框（必填，11 位校验，`type=tel` + 数字键盘），提交参数加 `phone`
2. **提交成功提示**：改为「反馈已收到，可凭手机尾号查询处理进度」
3. **查询入口**：表单页底部加「查询处理进度」文字入口 → 切到查询视图
4. **查询视图**（同文件内 view 切换）：
   - 输入手机尾号（4 位）+ 复用现有门店搜索抽屉选门店 → 查询
   - 结果列表：提交时间 + 状态徽标（待处理/处理中/已处理/已关闭，四色），点击进入详情
   - 详情：完整内容 + 图片预览（复用现有上传后的图片渲染）+ 状态 + 处理说明 + 时间线（三节点）
   - 详情需再次输入尾号验证（防遍历）
5. 样式照现有表单令牌（背景 `#F7F8F6`、主绿 `#2F8F57`、16px 圆角卡片）

---

## 7. 后台改动（admin）

| 文件 | 改动 |
|------|------|
| `admin/src/api/feedback.ts` | 新增 `updateFeedbackStatus(id, body)`；列表/详情类型加 status 字段 |
| `admin/src/views/feedback/FeedbackList.vue` | ① 表格加「状态」列（tag 四色：待处理橙/处理中蓝/已处理绿/已关闭灰）+「手机尾号」列 ② 筛选区加状态下拉 ③ 详情抽屉加状态下拉 + 处理说明输入框 + 「保存状态」按钮，保存后刷新列表 |

---

## 8. 涉及文件清单

| 类型 | 文件 | 动作 |
|------|------|------|
| SQL | `database/migration-add-feedback-phone-status.sql` | 新增 |
| 后端 | `server/.../entity/IssueFeedback.java` | 加 5 字段 |
| 后端 | `server/.../controller/FeedbackPublicController.java` | 加 2 个查询接口 |
| 后端 | `server/.../controller/FeedbackManageController.java` | 加状态流转 + 列表筛选 |
| 后端 | `server/.../service/IssueFeedbackService(+Impl).java` | 加查询/流转方法 |
| H5 | `xzcpc-xzg/upload/h5/feedback.html` | 手机号框 + 查询视图 |
| 后台 | `admin/src/api/feedback.ts` | 状态接口 + 类型 |
| 后台 | `admin/src/views/feedback/FeedbackList.vue` | 状态列/筛选/流转抽屉 |

> 全部增量改动，不修改其他功能。

---

## 9. 验证路径

1. 执行迁移 SQL → `ALTER TABLE` 生效（本地 MySQL）
2. 后端起服：`GET /api/xzg/feedback/query` 空尾号/缺门店返回校验错误；提交一条带手机号的反馈后，按尾号+门店能查到；错尾号查详情返回 404
3. H5（本地浏览器 / 传服务器微信扫码）：手机号不填被拦；提交成功提示新文案；查询视图尾号+门店出列表、撞车场景多条可点选、详情图片可看、时间线随后台改状态逐步点亮
4. 后台：状态筛选生效；改状态 + 填说明保存后，H5 查询页同步看到
5. 限频：查询接口连打超 10 次/分钟被拦

## 10. 部署步骤（按项目惯例）

1. 用户手动执行迁移 SQL（数据库变更不自动执行）
2. `feedback.html` 传服务器 `upload/h5/`（无需打包，线上即生效）
3. 后端 `mvn clean package -DskipTests`，重启 server（4026）
4. admin 改动随下次 `npm run build` 打进 jar

---

## 11. 风险与注意

- **只存尾号**：无法回访顾客，后续如要回访需加字段加密存全号（本次不做）
- **撞车误读**：同门店同尾号多人提交时靠「时间列表选择」+「详情验尾号」双重兜底，仍有极小概率选错看错，属可接受
- **存量数据**：迁移 SQL 对已有记录的 `phone_tail` 默认空串——旧数据查不到进度，可接受（新提交才查）
- **不新增通知**：顾客不主动查就不知道进度，纯自助查询，与现有"无通知"约定一致
