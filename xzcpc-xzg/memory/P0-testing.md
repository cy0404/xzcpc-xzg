---
updated: 2026-07-07
importance: 4
---

# P0 测试文档

> 配合 [P0-plan.md](P0-plan.md) 使用。覆盖 A1盘点 / A2报损 / A3调货 / A4物流 / B1问题 / B2通知 六个模块。

---

## 一、测试策略

```
单元测试 (70%)          集成测试 (20%)          E2E (10%)
────────────────        ────────────           ─────────
Service 业务逻辑         Mapper + SQL          小程序核心流程
工具类（换算/去皮）      事件监听 + 通知         总部端差异处理闭环
状态机流转               Controller + MockMvc  报损全流程
权限校验                 企迈/象目经理 Mock      调货全流程
```

### P0 测试重点

| 优先级 | 模块 | 重点测试 |
|--------|------|---------|
| 🔴 最高 | A1 差异处理 | 差异生成算法、状态机、并发 |
| 🔴 最高 | A2 半成品去皮 | 净重计算、负数校验、精度 |
| 🔴 最高 | A3 调货状态机 | 7 状态流转、非法转换拦截 |
| 🟡 高 | A1 盲盘 | DTO 字段白名单、无账面泄露 |
| 🟡 高 | B1 问题上报 | 必填校验、象目经理同步 |
| 🟢 中 | A1 称重换算 | 整包+散装+皮重组合计算 |
| 🟢 中 | B2 企微通知 | 事件触发、异步发送、失败重试 |

---

## 二、A1 盘点优化测试

### 2.1 盲盘保障

```yaml
test_blind_inventory:
  - name: "分区物料列表不返回账面库存"
    given: "task_zone_material 表有 input_qty 和 base_qty 两个数量字段"
    when: "调用 GET /api/mp/inventory/task/{taskId}/zone/{zoneId}/materials"
    then:
      - "返回 JSON 中不包含 book_qty、reference_qty、diff_qty 字段"
      - "返回 JSON 中不包含任何含 'book'、'reference'、'diff' 关键字的字段"

  - name: "提交后汇总页不展示差异"
    when: "调用 GET /api/mp/inventory/task/{taskId}/summary"
    then:
      - "返回 JSON 中不包含 diff_qty、book_qty 字段"
      - "只返回 total_qty（跨分区汇总）和 zone_count"
```

### 2.2 扫码盘点

| 测试场景 | 输入 | 预期 |
|----------|------|------|
| 条码匹配成功 | `barcode = "6901234567890"`（存在于 material.qr_code） | 返回物料名称、规格、单位、所属分区 |
| 条码对应物料不在当前分区 | 物料在 A 分区，当前在 B 分区扫码 | 返回物料信息 + `"not_in_current_zone": true`，底部面板显示"加入当前分区" |
| 条码未识别 | barcode 不在库中 | 返回 404 + 底部面板"未识别"，可提交条码补充申请 |
| 条码已盘过 | 当前分区已录入该物料 | 返回物料信息 + `"already_entered": true`，底部显示已盘数量和修改入口 |
| 空条码 | `barcode = ""` | 400 参数校验失败 |
| 条码格式异常 | `barcode = "<script>"` | 400，不执行 SQL 注入 |

### 2.3 称重换算

```java
@Test
@DisplayName("整包+散装称重+去皮 → 基础单位数量")
void shouldCalculateBaseQtyFromPackageAndWeight() {
    // 物料: 鸡胸肉, 基础单位=瓶, 每包=24瓶, 单重=50g/瓶
    // 输入: 整包=2, 散装总重=300g, 容器=大容器(120g)
    // 净重 = 300 - 120 = 180g
    // 散装换算 = 180 / 50 = 3.6 → 四舍五入 = 4瓶
    // 总计 = 2 × 24 + 4 = 52瓶
    WeighRequest req = new WeighRequest();
    req.setPackages(2);
    req.setGrossWeight(new BigDecimal("300"));
    req.setContainerId(1L);  // 大容器 120g

    BigDecimal result = weighService.calculate(req, material);

    assertThat(result).isEqualByComparingTo(new BigDecimal("52"));
}
```

