package org.example.notification.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.notification.config.NotificationConfig;
import org.example.notification.model.NotificationMessage;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 飞书渠道实现（自定义机器人 Webhook 版）
 * 今日快速验证，明日可替换为自建应用 SDK（支持卡片+按钮）
 */
@Slf4j
@Component
public class FeishuChannelImpl implements NotificationChannel {

    private static final String CHANNEL_TYPE = "FEISHU";

    private final NotificationConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public FeishuChannelImpl(NotificationConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String getType() {
        return CHANNEL_TYPE;
    }

    @Override
    public boolean supportsInteractive() {
        // Webhook 版不支持卡片按钮，明日换 SDK 后返回 true
        return false;
    }

    @Override
    public boolean send(NotificationMessage msg) {
        String webhookUrl = config.getFeishu().getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("[feishu] Webhook URL not configured, skipping send");
            return false;
        }

        if (!config.getFeishu().isEnabled()) {
            log.warn("[feishu] Channel disabled, skipping send");
            return false;
        }

        try {
            // 构建飞书自定义机器人消息格式
            Map<String, Object> payload = buildRobotPayload(msg);

            String jsonBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("[feishu] Message sent successfully: title={}, target={}", msg.getTitle(), msg.getTarget());
                return true;
            } else {
                log.error("[feishu] Send failed: status={}, body={}", response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("[feishu] Send error: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 构建飞书自定义机器人消息 JSON
     * 支持 Markdown 格式
     */
    private Map<String, Object> buildRobotPayload(NotificationMessage msg) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("msg_type", "markdown");

        Map<String, Object> content = new HashMap<>();
        StringBuilder markdown = new StringBuilder();
        if (msg.getTitle() != null && !msg.getTitle().isBlank()) {
            markdown.append("### ").append(msg.getTitle()).append("\n\n");
        }
        if (msg.getContent() != null) {
            markdown.append(msg.getContent());
        }
        if (msg.getTraceId() != null && !msg.getTraceId().isBlank()) {
            markdown.append("\n\n> traceId: `").append(msg.getTraceId()).append("`");
        }
        content.put("content", markdown.toString());
        payload.put("content", content);

        return payload;
    }
}
