# -*- coding: utf-8 -*-
"""发送未验收问题提醒卡片测试卡到日志群（与 IssueAcceptanceReminderJob 卡片结构完全一致）"""
import json
import urllib.request

WEBHOOK = "https://open.feishu.cn/open-apis/bot/v2/hook/f69d5ede-5be0-4d31-a285-4d64f4757bf8"

def text_obj(content):
    return {"tag": "plain_text", "content": content}

def md(content):
    return {"tag": "markdown", "content": content}

# ---------- 与 Java 代码 cardHeader("yellow", "⚠️ 未验收问题提醒") 一致 ----------
header = {
    "template": "yellow",
    "title": text_obj("⚠️ 未验收问题提醒"),
}

# ---------- 与 Java 代码 mdEl(info) 一致 ----------
info = md("本店有 <font color='red'>**4**</font> 条问题已处理完成，请在**「象子掌柜」小程序**中验收：")

# ---------- 与 Java 代码 table 元素一致（固定像素，总宽≈卡片画布） ----------
table = {
    "tag": "table",
    "columns": [
        {"name": "title", "display_name": "问题", "width": "280px"},
        {"name": "type", "display_name": "类型", "width": "120px"},
        {"name": "time", "display_name": "提交时间", "width": "140px"},
    ],
    "rows": [
        {"title": "地板漏水", "type": "设备问题", "time": "08-25"},
        {"title": "冻库温控面板报错 E-03，联系厂家维修未果，多次重启后依旧显示温度传感器异常，导致冷藏区温度持续偏高", "type": "设备问题", "time": "08-24"},
        {"title": "充不了电，发的充电器不对", "type": "设备问题", "time": "08-24"},
        {"title": "制冰机不制冷", "type": "设备问题", "time": "08-23"},
    ],
    "row_height": "low",
    "page_size": 10,
}

# ---------- 与 Java 代码一致：指引行在表格下方（note 灰色小字） ----------
bottom = {"tag": "note", "elements": [{"tag": "lark_md", "content": "📱 微信 →「象子掌柜」小程序 → 问题处理 → 待验收 → 点「已解决」"}]}

payload = {
    "msg_type": "interactive",
    "card": {
        "config": {"wide_screen_mode": True},
        "header": header,
        "elements": [info, {"tag": "hr"}, table, bottom],
    },
}

req = urllib.request.Request(
    WEBHOOK,
    data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
    headers={"Content-Type": "application/json"},
)
try:
    resp = urllib.request.urlopen(req, timeout=15)
    print("HTTP", resp.status)
    print(resp.read().decode("utf-8"))
except Exception as e:
    print("发送失败:", e)
