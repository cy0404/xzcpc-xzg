# -*- coding: utf-8 -*-
"""部分收货实测：企迈 9.2.3 num 语义 + 9.2.9 关单（用户已在企迈控制台建测试单）

用法：
  列表模式（只读，列测试门店近7天待入库单）：
      python tmp_qmai_partial_receive_test.py
  执行模式（对指定单号做实验，全程写日志文件）：
      python tmp_qmai_partial_receive_test.py <inboundNo> [--partial 2]

实验流程（每步都 9.2.2 回读，所有输出写入 tmp_qmai_partial_receive_test.log）：
  A 记录原状态（status / inboundNum / inboundPrice）
  B 9.2.3 写 num=partial（默认2）
  C 回读 → 若 status 已翻 2 = 任意写入即整单确认（用户模型不成立），停止
  D 9.2.3 写 num=Q（原全量）
  E 回读 → 若翻 2 = 全量写入自动关单
  F 仍 status=1 则调 9.2.9 finish 关单
  G 回读终态

判定要点：
  C 后 inboundNum=partial 且 status=1  → num=覆盖式、部分收货成立（用户模型）
  C 后 inboundNum=原值                → num 另存他处，继续 D/E/F 观察
  D 后 status=2                       → 收齐自动关单，9.2.9 可不用
"""
import hmac, hashlib, base64, time, random, json, sys, io
from urllib.parse import quote
import urllib.request

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
LOG = open("tmp_qmai_partial_receive_test.log", "a", encoding="utf-8")

def log(*a):
    line = " ".join(str(x) for x in a)
    LOG.write(line + "\n")
    LOG.flush()
    print(line)

log("\n===== 部分收货实测  开始 =====")

OPEN_ID = "71fcea7abc9709d653693116410b5385"
GRANT_CODE = "WaGI2rqy9b"
OPEN_KEY = "KYDwl5j39Wb71bff5fe6b0434ac6366a913c0dbc947q1SpQyZ"
BASE = "https://openapi.qmai.cn"
TEST_QM_STORE_ID = 246964
TEST_CANGKUID = "MDCK111118"

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

def pull(start, end, max_page=12):
    out = []
    for page in range(1, max_page + 1):
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

def describe(r):
    items = r.get("inboundProductList") or []
    parts = ["%s=%s" % (i.get("productName") or "?", i.get("inboundNum")) for i in items[:3]]
    return "status=%s type=%s | %s | %s bizNo=%s | %s" % (
        r.get("status"), r.get("inboundType"), r.get("warehouseName"),
        r.get("inboundNo"), r.get("bizNo"), " ".join(parts))

def find_inbound(inbound_no, days=5):
    for d in range(0, days):
        start = time.strftime("%Y-%m-%d 00:00:00", time.localtime(time.time() - 86400 * (d + 1)))
        end = time.strftime("%Y-%m-%d 23:59:59", time.localtime(time.time() - 86400 * d))
        log("  翻 %s ~ %s" % (start, end))
        for r in pull(start, end):
            if r.get("inboundNo") == inbound_no:
                return r
    return None

def update(inbound_no, inbound_type, num, price_str, code, name, tag):
    resp = call(BASE + "/v3/scm/order/inbound/order/update", {
        "matchType": 1, "inboundNo": inbound_no, "inboundType": inbound_type,
        "operator": "测试门店", "warehouseNo": TEST_CANGKUID,
        "productList": [{"num": num, "price": price_str,
                         "productCode": code, "productName": name}],
    })
    log("  [%s] 9.2.3 写 num=%s price=%s => code=%s msg=%s" % (
        tag, num, price_str, resp.get("code"), resp.get("message")))
    return resp

def finish(inbound_no):
    resp = call(BASE + "/v3/scm/order/inbound/order/finish",
                {"inboundNoList": [inbound_no]})
    log("  [F] 9.2.9 finish => code=%s msg=%s data=%s" % (
        resp.get("code"), resp.get("message"), resp.get("data")))
    return resp