边界值：

| 场景 | 整包 | 总重 | 容器重 | 预期 |
|------|------|------|--------|------|
| 净重为0 | 0 | 120 | 120 | 总计=0 |
| 净重为负 | 0 | 100 | 120 | 抛出异常 "净重不能为负" |
| 纯整包 | 3 | 0 | 0 | 3×24=72 |
| 纯散装 | 0 | 500 | 0 | 500/50=10 |
| 极大整包 | 99999 | 0 | 0 | 99999×24=2399976 |
| 精度边界 | 0 | 1 | 0 | 0（四舍五入） |
| 精度边界 | 0 | 25 | 0 | 1（四舍五入） |

### 2.4 差异处理

```java
@Test
@DisplayName("差异=实盘-账面，正数为盘盈，负数为盘亏")
void shouldCalculateDifference_whenActualGreaterThanBook() {
    // 账面 100，实盘 105 → 盘盈 5
    BigDecimal book = new BigDecimal("100");
    BigDecimal actual = new BigDecimal("105");

    InventoryDifference diff = diffService.calculate(book, actual);

    assertThat(diff.getDiffQty()).isEqualByComparingTo(new BigDecimal("5"));
    assertThat(diff.getDiffAmount()) // 差异金额 = 5 × 单价
        .isEqualByComparingTo(new BigDecimal("25.00")); // 单价5元
}

@Test
@DisplayName("差异低于阈值不生成差异项")
void shouldNotGenerateDiff_whenBelowThreshold() {
    // 阈值配置：差异率 < 1% 或 差异数量 < 2
    BigDecimal book = new BigDecimal("1000");
    BigDecimal actual = new BigDecimal("1005"); // 差异5，0.5%

    List<InventoryDifference> diffs = diffService.generateDiffs(taskId, threshold);

    assertThat(diffs).isEmpty();
}
```

状态机测试：

| 当前状态 | 操作 | 预期下一状态 |
|----------|------|-------------|
| pending | 督导开始处理 | processing |
| processing | 确认调整 | adjusted |
| processing | 标记无需调整 | closed |
| processing | 转问题单 | converted |
| adjusted | 任何操作 | 拒绝（终态） |
| closed | 任何操作 | 拒绝（终态） |
| converted | 任何操作 | 拒绝（终态） |

### 2.5 任务状态扩展

| 当前状态 | 触发条件 | 预期下一状态 |
|----------|---------|-------------|
| in_progress | 所有分区 100% 录入 | pending_submit |
| pending_submit | 店长提交 | submitted |
| in_progress | 超过 deadline | overdue |
| pending_submit | 超过 deadline | overdue |
| overdue | 总部延长时间 | in_progress |

---

## 三、A2 门店报损测试

### 3.1 半成品去皮

```java
@Test
@DisplayName("净重 = 含容器重量 - 容器重量")
void shouldCalculateNetWeight() {
    LossReportRequest req = new LossReportRequest();
    req.setLossType("daily");
    req.setLossObject("semi_finished");
    req.setGrossWeight(new BigDecimal("500"));  // 含容器
    req.setContainerId(1L);                     // 大容器 120g

    LossReport report = lossService.create(req);

    assertThat(report.getContainerWeight()).isEqualByComparingTo(new BigDecimal("120"));
    assertThat(report.getNetWeight()).isEqualByComparingTo(new BigDecimal("380"));
}

@Test
@DisplayName("净重≤0时不允许提交")
void shouldReject_whenNetWeightNegative() {
    req.setGrossWeight(new BigDecimal("100"));
    req.setContainerId(1L); // 容器 120g → 净重 -20

    assertThatThrownBy(() -> lossService.create(req))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("净重不能为负");
}
```

### 3.2 报损权限

| 角色 | 操作 | 预期 |
|------|------|------|
| store_manager | 新建报损 | ✅ 200 |
| owner（单店态） | 新建报损 | ✅ 200（继承店长能力） |
| staff | 新建报损 | ❌ 403 "仅店长可登记报损" |
| 未登录 | 新建报损 | ❌ 401 |

