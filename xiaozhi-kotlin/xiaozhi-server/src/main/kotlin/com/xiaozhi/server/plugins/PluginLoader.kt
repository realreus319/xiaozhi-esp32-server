package com.xiaozhi.server.plugins

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.LoggerFactory
import java.io.File

class PluginLoader(
    private val pluginDir: String = "plugins/functions"
) {
    private val logger = LoggerFactory.getLogger(PluginLoader::class.java)
    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build()

    private val loadedPlugins: MutableMap<String, Plugin> = mutableMapOf()

    fun loadPlugins() {
        val pluginDir = File(pluginDir)
        if (!pluginDir.exists()) {
            logger.warn("Plugin directory does not exist: $pluginDir")
            return
        }

        val pluginFiles = pluginDir.listFiles { 
            it.isFile && it.name.endsWith(".kt") 
        } ?: emptyArray()

        logger.info("Found ${pluginFiles.size} plugin files")

        pluginFiles.forEach { file ->
            try {
                val className = file.name.removeSuffix(".kt")
                val pluginClass = loadPluginClass(file, className)
                
                if (pluginClass != null) {
                    val plugin = instantiatePlugin(pluginClass)
                    loadedPlugins[className] = plugin
                    logger.info("Loaded plugin: $className")
                }
            } catch (e: Exception) {
                logger.error("Failed to load plugin from ${file.name}: ${e.message}", e)
            }
        }
    }

    private fun loadPluginClass(file: File, className: String): Class<out Plugin>? {
        return try {
            Thread.currentThread().contextClassLoader = Thread.currentThread().contextClassLoader
            Class.forName("com.xiaozhi.server.plugins.$className") as? Class<out Plugin>
        } catch (e: ClassNotFoundException) {
            logger.error("Plugin class not found: $className")
            null
        } catch (e: Exception) {
            logger.error("Error loading plugin class: $className", e)
            null
        }
    }

    private fun instantiatePlugin(pluginClass: Class<out Plugin>): Plugin? {
        return try {
            pluginClass.getDeclaredConstructor().newInstance() as Plugin
        } catch (e: Exception) {
            logger.error("Failed to instantiate plugin: ${pluginClass.name}", e)
            null
        }
    }

    fun getPlugin(name: String): Plugin? {
        return loadedPlugins[name]
    }

    fun getAllPlugins(): Map<String, Plugin> {
        return loadedPlugins.toMap()
    }
}

interface Plugin {
    val name: String
    val description: String
    val parameters: List<PluginParameter>

    suspend fun execute(arguments: Map<String, Any>): PluginResult

    fun initialize()
    fun cleanup()
}

data class PluginParameter(
    val name: String,
    val type: String,
    val required: Boolean = false,
    val description: String? = null
)

data class PluginResult(
    val success: Boolean,
    val response: String? = null,
    val error: String? = null
)
