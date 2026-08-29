#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""调试：单页搜索，打印完整返回（含分页字段）"""
import json
import re
import urllib.request

BASE = "https://open.feishu.cn"


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
    body = json.dumps({"query": "未验收问题提醒", "limit": 20}).encode()
    req = urllib.request.Request(
        BASE + "/open-apis/im/v1/messages/search?page_token=37d13e68b9bfc2a4",
        data=body,
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        method="POST")
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            d = json.loads(r.read().decode())
        data = d.get("data", {})
        print("code:", d.get("code"))
        print("has_more:", data.get("has_more"))
        print("page_token:", json.dumps(data.get("page_token"))[:80])
        print("items:", len(data.get("items", [])))
    except urllib.error.HTTPError as e:
        print("HTTP", e.code, e.read().decode()[:400])


if __name__ == "__main__":
    main()
