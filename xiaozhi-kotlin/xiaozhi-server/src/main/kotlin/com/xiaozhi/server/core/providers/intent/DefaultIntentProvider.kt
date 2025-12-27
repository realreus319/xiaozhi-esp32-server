package com.xiaozhi.server.core.providers.intent

import com.xiaozhi.server.core.providers.IntentProvider
import com.xiaozhi.server.core.providers.IntentResult
import com.xiaozhi.server.core.providers.LLMProvider
import com.xiaozhi.server.core.providers.FunctionDef
import com.xiaozhi.server.core.providers.Message
import org.slf4j.LoggerFactory

class DefaultIntentProvider : IntentProvider {
    private val logger = LoggerFactory.getLogger(DefaultIntentProvider::class.java)
    
    private var llmProvider: LLMProvider? = null
    private var intentType: String = "nointent"
    
    override suspend fun initialize() {
        logger.info("DefaultIntentProvider initialized with type: $intentType")
    }

    override suspend fun close() {
        llmProvider = null
        logger.info("DefaultIntentProvider closed")
    }

    override suspend fun setLLM(llm: LLMProvider) {
        this.llmProvider = llm
        logger.info("DefaultIntentProvider LLM set: ${llm.javaClass.simpleName}")
    }

    override suspend fun detectIntent(text: String): IntentResult? {
        if (intentType == "nointent") {
            return null
        }
        
        // TODO: 实现意图识别逻辑
        return null
    }

    override fun getFunctions(): List<FunctionDef> {
        // 默认返回空列表
        return emptyList()
    }

    companion object {
        const val TYPE = "default"
    }
}

class FunctionCallIntentProvider : IntentProvider {
    private val logger = LoggerFactory.getLogger(FunctionCallIntentProvider::class.java)
    
    private var llmProvider: LLMProvider? = null
    private var systemPrompt: String = ""
    
    override suspend fun initialize() {
        logger.info("FunctionCallIntentProvider initialized")
    }

    override suspend fun close() {
        llmProvider = null
        logger.info("FunctionCallIntentProvider closed")
    }

    override suspend fun setLLM(llm: LLMProvider) {
        this.llmProvider = llm
        logger.info("FunctionCallIntentProvider LLM set: ${llm.javaClass.simpleName}")
    }

    override suspend fun detectIntent(text: String): IntentResult? {
        val llm = llmProvider ?: return null
        
        // 构建意图识别提示词
        val prompt = buildIntentPrompt()
        
        // 调用 LLM 进行意图识别
        val messages = listOf(
            Message(role = "system", content = prompt),
            Message(role = "user", content = text)
        )
        
        var detectedIntent: String? = null
        var detectedParams = mutableMapOf<String, Any>()
        
        try {
            llm.generate("", messages).collect { response ->
                // 解析 LLM 响应，提取 function_call
                if (response.contains("function_call")) {
                    // TODO: 解析 JSON 获取 function_call
                    detectedIntent = "function_call"
                }
            }
        } catch (e: Exception) {
            logger.error("Intent detection failed: ${e.message}", e)
            return null
        }
        
        if (detectedIntent != null) {
            return IntentResult(
                name = detectedIntent,
                confidence = 0.8f,
                parameters = detectedParams
            )
        }
        
        return null
    }

    override fun getFunctions(): List<FunctionDef> {
        // 返回可用的函数列表
        return listOf(
            FunctionDef(
                name = "get_time",
                description = "获取当前时间",
                arguments = """{"type": "object", "properties": {}}"""
            ),
            FunctionDef(
                name = "get_weather",
                description = "获取天气信息",
                arguments = """{"type": "object", "properties": {"location": {"type": "string", "description": "城市名称"}}}"""
            ),
            FunctionDef(
                name = "play_music",
                description = "播放音乐",
                arguments = """{"type": "object", "properties": {"song": {"type": "string", "description": "歌曲名称"}}}"""
            )
        )
    }

    private fun buildIntentPrompt(): String {
        return """【严格格式要求】你必须只能返回JSON格式，绝对不能返回任何自然语言！

你是一个意图识别助手。请分析用户的最后一句话，判断用户意图并调用相应的函数。

【重要规则】以下类型的查询请直接返回result_for_context，无需调用函数：
- 询问当前时间（如：现在几点、当前时间、查询时间等）
- 询问今天日期（如：今天几号、今天星期几、今天是什么日期等）
- 询问所在城市（如：我现在在哪里、你知道我在哪个城市吗等）
系统会根据上下文信息直接构建回答。

处理步骤：
1. 分析用户输入，确定用户意图
2. 检查是否为上述基础信息查询（时间、日期等），如是则返回result_for_context
3. 从可用函数列表中选择最匹配的函数
4. 如果找到匹配的函数，生成对应的function_call 格式
5. 如果没有找到匹配的函数，返回{"function_call": {"name": "continue_chat"}}

返回格式要求：
1. 必须返回纯JSON格式，不要包含任何其他文字
2. 必须包含function_call字段
3. function_call必须包含name字段
4. 如果函数需要参数，必须包含arguments字段

示例：
\`\`
用户: 现在几点了？
返回: {"function_call": {"name": "result_for_context"}}
\`\`
\`\`
用户: 当前电池电量是多少？
返回: {"function_call": {"name": "get_battery_level", "arguments": {"response_success": "当前电池电量为{value}%", "response_failure": "无法获取Battery的当前电量百分比"}}}
\`\`
\`\`
用户: 我想结束对话
返回: {"function_call": {"name": "handle_exit_intent", "arguments": {"say_goodbye": "goodbye"}}}
\`\`
\`\`
用户: 你好啊
返回: {"function_call": {"name": "continue_chat"}}
\`\`

注意：
1. 只返回JSON格式，不要包含任何其他文字
2. 优先检查用户查询是否为基础信息（时间、日期等），如是则返回{"function_call": {"name": "result_for_context"}}，不需要arguments参数
3. 如果没有找到匹配的函数，返回{"function_call": {"name": "continue_chat"}}
4. 确保返回的JSON格式正确，包含所有必要的字段
5. result_for_context不需要任何参数，系统会自动从上下文获取信息
特殊说明：
- 当用户单次输入包含多个指令时（如'打开灯并且调高音量'）
- 请返回多个function_call组成的JSON数组
- 示例：{'function_calls': [{name:'light_on'}, {name:'volume_up'}]}

【最终警告】绝对禁止输出任何自然语言、表情符号或解释文字！只能输出有效JSON格式！违反此规则将导致系统错误！"""
    }

    companion object {
        const val TYPE = "function_call"
    }
}
