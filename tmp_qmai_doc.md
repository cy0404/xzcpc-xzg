#### 接口的说明/描述


### 请求地址

| 环境 | 请求地址 |
| -------- | -------- |
|沙箱环境|xxx|
|正式环境|https://openapi.qmai.cn/v3/newPattern/scmApiserver/post/require/order/list|

### 公共请求参数
[开发指南->接口调用约定【必读】](https://open.qmai.com/home/documentCenter?id=9)

### 业务请求参数

| 参数 | 描述 | 是否必须  | 类型         | 示例值 |
| -------- | -------- |-------|------------| ------ |
|pageNo|页数,示例值(1)| false| int||
|pageSize|每页数量,示例值(10)，最大支持50| false| int||
|storeIds|门店id，最多支持10个| false| list||
|distributeIdList|配送中心id列表，最多支持10个| false| list||
|requireNo|	订货单据号| false| string||
|requireProIdList|	订货单品项id列表，最多支持10个| false| list||
|statusList|订单状态列表 0-待审核，1-待发货，2-已部分发货，3-已发货，4-已驳回, 5-已取消, 6-已完成| false| list||
|startTime|	创建开始时间(date-time)| false| string|2025-11-07 16:41:58|
|endTime|创建结束时间(date-time)| false| string|2025-11-07 16:41:59|

### 请求示例
```javascript
{
    "pageNo": 1,
    "pageSize": 10,
    "sellerId": 0,
    "storeIds": [],
    "distributeIdList": [],
    "requireNo": "",
    "requireProIdList": [],
    "statusList": [],
    "startTime": "",
    "endTime": ""
}
```

### 公共响应参数
[开发指南->接口调用约定【必读】](https://open.qmai.com/home/documentCenter?id=9)

### 业务响应参数
| 参数| 描述 | 类型  | 示例值 |
|----------------|-----|-----|----- |
|total|数量|int||
|data|数据集|list||
|&emsp;id|id|int||
|&emsp;declareNo|来源单号|string||
|&emsp;requireNo|订货单号|string||
|&emsp;distributeId|配送中心id|int||
|&emsp;warehouseNo|配送中心编码|string||
|&emsp;warehouseName|配送中心名称|string||
|&emsp;productCateNum|订货品项种类|int||
|&emsp;productNum|订货品项数量|float||
|&emsp;amount|订单金额|float||
|&emsp;orderStatus|订单状态 0-待审核，1-待发货，2-已部分发货，3-已发货，4-已驳回, 5-已取消, 6-已完成, 7-处理中|int||
|&emsp;storeId|门店id|float||
|&emsp;storeWarehouseNo|门店仓库编码|string||
|&emsp;storeName|门店名称|string||
|&emsp;contactName|收货人|string||
|&emsp;contactPhone|联系方式|string||
|&emsp;address|门店仓库地址|string||
|&emsp;orderAt|订货时间|string||

### 响应示例

```javascript
{
    "total": 0,
    "data": [
        {
            "id": 0,
            "declareNo": "",
            "requireNo": "",
            "distributeId": 0,
            "warehouseNo": "",
            "warehouseName": "",
            "productCateNum": 0,
            "productNum": 0,
            "amount": 0,
            "orderStatus": 0,
            "storeId": 0,
            "storeWarehouseNo": "",
            "storeName": "",
            "contactName": "",
            "contactPhone": "",
            "address": "",
            "orderAt": ""
        }
    ]
}
```

### 异常示例
