package org.example.notification.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import com.lark.oapi.service.im.v1.model.*;
import lombok.extern.slf4j.Slf4j;
import org.example.notification.config.NotificationConfig;
import org.example.notification.model.MessageFormat;
import org.example.notification.model.NotificationMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 飞书渠道实现（oapi-sdk 版）
 * 支持普通消息 + 交互卡片 + 按钮回调
 * 使用自建应用认证（推荐生产环境）
 */
@Slf4j
@Component
public class FeishuChannelImpl implements NotificationChannel {

    private static final String CHANNEL_TYPE = "FEISHU";

    private final NotificationConfig config;
    private final Client client;

    public FeishuChannelImpl(@Autowired NotificationConfig config) {
        this.config = config;

        // 初始化飞书 SDK Client（自建应用方式）
        String appId = config.getFeishu().getAppId();
        String appSecret = config.getFeishu().getAppSecret();

        if (appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank()) {
            // 使用自建应用认证（完整支持卡片交互）
            this.client = Client.newBuilder(appId, appSecret)
                    .requestTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
            log.info("[feishu] SDK client initialized with appId={}", appId);
        } else {
            // Fallback: 无自建应用时使用 Webhook（但不支持卡片）
            this.client = null;
            log.warn("[feishu] No appId/appSecret configured, falling back to webhook mode (no card support)");
        }
    }

    @Override
    public String getType() {
        return CHANNEL_TYPE;
    }

    @Override
    public boolean supportsInteractive() {
        // 自建应用 SDK 支持交互卡片
        return client != null;
    }

    @Override
    public boolean send(NotificationMessage msg) {
        if (!config.getFeishu().isEnabled()) {
            log.warn("[feishu] Channel disabled, skipping send");
            return false;
        }

        // 优先使用 SDK，fallback 到 Webhook
        if (client != null) {
            return sendViaSdk(msg);
        } else {
            return sendViaWebhook(msg);
        }
    }

