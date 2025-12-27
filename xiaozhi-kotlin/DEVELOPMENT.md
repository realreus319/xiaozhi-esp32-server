# XiaoZhi Server - 开发文档

## 项目结构

```
xiaozhi-kotlin/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── xiaozhi-common/
│   └── src/main/kotlin/com/xiaozhi/common/
│       ├── AudioFormat.kt
│       ├── XiaoZhiConfig.kt
│       ├── protocol/
│       │   ├── WebsocketProtocol.kt
│       └── exception/
│       │       ├── XiaoZhiException.kt
│       │       └── utils/
│       │           └── AudioUtils.kt
├── xiaozhi-server/
│   ├── src/main/kotlin/com/xiaozhi/server/
│   │   ├── core/
│   │   │   ├── handler/
│   │   │   │   ├── Handler.kt
│   │   │   │   ├── AudioHandlerImpl.kt
│   │   │   │   ├── TextHandlerImpl.kt
│   │   │   │   └── IntentHandlerImpl.kt
│   │   │   ├── dialogue/
│   │   │   │   ├── DialogueManager.kt
│   │   │   │   └── DialogueContext.kt
│   │   │   ├── rate/
│   │   │   │   └── AudioRateController.kt
│   │   ├── ws/
│   │   │   │   ├── XiaoZhiWebSocketHandler.kt
│   │   │   │   └── WebSocketConfig.kt
│   │   ├── connection/
│   │   │   │   ├── ConnectionHandler.kt
│   │   │   │   ├── ConnectionManager.kt
│   │   │   │   └── ProviderService.kt
│   │   │   ├── config/
│   │   │   │   ├── XiaoZhiConfigProperties.kt
│   │   │   │   └── WebSocketConfig.kt
│   │   │   ├── tools/
│   │   │   │   ├── ToolManager.kt
│   │   │   │   ├── ServerPluginExecutor.kt
│   │   │   │   └── ToolExecutor.kt
│   │   │   ├── plugins/
│   │   │   │   ├── PluginLoader.kt
│   │   │   │   ├── PluginFunctionExecutor.kt
│   │   │   │   ├── builtin/
│   │   │   │   │   └── BuiltinPlugins.kt
│   │   │   └── providers/
│   │   │       ├── Providers.kt
│   │   │       ├── asr/
│   │   │       │   ├── DoubaoASRProvider.kt
│   │   │       │   ├── FunASRProvider.kt
│   │   │       ├── llm/
│   │   │       │   ├── DoubaoLLMProvider.kt
│   │   │       │   ├── OpenAILLMProvider.kt
│   │   │       │   └── IntentProvider.kt
│   │   │       ├── tts/
│   │   │       │   ├── EdgeTTSProvider.kt
│   │   │       │   ├── DoubaoTTSProvider.kt
│   │   │       │   └── VAD/
│   │   │       │   │   └── SileroVADProvider.kt
│   │   │       ├── dto/
│   │   │       │   └── ProviderFactory.kt
│   │   │   └── XiaoZhiServerApplication.kt
│   └── src/main/resources/
│       └── application.yml
└── xiaozhi-manager/
└── xiaozhi-assembly/
```

## API 文档

### WebSocket 接口

#### 连接
```
ws://localhost:8000/xiaozhi/v1/?device-id=xxx&client-id=xxx
```

Headers:
- `device-id`: 设备 ID（必需）
- `client-id`: 客户端 ID（可选）
- `authorization`: Bearer token（可选，开启认证后必需）

#### 消息类型

**Hello 消息**
```json
{
  "type": "hello",
  "version": 1,
  "transport": "websocket",
  "audioParams": {
    "format": "opus",
    "sampleRate": 16000,
    "channels": 1,
    "frameDuration": 60
  },
  "sessionId": "uuid"
}
```

**Text 消息**
```json
{
  "type": "text",
  "text": "你好",
  "session_id": "uuid"
}
```

**TTS 消息**
```json
{
  "type": "tts",
  "state": "start|stop",
  "text": "文本内容",
  "session_id": "uuid"
}
```

**State 消息**
```json
{
  "type": "state",
  "state": "listening|processing|speaking",
  "session_id": "uuid"
}
```

**Error 消息**
```json
{
  "type": "error",
  "error": "ERROR_TYPE",
  "message": "错误描述",
  "timestamp": 1234567890
}
```

## 开发指南

### 1. 添加新的 ASR 提供商

