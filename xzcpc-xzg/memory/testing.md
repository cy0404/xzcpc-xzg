---
updated: 2026-07-03
importance: 4
---

# 项目测试规范

> 当前状态：仅 1 个测试文件（ApiSignUtilTest）。以下规范面向**从零搭建测试体系**。

## 一、测试分层

```
        ┌──────────┐
        │  E2E     │  ← 暂不涉及（需要完整环境）
        ├──────────┤
        │  集成测试  │  ← @SpringBootTest + 真实 DB 或 Testcontainers
        ├──────────┤
        │  API 测试  │  ← @WebMvcTest + MockMvc
        ├──────────┤
        │  单元测试  │  ← 纯 JUnit 5 + Mockito（主力，占比 70%）
        └──────────┘
```

| 层级 | 测什么 | 框架 | 占比目标 |
|------|--------|------|----------|
| **单元测试** | Service 逻辑、Util 工具类、换算引擎 | JUnit 5 + Mockito | 70% |
| **API 测试** | Controller 参数校验、返回格式、权限拦截 | @WebMvcTest + MockMvc | 20% |
| **集成测试** | Mapper + SQL、事件监听、事务回滚 | @SpringBootTest + H2 | 10% |

---

## 二、命名规范

### 测试类命名

```
被测类名 + Test
  ✅ ApiSignUtilTest
  ✅ TaskServiceTest
  ✅ MaterialControllerTest
  ❌ TestTaskService
```

### 测试方法命名

采用 **should_预期行为_when_条件** 模式：

```java
@Test
void shouldReturnSortedParams_whenMapHasMultipleKeys() { ... }

@Test
void shouldThrowBusinessException_whenMaterialNotFound() { ... }

@Test
void shouldReturnEmptyList_whenStoreHasNoTasks() { ... }
```

中文团队也可以用中文 DisplayName 辅助：

```java
@Test
@DisplayName("物料不存在时抛出 BusinessException")
void shouldThrowBusinessException_whenMaterialNotFound() { ... }
```

### 测试文件位置

```
src/test/java/com/xzcpc/{模块}/
├── util/
│   └── ApiSignUtilTest.java       ← 纯单元测试
├── service/
│   └── TaskServiceTest.java       ← Mockito 单元测试
├── controller/
│   └── MaterialControllerTest.java ← @WebMvcTest
└── integration/
    └── TaskMapperTest.java        ← @SpringBootTest + H2
```

---

## 三、测试结构：AAA 模式

每个测试方法遵循 **Arrange → Act → Assert**：

```java
@Test
@DisplayName("跨分区汇总同物料数量")
void shouldSumSameMaterialAcrossZones_whenMultipleZonesHaveSameMaterial() {
    // ===== Arrange（准备数据）=====
    List<TaskZoneMaterial> materials = Arrays.asList(
        buildZoneMaterial("MAT001", "鸡胸肉", new BigDecimal("5")),
        buildZoneMaterial("MAT001", "鸡胸肉", new BigDecimal("3"))
    );

    // ===== Act（执行）=====
    List<TaskMaterialSummary> result = taskService.summarizeByMaterial(materials);

    // ===== Assert（验证）=====
    assertEquals(1, result.size());
    assertEquals(new BigDecimal("8"), result.get(0).getTotalQty());
    assertEquals("MAT001", result.get(0).getMaterialId());
}
```

### 断言优先使用 AssertJ

```java
// ❌ JUnit 原生断言（信息不够清晰）
assertEquals(3, list.size());

// ✅ AssertJ 流式断言（可读性更强 + 失败信息更明确）
assertThat(list).hasSize(3);
assertThat(result.getMaterialName()).isEqualTo("鸡胸肉");
assertThatThrownBy(() -> service.save(null))
    .isInstanceOf(BusinessException.class)
    .hasMessageContaining("物料不存在");
```

---

## 四、边界值测试

边界值是 bug 高发区，每个涉及数字、字符串、集合的方法都应覆盖。

### 4.1 数字边界

以**多单位换算**（`material_conversion_rule`）为例：

| 边界类型 | 测试值 | 预期 |
|----------|--------|------|
| 零值 | `from_quantity = 0` | 抛出异常或返回 0 |
| 负数 | `from_quantity = -1` | 抛出 BusinessException |
| 极大值 | `from_quantity = 99999999.9999` | 正常换算，不溢出 |
| 精度边界 | `from_quantity = 0.0001`（4位小数） | 正常换算 |
| 超精度 | `from_quantity = 0.00001`（5位小数） | 按规则截断或报错 |
| 单位换算 | 1 箱 = 12 瓶 → 输入 0.5 箱 | 返回 6 瓶 |

```java
@Test
@DisplayName("数量为零时抛出参数异常")
void shouldThrowException_whenQuantityIsZero() {
    ConversionRule rule = new ConversionRule();
    rule.setFromQuantity(new BigDecimal("0"));
    rule.setFromUnit("箱");
    rule.setToQuantity(new BigDecimal("12"));
    rule.setToUnit("瓶");

    assertThatThrownBy(() -> conversionService.convert(rule, BigDecimal.ZERO))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("数量必须大于0");
}
```

### 4.2 集合边界

