# 老板绑定对接文档

## 概述

小象基础为每个门店生成二维码，老板扫码后自动完成微信账号与门店的绑定。小象基础仅需提供表单页面和管理审核流程。

---

## 一、小象基础需要做的事情

### 1. 生成二维码

为每个门店生成一个二维码，二维码内容按以下模板拼接：

```
https://www.xzcpc-9pd.top/storeInventory/mp/redirect/?storeId={小象基础的门店ID}&returnUrl={小象基础表单页URL}

注意：returnUrl 的值需要做 URL 编码
```

**示例**：假设小象基础为门店 STORE001 生成二维码：

```
门店ID:      STORE001
表单页URL:   https://www.xzcpc-9pd.top/xxjc/form.html

二维码内容:  https://www.xzcpc-9pd.top/storeInventory/mp/redirect/?storeId=STORE001&returnUrl=https%3A%2F%2Fp.example.com%2Fform.html
```

将这个二维码打印提供给门店，老板用**微信**扫码即可。

---

### 2. 提供一个表单页面

老板扫码后，象掌柜小程序会自动跳转到二维码中配置的表单页（即上面填的 `returnUrl`）。

象掌柜跳转时会带上以下 URL 参数，请小象基础读取使用：

| 参数 | 何时出现 | 说明 |
|------|---------|------|
| `storeId` | 总是 | 当前扫码的门店 ID |
| `alreadyOwner` | 有条件 | 值为 `true` 时，表示该老板之前已绑定过其他门店 |
| `fromStoreId` | 有条件 | 当 `alreadyOwner=true` 时附带，值为该老板最早绑定的门店 ID |

**小象基础的处理逻辑**：

- **收到 `alreadyOwner=true`**：说明该老板不是新用户。小象基础根据 `fromStoreId` 查自己的数据库获取已有信息，**直接调用回调接口**（见第三节），无需人工审核。

- **没有 `alreadyOwner` 参数**：说明是新老板，走正常的表单填写 → 人工审核流程。表单内容由小象基础自行设计。审核通过后调用回调接口。

### 3. 审核通过后调用回调接口

无论自动通过还是人工审核通过，最终都调用同一个接口通知象掌柜：

```
POST https://www.xzcpc-9pd.top/storeInventory/api/mp/public/owner-callback
Content-Type: application/json
```

**请求体**：

```json
{
  "storeId": "STORE001",
  "storeName": "南屏街店",
  "name": "张三",
  "mobile": "13800138000"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| storeId | String | 是 | 门店 ID，从 URL 参数获取 |
| storeName | String | 是 | 门店名称 |
| name | String | 是 | 老板姓名 |
| mobile | String | 是 | 老板手机号 |

**返回值**：

```json
// 成功
{ "code": 200, "message": "success" }

// 失败
{ "code": 500, "message": "错误原因" }
```

**注意**：
- `code != 200` 表示失败，请重试
- 同一门店重复调用会更新信息，不会产生重复数据
- 调用前必须确保老板已扫码，否则接口会报错
- 审核不通过无需调用此接口，不回调即可，不会产生任何数据

---

## 二、完整流程总结

```
小象基础生成二维码 → 打印贴门店
        ↓
老板微信扫码
        ↓
象掌柜自动处理（小象基础无需关心）
        ↓
跳转到小象基础表单页，携带参数
        ↓
   ┌─ 新老板（无 alreadyOwner）：填表 → 人工审核 → 调回调接口
   │
   └─ 老老板（有 alreadyOwner=true）：小象基础直接调回调接口
```

---

## 三、小象基础需要做的事情（清单）

| 序号 | 内容 | 说明 |
|------|------|------|
| 1 | 提供表单页 URL | 发给象掌柜，用于配置微信后台二维码规则 |
| 2 | 自建审核后台 | 审核通过后调用象掌柜回调接口 |
| 3 | 自行生成二维码 | 按模板拼 URL，不需要象掌柜参与 |
