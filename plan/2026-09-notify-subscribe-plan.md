# 小程序订阅消息通知系统设计方案（2026-09）

> 状态：设计稿，待审批后实现
> 渠道结论（前期多轮调研已定稿）：**小程序一次性订阅消息**（主动推送）+ **首页待办卡**（降级兜底，复用现有，不建单独通知中心）。企微客服消息/应用消息/群机器人均因窗口或场景限制被排除。

---

## 一、方案总览

```
业务触发点（报损审批/任务下发/员工申请/调货/问题/订货…）
        │  ① 与业务同事务写入 notification_log（status=0 待发送）—— 发送永不阻塞业务
        ▼
NotificationSenderJob（mp-server，每 60s 扫批）
        │  ② 逐条：查该 openid 额度 → 有额度发订阅消息（扣 1）→ 无额度降级站内
        ▼
微信订阅消息（点击跳转业务页）  ←→  首页待办卡（天然兜底：业务数据在，卡片就在）
```

**为什么全部放 mp-server**：小程序消息发送需要 `WxMaService`（access_token），只有 mp-server 持有该 Bean。总部 server 只做"入队"（写 notification_log），不直接调微信。

**核心规则（用户已确认，实现必须遵守）**：

| 规则 | 内容 |
|------|------|
| 模板 | 所有场景共用 **1 个订阅消息模板**，内容以【模块名】前缀区分 |
| 额度 | 授权 +1（每次进入首页静默调用 requestSubscribeMessage + 关键提交动作也调用），发送 -1，无上限累积 |
| 降级 | 额度不足 → 不发微信，靠首页待办卡兜底（业务数据实时统计，天然覆盖） |
| 按店发送 | 一个经理管多家店 → 每家店各收一条（逐店入队） |
| 接收人 | 该门店店长/老板：`employee.role ∈ {店长/经理/老板}` 且 status=active ∪ `owner_registration` 绑定老板 |

---

## 二、7 场景通知矩阵

| # | 场景 | 触发时机 | 内容（thing1【模块】+ thing2 说明 + time1 时间） | 接收人 | 点击跳转 page | 首页兜底卡 |
|---|------|---------|------|--------|--------------|-----------|
| 1 | 报损待审批提醒 | 每天 10:00 job，仅当该店存在 pending_approval 才发（当日多条合并为 1 条："今日有 N 条报损待审批"） | 【报损审批】今日有 N 条报损记录待审批 | 店长/老板 | `/pages/loss-report/list/index?tab=pending_approval` | 报损卡 ✅已有 |
| 2 | 盘点任务下发 | 总部创建任务成功后逐店发（手动创建 + 自动周盘生成均覆盖——挂点在 batchCreate 内部） | 【盘点任务】{任务名}已下发，截止 {时间} | 店长/老板 | `/pages/task/list/index` | 盘点待办/任务列表 ✅已有 |
| 3 | 员工登记审批 | 店员提交登记申请成功 | 【员工审批】{姓名}申请登记，请审批 | 店长/老板 | `/pages/staff/approval/index` | 员工审批卡 ✅已有 |
| 4 | 调货 | 见下方状态细分 | 【调货】{门店}向您调货/您的调货单已被拒绝 | 对端/发起方店长 | `/pages/transfer/list/index` | 调货待处理卡 ✅已有 |
| 5 | 问题状态提醒 | 状态推进到 待联系/待验收 时（applyExternalStatus / syncByCallback 状态推进点） | 【问题】您的问题单已标记「请验收/待联系」 | **问题提交人**（issue 需新增 openid 列） | `/pages/issue/detail/index?id={id}` | 问题卡 ⚠️ 需核对店员视角 |
| 6 | 订货待确认 | 订货单生成成功后（每日 3:00 job / 周盘提交补充生成，挂 generateAll/generateByWeeklyTask 尾部） | 【订货】本周订货单已生成，{N} 件待确认 | 店长 | 智能订货 H5（webview 页） | 智能订货卡 ❌ 被注释，需恢复 |
| 7 | 订货去企迈付款 | 店长确认下单成功后 **1 小时** 发第一条；之后**每 2 小时**重复；**付款或当天 24:00 停止**（不立即发） | 【订货】本周订货已下单，还未付款，请到企迈付款 | 确认人（店长） | 智能订货 H5（webview 页） | 同上 |

### 场景 4 调货状态细分（按状态机语义）

调货方向：fromStore=调出方，toStore=调入方；createdBy=发起方。

