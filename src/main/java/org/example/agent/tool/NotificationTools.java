package org.example.agent.tool;

import org.example.notification.model.NotificationMessage;
import org.example.notification.service.NotificationService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 飞书通知 Agent Tool
 * 让 Planner/Executor 在 ReAct 循环中自主发送飞书通知
 */
@Component
public class NotificationTools {

    private final NotificationService notificationService;

    public NotificationTools(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Tool(name = "sendFeishuNotification",
          description = "向飞书发送告警报告、健康摘要或交互卡片。" +
                        "参数：title(标题), content(内容), target(语义标签如'值班群'), traceId(关联ID)。" +
                        "target 支持：'值班群'、'P0告警组'、'值班负责人'、'SRE群'。" +
                        "不要传入 open_id 或 chat_id，使用语义标签。")
    public String sendFeishuNotification(
            String title,
            String content,
            String target,
            String traceId
    ) {
        NotificationMessage msg = new NotificationMessage();
        msg.setTitle(title);
        msg.setContent(content);
        msg.setTarget(target);
        msg.setTraceId(traceId);
        notificationService.send(msg);
        return "飞书通知已发送至：" + target;
    }
}
