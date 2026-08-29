# -*- coding: utf-8 -*-
"""
9.2.3 price 单位二次验证（安全版）
靶子：已入库(status=2)的测试门店单 DH20260812000005（inboundNo=1294368962844602371）
     —— 已入库单写入不会再翻状态，仅短暂改价，随后还原。
步骤：读原值 → 写 12.34(数量保持原值) → 回读判定单位 → 还原 → 回读比对确认
输出：stdout + 追加落盘 tmp_qmai_price_unit_verify2.log
"""
import hmac, hashlib, base64, time, random, json
from urllib.parse import quote
import urllib.request

OPEN_ID = "71fcea7abc9709d653693116410b5385"
GRANT_CODE = "WaGI2rqy9b"
OPEN_KEY = "KYDwl5j39Wb71bff5fe6b0434ac6366a913c0dbc947q1SpQyZ"
BASE = "https://openapi.qmai.cn"
TARGET_INBOUND_NO = "1294368962844602371"
LOG_FILE = "tmp_qmai_price_unit_verify2.log"

def log(msg):
    print(msg, flush=True)
    with open(LOG_FILE, "a", encoding="utf-8") as f:
        f.write(msg + "\n")

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
            log("  retry %d: %s" % (i + 1, e)); time.sleep(2)
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

def read_target():
    for r in pull_inbounds("2026-08-08 00:00:00", "2026-08-15 23:59:59"):
        if r.get("inboundNo") == TARGET_INBOUND_NO:
            return r
    return None

def first_item(o):
    lst = o.get("inboundProductList") or []
    return (lst[0] if lst else None)

def update(price_str, num, operator="价格单位验证脚本"):
    resp = call(BASE + "/v3/scm/order/inbound/order/update", {
        "matchType": 1, "inboundNo": o.get("inboundNo"), "inboundType": o.get("inboundType"),
        "operator": operator, "warehouseNo": o.get("warehouseCode"),
        "productList": [{"num": num, "price": price_str,
                         "productCode": item.get("productCode"), "productName": item.get("productName")}],
    })
    log("  9.2.3 返回: %s" % json.dumps(resp, ensure_ascii=False)[:300])

log("===== 9.2.3 price 单位验证 (2nd, 安全版)  %s =====" % time.strftime("%Y-%m-%d %H:%M:%S"))

# ① 读原值
log("== 1. 读取靶子单当前原值 ==")
o = read_target()
if not o:
    log("  未找到 %s，终止" % TARGET_INBOUND_NO)
    raise SystemExit
item = first_item(o)
log("  inboundNo=%s bizNo=%s type=%s status=%s(2=已入库) warehouse=%s" % (
    o.get("inboundNo"), o.get("bizNo"), o.get("inboundType"), o.get("status"), o.get("warehouseName")))
log("  原值: 物料=%s code=%s 数量=%s 单价(raw)=%s 金额(raw)=%s 入库人=%s" % (
    item.get("productName"), item.get("productCode"), item.get("inboundNum"),
    item.get("inboundPrice"), o.get("inboundAmount"), o.get("inboundPerson")))
orig_price_raw = item.get("inboundPrice")
orig_num = item.get("inboundNum") or 0
orig_amount_raw = o.get("inboundAmount")
orig_status = o.get("status")
if orig_status != 2:
    log("  ⚠️ 注意：该单 status=%s，不是已入库(2)，仍继续但留意状态变化" % orig_status)
if orig_price_raw is None:
    log("  原单价为 None，无法比对还原，终止")
    raise SystemExit

# ② 写特殊值 12.34（数量保持原值）
log("== 2. 写入特殊价 12.34（数量=%s） ==" % orig_num)
update("12.34", orig_num)

# ③ 回读判定
log("== 3. 回读 9.2.2 判定单位 ==")
time.sleep(2)
r = read_target()
ri = first_item(r)
p = ri.get("inboundPrice")
log("  回读: status=%s 单价(raw)=%s 金额(raw)=%s" % (r.get("status"), p, r.get("inboundAmount")))
UNIT = None
if p is None:
    log("  回读单价为 None，无法判定")
elif abs(p - 1234) < 1:
    UNIT = "yuan"; log("  ★ 判定：9.2.3 price 单位 = 元（写 12.34 元 → 9.2.2 读回 1234 分）")
elif abs(p - 12.34) < 0.01 or abs(p - 12) < 1:
    UNIT = "fen"; log("  ★ 判定：9.2.3 price 单位 = 分（写 12.34 分 → 9.2.2 读回 %s 分）" % p)
else:
    log("  ★ 回读值 %s 不在预期，人工判断" % p)

# ④ 还原（双重保险：回读与原值比对，不一致换解释再还原一次）
log("== 4. 还原原值（raw=%s） ==" % orig_price_raw)
def restore_str(unit):
    if unit == "fen":
        return str(int(round(orig_price_raw)))
    return "%.2f" % (orig_price_raw / 100)

guesses = [UNIT] if UNIT else ["yuan", "fen"]
for g in guesses:
    log("  按 %s 解释还原：写 %s" % (g, restore_str(g)))
    update(restore_str(g), orig_num)
    time.sleep(2)
    rr = read_target()
    ri2 = first_item(rr)
    p2 = ri2.get("inboundPrice")
    log("  还原后回读: 单价(raw)=%s 金额(raw)=%s status=%s" % (p2, rr.get("inboundAmount"), rr.get("status")))
    if p2 is not None and abs(p2 - orig_price_raw) < 0.01:
        log("  ✅ 还原成功（与原值一致）")
        break
    log("  ⚠️ 与原值不一致，尝试另一解释...")

# ⑤ 终检
log("== 5. 终检 ==")
rr = read_target()
ri2 = first_item(rr)
p2 = ri2.get("inboundPrice")
ok = p2 is not None and abs(p2 - orig_price_raw) < 0.01
log("  最终: 单价(raw)=%s（原 %s）金额(raw)=%s（原 %s）status=%s（原 %s）→ %s" % (
    p2, orig_price_raw, rr.get("inboundAmount"), orig_amount_raw,
    rr.get("status"), orig_status, "还原成功 ✅" if ok else "仍有偏差 ❌ 请人工处理"))
log("  结论: 9.2.3 price 单位 = %s" % (UNIT or "未知"))
log("===== 结束 %s =====" % time.strftime("%Y-%m-%d %H:%M:%S"))
