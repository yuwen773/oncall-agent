# SuperBizAgent 项目分析报告

> 生成日期: 2026-04-07
> 项目版本: release-2026-01-02

---

## 1. 项目概述

SuperBizAgent 是一个**企业级智能业务 Agent 系统**，包含两大核心模块：

| 模块 | 功能 | 核心技术 |
|------|------|----------|
| **RAG 智能问答** | 基于向量检索的文档问答 | Milvus + DashScope Embedding |
| **AIOps 智能运维** | Planner-Executor-Replanner 自动告警分析 | Spring AI Agent + Supervisor 架构 |

---

## 2. 技术栈

### 2.1 核心框架

| 组件 | 版本 | 说明 |
|------|------|------|
| Java | 17 | 编程语言 |
| Spring Boot | 3.2.0 | 基础框架 |
| Spring AI | 1.1.0 | AI 抽象层 |
| Spring AI Alibaba | 1.1.0.0-RC2 | 阿里云 DashScope 集成 |
| Spring AI Agent Framework | 1.1.0.0-RC2 | 多 Agent 编排 |
| Spring AI MCP Client WebFlux | 1.1.0 | 腾讯云 CLS 日志集成 |

### 2.2 数据库与向量数据库

| 组件 | 版本 | 端口 |
|------|------|------|
| Milvus | 2.5.10 | 19530 |
| Milvus Console | 9091 | Web 管理界面 |
| Attu (可视化) | 2.5 | 8000 |
| etcd | 3.5.18 | - |
| MinIO | RELEASE.2023-03-20T20-16-18Z | - |

### 2.3 AI 服务

| 服务 | 配置 |
|------|------|
| DashScope SDK | 2.17.0 |
| Embedding 模型 | text-embedding-v4 |
| Chat 模型 | qwen3-max |
| API 超时 | 180 秒 |

### 2.4 监控与日志

| 组件 | 配置 |
|------|------|
| Prometheus | http://localhost:9090 |
| 腾讯云 CLS | MCP SSE 协议集成 |

### 2.5 其他依赖

| 库 | 版本 | 用途 |
|----|------|------|
| Gson | 2.10.1 | JSON 序列化 |
| Jackson | 2.17.0 | JSON 处理 |
| Lombok | 1.18.30 | 注解处理器 |
| jsonschema-generator | 4.36.0 | Spring AI 工具生成 |

---

## 3. 项目架构

### 3.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                      ChatController (端口 9900)                  │
│              /api/chat  /api/chat_stream  /api/ai_ops            │
└────────────────────────────┬────────────────────────────────────┘
                             │
              ┌──────────────┴──────────────┐
              ▼                             ▼
      ┌─────────────┐               ┌─────────────┐
      │  RagService │               │ AiOpsService│
      │ (向量检索)   │               │ (多Agent)   │
      └──────┬──────┘               └──────┬──────┘
             │                               │
    ┌────────┴────────┐           ┌─────────┴─────────┐
    ▼                 ▼           ▼                   ▼
 Milvus 2.5.10   DashScope     Prometheus         Tencent CLS
 (向量数据库)    (LLM/Embed)    (监控)            (日志, MCP)
```

### 3.2 项目模块结构

```
src/main/java/org/example/
├── controller/              # API 控制器层
│   ├── ChatController.java           # 统一 API 入口
│   ├── FileUploadController.java      # 文件上传
│   └── MilvusCheckController.java     # Milvus 健康检查
├── service/                 # 业务服务层
│   ├── ChatService.java              # 通用聊天服务
│   ├── AiOpsService.java              # AIOps 多 Agent 编排
│   ├── RagService.java                # RAG 问答服务
│   ├── VectorSearchService.java       # Milvus 向量搜索
│   ├── VectorEmbeddingService.java     # DashScope 向量化
│   ├── VectorIndexService.java         # 向量索引管理
│   └── DocumentChunkService.java       # 文档分块
├── agent/tool/              # Agent Tools
│   ├── DateTimeTools.java            # 时间查询
│   ├── QueryMetricsTools.java        # Prometheus 告警查询
│   ├── QueryLogsTools.java           # CLS 日志查询 (Mock)
│   └── InternalDocsTools.java        # 内部文档 RAG 检索
├── config/                  # 配置类
│   ├── DashScopeConfig.java          # DashScope API 配置
│   ├── MilvusConfig.java             # Milvus 客户端
│   ├── MilvusProperties.java
│   ├── WebMvcConfig.java             # CORS 配置
│   ├── WebConfig.java                # UTF-8 配置
│   └── FileUploadConfig.java         # 文件上传配置
├── client/                  # 客户端工厂
│   └── MilvusClientFactory.java     # Milvus 连接管理
├── dto/                     # 数据传输对象
├── constant/                 # 常量定义
└── Main.java                # 启动类
```

---

## 4. 核心组件详解

### 4.1 ChatController

| 端点 | 方法 | 用途 | 返回类型 |
|------|------|------|----------|
| `/api/chat` | POST | 普通对话 | JSON |
| `/api/chat_stream` | POST | 流式对话 (SSE) | text/event-stream |
| `/api/ai_ops` | POST | AIOps 分析 (SSE) | text/event-stream |
| `/api/chat/clear` | POST | 清空会话 | JSON |
| `/api/chat/session/{id}` | GET | 获取会话信息 | JSON |

### 4.2 AiOpsService - 三层 Agent 架构

```
┌─────────────────────────────────────────────────────────────────┐
│                      Supervisor Agent                            │
│            (基于 decision 在 Planner 与 Executor 间路由)           │
└─────────────────────────────────────────────────────────────────┘
           │                                      ▲
           ▼                                      │
