package org.example.dto;

import lombok.Data;

@Data
public class NotificationTestRequest {
    /** 语义标签，如"值班群" */
    private String target;
    /** 标题 */
    private String title;
    /** 内容 */
    private String content;
    /** 关联 traceId */
    private String traceId;
}
