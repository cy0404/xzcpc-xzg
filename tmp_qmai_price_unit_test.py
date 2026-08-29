# -*- coding: utf-8 -*-
"""
9.2.3 price 单位实测：用测试门店的待入库单，写入特殊值 12.34 → 回读 9.2.2 → 推断单位 → 还原
- 若回读 12.34  → 9.2.3 price 单位是「元」
- 若回读 1234   → 9.2.3 price 单位是「分」
- 若回读 0.1234 → 其他
步骤：① 找测试门店待入库单 ② 记录原值 ③ 写 12.34 ④ 回读 ⑤ 还原原值
"""
import hmac, hashlib, base64, time, random, json
from urllib.parse import quote
import urllib.request

OPEN_ID = "71fcea7abc9709d653693116410b5385"
GRANT_CODE = "WaGI2rqy9b"
OPEN_KEY = "KYDwl5j39Wb71bff5fe6b0434ac6366a913c0dbc947q1SpQyZ"
BASE = "https://openapi.qmai.cn"

TEST_QM_STORE_ID = 246964          # 测试门店 qmai_store_id
TEST_CANGKUID = "MDCK111118"       # 测试门店 cangkuid（OpenAPI warehouseNo）
TEST_WAREHOUSE_MARK = "827599547516667653"  # 控制台仓库ID（warehouseMark 兜底）

def call(url, params, retry=3):
    for i in range(retry):
        ts = int(time.time()); nonce = random.randint(10000, 99999)
        sorted_str = "grantCode=%s&nonce=%s&openId=%s&timestamp=%s" % (GRANT_CODE, nonce, OPEN_ID, ts)
        restored = quote(sorted_str, safe='').replace("%3D", "=").replace("%26", "&")
        tok = base64.b64encode(hmac.new(OPEN_KEY.encode(), restored.encode(), hashlib.sha1).digest()).decode()
        body = {"openId": OPEN_ID, "grantCode": GRANT_CODE, "nonce": nonce,
                "timestamp": ts, "token": quote(tok, safe=''), "params": params}
        req = urllib.request.Request(url, data=json.dumps(body).encode(),
                                     headers={"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.loads(r.read().decode())
        except Exception as e:
            print("  retry %d: %s" % (i + 1, e)); time.sleep(2)
    raise RuntimeError("call failed: " + url)

def pull_inbounds(start, end):
    out = []
    for page in range(1, 12):
        resp = call(BASE + "/v3/scm/order/inbound/order/list",
                    {"createdStartAt": start, "createdEndAt": end, "pageNo": page, "pageSize": 1000})
        data = resp.get("data") or {}
        recs = data.get("records") if isinstance(data, dict) else data
        if isinstance(data, dict) and recs is None:
            recs = data.get("data")
        if not recs:
            break
        out.extend(recs)
        if len(recs) < 1000:
            break
    return out

# ① 测试门店归属键（9.1.17）+ 待入库单
print("== 1. 拉测试门店报货单归属键 ==")
keys = set()
resp = call(BASE + "/v3/newPattern/scmApiserver/post/declare/order/list",
            {"storeIdList": [TEST_QM_STORE_ID],
             "createdStartAt": "2026-08-08 00:00:00", "createdEndAt": "2026-08-15 23:59:59",
             "pageNo": 1, "pageSize": 50, "statusList": [3, 4], "payStatusList": [1, 2]})
data = resp.get("data") or {}
recs = data.get("data") if isinstance(data, dict) else None
if recs:
    for r in recs:
        for f in ("declareNo", "requireNo", "bizNo"):
            if r.get(f): keys.add(r[f])
        for f in ("requireNoList", "purchaseApplyNoList", "bizNoList"):
            for v in (r.get(f) or []):
                if v: keys.add(v)
print("  归属键 %d 个" % len(keys), list(keys)[:10])

print("== 2. 找测试门店待入库单 ==")
target = None
for r in pull_inbounds("2026-08-08 00:00:00", "2026-08-15 23:59:59"):
    if r.get("inboundType") not in (1, 3) or r.get("status") != 1:
        continue
    wn = str(r.get("warehouseName") or "")
    if (r.get("bizNo") in keys) or ("测试门店" in wn):
        target = r
        break
if not target:
    print("  没找到测试门店的待入库单，终止")
    raise SystemExit
o = target
item = (o.get("inboundProductList") or [None])[0]
print("  命中: inboundNo=%s type=%s warehouseName=%s bizNo=%s" % (
    o.get("inboundNo"), o.get("inboundType"), o.get("warehouseName"), o.get("bizNo")))
print("  首件物品: %s 入库数量=%s 入库单价=%s(分?) 状态=%s" % (
    item.get("productName"), item.get("inboundNum"), item.get("inboundPrice"), o.get("status")))

# ③ 写特殊值 12.34
def update(price_str, num):
    resp = call(BASE + "/v3/scm/order/inbound/order/update", {
        "matchType": 1, "inboundNo": o.get("inboundNo"), "inboundType": o.get("inboundType"),
        "operator": "测试门店", "warehouseNo": TEST_CANGKUID,
        "productList": [{"num": num, "price": price_str,
                         "productCode": item.get("productCode"), "productName": item.get("productName")}],
    })
    print("  9.2.3 返回:", json.dumps(resp, ensure_ascii=False)[:200])

print("== 3. 写入特殊价 12.34（数量=原入库数量） ==")
update("12.34", item.get("inboundNum") or 0)

# ④ 回读
print("== 4. 回读 9.2.2 对比 ==")
time.sleep(1)
found = None
for r in pull_inbounds("2026-08-08 00:00:00", "2026-08-15 23:59:59"):
    if r.get("inboundNo") == o.get("inboundNo"):
        found = r
        break
if not found:
    print("  回读失败：没找到该单")
else:
    fi = (found.get("inboundProductList") or [None])[0]
    print("  回读: status=%s 单价=%s 数量=%s 入库金额=%s" % (
        found.get("status"), fi.get("inboundPrice"), fi.get("inboundNum"), fi.get("inboundAmount")))
    p = fi.get("inboundPrice")
    if p is None:
        print("  单价为 None，无法判断")
    elif abs(p - 12.34) < 0.01:
        print("  ★ 结论：9.2.3 price 单位 = 元（写入 12.34 读回 12.34）")
    elif abs(p - 1234) < 1:
        print("  ★ 结论：9.2.3 price 单位 = 分（写入 12.34 读回 1234）")
    elif abs(p - 0.1234) < 0.001:
        print("  ★ 结论：9.2.3 price 单位 = 厘/其他（写入 12.34 读回 0.1234）")
    else:
        print("  ★ 回读值 %s 不在预期，需人工判断" % p)

# ⑤ 还原原值（按推断单位换算回原分价）
print("== 5. 还原原值 ==")
orig_price_fen = item.get("inboundPrice") or 0
# 根据上一步结论换算；这里默认按「元」还原：原分价/100
orig_num = item.get("inboundNum") or 0
# 探测结果自动选择
probe = None
if found:
    p = (found.get("inboundProductList") or [None])[0].get("inboundPrice")
    if p is not None:
        if abs(p - 12.34) < 0.01: unit_yuan = True
        elif abs(p - 1234) < 1: unit_yuan = False
        else: unit_yuan = True  # 未知按元
        revert_price = ("%.2f" % (orig_price_fen / 100)) if unit_yuan else str(orig_price_fen)
        print("  按推断单位还原 price=%s num=%s" % (revert_price, orig_num))
        update(revert_price, orig_num)
print("完成")
