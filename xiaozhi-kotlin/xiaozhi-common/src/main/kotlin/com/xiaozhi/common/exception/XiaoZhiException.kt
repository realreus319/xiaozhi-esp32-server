package com.xiaozhi.common.exception

open class XiaoZhiException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class AuthenticationException(message: String = "Authentication failed") : XiaoZhiException(message)

class ConfigurationException(message: String) : XiaoZhiException(message)

open class ProviderException(message: String, providerType: String, cause: Throwable? = null) :
    XiaoZhiException("[$providerType] $message", cause)

class ASRException(message: String, cause: Throwable? = null) :
    ProviderException(message, "ASR", cause)

class LLMException(message: String, cause: Throwable? = null) :
    ProviderException(message, "LLM", cause)

class TTSException(message: String, cause: Throwable? = null) :
    ProviderException(message, "TTS", cause)

class VADException(message: String, cause: Throwable? = null) :
    ProviderException(message, "VAD", cause)

class MemoryException(message: String, cause: Throwable? = null) :
    ProviderException(message, "Memory", cause)

class IntentException(message: String, cause: Throwable? = null) :
    ProviderException(message, "Intent", cause)

class ConnectionException(message: String, cause: Throwable? = null) : XiaoZhiException(message, cause)

class TimeoutException(message: String = "Operation timeout") : XiaoZhiException(message)
