# 盘点工具 1.0 计划文档索引

本目录用于把项目计划拆成可逐个交给 AI 编码的模块文档，减少一次性上下文过大导致的幻觉和越界实现。

推荐使用方式：

```text
请只根据 plan/03-master-data-module.md 实现主数据同步与查询模块，不要实现其他模块。
```

## 文档列表

```text
00-overview.md                  # 项目总览、技术栈、业务闭环
01-database-design.md           # 数据库表结构、字段、索引、状态枚举
02-backend-architecture.md       # Spring Boot 分层、包结构、通用返回、异常处理
03-master-data-module.md         # 物料和门店主数据同步与查询模块
04-template-module.md            # 盘点模板、分区、模板物料模块
05-task-snapshot-module.md       # 创建月盘任务、生成任务快照
06-store-input-module.md         # 小程序任务查询、分区录入、保存
07-submit-summary-module.md      # 提交事务、汇总、默认清单回写
08-admin-web-plan.md             # Vue 3 管理端页面与接口对接
09-miniprogram-plan.md           # 微信小程序页面与接口对接
10-test-acceptance.md            # 测试用例、验收数据、关键场景
implementation-plan.md           # 原始完整实施总计划
```

## AI 编码约束

- 每次只实现一个模块文档对应的范围。
- 不要自行扩展未要求的功能。
- 涉及表结构时，以 `01-database-design.md` 为准。
- 涉及后端分层和返回格式时，以 `02-backend-architecture.md` 为准。
- 涉及物料和门店时，以 `03-master-data-module.md` 为准，盘点工具只引用基础数据库管理系统的主数据。
- 涉及提交、汇总和默认清单回写时，以 `07-submit-summary-module.md` 为准。
- 每次编码后必须说明修改文件、实现范围、运行方式、测试方式和未完成项。
