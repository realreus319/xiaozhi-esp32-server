package com.xiaozhi.server.core.providers.memory

import com.xiaozhi.common.dto.DialogueMessage
import com.xiaozhi.server.core.providers.LLMProvider
import com.xiaozhi.server.core.providers.MemoryProvider
import org.slf4j.LoggerFactory

class DefaultMemoryProvider : MemoryProvider {
    private val logger = LoggerFactory.getLogger(DefaultMemoryProvider::class.java)
    
    private var roleId: String? = null
    private var summaryConfig: Map<String, Any>? = null
    private var llmProvider: LLMProvider? = null
    private var saveToFile: Boolean = true
    
    private val dialogueHistory = mutableListOf<DialogueMessage>()

    override suspend fun initMemory(
        roleId: String,
        summaryConfig: Map<String, Any>?,
        saveToFile: Boolean
    ) {
        this.roleId = roleId
        this.summaryConfig = summaryConfig
        this.saveToFile = saveToFile
        logger.info("MemoryProvider initialized for role: $roleId, saveToFile: $saveToFile")
    }

    override suspend fun saveMemory(dialogue: List<DialogueMessage>, sessionId: String) {
        if (saveToFile) {
            // TODO: 保存到文件
            logger.debug("Saving dialogue to file: session=$sessionId, messages=${dialogue.size}")
        }
    }

    override suspend fun queryMemory(query: String?): String? {
        // TODO: 实现记忆查询逻辑
        return null
    }

    override suspend fun setLLM(llm: LLMProvider) {
        this.llmProvider = llm
        logger.info("MemoryProvider LLM set: ${llm.javaClass.simpleName}")
    }

    override suspend fun close() {
        dialogueHistory.clear()
        logger.info("MemoryProvider closed")
    }
}

class SimpleMemoryProvider : MemoryProvider {
    private val logger = LoggerFactory.getLogger(SimpleMemoryProvider::class.java)
    
    private val memory = mutableMapOf<String, String>()
    
    override suspend fun initMemory(
        roleId: String,
        summaryConfig: Map<String, Any>?,
        saveToFile: Boolean
    ) {
        logger.info("SimpleMemoryProvider initialized for role: $roleId")
    }

    override suspend fun saveMemory(dialogue: List<DialogueMessage>, sessionId: String) {
        // 简化版：只保存最后几条对话
        for (msg in dialogue.takeLast(5)) {
            val key = "${sessionId}_${msg.role}"
            memory[key] = msg.content
        }
        logger.debug("Saved ${dialogue.size} messages to memory")
    }

    override suspend fun queryMemory(query: String?): String? {
        if (query.isNullOrBlank()) {
            return null
        }
        
        // TODO: 实现基于关键词的记忆检索
        return null
    }

    override suspend fun setLLM(llm: LLMProvider) {
        logger.info("SimpleMemoryProvider LLM set")
    }

    override suspend fun close() {
        memory.clear()
        logger.info("SimpleMemoryProvider closed")
    }
}
