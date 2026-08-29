#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
搜索「未验收问题提醒」卡片并列出 message_id / 撤回。

用法:
    python search_messages.py            # 列出全部命中卡片
    python search_messages.py --retract  # 撤回全部命中卡片
"""
import json
import re
import sys
import urllib.request

BASE = "https://open.feishu.cn"
KEYWORD = "未验收问题提醒"


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


def search_all(token):
    items, page_token, seen = [], "", set()
    page = 0
    while page < 100:
        page += 1
        body = {"query": KEYWORD, "limit": 20}
        url = BASE + "/open-apis/im/v1/messages/search"
        if page_token:
            url += "?page_token=" + page_token
        req = urllib.request.Request(
            url,
            data=json.dumps(body).encode(),
            headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
            method="POST")
        try:
            with urllib.request.urlopen(req, timeout=20) as r:
                d = json.loads(r.read().decode())
        except urllib.error.HTTPError as e:
            print("HTTP", e.code, e.read().decode()[:400])
            break
        if d.get("code") != 0:
            print("搜索失败:", d)
            break
        data = d.get("data", {})
        got = data.get("items", [])
        items.extend(got)
        print(f"  第{page}页: {len(got)} 条, has_more={data.get('has_more')}")
        page_token = data.get("page_token", "")
        if not data.get("has_more") or not page_token:
            break
        if page_token in seen:
            print("  page_token 重复，停止翻页")
            break
        seen.add(page_token)
    return items


def main():
    retract = "--retract" in sys.argv
    only_chat = None
    except_chat = None
    for i, a in enumerate(sys.argv):
        if a == "--chat" and i + 1 < len(sys.argv):
            only_chat = set(sys.argv[i + 1].split(","))
        if a == "--except-chat" and i + 1 < len(sys.argv):
            except_chat = set(sys.argv[i + 1].split(","))
    token = get_token()
    items = search_all(token)
    print(f"命中 {len(items)} 张卡片")

    found = []
    for it in items:
        md = it.get("meta_data", {})
        mid = md.get("message_id")
        chat = md.get("chat_id", "")
        if not mid:
            continue
        if only_chat and chat not in only_chat:
            continue
        if except_chat and chat in except_chat:
            continue
        group = it.get("display_info", "").split("\n")[0]
        found.append((group, chat, mid))
        print(f"  {group}  chat={chat}  msg={mid}")

    if not found:
        print("未搜到卡片")
        return

    if not retract:
        print(f"\n共 {len(found)} 张。确认后执行: python search_messages.py --retract")
        return

    print(f"\n开始撤回 {len(found)} 张 ...")
    ok = fail = 0
    for group, chat, mid in found:
        req = urllib.request.Request(
            BASE + f"/open-apis/im/v1/messages/{mid}",
            headers={"Authorization": f"Bearer {token}"},
            method="DELETE")
        try:
            with urllib.request.urlopen(req, timeout=20) as r:
                d = json.loads(r.read().decode())
            if d.get("code") == 0:
                ok += 1
                print(f"  [✓] {group}")
            else:
                fail += 1
                print(f"  [✗] {group}: {d.get('code')} {d.get('msg')}")
        except urllib.error.HTTPError as e:
            fail += 1
            print(f"  [✗] {group}: HTTP {e.code} {e.read().decode()[:200]}")
    print(f"\n完成: 成功 {ok} / 失败 {fail}")


if __name__ == "__main__":
    main()
