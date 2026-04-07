package org.example.notification.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 统一通知消息模型
 */
@Data
public class NotificationMessage {

    /** 消息标题 */
    private String title;

    /** 消息内容（支持 Markdown） */
    private String content;

    /** 消息格式：TEXT / MARKDOWN / CARD */
    private MessageFormat format = MessageFormat.MARKDOWN;

    /** 目标用户 open_id / user_id 列表（@人） */
    private List<String> toUsers;

    /** 目标群组 chat_id 列表（发群） */
    private List<String> toGroups;

    /** 语义标签路由（如"值班群"、"P0告警组"）—— Agent 最爱 */
    private String target;

    /** 关联告警 ID / 会话 ID */
    private String traceId;

    /** 扩展字段 */
    private Map<String, Object> extra;

    /** 是否需要交互卡片按钮（明日支持） */
    private boolean interactive;

    /** 渠道类型，默认 FEISHU，易扩展企业微信/钉钉 */
    private String channelType = "FEISHU";
}