| 状态推进 | 通知谁 | 内容 | 说明 |
|---------|--------|------|------|
| create（createdBy≠fromStore：他店申请从本店调货） | **调出方店长** | {调入方}申请调货，请确认 | 触发点：confirm 前 = pending_confirm |
| create（createdBy=fromStore：本店主动调出 → 自动 confirm+ship） | **调入方店长** | {调出方}向您调货，请待收货 | 对应"待收货状态通知" |
| confirm/ship 完成 | **调入方店长** | {调出方}的调货已发出，请待收货 | 同上 |
| reject | **发起方店长** | 您的调货单已被{对方}拒绝 | "拒绝也要" ✅ |
| receive 完成 | （可选，不通知） | — | 完成态不打扰 |

> 实现核对点：以 TransferOrderServiceImpl create/confirm/ship/reject 各方法状态落库后的真实流转为准逐条挂接，create 自动发货分支（fromStore==当前店）要单独处理。

---

## 三、额度模型

### 3.1 授权充值（前端）

- **每次进入首页** `home/index` onShow：静默调 `wx.requestSubscribeMessage({tmplIds:[TEMPLATE_ID]})` → 返回 `accept` → 后端 +1
  - 用户勾过"总是保持以上选择，不再询问"后**不再弹窗、直接成功**——每次进首页静默 +1，额度自然积累
  - 用户拒绝过则返回 `reject`，不 +1，也不骚扰
- **关键提交动作**也调（提交成功回调里静默请求）：
  - 报损提交（form / form-daily）、报损审批动作（approve / reject-approval / batch-approve）
  - 调货发起 / 确认 / 收货、员工登记申请提交、问题提交、盘点任务提交
  - ⚠️ 智能订货确认在 **H5（webview）里发生，H5 无法调订阅授权** → 授权点放在"进入智能订货 H5 之前/返回小程序后"的原生页面动作上（见 §5.4 坑）

### 3.2 发送扣减（后端）

```
UPDATE notification_quota SET quota = quota - 1 WHERE openid = ? AND quota > 0
```
- 扣减成功 → 调微信发送；失败 → 本条标记"额度不足"，不再发微信（首页卡兜底）
- 单条消息一条额度；一次性授权与单条消息 1:1，模型天然一致

### 3.3 为什么要"授权+1 但发送前再查"

授权发生在用户主动操作时（在线），发送常发生在被动时刻（10:00 job / 2h 循环）——额度是**预存账户**，发送时只读余额，无需在线。

---

## 四、后端设计（xzcpc-xzg/mp）

### 4.1 新表 / 迁移（SQL 脚本交付用户执行，不自动跑库）

```sql
-- ① 订阅消息额度表
CREATE TABLE IF NOT EXISTS notification_quota (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid VARCHAR(64) NOT NULL COMMENT '接收人 openid',
  quota INT NOT NULL DEFAULT 0 COMMENT '剩余额度（授权+1 发送-1）',
  updated_at DATETIME DEFAULT NULL,
  created_at DATETIME DEFAULT NULL,
  UNIQUE KEY uk_openid (openid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小程序订阅消息授权额度';

-- ② issue 表新增提交人 openid（场景 5 通知提交人；存量单无法回填，历史单不通知）
ALTER TABLE issue ADD COLUMN submitter_openid VARCHAR(64) DEFAULT NULL COMMENT '问题提交人 openid（订阅消息通知用）';
```

### 4.2 复用现有表（零改动或极小改动）

- **notification_log**：即发送队列表。`status 0待发送/1成功/2失败`、`retryCount`、`failReason` 全部现成，event_type 是无约束 varchar → 直接存新事件名，无需改表
  - 建议 event_type 值：`LOSS_APPROVAL_REMIND` / `TASK_CREATED` / `STAFF_APPLY` / `TRANSFER_CONFIRM` / `TRANSFER_RECEIVE` / `TRANSFER_REJECT` / `ISSUE_PENDING_CONTACT` / `ISSUE_PENDING_ACCEPTANCE` / `ORDER_CONFIRM` / `ORDER_PAY_REMIND`
  - 新增一行**仅订阅消息用**的字段：`page_path`（点击跳转，带参数）
- **smart_order**：已有 qmPayStatus/qmOrderStatus 列，无迁移。⚠️ 核对：付款状态是否本地实时更新（若仅下单时快照，OrderPayReminderJob 停发判断需实时查企迈，见 §4.5）

### 4.3 新增类（mp 模块，全部在 mp-server 运行）

