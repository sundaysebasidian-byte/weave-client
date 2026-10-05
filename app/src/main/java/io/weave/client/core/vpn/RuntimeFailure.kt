package io.weave.client.core.vpn

import io.weave.client.core.bridge.NativeCoreException
import io.weave.client.subscription.SubscriptionImportException

/**
 * Stable startup/runtime failure categories.
 *
 * [code] is the allowlisted identifier persisted by RecoveryVault. [message] is the Chinese UI
 * source text (localized at the rendering boundary). Neither may contain endpoint details.
 */
enum class RuntimeFailure(val code: String, val message: String) {
    SAFE_MODE("safe_mode_enabled", "恢复中心已启用安全模式，请解除后再连接"),
    CORE_UNAVAILABLE("native_core_unavailable", "代理内核无法加载，请重新安装应用"),
    SUBSCRIPTION_PARSE(
        "subscription_parse_failed",
        "订阅节点结构无法读取；原订阅已保留，请更新或重新导入这份订阅",
    ),
    NODE_CONFIGURATION(
        "node_configuration_failed",
        "订阅节点参数未通过内核校验，请更新订阅或检查协议、端口及认证字段；原订阅未删除",
    ),
    NO_SUBSCRIPTION("subscription_validation_failed", "没有可用订阅，请先导入或选择直连"),
    SUBSCRIPTION_MISSING("subscription_validation_failed", "所选订阅已不存在，请重新选择出口"),
    NODES_NOT_LOADED("subscription_validation_failed", "订阅节点未成功载入，请重新选择出口或更新订阅"),
    DNS_CONFIGURATION("dns_configuration_failed", "DNS 配置未通过校验，请检查自定义 DNS 地址或重新选择预设"),
    VPN_ROUTE_NOT_READY("system_vpn_route_not_ready", "系统 VPN 路由尚未就绪，请关闭其他 VPN 后重新连接"),
    VPN_INTERFACE("vpn_interface_failed", "系统拒绝建立 VPN，请重新授权后再试"),
    UNDERLYING_NETWORK("underlying_network_unavailable", "没有可用的 Wi‑Fi 或移动数据网络"),
    UNKNOWN("runtime_failure", "代理启动失败，VPN 未连接；请查看恢复中心的最近失败记录"),
    ;

    companion object {
        fun of(error: Throwable?): RuntimeFailure {
            if (error == null) return UNKNOWN
            if (error is RuntimeFailureException) return error.failure
            if (error is SubscriptionImportException) return SUBSCRIPTION_PARSE
            if (error is NoSuchElementException) return SUBSCRIPTION_MISSING
            val lower = error.message.orEmpty().lowercase()
            if (error is NativeCoreException) {
                return when {
                    "proxy" in lower || "proxies" in lower || "provider" in lower -> NODE_CONFIGURATION
                    "dns" in lower -> DNS_CONFIGURATION
                    else -> UNKNOWN
                }
            }
            // Validation errors from DNS/route compilers are plain IllegalArgumentExceptions.
            return if ("dns" in lower) DNS_CONFIGURATION else UNKNOWN
        }

        /** The allowlisted RecoveryVault code; unknown errors keep only a sanitized class name. */
        fun code(error: Throwable?): String {
            val failure = of(error)
            if (failure != UNKNOWN || error == null) return failure.code
            return error.javaClass.simpleName
                .replace(Regex("[^A-Za-z0-9_.-]"), "_")
                .take(64)
                .ifBlank { UNKNOWN.code }
        }
    }
}

class RuntimeFailureException(
    val failure: RuntimeFailure,
    message: String = failure.message,
) : IllegalStateException(message)

internal fun fail(failure: RuntimeFailure): Nothing = throw RuntimeFailureException(failure)
