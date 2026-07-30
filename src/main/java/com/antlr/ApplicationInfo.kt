package com.antlr

import com.antlr.plugin.util.PluginDescriptorUtil
import com.intellij.openapi.extensions.PluginId

object ApplicationInfo {
    @JvmField
    var PLUGIN_ID = "com.my.antlr.tool"

    @JvmField
    var VERSION = loadVersion()

    private fun loadVersion(): String? {
        return PluginDescriptorUtil.getPluginVersion(PluginId.getId(PLUGIN_ID))
    }
}
