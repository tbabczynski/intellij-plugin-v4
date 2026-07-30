package com.antlr

import com.antlr.plugin.util.PluginDescriptorUtil
import com.intellij.openapi.extensions.PluginId

object ApplicationInfo {
    @JvmField
    val PLUGIN_ID: String = "com.my.antlr.tool"

    /**
     * Resolve on demand — must not run during {@code <clinit>} (PluginDetailsService is a service).
     */
    @JvmStatic
    fun getVersion(): String? {
        return PluginDescriptorUtil.getPluginVersion(PluginId.getId(PLUGIN_ID))
    }
}
