#!/usr/bin/env python3
"""
智能路由：纯文本 → DeepSeek，带图片 → Kimi
用法：
  python ai_router.py "你的问题"
  python ai_router.py "描述这张图" "https://xxx.com/pic.jpg"
"""

import sys, os, json, requests

DEEPSEEK_KEY = os.getenv("DEEPSEEK_API_KEY", "sk-3deb3d827bb74f9c98a5d4a82db40237")
KIMI_KEY     = os.getenv("KIMI_API_KEY",     "sk-MRBVuh5hySZIHS4OXfVwWKhvFhXVzpYfmFtZCI09VTqgeLMA")

def call_deepseek(messages):
    resp = requests.post(
        "https://api.deepseek.com/v1/chat/completions",
        headers={"Authorization": f"Bearer {DEEPSEEK_KEY}", "Content-Type": "application/json"},
        json={"model": "deepseek-chat", "messages": messages},
        timeout=120
    )
    return resp.json()["choices"][0]["message"]["content"]

def call_kimi(messages):
    resp = requests.post(
        "https://api.moonshot.ai/v1/chat/completions",
        headers={"Authorization": f"Bearer {KIMI_KEY}", "Content-Type": "application/json"},
        json={"model": "kimi-k2.6", "messages": messages},
        timeout=120
    )
    return resp.json()["choices"][0]["message"]["content"]

def has_image(messages):
    for msg in messages:
        content = msg.get("content", "")
        if isinstance(content, list):
            for part in content:
                if part.get("type") == "image_url":
                    return True
        elif isinstance(content, str):
            if content.startswith("http") and any(content.lower().endswith(ext) for ext in [".jpg", ".jpeg", ".png", ".gif", ".webp"]):
                return True
    return False

if __name__ == "__main__":
    args = sys.argv[1:]
    if not args:
        print("用法: python ai_router.py '问题' [图片URL]")
        sys.exit(1)

    text = args[0]
    images = [a for a in args[1:] if a.startswith("http")]

    if images:
        content = [{"type": "text", "text": text}]
        for url in images:
            content.append({"type": "image_url", "image_url": {"url": url}})
        messages = [{"role": "user", "content": content}]
        print("[使用 Kimi]", flush=True)
        print(call_kimi(messages))
    else:
        messages = [{"role": "user", "content": text}]
        print("[使用 DeepSeek]", flush=True)
        print(call_deepseek(messages))
