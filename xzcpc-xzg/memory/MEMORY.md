# xzcpc-xzg 核心知识库

> 每次会话自动加载。详细内容见各专题文件。

## 📋 速查卡

| 项 | 值 |
|----|-----|
| **项目** | 盘点工具 1.0 / 象掌柜 (xzcpc-xzg) |
| **用途** | 门店月度盘点管理系统：总部配模板→下发任务→门店小程序录入→自动汇总 |
| **技术栈** | Java 17 / Spring Boot 3.2.5 / MyBatis-Plus 3.5.5 / MySQL 8.0 |
| **前端** | Vue 3 + Ant Design Vue 4（总部）/ uni-app Vue 3（小程序） |
| **端口** | 总部端 4026 / 小程序端 30261 |
| **模块数** | 8 个 Maven 模块 / ~210 Java 文件 / ~125 API |
| **数据库** | `store_inventory` / schema.sql 建表 / 30 个迁移脚本 |

## 🆕 近期变更

### 2026-09-03 月度发券卡移除 @提及 + 汇总卡定稿交付生产（详见 dateMemory/2026-09-03.md）

- **月度发券确认卡不再 @人**：LossReportMonthlyVoucherJob 删 getAtUsers/appendAtMentions（9/3 10:00 生产旧 jar 已带 @ 发过一张，修复从下次部署起生效）
- **构建卡点复盘**：`clean package` 在 task testCompile/mp compile 连挂——真因是 **MpWebMvcConfig 缺 `import org.springframework.http.CacheControl`**（加 /upload/h5 no-cache 时漏 import，该改动从未成功编译）；补 import 后全量通过，no-cache 逻辑首次真正进 jar
- **到货报损汇总卡（周/月）口径定稿**：排除蔬菜水果类；**按物料聚合三列**（物料名 | 到货报损次数 | 报损总数量，无门店维度）；牛油果泥「件」×24 换算到包再聚合（134 包 + 2 件 ≠ 136，需先换算）；`period_loss_summary` 周/月共用一行收件人配置
- 新 jar（含全部卡片 + @移除 + no-cache）已构建交付，待用户部署 4026；8 月月汇总可手动补发：`POST /api/public/loss-report/trigger-period-summary?type=month`

### 2026-09-02 门店操作预警卡定稿（文本清单 + 查看跳转按钮）+ 支出页带参直达（详见 dateMemory/2026-09-02.md）

- **WeeklyStoreWarningJob 定稿**：每周一 8:30（`0 30 8 ? * MON`）；报损/支出分开判定，**缺任一即上榜（并集）**；文本分行清单（用户否决表格）：每督导一段 `**名**（N家）🔵无支出x·🟠无报损y`，每店一行 `店名｜无支出、无报损`
- **⚠️ schema 2.0 不支持 action 按钮（200861）**：跳转按钮卡走 **schema 1.0**（无 schema 字段、elements 顶层），与 LossReportPeriodSummaryJob 同款（见 patterns P017）
- **双按钮**：「查看支出明细/查看报损明细」→ 302 中介 `/api/public/weekly-warning/{expense,loss}-list-link`（applink 不能带 # 直达；报损**不传 lossType**=全部类型）
- **个人卡零配置**（supervisor_store_access 映射 open_id）；**群卡读 sys_config.feishu_supervisor_group_chat_id**（空则跳过；生产库需确认真实督导群，勿指测试群）
- **ExpenseList.vue 补带参直达**（仿 LossList 读 route.query startDate/endDate → dateRange）；LossList 早已支持
- 9/3 发版验证两个 302 正常；⚠️ trigger-weekly-warning 会真发生产督导，测试禁用

### 2026-08-29 补发联动企迈出库单 + 统计报表督导视图 + 评价管理渠道化/督导范围 + 物料同步增强（详见 dateMemory/2026-08-29.md）

- **出库口径定稿（用户两次纠正强制）**：数量按**库存单位**（stock_unit，回退 purchase_unit）经换算链换算；单价用**采购单价 purchase_price**；❌ 绝不 order_unit/order_price
- **企迈 9.2.4 建出库单**：`externalNo=BF{id}-{seq}` 幂等 + `outbound_order` 表 upsert；`autoOutbound=1` 立即扣库存；仓库映射：冷冻类/牛油果泥等→冷冻仓 PSCK000149，鲜奶类→PSCK000070，其余（固体/茶叶/液体…）→总仓 PSCK000023
- **convertQty SQL 修复**：material_conversion_rule **无 material_id 列**，必须 JOIN material_inventory_rule by rule_id；列名 from_quantity/to_quantity（旧 SQL 每跑必抛错被 catch 吞掉→降级建单=7张错单根因）
- **换算失败不再降级**（核心）：换算失败 → convertQty 返回 null → `markOutboundFailed`（BF{id}-0 failed 占位 + 飞书告警）→ **绝不带错误数量建单**；本地补发状态照常流转（confirmed_resend UPDATE 先于建单），门店收货不受影响；H5 已补发 tab 红行 + 「重新补发」→ `POST /api/public/loss-report/retry-outbound` 重试（成功 BF{id}-1 新单，再失败覆盖 BF{id}-0）
- **7 张历史错单**（数量错 24/16/12/5 倍）：错误金额 20,545.60 → 正确 871.80 元；处置 = `database/fix-7-bad-outbound.sql`（success→failed）+ H5 点重新补发按正确数量建新单；**企迈旧单无 API 撤销，需仓配人工处理**
- **启动失败修复**：① m2 依赖 jar 落后工作区（8/11 旧 common 无 feedbackNotifyExecutor）→ `mvn install -pl common,template,task,expense,people` ② IssueFeedbackServiceImpl final+@Qualifier+Lombok 构造注入丢注解 → 改 @Autowired 字段注入（见 patterns.md）
- **mp 编译修复**：唯一真错误 SmartOrderServiceImpl:372 `Task.getId()` Integer→Long（`.longValue()`）；其余「找不到符号」是旧 task jar 缺新方法，install 后自动消失；mp 修好后**全量打包可行**，server/mp-server 两 jar 全最新
- **统计报表督导视图**：总部下拉选督导 / 督导登录自动锁定（前端 `isSupervisorOnlyRole` + 后端 openId 强制过滤）；CSS conic-gradient 饼图 + 状态 chips 点击切换门店列表（含**督导列**）；固定 340px 高内部滚动——**坑：a-row/a-col 组件根元素不携带父 scoped data-v，scoped 样式不命中导致溢出，改纯 div flex 布局解决**
- **评价管理渠道化 + 督导数据范围**：issue_feedback 加 `channel`（默认 scan，美团/小红书预留；迁移脚本 `migration-add-feedback-channel.sql` **待执行**）；列表按渠道筛选 + Excel 导出；**督导只能看自己负责门店的反馈**（storeScope + adminPage in 过滤 + 详情/改状态越权校验）；入口对督导开放
- **物料同步增强**：源集合改按 **qm_code** 匹配（接口 id 会随上游换体系，按 id 匹配会误删存量）；半成品关闭时仍拉接口仅做保护 + del_flag 归位（ENABLED→0 复活、DISABLED→1）；新增生产→测试库镜像脚本 `migration-sync-material-prod-to-test.sql`

### 2026-08-28 未验收问题提醒：门店 chatId 过滤 + 单群触发 + 旧卡片撤回（详见 dateMemory/2026-08-28.md）

- **卡片链接 `/upload/h5/` → `/h5/`**（飞书 H5 = jar 内 static/）；表格删序号列；问题标题 lark_md 链接直达 H5
- **测试群运行时切换**：`sys_config.feishu_issue_reminder_test_chat_id` 非空=测试模式（发测试群、按钮无 chatId、H5 全量）；**为空=门店模式**（按 `store_info.chat_id` 分组、按钮带 `?chatId=`、H5 只显示本店；无问题的群不发卡片）。改库即生效无需重启
- **触发接口**：`POST /api/public/issue/trigger-acceptance-reminder`（无参=全量：测试群或全部门店群；**`?chatId=`=单群**，只发指定群）。定时 cron `0 0 9 * * ?`
- **H5 issue-accept.html**：按钮复位修复（卡「提交中」）、20 秒轮询 + 指纹 diff、URL chatId 过滤
- **飞书撤回**：`DELETE /open-apis/im/v1/messages/{message_id}`（POST 404），24h 内；已撤 49 张旧版门店卡片（recall-issue-cards.sh）
- **编译修复**：LossReportH5Controller `outboundPrice` 方法缺失补上（order_price 优先/purchase_price 兜底）
- ⚠️ 生产当前是 13:16 部署版（有 chatId 过滤、**无单群参数**）；13:31 单群版 jar 已打包**未部署**

### 2026-08-27 督导-门店关系同步 xinfo 外部接口（生产闭环）

