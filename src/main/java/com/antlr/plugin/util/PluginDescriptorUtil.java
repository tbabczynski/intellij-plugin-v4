package com.antlr.plugin.util;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Plugin metadata lookup via reflection only (no static internal API references).
 * <p>
 * Baseline &gt;= 262: {@code PluginDetailsService} ({@code findDetails}, {@code isDisabled}, ...).
 * Baseline &lt; 262: {@code PluginManagerCore.getPlugins()} / {@code isPluginInstalled} / {@code isDisabled}.
 * <p>
 * Same approach as database-tool's {@code PluginDescriptorUtil}.
 */
public final class PluginDescriptorUtil {
    private static final int PLUGIN_DETAILS_SERVICE_BASELINE = 262;
    private static final String IDE_PLUGINS_PKG = "com.intellij.ide.plugins.";

    private PluginDescriptorUtil() {
    }

    /**
     * @return {@code true} if the plugin is known to the IDE and not disabled by the user.
     */
    public static boolean isPluginEnabled(@NotNull PluginId pluginId) {
        if (usePluginDetailsService()) {
            Object details = findPluginDetails(pluginId);
            return details != null && !invokePluginDetailsServiceBoolean("isDisabled", pluginId);
        }
        return invokePluginManagerCoreBoolean("isPluginInstalled", pluginId)
                && !invokePluginManagerCoreBoolean("isDisabled", pluginId);
    }

    /**
     * @return {@code true} if the plugin is known to the IDE (installed), regardless of load state.
     */
    public static boolean isPluginInstalled(@NotNull PluginId pluginId) {
        if (usePluginDetailsService()) {
            return findPluginDetails(pluginId) != null;
        }
        return invokePluginManagerCoreBoolean("isPluginInstalled", pluginId);
    }

    /**
     * Disables a plugin by id. Requires IDE restart to take effect.
     *
     * @return {@code true} if the disable call completed without error
     */
    public static boolean disablePlugin(@NotNull String pluginId) {
        try {
            Class<?> managerClass = Class.forName(IDE_PLUGINS_PKG + "PluginManager");
            managerClass.getMethod("disablePlugin", String.class).invoke(null, pluginId);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    @Nullable
    public static String getPluginName(@NotNull PluginId pluginId) {
        Object details = findPluginDetails(pluginId);
        if (details != null) {
            return invokeStringMethod(details, "getName");
        }
        Object descriptor = findLegacyPluginDescriptor(pluginId);
        return descriptor != null ? invokeStringMethod(descriptor, "getName") : null;
    }

    @Nullable
    public static String getPluginVersion(@NotNull PluginId pluginId) {
        Object details = findPluginDetails(pluginId);
        if (details != null) {
            return invokeStringMethod(details, "getVersion");
        }
        Object descriptor = findLegacyPluginDescriptor(pluginId);
        return descriptor != null ? invokeStringMethod(descriptor, "getVersion") : null;
    }

    private static boolean usePluginDetailsService() {
        return getBaselineVersion() >= PLUGIN_DETAILS_SERVICE_BASELINE;
    }

    private static int getBaselineVersion() {
        return ApplicationInfo.getInstance().getBuild().getBaselineVersion();
    }

    @Nullable
    private static Object getPluginDetailsServiceInstance() {
        try {
            Class<?> serviceClass = Class.forName(IDE_PLUGINS_PKG + "PluginDetailsService");
            return serviceClass.getMethod("getInstance").invoke(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Nullable
    private static Object findPluginDetails(@NotNull PluginId pluginId) {
        if (!usePluginDetailsService()) {
            return null;
        }
        Object service = getPluginDetailsServiceInstance();
        if (service == null) {
            return null;
        }
        return invokeServiceMethod(service, "findDetails", pluginId);
    }

    private static boolean invokePluginDetailsServiceBoolean(@NotNull String methodName, @NotNull PluginId pluginId) {
        Object service = getPluginDetailsServiceInstance();
        if (service == null) {
            return false;
        }
        Object result = invokeServiceMethod(service, methodName, pluginId);
        return result instanceof Boolean && (Boolean) result;
    }

    @Nullable
    private static Object invokeServiceMethod(@NotNull Object service, @NotNull String methodName, @NotNull PluginId pluginId) {
        try {
            return service.getClass().getMethod(methodName, PluginId.class).invoke(service, pluginId);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Nullable
    private static Object findLegacyPluginDescriptor(@NotNull PluginId pluginId) {
        if (usePluginDetailsService()) {
            return null;
        }
        Object descriptor = findInPluginCollection(invokePluginManagerCoreList("getPlugins"), pluginId);
        if (descriptor != null) {
            return descriptor;
        }
        return findInPluginCollection(invokePluginManagerCoreList("getLoadedPlugins"), pluginId);
    }

    @Nullable
    private static Object findInPluginCollection(@Nullable Object plugins, @NotNull PluginId pluginId) {
        if (!(plugins instanceof Iterable<?> iterable)) {
            return null;
        }
        for (Object plugin : iterable) {
            Object currentId = invokeNoArgMethod(plugin, "getPluginId");
            if (pluginId.equals(currentId)) {
                return plugin;
            }
        }
        return null;
    }

    @Nullable
    private static Object invokePluginManagerCoreList(@NotNull String methodName) {
        try {
            Class<?> managerClass = Class.forName(IDE_PLUGINS_PKG + "PluginManagerCore");
            return managerClass.getMethod(methodName).invoke(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean invokePluginManagerCoreBoolean(@NotNull String methodName, @NotNull PluginId pluginId) {
        Object result = invokePluginManagerCore(methodName, pluginId);
        return result instanceof Boolean && (Boolean) result;
    }

    @Nullable
    private static Object invokePluginManagerCore(@NotNull String methodName, @NotNull PluginId pluginId) {
        try {
            Class<?> managerClass = Class.forName(IDE_PLUGINS_PKG + "PluginManagerCore");
            return managerClass.getMethod(methodName, PluginId.class).invoke(null, pluginId);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Nullable
    private static String invokeStringMethod(@NotNull Object target, @NotNull String methodName) {
        Object value = invokeNoArgMethod(target, methodName);
        return value != null ? value.toString() : null;
    }

    @Nullable
    private static Object invokeNoArgMethod(@NotNull Object target, @NotNull String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
