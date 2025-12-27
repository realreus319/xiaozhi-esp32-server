# XiaoZhi Server - Kotlin + Spring WebFlux 版本

## 架构设计

### 模块化单体架构（Modular Monolith）

```
xiaozhi-server/
├── xiaozhi-common/          # 通用模块（协议、DTO、错误码）
├── xiaozhi-server/          # 核心语音交互模块（可多实例部署）
├── xiaozhi-manager/         # 管理模块（业务接口、配置管理）
└── xiaozhi-assembly/        # 打包装配模块
```

### 核心特性

1. **技术栈**
   - Kotlin 2.1.0
   - Spring Boot 4.0.0-M5
   - Java 25
   - 全面使用 Kotlin 协程（suspend / Flow）
   - Spring WebFlux 响应式编程
   - Reactor + 协程无缝集成

2. **语音交互链路**
   - WebSocket 高并发连接管理
   - VAD（语音活动检测）
   - ASR（语音识别）- 支持多种提供商
   - LLM（大语言模型）- 支持多种模型
   - TTS（文本转语音）- 支持流式输出
   - 记忆管理（Memory）
   - 意图识别（Intent）

3. **架构优势**
   - **模块化单体**：清晰的模块边界，便于未来拆分为微服务
   - **协程优先**：避免直接使用 Mono/Flux，代码更简洁
   - **高性能**：充分利用协程调度能力，高并发、低延迟
   - **可扩展**：提供商模式，易于接入新的 ASR/LLM/TTS 服务

### 模块说明

#### xiaozhi-common
- 通用数据结构（Protocol / DTO）
- 事件模型（Events）
- 错误码（Error Codes）
- 常量定义（Constants）
- 工具类（Utils）

#### xiaozhi-server
- WebSocket 服务器
- 连接处理器
- VAD / ASR / LLM / TTS 核心组件
- 对话管理
- 记忆管理
- 意图识别
- 插件系统

#### xiaozhi-manager
- 设备管理
- 配置管理
- 用户管理
- 统计分析
- API 网关

#### xiaozhi-assembly
- 打包配置
- Docker 镜像构建
- 部署脚本

## 快速开始

### 构建项目
```bash
mvn clean package
```

### 运行服务
```bash
java -jar xiaozhi-assembly/target/xiaozhi-assembly-2.0.0.jar
```

### Docker 部署
```bash
docker build -t xiaozhi-server:2.0.0 .
docker run -p 8000:8000 -p 8003:8003 xiaozhi-server:2.0.0
```

## 配置说明

参考原 Python 版本的 `config.yaml`，配置文件位置：
- `config/config.yaml`：默认配置
- `data/.config.yaml`：覆盖配置（优先级更高）

## 与原 Python 版本的对应关系

| Python 模块 | Kotlin 模块 | 说明 |
|------------|------------|------|
| `core/websocket_server.py` | `xiaozhi-server.ws.WebSocketServer` | WebSocket 服务器 |
| `core/connection.py` | `xiaozhi-server.connection.ConnectionHandler` | 连接处理器 |
| `core/utils/asr.py` | `xiaozhi-server.asr.ASRProvider` | ASR 提供商 |
| `core/utils/llm.py` | `xiaozhi-server.llm.LLMProvider` | LLM 提供商 |
| `core/utils/tts.py` | `xiaozhi-server.tts.TTSProvider` | TTS 提供商 |
| `core/utils/vad.py` | `xiaozhi-server.vad.VADProvider` | VAD 提供商 |
| `core/utils/memory.py` | `xiaozhi-server.memory.MemoryProvider` | 记忆提供商 |
| `core/utils/intent.py` | `xiaozhi-server.intent.IntentProvider` | 意图提供商 |
| `config/settings.py` | `xiaozhi-common.config.XiaoZhiConfig` | 配置管理 |
| `plugins_func/` | `xiaozhi-server.plugins` | 插件系统 |

## 贡献指南

欢迎提交 Issue 和 Pull Request！

## 许可证

遵循原项目的开源许可证。
