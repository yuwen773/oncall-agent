package org.example.dto;

import lombok.Data;

@Data
public class NotificationConfigRequest {
    private String appId;
    private String appSecret;
    private String webhookUrl;
}