┌─────────────────────┐                ┌─────────────────────┐
│    Planner Agent    │                │   Executor Agent   │
│   (规划/重规划角色)   │─────────────────→│    (执行首个步骤)   │
│                     │  executor_feedback │                    │
│ • 分析任务           │                │ • 调用工具           │
│ • 制定步骤           │                │ • 收集证据           │
│ • 决定下一步 action   │                │ • 反馈给 Planner     │
└─────────────────────┘                └─────────────────────┘
           ▲
           │
    ┌──────┴──────┐
    │  Tools     │
    ├────────────┤
    │DateTimeTools│
    │QueryMetricsTools│
    │QueryLogsTools (Mock)│
    │InternalDocsTools│
    │ + MCP Tools (真实环境)│
    └────────────┘
```

### 4.3 RagService - 向量检索流程

```
用户问题 → VectorEmbeddingService.generateQueryVector()
    ↓
Milvus 向量搜索 (L2 距离, topK=3)
    ↓
RagService.buildPrompt() → DashScope 流式生成
    ↓
SSE 流式返回答案
```

### 4.4 Agent Tools

| 工具类 | 工具名 | 功能 |
|--------|--------|------|
| DateTimeTools | `getCurrentDateTime` | 获取当前日期时间 |
| QueryMetricsTools | `queryPrometheusAlerts` | 查询 Prometheus 告警 |
| InternalDocsTools | `queryInternalDocs` | RAG 内部文档检索 |
| QueryLogsTools | MCP 注入 | 腾讯云日志查询 |

---

## 5. 数据模型

### 5.1 Milvus Collection: `biz`

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | VarChar(256) | 主键 |
| vector | FloatVector(1024) | 1024 维向量 |
| content | VarChar(8192) | 文档内容 |
| metadata | JSON | 元数据 |

**索引配置:**
- 向量索引: IVF_FLAT
- 距离度量: L2 (欧氏距离)

### 5.2 会话管理

```java
private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
```

- 最多保留 6 对消息 (12 条)
- 线程安全 (ReentrantLock)
- 超过自动清理最旧消息

---

## 6. 配置汇总

### 6.1 application.yml 关键配置

```yaml
server.port: 9900
file.upload.path: ./uploads
file.upload.allowed-extensions: txt,md

milvus:
  host: localhost
  port: 19530
  database: default
  timeout: 10000

spring.ai.dashscope.api-key: ${DASHSCOPE_API_KEY}
dashscope.embedding.model: text-embedding-v4

rag.top-k: 3
rag.model: qwen3-max

prometheus.mock-enabled: false
cls.mock-enabled: false
```

### 6.2 环境变量

| 变量名 | 说明 | 必需 |
|--------|------|------|
| `DASHSCOPE_API_KEY` | 阿里云 DashScope API 密钥 | 是 |

---

## 7. 关键设计模式

| 模式 | 应用场景 |
|------|----------|
| **ReAct 模式** | ReactAgent 实现思考+工具调用循环 |
| **Supervisor 模式** | 多 Agent 协作调度 |
| **策略模式** | `buildMethodToolsArray()` 动态选择工具集 |
| **工厂模式** | MilvusClientFactory 客户端创建 |
| **滑动窗口** | SessionInfo 固定大小消息历史 |

---

## 8. API 请求/响应示例

### 8.1 聊天请求

**请求:**
```json
POST /api/chat
{
  "id": "session-123",
  "question": "查询当前时间"
}
```

**响应:**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true,
    "answer": "当前时间是 2026-04-07T10:30:00",
    "errorMessage": null
  }
}
```

### 8.2 流式响应 (SSE)

```
event: message
data: {"type":"content","data":"你好"}
data: {"type":"content","data":"，"}
data: {"type":"done","data":null}
```

---

## 9. 项目特点总结

### 9.1 优势

1. **架构清晰**: 分层设计，职责明确
2. **可扩展性强**: Tool 机制支持本地和 MCP 远程工具
3. **多 Agent 协作**: Supervisor 模式实现复杂的自动化流程
4. **流式响应**: SSE 支持实时交互体验
5. **Mock 模式**: 支持无外部服务的本地测试

### 9.2 注意事项

1. **无认证机制**: 生产环境需添加 Spring Security
2. **CORS 全开**: 生产环境需限制来源
3. **会话内存存储**: 重启后会话丢失，可考虑 Redis 持久化
4. **API Key 安全**: DASHSCOPE_API_KEY 勿提交到代码仓库

---

## 10. 关键文件路径

| 文件 | 路径 |
|------|------|
| pom.xml | `pom.xml` |
| application.yml | `src/main/resources/application.yml` |
| vector-database.yml | `vector-database.yml` |
| ChatController | `src/main/java/org/example/controller/ChatController.java` |
| AiOpsService | `src/main/java/org/example/service/AiOpsService.java` |
| RagService | `src/main/java/org/example/service/RagService.java` |
| MilvusConfig | `src/main/java/org/example/config/MilvusConfig.java` |
| Makefile | `Makefile` |