| 类 | 职责 |
|----|------|
| `SubscribeMessageService` | 发订阅消息。access_token 缓存镜像 `FeishuMessageService` 模式（cachedToken + tokenExpireAt + getToken）；send(openid, templateData, page) |
| `NotificationService`（业务入口） | `enqueue(...)`：与业务同事务写 notification_log（status=0）；`resolveReceivers(storeId)` → 店长/老板 openid 列表；`reserveQuota(openid)` 原子扣 1 |
| `NotificationSenderJob` | @Scheduled 每 60s 扫 notification_log status=0 → 逐条发送 → 更新日志（成功扣额度；43101 用户拒收/43004 模板占用/40037 模板错误 分类记录 failReason；额度不足直接标记不再重试） |
| `LossApprovalReminderJob` | cron `0 0 10 * * ?` 每天 10:00：按门店聚合 pending_approval 计数 > 0 → 每店一条 → 店长/老板 |
| `OrderPayReminderJob` | cron 每 30 分钟（`0 */30 7-23 * * ?`）：扫 smart_order 满足 [已下单确认 && 未付款 && now ≥ 下单+1h && now < 当天 24:00 && 距该单上次提醒 ≥ 2h] → 入队。停止条件：已付款（qmPayStatus）或跨天 |

### 4.4 业务挂点清单（对照已摸代码逐行）

| 场景 | 文件 / 位置 | 挂接 |
|------|------------|------|
| 1 | mp 新增 `LossApprovalReminderJob`（查询条件镜像 `pageApprovalByStore`：store + pending_approval） | 每日 10:00 |
| 2 | `task/.../TaskServiceImpl.batchCreate`（L287 `taskMapper.insert` 成功后循环内） | 每店入队一条（内容含任务名+截止时间）。自动周盘 `autoGenerateWeekly` 内部走 batchCreate，自动覆盖。**总部 server 侧入队**（server 依赖 mp，可直接用 NotificationLog mapper；需落地确认 Bean 可见性，若不可见则在 server 复制实体映射同一表） |
| 3 | `mp/.../MpStaffServiceImpl` 注册申请 insert 成功处 | 入队（内容含申请人姓名） |
| 4 | `mp/.../TransferOrderServiceImpl` create(L43 自动发货分支) / confirm(L106) / ship(L131) / reject(L189) | 按 §二 细分矩阵入队 |
| 5 | `mp/.../IssueServiceImpl` create(L59 补存 submitter_openid) + applyExternalStatus(L282) / syncByCallback(L317) 状态推进到 PENDING_CONTACT / PENDING_ACCEPTANCE | 查提交人 openid → 入队 |
| 6 | `mp/.../SmartOrderServiceImpl` generateAll(L118) / generateByWeeklyTask(L169) 成功后 | 按店入队店长 |
| 7 | `mp/.../SmartOrderServiceImpl` confirm(L2121) 企迈下单成功后（不下发，条件由 OrderPayReminderJob 判定） | 不立即发，1h 后由 job 首推 |

### 4.5 OrderPayReminderJob 判定细节

不依赖"预约表"，直接以 smart_order 现有字段条件扫描（天然幂等、跨天自动失效）：

```
now >= confirmed_at + 1h
AND qmPayStatus != 已付款（若本地列不实时 → job 内对候选单实时查企迈）
AND 最近一条 ORDER_PAY_REMIND(source_id=单号) 的 createdAt <= now - 2h
AND now < 当天 24:00
```
每天 24:00 后条件自动不满足 → 当天提醒终止，次日不再发（用户规则：当天 24:00 结束）。

### 4.6 配置

```yaml
# mp-server application.yml
notify:
  subscribe-template-id: xxxxxxxxxxxxxxxx   # 用户申请后填入
```
前端 constants.ts 同步一份 TEMPLATE_ID。

---

## 五、前端设计（miniapp）

### 5.1 新工具 `utils/subscribe.ts`

```ts
/** 静默请求订阅授权；accept 时上报额度 +1。任一环节失败均静默（不打扰用户） */
export async function topUpSubscribeOnce(): Promise<void>
```
- 调 `wx.requestSubscribeMessage({ tmplIds: [TEMPLATE_ID] })` → `res[TEMPLATE_ID] === 'accept'` → `POST /api/mp/notify/quota/plus`（后端按 openid 累加，可幂等防抖）
- 模板 ID 未配置（开发/测试期）→ 直接 return

### 5.2 后端接口

| 接口 | 说明 |
|------|------|
| `POST /api/mp/notify/quota/plus` | 授权成功上报 +1（登录态取 openid；同日内可多次，不设上限） |

### 5.3 接入点

