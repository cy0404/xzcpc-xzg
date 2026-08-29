# 企迈内部 API 逆向笔记（2026-08-18）

> 目标：评估「智能订货 H5 直接调用企迈内部支付接口」的可行性。
> 约束：全程未触发真实支付。所有结论基于只读验证。

## 一、登录流程（已验证可用，无需短信）

```
POST https://inapi.qmai.cn/gw/org-center/account/login-v2
Content-Type: application/json
Qm-From: console          device-no: <持久随机串>   device-type: Chrome
qm-channel: console       qm-device: windows        login-client-type: 1

body: {"deviceNo":"<deviceNo>","password":"<密码双base64>","username":"15126252751","version":2}
```

- **密码加密**：双重 Base64。例：`xzcp@2026` → `eHpjcEAyMDI2` → `ZUhwamNFQXlNREky`（注意用 `base64 -w0` 去除换行）
- **响应**：`halfToken=true` + `authToken`（即 qm_seller_token），Set-Cookie 同时带 `acw_tc`（30 分钟）和 `qm_seller_token`（5 天）

```
# 第二步：选择品牌/组织（完成登录，关键！不需要短信）
POST https://inapi.qmai.cn/gw/org-center/not-group/select-group-and-org
body: {"type":1,"typeId":216708,"groupId":42107}
# type/typeId/groupId 来自：
GET https://inapi.qmai.cn/gw/org-center/not-group/list-account-group-brand
# → data[0].id=42107(groupId), data[0].brands[0].orgs[0]={type:1,typeId:216708}
```

- 验证登录态：`GET /org-center/user/check-is-login` → `status:true`
- 账号信息：象子茶铺茶，品牌 216708（茶饮），admin 角色 2019293，联系人陈燕

## 二、只读接口验证结果

| 接口 | 路径 | 结果 |
|------|------|------|
| 报货单详情 | `POST /scm/app/declare/order/orderDetail` body `{"id":1296414975101669377}`（数字 id） | ✅ 返回完整订单 |
| 支付信息 | `POST /scm/console/declare/order/getPayInfo` | 接口存在，认证通过（报参数缺失 160001） |
| 支付信息2 | `POST /scm/app/require/order/getPayInfo` | 接口存在（报参数缺失 11000） |
| 余额 | `GET /finance-center/shop-account/balance` | 报"用户未登录"（可能需 storeId 上下文，未深究） |

- 报货单数字 id：`OpenAPI /v3/scm/order/declare/order/detail` 返回的 `id` 字段（如 BH20260818000012 → id=1296414975101669377）
- **参数名是数字 id，不是 declareNo**；declareNo 会报"报货单id为空"160001

## 三、支付接口

- 路径：`POST /scm/app/declare/order/pay`（H5 bundle 中找到定义，调用方在原生 APP chunk）
- 支付方式（从 H5 bundle 文案）：余额（云资金 Cloud Fund）/ 中信银行 / SaoBei CBK
- 支付页参数概念：`declareId`、`routeId`（供应链路线），具体 body 未逆向完整

## 四、关键结论

1. 内部接口调用**技术上可行**：登录 → Cookie → 业务接口，全部可用 Java HttpClient 模拟
2. **但没有支付开放 API 是企迈有意为之**（资金安全），内部接口无契约、无 SLA、随时可能变更或触发风控
3. 风险：封号/冻结风险、Cookie 泄露=后台被接管、升级后接口失效需持续维护
4. 建议：H5 端不做支付集成，提交成功后提示"待支付，请在企迈 APP 完成支付"；或做 deep link 唤起 APP 支付页

## 五、快速验证脚本

```bash
# 完整登录（bash）
PWD=$(echo -n "xzcp@2026" | base64 -w0 | base64 -w0)
curl -s -c qmai-cookies.txt -X POST "https://inapi.qmai.cn/gw/org-center/account/login-v2" \
  -H "Content-Type: application/json" -H "Qm-From: console" \
  -H "device-no: mqheg822sl9tmwaiix" -H "device-type: Chrome" \
  -H "qm-channel: console" -H "qm-device: windows" -H "login-client-type: 1" \
  -d "{\"deviceNo\":\"mqheg822sl9tmwaiix\",\"password\":\"$PWD\",\"username\":\"15126252751\",\"version\":2}"
curl -s -b qmai-cookies.txt -c qmai-cookies.txt -X POST "https://inapi.qmai.cn/gw/org-center/not-group/select-group-and-org" \
  -H "Content-Type: application/json" -H "Qm-From: console" \
  -H "device-no: mqheg822sl9tmwaiix" -H "device-type: Chrome" \
  -d '{"type":1,"typeId":216708,"groupId":42107}'
# 之后带 qmai-cookies.txt 调业务接口
```