### 3.3 到货验收报损状态机

| 当前状态 | 触发 | 预期 |
|----------|------|------|
| 门店提交 | — | pending（待厂家确认） |
| pending | 厂家确认补发 | confirmed_resend |
| pending | 厂家拒绝 | rejected |
| pending/confirmed_resend/rejected | 手动关闭 | closed |

---

## 四、A3 调货管理测试

### 4.1 状态机测试（核心）

```
                    ┌─→ cancelled（发起方取消）
                    │
pending_confirm ────┤
                    │
                    └─→ rejected（对方拒绝）
                    │
                    └─→ confirmed ──→ pending_ship ──→ pending_receive ──→ completed
```

```java
@ParameterizedTest
@CsvSource({
    "pending_confirm, confirm,  confirmed",
    "pending_confirm, cancel,    cancelled",
    "pending_confirm, reject,    rejected",
    "confirmed,       ship,      pending_ship",
    "pending_ship,    receive,   pending_receive",
    "pending_receive, complete,  completed",
})
void shouldTransitionState(String from, String action, String expectedTo) {
    TransferOrder order = buildOrder(from);
    
    TransferOrder result = transferService.execute(order.getId(), action);
    
    assertThat(result.getStatus()).isEqualTo(expectedTo);
}
```

非法转换拦截：

| 当前状态 | 非法操作 | 预期 |
|----------|---------|------|
| completed | confirm | ❌ "已完成订单不可操作" |
| cancelled | ship | ❌ "已取消订单不可操作" |
| pending_confirm | receive | ❌ "请先确认调货" |
| pending_ship | complete | ❌ "请先确认收货" |

### 4.2 业务校验

| 场景 | 预期 |
|------|------|
| 调出门店=调入门店 | ❌ "不能向同一门店调货" |
| 调货数量=0 | ❌ "数量必须大于0" |
| 调货数量为负数 | ❌ "数量必须大于0" |
| 物品名称为空 | ❌ "物品名称不能为空" |
| 非本店操作（staff 操作其他门店） | ❌ 403 |

---

## 五、A4 物流信息测试

### 5.1 状态枚举

| 状态 | 展示 |
|------|------|
| pending_shipment | 待发货 |
| in_transit | 运输中 |
| delivering | 派送中 |
| signed | 已签收 |
| abnormal | 异常 |
| query_failed | 查询失败 |

### 5.2 边界值

| 场景 | 输入 | 预期 |
|------|------|------|
| 运单号为空 | `tracking_no = ""` | ❌ 400 |
| 运单号重复 | 同一运单号录入两次 | ❌ 409 "运单号已存在" |
| 第三方查询失败 | API 返回异常 | 状态自动置 `query_failed` |
| 无物流轨迹 | 刚录入暂无轨迹 | 轨迹列表为空，状态 `pending_shipment` |

---

## 六、B1 问题处理测试

### 6.1 必填校验

| 字段 | 空值 | 超长 |
|------|------|------|
| title | ❌ "标题不能为空" | >200字符截断或报错 |
| issue_type | ❌ "请选择问题类型" | — |
| urgency | ❌ "请选择紧急程度" | — |
| description | ❌ "请填写问题描述" | >2000字符截断或报错 |
| contact_name | ❌ "联系人不能为空" | — |
| contact_phone | ❌ "联系电话不能为空" | 格式校验 |
| images | ✅ 可选 | — |

### 6.2 权限

| 角色 | 提交问题 | 预期 |
|------|---------|------|
| store_manager | ✅ | 200 |
| owner | ✅ | 200 |
| staff | ❌ | 403 "仅店长可提交问题" |

### 6.3 象目经理同步

```java
@Test
@DisplayName("问题提交后异步同步到象目经理")
void shouldSyncToXiangmuAfterSubmit() {
    Issue issue = issueService.submit(request);

    // 异步验证
    await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
        verify(xiangmuSyncService).syncIssue(issue.getId());
    });
}

@Test
@DisplayName("象目经理同步失败不影响问题保存")
void shouldSaveIssueEvenWhenSyncFails() {
    doThrow(new RuntimeException("网络异常")).when(xiangmuSyncService).syncIssue(any());

    Issue issue = issueService.submit(request);

    assertThat(issue.getId()).isNotNull();
    assertThat(issue.getSyncStatus()).isEqualTo("failed");
}
```