    /**
     * 通过 SDK 发送（支持交互卡片）
     */
    private boolean sendViaSdk(NotificationMessage msg) {
        try {
            // 获取 targetId（从 extra 中获取，由 NotificationService 注入）
            String targetId = getTargetId(msg);
            String receiveIdType = getReceiveIdType(msg);

            if (targetId == null || targetId.isBlank()) {
                log.warn("[feishu] No targetId found for SDK send, falling back to webhook");
                return sendViaWebhook(msg);
            }

            // 根据 format 选择发送方式
            if (msg.getFormat() == MessageFormat.CARD && msg.isInteractive()) {
                return sendInteractiveCard(msg, targetId, receiveIdType);
            } else {
                return sendTextMessage(msg, targetId, receiveIdType);
            }
        } catch (Exception e) {
            log.error("[feishu] SDK send error: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 发送文本消息
     */
    private boolean sendTextMessage(NotificationMessage msg, String targetId, String receiveIdType) {
        try {
            StringBuilder content = new StringBuilder();
            if (msg.getTitle() != null && !msg.getTitle().isBlank()) {
                content.append(msg.getTitle()).append("\n\n");
            }
            if (msg.getContent() != null) {
                content.append(msg.getContent());
            }
            if (msg.getTraceId() != null && !msg.getTraceId().isBlank()) {
                content.append("\n\n> traceId: `").append(msg.getTraceId()).append("`");
            }

            CreateMessageReq req = CreateMessageReq.newBuilder()
                    .receiveIdType(receiveIdType)
                    .createMessageReqBody(CreateMessageReqBody.newBuilder()
                            .receiveId(targetId)
                            .msgType("text")
                            .content(String.format("{\"text\":\"%s\"}", escapeJson(content.toString())))
                            .build())
                    .build();

            CreateMessageResp resp = client.im().message().create(req);

            if (resp.success()) {
                log.info("[feishu] SDK text message sent: title={}, targetId={}", msg.getTitle(), targetId);
                return true;
            } else {
                log.error("[feishu] SDK send failed: code={}, msg={}", resp.getCode(), resp.getMsg());
                return false;
            }
        } catch (Exception e) {
            log.error("[feishu] SDK text send error: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 发送交互卡片消息
     * 飞书交互卡片使用 post 消息类型，content 为卡片 JSON
     */
    private boolean sendInteractiveCard(NotificationMessage msg, String targetId, String receiveIdType) {
        try {
            String cardContent = buildInteractiveCard(msg);

            CreateMessageReq req = CreateMessageReq.newBuilder()
                    .receiveIdType(receiveIdType)
                    .createMessageReqBody(CreateMessageReqBody.newBuilder()
                            .receiveId(targetId)
                            .msgType("interactive")
                            .content(cardContent)
                            .build())
                    .build();

            CreateMessageResp resp = client.im().message().create(req);

            if (resp.success()) {
                log.info("[feishu] SDK interactive card sent: title={}, targetId={}", msg.getTitle(), targetId);
                return true;
            } else {
                log.error("[feishu] SDK card send failed: code={}, msg={}", resp.getCode(), resp.getMsg());
                return false;
            }
        } catch (Exception e) {
            log.error("[feishu] SDK card send error: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 构建交互卡片 JSON
     * 包含标题、内容、traceId、以及"确认执行"按钮
     */
    private String buildInteractiveCard(NotificationMessage msg) {
        Map<String, Object> card = new HashMap<>();
        card.put("schema", "2.0");
        card.put("tag", "card");

        Map<String, Object> header = new HashMap<>();
        header.put("title", Map.of("tag", "plain_text", "content", msg.getTitle() != null ? msg.getTitle() : "告警通知"));
        header.put("template", "red");
        card.put("header", header);

        Map<String, Object> elements = new HashMap<>();
        elements.put("tag", "div");
        elements.put("text", Map.of(
                "tag", "lark_md",
                "content", msg.getContent() != null ? msg.getContent() : ""
        ));

        // 构建卡片内容元素
        java.util.List<Object> cardElements = new java.util.ArrayList<>();
        cardElements.add(elements);

        // 添加 traceId
        if (msg.getTraceId() != null && !msg.getTraceId().isBlank()) {
            Map<String, Object> traceElement = new HashMap<>();
            traceElement.put("tag", "note");
            traceElement.put("elements", java.util.List.of(
                    Map.of("tag", "plain_text", "content", "traceId: " + msg.getTraceId())
            ));
            cardElements.add(traceElement);
        }

        // 添加按钮
        Map<String, Object> action = new HashMap<>();
        action.put("tag", "action");

        java.util.List<Object> buttons = new java.util.ArrayList<>();

        // 确认按钮
        Map<String, Object> confirmBtn = new HashMap<>();
        confirmBtn.put("tag", "button");
        confirmBtn.put("text", Map.of("tag", "plain_text", "content", "✅ 确认执行"));
        confirmBtn.put("type", "primary");
        Map<String, Object> confirmAction = new HashMap<>();
        confirmAction.put("tag", "optimized");
        confirmAction.put("actionType", "callback");
        confirmAction.put("callbackData", String.format("action=confirm&traceId=%s", msg.getTraceId() != null ? msg.getTraceId() : ""));
        confirmAction.put("confirm", Map.of(
                "title", "确认执行",
                "content", "确定要执行此操作吗？"
        ));
        confirmBtn.put("value", confirmAction);
        buttons.add(confirmBtn);

        // 取消按钮
        Map<String, Object> cancelBtn = new HashMap<>();
        cancelBtn.put("tag", "button");
        cancelBtn.put("text", Map.of("tag", "plain_text", "content", "❌ 取消"));
        cancelBtn.put("type", "default");
        Map<String, Object> cancelAction = new HashMap<>();
        cancelAction.put("tag", "optimized");
        cancelAction.put("actionType", "callback");
        cancelAction.put("callbackData", String.format("action=cancel&traceId=%s", msg.getTraceId() != null ? msg.getTraceId() : ""));
        cancelBtn.put("value", cancelAction);
        buttons.add(cancelBtn);

        action.put("actions", buttons);
        cardElements.add(action);

        card.put("elements", cardElements);

        try {
            return new ObjectMapper().writeValueAsString(card);
        } catch (Exception e) {
            log.error("[feishu] Card JSON serialization error: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 通过 Webhook 发送（Fallback，不支持卡片）
     */
    private boolean sendViaWebhook(NotificationMessage msg) {
        String webhookUrl = config.getFeishu().getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("[feishu] No webhook URL configured, skipping send");
            return false;
        }

        try {
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

            String jsonBody = new ObjectMapper().writeValueAsString(payload);

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(webhookUrl))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();

            java.net.http.HttpResponse<String> response = java.net.http.HttpClient.newHttpClient()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("[feishu] Webhook message sent: title={}, target={}", msg.getTitle(), msg.getTarget());
                return true;
            } else {
                log.error("[feishu] Webhook send failed: status={}, body={}", response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("[feishu] Webhook send error: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 从 msg.extra 中获取 targetId
     */
    private String getTargetId(NotificationMessage msg) {
        if (msg.getExtra() != null && msg.getExtra().get("targetId") != null) {
            return msg.getExtra().get("targetId").toString();
        }
        return null;
    }

    /**
     * 根据 targetType 判断 receive_id_type
     */
    private String getReceiveIdType(NotificationMessage msg) {
        if (msg.getExtra() != null && "user".equals(msg.getExtra().get("targetType"))) {
            return "open_id";
        }
        return "chat_id";
    }

    /**
     * JSON 字符串转义
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
