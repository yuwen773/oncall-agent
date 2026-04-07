package org.example.notification.model;

/**
 * 通知消息格式枚举
 */
public enum MessageFormat {
    /** 纯文本 */
    TEXT,
    /** Markdown 格式 */
    MARKDOWN,
    /** 交互卡片（明日支持按钮回调） */
    CARD
}
