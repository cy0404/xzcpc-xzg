#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""测试：用消息详情接口查一条已知消息，判断 content 为空是权限还是接口问题"""
import json
import re
import urllib.request

BASE = "https://open.feishu.cn"
MSG_ID = "om_x100b52fd49ab78a0c3d7a94ebce6990"  # 日志群里 14:43 的 app text 消息


def get_token():
    with open("xzcpc-xzg/server/src/main/resources/application.yml", encoding="utf-8") as f:
        app_id = re.search(r"app-id:\s*(\S+)", f.read()).group(1)
    with open("xzcpc-xzg/server/src/main/resources/application-local.yml", encoding="utf-8") as f:
        app_secret = re.search(r"FEISHU_APP_SECRET:\s*(\S+)", f.read()).group(1)
    req = urllib.request.Request(
        BASE + "/open-apis/auth/v3/tenant_access_token/internal",
        data=json.dumps({"app_id": app_id, "app_secret": app_secret}).encode(),
        headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=20) as r:
        return json.loads(r.read().decode())["tenant_access_token"]


def main():
    token = get_token()
    url = f"{BASE}/open-apis/im/v1/messages/{MSG_ID}"
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            d = json.loads(r.read().decode())
        print("code:", d.get("code"), "msg:", d.get("msg"))
        print(json.dumps(d, ensure_ascii=False, indent=2)[:800])
    except urllib.error.HTTPError as e:
        print("HTTP", e.code, e.read().decode()[:500])


if __name__ == "__main__":
    main()
