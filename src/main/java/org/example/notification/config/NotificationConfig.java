package org.example.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知模块配置
 * 映射 application.yml 的 notification.* 配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "notification")
public class NotificationConfig {

    /** 飞书配置 */
    private FeishuConfig feishu = new FeishuConfig();

    /** 语义标签 → 真实 ID 映射 */
    private Map<String, TargetEntry> targets = new HashMap<>();

    @Data
    public static class TargetEntry {
        /** 类型：group / user */
        private String type;
        /** ID 列表（支持一个标签对应多个群/用户） */
        private List<String> ids;
    }

    @Data
    public static class FeishuConfig {
        /** 自定义机器人 Webhook URL（今日使用） */
        private String webhookUrl;
        /** 自建应用 App ID（明日使用） */
        private String appId;
        /** 自建应用 App Secret（明日使用） */
        private String appSecret;
        /** 是否启用 */
        private boolean enabled = true;
    }
}
