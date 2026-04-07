# 飞书多渠道通知模块设计文档

> 日期：2026-04-07
> 状态：已批准

## 1. 概述

### 1.1 目标

在 SuperBizAgent 项目中实现**多渠道通知模块**，优先支持飞书（普通消息 + 交互卡片 + Human-in-the-Loop），后续极易扩展企业微信/钉钉。

### 1.2 设计原则

1. **零侵入 SSE 流** — 通知作为 Tool 接入，`/ai_ops` SSE 输出完全不变
2. **适配器模式** — `NotificationChannel` 接口 + `FeishuChannelImpl`，新增渠道只需实现 Impl
3. **语义标签路由** — Agent 传 `target="值班群"`，底层自动解析为 `toGroups` / `toUsers`
4. **渐进式实现** — Webhook 机器人先跑通 → 明日可无缝切换自建应用 SDK
5. **Web 可管理** — 新增 Controller 供现有 Web 页面配置、测试、查看历史

### 1.3 技术选型

- **飞书 SDK**：`com.larksuite.oapi:larksuite-oapi-java-sdk:2.2.0`
- **Spring Retry**：失败重试（已在项目中配置）
- **渐进式**：
  - 今天：自定义机器人 Webhook（1小时跑通）
  - 明天：自建应用官方 SDK（支持卡片+按钮+人机交互）

---

## 2. 整体架构

### 2.1 数据流

```
Planner/Executor ReAct 循环
         │
         ▼
  sendFeishuNotification Tool
         │
         ▼
  NotificationService (语义解析)
         │
    ┌────┴────┐
    ▼         ▼
toGroups   toUsers
    │         │
    └────┬────┘
         ▼
 FeishuChannelImpl
 (Webhook → 明日换 SDK)
         │
         ▼
    飞书用户/群
```

### 2.2 包结构

```
src/main/java/org/example/
├── notification/
│   ├── model/
│   │   ├── NotificationMessage.java    # 统一消息模型
│   │   └── MessageFormat.java          # TEXT / MARKDOWN / CARD
│   ├── channel/
│   │   ├── NotificationChannel.java    # 渠道接口
│   │   └── FeishuChannelImpl.java      # 飞书实现（Webhook版先行）
│   ├── config/
│   │   └── NotificationConfig.java     # 配置映射（值班群→ID）
│   └── service/
│       └── NotificationService.java     # 核心路由 + 语义解析
├── agent/tool/
│   └── NotificationTools.java           # ★ Agent Tool（零侵入接入）
└── controller/
    └── NotificationController.java      # Web 管理接口
```

---

## 3. 核心模型

### 3.1 NotificationMessage

```java
@Data
public class NotificationMessage {
    private String title;
    private String content;                    // Markdown 支持
    private MessageFormat format = MARKDOWN;   // TEXT / MARKDOWN / CARD

    // 底层真实目标（支持同时发群 + @人）
    private List<String> toUsers;             // open_id / user_id 列表
    private List<String> toGroups;            // chat_id / group_id 列表

    // 语义标签路由 —— Agent 最爱
    private String target;                    // "值班群" / "P0告警组" / "值班负责人"

    private String traceId;                   // 关联告警ID
    private Map<String, Object> extra;
    private boolean interactive;               // 是否需要卡片按钮（明日支持）

    // 多渠道预留
    private String channelType = "FEISHU";    // 默认飞书，易扩展企业微信/钉钉
}
```

### 3.2 NotificationConfig

```java
@Data
@ConfigurationProperties(prefix = "notification")
public class NotificationConfig {

    private FeishuConfig feishu = new FeishuConfig();
    private Map<String, TargetEntry> targets = new HashMap<>();

    @Data
    public static class TargetEntry {
        private String type;      // "group" / "user"
        private List<String> ids; // 支持数组（一个标签对应多个群/用户）
    }

    @Data
    public static class FeishuConfig {
        private String webhookUrl;
        private String appId;
        private String appSecret;
        private boolean enabled = true;
    }
}
```

---

## 4. 配置设计

### 4.1 application.yml

```yaml
notification:
  feishu:
    webhook-url: ${FEISHU_WEBHOOK_URL:}
    app-id: ${FEISHU_APP_ID:}
    app-secret: ${FEISHU_APP_SECRET:}
    enabled: true

  # 语义标签 → 真实 ID 映射（支持数组）
  targets:
    值班群:
      type: group
      ids:
        - oc_xxxxxxxxxxxx
        - oc_yyyyyyyyyy
    P0告警组:
      type: group
      ids:
        - oc_aaaaaaaaaaaa
    值班负责人:
      type: user
      ids:
        - ou_zzzzzzzzzzzz
    SRE群:
      type: group
      ids:
        - oc_bbbbbbbbbbbb
```

---

## 5. 核心组件

### 5.1 NotificationChannel 接口

```java
public interface NotificationChannel {
    String getType();                     // "FEISHU", "WECOM", ...
    boolean send(NotificationMessage msg);
    boolean supportsInteractive();        // 是否支持卡片按钮
}
```

### 5.2 FeishuChannelImpl（Webhook 版先行）

- 使用飞书自定义机器人 Webhook 发送消息
- 今日快速验证，明日替换为自建应用 SDK
- 支持 Markdown 格式内容

### 5.3 NotificationService

核心职责：
1. 接收 `NotificationMessage`
2. 如果 `target` 不为空，查询 `NotificationConfig.targets` 映射表
3. 解析出 `toGroups` / `toUsers`
4. 循环调用 `FeishuChannelImpl.send()`

