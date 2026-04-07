package org.example.notification.channel;

import org.example.notification.model.NotificationMessage;

/**
 * 通知渠道接口（适配器模式）
 * 所有 IM 渠道（飞书、企业微信、钉钉等）均需实现此接口
 */
public interface NotificationChannel {

    /**
     * 获取渠道类型标识
     * @return 渠道类型，如 "FEISHU", "WECOM", "DINGTALK"
     */
    String getType();

    /**
     * 发送通知消息
     * @param msg 通知消息
     * @return 是否发送成功
     */
    boolean send(NotificationMessage msg);

    /**
     * 当前渠道是否支持交互卡片（按钮等）
     * @return true 表示支持
     */
    boolean supportsInteractive();
}
