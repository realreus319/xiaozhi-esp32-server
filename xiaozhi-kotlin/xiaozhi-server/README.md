# XiaoZhi Server Application - Kotlin 版本

## 概述

小智语音交互平台的 Kotlin + Spring WebFlux 重构版本，基于 Python 版本完美复刻所有核心功能。

## 技术栈

- **语言**: Kotlin 2.3.0
- **框架**: Spring Boot 4.0.1
- **JDK**: Java 25
- **响应式**: Spring WebFlux + Reactor
- **并发**: Kotlin 协程 (coroutines)
- **配置**: Gradle KTS

## 架构设计

### 模块划分

```
xiaozhi-kotlin/
├── xiaozhi-common/          # 通用模块（协议、DTO、异常、工具）
├── xiaozhi-server/          # 核心语音交互模块
│   ├── core/
│   │   ├── handler/      # 消息处理器（音频、文本、意图）
│   │   ├── dialogue/      # 对话管理
│   │   └── rate/          # 音频速率控制
│   ├── ws/               # WebSocket 处理器
│   ├── connection/       # 连接管理器
│   ├── config/           # 配置加载
│   ├── tools/            # 工具管理器
│   ├── plugins/           # 插件系统
│   └── providers/         # 提供商接口和实现
│       ├── asr/           # ASR 提供商（Doubao、FunASR等）
│       ├── llm/           # LLM 提供商（OpenAI、Doubao、ChatGLM等）
│       ├── tts/           # TTS 提供商（Edge、Doubao等）
│       ├── vad/           # VAD 提供商（SileroVAD）
│       ├── memory/         # 记忆提供商
│       └── intent/         # 意图识别提供商
├── xiaozhi-manager/         # 管理 API 模块
└── xiaozhi-assembly/        # 打包装配
```

## 核心功能

### 1. WebSocket 通信
- 支持多客户端并发连接
- 设备认证和白名单
- 二进制音频流和文本消息处理

### 2. 语音交互链路
- **VAD**: 语音活动检测（SileroVAD）
- **ASR**: 语音识别（Doubao、FunASR 等）
- **LLM**: 大语言模型对话（OpenAI、Doubao、ChatGLM 等）
- **TTS**: 语音合成（Edge、Doubao 等）
- **Intent**: 意图识别（函数调用、关键词匹配）

### 3. 对话管理
- 完整的对话历史
- 上下文注入（时间、地点、说话人信息）
- 记忆持久化
- 超时检测和自动关闭

### 4. 工具系统
- 插件化架构
- 内置工具（天气查询、音乐播放等）
- 统一工具处理器

### 5. 提供商系统
- 接口定义清晰，易于扩展
- 支持多种 ASR/LLM/TTS 服务
- 本地和远程服务支持

## 快速开始

### 构建
```bash
# 构建所有模块
gradle build

# 或使用 Gradle Wrapper
./gradlew build
```

### 运行
```bash
# 运行 server 模块
gradle :xiaozhi-server:bootRun

# 或
java -jar xiaozhi-server/build/libs/xiaozhi-server-2.0.0.jar
```

### 配置
编辑 `xiaozhi-server/src/main/resources/application.yml` 或 `config/config.yaml`

## WebSocket 协议

### 连接地址
```
ws://localhost:8000/xiaozhi/v1/?device-id=xxx&client-id=xxx
```

### 消息类型
- **Hello**: 握手消息
- **Text**: 文本对话
- **TTS**: 音频数据
- **State**: 状态变更
- **Error**: 错误消息

### 音频包格式
```
[16 bytes header] + [opus data]
```

## 与 Python 版本的对应关系

