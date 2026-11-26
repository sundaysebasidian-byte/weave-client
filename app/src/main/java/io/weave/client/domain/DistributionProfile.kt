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
    const val SILENT_APP_INSTALLS: Boolean = false
    const val TELEMETRY: Boolean = false
    const val CRASH_REPORTING: Boolean = false
    const val BUNDLED_PROXY_CREDENTIALS: Boolean = false
    const val USER_SELECTED_REMOTE_ENDPOINTS: Boolean = true

    const val disclosure: String =
        "Weave 不运营账号、云端控制、节点中继或遥测。Weave Next 可查询 GitHub 公开发布，不自行下载安装。第三方端点可能收到完成请求所需的数据。"
}
