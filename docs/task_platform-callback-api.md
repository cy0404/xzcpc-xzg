# 外部系统回调接口文档

## 概述

task_platform 表单提交成功后，通过此接口将问题同步到象掌柜系统。
后续状态变更时（处理中→待验收→已解决）再次调用同一接口更新。

---

## 接口

```
POST https://www.xzcpc-9pd.top/storeInventory/api/mp/public/issue/callback
Content-Type: application/json
```

---

## 鉴权

请求头携带 `X-Api-Key`：

```
X-Api-Key: 3707d9d759ccacb2dab2da4050a25985
```

---

## 请求参数（JSON Body）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `chatId` | string | 是 | 门店群聊标识，来自表单页 URL 参数 `?chatId=xxx` |
| `externalId` | number | 是 | 表单提交后返回的问题 ID |
| `issueNo` | string | 否 | 问题编号 |
| `status` | string | 否 | 问题状态 |
| `title` | string | 否 | 问题标题 |
| `storeName` | string | 否 | 门店名称 |
| `source` | string | 否 | 来源渠道（见下方枚举） |

### source 取值

| 回调传入值 | 数据库存储 | 总部端展示 |
|-----------|-----------|-----------|
| `wxapp` | `wxapp` | 小程序 |
| `MINI_PROGRAM` / `WXAPP` | `MINI_PROGRAM` / `WXAPP` | 小程序 |
| `FEISHU_GROUP` | `FEISHU_GROUP` | 飞书群 |
| `HQ` | `HQ` | 总部 |
| 空/null | null | -- |

> **注意**：数据库存储原始值不做转换，总部端展示时统一映射为中文标签。

### status 取值

| 值 | 含义 |
|----|------|
| `PENDING_CONFIRMATION` | 待确认 |
| `IN_PROGRESS` | 处理中 |
| `PENDING_ACCEPTANCE` | 待验收 |
| `RESOLVED` | 已解决 |
| `CLOSED` | 已关闭 |
| `OVERDUE` | 逾期 |

### upsert 行为

同一 `externalId` 可多次调用。首次调用**创建**，后续调用**更新** `status`、`title`、`storeName` 等字段。

### 请求示例（创建）

```json
{
  "chatId": "oc_ea177cd1cd4c074677c53785db6fd1b7",
  "externalId": 1234,
  "issueNo": "ISS202607150001",
  "status": "PENDING_CONFIRMATION",
  "title": "制冰机无法出冰",
  "storeName": "象子茶铺茶南亚风情店",
  "source": "wxapp"
}
```

### 请求示例（状态变更）

```json
{
  "chatId": "oc_ea177cd1cd4c074677c53785db6fd1b7",
  "externalId": 1234,
  "status": "PENDING_ACCEPTANCE"
}
```

---

## 响应

### 成功（HTTP 200）

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "bizCode": "ISS202607150001"
  }
}
```

### 失败

| HTTP | 原因 |
|------|------|
| 403 | `X-Api-Key` 缺失或错误 |
| 500 | `chatId` 或 `externalId` 缺失、未找到对应门店 |

---

## 表单 URL 参数

小程序打开的表单 URL 包含两个参数：

```
https://www.xzcpc-9pd.top/task_platform/store-issue-form.html?chatId=oc_xxx&source=wxapp
```

| 参数 | 说明 |
|------|------|
| `chatId` | 门店群聊标识，回调时原样传入 |
| `source=wxapp` | 标识来源为微信小程序 |

---

## 调用时机

| 时机 | 说明 |
|------|------|
| 表单提交成功 | 创建问题，传入完整字段 |
| 状态变更 | 处理中→待验收→已解决，更新 status |

### 后端伪代码

```js
// 表单提交成功后的处理流程

// 1. 从 URL 读取参数
const chatId = req.query.chatId
const source = req.query.source  // "wxapp"

// 2. 创建问题（task_platform 内部）
const formRes = await createIssue({ ...req.body, chatId })

// 3. 回调象掌柜同步
await fetch('https://www.xzcpc-9pd.top/storeInventory/api/mp/public/issue/callback', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', 'X-Api-Key': '3707d9d759ccacb2dab2da4050a25985' },
  body: JSON.stringify({
    chatId: chatId,
    externalId: formRes.data.id,
    issueNo: formRes.data.issueNo,
    status: formRes.data.status,
    title: req.body.description,
    storeName: req.body.store,
    source: source
  })
})
```

---

## 二、处理记录回调

当 task_platform 有新处理记录时，推送最新数据到象掌柜存储。

### 接口

```
POST https://www.xzcpc-9pd.top/storeInventory/api/mp/public/issue/callback-records
Content-Type: application/json
X-Api-Key: 3707d9d759ccacb2dab2da4050a25985
```

### 请求参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `externalId` | number | 是 | 问题 ID（同创建回调的 externalId） |

只传最新一条记录即可，我们覆盖存储。

### 请求示例

```json
{
  "externalId": 1234,
  "latestRecord": { "time": "2026-07-16 09:15:00", "author": "张三", "text": "更换了封口机密封圈，测试正常" }
}
```

### 响应

```json
{ "code": 200, "message": "success" }
```

### 调用时机

每次有新的处理记录时，推送最新一条。

---

## 说明

- 回调接口仅做**数据同步**，不影响表单自身提交流程
- 回调失败不会回滚表单提交，两边数据以各自系统为准
- `chatId` 由小程序在打开表单 URL 时通过 `?chatId=xxx&source=wxapp` 传入
