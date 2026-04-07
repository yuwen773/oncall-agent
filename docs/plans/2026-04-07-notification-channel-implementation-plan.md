# 飞书多渠道通知模块实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现飞书通知模块，Agent 可通过 Tool 发送通知到飞书群/用户，支持语义标签路由，零侵入 SSE 流。

**Architecture:** 适配器模式 + 语义标签路由。NotificationService 统一入口，FeishuChannelImpl（Webhook版）先跑通，明日换 SDK。

**Tech Stack:** Java 17, Spring Boot 3.2.0, Spring AI 1.1.0, larksuite-oapi-java-sdk 2.2.0, Spring Retry

---

## Phase 1: Day 1 — 核心组件（NotificationMessage / Channel 接口 / Feishu 实现 / Service）

### Task 1: pom.xml 添加飞书 SDK 依赖

**Files:**
- Modify: `pom.xml:140-150`

**Step 1: 添加依赖**

在 `pom.xml` 的 `dependencies` 节点末尾（`</dependencies>` 前）添加：

```xml
        <!-- 飞书官方 SDK（2026年最新稳定版） -->
        <dependency>
            <groupId>com.larksuite.oapi</groupId>
            <artifactId>larksuite-oapi-java-sdk</artifactId>
            <version>2.2.0</version>
        </dependency>

        <!-- Spring Retry（失败重试） -->
        <dependency>
            <groupId>org.springframework.retry</groupId>
            <artifactId>spring-retry</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aspects</artifactId>
        </dependency>
```

**Step 2: 验证依赖**

Run: `cd D:/Work/code/oncall-agent && mvn dependency:tree -Dincludes=com.larksuite.oapi 2>&1 | grep larksuite`
Expected: `com.larksuite.oapi:larksuite-oapi-java-sdk:jar:2.2.0`

**Step 3: Commit**

```bash
git add pom.xml
git commit -m "feat(notification): 添加飞书 SDK 和 Spring Retry 依赖

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 2: 创建 MessageFormat 枚举

**Files:**
- Create: `src/main/java/org/example/notification/model/MessageFormat.java`

**Step 1: 创建文件**

```java
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
```

**Step 2: 验证文件**

Run: `ls src/main/java/org/example/notification/model/MessageFormat.java`
Expected: 文件存在

**Step 3: Commit**

```bash
git add src/main/java/org/example/notification/model/MessageFormat.java
git commit -m "feat(notification): 添加 MessageFormat 枚举

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 3: 创建 NotificationMessage 模型

**Files:**
- Create: `src/main/java/org/example/notification/model/NotificationMessage.java`

**Step 1: 创建文件**

```java
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
```

**Step 2: 验证文件**

Run: `ls src/main/java/org/example/notification/model/NotificationMessage.java`
Expected: 文件存在

**Step 3: Commit**

```bash
git add src/main/java/org/example/notification/model/NotificationMessage.java
git commit -m "feat(notification): 添加 NotificationMessage 模型

包含 title/content/format/toUsers/toGroups/target/traceId/ext/interactive/channelType
支持语义标签路由和多渠道预留

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 4: 创建 NotificationChannel 接口

**Files:**
- Create: `src/main/java/org/example/notification/channel/NotificationChannel.java`

**Step 1: 创建文件**

```java
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
```

**Step 2: 验证文件**

Run: `ls src/main/java/org/example/notification/channel/NotificationChannel.java`
Expected: 文件存在

**Step 3: Commit**

```bash
git add src/main/java/org/example/notification/channel/NotificationChannel.java
git commit -m "feat(notification): 添加 NotificationChannel 接口

定义 getType()/send()/supportsInteractive()
适配器模式，易扩展企业微信/钉钉

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 5: 创建 NotificationConfig 配置类

**Files:**
- Create: `src/main/java/org/example/notification/config/NotificationConfig.java`

**Step 1: 创建文件**

```java
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
```

**Step 2: 验证文件**

Run: `ls src/main/java/org/example/notification/config/NotificationConfig.java`
Expected: 文件存在

**Step 3: Commit**