def list_pending():
    log("== 列表模式：测试门店近7天入库单 ==")
    recs = pull(time.strftime("%Y-%m-%d 00:00:00", time.localtime(time.time() - 86400 * 7)),
                time.strftime("%Y-%m-%d 23:59:59", time.localtime()))
    cnt = 0
    for r in recs:
        wn = str(r.get("warehouseName") or "")
        if "测试门店" not in wn:
            continue
        cnt += 1
        log("  " + describe(r))
    log("测试门店入库单共 %d 条（status: 1=待入库 2=已入库 3=已作废）" % cnt)

def run_test(inbound_no, partial):
    log("== 执行模式：inboundNo=%s partial=%s ==" % (inbound_no, partial))
    # A 找单 + 原状态
    r = find_inbound(inbound_no)
    if r is None:
        log("  !! 近5天没找到该单号，终止"); return
    st = r.get("status")
    if st != 1:
        log("  !! 该单 status=%s 不是待入库(1)，不动它，终止" % st); return
    items = r.get("inboundProductList") or []
    item = None
    for it in items:
        if it.get("inboundNum") and it.get("inboundNum") > partial:
            item = it; break
    if item is None:
        log("  !! 没有数量大于 %s 的物料（整单数量太小没法测部分收货），终止" % partial)
        log("     现有物料: " + " ".join("%s=%s" % (i.get("productName"), i.get("inboundNum")) for i in items))
        return
    Q = item.get("inboundNum")
    code = item.get("productCode"); name = item.get("productName")
    price_raw = item.get("inboundPrice") or 0
    price_str = ("%.2f" % (price_raw / 100.0)) if isinstance(price_raw, (int, float)) else "0"
    log("  [A] 原状态: %s" % describe(r))
    log("      选中的物料: %s code=%s 数量Q=%s 单价raw=%s(分) 写价=%s" % (name, code, Q, price_raw, price_str))

    # B 写部分量
    update(inbound_no, r.get("inboundType"), partial, price_str, code, name, "B")
    time.sleep(1)
    r2 = find_inbound(inbound_no)
    st2 = r2.get("status") if r2 else None
    it2 = next((i for i in (r2.get("inboundProductList") or [])
                if i.get("productCode") == code), None) if r2 else None
    log("  [C] 部分量后回读: status=%s inboundNum=%s（原Q=%s）" % (
        st2, it2.get("inboundNum") if it2 else None, Q))
    if st2 == 2:
        log("  !! 写部分量就翻2 = 任意写入即整单确认，部分收货模型不成立，停止")
        return

    # D 写全量
    update(inbound_no, r.get("inboundType"), Q, price_str, code, name, "D")
    time.sleep(1)
    r3 = find_inbound(inbound_no)
    st3 = r3.get("status") if r3 else None
    it3 = next((i for i in (r3.get("inboundProductList") or [])
                if i.get("productCode") == code), None) if r3 else None
    log("  [E] 全量后回读: status=%s inboundNum=%s" % (
        st3, it3.get("inboundNum") if it3 else None))
    if st3 == 2:
        log("  => 结论：部分量保持 status=1、全量自动关单；num 语义见 C 步 inboundNum 变化（=partial 为覆盖式，=原Q 为另存）")
    else:
        log("  => 全量写入未翻2，试 9.2.9 finish")
        finish(inbound_no)
        time.sleep(1)
        r4 = find_inbound(inbound_no)
        st4 = r4.get("status") if r4 else None
        log("  [G] finish 后回读: status=%s" % st4)
        log("  => 结论：关单需显式调 9.2.9（全量写入不会自动关单）")
    log("===== 实测结束 =====")

if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if len(args) >= 1:
        partial = 2
        if "--partial" in sys.argv:
            i = sys.argv.index("--partial")
            if i + 1 < len(sys.argv):
                partial = float(sys.argv[i + 1])
        run_test(args[0], partial)
    else:
        list_pending()
