# -*- coding: utf-8 -*-
"""分析 30 天窗口内类型1/3 入库单的归属匹配覆盖率：
层级1 bizNo∈报货键（需全量拉，此处单独重算）；层级2 warehouseCode→cangkuid；层级3 归一化名称。
输出 residue（任何层级都匹配不上的）按 warehouseName 分组统计，供构建别名映射。
"""
import hmac, hashlib, base64, time, random, json, sys, datetime, re
from urllib.parse import quote
import urllib.request
import pymysql

OPEN_ID = "71fcea7abc9709d653693116410b5385"
GRANT_CODE = "WaGI2rqy9b"
OPEN_KEY = "KYDwl5j39Wb71bff5fe6b0434ac6366a913c0dbc947q1SpQyZ"
BASE = "https://openapi.qmai.cn"
DB = dict(host="162.14.122.80", port=3306, user="store_inventory",
          password="Xzcpc@2026", database="store_inventory")
DTF = "%Y-%m-%d %H:%M:%S"
INBOUND_DAYS = int(sys.argv[1]) if len(sys.argv) > 1 else 30


def call(url, params, retry=3):
    last = None
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
            last = e; time.sleep(1.5)
    raise RuntimeError("call failed %s: %s" % (url, last))


def norm(s):
    return re.sub(r"[()（）\s　]", "", s or "")


def main():
    _now = datetime.datetime.now()
    s0 = (_now - datetime.timedelta(days=INBOUND_DAYS)).strftime(DTF)
    e0 = _now.strftime(DTF)
    print("入库窗口: %s ~ %s" % (s0, e0))

    # 门店：cangkuid 映射 + 归一化名称映射
    conn = pymysql.connect(**DB, charset="utf8mb4")
    cur = conn.cursor()
    cur.execute("SELECT store_id, store_name, cangkuid FROM store_info WHERE del_flag=0")
    rows = cur.fetchall()
    conn.close()
    ck2id = {r[2]: r[0] for r in rows if r[2]}
    name2id = {}
    for r in rows:
        name2id.setdefault(norm(r[1]), r[0])
    print("门店 %d 家；有 cangkuid %d 家" % (len(rows), len(ck2id)))

    # 拉全量入库单
    inbounds = []
    for page in range(1, 60):
        resp = call(BASE + "/v3/scm/order/inbound/order/list",
                    {"createdStartAt": s0, "createdEndAt": e0, "pageNo": page, "pageSize": 1000})
        data = resp.get("data") or {}
        recs = data.get("records") if isinstance(data, dict) else data
        if isinstance(data, dict) and recs is None:
            recs = data.get("data")
        if not recs:
            break
        inbounds.extend(recs)
        if page % 5 == 0:
            print("  已拉 %d 页 / %d 条" % (page, len(inbounds)))
        if len(recs) < 1000:
            break
    print("入库单总数 %d 条" % len(inbounds))

    t13 = [r for r in inbounds if r.get("inboundType") in (1, 3)]
    print("类型1/3: %d 条" % len(t13))

    hit2 = hit3 = 0
    residue = []
    for r in t13:
        wc = r.get("warehouseCode")
        if wc and wc in ck2id:
            hit2 += 1
            continue
        n = norm(r.get("warehouseName"))
        if n and n in name2id:
            hit3 += 1
            continue
        residue.append(r)
    print("warehouseCode→cangkuid 命中: %d" % hit2)
    print("归一化名称命中: %d" % hit3)
    print("残留: %d 条" % len(residue))

    from collections import Counter
    c = Counter((r.get("warehouseName") or "", r.get("warehouseCode") or "") for r in residue)
    print("残留按 (warehouseName, warehouseCode) 分组:")
    for (n, ck), cnt in c.most_common(40):
        print("  %s | %s : %d" % (n, ck, cnt))

    # 残留里 warehouseCode 重复出现的（说明是真实门店仓，只是 cangkuid 未存/名字对不上）
    codes = Counter(r.get("warehouseCode") for r in residue if r.get("warehouseCode"))
    print("残留 warehouseCode 频次（前20）:")
    for ck, cnt in codes.most_common(20):
        print("  %s : %d" % (ck, cnt))


if __name__ == "__main__":
    main()