- **新功能**：`POST /api/public/supervisor-sync/trigger`（默认 dry-run，`?apply=true` 写库）——拉新门店接口（X-API-Key）→ 匹配督导（接口 `supervisor.userId` ↔ `admin_permission.user_id`）→ 匹配门店（接口 `id` ↔ `store_info.store_code`）→ upsert `supervisor_store_access`（source='auto'，无条件覆盖）→ 更新 supervisor_name + xinfo 字段（xinfo_store_name/province/city/district/address，旧店不改 store_name）
- **新店建店**：store_id **本地生成** `cm`+24 位（genStoreId 查重），⚠️ 不可取接口 id（NOT NULL 无默认值会 500）
- **数据库**：admin_permission 加 `user_id` + uk 索引；supervisor_store_access 加 `source`；sys_config 预置 supervisor_api_url/key（`migration-add-supervisor-user-id.sql`）
- **生产结果**：188 店 xinfo 全填充、170 条 auto 关系、8 家新店（store_id 清单见日志）、173 家 supervisor_name；9 位督导
- **督导名写本地名**（用户拍板）：admin_permission.name，接口 displayName 只做 mismatch 提醒
- **⚠️ 生产 4026 还是 8-20 旧 jar**（无督导同步接口，trigger 404），生产数据是测试库导入的；重新打包部署后才有接口
- **⚠️ admin_permission.name 历史脏数据**（企迈格式带手机号），改 name 不影响权限（权限只看 open_id + roles）；本次清理马迎峰/唐敏 2 位
- 详见 [dateMemory/2026-08-27.md](dateMemory/2026-08-27.md)

### 2026-08-26 未验收问题提醒上线（每日9点黄色卡片到门店群）+ 已读回执追踪

- **需求**：每天 9:00 统计 `issue.status='pending_acceptance'` 的问题，黄色预警卡片发到**每个门店自己的飞书群**（store_info.chat_id），提醒在「象子掌柜」小程序验收；含全部待验收问题（每天重复提醒直到验收）
- **新文件** `IssueAcceptanceReminderJob`（server 模块，`@Scheduled 0 0 9 * * ?`，doSend 按群分组、缺 chat_id 记 WARN 跳过）；手动触发 `POST /api/public/issue/trigger-acceptance-reminder`
- **卡片设计定稿**：yellow header「⚠️ 未验收问题提醒」+ 红色加粗条数 + 表格（问题 280px / 类型 120px / 提交时间 170px，**固定像素可手机横滑**，提交时间 `MM-dd HH:mm`）+ 底部 note 灰字验收指引；表格超长省略号可悬浮看全文、page_size 10
- **已读回执**（飞书 `GET /im/v1/messages/{message_id}/read_users`，仅机器人自身 7 天内消息）：新表 `issue_reminder_send_log`（记录 message_id，迁移脚本待执行）；`FeishuMessageService.sendToChat/sendToUser` **改返回 message_id** + 新增 `getReadUsers`（翻页、时间戳→MM-dd HH:mm）；Job 发送后记日志（表未建不影响发送）；查询 `GET /api/public/issue/reminder-read?date=xxx`（每店取当天最后一条）
- **🐛 飞书静默失败修复**：230002（机器人不在群）返回 HTTP 200 + body code != 0，原 sendMessage 不查响应体→静默失败；已加响应体 code 检查记 WARN
- **⚠️ 排查**：issue 表无 chat_id，群 ID 在 store_info（按 store_id JOIN）；测试库 store_inventory，日志群测试门店 chat_id=`oc_ea177cd1cd4c074677c53785db6fd1b7`；卡片预览走 webhook（不需进群）、正式发送走应用 API（**必须机器人进群**）
- **待办**：用户执行建表迁移+清理脚本；server 重新打包部署；机器人 `cli_aa90f3d66d7adbb7` 拉进日志群；触发后看日志 230002 排查
- 详见 [dateMemory/2026-08-26.md](dateMemory/2026-08-26.md)

### 2026-08-21 Issue 回调乐观锁竞态修复 + InboundSyncJob 飞书聚合告警 + 生产库索引审计

- **🐛 Issue 回调状态不更新根因**（用户：回调到了但状态没变）：`issue` 表带 `@Version` 乐观锁，`/callback` 与 `/callback-records` 并发到达同读 version=0 → records 先写（0→1）→ 状态回调 `updateById` 的 `WHERE version=0` 静默影响 0 行（无异常无重试）→ 状态不更新；DB 证据 row 568 version=1
- **修复**（IssueServiceImpl.java）：新增 `updateWithRetry`（0 行影响→重读 version 重试×3），`syncByCallback`/`applyExternalStatus` 改走它；`saveRecords` 改 `LambdaUpdateWrapper` **只更新 records_data + updated_at**（不触发乐观锁、不覆盖回调并发写入）
- **⚠️ 通用教训**：带 `@Version` 的表并发 select-then-updateById 会静默丢更新；写路径应只更新自己字段或对 0 行重试
- **🐛 InboundSyncJob 报错不告警根因**：FeishuWebhookAlertService 只从 GlobalExceptionHandler（HTTP 请求）+ 入库收货流程触发，**定时任务 catch 的异常只 log.error 不告警**；`Table 'inbound_order' doesn't exist` 是生产库没执行 migration-add-inbound-order.sql（测试库执行过）
- **修复**（InboundSyncJob.java）：fail>0 时聚合一条飞书群告警（FeishuAlertContext httpMethod=JOB、userHint=ok=X fail=Y 失败门店最多 5 家）替代静默
- **⏸️ @Scheduled 已注释 + TODO**（生产建表后再开启）；⚠️ **用户已执行建表迁移，下次发版恢复 `@Scheduled(cron = "0 0 3 * * ?")`**
- **🔍 生产库审计**：50 张 entity 表齐全；**3 组索引待执行**——operation_log `idx_op_created/idx_op_username_module/idx_op_operation`（migration-fix-slow-query-log.sql，日志查询 15s 慢查询）、task `idx_task_created_at`（migration-add-task-created-at-index.sql）、store_info `idx_store_del_updated`（migration-add-indexes.sql #6）；`store_zone_material`（孤儿表）/`operation_log.openid`/`material.biz_code` 判定不需要
- 详见 [dateMemory/2026-08-21.md](dateMemory/2026-08-21.md)

### 2026-08-19 审核完成即时卡片（sendAuditDoneCard）链接修复：与每日 B2 一致