```bash
git add src/main/java/org/example/notification/config/NotificationConfig.java
git commit -m "feat(notification): 添加 NotificationConfig 配置类

支持 notification.feishu.* 和 notification.targets.*
TargetEntry 支持 type + ids 数组（一个标签对应多个目标）

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 6: 创建 FeishuChannelImpl（Webhook 版）

**Files:**
- Create: `src/main/java/org/example/notification/channel/FeishuChannelImpl.java`

**Step 1: 创建文件**

```java
package org.example.notification.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.notification.config.NotificationConfig;
import org.example.notification.model.NotificationMessage;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 飞书渠道实现（自定义机器人 Webhook 版）
 * 今日快速验证，明日可替换为自建应用 SDK（支持卡片+按钮）
 */
@Slf4j
@Component
public class FeishuChannelImpl implements NotificationChannel {

    private static final String CHANNEL_TYPE = "FEISHU";

    private final NotificationConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public FeishuChannelImpl(NotificationConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String getType() {
        return CHANNEL_TYPE;
    }

    @Override
    public boolean supportsInteractive() {
        // Webhook 版不支持卡片按钮，明日换 SDK 后返回 true
        return false;
    }

    @Override
    public boolean send(NotificationMessage msg) {
        String webhookUrl = config.getFeishu().getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("[feishu] Webhook URL not configured, skipping send");
            return false;
        }

        if (!config.getFeishu().isEnabled()) {
            log.warn("[feishu] Channel disabled, skipping send");
            return false;
        }

        try {
            // 构建飞书自定义机器人消息格式
            Map<String, Object> payload = buildRobotPayload(msg);

            String jsonBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("[feishu] Message sent successfully: title={}, target={}", msg.getTitle(), msg.getTarget());
                return true;
            } else {
                log.error("[feishu] Send failed: status={}, body={}", response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("[feishu] Send error: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 构建飞书自定义机器人消息 JSON
     * 支持 Markdown 格式
     */
    private Map<String, Object> buildRobotPayload(NotificationMessage msg) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("msg_type", "markdown");

        Map<String, Object> content = new java.util.HashMap<>();
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

        return payload;
    }
}
```

**Step 2: 验证编译**

Run: `cd D:/Work/code/oncall-agent && mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS（无错误）

**Step 3: Commit**

```bash
git add src/main/java/org/example/notification/channel/FeishuChannelImpl.java
git commit -m "feat(notification): 添加 FeishuChannelImpl（Webhook 版）

使用飞书自定义机器人 Webhook 发送 Markdown 消息
今日快速验证，明日可替换为自建应用 SDK

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 7: 创建 NotificationService

**Files:**
- Create: `src/main/java/org/example/notification/service/NotificationService.java`

**Step 1: 创建文件**

```java
package org.example.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.example.notification.channel.NotificationChannel;
import org.example.notification.config.NotificationConfig;
import org.example.notification.model.NotificationMessage;
import org.springframework.stereotype.Service;

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
        cloned.setExtra(msg.getExtra() != null ? new java.util.HashMap<>(msg.getExtra()) : new java.util.HashMap<>());
        cloned.getExtra().put("targetId", targetId);
        cloned.getExtra().put("targetType", targetType);
        return cloned;
    }
}
```

**Step 2: 验证编译**

Run: `cd D:/Work/code/oncall-agent && mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/org/example/notification/service/NotificationService.java
git commit -m "feat(notification): 添加 NotificationService 核心路由服务

支持语义标签解析（target → toGroups/toUsers）
渠道分发 + 单目标克隆发送

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 8: application.yml 添加 notification 配置

**Files:**
- Modify: `src/main/resources/application.yml`

**Step 1: 在文件末尾添加配置块**

在 `application.yml` 末尾添加：

```yaml
# 通知模块配置
notification:
  feishu:
    webhook-url: ${FEISHU_WEBHOOK_URL:}
    app-id: ${FEISHU_APP_ID:}
    app-secret: ${FEISHU_APP_SECRET:}
    enabled: true
  # 语义标签 → 真实 ID 映射
  targets:
    值班群:
      type: group
      ids:
        - oc_xxxxxxxxxxxx
    P0告警组:
      type: group
      ids:
        - oc_yyyyyyyyyyyyyyyy
    值班负责人:
      type: user
      ids:
        - ou_zzzzzzzzzzzzzzzz
    SRE群:
      type: group
      ids:
        - oc_aaaaaaaaaaaa
```

**Step 2: 验证配置**

Run: `grep -A 20 "^notification:" src/main/resources/application.yml`
Expected: 输出包含 feishu 和 targets 配置

**Step 3: Commit**

```bash
git add src/main/resources/application.yml
git commit -m "feat(notification): 添加 application.yml notification 配置

包含飞书 Webhook/AppID/AppSecret 和语义标签映射

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Phase 2: Day 2 — Agent Tool 集成 + Planner Prompt

### Task 9: 创建 NotificationTools Agent Tool

**Files:**
- Create: `src/main/java/org/example/agent/tool/NotificationTools.java`

**Step 1: 创建文件**

```java
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
```

**Step 2: 验证编译**

Run: `cd D:/Work/code/oncall-agent && mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/org/example/agent/tool/NotificationTools.java
git commit -m "feat(notification): 添加 NotificationTools Agent Tool

sendFeishuNotification(title, content, target, traceId)
target 使用语义标签，不暴露 open_id/chat_id

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 10: 将 NotificationTools 集成到 AiOpsService

**Files:**
- Modify: `src/main/java/org/example/service/AiOpsService.java`

**Step 1: 添加 @Autowired 注入**

在 `AiOpsService` 类中，`dateTimeTools`、`internalDocsTools`、`queryMetricsTools`、`queryLogsTools` 之后添加：

```java
    @Autowired
    private NotificationTools notificationTools;
```

**Step 2: 修改 buildMethodToolsArray 方法**

将：

```java
    private Object[] buildMethodToolsArray() {
        if (queryLogsTools != null) {
            return new Object[]{dateTimeTools, internalDocsTools, queryMetricsTools, queryLogsTools};
        } else {
            return new Object[]{dateTimeTools, internalDocsTools, queryMetricsTools};
        }
    }
```

改为：

```java
    private Object[] buildMethodToolsArray() {
        if (queryLogsTools != null) {
            return new Object[]{
                dateTimeTools, internalDocsTools, queryMetricsTools, queryLogsTools,
                notificationTools
            };
        } else {
            return new Object[]{
                dateTimeTools, internalDocsTools, queryMetricsTools,
                notificationTools
            };
        }
    }
```

**Step 3: 验证编译**

Run: `cd D:/Work/code/oncall-agent && mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

**Step 4: Commit**

```bash
git add src/main/java/org/example/service/AiOpsService.java
git commit -m "feat(notification): 将 NotificationTools 集成到 AiOpsService

零侵入 SSE 流：NotificationTools 作为 Tool 加入 buildMethodToolsArray
Planner/Executor 可在 ReAct 循环中自主调用 sendFeishuNotification

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 11: 更新 Planner Prompt 添加通知引导

**Files:**
- Modify: `src/main/java/org/example/service/AiOpsService.java`

**Step 1: 在 buildPlannerPrompt() 方法末尾添加通知要求段落**

在 Planner Prompt 的"## 最终报告输出要求"之后、"**重要提醒**"之前添加：

```markdown
## 通知要求

- 告警分析完成后，必须使用 sendFeishuNotification 工具将报告发送到"值班群"
- P0/P1 级别告警必须发送通知；P2 及以下可选择性发送
- target 参数传入语义标签（如"值班群"、"P0告警组"），不要传入 open_id 或 chat_id
- traceId 传入告警关联的唯一标识

示例：
- 正确：sendFeishuNotification(title="P0告警分析完成", content="...", target="值班群", traceId="alert-20260407-001")
- 错误：不要传入 open_id 或 chat_id
```

**Step 2: 验证编译**

Run: `cd D:/Work/code/oncall-agent && mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/org/example/service/AiOpsService.java
git commit -m "feat(notification): Planner Prompt 添加通知引导

引导 Planner 在告警分析完成后使用 sendFeishuNotification
提供正确/错误示例，防止 Agent 暴露 open_id/chat_id

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Phase 3: Day 3 — Web Controller API + DTO

### Task 12: 创建 Request/Response DTO

**Files:**
- Create: `src/main/java/org/example/dto/NotificationConfigRequest.java`
- Create: `src/main/java/org/example/dto/NotificationTestRequest.java`

**Step 1: 创建 NotificationConfigRequest**

```java
package org.example.dto;

import lombok.Data;

@Data
public class NotificationConfigRequest {
    private String appId;
    private String appSecret;
    private String webhookUrl;
}
```

**Step 2: 创建 NotificationTestRequest**

```java
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
```

**Step 3: Commit**

```bash
git add src/main/java/org/example/dto/NotificationConfigRequest.java \
        src/main/java/org/example/dto/NotificationTestRequest.java
git commit -m "feat(notification): 添加 Notification Web DTO

NotificationConfigRequest: appId/appSecret/webhookUrl
NotificationTestRequest: target/title/content/traceId

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 13: 创建 NotificationController

**Files:**
- Create: `src/main/java/org/example/controller/NotificationController.java`

**Step 1: 创建文件**

```java
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
}
```

**Step 2: 验证编译**

Run: `cd D:/Work/code/oncall-agent && mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/org/example/controller/NotificationController.java
git commit -m "feat(notification): 添加 NotificationController

POST /api/notification/config — 配置飞书连接参数
POST /api/notification/test — 手动测试发送
GET/PUT /api/notification/targets — 语义标签管理

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Phase 4: Day 4（可选）— 交互卡片 + 飞书回调

### Task 14: FeishuChannelImpl 升级为自建应用 SDK（支持卡片）

此任务待明日实施：

**Files:**
- Modify: `src/main/java/org/example/notification/channel/FeishuChannelImpl.java`

**变更内容：**
1. 替换 Webhook 发送为官方 SDK `ImService`
2. 实现 `supportsInteractive()` 返回 `true`
3. 支持 `NotificationMessage.format == CARD` 时发送交互卡片
4. 实现按钮回调接收逻辑

**验收标准：**
- Agent 发送的卡片消息带"确认执行"按钮
- 按钮点击后回调 `POST /callback/feishu` 触发 MCP 工具

---

## 验收标准

| 任务 | 验收条件 |
|------|----------|
| Task 1 | `mvn dependency:tree` 显示 larksuite-oapi-java-sdk:2.2.0 |
| Task 3 | `NotificationMessage` 包含所有字段（title/content/format/toUsers/toGroups/target/traceId/extra/interactive/channelType） |
| Task 4 | `NotificationChannel` 接口定义 `getType()`/`send()`/`supportsInteractive()` |
| Task 5 | `NotificationConfig` 正确映射 `notification.targets.*` |
| Task 6 | `FeishuChannelImpl` Webhook 版可发送 Markdown 消息 |
| Task 7 | `NotificationService.send()` 正确解析语义标签并分发 |
| Task 9 | `NotificationTools.sendFeishuNotification()` 可通过 `@Tool` 注解被 Agent 调用 |
| Task 10 | `AiOpsService.buildMethodToolsArray()` 包含 `notificationTools` |
| Task 11 | Planner Prompt 包含通知引导段落 |
| Task 13 | `/api/notification/test` 可手动触发发送 |
| 全流程 | `mvn spring-boot:run` 启动成功，`POST /api/notification/test` 返回发送成功 |

---

## 快速启动命令

```bash
# 设置飞书 Webhook（Day 1 后即可测试）
export FEISHU_WEBHOOK_URL=https://open.feishu.cn/open-apis/bot/v2/hook/你的机器人token

# 编译
cd D:/Work/code/oncall-agent
mvn compile

# 启动
mvn spring-boot:run

# 测试发送
curl -X POST http://localhost:9900/api/notification/test \
  -H "Content-Type: application/json" \
  -d '{"target":"值班群","title":"测试通知","content":"这是一条测试消息","traceId":"test-001"}'
```
