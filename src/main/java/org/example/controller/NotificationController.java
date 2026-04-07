package org.example.controller;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.NotificationConfigRequest;
import org.example.dto.NotificationTestRequest;
import org.example.notification.config.NotificationConfig;
import org.example.notification.model.NotificationMessage;
import org.example.notification.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 通知模块 Web Controller
 * 供现有 Web 页面配置、测试、管理语义标签
 */
@Slf4j
@RestController
@RequestMapping("/api/notification")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationConfig notificationConfig;

    // ========== 配置接口 ==========

    /**
     * 配置飞书连接参数
     * POST /api/notification/config
     */
    @PostMapping("/config")
    public ResponseEntity<ApiResponse<String>> config(@RequestBody NotificationConfigRequest request) {
        if (request.getAppId() != null) {
            notificationConfig.getFeishu().setAppId(request.getAppId());
        }
        if (request.getAppSecret() != null) {
            notificationConfig.getFeishu().setAppSecret(request.getAppSecret());
        }
        if (request.getWebhookUrl() != null) {
            notificationConfig.getFeishu().setWebhookUrl(request.getWebhookUrl());
        }
        log.info("Notification config updated");
        return ResponseEntity.ok(ApiResponse.success("配置已更新"));
    }

    // ========== 测试接口 ==========

    /**
     * 手动测试发送
     * POST /api/notification/test
     */
    @PostMapping("/test")
    public ResponseEntity<ApiResponse<String>> test(@RequestBody NotificationTestRequest request) {
        try {
            NotificationMessage msg = new NotificationMessage();
            msg.setTitle(request.getTitle());
            msg.setContent(request.getContent());
            msg.setTarget(request.getTarget());
            msg.setTraceId(request.getTraceId());
            notificationService.send(msg);
            log.info("Test notification sent: target={}, title={}", request.getTarget(), request.getTitle());
            return ResponseEntity.ok(ApiResponse.success("发送成功"));
        } catch (Exception e) {
            log.error("Test notification failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.error("发送失败: " + e.getMessage()));
        }
    }

    // ========== 语义标签管理 ==========

    /**
     * 获取所有语义标签映射
     * GET /api/notification/targets
     */
    @GetMapping("/targets")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTargets() {
        Map<String, Object> result = new HashMap<>();
        notificationConfig.getTargets().forEach((label, entry) -> {
            Map<String, Object> item = new HashMap<>();
            item.put("type", entry.getType());
            item.put("ids", entry.getIds());
            result.put(label, item);
        });
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 动态更新语义标签映射
     * PUT /api/notification/targets
     */
    @PutMapping("/targets")
    public ResponseEntity<ApiResponse<String>> updateTargets(@RequestBody Map<String, Map<String, Object>> targets) {
        targets.forEach((label, entry) -> {
            NotificationConfig.TargetEntry targetEntry = new NotificationConfig.TargetEntry();
            targetEntry.setType((String) entry.get("type"));
            Object ids = entry.get("ids");
            if (ids instanceof java.util.List) {
                targetEntry.setIds(((java.util.List<?>) ids).stream()
                        .map(Object::toString)
                        .toList());
            }
            notificationConfig.getTargets().put(label, targetEntry);
        });
        log.info("Targets updated: {}", targets.keySet());
        return ResponseEntity.ok(ApiResponse.success("语义标签已更新"));
    }

    // ========== 内部类（复用 ChatController 的 ApiResponse） ==========

    @Getter
    @Setter
    public static class ApiResponse<T> {
        private int code;
        private String message;
        private T data;

        public static <T> ApiResponse<T> success(T data) {
            ApiResponse<T> response = new ApiResponse<>();
            response.setCode(200);
            response.setMessage("success");
            response.setData(data);
            return response;
        }

        public static <T> ApiResponse<T> error(String message) {
            ApiResponse<T> response = new ApiResponse<>();
            response.setCode(500);
            response.setMessage(message);
            return response;
        }
    }

    // ========== 飞书卡片回调 ==========

    /**
     * 飞书卡片按钮回调
     * POST /callback/feishu
     * 飞书交互卡片点击按钮后会调用此接口
     */
    @PostMapping("/callback/feishu")
    public ResponseEntity<String> feishuCallback(@RequestBody Map<String, Object> callbackData) {
        try {
            log.info("[feishu] Card callback received: {}", callbackData);

            // 解析回调数据
            // 飞书回调格式：{ "action": {...}, "context": {...} }
            Object actionObj = callbackData.get("action");
            if (actionObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> action = (Map<String, Object>) actionObj;
                String value = (String) action.get("value");
                if (value != null && value.contains("action=confirm")) {
                    log.info("[feishu] Confirm action triggered");
                    // TODO: 触发 MCP 工具执行或 Agent 继续执行
                    return ResponseEntity.ok("{\"msg\":\"已确认\"}");
                } else if (value != null && value.contains("action=cancel")) {
                    log.info("[feishu] Cancel action triggered");
                    return ResponseEntity.ok("{\"msg\":\"已取消\"}");
                }
            }

            return ResponseEntity.ok("{\"msg\":\"callback received\"}");
        } catch (Exception e) {
            log.error("[feishu] Callback error: {}", e.getMessage(), e);
            return ResponseEntity.ok("{\"msg\":\"error: " + e.getMessage() + "\"}");
        }
    }
}
