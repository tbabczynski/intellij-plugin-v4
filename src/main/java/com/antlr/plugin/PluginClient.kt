package com.antlr.plugin

import com.intellij.openapi.extensions.PluginDescriptor

object PluginClient {
    /**
     * Non-bundled plugins for error reports. Uses reflection so we do not call
     * internal [com.intellij.ide.plugins.PluginManager] APIs at link time.
     */
    fun collectPlugin(): List<PluginDescriptor> {
        val plugins = loadPlugins() ?: return emptyList()
        val result = mutableListOf<PluginDescriptor>()
        for (plugin in plugins) {
            if (plugin !is PluginDescriptor) continue
            if (isBundled(plugin)) continue
            result.add(plugin)
        }
        return result
    }

    private fun loadPlugins(): Iterable<*>? {
        return try {
            val managerClass = Class.forName("com.intellij.ide.plugins.PluginManager")
            when (val value = managerClass.getMethod("getPlugins").invoke(null)) {
                is Array<*> -> value.asList()
                is Iterable<*> -> value
                else -> null
            }
        } catch (_: ReflectiveOperationException) {
            try {
                val coreClass = Class.forName("com.intellij.ide.plugins.PluginManagerCore")
                when (val value = coreClass.getMethod("getPlugins").invoke(null)) {
                    is Array<*> -> value.asList()
                    is Iterable<*> -> value
                    else -> null
                }
            } catch (_: ReflectiveOperationException) {
                null
            }
        }
    }

    private fun isBundled(plugin: Any): Boolean {
        return try {
            plugin.javaClass.getMethod("isBundled").invoke(plugin) as? Boolean ?: false
        } catch (_: ReflectiveOperationException) {
            false
        }
    }
}
