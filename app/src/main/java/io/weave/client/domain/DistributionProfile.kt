package io.weave.client.domain

/**
 * Declarative boundary for the public local-open-source build.
 *
 * These flags describe the Weave distribution itself; they do not claim that
 * user-selected subscriptions, proxy servers, DNS resolvers, or destinations
 * are local or do not retain data.
 */
object DistributionProfile {
    const val ID: String = "local-open-source"
    const val HOSTED_WEAVE_SERVICE: Boolean = false
    const val REMOTE_APP_UPDATES: Boolean = false
    const val TELEMETRY: Boolean = false
    const val CRASH_REPORTING: Boolean = false
    const val BUNDLED_PROXY_CREDENTIALS: Boolean = false
    const val USER_SELECTED_REMOTE_ENDPOINTS: Boolean = true

    val disclosure: String =
        "本地开源发行配置：Weave 不运营账户、云端控制、节点中继、遥测或远程更新；" +
            "用户主动选择的订阅、代理、DNS 和 IP 检测端点仍可能收到完成请求所需的数据。"
}