---

## 七、B2 企微通知测试

### 7.1 事件触发矩阵

| 事件 | 触发时机 | 通知对象 | 通知文案包含 |
|------|---------|---------|------------|
| 问题处理中 | issue.status → processing | 提交门店的负责人 | 问题标题 + 状态变更 |
| 问题已解决 | issue.status → resolved | 提交门店的负责人 | 问题标题 + 处理结果 |
| 物流异常 | logistics.status → abnormal | 关联门店负责人 | 运单号 + 异常说明 |
| 调货待确认 | transfer.status → pending_confirm | 对方门店负责人 | 调出门店 + 物品 + 数量 |
| 调货待收货 | transfer.status → pending_receive | 调入门店负责人 | 调出门店 + 物流信息 |
| 订货待确认 | 每周推送订货单 | 门店负责人 | 订货周期 + 待确认项数 |

### 7.2 通知可靠性

```java
@Test
@DisplayName("通知发送失败记录日志并重试")
void shouldLogAndRetry_whenSendFails() {
    when(weComApi.send(any())).thenThrow(new RuntimeException("网络超时"));

    notificationService.send(buildEvent());

    NotificationLog log = notificationLogMapper.selectOne(/* 最新一条 */);
    assertThat(log.getStatus()).isEqualTo(2);  // 失败
    assertThat(log.getFailReason()).contains("网络超时");

    // 重试3次
    verify(weComApi, times(3)).send(any());
}

@Test
@DisplayName("同一事件不重复发送")
void shouldNotDuplicateNotification() {
    notificationService.send(event);
    notificationService.send(event); // 同一事件再次触发

    verify(weComApi, times(1)).send(any()); // 只发送一次
}
```

---

## 八、P0 测试检查清单

上线前每条必须通过：

| # | 检查项 | 所属 |
|---|--------|------|
| ☐ | 小程序端盘点物料列表不返回账面库存 | A1 |
| ☐ | 净重为负时称重提交被拦截 | A1 |
| ☐ | 差异项正负计算正确（实盘-账面） | A1 |
| ☐ | 差异低于阈值不生成差异项 | A1 |
| ☐ | 差异终态（adjusted/closed/converted）不可再操作 | A1 |
| ☐ | 半成品去皮净重 ≤ 0 时提交被拦截 | A2 |
| ☐ | staff 角色报损返回 403 | A2 |
| ☐ | 调货7种状态的正向流转全部通过 | A3 |
| ☐ | 调货非法状态转换全部被拦截 | A3 |
| ☐ | 同一门店不可向自己调货 | A3 |
| ☐ | 运单号重复录入返回 409 | A4 |
| ☐ | 问题必填字段为空时返回校验错误 | B1 |
| ☐ | 象目经理同步失败不影响问题保存 | B1 |
| ☐ | 企微通知异步发送不阻塞主流程 | B2 |
| ☐ | 企微通知不重复发送 | B2 |
| ☐ | LoginUser.role 在登录时正确写入 | 分权 |
| ☐ | JWT token 中携带 role 字段 | 分权 |

---

## 九、测试基础设施补充

P0 开发前需要先补齐（继承 [testing.md](testing.md) 的清单）：

| 项 | 状态 | 说明 |
|----|------|------|
| H2 依赖 | 🔴 必须 | 集成测试用 `MODE=MySQL` 模拟 |
| `application-test.yml` | 🔴 必须 | 测试专用数据源配置 |
| Mockito `@MockBean` | ✅ 已有 | spring-boot-starter-test 包含 |
| 企迈 API Mock | 🟡 需要 | WireMock 或 `@MockBean` |
| 象目经理 API Mock | 🟡 需要 | `@MockBean` |
| 企微 API Mock | 🟡 需要 | `@MockBean` |
| JaCoCo | 🟢 建议 | 初始阈值 30%，逐步提高 |
