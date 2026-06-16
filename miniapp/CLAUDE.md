# 盘点工具 1.0 — 小程序端参考

## 基本信息

- **框架**：uni-app (Vue 3 + TypeScript + Vite)，目标微信小程序
- **UI 库**：wot-design-uni（easycom 自动注册 `wd-*` 组件）
- **状态管理**：Pinia
- **端口**：Vite dev server 3000，代理 `/api` → `localhost:8080`
- **启动**：`cd miniapp && npm run dev:mp-weixin` → 微信开发者工具导入 `dist/dev/mp-weixin/`

## 页面结构与路由

所有页面在 `src/pages.json` 中扁平注册，无 tabBar，通过 `uni.navigateTo` / `uni.redirectTo` 导航：

| 路由 | 页面 | 核心职责 |
|------|------|---------|
| `pages/login/index` | 登录 | 微信授权 → 手机号绑定 → 进入任务列表 |
| `pages/task/list/index` | 任务列表 | 当前任务 + 历史任务，按状态分组展示 |
| `pages/task/detail/index` | 任务详情 | 总进度 + 分区列表（含状态/进度），入口汇总：查看结果/查看汇总/调整分区 |
| `pages/task/zone-entry/index` | 盘点物料 | 核心录入页：搜索物料、输入数量、备注弹窗、失焦自动保存、保存本分区 |
| `pages/task/zone-adjust/index` | 调整分区与物料 | 增删分区、移除物料、添加物料入口 |
| `pages/task/material-pick/index` | 添加物料 | 搜索候选项并添加到分区（已在分区的置灰） |
| `pages/task/summary/index` | 盘点汇总 | 完成情况 + 跨分区物料汇总 + 提交按钮 |
| `pages/task/result/index` | 盘点结果 | 只读，按分区/按物料汇总双视图切换 |

## 认证流程

```
App.vue onLaunch → userStore.checkLogin() → 本地 token 存在 → GET /auth/me 验证
                                                   ↓ 失败 → 清除 token
任务列表 onShow → isLoggedIn 为 false → reLaunch 到登录页

登录页：
  step 1「微信授权登录」→ uni.login() 拿 code → POST /auth/wx/login
    → bound=true  → 直接进入任务列表
    → bound=false → 切换到 step 2 绑定手机号
  step 2「绑定手机号」→ 输入手机号 → POST /auth/wx/bind-phone → 进入任务列表
```

- **登录判定**：`isLoggedIn = !!token && bound`（必须同时有 token 且已绑定手机号）
- **401 拦截**：request.ts 中任何响应 `code === 401` 自动清 token 跳登录页
- **开发模式**：登录页有黄色提示条，可直接输入明文手机号绑定（绕过微信 getPhoneNumber）

## API 层

所有接口前缀 `/api/mp`，token 通过 `Authorization: Bearer <token>` 传递。

- `src/api/auth.ts` — 登录/绑定/登出/查当前用户
- `src/api/task.ts` — 任务列表/详情/汇总/结果/提交
- `src/api/zone.ts` — 分区物料列表/保存/单条保存/分区增删排
- `src/api/material.ts` — 候选物料搜索/添加/移除/排序

请求封装 `src/utils/request.ts`：
- 自动挂 token、全局 loading、统一错误提示
- 错误码映射：401 未登录、4031 未指派任务、4032 已提交不可改、4033 已过期、4040 任务不存在

## Store

- `user.ts` — token、phone、storeName、bound；checkLogin/wxLogin/bindPhone/logout
- `task.ts` — currentTasks[]、historyTasks[]、loading；fetchTaskList/reset

## 组件

- `EmptyState.vue` — 空状态占位，props: text/type(empty|error|network)
- `Skeleton.vue` — 骨架屏，props: rows
- `ConfirmDialog.vue` — wd-message-box 薄封装，props: show/title/content，emits: confirm/cancel

## 重要注意事项

### uni-app 特有的坑
1. **`manifest.json` 和 `pages.json` 必须放在 `src/` 目录下**，否则 uni-app CLI 找不到
2. **pages.json 中的 page path 不加 `src/` 前缀**，uni-app 默认以 src 为根
3. **Vue 响应式 API（ref、computed、reactive 等）从 `vue` 导入**，不能从 `@dcloudio/uni-app` 导入
4. **uni-app 生命周期钩子（onLoad、onShow、onLaunch 等）从 `@dcloudio/uni-app` 导入**
5. **`wot-design-uni` 没有 default export**，组件通过 easycom 自动注册，直接使用 `<wd-xxx>` 标签即可；如需使用工具函数（useToast、useMessage），按需导入命名导出

### 业务逻辑要点
6. **单店长单任务**：同一手机号同时最多一个未提交任务
7. **分区三态**：未开始（无任何录入）/ 进行中（部分录入）/ 已录入（所有物料填齐 + zone_saved=1）
8. **数量可为 0，不可为负数或空**；负数前端红框阻止保存
9. **调整分区/物料只影响当前任务快照**，不修改基础物料库
10. **删除已录入数据须二次确认**，弹窗提示「将丢失 X 条录入数据」
11. **截止时间过期**：列表/详情红字提示，写接口后端 AOP 拦截返回 4033
12. **已提交任务不可编辑、不可重复提交**
13. **提交后副作用**：更新 task_material_summary + store_zone_material（后端已实现）
14. **zone-entry 页的失焦自动保存**：每个物料 input blur 时调用 `itemSave()` 单条保存草稿；「保存本分区」按钮调用 `saveZone()` 批量保存并置 `zone_saved=1`

### 当前代码待完善
15. **没有登出按钮**：store 导出了 logout() 但 UI 未调用
16. **zone-adjust 页为每个分区串行调用 fetchZoneMaterials**：分区多时较慢，可考虑后端提供批量接口
17. **`api/material.ts` 中的 fetchCandidates** 在逻辑上属于 zone 操作，但物理上放在了 material.ts