- **用户反馈**：飞书「到货验收报损 · 门店 · 物料」卡片（审核确认后即时发）点「查看详情」，补发页只显示这一条，其他补发不展示
- **卡片身份**：不是每日 9:30 的 B2，而是 `LossReportH5Controller.sendAuditDoneCard`——每确认一单审核即时发的单条通知卡
- **根因**：原链接 `?tab=resend&category=<resolveGroupKey(物料分类)>&reason=<原因>` ——category 是物料所属**分组名**（如乳制品，`loss_notify_card_config` 配置），H5 `dailySummary` 收到分组名走 `AND m.category IN (该分组)` 只查该分组，再叠加 reason 过滤 → 其他分类待补发全被过滤；B2 链接 `category=其他类` 走「排除已配置分组」分支显示全部
- **修复**（LossReportH5Controller.java:439-444）：链接改为与 B2 完全一致——`date=今天 + category=URL编码(其他类) + tab=resend + uid=userId`，去分组/reason 过滤；`groupKey`/`reason` 变量保留（水果蔬菜拦截 + cardType 判断仍用）
- **编译验证**：`mvn compile -pl server -am` 通过
- **⚠️ 部署受阻**：服务器启动新 jar 报 `Could not resolve placeholder 'XINFO_API_KEY'`——`server/application.yml:35` `key: ${XINFO_API_KEY}`（无默认值），服务器环境缺该变量（测试值见 mp-server/server-test.sh:24）；需补环境变量后重启才生效
- **🔍 排查工具**：会话中图片 [Unsupported Image] 看不了时，用根目录 `ai_router.py` 调智谱 GLM-4V 看图（`python ai_router.py "问题" "图片路径"`）；截图在 `%TEMP%\screenshot-*.png`（微信聊天图在 `Documents\xwechat_files\...\InputTemp\`）
- **牛油果泥统计表分档回滚**：三档（全部/24包及以上/24包以下）是排查误判时加的，用户问"为什么有全部tab"后回滚为**两档、默认24包及以上**（buildAvoStatTable 去全部tab+avoStatAll，残留 'all' 值回落 big）；⚠️ 教训：误判方向产生的未定论改动应立即回滚或标记，别留在本地悬着
- 详见 [dateMemory/2026-08-19.md](dateMemory/2026-08-19.md)

### 2026-08-17 牛油果泥审核卡片改版（3主tab×2子tab）+ 全选取消bug + 审核即时刷新

- **需求（用户）**：牛油果泥审核卡片分三个 tab（未审核/已通过/已拒绝），每个 tab 下再有 已下载/未下载 子tab；用户拍板：下载按钮保留（已通过/已拒绝可补下载）、无导出按钮、无视频（只有图片）算已下载、其他类审核页（卡片 B1）不动
- **归口规则**：未审核=`!result`；已通过=`已登记` 或 `补发`（confirmed_resend/received/not_received 已过审核走完补发流程，漏掉会从页面消失——原逻辑把「补发」标签错误显示为「待审核」，已连带修正）；已拒绝=`拒绝`
- **实现（loss-daily-confirm.html，server static/ 内，需打包 server）**：per-主tab 独立子tab记忆（localStorage `avoSubPending/Passed/Rejected`，switchAvoSub 同时写 activeTab）；`avoIsDl(r)`=无视频或 downloaded=true 算已下载；删除隐藏空导出锚点（exportAvocado 函数保留无人调用）；顶部统计卡 statPassed 改为已登记+补发
- **🐛 全选取消 bug 根因**：onCheckChange 回显 = 已勾数 === **全页** checkbox 总数，但 toggleAll 只勾**当前 tab 可见**（其他 tab/搜索隐藏不勾）→ 点全选后全选框立刻弹回未勾选 → 第二次点击永远被当"勾选"，取消分支不可达。修复：回显按「当前 tab 未被搜索隐藏」的可见集合比较（与 toggleAll 操作集合一致）
- **🐛 审核后不归位（改造暴露的遗留）**：doMark 原地更新不刷新 → 审核完卡片留在未审核 tab、统计不更新。修复：`tab==='audit' && materialId` 时 card.remove() + `refreshAvoTabCounts()`（重算三tab子tab数量 + statPending/Passed/Rejected + onCheckChange）
- **验证**：node --check 内嵌 script 通过；B1 分支逐行未动；grep 无 audited/avoSubTab 残留（auditedCount 死变量保留）
- **待执行**：server 打包部署（H5 3 处改动）+ mp-server 打包部署（-nostdin 防挂死 + faststart 一并发布）
- 详见 [dateMemory/2026-08-17.md](dateMemory/2026-08-17.md)

### 2026-08-15 入库管理接入企迈 OpenAPI（方案B：全 OpenAPI 单通道）

- **决策链路**：9.2.3 物品数量更新实测 = 官方确认收货接口（code=0，warehouseNo=cangkuid 与 warehouseMark=控制台仓库ID 均可用，子集 productList 也接受）→ 9.2.2 批量查询入库单（租户级全量、含明细、**无 total 字段按页<pageSize 判尾**）→ 用户拍板「只需要1仓配入库、3采购 选B」：同步/收货全走 OpenAPI，**控制台接口 + Cookie 自动登录彻底不用**（账号 15126252751 也不需要）
- **同步四级归属匹配（2026-08-15 晚间升级，用户提醒"需要用 qmai_store_id"后实测定稿）**：`syncFromQmai(storeId, qmaiStoreId, days)` — 定时 7 天 / 下拉刷新 2 天。流程：近 N 天报货单（按 qmai_store_id 拉）收集归属键 + 本店仓库编码集 → 9.2.2 分页拉全量（安全上限 50 页×1000）→ 类型白名单 {1,3} + 四级匹配：**① bizNo∈键集**（declareNo/requireNo/bizNo/requireNoList/purchaseApplyNoList/**purchaseNoList**/bizNoList）**② warehouseCode∈本店仓库集**（报货单 **storeWarehouseNo** ∪ store_info.cangkuid）**③ 归一化仓库名=店名**（去括号/空白）**④ 首尾包含模糊**（毕节店↔毕节招商花园店，短侧≥2字）→ 按 inbound_no upsert（已存在只更新企迈侧字段；本地 pending 才随企迈状态联动；本地无明细时回填，productNum=inboundNum 兜底 num、price=inboundPrice 兜底 costPrice）
- **🎯 关键实测（2026-08-15）**：9.2.2 **忽略一切门店/仓库过滤参数**（storeIdList/storeIds/storeId/warehouseNo/warehouseCode/warehouseId/warehouseIdList 全返回租户全量），记录里也无 storeId 字段 → 门店归属必须客户端匹配；报货单（9.1.17，按 qmai_store_id 查）记录含 **storeWarehouseNo**（=入库单 warehouseCode）与 **purchaseNoList**（采购单号 CG*；之前只收了 purchaseApplyNoList=CGSQ 采购申请号，**企迈两套编号互不相通**）→ 这才是"用 qmai_store_id"的正确姿势：按店拉报货单拿到该店仓库编码集+键集再去匹配入库单。**一店可多仓库**（版纳曼城店 MDCK000152/154 两个仓），故匹配按仓库编码集合而非单个 cangkuid；回填实测 5682 单（层级1 4303 + 层级2 1381），未匹配 421 全为冷冻仓 PSCK000149/新螺蛳湾总仓 PSCK000023/培训部报货专用店 MDCK000094（仓库级/培训部单，正确地排除在外）
- **inbound_order 表新列**：`qmai_store_id`（按原计划补回，归属门店的企迈 ID）+ `warehouse_code`（9.2.2 warehouseCode，**9.2.3 确认收货 warehouseNo 优先用它**→ 回落 store cangkuid → 回落 warehouseMark）；回填脚本对名称匹配/报货单唯一仓库的门店自动补缺失 cangkuid（幂等，仅空时写）
- **⚠️ inboundType 语义差异**：OpenAPI 1仓配/2盘盈/3采购/4返配/5销退/6调拨 vs 控制台 1期初/2盘盈/3采购/4调拨/5退货/6其他/10加工 —— 方案B 单通道后无冲突
- **收货严格一致**：quickInbound/receiveItem/batchReceive 本地更新后**同事务**调 9.2.3（operator=storeName 兜底 openid，num=receivedQty，price=toPlainString 兜底 "0"，productCode 空直接 BusinessException）；企迈异常 → FeishuWebhookAlertService 异步告警（FeishuAlertContext 仿 GlobalExceptionHandler）+ BusinessException「同步企迈收货失败」→ **整体回滚**
- **实测企迈无自动入库**（9.2.2 近7天全量 5000+ 条）：类型1/3 待入库 879 vs 已入库 644；已入库的 92%（596/644）是创建 3 天后人工确认的（入库时间晚 3 天+），1 小时内即时收货仅 21 条（总仓库管确认）；待入库最老挂 8/8（有 7/26 采购单对应单据，快 3 周没人收）。**用户拍板：不加超时自动入库，纯手动确认**（企迈侧也是人工确认，本地跟随同步即可）；临时查询脚本 tmp_qmai_inbound_status.py 可复跑核数据
- **🎯 9.2.3 price 参数实测不生效**（tmp_qmai_price_unit_test.py，测试门店单写 12.34 回读仍 1200 分）——price 传什么值都无副作用，按必填传即可；**但调用后 status 1→2（确认收货生效）**，这正是要的行为。9.2.2 金额/单价单位为**分**（item inboundPrice=1800=18元）→ Java 端已加 `fenToYuan`（÷100 存元，与 H5 显示及控制台历史数据一致）；报货单匹配窗口放宽到 `max(days, 30)`（入库单 bizNo 常指向更早的采购申请单）
- **批量回填方案**（生产库 inbound 两表已被删，需重建+回填）：① 用户执行 `database/migration-add-inbound-order.sql`（DROP 重建）② `python tmp_inbound_backfill.py [入库天数=30] [报货天数=60]` 生成 `tmp_inbound_backfill.sql`（订单按 inbound_no 幂等 upsert、明细仅空单回填、金额分→元、类型仅1/3、四级匹配归属 + cangkuid 缺失回填）→ 用户在生产库执行 ③ 部署新后端后由凌晨3点定时+下拉刷新增量接管
- **warehouseNo 策略**：9.2.3 确认收货优先用**入库单自身的 warehouse_code**（收货仓库即订单仓库）→ 回落 store_info.cangkuid（JdbcTemplate 查询，即 OpenAPI 仓库编码）→ 回落 warehouseMark=warehouseId（控制台ID，实测同样有效）
- **新文件/改动**：`InboundSyncJob`（@Scheduled "0 0 3 * * ?" 遍历 qmaiStoreId 非空门店，ok/fail 计数）；`InventoryMpApplication` 加 `@EnableScheduling`（mp-server 启动器此前无定时）；`MpInboundController#sync` 去掉 warehouseId 校验（无仓库配置门店也能同步）、下拉刷新 days=2；`MpInboundServiceImpl` 整体重写（删 QmaiConsoleClient 依赖）；`QmaiClient` 新增 getInboundOrderList(9.2.2)/updateInboundOrder(9.2.3) + 4 个 DTO
- **编译验证**：`mvn -pl mp -am compile` 通过
- **待用户执行**：测试库 store_inventory 重建 inbound 表（原迁移脚本，早前被删）+ 打包部署 mp-server + 生产 upload/h5 缺 inbound 两页上线时补传
- 详见 [dateMemory/2026-08-15.md](dateMemory/2026-08-15.md)

### 2026-08-15 飞书内置浏览器媒体不显示排查 + poster 404 降级 + 存量视频 faststart

