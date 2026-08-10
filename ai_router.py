#!/usr/bin/env python3
"""
智能路由：纯文本 → DeepSeek，带图片 → 智谱 GLM-4V
用法：
  python ai_router.py "你的问题"
  python ai_router.py "描述这张图" "C:/path/to/screenshot.png"
  python ai_router.py "描述这张图" "https://xxx.com/pic.jpg"
"""

import sys, os, base64, json, requests
import urllib3
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

DEEPSEEK_KEY = os.getenv("DEEPSEEK_API_KEY", "sk-3deb3d827bb74f9c98a5d4a82db40237")
ZHIPU_KEY    = os.getenv("ZHIPU_API_KEY", "410cfab6608a4b8180797b27c2ba3f0f.ONHbjCAqcaUFHy4Y")

def call_deepseek(messages):
    resp = requests.post(
        "https://api.deepseek.com/v1/chat/completions",
        headers={"Authorization": f"Bearer {DEEPSEEK_KEY}", "Content-Type": "application/json"},
        json={"model": "deepseek-chat", "messages": messages},
        timeout=120
    )
    return resp.json()["choices"][0]["message"]["content"]

def call_zhipu(messages):
    resp = requests.post(
        "https://open.bigmodel.cn/api/paas/v4/chat/completions",
        headers={"Authorization": f"Bearer {ZHIPU_KEY}", "Content-Type": "application/json"},
        json={"model": "glm-4v", "messages": messages},
        timeout=120,
        verify=False
    )
    j = resp.json()
    if "choices" not in j:
        print("[智谱错误]", json.dumps(j, ensure_ascii=False), file=sys.stderr)
        sys.exit(1)
    return j["choices"][0]["message"]["content"]

def image_to_data_url(path):
    """将本地图片文件转为 data: URL"""
    with open(path, "rb") as f:
        b64 = base64.b64encode(f.read()).decode()
    ext = os.path.splitext(path)[1].lower().lstrip(".")
    mime = {"jpg": "jpeg", "jpeg": "jpeg", "png": "png", "gif": "gif", "webp": "webp"}.get(ext, "png")
    return f"data:image/{mime};base64,{b64}"

if __name__ == "__main__":
    args = sys.argv[1:]
    if not args:
        print("用法: python ai_router.py '问题' [图片路径或URL]")
        sys.exit(1)

    text = args[0]
    images = args[1:]

    if images:
        content = [{"type": "text", "text": text}]
        for img in images:
            if img.startswith("http"):
                url = img
            else:
                url = image_to_data_url(img)
            content.append({"type": "image_url", "image_url": {"url": url}})
        messages = [{"role": "user", "content": content}]
        print("[使用 智谱 GLM-4V]", flush=True)
        print(call_zhipu(messages))
    else:
        messages = [{"role": "user", "content": text}]
        print("[使用 DeepSeek]", flush=True)
        print(call_deepseek(messages))