| 边界类型 | 输入 | 预期 |
|----------|------|------|
| 空集合 | `Collections.emptyList()` | 返回空列表，不抛 NPE |
| 单元素 | `List.of(oneItem)` | 正常处理 |
| 重复元素 | 同一 material_id 出现 3 次 | 正确汇总 |
| null | `null` | 抛出异常或返回空（取决于方法约定） |

### 4.3 字符串边界

| 边界类型 | 输入 | 预期 |
|----------|------|------|
| 空字符串 | `""` | 参数校验失败 |
| 超长字符串 | `name = 201 字符`（字段限制 200） | 截断或报错 |
| 特殊字符 | `name = "测试<script>alert(1)</script>"` | 正常存储或转义 |
| SQL 注入 | `name = "'; DROP TABLE task; --"` | MyBatis-Plus 参数化查询自动防注入 |

### 4.4 状态机边界

以**任务状态**（`not_started → in_progress → submitted`）为例：

| 边界类型 | 测试场景 | 预期 |
|----------|----------|------|
| 合法转换 | not_started → in_progress | 成功 |
| 合法转换 | in_progress → submitted | 成功 |
| 非法转换 | submitted → in_progress | 抛出异常 |
| 非法转换 | not_started → submitted（跳过） | 抛出异常 |
| 终态修改 | submitted 状态尝试修改录入数据 | 抛出异常 |

---

## 五、Mock 规范

### 5.1 单元测试 Mock Service 依赖

```java
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private TemplateService templateService;

    @InjectMocks
    private TaskServiceImpl taskService;

    @Test
    void shouldCreateTaskWithSnapshot_whenTemplateIsValid() {
        // Arrange
        Template template = buildTemplate();
        when(templateService.getById(1L)).thenReturn(template);

        // Act
        taskService.createTask(buildCreateRequest());

        // Assert
        verify(taskMapper).insert(any(Task.class));
    }
}
```

### 5.2 API 测试 Mock Service 层

```java
@WebMvcTest(MaterialController.class)
class MaterialControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MaterialService materialService;

    @Test
    @DisplayName("分页查询返回 R.ok 格式")
    void shouldReturnOk_whenQueryPage() throws Exception {
        when(materialService.page(any()))
            .thenReturn(new Page<Material>());

        mockMvc.perform(get("/api/materials?page=1&size=10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }
}
```

### 5.3 集成测试不 Mock Service

集成测试用 `@SpringBootTest` + H2，**不 Mock Service**，让真实 SQL 执行：

```java
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class TaskMapperTest {

    @Autowired
    private TaskMapper taskMapper;

    @Test
    void shouldInsertAndQueryTask() {
        Task task = new Task();
        task.setTaskName("测试任务");
        task.setStoreId("STORE001");
        taskMapper.insert(task);

        Task found = taskMapper.selectById(task.getId());
        assertThat(found).isNotNull();
        assertThat(found.getTaskName()).isEqualTo("测试任务");
    }
}
```

---

## 六、测试基础设施搭建清单

当前缺失，建议按优先级逐步添加：

### 🔴 P0 — 立即（不依赖外部资源）

1. **测试 Profile**：在各模块 `src/test/resources/` 下创建 `application-test.yml`
   ```yaml
   spring:
     profiles: test
     datasource:
       url: jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=true
       driver-class-name: org.h2.Driver
   ```

2. **H2 依赖**：父 POM 添加
   ```xml
   <dependency>
       <groupId>com.h2database</groupId>
       <artifactId>h2</artifactId>
       <scope>test</scope>
   </dependency>
   ```

### 🟡 P1 — 短期（推进测试覆盖）

3. **JaCoCo 覆盖率插件**，阈值暂设 30% 然后逐步提高
4. **Mockito 静态方法支持**：`mockito-inline`（如需 mock `AdminContextHolder` 等静态工具类）
5. **MyBatis-Plus 测试工具**：`mybatis-plus-boot-starter-test` 内置测试自动配置

### 🟢 P2 — 中长期（测试更真实环境）

6. **Testcontainers**：用 Docker 拉真实 MySQL 做集成测试（替代 H2，避免 SQL 方言差异）
7. **前端测试**：admin 端加 `vitest`，miniapp 端配置 `@dcloudio/uni-automator`

---

## 七、测试检查清单

新增或修改代码时，检查以下测试是否覆盖：

| 检查项 | 说明 |
|--------|------|
| ☐ 正常路径 | Happy path：一切正常时返回正确结果 |
| ☐ 空值输入 | null / 空字符串 / 空集合 不会 NPE |
| ☐ 边界值 | 0、负数、极大值、精度边界 |
| ☐ 异常路径 | 依赖服务异常时传播或降级 |
| ☐ 状态机 | 合法转换通过、非法转换抛异常 |
| ☐ 并发 | version 乐观锁冲突时重试或报错（核心业务） |
| ☐ 权限 | 无权限用户访问时返回 403 |

---

## 八、运行测试命令

```bash
# 全量测试
cd xzcpc-xzg && mvn test

# 单模块测试
cd xzcpc-xzg && mvn test -pl common

# 单测试类
mvn test -pl common -Dtest=ApiSignUtilTest

# 跳过测试（打包用）
mvn clean package -DskipTests
```
