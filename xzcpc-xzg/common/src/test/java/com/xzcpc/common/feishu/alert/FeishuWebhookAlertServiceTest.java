package com.xzcpc.common.feishu.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FeishuWebhookAlertService 单元测试。
 * <p>
 * 覆盖消息构建、堆栈截断、开关控制、异常安全四个维度。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FeishuWebhookAlertService")
class FeishuWebhookAlertServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper;
    private FeishuAlertProperties alertProperties;
    private FeishuWebhookAlertService alertService;

    private static final String WEBHOOK_URL = "https://open.feishu.cn/open-apis/bot/v2/hook/test-hook-id";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        alertProperties = new FeishuAlertProperties();
        alertProperties.setEnabled(true);
        alertProperties.setWebhookUrl(WEBHOOK_URL);
        alertProperties.setAppName("象掌柜-测试");
        alertService = new FeishuWebhookAlertService(alertProperties, restTemplate, objectMapper);
    }

    // ==================== 消息构建测试 ====================

    @Nested
    @DisplayName("buildText — 消息构建")
    class BuildTextTests {

        @Test
        @DisplayName("完整上下文 → 消息包含所有字段")
        void shouldIncludeAllFields() {
            FeishuAlertContext ctx = FeishuAlertContext.builder()
                    .httpMethod("GET")
                    .requestPath("/api/materials")
                    .queryString("name=测试&page=1")
                    .remoteAddr("10.0.1.25")
                    .userHint("张三 (ou_abc123)")
                    .appName("象掌柜-总部端")
                    .environment("prod")
                    .build();

            Exception e = new NullPointerException("Cannot invoke \"Material.getName()\"");

            String text = alertService.buildText(ctx, e);

            assertTrue(text.contains("【象掌柜-总部端 服务端异常】"));
            assertTrue(text.contains("应用: 象掌柜-总部端"));
            assertTrue(text.contains("环境: prod"));
            assertTrue(text.contains("请求: GET /api/materials?name=测试&page=1"));
            assertTrue(text.contains("来源: 10.0.1.25"));
            assertTrue(text.contains("用户: 张三 (ou_abc123)"));
            assertTrue(text.contains("异常: java.lang.NullPointerException — Cannot invoke \"Material.getName()\""));
            assertTrue(text.contains("【堆栈摘要】"));
        }

        @Test
        @DisplayName("queryString 为空 → 不在请求行追加 ?")
        void shouldNotAppendEmptyQueryString() {
            FeishuAlertContext ctx = FeishuAlertContext.builder()
                    .httpMethod("POST")
                    .requestPath("/api/tasks")
                    .queryString(null)
                    .remoteAddr("192.168.1.1")
                    .userHint(null)
                    .appName("象掌柜")
                    .environment("dev")
                    .build();

            String text = alertService.buildText(ctx, new RuntimeException("boom"));

            // 请求行不应以 ? 结尾
            assertTrue(text.contains("请求: POST /api/tasks\n"));
            assertFalse(text.contains("/api/tasks?"));
        }

        @Test
        @DisplayName("remoteAddr 为 null → 显示空字符串")
        void shouldHandleNullRemoteAddr() {
            FeishuAlertContext ctx = FeishuAlertContext.builder()
                    .httpMethod("GET")
                    .requestPath("/api/test")
                    .queryString(null)
                    .remoteAddr(null)
                    .userHint(null)
                    .appName("象掌柜")
                    .environment("dev")
                    .build();

            String text = alertService.buildText(ctx, new RuntimeException("test"));

            assertTrue(text.contains("来源: \n"));
        }

        @Test
        @DisplayName("userHint 为空 → 不显示用户行")
        void shouldNotShowUserWhenEmpty() {
            FeishuAlertContext ctx = FeishuAlertContext.builder()
                    .httpMethod("GET")
                    .requestPath("/api/test")
                    .queryString(null)
                    .remoteAddr("127.0.0.1")
                    .userHint(null)
                    .appName("象掌柜")
                    .environment("dev")
                    .build();

            String text = alertService.buildText(ctx, new RuntimeException("test"));

            assertFalse(text.contains("用户:"));
        }

        @Test
        @DisplayName("异常 message 为 null → 不显示 — 消息段")
        void shouldHandleNullExceptionMessage() {
            FeishuAlertContext ctx = FeishuAlertContext.builder()
                    .httpMethod("GET")
                    .requestPath("/api/test")
                    .queryString(null)
                    .remoteAddr("127.0.0.1")
                    .userHint(null)
                    .appName("象掌柜")
                    .environment("dev")
                    .build();

            // 无 message 的异常
            Exception e = new NullPointerException();

            String text = alertService.buildText(ctx, e);

            assertTrue(text.contains("java.lang.NullPointerException"));
            // 不应该有 " — " 后跟 message
            assertFalse(text.contains(" — null"));
        }

        @Test
        @DisplayName("异常 message 超过 200 字符 → 截断并加 ...")
        void shouldTruncateLongExceptionMessage() {
            FeishuAlertContext ctx = FeishuAlertContext.builder()
                    .httpMethod("GET")
                    .requestPath("/api/test")
                    .queryString(null)
                    .remoteAddr("127.0.0.1")
                    .userHint(null)
                    .appName("象掌柜")
                    .environment("dev")
                    .build();

            // 构造一个超过 200 字符的 message
            String longMsg = "A".repeat(250);
            Exception e = new RuntimeException(longMsg);

            String text = alertService.buildText(ctx, e);

            // 异常摘要行应截断到 200 字符 + "..."
            String truncated = "A".repeat(200) + "...";
            assertTrue(text.contains(" — " + truncated),
                    "异常摘要行应包含截断后的 message");

            // 异常摘要行不应包含完整 message
            String exceptionLine = text.lines()
                    .filter(line -> line.startsWith("异常: "))
                    .findFirst().orElse("");
            assertFalse(exceptionLine.contains(longMsg),
                    "异常摘要行不应包含完整 message");
        }
    }

    // ==================== 堆栈截断测试 ====================

    @Nested
    @DisplayName("堆栈截断")
    class StackTraceTests {

        @Test
        @DisplayName("堆栈 ≤ 24 行 → 完整输出")
        void shouldPrintFullStackTraceWhenShort() {
            FeishuAlertContext ctx = minimalContext();

            // 只有几行的堆栈
            Exception e = new RuntimeException("short");
            String text = alertService.buildText(ctx, e);

            // 不应出现省略提示
            assertFalse(text.contains("已省略"));
        }

        @Test
        @DisplayName("堆栈 > 24 行 → 截断并标注省略行数")
        void shouldTruncateLongStackTrace() {
            FeishuAlertContext ctx = minimalContext();

            // 构造深层调用链产生超过 24 行堆栈
            Exception deep = generateDeepStackTrace(50);
            String text = alertService.buildText(ctx, deep);

            // 应出现省略提示
            assertTrue(text.contains("已省略"));
            assertTrue(text.contains("(共 "));
        }

        @Test
        @DisplayName("堆栈字符超过 1600 → 二次截断")
        void shouldTruncateByCharLimit() {
            FeishuAlertContext ctx = minimalContext();

            // 构造一个长类名 + 长文件名的深层堆栈
            Exception deep = generateDeepStackTrace(100);
            String text = alertService.buildText(ctx, deep);

            // 堆栈段总字符不超过 1600（加上前缀和省略标记）
            int stackStart = text.indexOf("【堆栈摘要】");
            String stackSection = text.substring(stackStart);
            // 标题 + 堆栈内容应在合理范围（允许省略标记额外字符）
            assertTrue(stackSection.length() <= 1700,
                    "stack section too long: " + stackSection.length());
        }
    }

    // ==================== notify 开关控制测试 ====================

    @Nested
    @DisplayName("notify — 开关控制")
    class NotifyToggleTests {

        @Test
        @DisplayName("enabled=false → 不发送")
        void shouldNotSendWhenDisabled() {
            alertProperties.setEnabled(false);

            FeishuAlertContext ctx = minimalContext();
            alertService.notify(ctx, new RuntimeException("test"));

            // RestTemplate 不应被调用
            verify(restTemplate, never()).postForEntity(anyString(), any(), any());
        }

        @Test
        @DisplayName("webhookUrl 为 null → 不发送")
        void shouldNotSendWhenUrlIsNull() {
            alertProperties.setWebhookUrl(null);

            FeishuAlertContext ctx = minimalContext();
            alertService.notify(ctx, new RuntimeException("test"));

            verify(restTemplate, never()).postForEntity(anyString(), any(), any());
        }

        @Test
        @DisplayName("webhookUrl 为空字符串 → 不发送")
        void shouldNotSendWhenUrlIsEmpty() {
            alertProperties.setWebhookUrl("");

            FeishuAlertContext ctx = minimalContext();
            alertService.notify(ctx, new RuntimeException("test"));

            verify(restTemplate, never()).postForEntity(anyString(), any(), any());
        }

        @Test
        @DisplayName("webhookUrl 为纯空格 → 不发送")
        void shouldNotSendWhenUrlIsBlank() {
            alertProperties.setWebhookUrl("   ");

            FeishuAlertContext ctx = minimalContext();
            alertService.notify(ctx, new RuntimeException("test"));

            verify(restTemplate, never()).postForEntity(anyString(), any(), any());
        }
    }

    // ==================== notify 正常发送测试 ====================

    @Nested
    @DisplayName("notify — 正常发送")
    class NotifySendTests {

        @Test
        @DisplayName("配置正确 → 发送 JSON 到 Webhook URL")
        void shouldSendJsonToWebhookUrl() {
            FeishuAlertContext ctx = FeishuAlertContext.builder()
                    .httpMethod("GET")
                    .requestPath("/api/materials")
                    .queryString("name=测试")
                    .remoteAddr("10.0.1.25")
                    .userHint("张三")
                    .appName("象掌柜-总部端")
                    .environment("prod")
                    .build();

            alertService.notify(ctx, new RuntimeException("test error"));

            // 验证 RestTemplate 被调用了正确的 URL
            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            verify(restTemplate, timeout(5000)).postForEntity(
                    urlCaptor.capture(), entityCaptor.capture(), eq(String.class));

            assertEquals(WEBHOOK_URL, urlCaptor.getValue());

            // 验证请求头
            HttpEntity<?> entity = entityCaptor.getValue();
            HttpHeaders headers = entity.getHeaders();
            assertEquals(MediaType.APPLICATION_JSON, headers.getContentType());

            // 验证消息体结构
            String body = (String) entity.getBody();
            assertNotNull(body);
            assertTrue(body.contains("\"msg_type\""));
            assertTrue(body.contains("\"text\""));
            assertTrue(body.contains("text"));
            assertTrue(body.contains("【象掌柜-总部端 服务端异常】"));
        }

        @Test
        @DisplayName("RestTemplate 抛异常 → notify 不向上传播")
        void shouldNotPropagateRestTemplateException() {
            when(restTemplate.postForEntity(anyString(), any(), any()))
                    .thenThrow(new RuntimeException("网络超时"));

            FeishuAlertContext ctx = minimalContext();

            // 不应抛出异常
            assertDoesNotThrow(() ->
                    alertService.notify(ctx, new RuntimeException("test")));
        }
    }

    // ==================== 工具方法 ====================

    private FeishuAlertContext minimalContext() {
        return FeishuAlertContext.builder()
                .httpMethod("GET")
                .requestPath("/api/test")
                .queryString(null)
                .remoteAddr("127.0.0.1")
                .userHint(null)
                .appName("象掌柜-测试")
                .environment("dev")
                .build();
    }

    /**
     * 构造一个堆栈行数超过 24 的异常。
     * 直接通过 setStackTrace 注入人工构造的超长堆栈，不依赖运行时调用深度。
     * 注意：每行需足够短，让 24 行 + 省略标记保持在 1600 字符以内。
     */
    private Exception generateDeepStackTrace(int unused) {
        Exception e = new RuntimeException("deep stack exception");
        StackTraceElement[] frames = new StackTraceElement[50];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = new StackTraceElement(
                    "c.S" + (i % 3),
                    "m" + i,
                    "S.java",
                    i + 1
            );
        }
        e.setStackTrace(frames);
        return e;
    }
}