1. **首页 `pages/home/index/index.vue` onShow** → topUpSubscribeOnce()（每次进入 +1）
2. **关键提交动作**（成功后调用）：报损提交 / 报损审批、调货发起·确认·收货、员工登记提交、问题提交、盘点任务提交、（订货相关见 5.4 坑）
3. **接收人 = 任何人**：店员提交问题后也是场景 5 接收人——店员进首页同样充值 ✅

### 5.4 ⚠️ 智能订货 H5 的授权坑

场景 6/7 的动作（确认、付款）都发生在 **webview 里的 H5**，H5 无法调 `wx.requestSubscribeMessage`。
→ 方案：**进入智能订货 H5 前的原生按钮/跳转处**（首页智能订货卡点击、webview 容器页 onLoad）调一次授权 + 从 H5 返回小程序后（webview onUnload/返回回调）再补一次。落地时在 `pages/common/webview/index.vue` 处理。

### 5.5 首页待办卡

- 恢复被注释的**智能订货卡**（`home/index/index.vue` L279-287 / L319-322 注释块）：H5 入口已写好（goPendingItem / smartOrderH5Url 动态拼 token），数据源已按角色门控
- 其余卡（报损/调货/员工审批/问题处理）已存在，无需新建
- ⚠️ 核对点：问题卡当前统计 `pendingAcceptance` 是店长视角——**店员（提交人）登录后首页是否展示自己提交的问题待办**？若不展示，场景 5 的站内兜底缺失，需让问题卡对提交人可见（或 issue/list 已展示自己提交的单，卡只统计店长即可，店员走列表页）。

### 5.6 跳转参数支持

- `loss-report/list` 目前无 onLoad query 处理 → 加 `tab` 参数直达"待审核" tab（列表页已有 tab 状态）
- `issue/detail?id=`、`transfer/list`、`staff/approval` 均为普通页面跳转，验参即可

---

## 六、模板申请指引（用户在微信侧操作）

1. 登录 [mp.weixin.qq.com](https://mp.weixin.qq.com) → 功能 → 订阅消息
2. 从公共模板库选 **一次性订阅消息** 模板，按如下顺序选关键词（字段编号对应代码里的 thing1/thing2/time1，申请回执里能看到最终字段名，配置化映射即可）：
   - 关键词 1 → 标题内容（如"通知事项"，≤20 字，代码传【模块名】前缀）
   - 关键词 2 → 说明内容（如"提醒内容"）
   - 关键词 3 → 时间（time 类型）
3. 若类目不符被拒：换同语义关键词重选（字段顺序无硬性约束，拿到模板后在 `SubscribeMessageService` 的 data map 按实际字段名组装）
4. 申请成功后把 **template_id** 给我 → 填 yml 配置 + 前端 constants

---

## 七、交付物清单（审批后实施）

### SQL 脚本（交用户执行）
1. `notification_quota` 建表
2. `issue.submitter_openid` 加列

### 后端（xzcpc-xzg/mp，git 按功能粒度提交）
1. `SubscribeMessageService`（token 缓存 + send）
2. `NotificationService`（enqueue / resolveReceivers / reserveQuota）+ 新实体 NotificationQuota + 新接口 quota/plus
3. `NotificationSenderJob`（60s 轮询）
4. `LossApprovalReminderJob`（10:00）
5. `OrderPayReminderJob`（30min 粒度）
6. 业务挂点：报损（无，job 覆盖）、任务 batchCreate（server 侧）、员工申请、调货 4 方法、issue（create 补 submitter_openid + 状态推进 2 方法）、smart-order generate×2 + confirm（支付提醒只置条件）

### 前端（miniapp，git 按功能粒度提交）
1. `utils/subscribe.ts` + constants
2. 首页 onShow 充值 + 智能订货卡恢复
3. 各提交动作接入充值
4. loss-report/list tab 参数支持

---

## 八、未决点（实现前逐项核对）

| # | 项 | 现状 | 影响 |
|---|----|------|------|
| 1 | smart_order.qmPayStatus 是否本地实时更新 | 疑似下单时快照 + 实时查询不落库（L1243 区域） | 付款停发判断方式 |
| 2 | 店员登录首页是否可见问题卡/自己提交的问题 | 待核对 miniapp issue 页与 home 卡逻辑 | 场景 5 站内兜底 |
| 3 | server 模块能否直接 import mp 的 NotificationLog mapper | server 依赖 mp，大概率可以 | 场景 2 入队代码位置 |
| 4 | 智能订货 H5 当前确认/付款交互页 URL 形态 | webview + 动态 token | 场景 6/7 跳转与授权点 |
| 5 | loss-report/list 默认 tab 顺序 | 待审核 tab 位置 | 跳转直达是否需要带参 |
