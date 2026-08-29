# 门店差评数据推送接口

## 接口地址

```
POST http://162.14.122.80:4026/api/public/negative-reviews/sync
Content-Type: application/json
X-Api-Key: wx3081dfa30167bb6e
```

## 请求参数

Body 为数组，每条记录字段如下：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| externalId | string | 是 | 唯一ID，用于去重 |
| storeId | string | 是 | 门店ID |
| storeName | string | 否 | 门店名称 |
| reviewPlatform | string | 是 | 评价平台（美团/大众点评/饿了么等） |
| reviewScore | number | 否 | 评价分数，支持小数 |
| reviewDate | string | 是 | 评价日期，格式 yyyy-MM-dd |
| reviewContent | string | 否 | 评价内容 |

## 请求示例

```json
[
  {
    "externalId": "NEG20260812001",
    "storeId": "STORE001",
    "storeName": "南屏街店",
    "reviewPlatform": "美团",
    "reviewScore": 1.5,
    "reviewDate": "2026-08-12",
    "reviewContent": "等了半个小时才上菜，服务员态度也很差。"
  },
  {
    "externalId": "NEG20260812002",
    "storeId": "STORE002",
    "storeName": "翠湖店",
    "reviewPlatform": "大众点评",
    "reviewScore": 2.0,
    "reviewDate": "2026-08-11",
    "reviewContent": "味道还行，但是环境太吵了。"
  }
]
```

## 响应

成功（200）：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "count": 2,
    "message": "同步完成，共 2 条"
  }
}
```

失败：

| 状态码 | 原因 |
|--------|------|
| 403 | X-Api-Key 错误 |
| 500 | 参数格式错误 |

## 说明

- 每天推送一次即可，传数组批量同步
- 按 externalId 去重，已存在的更新内容，不存在的新增
- 没有差评数据时可以传空数组 `[]` 或不调用
