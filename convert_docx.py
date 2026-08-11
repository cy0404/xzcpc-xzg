import mammoth, re, os

docx = r'C:\Users\xiemg\Documents\xwechat_files\wxid_pxr6eqct323i21_dbc1\msg\file\2026-08\象子冷冻牛油果泥门店报损流程(3).docx'
img_dir = r'C:\Users\xiemg\Documents\test\inventory-tool\xzcpc-xzg\upload\h5\guide-img'
out_path = r'C:\Users\xiemg\Documents\test\inventory-tool\xzcpc-xzg\upload\h5\loss-guide.html'

style_map = "p[style-name='!'] => p.red:fresh"

with open(docx, 'rb') as f:
    result = mammoth.convert_to_html(f, style_map=style_map)

html = result.value
imgs = sorted([f for f in os.listdir(img_dir) if os.path.isfile(os.path.join(img_dir, f))])

# Replace base64 images with file references
def make_replacer():
    idx = [0]
    def replacer(m):
        i = idx[0]
        idx[0] += 1
        if i < len(imgs):
            return 'src="guide-img/' + imgs[i] + '"'
        return m.group(0)
    return replacer

html = re.sub(r'src="data:image/[^"]+"', make_replacer(), html)

head = """<!DOCTYPE html>
<html><head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<style>
body{font-family:-apple-system,sans-serif;padding:16px;max-width:600px;margin:0 auto;color:#1F2421;line-height:1.8}
img{max-width:100%;border-radius:8px;margin:8px 0}
table{width:100%;border-collapse:collapse}
h1,h2{color:#2F8F57}
p[style*="color:red"], span[style*="color:red"], [style*="color:red"] { color: #E05A47 !important; }
</style></head><body>
"""

html = head + html + '</body></html>'

with open(out_path, 'w', encoding='utf-8') as f:
    f.write(html)

print('Size:', len(html), 'bytes, Images:', len(imgs))
