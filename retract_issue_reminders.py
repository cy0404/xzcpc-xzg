#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
撤回误发的「未验收问题提醒」卡片（飞书应用机器人）。

用法:
    python retract_issue_reminders.py            # dry-run：列出匹配到的卡片，不撤回
    python retract_issue_reminders.py --retract  # 执行撤回
    python retract_issue_reminders.py --from 2026-08-27  # 指定回溯起始（默认今天 00:00）

原理: 列机器人所在群 -> 拉群消息历史 -> 匹配「未验收问题提醒」卡片 -> 撤回。
配置: 应用凭据默认读 server/src/main/resources/application-local.yml,可传 --app-id/--app-secret 覆盖。
"""
import argparse
import json
import re
import time
import urllib.parse
import urllib.request

FEISHU_BASE = "https://open.feishu.cn"
KEYWORD = "未验收问题提醒"


def get_tenant_token(app_id, app_secret):
    req = urllib.request.Request(
        FEISHU_BASE + "/open-apis/auth/v3/tenant_access_token/internal",
        data=json.dumps({"app_id": app_id, "app_secret": app_secret}).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        d = json.loads(resp.read().decode())
    if d.get("code") != 0:
        raise RuntimeError(f"获取 tenant token 失败: {d}")
    return d["tenant_access_token"]


def api_get(token, path, params):
    url = FEISHU_BASE + path + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            detail = e.read().decode()
        except Exception:
            detail = ""
        raise RuntimeError(f"GET {path} HTTP {e.code}: {detail}")


def api_delete(token, path):
    req = urllib.request.Request(
        FEISHU_BASE + path,
        headers={"Authorization": f"Bearer {token}"},
        method="DELETE",
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode())


def list_chats(token):
    items, page_token = [], ""
    while True:
        params = {"page_size": 100}
        if page_token:
            params["page_token"] = page_token
        d = api_get(token, "/open-apis/im/v1/chats", params)
        if d.get("code") != 0:
            raise RuntimeError(f"列群失败: {d}")
        items.extend(d.get("data", {}).get("items", []))
        page_token = d.get("data", {}).get("page_token", "")
        if not page_token:
            break
    return items


def list_messages(token, chat_id, start_ms, end_ms):
    items, page_token = [], ""
    while True:
        params = {
            "container_id_type": "chat",
            "container_id": chat_id,
            "start_time": str(start_ms),
            "end_time": str(end_ms),
            "page_size": 50,
        }
        if page_token:
            params["page_token"] = page_token
        d = api_get(token, "/open-apis/im/v1/messages", params)
        if d.get("code") != 0:
            print(f"   [跳过] 拉群 {chat_id} 消息失败: {d.get('code')} {d.get('msg')}")
            return []
        items.extend(d.get("data", {}).get("items", []))
        page_token = d.get("data", {}).get("page_token", "")
        if not page_token:
            break
    return items


def is_reminder_card(msg):
    if msg.get("msg_type") != "interactive":
        return False
    try:
        content = json.loads(msg.get("content", "{}"))
    except Exception:
        return False
    return KEYWORD in json.dumps(content, ensure_ascii=False)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--retract", action="store_true", help="执行撤回（默认只列出）")
    ap.add_argument("--from", dest="from_date", default=time.strftime("%Y-%m-%d"),
                    help="回溯起始日期，默认今天")
    ap.add_argument("--chats-file", default=None,
                    help="群 chat_id 文件（每行一个），跳过列群接口（无 im:chat:readonly 权限时用）")
    ap.add_argument("--app-id", default=None)
    ap.add_argument("--app-secret", default=None)
    args = ap.parse_args()

    app_id = args.app_id
    app_secret = args.app_secret
    if not app_id or not app_secret:
        base = "xzcpc-xzg/server/src/main/resources"
        if not app_id:
            with open(f"{base}/application.yml", encoding="utf-8") as f:
                m = re.search(r"app-id:\s*(\S+)", f.read())
                if m:
                    app_id = m.group(1)
        if not app_secret:
            with open(f"{base}/application-local.yml", encoding="utf-8") as f:
                m = re.search(r"FEISHU_APP_SECRET:\s*(\S+)", f.read())
                if m:
                    app_secret = m.group(1)
    assert app_id and app_secret, "缺少 app-id / app-secret"

    start_ms = int(time.mktime(time.strptime(args.from_date, "%Y-%m-%d"))) * 1000
    end_ms = int(time.time()) * 1000

    print(f"获取 token ...")
    token = get_tenant_token(app_id, app_secret)
    if args.chats_file:
        with open(args.chats_file, encoding="utf-8-sig") as f:
            chats = [{"chat_id": line.strip(), "name": line.strip()}
                     for line in f if line.strip()]
        print(f"从文件读取 {len(chats)} 个群")
    else:
        print(f"列出机器人所在群 ...")
        chats = list_chats(token)
        print(f"共 {len(chats)} 个群")

    found = []
    for chat in chats:
        cid = chat.get("chat_id", "")
        name = chat.get("name", "")
        msgs = list_messages(token, cid, start_ms, end_ms)
        hits = [m for m in msgs if is_reminder_card(m)]
        if hits:
            print(f"[命中] 群 {name} ({cid}) 共 {len(hits)} 张卡片")
            for m in hits:
                mid = m.get("message_id")
                ct = time.strftime("%H:%M:%S", time.localtime(int(m.get("create_time", "0")) / 1000))
                found.append((cid, name, mid, ct))
                print(f"   - message_id={mid} 时间={ct}")
        else:
            print(f"[无] {name} ({cid})")

    if not found:
        print("未匹配到任何「未验收问题提醒」卡片，请检查 --from 时间范围")
        return

    if not args.retract:
        print(f"\n共 {len(found)} 张卡片待撤回。确认无误后执行: python retract_issue_reminders.py --retract")
        return

    print(f"\n开始撤回 {len(found)} 张卡片 ...")
    ok = fail = 0
    for cid, name, mid, ct in found:
        d = api_delete(token, f"/open-apis/im/v1/messages/{mid}")
        if d.get("code") == 0:
            ok += 1
            print(f"[撤回成功] 群 {name} 消息 {mid} ({ct})")
        else:
            fail += 1
            print(f"[撤回失败] 群 {name} 消息 {mid}: {d.get('code')} {d.get('msg')}")
    print(f"\n完成: 成功 {ok} / 失败 {fail}")


if __name__ == "__main__":
    main()
