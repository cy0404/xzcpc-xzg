# -*- coding: utf-8 -*-
"""从 sys_config 读 qmai_console_cookie，调企迈控制台仓配中心仓库列表接口"""
import json, urllib.request, pymysql

DB = dict(host="162.14.122.80", port=3306, user="store_inventory",
          password="Xzcpc@2026", database="store_inventory")
conn = pymysql.connect(**DB)
cur = conn.cursor()
cur.execute("SELECT config_value FROM sys_config WHERE config_key='qmai_console_cookie'")
row = cur.fetchone()
conn.close()
if not row:
    print("sys_config 无 qmai_console_cookie")
    raise SystemExit(1)
cookie = row[0]
print("cookie 长度:", len(cookie))

def call_console(path, params):
    body = json.dumps(params).encode()
    req = urllib.request.Request("https://inapi.qmai.cn" + path, data=body,
                                 headers={
                                     "Content-Type": "application/json",
                                     "Cookie": "qm_seller_token=" + cookie + "; ALL_DATA_SELLERID=216708",
                                 })
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return {"http_error": e.code, "body": e.read().decode()[:300]}

# 探测仓配中心仓库列表接口（常见路径）
for path in ["/gw/scm/console/warehouse/list",
             "/gw/scm/console/warehouse/query",
             "/gw/scm/console/warehouse/page",
             "/gw/scm/console/store/warehouse/list"]:
    resp = call_console(path, {"pageNo": 1, "pageSize": 500})
    code = resp.get("code") if isinstance(resp, dict) else None
    print("\n== %s == code=%s" % (path, code))
    if code == 0:
        data = resp.get("data") or {}
        rows = data.get("list") or data.get("data") or data.get("records") or []
        if isinstance(data, dict) and isinstance(data.get("records"), list):
            rows = data["records"]
        print("返回行数:", len(rows) if isinstance(rows, list) else "N/A")
        if isinstance(rows, list):
            for r in rows[:250]:
                print(json.dumps(r, ensure_ascii=False)[:200])
        break
    else:
        print(str(resp)[:200])