```java
@Service
public class NotificationService {
    private final List<NotificationChannel> channels;
    private final NotificationConfig config;

    public void send(NotificationMessage msg) {
        // 语义标签解析
        if (msg.getTarget() != null && !msg.getTarget().isBlank()) {
            resolveTarget(msg);
        }

        // 路由发送
        channels.stream()
                .filter(c -> c.getType().equals(msg.getChannelType()))
                .forEach(c -> {
                    if (msg.getToGroups() != null) {
                        msg.getToGroups().forEach(id -> c.send(msg));
                    }
                    if (msg.getToUsers() != null) {
                        msg.getToUsers().forEach(id -> c.send(msg));
                    }
                });
    }

    private void resolveTarget(NotificationMessage msg) {
        NotificationConfig.TargetEntry entry = config.getTargets().get(msg.getTarget());
        if (entry == null) return;

        if ("group".equals(entry.getType())) {
            msg.setToGroups(entry.getIds());
        } else if ("user".equals(entry.getType())) {
            msg.setToUsers(entry.getIds());
        }
    }
}
```

### 5.4 NotificationTools（Agent Tool）

```java
@Component
public class NotificationTools {

    private final NotificationService notificationService;

    @Tool(name = "sendFeishuNotification",
          description = "向飞书发送告警报告、健康摘要或交互卡片。" +
                        "参数：title(标题), content(内容), target(语义标签如'值班群'), traceId(关联ID)")
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

---

## 6. 与 AiOpsService 集成

### 6.1 集成方式（零侵入）

在 `AiOpsService.java` 的 `buildMethodToolsArray()` 中注入 `NotificationTools`：

```java
@Autowired
private NotificationTools notificationTools;

private Object[] buildMethodToolsArray() {
    if (queryLogsTools != null) {
        return new Object[]{
            dateTimeTools, internalDocsTools, queryMetricsTools, queryLogsTools,
            notificationTools   // ← 新增
        };
    } else {
        return new Object[]{
            dateTimeTools, internalDocsTools, queryMetricsTools,
            notificationTools   // ← 新增
        };
    }
}
```

**关键：SSE 流完全不变**
- `/ai_ops` SSE 接口 → `ChatController` → `AiOpsService.executeAiOpsAnalysis()`
- Planner/Executor 在 ReAct 循环中自主调用 `sendFeishuNotification` Tool
- SSE 输出只返回给前端，通知走异步 Tool 路径

### 6.2 Planner Prompt 引导

在 `buildPlannerPrompt()` 的"最终报告输出要求"后添加：

```
## 通知要求

- 告警分析完成后，必须使用 sendFeishuNotification 工具将报告发送到"值班群"
- P0/P1 级别告警必须发送通知；P2 及以下可选择性发送
- target 参数传入语义标签（如"值班群"、"P0告警组"），不要传入 open_id 或 chat_id
- traceId 传入告警关联的唯一标识

示例：
- 正确：sendFeishuNotification(title="P0告警分析完成", content="...", target="值班群", traceId="alert-20260407-001")
- 错误：不要传入 open_id 或 chat_id
```

---

## 7. Web Controller API

### 7.1 NotificationController

```java
@RestController
@RequestMapping("/api/notification")
public class NotificationController {

    // POST /api/notification/config — 配置飞书连接参数
    // Body: NotificationConfigRequest { appId, appSecret, webhookUrl }

    // POST /api/notification/test — 手动测试发送
    // Body: NotificationTestRequest { target, title, content, traceId }

    // GET /api/notification/targets — 获取所有语义标签映射
    // PUT /api/notification/targets — 动态更新语义标签（Web页面可实时编辑）

    // GET /api/notification/history — 查看最近通知记录（NotificationLog实体）

    // POST /callback/feishu — 飞书卡片按钮回调（明日支持）
}
```

### 7.2 Request DTO

```java
// NotificationConfigRequest.java
@Data
public class NotificationConfigRequest {
    private String appId;
    private String appSecret;
    private String webhookUrl;
}

// NotificationTestRequest.java
@Data
public class NotificationTestRequest {
    private String target;    // 语义标签
    private String title;
    private String content;
    private String traceId;
}
```

### 7.3 NotificationLog 实体（可选）

```java
@Entity
@Data
public class NotificationLog {
    @Id
    private Long id;
    private String title;
    private String content;
    private String target;        // 语义标签
    private String channelType;   // FEISHU / WECOM / DINGTALK
    private boolean success;
    private String errorMessage;
    private String traceId;
    private LocalDateTime createTime;
}
```

---

## 8. Web 页面集成点

在现有 Web 页面新增：

| 功能 | 接口 | 说明 |
|------|------|------|
| 通知配置 | `POST /api/notification/config` | 配置飞书 AppID/Secret/Webhook |
| 测试发送 | `POST /api/notification/test` | Web 页面一键测试 |
| 目标管理 | `GET/PUT /api/notification/targets` | 增删改语义标签映射 |
| 发送历史 | `GET /api/notification/history` | 查看最近 20 条发送记录 |

---

## 9. 实施路线图

| 阶段 | 内容 | 优先级 |
|------|------|--------|
| Day 1 | pom.xml 加依赖 + NotificationMessage + NotificationChannel 接口 + FeishuChannelImpl（Webhook版）+ NotificationService + NotificationTools | P0 |
| Day 2 | NotificationTools 集成到 AiOpsService + Planner Prompt 引导 | P0 |
| Day 3 | NotificationController + Web 配置/测试页面调用 | P1 |
| Day 4 | 交互卡片（按钮确认执行 MCP）+ 飞书回调 | P1 |
| 后续 | 企业微信/钉钉适配器（只需再实现一个 Impl） | P2 |

---

## 10. 参考项目

- [MateClaw](https://github.com/matevip/mateclaw) — Spring AI Alibaba 多渠道通知最佳实践参考