创建文件：`xiaozhi-server/src/main/kotlin/com/xiaozhi/server/providers/asr/NewASRProvider.kt`

```kotlin
import com.xiaozhi.server.providers.ASRProvider
import kotlinx.coroutines.flow.Flow

class NewASRProvider : ASRProvider {
    private val logger = LoggerFactory.getLogger(NewASRProvider::class.java)
    
    private var isInitialized = false
    
    override suspend fun initialize() {
        // 初始化逻辑
        isInitialized = true
        logger.info("NewASR provider initialized")
    }
    
    override suspend fun openAudioChannels() {
        // 打开音频通道
        logger.info("Audio channels opened")
    }
    
    override suspend fun close() {
        isInitialized = false
        logger.info("Provider closed")
    }
    
    override suspend fun transcribe(audioData: ByteArray): String? {
        // 转录音频
        return null
    }
    
    override fun transcribeStream(audioFlow: Flow<ByteArray>): Flow<String?> {
        return audioFlow.map { null }
    }
    
    companion object {
        const val TYPE = "new_asr"
    }
}
```

在 `ProviderFactory.kt` 中注册：
```kotlin
"new_asr" -> NewASRProvider().apply { ... }
```

### 2. 添加新的 LLM 提供商

创建文件：`xiaozhi-server/src/main/kotlin/com/xiaozhi/server/providers/llm/NewLLMProvider.kt`

```kotlin
import com.xiaozhi.server.providers.LLMProvider
import kotlinx.coroutines.flow.Flow

class NewLLMProvider : LLMProvider {
    private val logger = LoggerFactory.getLogger(NewLLMProvider::class.java)
    
    private var isInitialized = false
    
    override suspend fun initialize() {
        isInitialized = true
        logger.info("NewLLM provider initialized")
    }
    
    override suspend fun close() {
        isInitialized = false
    }
    
    override suspend fun generate(
        sessionId: String,
        messages: List<Message>
    ): Flow<String> {
        return flowOf("回复内容")
    }
    
    override suspend fun generateWithFunctions(
        sessionId: String,
        messages: List<Message>,
        functions: List<FunctionDef>
    ): Flow<LLMResponse> {
        return flowOf(LLMResponse("回复内容", emptyList()))
    }
    
    companion object {
        const val TYPE = "new_llm"
    }
}
```

### 3. 添加插件

创建文件：`xiaozhi-server/src/main/kotlin/com/xiaozhi/server/plugins/NewPlugin.kt`

```kotlin
import com.xiaozhi.server.plugins.Plugin
import kotlinx.serialization.Serializable

class NewPlugin : Plugin {
    override val name = "new_plugin"
    override val description = "插件描述"
    override val parameters = listOf(
        PluginParameter(
            name = "param1",
            type = "string",
            required = true,
            description = "参数1描述"
        )
    )
    
    override suspend fun initialize() {}
    override fun cleanup() {}
    
    override suspend fun execute(arguments: Map<String, Any>): PluginResult {
        return PluginResult(
            success = true,
            response = "执行结果"
        )
    }
}
```

### 4. 运行测试

```bash
# 运行服务
gradle :xiaozhi-server:bootRun

# 构建所有模块
gradle clean build
```

## 配置说明

### application.yml
```yaml
server:
  port: 8000

xiaozhi:
  config-path: config/config.yaml
  data-dir: data

logging:
  level:
    com.xiaozhi: DEBUG
```

### config.yaml
完整配置示例见 Python 版本的 `config.yaml`

## 故障排查

### �见问题

1. **连接失败**
   - 检查端口是否被占用
   - 检查防火墙设置
   - 检查认证配置

2. **音频处理延迟**
   - 调整 `AudioRateController` 参数
   - 检查网络连接
   - 优化 WebSocket 心跳保活

3. **TTS 错误**
   - 检查提供商 API 密钥
   - 检查网络连接
   - 查看 TTS 日志

4. **内存泄漏**
   - 监控 `ConnectionManager` 的连接数
   - 确保连接正确关闭
   - 检查协程是否正确取消

## 性能优化

### 并发处理
- 使用 `Channel` 进行并发通信
- 使用协程调度器优化
- 合理配置线程池大小

### 资源管理
- 定期清理不活跃连接
- 及时释放音频缓冲
- 限制并发音频处理数量

## 贡献指南

1. Fork 项目
2. 创建功能分支
3. 提交更改
4. 推送到主分支
5. 创建 Pull Request

## 许可证
MIT License
