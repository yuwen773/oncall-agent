# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SuperBizAgent is an enterprise-grade intelligent business agent system with two core modules:
- **RAG 智能问答**: Retrieval-augmented generation Q&A with Milvus vector database and Alibaba DashScope
- **AIOps 智能运维**: AI Agent-based automated operations using Planner-Executor-Replanner architecture

## Build & Run Commands

```bash
# Set environment variable
export DASHSCOPE_API_KEY=your-api-key

# One-time setup (starts Milvus, builds, runs, uploads docs)
make init

# Manual start
make up                              # Start Milvus via Docker Compose
mvn clean install                    # Build the project
mvn spring-boot:run                  # Run the application

# Development
mvn spring-boot:run -DskipTests      # Skip tests during development
mvn test -Dtest=ClassName            # Run single test class
mvn test -Dtest=ClassName#methodName  # Run single test method

# Docker
make stop && make down               # Stop services
```

## Architecture

### Core Flow
```
ChatController → ChatService → AiOpsService/RagService
                                      ↓
                              Spring AI Agent
                                      ↓
                    Planner → Executor → Replanner (AIOps)
                                      ↓
                              Tools (DateTime, QueryLogs, etc.)
```

### Key Services
- `ChatService`: Unified entry point for chat, routes to RAG or AIOps
- `AiOpsService`: Planner-Executor-Replanner agent for operations automation
- `RagService`: Vector search + LLM for document Q&A
- `VectorSearchService`: Milvus vector similarity search

### Agent Tools (src/main/java/org/example/agent/tool/)
- `DateTimeTools.java`: Time/date queries
- `QueryMetricsTools.java`: Alert/metrics queries (Prometheus)
- `QueryLogsTools.java`: Log queries (Tencent CLS via MCP)
- `InternalDocsTools.java`: Internal documentation search

### Configuration
- `application.yml`: Main config (server port 9900, Milvus, DashScope, RAG settings)
- `vector-database.yml`: Docker Compose for Milvus (ports 19530, 9091, Attu on 8000)
- `dashscope` section: Text embedding model (`text-embedding-v4`)
- `prometheus`/`cls` sections: Mock modes available for testing without external services

## API Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/chat` | POST | Non-streaming chat |
| `/api/chat_stream` | POST | SSE streaming chat |
| `/api/ai_ops` | POST | AIOps analysis (SSE) |
| `/api/chat/clear` | POST | Clear session history |
| `/api/chat/session/{id}` | GET | Get session info |
| `/api/upload` | POST | Upload file to vector DB |
| `/milvus/health` | GET | Milvus health check |

## Tech Stack

- Java 17, Spring Boot 3.2.0, Spring AI 1.1.0
- Spring AI Alibaba 1.1.0.0-RC2 (DashScope + Agent Framework)
- Milvus 2.6.x (vector database)
- Alibaba DashScope SDK 2.17.0 (LLM + embeddings)
- Spring AI MCP Client WebFlux (for Tencent CLS log integration)
