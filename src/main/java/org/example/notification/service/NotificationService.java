package org.example.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.example.notification.channel.NotificationChannel;
import org.example.notification.config.NotificationConfig;
import org.example.notification.model.NotificationMessage;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;

/**
 * 通知服务核心（语义标签路由 + 渠道分发）
 */
@Slf4j
@Service
public class NotificationService {

    private final List<NotificationChannel> channels;
    private final NotificationConfig config;

    public NotificationService(List<NotificationChannel> channels, NotificationConfig config) {
        this.channels = channels;
        this.config = config;
    }

    /**
     * 发送通知
     * 1. 如果 target 不为空，查询语义标签映射，填充 toGroups / toUsers
     * 2. 路由到对应渠道，循环发送
     */
    public void send(NotificationMessage msg) {
        // 语义标签解析
        if (msg.getTarget() != null && !msg.getTarget().isBlank()) {
            resolveTarget(msg);
        }

        // 路由发送
        String channelType = msg.getChannelType() != null ? msg.getChannelType() : "FEISHU";
        channels.stream()
                .filter(c -> c.getType().equals(channelType))
                .findFirst()
                .ifPresent(channel -> {
                    if (msg.getToGroups() != null) {
                        msg.getToGroups().forEach(id -> {
                            NotificationMessage single = cloneForTarget(msg, id, "group");
                            channel.send(single);
                        });
                    }
                    if (msg.getToUsers() != null) {
                        msg.getToUsers().forEach(id -> {
                            NotificationMessage single = cloneForTarget(msg, id, "user");
                            channel.send(single);
                        });
                    }
                    // 如果既没有 toGroups 也没有 toUsers，尝试直接发送（fallback）
                    if ((msg.getToGroups() == null || msg.getToGroups().isEmpty())
                            && (msg.getToUsers() == null || msg.getToUsers().isEmpty())) {
                        log.warn("[notification] No target resolved for message: title={}, target={}",
                                msg.getTitle(), msg.getTarget());
                    }
                });
    }

    /**
     * 根据语义标签解析真实 ID
     */
    private void resolveTarget(NotificationMessage msg) {
        NotificationConfig.TargetEntry entry = config.getTargets().get(msg.getTarget());
        if (entry == null) {
            log.warn("[notification] Target label not found: {}", msg.getTarget());
            return;
        }

        if ("group".equals(entry.getType())) {
            msg.setToGroups(entry.getIds());
            log.info("[notification] Resolved target '{}' to groups: {}", msg.getTarget(), entry.getIds());
        } else if ("user".equals(entry.getType())) {
            msg.setToUsers(entry.getIds());
            log.info("[notification] Resolved target '{}' to users: {}", msg.getTarget(), entry.getIds());
        }
    }

    /**
     * 为单个目标克隆消息（FeishuChannelImpl 需要 targetId）
     * 这里把 targetId 存在 extra 中，channel 实现自行处理
     */
    private NotificationMessage cloneForTarget(NotificationMessage msg, String targetId, String targetType) {
        NotificationMessage cloned = new NotificationMessage();
        cloned.setTitle(msg.getTitle());
        cloned.setContent(msg.getContent());
        cloned.setFormat(msg.getFormat());
        cloned.setTraceId(msg.getTraceId());
        cloned.setInteractive(msg.isInteractive());
        cloned.setChannelType(msg.getChannelType());
        cloned.setExtra(msg.getExtra() != null ? new HashMap<>(msg.getExtra()) : new HashMap<>());
        cloned.getExtra().put("targetId", targetId);
        cloned.getExtra().put("targetType", targetType);
        return cloned;
    }
}