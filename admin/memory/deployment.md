# 象掌柜总部管理后台 — 部署指南

## 部署目标

| 项目 | 值 |
|---|---|
| 服务器 | `162.14.122.80` |
| SSH 端口 | `22` |
| 部署路径 | `/home/StoreInventory/server/frontEnd` |
| 构建产物 | `admin/dist/`（Vite 构建输出） |
| 访问方式 | Nginx 静态文件服务 + 反向代理 |

---

## 一、构建前端

在项目根目录 `inventory-tool/` 下执行：

```bash
# 进入 admin 目录
cd admin

# 安装依赖（首次或 package.json 变更时）
npm install

# 生产构建
npm run build
```

构建完成后，产物在 `admin/dist/` 目录，包含 `index.html` 及 `assets/`（JS/CSS/图片等静态资源）。

> **说明**：Hash 路由模式（`createWebHashHistory`），所有 URL 带 `#/` 前缀，因此 Nginx 只需指向 `index.html`，无需配置 `try_files` fallback。

---

## 二、上传到服务器

### 方式一：SCP 直接上传（推荐）

```bash
# 在 admin/ 目录下执行
scp -r dist/* root@162.14.122.80:/home/StoreInventory/server/frontEnd/
```

### 方式二：先压缩再上传

```bash
# 压缩
cd admin && tar -czf dist.tar.gz dist/

# 上传
scp dist.tar.gz root@162.14.122.80:/home/StoreInventory/server/frontEnd/

# SSH 到服务器解压
ssh root@162.14.122.80
cd /home/StoreInventory/server/frontEnd
tar -xzf dist.tar.gz --strip-components=1  # 去掉 dist/ 前缀，文件直接放 frontEnd/
rm dist.tar.gz
```

### 方式三：rsync 增量同步

```bash
rsync -avz --delete admin/dist/ root@162.14.122.80:/home/StoreInventory/server/frontEnd/
```

> `--delete` 会删除服务器上多余的文件，确保与本地构建产物完全一致。

---

## 三、Nginx 配置

服务器需安装 Nginx，配置文件参考：

```nginx
server {
    listen       80;
    server_name  162.14.122.80;  # 或你的域名

    # 管理后台静态文件
    location / {
        root   /home/StoreInventory/server/frontEnd;
        index  index.html;
    }

    # API 反向代理到后端
    location /api/ {
        proxy_pass         http://127.0.0.1:8080;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
    }

    # 上传文件（如有独立上传路径）
    location /upload/ {
        alias /home/StoreInventory/server/upload/;
    }
}
```

### 重载 Nginx

```bash
# 测试配置
nginx -t

# 重载
nginx -s reload

# 或通过 systemctl
systemctl reload nginx
```

---

## 四、验证部署

1. **浏览器访问**：`http://162.14.122.80`（或配置的域名）
2. **检查项**：
   - 页面正常加载，标题显示"象掌柜总部"
   - 侧边栏菜单完整（盘点管理 / 支出管理 / 人员管理 / 系统设置）
   - 浏览器 DevTools Network 面板确认静态资源（JS/CSS）加载无 404
   - API 请求 `/api/*` 正常代理到后端
3. **登录验证**：飞书 OAuth 流程走通（生产环境），或确认开发登录可用

---

## 五、目录结构（服务器端）

部署后的目录结构：

```
/home/StoreInventory/server/
├── frontEnd/                  # 前端静态文件（本指南部署目标）
│   ├── index.html
│   └── assets/
│       ├── *.js
│       └── *.css
├── upload/                    # 上传文件存储（如有）
└── mq-server/                 # 后端服务（消息队列模块）
```

---

## 六、常见问题

### Q: 部署后页面空白？

- 检查浏览器控制台是否有 JS 报错
- 确认 `index.html` 引用的 `assets/` 路径正确
- 确认 Nginx `root` 指向正确的 `frontEnd` 目录

### Q: API 请求 404？

- 确认后端 Java 服务已启动（`java -jar server-1.0.0.jar`）
- 检查 Nginx `proxy_pass` 端口与后端监听端口一致
- 检查防火墙是否放行后端端口

### Q: 登录/认证失败？

- 生产环境确认飞书应用配置正确（App ID、App Secret）
- 生产环境确认回调域名与飞书开放平台配置一致
- 开发环境确认 `/api/auth/dev/login` 端点可用

### Q: 部署后旧文件残留？

- 使用 rsync `--delete` 参数自动清理
- 或手动 `rm -rf /home/StoreInventory/server/frontEnd/*` 后再上传

---

## 七、快速部署命令汇总

```bash
# === 本地 ===
cd admin
npm run build

# === 上传（选一种） ===
scp -r dist/* root@162.14.122.80:/home/StoreInventory/server/frontEnd/

# === 服务器 ===
ssh root@162.14.122.80 "nginx -t && nginx -s reload"
```
