# SuperBizAgent 项目上手指南

> 适用版本: release-2026-01-02
> 更新日期: 2026-04-07

---

## 1. 环境要求

### 1.1 必需环境

| 组件 | 版本要求 |
|------|----------|
| Java | 17+ |
| Maven | 3.6+ |
| Docker | 20.10+ |
| Docker Compose | 2.0+ |

### 1.2 外部服务

| 服务 | 端口 | 用途 |
|------|------|------|
| Milvus | 19530, 9091, 8000 | 向量数据库 |
| Prometheus | 9090 | 监控 (可选，启用 mock) |
| 腾讯云 CLS | - | 日志服务 (可选，启用 mock) |

---

## 2. 快速开始

### 2.1 一键初始化 (推荐)

```bash
# 设置环境变量
export DASHSCOPE_API_KEY=your-api-key

# 执行一键初始化 (启动 Milvus + 构建 + 运行 + 上传文档)
make init
```

### 2.2 手动启动

#### 步骤 1: 启动 Milvus

```bash
# 启动 Milvus (Docker Compose)
make up

# 或手动执行
docker-compose -f vector-database.yml up -d
```

#### 步骤 2: 构建项目

```bash
mvn clean install
```

#### 步骤 3: 运行应用

```bash
export DASHSCOPE_API_KEY=your-api-key
mvn spring-boot:run
```

### 2.4 验证服务

```bash
# 健康检查
curl http://localhost:9900/milvus/health

# 预期响应
{"message":"ok","collections":["biz"]}
```

---

## 3. 开发模式

### 3.1 跳过测试运行

```bash
mvn spring-boot:run -DskipTests
```

### 3.2 运行单个测试

```bash
mvn test -Dtest=ClassName
mvn test -Dtest=ClassName#methodName
```

### 3.3 Mock 模式 (无需外部服务)

如果无法访问 Prometheus 或腾讯云 CLS，可启用 Mock 模式：

编辑 `src/main/resources/application.yml`:

```yaml
prometheus:
  mock-enabled: true   # 启用 Prometheus Mock

cls:
  mock-enabled: true   # 启用 CLS Mock
```

启用后，系统将返回模拟数据，无需实际连接外部服务。

---

## 4. API 使用

### 4.1 普通对话

```bash
curl -X POST http://localhost:9900/api/chat \
  -H "Content-Type: application/json" \
  -d '{"id":"test-1","question":"你好"}'
```

### 4.2 流式对话 (SSE)

```bash
curl -N -X POST http://localhost:9900/api/chat_stream \
  -H "Content-Type: application/json" \
  -d '{"id":"test-1","question":"查询当前时间"}'
```

### 4.3 AIOps 告警分析

```bash
curl -N -X POST http://localhost:9900/api/ai_ops \
  -H "Content-Type: application/json" \
  -d '{"question":"分析最近的告警"}'
```

### 4.4 上传文档到向量库

```bash
curl -X POST http://localhost:9900/api/upload \
  -F "file=@./docs/example.txt"
```

### 4.5 清空会话

```bash
curl -X POST http://localhost:9900/api/chat/clear \
  -H "Content-Type: application/json" \
  -d '{"id":"test-1"}'
```

### 4.6 获取会话信息

```bash
curl http://localhost:9900/api/chat/session/test-1
```

---

## 5. 项目结构

```
SuperBizAgent/
├── src/main/java/org/example/
│   ├── controller/          # API 控制器
│   ├── service/             # 业务服务
│   ├── agent/tool/          # Agent 工具
│   ├── config/              # 配置类
│   ├── client/              # 客户端工厂
│   ├── dto/                 # 数据对象
│   └── constant/            # 常量
├── src/main/resources/
│   └── application.yml       # 主配置
├── src/test/java/           # 测试代码
├── docs/                    # 文档 (本指南所在目录)
├── aiops-docs/              # AIOps 相关文档
├── pom.xml                  # Maven 配置
├── vector-database.yml      # Milvus Docker Compose
├── Makefile                 # 构建命令
└── README.md                # 项目说明
```

---

## 6. 配置文件说明

### 6.1 环境变量

| 变量名 | 说明 | 示例 |
|--------|------|------|
| `DASHSCOPE_API_KEY` | 阿里云 DashScope API 密钥 | `sk-xxxxxxxxxx` |

### 6.2 application.yml 主要配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `server.port` | 9900 | 服务端口 |
| `file.upload.path` | `./uploads` | 上传目录 |
| `milvus.host` | localhost | Milvus 地址 |
| `milvus.port` | 19530 | Milvus 端口 |
| `dashscope.embedding.model` | text-embedding-v4 | Embedding 模型 |
| `rag.top-k` | 3 | 检索返回数量 |
| `rag.model` | qwen3-max | Chat 模型 |
| `prometheus.mock-enabled` | false | Prometheus Mock |
| `cls.mock-enabled` | false | CLS Mock |

---

## 7. 常见问题

### Q1: 启动报错 `DASHSCOPE_API_KEY` not found

**解决:**
```bash
export DASHSCOPE_API_KEY=your-api-key
mvn spring-boot:run
```

### Q2: Milvus 连接失败

**解决:**
```bash
# 检查 Milvus 容器是否运行
docker ps | grep milvus

# 重启 Milvus
docker-compose -f vector-database.yml restart
```

### Q3: 端口被占用

**解决:**
```bash
# 查看端口占用
netstat -ano | findstr :9900

# 修改端口 (application.yml)
server:
  port: 9901
```

### Q4: 上传文件失败

**解决:**
```bash
# 确保上传目录存在
mkdir -p ./uploads

# 检查目录权限
chmod 755 ./uploads
```

---

## 8. Docker 命令

```bash
# 停止服务
make stop

# 停止并删除容器
make down

# 查看日志
docker-compose -f vector-database.yml logs -f

# 进入 Milvus CLI
docker exec -it milvus milvus-server milvus-cli
```

---

## 9. 下一步

- 阅读 [项目分析报告](./PROJECT_ANALYSIS_REPORT.md) 深入了解架构
- 查看 `aiops-docs/` 目录了解 AIOps 功能详情
- 阅读 `src/main/java/` 源码了解具体实现
- 阅读 `Makefile` 了解所有可用的构建命令

---

## 10. 获取帮助

- 项目 README: `README.md`
- 技术问题: 查看 `CLAUDE.md` 了解更多项目约定
- 外部依赖文档:
  - [Spring AI Alibaba](https://github.com/alibaba/spring-ai-alibaba)
  - [Milvus Java SDK](https://github.com/milvus-io/milvus-sdk-java)
  - [DashScope](https://help.aliyun.com/zh/dashscope/)
