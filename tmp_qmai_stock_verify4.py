# -*- coding: utf-8 -*-
"""9.2.13 正确调用姿势验证：外层 body {openId,grantCode,nonce,timestamp,token,params}"""
import sys, hmac, hashlib, base64, time, random, json
from urllib.parse import quote
sys.stdout.reconfigure(encoding='utf-8')
import urllib.request

OPEN_ID = "71fcea7abc9709d653693116410b5385"
GRANT_CODE = "WaGI2rqy9b"
OPEN_KEY = "KYDwl5j39Wb71bff5fe6b0434ac6366a913c0dbc947q1SpQyZ"
BASE = "https://openapi.qmai.cn"

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
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.loads(r.read().decode())
        except Exception as e:
            print("  retry %d: %s" % (i + 1, e)); time.sleep(2)
    raise RuntimeError("call failed: " + url)

# 1) 单仓 PSCK000023
resp = call(BASE + "/v3/newPattern/scmApiserver/post/warehouse-product/list",
            {"pageNo": 1, "pageSize": 100, "isEmpty": 1, "warehouseNoList": ["PSCK000023"]})
print("PSCK000023:", json.dumps(resp, ensure_ascii=False)[:300])

# 2) 冷冻仓 PSCK000149（之前 WP0854 有货）
resp2 = call(BASE + "/v3/newPattern/scmApiserver/post/warehouse-product/list",
             {"pageNo": 1, "pageSize": 100, "isEmpty": 1, "warehouseNoList": ["PSCK000149"]})
data = resp2.get("data") or {}
rows = data.get("list") or data.get("data") or data.get("records") or []
print("PSCK000149: total=%s 行数=%d" % (data.get("total"), len(rows)))
if rows:
    r = rows[0]
    print("  样例:", json.dumps(r, ensure_ascii=False)[:250])