- **现象**：飞书桌面客户端内置浏览器打开审核页，图片/视频不显示；外部 Chrome 正常；图片直链在飞书聊天里点开正常；9:40 重新部署 server jar 后出现
- **排查**：nginx 无防盗链（valid_referers）、无 Content-Disposition → 排除；页面无 CSP/懒加载，媒体 CSS 新旧版一致 → 排除页面回归；服务器日志「客户端断开连接」（ClientAbortException，飞书 webview 掐断请求 ×3）未实锤因果，其后的 HttpMessageNotWritableException 堆栈是异常处理器二次报错、**噪音无害**
- **实锤问题**：新审核页视频 `poster=<原名>_thumb.jpg`，但生产 mp-server 仍是 8月10 旧 jar（generateThumbnail 8/11 ee58cc8 才提交）→ 线上所有视频无 _thumb.jpg → poster 全 404。修复：视频块 `onerror="this.removeAttribute('poster')"` 降级黑块+播放键（loss-daily-confirm.html，server jar 内随打包生效）
- **⚠️ 打包教训**：用户两次"打包上传"的 server jar 都不含本地改动（线上 curl grep removeAttribute=0）——打包代码副本与工作区不一致；打包前需在打包目录验证（Select-String 检查最新标记）；admin vite outDir=dist 不覆盖 server static（排除 npm build 清空嫌疑）
- **结果**：图片恢复（用户确认）；视频慢 = faststart 老问题——生产 mp-server 8月10 无 faststart 代码，8/13 compress-videos.sh 重编码的 34 个视频快、之后新上传的原片慢
- **faststart-all.sh 加日期截断**：用户拍板「视频处理13号之后的就可以了」+「不含8月13日」→ 新增 `SINCE_DATE`（默认 **2026-08-14**，env 可覆盖）+ find `-newermt`；8/13 及以前已被压缩脚本处理跳过。部署：**/data/inventory-upload/ 根目录**（不放 h5/，外网可下载且找不到 voucher），chmod +x 手动跑；MIN_AGE 60 分钟保护，一小时后重跑（幂等）
- **🎯 ffmpeg 失败根因实锤**（首轮脚本跑出 10 个失败日志）：`Unable to find a suitable output format for '...faststart.tmp'`——ffmpeg 靠输出扩展名推断容器格式，`.faststart.tmp` 推断不出 → 报错；**Java faststartRemux 同款坑**（此前测试环境"每次都失败"即此因，非磁盘/编解码）。修复：脚本与 Java 均加显式 `-f mp4/mov`；compress-videos.sh 一直成功是因为临时文件后缀正常。首轮统计：共66 跳过56（已 faststart）失败10（8/14 后新原片=点击慢那批，重跑即好）
- **🔍 第二轮：点视频"零请求"+ tile 去 video 化修复**：重跑脚本共66 重排10 跳过56 失败0 全部处理完，但用户实测仍慢 → 挂 nginx 日志点视频，**一条 .mp4/.mov 请求都没有**（只有 4 张 5-6MB jpeg 200）；线上 4026 页面与本地 diff=0（部署终于到位）；用户观察「把所有视频加载完才开始播放」→ 飞书 webview 无视 preload="none"（同 iOS 微信），进页面为每个 `<video>` 拉全部视频数据 → 修复：tile 内 `<video src poster>` 换成 `<img src=thumbOf onerror="this.remove()">`（视频字节只在点击弹窗时加载，与 loss-arrival.html 同模式）；**打包验证标记：`this.remove()` 计数=1**
- **🎯 视频慢的最终根因（本轮实锤）**：审核页 daily-summary 一次返回 **285 个媒体**（203 张 5-6MB 原图 ≈1GB + 82 个视频），页面一次性全量加载 → 服务器带宽仅 ~5Mbps（用户本机 curl 实测该视频 4.5s/637KB/s 下完，网络本身没问题）→ 点视频排在 1GB 图片后面，DevTools 瀑布显示 3.5 分钟里大部分在「资源计划队列 15.5s + 已停止」→ **与文件/faststart/飞书均无关**。修复：图片加 `loading="lazy"`（2 处）；**打包验证标记：`loading="lazy"`=2 + `this.remove()`=1**
- **治本方案（用户拍板"改"，已实现待打包）**：图片也做缩略图，一页 1GB → ~20MB——① MpUploadController 新增 `generateImageThumb`：上传时**异步线程**生成 `<原名>_thumb.jpg`（ffmpeg scale=320:-2 -frames:v 1 -q:v 5，autorotate 按 EXIF 转正；失败静默，页面 onerror 回退原图）；**异步不阻塞上传响应**，晚 10 点报损上传高峰零影响；uploadVoucher + completeChunkUpload 图片分支各挂一处（**打包验证：generateImageThumb=3**）② 审核页图片 tile 改用 `thumbOf(mediaUrl)` + `data-orig` 一次性回退原图（**打包验证：data-orig=3**）③ `upload/generate-image-thumbs.sh` 批量补生成存量图片（已有跳过/幂等、60 分钟保护、tmp+mv 防半截、nice 串行；部署 /data/inventory-upload/ 根目录，**凌晨 2 点跑**与晚 10 点错开，建议 crontab `0 2 * * *`）；原图 URL/文件不动，其他用图处（大图弹窗/ZIP 下载/Excel 导出/飞书卡片）不受影响。**首轮实跑全失败已修**：`_thumb.jpg.tmp` 后缀推断不出格式（.tmp 同款坑，第三次中招）→ 加 `-f image2`，重跑**成功 4125/4125**（存量图片含历史全部，缩略图已就绪）。**通用规则：服务器 ffmpeg 3.4.13 输出文件带未知后缀必须显式 -f**。后续反馈「点击图片弹窗慢」：原图 5-6MB≈9 秒白屏 → 弹窗改**渐进式**（先显缩略图秒出——用户要求不加模糊，「高清图加载中…」提示，原图后台加载完自动替换；新标记 **lb-tip=4 + 高清图加载中=1**）；物理等待不变，可选后续治本 _mid.jpg 中图
- **nginx 媒体缓存坑**：媒体 `Cache-Control: public, immutable` + faststart 原地重写文件 = 已缓存旧文件的客户端永远不发新请求（解释"点了没请求"）；处理：清飞书缓存（头像→设置→通用→清理缓存）+ nginx 媒体头改 `public, max-age=86400`（去 immutable）
- **牛油果泥审核页厂家单独一行**：备注是「厂家：蓝蛙牛油果 …」门店手打格式 → 页面卡片厂家行去掉 `!r.isAvocado` 条件（对所有报表显示）+ 备注渲染剥离「厂家：X」前缀防重复；厂家值用页面标准 tag() 药丸标签（新增 `.tag-supplier` 绿色类与 tag-ok 同色）；用户要求**补发卡片不展示厂家**（buildStoreGroups 仅补发卡片使用）→ 现只审核/确认页卡片厂家行 + 【】备注行两处显示；后端无需改（extractSupplier 早已解析 supplier 返回）；打包验证新增标记 **`indexOf('厂家`=2 + `'supplier')`=2 + `.tag-supplier`=1**
- **无厂家报表不再混入蓝蛙 ZIP**（用户反馈「之前没有选厂家的下载到蓝蛙牛油果泥的zip里」）：根因是「空 supplier→蓝蛙」兜底——到货登记页厂家选择写 `【蓝蛙牛油果】` 前缀而 extractSupplier 只识别 `厂家：X`，识别不到的全兜底成蓝蛙。修复：后端 extractSupplier 增加解析 `【(.+?)】` 前缀（hass牛油果不再错归蓝蛙）+ 4 处「蓝蛙」兜底改独立「无厂家」分组（supplierSet / 下载 Excel 厂家列 / exportAvocado 过滤 / ZIP 内文件夹）+ 页面 doDownload 分组兜底改「无厂家」（单独 ZIP）+ 备注渲染剥离「【supplier】」前缀 + 【】分支加 `!r.supplier` 防双标签；打包验证标记：页面 `'无厂家'`=1 + 页面「蓝蛙」=0，Java `无厂家/`=1 + `【(.+?)】`=1
- **待办**：server jar 重新打包部署（先验证 loading="lazy"=2 + this.remove()=1 + data-orig=3 + lb-tip=4 + 高清图加载中=1 + indexOf('厂家=2 + 'supplier')=2 + .tag-supplier=1 + 页面'无厂家'=1 + 页面蓝蛙=0 + Java 无厂家/=1 + Java 【(.+?)】=1，部署后 curl 4026 验证）；生产 mp-server 重新打包（faststart+缩略图+`-f` 修复+图片缩略图，低峰期，Select-String faststartRemux=3 + generateImageThumb=3）；generate-image-thumbs.sh crontab 0 2 * * * 兜底；faststart-all.sh 一小时后重跑（60 分钟保护内新上传，幂等）；compress-videos.sh crontab（30 1 * * *）未配
- 详见 [dateMemory/2026-08-15.md](dateMemory/2026-08-15.md)

### 2026-08-14 智能订货模块（P1 全栈上线：mp 后端 + 小程序）

- **核心链路**：每周一 6:00 定时（`SmartOrderGenerateJob`，也可 `POST /api/mp/smart-order/generate` 手动）按门店生成建议订货单 → 店长/老板小程序确认（数量可调，0=不订）→ 真实调用企迈创建报货单 → success（企迈单号）/ submit_failed（可重试）
- **建议量算法**：dailyUse = 库存差法 `(前次盘点 + 期间企迈报货单入库 − 本次盘点) ÷ 间隔天数`，不足两次盘点用 sys_config `smart_order_default_daily_use`(0.5)；`base = max(0, dailyUse×(cycleDays+safetyDays) − current)`；cycleDays/safetyDays 取 sys_config（7/3，**预留企迈源**，用户说后续从企迈拉）；单位修正走 `ConversionFactorUtil` BFS 换算链（有 unit 链 → CEILING 取整，否则 2 位小数）；只入 suggest>0 且 qm_code 非空的物料
- **⚠️ 数据源偏差（与计划不同）**：`store_zone_material` 是空表（全仓库无写入者，grep + git log 验证），物料池与当前库存都取**最近一次已提交任务的 task_material_summary**；周盘上线后只换 `loadCurrentInventory` 取数方法（代码留 ★ 替换缝注释）
- **状态机**：pending → syncing → success / submit_failed；确认两阶段（Phase A 短事务条件更新 `status IN(pending,submit_failed)`→syncing，Phase B 无事务调企迈，Phase C 短事务落终态，TransactionTemplate）；syncing 卡 5 分钟自动重置为 submit_failed 可重试
- **warehouseNo 策略**：直接复用 `store_info.cangkuid`（既有列，外部 API 同步自动填，**不加新列**——用户明确"企迈仓库id是cangkuid"）；确认时为空则自愈——从最近 60 天报货单读 storeWarehouseNo 回写 cangkuid；仍空 → BusinessException「门店尚未配置企迈仓库编码」；根目录 `tmp_smart_order_warehouse_no.sql` 是 cangkuid 核对脚本（多数门店无需运维操作）
- **⚠️ 前端形式（用户指令）**：智能订货两个页面用 **H5 + web-view**（参考入库管理），不做原生 uni-app 页——`upload/h5/smart-order-list.html`（概览面板+卡片，`?all=1` 跨店/单店两种模式）+ `smart-order-detail.html`（周概览+状态卡 4 态+步进器+确认），static/h5 有 jar 内副本；小程序入口（工具页查看类首项 + 首页待处理卡片）拼 webview URL 并传 `title` 参数动态改导航栏标题；H5 自包含原生 JS（API 推导/token URL 参数/Bearer 头/xhr 封装/pageshow 刷新/下拉刷新），部署改 upload/h5 文件传服务器即可无需打包
- **新文件**：`SmartOrder`/`SmartOrderItem`（entity/mapper）、`SmartOrderService`/`Impl`（~650 行）、`SmartOrderController`（员工 isStaffOnly 全部 403）、`SmartOrderGenerateJob`、`ConversionFactorUtil`（从 MpTransferController 抽取，原处改委托调用）、`QmaiClient.createDeclareOrder`（企迈 `/v3/newPattern/scmApiserver/post/declare/order/create`，HMAC-SHA1 复用 makeAuth/buildBody/doPost）
- **DB**：`V17__smart_order.sql`（smart_order + smart_order_item + 5 条 sys_config，UNIQUE(store_id, week_start_date) 幂等兜底，**无 store_info 改列**）；schema.sql 已同步。⚠️ **线上曾执行过旧版 V17（含 ALTER 加 qmai_warehouse_no 列）**，表/配置与最终版一致无需处理，仅需跑 `database/migration-drop-qmai-warehouse-no.sql` 删掉多余列（脚本内含迁数兜底 + 存在性核对）
- **权限**：店长/老板可用；list/overview 支持 `?all=true` 跨店（findStoresByOpenid），confirm 始终按单据所属门店校验，绝不使用上下文门店
- 详见 [dateMemory/2026-08-14.md](dateMemory/2026-08-14.md)

### 2026-08-14 审核页页面两边留白收窄（16px → 8px）

- 用户反馈截图：飞书里打开审核页两边留白太宽 → `.card/.rpt/.store-group/.tabs/.banner` 等 12 处 CSS 的横向 16px margin/padding 统一改 8px + 2 处 JS 模板内联样式（renderCard 卡片、导出链接行）
- 追加：补发卡片（renderResendCard 外层 card）左右外边距再收窄 8px → 4px → 最终贴边 0（margin:12px 0）
- 再追加：补发页各门店分组（.store-group，仅 buildStoreGroups 使用、只出现在补发卡片内）左右边距 8px → 4px → 最终贴边 0（margin:12px 0，用户「左右贴边」）；牛油果泥/其他类两张卡片同规则
- 最终：用户截图确认指的是「卡片自身的内边距」→ 补发外层卡片（renderResendCard）内边距先归 0，再依次微调 `2px → 4px`，现为 `padding:16px 4px`（左右 4px 微留边，上下保持原 16px）；牛油果泥统计卡片（buildAvoStatTable）padding:0 保持；门店分组（margin:12px 0）与统计表（.stat-body 左右 0）贴边不变
- 再追加：外层补发容器去掉 `card` 类（白底/边框/阴影），改为纯透明容器 `<div style="margin:12px 0;padding:16px 4px">`——消除"白卡片套白卡片"双层嵌套，页面只剩统计卡片+门店分组一层卡片
- 只动 loss-daily-confirm.html（server fat jar 内，随 server 打包部署生效）；node --check 通过

### 2026-08-14 其他类补发统计表样式重做

- 用户反馈「其他类补发H5表格统计太丑」→ 弃用 2 列 table（门店/物料顿号拼接、cell 无内边距贴边）
- 改版：表头加「N家门店 · N条物料」灰字统计；每门店一行 = 粗体门店名 + 物料 chips（浅绿底 #E7F4EB 绿字 #2F8F57 与审核页供应商标签同款，数量 `<b>×3包</b>` 加粗深色），flex-wrap 自动换行
- 新增 CSS：`.stat-body{padding:0 8px 12px}`（0 → 4px → 用户截图反馈「字和边框贴太近」→ 最终 8px）/ `.stat-row` / `.stat-store` / `.stat-chips` / `.stat-chip` / `.stat-chip b`；统计表表头内联 `padding:12px 8px`（只影响统计表，不动门店分组表头）；牛油果泥统计卡片同步 `padding:0 8px`；展开收起（toggleStatBody）与 tab 联动逻辑不变
- 牛油果泥统计表（avoStatTableRows）仍是原表格样式，未动；node --check 通过；server fat jar 内随打包生效

### 2026-08-14 视频ZIP下载缺文件修复（HTTP兜底双重编码 + 本地读取失败回退）

- **现象**：15:49 下载蓝蛙牛油果 ZIP（象子茶铺茶孟定店 8包），`3.jpg`、`4.mp4` 两个条目打包失败，但接口返回 200 → 下载的 ZIP 静默缺文件
- **根因1（日志铁证）**：HTTP 兜底 URL 双重编码——`rt.execute("http://localhost:30261" + ve.url)` 的 ve.url 已是 `%E8...` 编码，RestTemplate 再编码一次，请求行出现 `%25E8...`，mp 端 Tomcat 只解一次 → 找字面 `%E8...` 文件名 → 404；`remoteFileSize` HEAD 探测同病 → 该文件大小按 0 计 → 拆包计划 totalSize/分包划分失真
- **根因2（拆包重构回归）**：旧代码本地读取失败会继续 HTTP 兜底；新 `buildPartZip` 本地 copy 抛异常直接丢条目（3.jpg 即此情形）
- **修复（LossReportH5Controller）**：新增 `httpUrl(path)` 先 `URLDecoder.decode` 再拼（RestTemplate 会正确重新编码一次），remoteFileSize 和 buildPartZip 兜底统一走它；本地读取改开流验证 + 失败转 HTTP 兜底（putNextEntry 前开流，失败时同名重开条目，解压工具后条为准）；「下载视频失败」WARN 带上 `e.getMessage()`
- **运维**：server 端需重新打包；**部署后删除 server 的 `upload/zips/` 缓存**（今天的坏 ZIP 已按文件名缓存，不清会一直下到缺文件的旧包）
- 详见 [dateMemory/2026-08-14.md](dateMemory/2026-08-14.md)

### 2026-08-14 MpUploadController ffmpeg 防挂死加固（-nostdin）

- **根因**：服务器 ffmpeg 3.4.13 调试控制台会读 stdin（compress-videos.sh 曾因此死锁：吃掉 find 管道里下一个文件路径）；Java ProcessBuilder 子进程 stdin 是 JVM 开但永不写入的空管道，若被读 `waitFor()` 永久挂住、上传请求线程泄漏
- **修复**：`faststartRemux` / `generateThumbnail` / `transcodeToH264`（含其 ffprobe probe）4 个调用统一加 `-nostdin` 参数 + start 后 `getOutputStream().close()`（try/catch 忽略）；generateThumbnail 的 BufferedReader 排水顺手改 `transferTo(nullOutputStream())`（消除资源泄漏警告）
- 本地 `mvn -pl mp -am compile` 通过，需 mp-server 重新打包部署才生效
- **运维提醒**：视频压缩脚本已部署（/data/inventory-upload/compress-videos.sh，13 号起 34/34 压完），**crontab 还没配**，待加凌晨 1:30 定时任务

### 2026-08-14 视频点击播放慢修复（faststart 重排）

- **根因**：手机原片 moov 在文件尾，iOS 微信/浏览器点击播放必须拉完整文件才开播（边看边下载失效）；PC Chrome 会跳尾读 moov 所以不明显
- **后端**（MpUploadController）：新增 `faststartRemux(File)`，直传 uploadVoucher 和分片合并 completeChunkUpload 两个入口存盘后、生成缩略图前同步调用；`ffmpeg -y -v error -i src -c copy -movflags +faststart tmp` 成功才替换（不重新编码，几秒），仅 mp4/mov，失败保留原文件
- **存量视频**：用户拍板不处理（"过去的不用管了"）；`upload/faststart-all.sh` 已写好保留备用（幂等、ffprobe 校验转出流、清理残留 tmp），想处理时传服务器跑一次
- 部署：只需 mp-server 重新打包，新上传视频自动 faststart
- ⚠️ 8/14 实锤两套环境 mp-server 均为旧 jar（生产 87MB 视频 moov@87162881 尾、测试 162MB 视频 moov@169605227 尾），用户"点视频半天转不出来"根因；生产/测试上传目录分别为 `/data/inventory-upload` 与 `/home/StoreInventory/test/mq-server/upload`（同机 VM-0-3-centos）

### 2026-08-14 H5 视频首屏加载慢修复（poster 缩略图）

- **根因**：审核页（loss-daily-confirm.html）每张卡片都有 `<video>` 且没加 poster，iOS 微信 WKWebView 无视 `preload="none"`，首次进页面为每个视频拉数据 → 首屏很慢
- **修复**：卡片视频加 `poster="<原名>_thumb.jpg"`（上传时服务端已生成缩略图）+ 新增 `thumbOf()` 函数；审核页仅剩视频播放弹窗（videoPlayer）不需 poster
- **顺带**：到货登记页（upload/h5/loss-arrival.html）编辑已有报损单时旧视频无缩略图，预填时用 `thumbOf()` 指到服务端已生成的缩略图（新上传视频本来就有）
- 只改 H5 静态页，无后端/数据库改动；审核页在 server fat jar 内需重新打包，到货页在 upload/h5/ 直接传服务器

### 2026-08-14 牛油果泥审核：确认登记支持修改数量

- **需求**：确认报损登记时可修改数量，同时保留原始数量追溯
- **方案（小改动版，用户拍板）**：修改后数量直接**写回 `input_qty`**（`input_unit` 改为换算后单位"包"），下游补发/群日报/导出全部沿用原逻辑**零改动**；新增单列 `orig_qty` 记录修改前原始数量（按包，NULL=未修改）；迁移脚本 `database/migration-add-confirm-qty.sql`（需手动执行；若已执行过旧版 confirm_qty/confirm_unit 两列脚本需先 DROP 这两列）
- **后端**（LossReportH5Controller.confirmSingle）：接受 `confirmQty`，与原始一致则不记录；否则 UPDATE `input_qty=确认值, input_unit=包, orig_qty=原始换算值`，日志记 `数量修改：原始X包 → 确认Y包`；dailySummary 加 `r.orig_qty` 返回 `origQtyStr`（"48包"）供前端标注
- **H5**（loss-daily-confirm.html）：弹窗加「原始数量（只读）+ 确认数量（可改）」行，仅牛油果泥确认登记时显示；卡片 h3 红色标注 `（数量修改：原始X包）`；与原始一致不传 confirmQty；弹窗「数量修改」与「备注」分区块展示（各自浅底框+标题，备注块所有弹窗统一）
- 总部台账等下游全部读 input_qty，自动显示修改后数量，无需额外改动
- 详见 [dateMemory/2026-08-14.md](dateMemory/2026-08-14.md)

### 2026-08-12~14 H5 补发页重构 + 视频分包下载

**H5 补发页（loss-daily-confirm.html）**：
- **入口区分**：卡片 G 链接带 `materialId=牛油果泥ID` 只展示牛油果泥卡片（按门店合计包数分档「24包及以上/24包以下」，统计表 tab 与待补发列表联动，默认 24包及以上）；卡片 B2 链接不带 materialId 只展示其他类卡片；两个卡片标题已移除
- **多门店搜索**：空格/逗号/顿号/分号分隔，任一命中即显示；清空搜索恢复全部（含被搜索隐藏的物料条目）
- **批量补发**：单门店批量——全选+批量补发按钮在门店名一行右侧，只作用于本门店；牛油果泥卡片另有卡片级「全选（所有门店）」+批量补发，覆盖页面里所有门店（仅牛油果泥卡片有）；按钮文案 2026-08-14 从「确认补发/批量确认补发」简化为「补发/批量补发」（含弹窗「确定补发？」、alert「已补发 N 条」、审核页补发后状态「已补发」）
- **勾选统计**：门店「共X包」和卡片级「N家 · 共X包」只计算勾选中的条目，随勾选/全选/批量完成实时变化（初始 0）
- **搜索跨 tab 生效**：搜索词存 sessionStorage（`sgSearch_<date>`），tab 切换刷新后 `restoreSearch()` 自动恢复，待补发/已补发两个 tab 均被过滤
- **卡片跳过**：待审核/待补发为 0 时 B 系列卡片和卡片 E 不再发送（`LossReportDailySummaryJob`）

**视频下载 990MB 拆包（LossReportH5Controller）**：
- 新增 `GET /api/public/loss-report/download-videos-plan`：统计视频总大小并打 download 日志，返回分包信息（split/parts/base/names）
- `download-videos` 加 `part` 参数：`part>0` 下载指定分包（`-partNofM` 后缀）；`part=0` 保持旧单包行为
- 阈值 `ZIP_SPLIT_SIZE = 990MB`，贪心拆包（本地文件 length 计大小，HTTP 兜底 HEAD Content-Length）；分包 ZIP 同样缓存 `upload/zips/`
- H5 doDownload 先规划再按 3 秒间隔依次下载各分包
- 详见 [dateMemory/2026-08-14.md](dateMemory/2026-08-14.md)

### 2026-08-11 牛油果泥换算修复 + 自动收货 + 门店操作预警

- **牛油果泥单位换算修复**：`convertAvocadoQty`/`convertAvocadoUnit` 增加 unit 参数，仅当 `input_unit="件"` 时 ×24 改为"包"，已是"包"则不变
- **H5 统计表换算**：`avocadoStats` 查询后在 Java 层按 store_name 合并，件→包换算
- **自动收货**：新增 `LossReportAutoReceiveJob`（每天凌晨3点），`confirmed_resend` 超过4天→自动 `received`
- **门店操作预警**：新增 `WeeklyStoreWarningJob`，每周一8:00将上周无支出且无报损门店按督导分组发飞书通知（个人+群），当前 cron 已注释
  - 手动触发：`POST /api/public/loss-report/trigger-weekly-warning`
  - 依赖 `supervisor_store_access`（督导→open_id 映射）+ `sys_config.feishu_supervisor_group_chat_id`（督导群 chat_id）
- **督导-门店映射同步**：CSV → `store_info.supervisor_name`（166条 UPDATE）→ `supervisor_store_access` 重建
  - `admin_permission.name` 清除备注后缀（如"实习督导"），与 `store_info.supervisor_name` 纯名字对齐
  - 工具脚本：`tools/compare_supervisor_csv.py` → `database/compare_result.sql`
- **文档**：`CLAUDE.md` 新增"到货验收报损 · 飞书通知系统"完整章节（含 WeeklyStoreWarningJob）
- 详见 [dateMemory/2026-08-11.md](dateMemory/2026-08-11.md)

### 2026-08-03~08-10 飞书报损通知重构

**核心改动**：报损卡片从群聊改为按角色发个人，卡片按审核/补发维度拆分，群聊仅保留每日统计。

- **DB 变更**：
  - `V13__loss_notify_card_config.sql` — 旧表 `material_loss_notify_config` 替换为竖表 `loss_notify_card_config`，按 `category + card_type + feishu_user_id + status` 配置收件人
  - `V14__loss_report_log_attachment.sql` — `loss_report_log` 加 `attachment_url TEXT` 字段
  - `sys_config` 新增 `feishu_avocado_material_id`
- **新建工具类**：`common/.../FeishuMessageService.java` — 飞书 token、发送（`sendToUser`/`sendToChat`）、配置查询、分类分组。支持多人
- **重写 DailySummaryJob**：每日9:30发卡片给个人，卡片维度：

| 分类 | 卡片 | card_type | 颜色 | 频率 |
|------|------|-----------|------|------|
| 水果蔬菜 | A 审核 | pending | 黄 | 每天 |
| 其他类-外包装 | B1a 审核 | damage_audit | 黄 | 每天 |
| 其他类-非外包装 | B2a 审核 | other_audit | 蓝 | 每天 |
| 其他类-全部 | B2b 补发 | other_resend | 绿 | 每天 |
| 牛油果泥 | E 审核 | avocado_audit | 蓝 | 每天 |
| 全部 | F 群统计 | — | 蓝 | 每天 |

卡片标题区分审核/补发：`到货验收报损 · 审核 · XXX` / `到货验收报损 · 补发`。水果蔬菜不按原因拆分。加急卡片店长审批通过后才发。

- **H5 页面**（`loss-daily-confirm.html`）：
  - 审核 Tab + 补发 Tab（按门店分组折叠）+ 牛油果泥 3 Tab（未审核/已审核，未审核下含未下载/已下载子Tab）
  - 确认/拒绝弹窗：备注输入 + 附件上传（图片/视频，代理到 mp 端）
  - 视频下载 ZIP（按 report IDs，文件名含物料名+数量+日期）+ 同步下载 Excel
  - 牛油果泥统计表（按门店汇总）+ 导出 Excel（按 Tab 区分已下载/未下载）
  - 全选只勾可见物料、搜索支持门店/物料/企迈单号、已通过/已拒绝/已收货/未收到货标签
  - 页面可见时自动刷新、时间戳防缓存
- **小程序端**：详情页流程时间线展示操作附件（图片预览/视频播放）、列表展示最新进度
- **其他修复**：
  - `MaterialServiceImpl` 缓存名冲突、`InventoryReportController` SQL 分页、`MpTaskServiceImpl` 物理删除
  - `GlobalExceptionHandler` ClientAbortException→WARN、Admin 端视频支持
  - `application-prod.yml` 加 multipart 50MB + tomcat max-post 50MB
- **流程**：提交→待审核(pending)→审核确认(registered)→补发(confirmed_resend)→门店收货(received/not_received)

### 2026-07-28~08-01 盘点差异处理模块
- **计算引擎**：`DifferenceCalcService/Impl` — 8 数据源差异计算，时间窗口：上次 `submitted_at` → 本次 `submitted_at`
- **公式**：理论剩余 = 上月剩余 + 采购(PG) + 订货(PG) + 调货净值 + 还货净值 - 报损 + 自购 - 消耗(PG)
- **PG 数据源**：`stat_date` 按天查，NULL unit 自动补全，`PgDataSourceConfig` 双数据源
- **单位换算**：所有数据源统一 `convertToBaseUnit` 换算到盘点基础单位
- **零盘点物料**：`not_entered`/`zero_entered` 也参与计算
- **DB 变更**：`V9__diff_redesign.sql` — 重建 `inventory_difference`（8来源+计算），`task_material_summary` 加 `original_qty`/`adjusted_qty`，`transfer_order_item` 加 `material_id`，`loss_report` 加 `completed_at`，统一 collation
- **新增表**：`difference_modify_log` — 每次 `adjusted_qty` 修改日志；`difference_process_log` FK 改为 `ON DELETE SET NULL`
- **阈值**：`sys_config.diff_threshold_rate`，前端可调，自动重算 `is_large`
- **定时任务**：`DiffCalcJob` 每月1号10:00 串行计算，`calcLock` 防并发
- **API**：`GET diff-tasks` / `POST diff-tasks/{id}/calculate` / `POST diff-tasks/batch-calculate` / `PUT differences/{id}/adjust` / `GET|PUT diff-config`
- **前端**：`DifferenceList.vue`（任务级列表+筛选+阈值）+ `DifferenceDetail.vue`（精简6列+详情弹窗+录入明细+行内修改）
- **权限**：`AdminLoginInterceptor` JWT 角色改为实时查 DB，督导只看自己门店
- **分页修复**：`MyBatisPlusConfig` 加 `PaginationInnerInterceptor` 修复 total=0
- **盘点列表**：自动选中最近月份 + 指标卡片 + 统计报表 Tab（督导完成度柱状图）

### 2026-07-28
- **外部查询接口新增**：`InventoryReportController` 新增 2 个 `/api/reports` 端点（Bearer Token 鉴权）
  - `GET /api/reports/loss-report` — 到货报损单：`loss_type='arrival'` + status IN ('registered','confirmed_resend','received','not_received')，返回14字段
  - `GET /api/reports/transfer-order` — 调货单：`status='completed'`，每条 item 拆 2 行（调出_0/调入_1），返回12字段
- **新依赖注入**：`LossReportMapper`、`LossReportLogMapper`、`TransferOrderMapper`、`TransferOrderItemMapper`、`TransferReturnRecordMapper`
- **文档**：[docs/reports-api.md](../docs/reports-api.md)

### 2026-07-27
- **报损分类差异化**：`material_loss_notify_config` 分组→水果蔬菜类/其他类不同流程；新增 `registered`（已登记）状态
- **H5 确认页 Tab 布局**：水果蔬菜2Tab(待审核/已登记)、其他类3Tab(待审核/待发货/已发货)；拒绝必填原因
- **加急报损**：小程序新增"是否加急"；`loss_report.urgent` 字段；server 端 `send-urgent-card` 汇总发红色飞书卡片
- **月度发券**：`LossReportMonthlyVoucherJob` 每月3号10:00；`loss-monthly-voucher.html`
- **小程序6Tab**：全部/待审核/待收货/待确认/已登记/已拒绝
- **性能**：daily-summary SQL 限制近30天；批量查配置替代逐条查库
- **新 API**：`issue-voucher`、`batch-issue-voucher`、`monthly-registered`、`send-urgent-card`、`trigger-monthly-voucher`
- **新文件**：`LossReportMonthlyVoucherJob.java`、`loss-monthly-voucher.html`、`V8__loss_report_urgent.sql`
- **LossReport 实体**：新增 `urgent(Integer)`、`isFruitVeg(Boolean transient)`
- **LossReportServiceImpl**：新增 `resolveFruitVeg()` 批量分类判定；加急后调 server 发卡

### 2026-07-21~23
- **P2 督导拜访模块上线**：总部端台账+创建表单，小程序端确认/异议/任务执行
- **新表**：`supervisor_visit`、`supervisor_visit_action`、`supervisor_store_access`
- **新增 mp 文件**：`SupervisorVisit`、`SupervisorVisitAction`、`SupervisorStoreAccess`（Entity/Mapper）+ `SupervisorVisitService/Impl`（~650行）+ `MpSupervisorVisitController`
- **新增 server 文件**：`SupervisorVisitManageController`（12个端点）
- **权限**：`supervisor_store_access` 表 openId→门店映射，非 `headquarters_admin` 只看自己有权限的门店
- **状态机**：draft→pending_confirm→in_progress→completed（含 objection 异议分支）
- **逾期**：动态判断 tracking_time < 今天，逾期仍可提交，总端保留逾期痕迹
- **确认人**：`confirmManagerOpenid/Name` + `confirmOwnerOpenid/Name`，回填编辑
- **迁移 SQL**：`migration-add-supervisor-visit.sql`、`migration-add-supervisor-store-access.sql`、`migration-add-supervisor-visit-confirm-openid.sql`
- **统一 collation**：admin_permission、store_info 统一为 utf8mb4_0900_ai_ci
- **物料管理换算筛选**：换算类型下拉从"已维护/未维护"→"单位换算/称重换算"，后端 `matchConversionType` 新增 `unit`/`weight`
- **飞书到货 H5 卡片**：每条门店报损新增报损时间（`occurred_date`）；字段换行；未收到货反馈红底

### 2026-07-13
- **报损状态补全**：前端下拉/颜色/标签覆盖全部 8 种（`pending_approval`/`completed`/`received`/`not_received`）
- **调货台账列表**：去掉数量列，状态下拉精简为 3 种（待确认/已完成/已拒绝）
- **侧边栏恢复**：问题处理/物流信息/督导拜访重新显示

### 2026-07-11
- **调货台账 admin 端**：列对齐设计稿（去方向/数量/原因，新增物品/发起人/更新时间），详情抽屉去方向字段
- **发起人显示姓名**：`TransferOrder.createdByName` 瞬态字段，`pageAll()` 批量查 `employee` 表 `openid→name`
- **导出 Excel**：`TransferManageController.export()` Apache POI 生成 xlsx
- **Entity+SQL 注释补全**：`TransferOrder` 全部 22 字段、`TransferOrderItem` 全部 12 字段加中文注释
- **SQL 建表补全**：`transfer_order` 新增 `handoff`/`creator_store_id`，`transfer_order_item` 新增 `base_unit`/`base_qty`/`input_unit`/`input_qty`/`unit_price`
- **报损列表状态补全**：新增 `pending_approval`（待店长审批）
- **性能优化**：`store_info` 添加 `(del_flag, updated_at)` 复合索引，`getAllStores()` 全表扫描 508ms→个位数
- **菜单**：临时隐藏问题处理/物流信息/督导拜访（7/13 已恢复）

### 2026-07-10
- **调货物料搜索优化**：去掉 `.orderByAsc(sortNo)`，IN 查询 1315ms→几十ms
- **全部门店接口冲突修复**：`MpStoreController` 路径 `/api/mp/stores`→`/api/mp/stores-all`，不再和 `MpAuthController` 冲突
- **调货概览增加已拒绝**：`overview()` 新增 `rejected` 计数，排除列表移除 "rejected"
- **概览删除待交接**：`confirmed` 从概览统计中移除（自动确认后该状态不再出现）
- **创建人门店追踪**：`transfer_order` 新增 `creator_store_id`，创建时写入当前门店 ID
- **交接方式**：`transfer_order` 新增 `handoff` 字段（门店自取/第三方物流）
- **调货物料明细增强**：`transfer_order_item` 新增 `base_unit`/`base_qty`/`input_unit`/`input_qty`/`unit_price`
- **单位换算优化**：物料搜索仅返回 unit 类型单位（不含称重），返回 `unitInfos`（含换算提示"1箱=24个"）和 `unitPrices`
- **报损多单位换算**：到货验收支持 `inputUnit`→`baseUnit` 换算（`computeConversionFactor`）
- **容器别名**：`container_config` 新增 `alias` 字段

### 2026-07-09
- **P0 调货管理模块上线**：店间直调 7 状态流转 + HQ 只读台账 + Excel 导出
- **调货 API**：`MpTransferController`（CRUD + 全门店查询 + 物料搜索 + 概览统计）、`TransferManageController`（总部台账 + 导出）
- **调货物料搜索**：按 `inventory_units` 返回可选单位，仅 unit 类型换算（不含称重），自动换算单价（`unitPrices`），返回换算提示（`unitInfos`，1箱=24个）
- **新表**：`transfer_order`（含 `creator_store_id`、`handoff`、`total_qty`）、`transfer_order_item`（含 `base_unit`、`base_qty`、`input_unit`、`input_qty`、`unit_price`）
- **确认自动发货**：调出方确认后自动 `confirmed→pending_ship`，写入发货人/时间
- **任务列表支持全门店**：`MpTaskController.list(?all=true)` 跨名下所有门店查询
- **人员列表全门店**：`MpStaffController.list(?all=true)` + `listAllStoresStaff()`
- **报损/调货全门店统计**：`overviewByStores(openid)` 聚合名下各门店待处理数
- **门店切换同步**：工具页/首页切换门店时调用 `switchStore` 同步后端 session

### 2026-07-11/13
- **过期任务移入历史**：`MpTaskServiceImpl.list()` 过期未提交任务从 `current` 移入 `history`，`DeadlineGuardAspect` 继续拦截过期写操作
- **员工审批待处理事项**：新增 `GET /api/mp/staff/applications/overview?all=true`，`MpStaffService.overviewByStores()`，首页待处理事项增加员工审批卡片
- **调货待处理仅保留关键状态**：`overviewByStores` 和 `overview()` 仅统计 `pending_receive`（确认收货）+ `pending_ship`（确认可调出），移除 `pending_confirm`/`confirmed`
- **报损待处理仅保留关键状态**：`overviewByStores` 仅统计 `pending_approval`（待审核）+ `confirmed_resend`（待补发），移除 `pending`
- **门店搜索接口公开**：`MpWebMvcConfig` 排除 `/api/mp/stores-all`，登录前/切换本地环境时不再 403

### 2026-07-10/11
- **报损字段重命名**：`loss_report` 表 `unit`→`input_unit`、`loss_qty`→`input_qty`，新增 `base_unit`/`base_qty`/`qimai_order_no`/`reject_reason`
- **到货多单位换算**：`MaterialConversionRule` BFS换算链，录入 `input_unit`+`input_qty` → 自动计算 `base_qty`
- **报损审批流程**：店员提交→`pending_approval`→店长通过/拒绝；日常直接 `completed`，到货发飞书
- **飞书每日汇总**：`LossReportDailySummaryJob` 每天9:30汇总前一天pending到货报损，按 `material_loss_notify_config` 分类发卡片
- **飞书 H5 确认页**：`LossReportH5Controller` + `loss-daily-confirm.html`（每日确认页），`LossReportPageController`（server端H5页面）
- **操作日志**：`loss_report_log` 表，所有状态变更记录（submit/approve/confirm/reject/receive/not_receive）
- **新状态**：`completed`（已录入）、`received`（已收货）、`not_received`（未收到货）
- **新表**：`loss_report_log`、`material_loss_notify_config`、`sys_config`
- **角色判定优化**：`determineRole()` 直接查 employee.role，移除 owner_registration 依赖
- **mp 飞书代码清理**：删除 `FeishuMessageService/Impl`、`FeishuSummaryService/Impl`、`LossReportEventListener`等，飞书逻辑全移 server

### 2026-07-13
- **调货概览修复**：`overview()` `pendingConfirm` 字段名修正（原拼写错误 `pendingReceive`），补充 `pending_confirm` 状态计数
- **全门店汇总去重**：新增 `overviewTotal()` 单次 SQL 聚合全门店（`fromStoreId IN(...) OR toStoreId IN(...)`），避免逐门店累加导致双倍计数
- **首页待处理过滤**：`overviewByStores()` 按操作方过滤 — 待确认只统计 fromStore，待收货只统计 toStore
- **报损可见标记**：`material` 新增 `loss_visible`，白名单物料默认展示，搜关键词时查全表
- **物料搜索优化**：搜索框初始折叠，点击展开，关键词下方显示结果，假滚动条提示可滑动
- **报损单位+审批**：`loss_report` 字段重命名(`unit`→`input_unit`)，新增 `base_unit`/`base_qty`/`qimai_order_no`/`reject_reason`，到货多单位换算
- **报损操作日志**：`loss_report_log` 表，所有状态变更记录
- **飞书通知体系**：`sys_config`+`material_loss_notify_config` 表，`LossReportDailySummaryJob` 每日汇总
- **新状态**：`completed`（已录入）、`received`（已收货）、`not_received`（未收到货）、`pending_approval`

### 2026-07-10/11
- **到货弹窗**：标题"到货验收报损"，橙色副标题"仅限企迈平台发货的到货验收报损"
- **H5页面**：桌面端40%宽度居中，移动端100%/430px；状态标签区分已确认(绿)/已拒绝(红)
- **飞书卡片**：applink包一层强制内置浏览器打开，颜色按分类（水果黄/其他蓝），去除明细条
- **企迈单号**：到货必填，卡片和H5均展示
- **P0 门店报损模块上线**：小程序端列表+新建+详情，总部端报损台账+详情抽屉
- **报损 API**：`MpLossReportController`（CRUD+物料搜索+容器列表）、`LossReportManageController`（总部台账+导出）
- **报损管理增强**：总部端筛选新增门店（`mendianmingcheng`）+日期范围（`a-range-picker`），详情抽屉优化（报损信息卡片+计算公式+备注+附件），导出 Excel（Apache POI 5.2.5，`fetch`+Blob 下载）
- **物料换算双区域**：规则换算（unit）和称重换算（weight）分区展示，排序箭头，`weightUnit` 写入 `inventory_units`
- **角色权限**：`LoginUser` 新增 `role` 字段，JWT 携带 role，`determineRole()` 老板>店长>员工
- **扫码盘点**：`MpInventoryController` 扫码识别物料 + 条码补充申请
- **差异处理**：`DifferenceService` 差异生成+阈值过滤+状态机（pending→adjusted/closed/converted）
- **Admin 侧边栏重构**：分组结构（门店运营/协同流转/经营支撑/系统）
- **BizCodeUtil 改为随机数**
- **P0 新表**：`loss_report`、`container_config`、`barcode_supplement`、`inventory_difference` 等 11 张

### 2026-07-06/07
- **自购食材物料采购**：小程序支出登记新增自购食材类型，物料搜索按父级分类过滤，仅存 `self_purchase_material` 表
- **API 新增**：`GET /api/mp/materials/by-category`（物料分类搜索+缓存）、`GET /api/mp/expenses/{id}/material`（物料明细查询）
- **自购物料表变更**：新增 `store_miniapp_no`、`material_id`、`purchase_date`、`handler_name`、`voucher_url`、`remark`，删除 `received_qty`
- **Admin 侧边栏固定**：`App.vue` 中 `a-layout-sider` 设为 `position: fixed`，内容滚动不受影响

## 🗂️ 记忆索引

| 文件 | 读我当需要... |
|------|--------------|
| [decisions.md](decisions.md) | 🧠 理解为什么这样设计（快照隔离、多模块、多单位换算…） |
| [patterns.md](patterns.md) | 📐 写代码时查命名约定、分层规范、JSON 格式 |
| [feedback.md](feedback.md) | 📝 了解用户纠正过什么、有哪些特殊偏好 |
| [contacts.md](contacts.md) | 📇 找模块关键类、外部系统接口、数据库连接信息 |
| [testing.md](testing.md) | 🧪 写测试时查命名规范、怎么 mock、边界值怎么测 |
| `YYYY-MM-DD.md` | 📅 回顾近期讨论和临时结论（30 天自动衰减） |

## 🧭 模块速查

```
common (31)  →  R<T> / 异常 / 缓存 / 日志AOP / ThreadLocal上下文
template (25) → 物料CRUD + 盘点规则 + 模板分区
task (22)    → 月盘任务 + 门店 + 快照同步 + 事件监听
expense (23) → 支出类型CRUD + 支出记录 + 自购成本 + 统计看板
people (15)  → 员工 + 老板绑定 + 门店联系人
server (21)  → 飞书登录 / JWT / 多角色权限 / 启动器 :4026
mp (81)      → 小程序全部API（13个控制器）
mp-server (6)→ 小程序启动器 :30261
```

依赖链：`mp-server → mp → task → template → common`

## ⚙️ 关键设计模式

- **快照隔离**：任务创建时复制模板数据，模板变更不影响已有任务
- **事件驱动同步**：模板/物料变更 → Spring Event → 异步重建未开始任务快照
- **多单位换算**：`material_conversion_rule` 表支持 unit（箱→瓶）和 weight（kg→g）
- **逻辑删除 + 乐观锁**：所有核心表 `del_flag` + `version`
- **ThreadLocal 上下文**：`AdminContextHolder`（总部）/ `UserContextHolder`（小程序）

## 🔗 相关文档

- 项目上下文：[CLAUDE.md](../CLAUDE.md)
- 数据库：[schema.sql](../../database/schema.sql) / [CHANGELOG.md](../../database/CHANGELOG.md)
- 总部前端：[admin/](../../admin/)
- 小程序前端：[miniapp/](../../miniapp/)

## 2026-07-14/15 更新

### 支出类型排序
- `expense_type` 表加 `sort_no INT DEFAULT NULL`，越小越靠前，NULL 排最后
- 小程序端 `MpExpenseServiceImpl.listTypes()`：`ORDER BY sort_no IS NULL, sort_no ASC, id ASC`
- 总部端 `ExpenseTypeServiceImpl.list()`：同上
- 新建类型自动 `sortNo = MAX(sort_no) + 1`
- SQL：`database/migrate-expense-type-add-sort.sql`

## 2026-07-17 更新

### 物料表索引优化
- material 表加 `(del_flag, material_name)` 联合索引
- SQL：`database/migration-material-add-name-index.sql`
- 慢查询 `ORDER BY material_name LIMIT 100` 从 ~670ms 降到 <50ms

### 问题处理模块上线
- mp 模块新增 `MpIssueController` + `IssueCallbackController`（~16个端点）
- server 模块新增 `IssueManageController`（总部只读台账）
- 状态直接使用 task_platform 原始值

### 调货还货/还钱模块
- 新表 `transfer_return_record`，`TransferReturnService/Impl`，`MpTransferReturnController`
- 归还状态用 `handoff='returned'`(不改 `status`)
- `doReturn`: 还货校验数量上限,还钱金额不限
- `confirmReturn`: 仅调出门店可确认
- 详情接口返回 `returnRecords`

### 问题处理日志
- 点"上报新问题"即记录"上报问题"(`POST /issue/report-click`)
- Server 端补 xiangmu 配置+图片 URL 前缀处理

### 支出统计看板优化
- `ExpenseController.dashboard()` 支持 `startDate`/`endDate` 参数
- `ExpenseDashboardResp.StoreRanking` 新增 `count`/`pctAmount`/`pctCount`
- 新增 `buildMonthlyTrend()` 按月汇总
- `buildStoreRanking` 去 `.limit(8)`，返回全部门店

### Issue 实体精简 + 回复回写（2026-07-22）
- **删除 Issue 实体字段**（只写不读）：`contactName`、`contactPhone`、`images`、`processResult`、`acceptedBy`、`acceptedAt`、`submittedBy`、`processedAt`、`resolvedAt`
- **字段映射**：`replyText` 通过 `@TableField("acceptance_remark")` 映射到列 `acceptance_remark`（列名不变，无 SQL 迁移，注释改为"解决原因"）
- `IssueCreateReq` 同步精简，去 `images`/`contactName`/`contactPhone`
- `replyExternal()` 成功后将 `replyText` 回写到 `issue.replyText`（即 `acceptance_remark` 列）
- 同步修复 `P0ModuleTests.java`
