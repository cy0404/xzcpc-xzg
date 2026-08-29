#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""调试：dump 指定群今天的消息类型与内容，定位卡片匹配失败原因"""
import json
import re
import time
import urllib.parse
import urllib.request

BASE = "https://open.feishu.cn"
CHAT = "oc_ea177cd1cd4c074677c53785db6fd1b7"


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
    start = int(time.mktime(time.strptime("2026-08-27 00:00", "%Y-%m-%d %H:%M"))) * 1000
    end = int(time.time()) * 1000
    params = {"container_id_type": "chat", "container_id": CHAT, "page_size": 50}
    url = BASE + "/open-apis/im/v1/messages?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
    with urllib.request.urlopen(req, timeout=20) as r:
        d = json.loads(r.read().decode())
    print("code:", d.get("code"), "msg:", d.get("msg"))
    items = d.get("data", {}).get("items", [])
    print(f"共 {len(items)} 条消息")
    for m in items:
        ct = time.strftime("%H:%M:%S", time.localtime(int(m.get("create_time", "0")) / 1000))
        mtype = m.get("msg_type")
        sender = m.get("sender", {}).get("sender_type", "")
        content = m.get("content", "")
        print(f"--- {ct} type={mtype} sender={sender} id={m.get('message_id')}")
        print("    content:", content[:400].replace("\n", " "))


if __name__ == "__main__":
    main()