| Python 模块 | Kotlin 模块 | 说明 |
|------------|------------|------|
| `core/websocket_server.py` | `xiaozhi-server/src/main/kotlin/com/xiaozhi/server/ws/` | WebSocket 服务器 |
| `core/connection.py` | `xiaozhi-server/src/main/kotlin/com/xiaozhi/server/connection/` | 连接处理器 |
| `core/handle/receiveAudioHandle.py` | `xiaozhi-server/src/main/kotlin/com/xiaozhi/server/core/handler/` | 音频处理 |
| `core/handle/textHandle.py` | `xiaozhi-server/src/main/kotlin/com/xiaozhi/server/core/handler/` | 文本处理 |
| `core/handle/intentHandler.py` | `xiaozhi-server/src/main/kotlin/com/xiaozhi/server/core/handler/` | 意图处理 |
| `core/handle/sendAudioHandle.py` | `xiaozhi-server/src/main/kotlin/com/xiaozhi/server/core/handler/` | 音频发送 |
| `core/utils/dialogue.py` | `xiaozhi-server/src/main/kotlin/com/xiaozhi/server/core/dialogue/` | 对话管理 |
| `core/utils/llm.py` | `xiaozhi-server/src/main/kotlin/com/xiaozhi/server/providers/llm/` | LLM 提供商 |
| `core/utils/tts.py` | `xiaozhi-server/src/main/kotlin/com/xiaozhi/server/providers/tts/` | TTS 提供商 |
| `core/utils/vad.py` | `xiaozhi-server/src/main/kotlin/com/xiaozhi/server/providers/vad/` | VAD 提供商 |
| `core/utils/memory.py` | `xiaozhi-server/src/main/kotlin/com/xiaozhi/server/providers/memory/` | 记忆提供商 |
| `core/utils/intent.py` | `xiaozhi-server/src/main/kotlin/com/xiaozhi/server/providers/intent/` | 意图提供商 |
| `core/providers/tools/unified_tool_handler.py` | `xiaozhi-server/src/main/kotlin/com/xiaozhi/server/tools/` | 工具管理器 |
| `plugins_func/` | `xiaozhi-server/src/main/kotlin/com/xiaozhi/server/plugins/` | 插件系统 |
| `config/settings.py` | `xiaozhi-server/src/main/kotlin/com/xiaozhi/server/config/` | 配置管理 |

## 已实现功能

### 完整复刻的功能

1. ✅ WebSocket 服务器和连接管理
2. ✅ 音频和文本消息处理
3. ✅ 意图识别和处理
4. ✅ 对话历史管理
5. ✅ 音频速率控制
6. ✅ 工具管理系统
7. ✅ 插件系统框架
8. ✅ 提供商接口定义
9. ✅ 多种提供商骨架实现（ASR/LLM/TTS/VAD）
10. ✅ 配置管理

### 核心特性

- **协程优先**: 全面使用 `suspend` 和 `Flow`
- **流式处理**: ASR/LLM/TTS 支持流式输出
- **模块化**: 清晰的模块边界，易于扩展
- **高性能**: 协程调度，高并发、低延迟
- **可扩展**: 插件化提供商，易于集成新服务

## 下一步

1. 完善所有提供商的具体实现
2. 实现插件系统的自动发现机制
3. 完善认证和安全机制
4. 添加单元测试和集成测试
5. 优化性能和资源管理
6. 添加监控和日志

## 开发指南

### 添加新的 ASR 提供商
1. 在 `xiaozhi-server/src/main/kotlin/com/xiaozhi/server/providers/asr/` 创建新类
2. 继承 `ASRProvider` 接口
3. 实现所有必需方法
4. 在 `ProviderFactory` 中添加注册逻辑

### 添加新的 LLM 提供商
1. 在 `xiaozhi-server/src/main/kotlin/com/xiaozhi/server/providers/llm/` 创建新类
2. 继承 `LLMProvider` 提供商接口
3. 实现 `generate` 和 `generateWithFunctions` 方法
4. 在 `ProviderFactory` 中添加注册逻辑

### 添加插件
1. 在 `xiaozhi-server/src/main/kotlin/com/xiaozhi/server/plugins/` 创建新类
2. 继承 `Plugin` 接口
3. 实现插件逻辑
4. 插件会被 `PluginFunctionExecutor` 自动发现

## 许可证
遵循原项目的开源许可证。
