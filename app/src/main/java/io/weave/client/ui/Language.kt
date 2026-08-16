package io.weave.client.ui

import androidx.compose.runtime.staticCompositionLocalOf
import io.weave.client.domain.WeaveLanguage
import java.util.concurrent.ConcurrentHashMap

/** The UI language is app-local and does not change the device-wide locale. */
val LocalWeaveLanguage = staticCompositionLocalOf { WeaveLanguage.SIMPLIFIED_CHINESE }

/**
 * Translates stable UI labels at the rendering boundary. User data (app names, node names,
 * subscription names and URLs) is intentionally left untouched. The map is deliberately keyed by
 * the existing Chinese source text so older persisted labels and runtime error messages remain
 * compatible while the Compose UI is migrated incrementally to resources.
 */
fun localizeWeaveText(text: String, language: WeaveLanguage): String {
    if (language == WeaveLanguage.SIMPLIFIED_CHINESE || text.isBlank()) return text
    translationTable(language)[text]?.let { return it }
    return translateCommonPatterns(text, language) ?: text
}

private fun translateCommonPatterns(text: String, language: WeaveLanguage): String? {
    Regex("^(.+) 缺少 ?(UUID|V2Ray settings|加密方式或密码|密码|服务器列表|服务器地址|端口)$").matchEntire(text)?.let { match ->
        val owner = match.groupValues[1]
        val field = match.groupValues[2]
        val localizedField = when (field) {
            "UUID" -> when (language) {
                WeaveLanguage.TRADITIONAL_CHINESE -> "UUID"
                WeaveLanguage.ENGLISH -> "UUID"
                WeaveLanguage.JAPANESE -> "UUID"
                WeaveLanguage.FRENCH -> "UUID"
                WeaveLanguage.GERMAN -> "UUID"
                WeaveLanguage.SIMPLIFIED_CHINESE -> field
            }
            "V2Ray settings" -> when (language) {
                WeaveLanguage.TRADITIONAL_CHINESE -> "V2Ray 設定"
                WeaveLanguage.ENGLISH -> "V2Ray settings"
                WeaveLanguage.JAPANESE -> "V2Ray 設定"
                WeaveLanguage.FRENCH -> "paramètres V2Ray"
                WeaveLanguage.GERMAN -> "V2Ray-Einstellungen"
                WeaveLanguage.SIMPLIFIED_CHINESE -> field
            }
            "加密方式或密码" -> when (language) {
                WeaveLanguage.TRADITIONAL_CHINESE -> "加密方式或密碼"
                WeaveLanguage.ENGLISH -> "encryption method or password"
                WeaveLanguage.JAPANESE -> "暗号方式またはパスワード"
                WeaveLanguage.FRENCH -> "méthode de chiffrement ou mot de passe"
                WeaveLanguage.GERMAN -> "Verschlüsselungsmethode oder Passwort"
                WeaveLanguage.SIMPLIFIED_CHINESE -> field
            }
            "密码" -> when (language) {
                WeaveLanguage.TRADITIONAL_CHINESE -> "密碼"
                WeaveLanguage.ENGLISH -> "password"
                WeaveLanguage.JAPANESE -> "パスワード"
                WeaveLanguage.FRENCH -> "mot de passe"
                WeaveLanguage.GERMAN -> "Passwort"
                WeaveLanguage.SIMPLIFIED_CHINESE -> field
            }
            "服务器列表" -> when (language) {
                WeaveLanguage.TRADITIONAL_CHINESE -> "伺服器清單"
                WeaveLanguage.ENGLISH -> "server list"
                WeaveLanguage.JAPANESE -> "サーバー一覧"
                WeaveLanguage.FRENCH -> "liste des serveurs"
                WeaveLanguage.GERMAN -> "Serverliste"
                WeaveLanguage.SIMPLIFIED_CHINESE -> field
            }
            "服务器地址" -> when (language) {
                WeaveLanguage.TRADITIONAL_CHINESE -> "伺服器位址"
                WeaveLanguage.ENGLISH -> "server address"
                WeaveLanguage.JAPANESE -> "サーバーアドレス"
                WeaveLanguage.FRENCH -> "adresse du serveur"
                WeaveLanguage.GERMAN -> "Serveradresse"
                WeaveLanguage.SIMPLIFIED_CHINESE -> field
            }
            else -> when (language) {
                WeaveLanguage.TRADITIONAL_CHINESE -> "連接埠"
                WeaveLanguage.ENGLISH -> "port"
                WeaveLanguage.JAPANESE -> "ポート"
                WeaveLanguage.FRENCH -> "port"
                WeaveLanguage.GERMAN -> "Port"
                WeaveLanguage.SIMPLIFIED_CHINESE -> field
            }
        }
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "$owner 缺少 $localizedField"
            WeaveLanguage.ENGLISH -> "$owner is missing $localizedField"
            WeaveLanguage.JAPANESE -> "$owner に $localizedField がありません"
            WeaveLanguage.FRENCH -> "$owner n’a pas de $localizedField"
            WeaveLanguage.GERMAN -> "$owner benötigt $localizedField"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^(ss|ssr|vmess|vless|trojan|hysteria2?|tuic|wireguard|socks5?|http|shadowtls) 节点地址格式无效$").matchEntire(text)?.let { match ->
        val scheme = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "$scheme 節點位址格式無效"
            WeaveLanguage.ENGLISH -> "$scheme node address format is invalid"
            WeaveLanguage.JAPANESE -> "$scheme ノードのアドレス形式が無効です"
            WeaveLanguage.FRENCH -> "Format d’adresse du nœud $scheme invalide"
            WeaveLanguage.GERMAN -> "Ungültiges Adressformat für $scheme-Knoten"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^(ss|ssr|vmess|vless|trojan|hysteria2?|tuic|wireguard|socks5?|http|shadowtls) 节点$").matchEntire(text)?.let { match ->
        val scheme = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "$scheme 節點"
            WeaveLanguage.ENGLISH -> "$scheme node"
            WeaveLanguage.JAPANESE -> "$scheme ノード"
            WeaveLanguage.FRENCH -> "Nœud $scheme"
            WeaveLanguage.GERMAN -> "$scheme-Knoten"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^(ss|ssr|vmess|vless|trojan|hysteria2?|tuic|wireguard|socks5?|http|shadowtls) 节点 (\\d+)$").matchEntire(text)?.let { match ->
        val scheme = match.groupValues[1]
        val index = match.groupValues[2]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "$scheme 節點 $index"
            WeaveLanguage.ENGLISH -> "$scheme node $index"
            WeaveLanguage.JAPANESE -> "$scheme ノード $index"
            WeaveLanguage.FRENCH -> "Nœud $scheme $index"
            WeaveLanguage.GERMAN -> "$scheme-Knoten $index"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^暂不支持将 (.+) URI 转换为 Mihomo$").matchEntire(text)?.let { match ->
        val scheme = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "目前不支援將 $scheme URI 轉換為 Mihomo"
            WeaveLanguage.ENGLISH -> "Converting $scheme URIs to Mihomo is not supported yet"
            WeaveLanguage.JAPANESE -> "$scheme URI の Mihomo への変換には未対応です"
            WeaveLanguage.FRENCH -> "La conversion des URI $scheme vers Mihomo n’est pas encore prise en charge"
            WeaveLanguage.GERMAN -> "Die Umwandlung von $scheme-URIs in Mihomo wird noch nicht unterstützt"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^(V2Ray|sing-box) (.+) 出站暂不支持安全转换$").matchEntire(text)?.let { match ->
        val family = match.groupValues[1]
        val protocol = match.groupValues[2]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "$family $protocol 出站目前不支援安全轉換"
            WeaveLanguage.ENGLISH -> "Safe conversion of $family $protocol outbounds is not supported yet"
            WeaveLanguage.JAPANESE -> "$family $protocol outbound の安全な変換には未対応です"
            WeaveLanguage.FRENCH -> "La conversion sécurisée des sorties $family $protocol n’est pas encore prise en charge"
            WeaveLanguage.GERMAN -> "Die sichere Umwandlung von $family-$protocol-Ausgängen wird noch nicht unterstützt"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^· (\\d+) 个同名同协议节点未自动删除$").matchEntire(text)?.let { match ->
        val count = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "· $count 個同名同協定節點未自動刪除"
            WeaveLanguage.ENGLISH -> "· $count same-name, same-protocol nodes were not removed automatically"
            WeaveLanguage.JAPANESE -> "· 同名・同プロトコルのノード $count 件は自動削除されませんでした"
            WeaveLanguage.FRENCH -> "· $count nœuds de même nom et protocole n’ont pas été supprimés automatiquement"
            WeaveLanguage.GERMAN -> "· $count Knoten mit gleichem Namen und Protokoll wurden nicht automatisch entfernt"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^「(.+)」不是远程 HTTPS 订阅，已跳过自动刷新$").matchEntire(text)?.let { match ->
        val name = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "「$name」不是遠端 HTTPS 訂閱，已略過自動重新整理"
            WeaveLanguage.ENGLISH -> "“$name” is not a remote HTTPS subscription; automatic refresh skipped"
            WeaveLanguage.JAPANESE -> "「$name」はリモート HTTPS 購読ではないため、自動更新をスキップしました"
            WeaveLanguage.FRENCH -> "« $name » n’est pas un abonnement HTTPS distant ; actualisation automatique ignorée"
            WeaveLanguage.GERMAN -> "„$name“ ist kein HTTPS-Fernabonnement; automatische Aktualisierung übersprungen"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^已连接 · (\\d+) 个订阅$").matchEntire(text)?.let { match ->
        val count = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "已連線 · $count 個訂閱"
            WeaveLanguage.ENGLISH -> "Connected · $count subscriptions"
            WeaveLanguage.JAPANESE -> "接続済み · 購読 $count 件"
            WeaveLanguage.FRENCH -> "Connecté · $count abonnements"
            WeaveLanguage.GERMAN -> "Verbunden · $count Abonnements"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^(传输内容|订阅内容|粘贴内容)超过 (\\d+) (MiB|KiB) 限制$").matchEntire(text)?.let { match ->
        val kind = match.groupValues[1]
        val size = match.groupValues[2]
        val unit = match.groupValues[3]
        val englishKind = when (kind) {
            "传输内容" -> "Transfer content"
            "订阅内容" -> "Subscription content"
            else -> "Pasted content"
        }
        val localizedKind = when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> when (kind) {
                "传输内容" -> "傳輸內容"
                "订阅内容" -> "訂閱內容"
                else -> "貼上內容"
            }
            WeaveLanguage.JAPANESE -> when (kind) {
                "传输内容" -> "転送内容"
                "订阅内容" -> "購読内容"
                else -> "貼り付け内容"
            }
            WeaveLanguage.FRENCH -> when (kind) {
                "传输内容" -> "Contenu du transfert"
                "订阅内容" -> "Contenu de l’abonnement"
                else -> "Contenu collé"
            }
            WeaveLanguage.GERMAN -> when (kind) {
                "传输内容" -> "Übertragungsinhalt"
                "订阅内容" -> "Abonnementinhalt"
                else -> "Eingefügter Inhalt"
            }
            WeaveLanguage.ENGLISH, WeaveLanguage.SIMPLIFIED_CHINESE -> englishKind
        }
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "${localizedKind}超過 $size $unit 限制"
            WeaveLanguage.ENGLISH -> "$englishKind exceeds the $size $unit limit"
            WeaveLanguage.JAPANESE -> "${localizedKind}が $size $unit の上限を超えています"
            WeaveLanguage.FRENCH -> "$localizedKind dépasse la limite de $size $unit"
            WeaveLanguage.GERMAN -> "$localizedKind überschreitet das Limit von $size $unit"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^请选择 1–(\\d+) 个订阅$").matchEntire(text)?.let { match ->
        val max = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "請選擇 1–$max 個訂閱"
            WeaveLanguage.ENGLISH -> "Select 1–$max subscriptions"
            WeaveLanguage.JAPANESE -> "購読を 1～$max 件選択してください"
            WeaveLanguage.FRENCH -> "Sélectionnez 1 à $max abonnements"
            WeaveLanguage.GERMAN -> "Wähle 1–$max Abonnements"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^策略包规则数量必须为 1–(\\d+)$").matchEntire(text)?.let { match ->
        val max = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "策略包規則數量必須為 1–$max"
            WeaveLanguage.ENGLISH -> "A policy pack must contain 1–$max rules"
            WeaveLanguage.JAPANESE -> "ポリシーパックのルール数は 1～$max 件である必要があります"
            WeaveLanguage.FRENCH -> "Un pack de règles doit contenir 1 à $max règles"
            WeaveLanguage.GERMAN -> "Ein Richtlinienpaket muss 1–$max Regeln enthalten"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^第 (\\d+) 条(.+)$").matchEntire(text)?.let { match ->
        val index = match.groupValues[1]
        val reason = match.groupValues[2].trimStart()
        val englishReason = when {
            reason == "规则不是对象" -> " rule is not an object"
            reason == "规则值包含非法分隔符" -> " rule value contains an invalid separator"
            reason == "规则值无效" -> " rule value is invalid"
            reason == "规则动作不支持" -> " rule action is unsupported"
            reason == "规则类型不支持" -> " rule type is unsupported"
            reason == "域名无效" -> " domain is invalid"
            reason == "CIDR 无效" -> " CIDR is invalid"
            reason == "进程名无效" -> " process name is invalid"
            else -> " rule is invalid"
        }
        val traditionalReason = when {
            reason == "规则不是对象" -> "規則不是物件"
            reason == "规则值包含非法分隔符" -> "規則值包含無效分隔符"
            reason == "规则值无效" -> "規則值無效"
            reason == "规则动作不支持" -> "規則動作不支援"
            reason == "规则类型不支持" -> "規則類型不支援"
            reason == "域名无效" -> "網域無效"
            reason == "CIDR 无效" -> "CIDR 無效"
            reason == "进程名无效" -> "程序名稱無效"
            else -> "規則值無效"
        }
        val japaneseReason = when {
            reason == "规则不是对象" -> "ルールはオブジェクトではありません"
            reason == "规则值包含非法分隔符" -> "ルール値に無効な区切り文字があります"
            reason == "规则值无效" -> "ルール値が無効です"
            reason == "规则动作不支持" -> "ルールアクションは未対応です"
            reason == "规则类型不支持" -> "ルール種別は未対応です"
            reason == "域名无效" -> "ドメインが無効です"
            reason == "CIDR 无效" -> "CIDR が無効です"
            reason == "进程名无效" -> "プロセス名が無効です"
            else -> "ルールが無効です"
        }
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "第 $index 條$traditionalReason"
            WeaveLanguage.ENGLISH -> "Rule $index$englishReason"
            WeaveLanguage.JAPANESE -> "$index 件目：$japaneseReason"
            WeaveLanguage.FRENCH -> "Règle $index$englishReason"
            WeaveLanguage.GERMAN -> "Regel $index$englishReason"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^Mihomo 原生库复制不完整：(.*)$").matchEntire(text)?.let { match ->
        val name = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "Mihomo 原生函式庫複製不完整：$name"
            WeaveLanguage.ENGLISH -> "Mihomo native library copy is incomplete: $name"
            WeaveLanguage.JAPANESE -> "Mihomo ネイティブライブラリのコピーが不完全です：$name"
            WeaveLanguage.FRENCH -> "La copie de la bibliothèque native Mihomo est incomplète : $name"
            WeaveLanguage.GERMAN -> "Die Kopie der nativen Mihomo-Bibliothek ist unvollständig: $name"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^订阅服务器返回 HTTP (\\d+)$").matchEntire(text)?.let { match ->
        val status = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "訂閱伺服器回傳 HTTP $status"
            WeaveLanguage.ENGLISH -> "The subscription server returned HTTP $status"
            WeaveLanguage.JAPANESE -> "購読サーバーが HTTP $status を返しました"
            WeaveLanguage.FRENCH -> "Le serveur d’abonnement a renvoyé HTTP $status"
            WeaveLanguage.GERMAN -> "Der Abonnementserver gab HTTP $status zurück"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^发现未支持的节点格式：(.*)$").matchEntire(text)?.let { match ->
        val value = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "發現不支援的節點格式：$value"
            WeaveLanguage.ENGLISH -> "Unsupported node format found: $value"
            WeaveLanguage.JAPANESE -> "未対応のノード形式：$value"
            WeaveLanguage.FRENCH -> "Format de nœud non pris en charge : $value"
            WeaveLanguage.GERMAN -> "Nicht unterstütztes Knotenformat: $value"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^最多保存 (\\d+) 条本地规则$").matchEntire(text)?.let { match ->
        val max = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "最多保存 $max 條本機規則"
            WeaveLanguage.ENGLISH -> "You can save up to $max local rules"
            WeaveLanguage.JAPANESE -> "ローカルルールは最大 $max 件保存できます"
            WeaveLanguage.FRENCH -> "Vous pouvez enregistrer jusqu’à $max règles locales"
            WeaveLanguage.GERMAN -> "Bis zu $max lokale Regeln können gespeichert werden"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^新增 (\\d+) · 移除 (\\d+) · 保留 (\\d+)$").matchEntire(text)?.let { match ->
        val added = match.groupValues[1]
        val removed = match.groupValues[2]
        val kept = match.groupValues[3]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "新增 $added · 移除 $removed · 保留 $kept"
            WeaveLanguage.ENGLISH -> "Added $added · removed $removed · kept $kept"
            WeaveLanguage.JAPANESE -> "追加 $added · 削除 $removed · 保持 $kept"
            WeaveLanguage.FRENCH -> "Ajoutés $added · supprimés $removed · conservés $kept"
            WeaveLanguage.GERMAN -> "Hinzugefügt $added · entfernt $removed · behalten $kept"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^共 (\\d+) 个节点$").matchEntire(text)?.let { match ->
        val count = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "共 $count 個節點"
            WeaveLanguage.ENGLISH -> "$count nodes total"
            WeaveLanguage.JAPANESE -> "ノード合計 $count"
            WeaveLanguage.FRENCH -> "$count nœuds au total"
            WeaveLanguage.GERMAN -> "$count Knoten insgesamt"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^应用规则  (\\d+)$").matchEntire(text)?.let { match ->
        val count = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "應用規則  $count"
            WeaveLanguage.ENGLISH -> "App rules  $count"
            WeaveLanguage.JAPANESE -> "アプリルール  $count"
            WeaveLanguage.FRENCH -> "Règles d’app  $count"
            WeaveLanguage.GERMAN -> "App-Regeln  $count"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^(\\d+) 个节点$").matchEntire(text)?.let { match ->
        val count = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "$count 個節點"
            WeaveLanguage.ENGLISH -> "$count nodes"
            WeaveLanguage.JAPANESE -> "$count ノード"
            WeaveLanguage.FRENCH -> "$count nœuds"
            WeaveLanguage.GERMAN -> "$count Knoten"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^导出所选 (\\d+) 个订阅$").matchEntire(text)?.let { match ->
        val count = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "匯出所選 $count 個訂閱"
            WeaveLanguage.ENGLISH -> "Export $count selected subscriptions"
            WeaveLanguage.JAPANESE -> "選択したサブスクリプション $count 件をエクスポート"
            WeaveLanguage.FRENCH -> "Exporter $count abonnements sélectionnés"
            WeaveLanguage.GERMAN -> "$count ausgewählte Abonnements exportieren"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^从 (\\d+) 个节点中自动选择$").matchEntire(text)?.let { match ->
        val count = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "從 $count 個節點中自動選擇"
            WeaveLanguage.ENGLISH -> "Choose automatically from $count nodes"
            WeaveLanguage.JAPANESE -> "$count ノードから自動選択"
            WeaveLanguage.FRENCH -> "Choisir automatiquement parmi $count nœuds"
            WeaveLanguage.GERMAN -> "Automatisch aus $count Knoten wählen"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^连续失败：(\\d+) 次$").matchEntire(text)?.let { match ->
        val count = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "連續失敗：$count 次"
            WeaveLanguage.ENGLISH -> "Consecutive failures: $count"
            WeaveLanguage.JAPANESE -> "連続失敗：$count 回"
            WeaveLanguage.FRENCH -> "Échecs consécutifs : $count"
            WeaveLanguage.GERMAN -> "Aufeinanderfolgende Fehler: $count"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^正在恢复代理连接（第 (\\d+) 次）$").matchEntire(text)?.let { match ->
        val count = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "正在恢復代理連線（第 $count 次）"
            WeaveLanguage.ENGLISH -> "Restoring proxy connection (attempt $count)"
            WeaveLanguage.JAPANESE -> "プロキシ接続を復元中（$count 回目）"
            WeaveLanguage.FRENCH -> "Restauration de la connexion proxy (tentative $count)"
            WeaveLanguage.GERMAN -> "Proxy-Verbindung wird wiederhergestellt (Versuch $count)"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^(\\d+) 个节点 · 本地加密保存$").matchEntire(text)?.let { match ->
        val count = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "$count 個節點 · 本機加密保存"
            WeaveLanguage.ENGLISH -> "$count nodes · encrypted locally"
            WeaveLanguage.JAPANESE -> "$count ノード · 端末内で暗号化保存"
            WeaveLanguage.FRENCH -> "$count nœuds · chiffrés localement"
            WeaveLanguage.GERMAN -> "$count Knoten · lokal verschlüsselt"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^(\\d+) · (稳定|一般|波动|未完成)$").matchEntire(text)?.let { match ->
        val score = match.groupValues[1]
        val label = localizeWeaveText(match.groupValues[2], language)
        return "$score · $label"
    }
    Regex("^(.+) · 中位 (\\d+)ms · P95 (\\d+)ms · 抖(\\d+|—) · 丢(\\d+)% · (\\d+)/(\\d+)$").matchEntire(text)?.let { match ->
        val protocol = match.groupValues[1]
        val median = match.groupValues[2]
        val p95 = match.groupValues[3]
        val jitter = match.groupValues[4]
        val loss = match.groupValues[5]
        val successful = match.groupValues[6]
        val total = match.groupValues[7]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "$protocol · 中位數 $median ms · P95 $p95 ms · 抖動$jitter · 丟失$loss% · $successful/$total"
            WeaveLanguage.ENGLISH -> "$protocol · median $median ms · P95 $p95 ms · jitter $jitter · $loss% loss · $successful/$total"
            WeaveLanguage.JAPANESE -> "$protocol · 中央値 $median ms · P95 $p95 ms · ジッター $jitter · 損失 $loss% · $successful/$total"
            WeaveLanguage.FRENCH -> "$protocol · médiane $median ms · P95 $p95 ms · gigue $jitter · perte $loss % · $successful/$total"
            WeaveLanguage.GERMAN -> "$protocol · Median $median ms · P95 $p95 ms · Jitter $jitter · $loss% Verlust · $successful/$total"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^(.+) · 延迟— · P95— · 抖(\\d+|—) · 丢(\\d+)% · (\\d+)/(\\d+)$").matchEntire(text)?.let { match ->
        val protocol = match.groupValues[1]
        val jitter = match.groupValues[2]
        val loss = match.groupValues[3]
        val successful = match.groupValues[4]
        val total = match.groupValues[5]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "$protocol · 延遲— · P95— · 抖動$jitter · 丟失$loss% · $successful/$total"
            WeaveLanguage.ENGLISH -> "$protocol · latency— · P95— · jitter $jitter · $loss% loss · $successful/$total"
            WeaveLanguage.JAPANESE -> "$protocol · レイテンシ— · P95— · ジッター $jitter · 損失 $loss% · $successful/$total"
            WeaveLanguage.FRENCH -> "$protocol · latence — · P95— · gigue $jitter · perte $loss % · $successful/$total"
            WeaveLanguage.GERMAN -> "$protocol · Latenz— · P95— · Jitter $jitter · $loss% Verlust · $successful/$total"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^例如 (.+)$").matchEntire(text)?.let { match ->
        val example = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "例如 $example"
            WeaveLanguage.ENGLISH -> "e.g. $example"
            WeaveLanguage.JAPANESE -> "例：$example"
            WeaveLanguage.FRENCH -> "ex. $example"
            WeaveLanguage.GERMAN -> "z. B. $example"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^(.+) · (.+) · (.+) · DNS 旁路保护 \\+ (.+)$").matchEntire(text)?.let { match ->
        val profile = localizeWeaveText(match.groupValues[1], language)
        val transport = localizeWeaveText(match.groupValues[2], language)
        val routing = localizeWeaveText(match.groupValues[3], language)
        val bypass = when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "DNS 旁路保護"
            WeaveLanguage.ENGLISH -> "DNS bypass protection"
            WeaveLanguage.JAPANESE -> "DNS バイパス保護"
            WeaveLanguage.FRENCH -> "Protection contre le contournement DNS"
            WeaveLanguage.GERMAN -> "DNS-Umgehungsschutz"
            WeaveLanguage.SIMPLIFIED_CHINESE -> "DNS 旁路保护"
        }
        val suffix = localizeWeaveText(match.groupValues[4], language)
        return "$profile · $transport · $routing · $bypass + $suffix"
    }
    Regex("^(\\d+) 项已从本地配置确认 · (\\d+) 项需要外部验证$").matchEntire(text)?.let { match ->
        val verified = match.groupValues[1]
        val external = match.groupValues[2]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "$verified 項已從本機設定確認 · $external 項需要外部驗證"
            WeaveLanguage.ENGLISH -> "$verified confirmed from local configuration · $external require external verification"
            WeaveLanguage.JAPANESE -> "$verified 件はローカル設定で確認済み · $external 件は外部検証が必要"
            WeaveLanguage.FRENCH -> "$verified confirmés par la configuration locale · $external nécessitent une vérification externe"
            WeaveLanguage.GERMAN -> "$verified aus der lokalen Konfiguration bestätigt · $external benötigen eine externe Prüfung"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^(\\d+)/(\\d+) 个端点可达 · 中位 (.+) ms$").matchEntire(text)?.let { match ->
        val reachable = match.groupValues[1]
        val total = match.groupValues[2]
        val median = match.groupValues[3]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "$reachable/$total 個端點可達 · 中位數 $median ms"
            WeaveLanguage.ENGLISH -> "$reachable/$total endpoints reachable · median $median ms"
            WeaveLanguage.JAPANESE -> "$reachable/$total 件のエンドポイントが到達可能 · 中央値 $median ms"
            WeaveLanguage.FRENCH -> "$reachable/$total points accessibles · médiane $median ms"
            WeaveLanguage.GERMAN -> "$reachable/$total Endpunkte erreichbar · Median $median ms"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^端点不可达：(.*)$").matchEntire(text)?.let { match ->
        val detail = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "端點無法連線：$detail"
            WeaveLanguage.ENGLISH -> "Endpoint unreachable: $detail"
            WeaveLanguage.JAPANESE -> "エンドポイントに到達できません：$detail"
            WeaveLanguage.FRENCH -> "Point inaccessible : $detail"
            WeaveLanguage.GERMAN -> "Endpunkt nicht erreichbar: $detail"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^ASN  (.+) · 未知运营商$").matchEntire(text)?.let { match ->
        val asn = match.groupValues[1]
        val carrier = localizeWeaveText("未知运营商", language)
        return "ASN  $asn · $carrier"
    }
    Regex("^应用规则 · (.+)$").matchEntire(text)?.let { match ->
        val prefix = localizeWeaveText("应用规则", language)
        return "$prefix · ${match.groupValues[1]}"
    }
    Regex("^本地规则 · (.+)$").matchEntire(text)?.let { match ->
        val prefix = localizeWeaveText("本地规则", language)
        val suffix = match.groupValues[1]
        val separator = suffix.indexOf(' ')
        val localizedSuffix = if (separator > 0) {
            "${localizeWeaveText(suffix.substring(0, separator), language)}${suffix.substring(separator)}"
        } else {
            suffix
        }
        return "$prefix · $localizedSuffix"
    }
    Regex("^(.+) 加密解析 · (.+)$").matchEntire(text)?.let { match ->
        val transport = localizeWeaveText(match.groupValues[1], language)
        val endpoint = localizeWeaveText(match.groupValues[2], language)
        return "$transport ${when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "加密解析"
            WeaveLanguage.ENGLISH -> "encrypted resolution"
            WeaveLanguage.JAPANESE -> "暗号化解決"
            WeaveLanguage.FRENCH -> "résolution chiffrée"
            WeaveLanguage.GERMAN -> "verschlüsselte Auflösung"
            WeaveLanguage.SIMPLIFIED_CHINESE -> "加密解析"
        }} · $endpoint"
    }
    Regex("^STUN 端口 (\\d+) 已按规则阻断$").matchEntire(text)?.let { match ->
        val port = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "STUN 連接埠 $port 已按規則阻擋"
            WeaveLanguage.ENGLISH -> "STUN port $port blocked by rule"
            WeaveLanguage.JAPANESE -> "STUN ポート $port はルールでブロック済み"
            WeaveLanguage.FRENCH -> "Port STUN $port bloqué par la règle"
            WeaveLanguage.GERMAN -> "STUN-Port $port per Regel blockiert"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^(\\d+)/(\\d+) 个上游可达$").matchEntire(text)?.let { match ->
        val available = match.groupValues[1]
        val total = match.groupValues[2]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "$available/$total 個上游可達"
            WeaveLanguage.ENGLISH -> "$available/$total upstreams reachable"
            WeaveLanguage.JAPANESE -> "$available/$total 件の上流に到達可能"
            WeaveLanguage.FRENCH -> "$available/$total amonts accessibles"
            WeaveLanguage.GERMAN -> "$available/$total Upstreams erreichbar"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^HTTPS (\\d+) · (端点可达|服务端错误)$").matchEntire(text)?.let { match ->
        val code = match.groupValues[1]
        val status = localizeWeaveText(match.groupValues[2], language)
        return "HTTPS $code · $status"
    }
    Regex("^(.+) 没有指定订阅$").matchEntire(text)?.let { match ->
        val owner = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "$owner 未指定訂閱"
            WeaveLanguage.ENGLISH -> "$owner has no subscription selected"
            WeaveLanguage.JAPANESE -> "$owner に購読が指定されていません"
            WeaveLanguage.FRENCH -> "$owner n’a pas d’abonnement sélectionné"
            WeaveLanguage.GERMAN -> "$owner hat kein Abonnement ausgewählt"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^(.+) 没有指定节点$").matchEntire(text)?.let { match ->
        val owner = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "$owner 未指定節點"
            WeaveLanguage.ENGLISH -> "$owner has no node selected"
            WeaveLanguage.JAPANESE -> "$owner にノードが指定されていません"
            WeaveLanguage.FRENCH -> "$owner n’a pas de nœud sélectionné"
            WeaveLanguage.GERMAN -> "$owner hat keinen Knoten ausgewählt"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^(.+) 指向的订阅不可用于 Mihomo；当前仅支持 Clash YAML$").matchEntire(text)?.let { match ->
        val owner = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "$owner 指向的訂閱不可用於 Mihomo；目前僅支援 Clash YAML"
            WeaveLanguage.ENGLISH -> "The subscription targeted by $owner cannot be used by Mihomo; only Clash YAML is currently supported"
            WeaveLanguage.JAPANESE -> "$owner が指定する購読は Mihomo で使用できません。現在は Clash YAML のみ対応しています"
            WeaveLanguage.FRENCH -> "L’abonnement ciblé par $owner ne peut pas être utilisé par Mihomo ; seul Clash YAML est pris en charge"
            WeaveLanguage.GERMAN -> "Das von $owner angegebene Abonnement ist für Mihomo ungeeignet; derzeit wird nur Clash YAML unterstützt"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^(.+) 指向的节点已不存在，请重新选择$").matchEntire(text)?.let { match ->
        val owner = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "$owner 指向的節點已不存在，請重新選擇"
            WeaveLanguage.ENGLISH -> "The node targeted by $owner no longer exists; select another"
            WeaveLanguage.JAPANESE -> "$owner が指定するノードは存在しません。選び直してください"
            WeaveLanguage.FRENCH -> "Le nœud ciblé par $owner n’existe plus ; sélectionnez-en un autre"
            WeaveLanguage.GERMAN -> "Der von $owner angegebene Knoten existiert nicht mehr; bitte neu auswählen"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^Mihomo TUN 启动失败，错误码 (\\d+)$").matchEntire(text)?.let { match ->
        val code = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "Mihomo TUN 啟動失敗，錯誤碼 $code"
            WeaveLanguage.ENGLISH -> "Mihomo TUN failed to start (error $code)"
            WeaveLanguage.JAPANESE -> "Mihomo TUN の起動に失敗しました（エラー $code）"
            WeaveLanguage.FRENCH -> "Échec du démarrage du TUN Mihomo (erreur $code)"
            WeaveLanguage.GERMAN -> "Mihomo-TUN konnte nicht gestartet werden (Fehler $code)"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^无法写入订阅 (.+) 的运行时副本$").matchEntire(text)?.let { match ->
        val name = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "無法寫入訂閱 $name 的執行副本"
            WeaveLanguage.ENGLISH -> "Unable to write the runtime copy of subscription $name"
            WeaveLanguage.JAPANESE -> "購読 $name の実行用コピーを書き込めません"
            WeaveLanguage.FRENCH -> "Impossible d’écrire la copie d’exécution de l’abonnement $name"
            WeaveLanguage.GERMAN -> "Laufzeitkopie des Abonnements $name konnte nicht geschrieben werden"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^第三方信息服务标记为 (.*)；这不是恶意判定$").matchEntire(text)?.let { match ->
        val labels = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "第三方資訊服務標記為 $labels；這不是惡意判定"
            WeaveLanguage.ENGLISH -> "A third-party information service labels it $labels; this is not a malicious verdict"
            WeaveLanguage.JAPANESE -> "第三者情報サービスのラベル：$labels。悪意の判定ではありません"
            WeaveLanguage.FRENCH -> "Un service tiers le signale comme $labels ; ce n’est pas un verdict malveillant"
            WeaveLanguage.GERMAN -> "Ein Drittanbieter markiert es als $labels; dies ist kein Schadensbefund"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^未取得 IPv4 公网地址 · (.*)$").matchEntire(text)?.let { match ->
        val detail = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "未取得 IPv4 公網地址 · $detail"
            WeaveLanguage.ENGLISH -> "No public IPv4 address obtained · $detail"
            WeaveLanguage.JAPANESE -> "IPv4 パブリックアドレスを取得できません · $detail"
            WeaveLanguage.FRENCH -> "Aucune adresse IPv4 publique obtenue · $detail"
            WeaveLanguage.GERMAN -> "Keine öffentliche IPv4-Adresse erhalten · $detail"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^检测到 (.*)；与“仅 IPv4”设置不一致$").matchEntire(text)?.let { match ->
        val address = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "偵測到 $address；與「僅 IPv4」設定不一致"
            WeaveLanguage.ENGLISH -> "Detected $address; inconsistent with the IPv4-only setting"
            WeaveLanguage.JAPANESE -> "$address を検出しました。「IPv4 のみ」設定と一致しません"
            WeaveLanguage.FRENCH -> "$address détectée ; incohérent avec le réglage IPv4 uniquement"
            WeaveLanguage.GERMAN -> "$address erkannt; widerspricht der Einstellung Nur IPv4"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^(.+) · (.+)；这是配置证据，不是外部泄漏测试$").matchEntire(text)?.let { match ->
        val transport = localizeWeaveText(match.groupValues[1], language)
        val profile = localizeWeaveText(match.groupValues[2], language)
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "$transport · $profile；這是設定證據，不是外部洩漏測試"
            WeaveLanguage.ENGLISH -> "$transport · $profile; this is configuration evidence, not an external leak test"
            WeaveLanguage.JAPANESE -> "$transport · $profile。これは設定の証拠であり、外部リークテストではありません"
            WeaveLanguage.FRENCH -> "$transport · $profile ; preuve de configuration, pas un test externe de fuite"
            WeaveLanguage.GERMAN -> "$transport · $profile; Konfigurationsnachweis, kein externer Lecktest"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^(\\d+) GB 已用$").matchEntire(text)?.let { match ->
        val amount = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "$amount GB 已用"
            WeaveLanguage.ENGLISH -> "$amount GB used"
            WeaveLanguage.JAPANESE -> "$amount GB 使用済み"
            WeaveLanguage.FRENCH -> "$amount Go utilisés"
            WeaveLanguage.GERMAN -> "$amount GB verwendet"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^节点  (\\d+)$").matchEntire(text)?.let { match ->
        val count = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "節點  $count"
            WeaveLanguage.ENGLISH -> "Nodes  $count"
            WeaveLanguage.JAPANESE -> "ノード  $count"
            WeaveLanguage.FRENCH -> "Nœuds  $count"
            WeaveLanguage.GERMAN -> "Knoten  $count"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^中位 (\\d+)ms$").matchEntire(text)?.let { match ->
        val latency = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "中位數 ${latency}ms"
            WeaveLanguage.ENGLISH -> "Median ${latency}ms"
            WeaveLanguage.JAPANESE -> "中央値 ${latency}ms"
            WeaveLanguage.FRENCH -> "Médiane ${latency} ms"
            WeaveLanguage.GERMAN -> "Median ${latency} ms"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^(\\d+) ms · 丢(\\d+)%$").matchEntire(text)?.let { match ->
        val latency = match.groupValues[1]
        val loss = match.groupValues[2]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "${latency} ms · 丟${loss}%"
            WeaveLanguage.ENGLISH -> "${latency} ms · ${loss}% loss"
            WeaveLanguage.JAPANESE -> "${latency} ms · 損失 ${loss}%"
            WeaveLanguage.FRENCH -> "${latency} ms · perte ${loss} %"
            WeaveLanguage.GERMAN -> "${latency} ms · ${loss}% Verlust"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^(\\d+) ms · 抖(\\d+)$").matchEntire(text)?.let { match ->
        val latency = match.groupValues[1]
        val jitter = match.groupValues[2]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "${latency} ms · 抖動${jitter}"
            WeaveLanguage.ENGLISH -> "${latency} ms · jitter ${jitter}"
            WeaveLanguage.JAPANESE -> "${latency} ms · ジッター ${jitter}"
            WeaveLanguage.FRENCH -> "${latency} ms · gigue ${jitter}"
            WeaveLanguage.GERMAN -> "${latency} ms · Jitter ${jitter}"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^边缘节点  (.+)$").matchEntire(text)?.let { match ->
        val edge = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "邊緣節點  $edge"
            WeaveLanguage.ENGLISH -> "Edge  $edge"
            WeaveLanguage.JAPANESE -> "エッジ  $edge"
            WeaveLanguage.FRENCH -> "Nœud périphérique  $edge"
            WeaveLanguage.GERMAN -> "Edge  $edge"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^确认短码：(.*)$").matchEntire(text)?.let { match ->
        val code = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "確認短碼：$code"
            WeaveLanguage.ENGLISH -> "Confirmation code: $code"
            WeaveLanguage.JAPANESE -> "確認コード：$code"
            WeaveLanguage.FRENCH -> "Code de confirmation : $code"
            WeaveLanguage.GERMAN -> "Bestätigungscode: $code"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^添加 (.+)$").matchEntire(text)?.let { match ->
        val app = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "新增 $app"
            WeaveLanguage.ENGLISH -> "Add $app"
            WeaveLanguage.JAPANESE -> "$app を追加"
            WeaveLanguage.FRENCH -> "Ajouter $app"
            WeaveLanguage.GERMAN -> "$app hinzufügen"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^删除后，(.+) 将改用默认出口。$").matchEntire(text)?.let { match ->
        val app = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "刪除後，$app 將改用預設出口。"
            WeaveLanguage.ENGLISH -> "After deletion, $app will use the default exit."
            WeaveLanguage.JAPANESE -> "削除後、$app はデフォルト出口を使用します。"
            WeaveLanguage.FRENCH -> "Après suppression, $app utilisera la sortie par défaut."
            WeaveLanguage.GERMAN -> "Nach dem Löschen verwendet $app den Standardausgang."
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^将永久删除「(.+)」、加密订阅地址和 (\\d+) 个节点。$").matchEntire(text)?.let { match ->
        val name = match.groupValues[1]
        val count = match.groupValues[2]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "將永久刪除「$name」、加密訂閱地址和 $count 個節點。"
            WeaveLanguage.ENGLISH -> "This permanently deletes “$name”, the encrypted subscription URL and $count nodes."
            WeaveLanguage.JAPANESE -> "「$name」、暗号化された購読 URL、$count ノードを完全に削除します。"
            WeaveLanguage.FRENCH -> "« $name », l’URL chiffrée et $count nœuds seront supprimés définitivement."
            WeaveLanguage.GERMAN -> "„$name“, die verschlüsselte Abo-URL und $count Knoten werden dauerhaft gelöscht."
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^引用它的 (\\d+) 条应用规则也会删除，这些应用随后使用默认出口。$").matchEntire(text)?.let { match ->
        val count = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "引用它的 $count 條應用程式規則也會刪除，這些應用程式隨後使用預設出口。"
            WeaveLanguage.ENGLISH -> "The $count app rules that reference it will also be deleted; those apps will use the default exit."
            WeaveLanguage.JAPANESE -> "参照しているアプリルール $count 件も削除され、対象アプリはデフォルト出口を使用します。"
            WeaveLanguage.FRENCH -> "Les $count règles d’application qui le référencent seront aussi supprimées ; ces apps utiliseront la sortie par défaut."
            WeaveLanguage.GERMAN -> "Die $count darauf verweisenden App-Regeln werden ebenfalls gelöscht; diese Apps nutzen den Standardausgang."
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^完成 (\\d+)/(\\d+) 项 · (\\d+) ms$").matchEntire(text)?.let { match ->
        val completed = match.groupValues[1]
        val total = match.groupValues[2]
        val elapsed = match.groupValues[3]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "完成 $completed/$total 項 · $elapsed ms"
            WeaveLanguage.ENGLISH -> "$completed/$total complete · $elapsed ms"
            WeaveLanguage.JAPANESE -> "$completed/$total 件完了 · $elapsed ms"
            WeaveLanguage.FRENCH -> "$completed/$total terminées · $elapsed ms"
            WeaveLanguage.GERMAN -> "$completed/$total abgeschlossen · $elapsed ms"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^已刷新 (\\d+) 个远程订阅$").matchEntire(text)?.let { match ->
        val count = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "已重新整理 $count 個遠端訂閱"
            WeaveLanguage.ENGLISH -> "Refreshed $count remote subscriptions"
            WeaveLanguage.JAPANESE -> "リモート購読を $count 件更新しました"
            WeaveLanguage.FRENCH -> "$count abonnements distants actualisés"
            WeaveLanguage.GERMAN -> "$count entfernte Abonnements aktualisiert"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^已完成 (\\d+) 个，(\\d+) 个失败$").matchEntire(text)?.let { match ->
        val completed = match.groupValues[1]
        val failed = match.groupValues[2]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "已完成 $completed 個，$failed 個失敗"
            WeaveLanguage.ENGLISH -> "$completed complete, $failed failed"
            WeaveLanguage.JAPANESE -> "$completed 件完了、$failed 件失敗"
            WeaveLanguage.FRENCH -> "$completed terminés, $failed échecs"
            WeaveLanguage.GERMAN -> "$completed abgeschlossen, $failed fehlgeschlagen"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^已安全同步 (\\d+) 个订阅；同源订阅已原位更新$").matchEntire(text)?.let { match ->
        val count = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "已安全同步 $count 個訂閱；同源訂閱已原位更新"
            WeaveLanguage.ENGLISH -> "Safely synced $count subscriptions; matching sources were updated in place"
            WeaveLanguage.JAPANESE -> "$count 件の購読を安全に同期し、同一ソースを更新しました"
            WeaveLanguage.FRENCH -> "$count abonnements synchronisés en sécurité ; les sources identiques ont été mises à jour"
            WeaveLanguage.GERMAN -> "$count Abonnements sicher synchronisiert; gleiche Quellen wurden aktualisiert"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^生成时间：(.*)$").matchEntire(text)?.let { match ->
        val time = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "產生時間：$time"
            WeaveLanguage.ENGLISH -> "Generated: $time"
            WeaveLanguage.JAPANESE -> "生成日時：$time"
            WeaveLanguage.FRENCH -> "Généré : $time"
            WeaveLanguage.GERMAN -> "Erstellt: $time"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^来源：(.*)$").matchEntire(text)?.let { match ->
        val source = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "來源：$source"
            WeaveLanguage.ENGLISH -> "Source: $source"
            WeaveLanguage.JAPANESE -> "ソース：$source"
            WeaveLanguage.FRENCH -> "Source : $source"
            WeaveLanguage.GERMAN -> "Quelle: $source"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^最近一次：可用 (\\d+)/(\\d+) · 中位 (\\d+) ms · P95 (\\d+) ms · 平均丢包 (\\d+)%$").matchEntire(text)?.let { match ->
        val available = match.groupValues[1]
        val total = match.groupValues[2]
        val median = match.groupValues[3]
        val p95 = match.groupValues[4]
        val loss = match.groupValues[5]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "最近一次：可用 $available/$total · 中位數 $median ms · P95 $p95 ms · 平均丟包 $loss%"
            WeaveLanguage.ENGLISH -> "Last run: $available/$total available · median $median ms · P95 $p95 ms · avg. loss $loss%"
            WeaveLanguage.JAPANESE -> "前回：$available/$total 件利用可能 · 中央値 $median ms · P95 $p95 ms · 平均損失 $loss%"
            WeaveLanguage.FRENCH -> "Dernier test : $available/$total disponibles · médiane $median ms · P95 $p95 ms · perte moy. $loss %"
            WeaveLanguage.GERMAN -> "Letzter Lauf: $available/$total verfügbar · Median $median ms · P95 $p95 ms · Ø Verlust $loss%"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^v(.+) · (\\d+) 条 · (.+)$").matchEntire(text)?.let { match ->
        val version = match.groupValues[1]
        val count = match.groupValues[2]
        val integrity = match.groupValues[3]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "v$version · $count 條 · $integrity"
            WeaveLanguage.ENGLISH -> "v$version · $count rules · $integrity"
            WeaveLanguage.JAPANESE -> "v$version · $count 件 · $integrity"
            WeaveLanguage.FRENCH -> "v$version · $count règles · $integrity"
            WeaveLanguage.GERMAN -> "v$version · $count Regeln · $integrity"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^正在刷新 (.+)$").matchEntire(text)?.let { match ->
        val name = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "正在重新整理 $name"
            WeaveLanguage.ENGLISH -> "Refreshing $name"
            WeaveLanguage.JAPANESE -> "$name を更新中"
            WeaveLanguage.FRENCH -> "Actualisation de $name"
            WeaveLanguage.GERMAN -> "$name wird aktualisiert"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^已删除订阅「(.+)」$").matchEntire(text)?.let { match ->
        val name = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "已刪除訂閱「$name」"
            WeaveLanguage.ENGLISH -> "Deleted subscription “$name”"
            WeaveLanguage.JAPANESE -> "購読「$name」を削除しました"
            WeaveLanguage.FRENCH -> "Abonnement « $name » supprimé"
            WeaveLanguage.GERMAN -> "Abonnement „$name“ gelöscht"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^已导入策略包「(.+)」$").matchEntire(text)?.let { match ->
        val name = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "已匯入策略包「$name」"
            WeaveLanguage.ENGLISH -> "Imported policy pack “$name”"
            WeaveLanguage.JAPANESE -> "ポリシーパック「$name」をインポートしました"
            WeaveLanguage.FRENCH -> "Pack de règles « $name » importé"
            WeaveLanguage.GERMAN -> "Richtlinienpaket „$name“ importiert"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^策略包超过 (\\d+) KiB 限制$").matchEntire(text)?.let { match ->
        val size = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "策略包超過 $size KiB 限制"
            WeaveLanguage.ENGLISH -> "Policy pack exceeds the $size KiB limit"
            WeaveLanguage.JAPANESE -> "ポリシーパックが $size KiB の上限を超えています"
            WeaveLanguage.FRENCH -> "Le pack de règles dépasse la limite de $size KiB"
            WeaveLanguage.GERMAN -> "Das Richtlinienpaket überschreitet das Limit von $size KiB"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^(.+)，正在安全更新运行配置$").matchEntire(text)?.let { match ->
        val prefix = match.groupValues[1]
        val localizedPrefix = localizeWeaveText(prefix, language)
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "$localizedPrefix，正在安全更新執行設定"
            WeaveLanguage.ENGLISH -> "$localizedPrefix; safely updating the runtime configuration"
            WeaveLanguage.JAPANESE -> "$localizedPrefix。実行設定を安全に更新中"
            WeaveLanguage.FRENCH -> "$localizedPrefix ; mise à jour sécurisée de la configuration"
            WeaveLanguage.GERMAN -> "$localizedPrefix; Laufzeitkonfiguration wird sicher aktualisiert"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^(.+)，正在安全应用$").matchEntire(text)?.let { match ->
        val prefix = match.groupValues[1]
        val localizedPrefix = localizeWeaveText(prefix, language)
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "$localizedPrefix，正在安全套用"
            WeaveLanguage.ENGLISH -> "$localizedPrefix; safely applying changes"
            WeaveLanguage.JAPANESE -> "$localizedPrefix。安全に適用中"
            WeaveLanguage.FRENCH -> "$localizedPrefix ; application sécurisée"
            WeaveLanguage.GERMAN -> "$localizedPrefix; Änderungen werden sicher angewendet"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^最近可用快照：(.*)$").matchEntire(text)?.let { match ->
        val snapshot = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "最近可用快照：$snapshot"
            WeaveLanguage.ENGLISH -> "Latest usable snapshot: $snapshot"
            WeaveLanguage.JAPANESE -> "最新の利用可能なスナップショット：$snapshot"
            WeaveLanguage.FRENCH -> "Dernier instantané utilisable : $snapshot"
            WeaveLanguage.GERMAN -> "Letzter nutzbarer Snapshot: $snapshot"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^最近失败：(.*)$").matchEntire(text)?.let { match ->
        val failure = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "最近失敗：$failure"
            WeaveLanguage.ENGLISH -> "Latest failure: $failure"
            WeaveLanguage.JAPANESE -> "直近の失敗：$failure"
            WeaveLanguage.FRENCH -> "Dernier échec : $failure"
            WeaveLanguage.GERMAN -> "Letzter Fehler: $failure"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^远程订阅已安全更新 · (.*)$").matchEntire(text)?.let { match ->
        val detail = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "遠端訂閱已安全更新 · $detail"
            WeaveLanguage.ENGLISH -> "Remote subscription safely updated · $detail"
            WeaveLanguage.JAPANESE -> "リモート購読を安全に更新しました · $detail"
            WeaveLanguage.FRENCH -> "Abonnement distant mis à jour en sécurité · $detail"
            WeaveLanguage.GERMAN -> "Entferntes Abonnement sicher aktualisiert · $detail"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^订阅文件已安全替换 · (.*)$").matchEntire(text)?.let { match ->
        val detail = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "訂閱檔案已安全替換 · $detail"
            WeaveLanguage.ENGLISH -> "Subscription file safely replaced · $detail"
            WeaveLanguage.JAPANESE -> "購読ファイルを安全に置換しました · $detail"
            WeaveLanguage.FRENCH -> "Fichier d’abonnement remplacé en sécurité · $detail"
            WeaveLanguage.GERMAN -> "Abonnementdatei sicher ersetzt · $detail"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^已安全导入「(.+)」$").matchEntire(text)?.let { match ->
        val name = match.groupValues[1]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "已安全匯入「$name」"
            WeaveLanguage.ENGLISH -> "Imported “$name” safely"
            WeaveLanguage.JAPANESE -> "「$name」を安全にインポートしました"
            WeaveLanguage.FRENCH -> "« $name » importé en sécurité"
            WeaveLanguage.GERMAN -> "„$name“ sicher importiert"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^(.+) · (\\d+) → (\\d+) 节点$").matchEntire(text)?.let { match ->
        val summary = localizeWeaveText(match.groupValues[1], language)
        val oldCount = match.groupValues[2]
        val newCount = match.groupValues[3]
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "$summary · $oldCount → $newCount 個節點"
            WeaveLanguage.ENGLISH -> "$summary · $oldCount → $newCount nodes"
            WeaveLanguage.JAPANESE -> "$summary · $oldCount → $newCount ノード"
            WeaveLanguage.FRENCH -> "$summary · $oldCount → $newCount nœuds"
            WeaveLanguage.GERMAN -> "$summary · $oldCount → $newCount Knoten"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^· (.+)：(.+)$").matchEntire(text)?.let { match ->
        val title = localizeWeaveText(match.groupValues[1], language)
        val detail = localizeWeaveText(match.groupValues[2], language)
        val separator = when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE, WeaveLanguage.JAPANESE, WeaveLanguage.SIMPLIFIED_CHINESE -> "："
            WeaveLanguage.ENGLISH -> ": "
            WeaveLanguage.FRENCH -> " : "
            WeaveLanguage.GERMAN -> ": "
        }
        return "· $title$separator$detail"
    }
    Regex("^审计提示：(.*)$").matchEntire(text)?.let { match ->
        val details = match.groupValues[1]
            .split("；")
            .joinToString(when (language) {
                WeaveLanguage.TRADITIONAL_CHINESE, WeaveLanguage.JAPANESE, WeaveLanguage.SIMPLIFIED_CHINESE -> "；"
                WeaveLanguage.ENGLISH, WeaveLanguage.FRENCH, WeaveLanguage.GERMAN -> "; "
            }) { localizeWeaveText(it, language) }
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "稽核提示：$details"
            WeaveLanguage.ENGLISH -> "Audit note: $details"
            WeaveLanguage.JAPANESE -> "監査の注意：$details"
            WeaveLanguage.FRENCH -> "Avertissement d’audit : $details"
            WeaveLanguage.GERMAN -> "Audit-Hinweis: $details"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^审计阻止：(.*)$").matchEntire(text)?.let { match ->
        val details = match.groupValues[1]
            .split("；")
            .joinToString(when (language) {
                WeaveLanguage.TRADITIONAL_CHINESE, WeaveLanguage.JAPANESE, WeaveLanguage.SIMPLIFIED_CHINESE -> "；"
                WeaveLanguage.ENGLISH, WeaveLanguage.FRENCH, WeaveLanguage.GERMAN -> "; "
            }) { localizeWeaveText(it, language) }
        return when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE -> "稽核阻止：$details"
            WeaveLanguage.ENGLISH -> "Audit blocked: $details"
            WeaveLanguage.JAPANESE -> "監査によりブロック：$details"
            WeaveLanguage.FRENCH -> "Audit bloqué : $details"
            WeaveLanguage.GERMAN -> "Audit blockiert: $details"
            WeaveLanguage.SIMPLIFIED_CHINESE -> text
        }
    }
    Regex("^(.+)：(.+)$").matchEntire(text)?.let { match ->
        val prefix = match.groupValues[1]
        val localizedPrefix = translationTable(language)[prefix] ?: return@let
        val localizedValue = localizeWeaveText(match.groupValues[2], language)
        val separator = when (language) {
            WeaveLanguage.TRADITIONAL_CHINESE, WeaveLanguage.JAPANESE -> "："
            WeaveLanguage.ENGLISH -> ": "
            WeaveLanguage.FRENCH -> " : "
            WeaveLanguage.GERMAN -> ": "
            WeaveLanguage.SIMPLIFIED_CHINESE -> "："
        }
        return "$localizedPrefix$separator$localizedValue"
    }
    return null
}

/**
 * The Compose surface still contains a few long, explanatory strings while it is being moved to
 * resource-backed copy. Keep those strings in one supplemental catalog so a language switch never
 * leaves a visible Chinese fragment behind. The English value is a deliberate last-resort
 * fallback for long legal/technical copy; high-frequency controls provide native translations.
 */
private data class SupplementalTranslation(
    val english: String,
    val traditional: String = english,
    val japanese: String = english,
    val french: String = english,
    val german: String = english,
) {
    fun resolve(language: WeaveLanguage): String = when (language) {
        WeaveLanguage.SIMPLIFIED_CHINESE -> error("supplemental translations are not used for zh-CN")
        WeaveLanguage.TRADITIONAL_CHINESE -> traditional
        WeaveLanguage.ENGLISH -> english
        WeaveLanguage.JAPANESE -> japanese
        WeaveLanguage.FRENCH -> french
        WeaveLanguage.GERMAN -> german
    }
}

private fun supplementalUiTranslations(language: WeaveLanguage): Map<String, String> =
    SUPPLEMENTAL_TRANSLATIONS.mapValues { (_, value) -> value.resolve(language) }

private val SUPPLEMENTAL_TRANSLATIONS = mapOf(
    "DNS 泄漏测试" to SupplementalTranslation(
        english = "DNS leak test",
        traditional = "DNS 洩漏測試",
        japanese = "DNS リークテスト",
        french = "Test de fuite DNS",
        german = "DNS-Leak-Test",
    ),
    "DNS 端点检测" to SupplementalTranslation(
        english = "DNS endpoint test",
        traditional = "DNS 端點檢測",
        japanese = "DNS エンドポイントをテスト",
        french = "Test des points de terminaison DNS",
        german = "DNS-Endpunkte testen",
    ),
    "DoH / DoT 地址" to SupplementalTranslation(
        english = "DoH / DoT address",
        traditional = "DoH / DoT 位址",
        japanese = "DoH / DoT アドレス",
        french = "Adresse DoH / DoT",
        german = "DoH-/DoT-Adresse",
    ),
    "HTTPS 延迟" to SupplementalTranslation(
        english = "HTTPS latency",
        traditional = "HTTPS 延遲",
        japanese = "HTTPS レイテンシ",
        french = "Latence HTTPS",
        german = "HTTPS-Latenz",
    ),
    "HTTPS 订阅地址" to SupplementalTranslation(
        english = "HTTPS subscription URL",
        traditional = "HTTPS 訂閱位址",
        japanese = "HTTPS サブスクリプション URL",
        french = "URL d’abonnement HTTPS",
        german = "HTTPS-Abonnement-URL",
    ),
    "IP 协议" to SupplementalTranslation(
        english = "IP protocol",
        traditional = "IP 協定",
        japanese = "IP プロトコル",
        french = "Protocole IP",
        german = "IP-Protokoll",
    ),
    "IPv6 / WebRTC 测试" to SupplementalTranslation(
        english = "IPv6 / WebRTC test",
        traditional = "IPv6 / WebRTC 測試",
        japanese = "IPv6 / WebRTC テスト",
        french = "Test IPv6 / WebRTC",
        german = "IPv6-/WebRTC-Test",
    ),
    "IP（可选，用于 CIDR 规则）" to SupplementalTranslation(
        english = "IP (optional, for CIDR rules)",
        traditional = "IP（選填，用於 CIDR 規則）",
        japanese = "IP（任意、CIDR ルール用）",
        french = "IP (facultative, pour les règles CIDR)",
        german = "IP (optional, für CIDR-Regeln)",
    ),
    "Weave 一次性局域网链接" to SupplementalTranslation(
        english = "Weave one-time LAN link",
        traditional = "Weave 一次性區域網路連結",
        japanese = "Weave ワンタイム LAN リンク",
        french = "Lien LAN à usage unique Weave",
        german = "Einmaliger Weave-LAN-Link",
    ),
    "一次性传输二维码" to SupplementalTranslation(
        english = "One-time transfer QR code",
        traditional = "一次性傳輸 QR Code",
        japanese = "ワンタイム転送 QR コード",
        french = "QR de transfert à usage unique",
        german = "Einmaliger Übertragungs-QR-Code",
    ),
    "不会上传" to SupplementalTranslation(
        english = "Nothing is uploaded",
        traditional = "不會上傳",
        japanese = "アップロードしません",
        french = "Aucun envoi",
        german = "Keine Uploads",
    ),
    "不可达" to SupplementalTranslation(
        english = "Unreachable",
        traditional = "無法連線",
        japanese = "到達不能",
        french = "Injoignable",
        german = "Nicht erreichbar",
    ),
    "仅在你点击检测时请求公开 HTTPS 端点；Weave 不保存或上传结果" to SupplementalTranslation(
        english = "Public HTTPS endpoints are requested only when you tap Test; Weave does not store or upload the result",
        traditional = "只有在你點選檢測時才會請求公開 HTTPS 端點；Weave 不會保存或上傳結果",
        japanese = "テストをタップした時だけ公開 HTTPS エンドポイントに接続し、結果は保存・送信しません",
        french = "Les points HTTPS publics ne sont sollicités qu’après votre action ; Weave ne conserve ni n’envoie le résultat",
        german = "Öffentliche HTTPS-Endpunkte werden nur nach Tippen auf Test angefragt; Weave speichert oder lädt das Ergebnis nicht hoch",
    ),
    "仅在订阅页主动生成后临时开启" to SupplementalTranslation(
        english = "Enabled temporarily only after you generate it on the Subscriptions page",
        traditional = "只有在訂閱頁主動產生後才會暫時開啟",
        japanese = "サブスクリプション画面で生成した後だけ一時的に有効になります",
        french = "Activé temporairement uniquement après une génération volontaire dans Abonnements",
        german = "Nur vorübergehend aktiv, nachdem du es auf der Abonnementseite erzeugt hast",
    ),
    "仅支持加密地址：HTTPS DoH 或 TLS DoT。不会接受 udp://、tcp:// 或明文 IP DNS。" to SupplementalTranslation(
        english = "Encrypted addresses only: HTTPS DoH or TLS DoT. udp://, tcp:// and plaintext IP DNS are rejected.",
        traditional = "僅支援加密位址：HTTPS DoH 或 TLS DoT。不接受 udp://、tcp:// 或明文 IP DNS。",
        japanese = "暗号化アドレスのみ対応：HTTPS DoH または TLS DoT。udp://、tcp://、平文 IP DNS は拒否します。",
        french = "Adresses chiffrées uniquement : DoH HTTPS ou DoT TLS. udp://, tcp:// et DNS IP en clair sont refusés.",
        german = "Nur verschlüsselte Adressen: HTTPS-DoH oder TLS-DoT. udp://, tcp:// und unverschlüsseltes IP-DNS werden abgelehnt.",
    ),
    "仅测 TLS / HTTPS 端点，不发送域名查询" to SupplementalTranslation(
        english = "Only TLS / HTTPS endpoints are probed; no domain queries are sent",
        traditional = "僅測試 TLS / HTTPS 端點，不會傳送網域查詢",
        japanese = "TLS / HTTPS エンドポイントだけを測定し、ドメイン検索は送信しません",
        french = "Seuls les points TLS / HTTPS sont testés ; aucune requête de domaine n’est envoyée",
        german = "Nur TLS-/HTTPS-Endpunkte werden geprüft; es werden keine Domainabfragen gesendet",
    ),
    "你已经确认当前版本的说明。" to SupplementalTranslation(
        english = "You have acknowledged the notes for this version.",
        traditional = "你已確認目前版本的說明。",
        japanese = "このバージョンの説明を確認しました。",
        french = "Vous avez confirmé les informations de cette version.",
        german = "Du hast die Hinweise für diese Version bestätigt.",
    ),
    "你选择的订阅提供方、代理节点和目标网站仍可能看到连接所必需的元数据。" to SupplementalTranslation(
        english = "Your chosen subscription provider, proxy node and destination site may still see metadata needed for the connection.",
        traditional = "你選擇的訂閱提供方、代理節點與目標網站仍可能看到連線所需的中繼資料。",
        japanese = "選択した購読提供者、プロキシノード、接続先サイトには接続に必要なメタデータが見える場合があります。",
        french = "Le fournisseur d’abonnement, le nœud proxy et le site cible choisis peuvent voir les métadonnées nécessaires à la connexion.",
        german = "Dein gewählter Abonnementanbieter, Proxy-Knoten und Zielserver können für die Verbindung erforderliche Metadaten sehen.",
    ),
    "你选择的订阅提供方和代理节点仍可能看到来源 IP、连接时间与部分流量元数据。" to SupplementalTranslation(
        english = "Your chosen subscription provider and proxy node may still see your source IP, connection time and some traffic metadata.",
        traditional = "你選擇的訂閱提供方與代理節點仍可能看到來源 IP、連線時間與部分流量中繼資料。",
        japanese = "選択した購読提供者とプロキシノードには送信元 IP、接続時刻、一部の通信メタデータが見える場合があります。",
        french = "Le fournisseur d’abonnement et le nœud proxy choisis peuvent voir l’IP source, l’heure de connexion et certaines métadonnées.",
        german = "Dein gewählter Anbieter und Proxy-Knoten können Quell-IP, Verbindungszeit und einige Verkehrsmetadaten sehen.",
    ),
    "候选配置先由内核解析验证；失败时保留上一份可用配置。" to SupplementalTranslation(
        english = "The candidate configuration is parsed and validated by the core first; the last working configuration is retained on failure.",
        traditional = "候選設定會先由核心解析驗證；失敗時保留上一份可用設定。",
        japanese = "候補設定はまずコアで解析・検証し、失敗時は前回の有効な設定を保持します。",
        french = "La configuration candidate est d’abord validée par le noyau ; en cas d’échec, la dernière configuration valide est conservée.",
        german = "Die Kandidatenkonfiguration wird zuerst vom Core geprüft; bei Fehlern bleibt die letzte funktionierende Konfiguration erhalten.",
    ),
    "先选择订阅，再选择出口" to SupplementalTranslation(
        english = "Choose a subscription first, then choose an exit",
        traditional = "先選擇訂閱，再選擇出口",
        japanese = "まず購読を選び、その後に出口を選択",
        french = "Choisissez d’abord un abonnement, puis une sortie",
        german = "Zuerst ein Abonnement, dann einen Ausgang wählen",
    ),
    "出口" to SupplementalTranslation(
        english = "Exit",
        traditional = "出口",
        japanese = "出口",
        french = "Sortie",
        german = "Ausgang",
    ),
    "删除分流规则？" to SupplementalTranslation(
        english = "Delete this routing rule?",
        traditional = "刪除分流規則？",
        japanese = "このルーティングルールを削除しますか？",
        french = "Supprimer cette règle de routage ?",
        german = "Diese Routing-Regel löschen?",
    ),
    "删除订阅" to SupplementalTranslation(
        english = "Delete subscription",
        traditional = "刪除訂閱",
        japanese = "購読を削除",
        french = "Supprimer l’abonnement",
        german = "Abonnement löschen",
    ),
    "删除订阅？" to SupplementalTranslation(
        english = "Delete subscription?",
        traditional = "刪除訂閱？",
        japanese = "購読を削除しますか？",
        french = "Supprimer l’abonnement ?",
        german = "Abonnement löschen?",
    ),
    "刷新远程订阅" to SupplementalTranslation(
        english = "Refresh remote subscriptions",
        traditional = "重新整理遠端訂閱",
        japanese = "リモート購読を更新",
        french = "Actualiser les abonnements distants",
        german = "Entfernte Abonnements aktualisieren",
    ),
    "前往订阅页，点击互传按钮，选择生成二维码/链接或扫描导入。" to SupplementalTranslation(
        english = "Open Subscriptions, tap Transfer, then generate a QR/link or scan one to import.",
        traditional = "前往訂閱頁，點選互傳，選擇產生 QR Code／連結或掃描匯入。",
        japanese = "購読画面を開き、転送をタップして QR / リンクを生成するか、スキャンしてインポートします。",
        french = "Ouvrez Abonnements, touchez Transfert, puis générez un QR/lien ou scannez-en un pour importer.",
        german = "Öffne Abonnements, tippe auf Übertragen und erstelle einen QR/Link oder scanne einen zum Import.",
    ),
    "加密" to SupplementalTranslation("Encrypted", "加密", "暗号化", "Chiffré", "Verschlüsselt"),
    "协议" to SupplementalTranslation("Protocol", "協定", "プロトコル", "Protocole", "Protokoll"),
    "发行配置" to SupplementalTranslation("Release profile", "發行設定", "リリース構成", "Profil de publication", "Release-Profil"),
    "发送设备显示的 6 位确认短码" to SupplementalTranslation(
        "Enter the 6-digit confirmation code shown on the sending device",
        "輸入傳送裝置顯示的 6 位確認短碼",
        "送信側に表示された 6 桁の確認コードを入力",
        "Saisissez le code de confirmation à 6 chiffres affiché sur l’appareil émetteur",
        "Gib den 6-stelligen Bestätigungscode des sendenden Geräts ein",
    ),
    "变更安全" to SupplementalTranslation("Change safely", "安全變更", "安全に変更", "Modification sécurisée", "Sicher ändern"),
    "只在本机生效；应用规则优先于这里的规则" to SupplementalTranslation(
        "Applies on this device only; app rules take priority over these rules",
        "僅在本機生效；應用程式規則優先於此處規則",
        "この端末だけに適用され、アプリルールがここでのルールより優先されます",
        "S’applique uniquement à cet appareil ; les règles d’application sont prioritaires",
        "Gilt nur auf diesem Gerät; App-Regeln haben Vorrang vor diesen Regeln",
    ),
    "只展示当前内核实际测到的字段；未测项目保持“—”，不估算 DNS、TLS 或带宽。" to SupplementalTranslation(
        "Only fields measured by the running core are shown; untested fields stay “—”; DNS, TLS and bandwidth are never estimated.",
        "只顯示目前核心實際測得的欄位；未測項目保持「—」，不估算 DNS、TLS 或頻寬。",
        "実行中のコアが実測した項目だけを表示し、未測定項目は「—」のままにします。DNS、TLS、帯域幅は推定しません。",
        "Seuls les champs réellement mesurés par le noyau sont affichés ; les éléments non testés restent « — ».",
        "Nur vom laufenden Core gemessene Felder werden angezeigt; ungetestete bleiben „—“.",
    ),
    "只有你主动生成时才监听局域网；成功读取一次或 5 分钟后自动失效。" to SupplementalTranslation(
        "The LAN is listened to only after you generate a transfer; it expires after one successful read or 5 minutes.",
        "只有你主動產生時才會監聽區域網路；成功讀取一次或 5 分鐘後自動失效。",
        "転送を生成した時だけ LAN を待ち受け、1 回の読み取り成功または 5 分後に失効します。",
        "Le réseau local n’est écouté qu’après votre génération ; le lien expire après une lecture ou 5 minutes.",
        "Das LAN wird nur nach deiner Erzeugung überwacht; nach einem erfolgreichen Lesen oder 5 Minuten verfällt es.",
    ),
    "只模拟本机规则，不执行网络请求" to SupplementalTranslation(
        "Simulates local rules only; no network request is made",
        "只模擬本機規則，不執行網路請求",
        "端末内ルールだけをシミュレートし、ネットワーク要求は行いません",
        "Simule uniquement les règles locales ; aucune requête réseau",
        "Simuliert nur lokale Regeln; keine Netzwerkanfrage",
    ),
    "哈希已验证" to SupplementalTranslation("Hash verified", "雜湊已驗證", "ハッシュ検証済み", "Hachage vérifié", "Hash verifiziert"),
    "固定节点、自动策略、直连或阻止。应用选择始终优先。" to SupplementalTranslation(
        "Fixed node, automatic strategy, direct or block. App choices always take priority.",
        "固定節點、自動策略、直連或阻止。應用程式選擇始終優先。",
        "固定ノード、自動戦略、ダイレクト、ブロックを選択できます。アプリの選択が常に優先されます。",
        "Nœud fixe, stratégie automatique, direct ou blocage. Le choix de l’application est prioritaire.",
        "Fester Knoten, automatische Strategie, direkt oder blockieren. App-Auswahlen haben immer Vorrang.",
    ),
    "域名" to SupplementalTranslation("Domain", "網域", "ドメイン", "Domaine", "Domain"),
    "多次测速并排序" to SupplementalTranslation("Probe several times and sort", "多次測速並排序", "複数回測定して並べ替え", "Tester plusieurs fois et trier", "Mehrfach prüfen und sortieren"),
    "失败" to SupplementalTranslation("Failed", "失敗", "失敗", "Échec", "Fehlgeschlagen"),
    "如何使用" to SupplementalTranslation("How to use", "如何使用", "使い方", "Mode d’emploi", "Verwendung"),
    "安全模式已启用" to SupplementalTranslation("Safe mode enabled", "安全模式已啟用", "セーフモードが有効", "Mode sécurisé activé", "Abgesicherter Modus aktiviert"),
    "对应源码" to SupplementalTranslation("Source commit", "對應原始碼", "対応するソース", "Source correspondante", "Zugehöriger Quellcode"),
    "导入 .weave-policy JSON" to SupplementalTranslation("Import .weave-policy JSON", "匯入 .weave-policy JSON", ".weave-policy JSON をインポート", "Importer le JSON .weave-policy", ".weave-policy-JSON importieren"),
    "导出到另一台设备" to SupplementalTranslation("Export to another device", "匯出到另一台裝置", "別の端末へエクスポート", "Exporter vers un autre appareil", "Auf ein anderes Gerät exportieren"),
    "尚未检测" to SupplementalTranslation("Not tested yet", "尚未檢測", "未測定", "Pas encore testé", "Noch nicht getestet"),
    "已确认" to SupplementalTranslation("Confirmed", "已確認", "確認済み", "Confirmé", "Bestätigt"),
    "已选择" to SupplementalTranslation("Selected", "已選擇", "選択済み", "Sélectionné", "Ausgewählt"),
    "延迟—" to SupplementalTranslation("Latency —", "延遲—", "レイテンシ —", "Latence —", "Latenz —"),
    "开源组件" to SupplementalTranslation("Open-source components", "開源元件", "オープンソースコンポーネント", "Composants open source", "Open-Source-Komponenten"),
    "开源许可、第三方组件与无担保声明" to SupplementalTranslation("Open-source licenses, third-party components and warranty disclaimer", "開源授權、第三方元件與無擔保聲明", "オープンソースライセンス、サードパーティコンポーネント、無保証声明", "Licences open source, composants tiers et absence de garantie", "Open-Source-Lizenzen, Drittkomponenten und Haftungsausschluss"),
    "当前选择" to SupplementalTranslation("Current selection", "目前選擇", "現在の選択", "Sélection actuelle", "Aktuelle Auswahl"),
    "恢复中心不保存订阅 URL、节点凭据或明文配置；解除安全模式后需要你主动重新连接。" to SupplementalTranslation("Recovery Center does not store subscription URLs, node credentials or plaintext configuration; reconnect manually after leaving safe mode."),
    "手动选择节点" to SupplementalTranslation("Choose nodes manually", "手動選擇節點", "ノードを手動選択", "Choisir les nœuds manuellement", "Knoten manuell wählen"),
    "打开" to SupplementalTranslation("Open", "開啟", "開く", "Ouvrir", "Öffnen"),
    "拒绝这个应用的连接" to SupplementalTranslation("Reject this app's connections", "拒絕此應用程式的連線", "このアプリの接続を拒否", "Refuser les connexions de cette application", "Verbindungen dieser App ablehnen"),
    "搜索节点或协议" to SupplementalTranslation("Search nodes or protocols", "搜尋節點或協定", "ノードまたはプロトコルを検索", "Rechercher des nœuds ou protocoles", "Knoten oder Protokolle suchen"),
    "改为 HTTPS 订阅地址（可选）" to SupplementalTranslation("Replace with an HTTPS subscription URL (optional)", "改為 HTTPS 訂閱位址（選填）", "HTTPS 購読 URL に変更（任意）", "Remplacer par une URL d’abonnement HTTPS (facultatif)", "Durch HTTPS-Abonnement-URL ersetzen (optional)"),
    "无效" to SupplementalTranslation("Invalid", "無效", "無効", "Invalide", "Ungültig"),
    "无签名·需复核" to SupplementalTranslation("Unsigned · review required", "無簽名 · 需複核", "未署名・要確認", "Non signé · vérification requise", "Unsigniert · Prüfung erforderlich"),
    "显示订阅地址" to SupplementalTranslation("Show subscription URL", "顯示訂閱位址", "購読 URL を表示", "Afficher l’URL d’abonnement", "Abonnement-URL anzeigen"),
    "更新远程订阅" to SupplementalTranslation("Update remote subscriptions", "更新遠端訂閱", "リモート購読を更新", "Mettre à jour les abonnements distants", "Entfernte Abonnements aktualisieren"),
    "最近一次候选配置与旧配置均未能启动" to SupplementalTranslation("Neither the latest candidate nor the previous configuration could start"),
    "最近一次：未发现可用节点" to SupplementalTranslation("Last run: no usable nodes found", "最近一次：未發現可用節點", "前回：利用可能なノードなし", "Dernier test : aucun nœud utilisable", "Letzter Lauf: keine nutzbaren Knoten"),
    "未命中前面规则的流量使用连接页选择的订阅与节点。" to SupplementalTranslation("Traffic that matches no earlier rule uses the subscription and node selected on the Connection page."),
    "未完成" to SupplementalTranslation("Incomplete", "未完成", "未完了", "Incomplet", "Unvollständig"),
    "未指定应用" to SupplementalTranslation("Unspecified app", "未指定應用程式", "未指定アプリ", "Application non spécifiée", "Nicht zugeordnete App"),
    "未提供流量信息" to SupplementalTranslation("No traffic information", "未提供流量資訊", "通信量情報なし", "Aucune information de trafic", "Keine Verkehrsinformationen"),
    "未检测" to SupplementalTranslation("Not tested", "未檢測", "未測定", "Non testé", "Nicht getestet"),
    "未测试" to SupplementalTranslation("Untested", "未測試", "未テスト", "Non testé", "Nicht getestet"),
    "未知" to SupplementalTranslation("Unknown", "未知", "不明", "Inconnu", "Unbekannt"),
    "未知运营商" to SupplementalTranslation("Unknown carrier", "未知電信商", "不明な通信事業者", "Opérateur inconnu", "Unbekannter Anbieter"),
    "本地发行边界" to SupplementalTranslation("Local release boundary", "本機發行邊界", "ローカルリリースの範囲", "Périmètre de publication locale", "Lokaler Release-Rahmen"),
    "本地导入、哈希校验、可回滚启停" to SupplementalTranslation("Local import, hash verification and rollback", "本機匯入、雜湊校驗、可回滾啟停", "端末内インポート、ハッシュ検証、ロールバック", "Import local, vérification du hachage et retour arrière", "Lokaler Import, Hashprüfung und Rollback"),
    "本地策略" to SupplementalTranslation("Local policy", "本機策略", "ローカルポリシー", "Politique locale", "Lokale Richtlinie"),
    "本机存储" to SupplementalTranslation("Local storage", "本機儲存", "端末内ストレージ", "Stockage local", "Lokaler Speicher"),
    "本机访问" to SupplementalTranslation("Local access", "本機存取", "端末内アクセス", "Accès local", "Lokaler Zugriff"),
    "查看和编辑订阅" to SupplementalTranslation("View and edit subscriptions", "檢視與編輯訂閱", "購読を表示・編集", "Afficher et modifier les abonnements", "Abonnements anzeigen und bearbeiten"),
    "查看失败记录、解除安全模式" to SupplementalTranslation("View failures and leave safe mode", "檢視失敗記錄、解除安全模式", "失敗記録を確認してセーフモードを解除", "Voir les échecs et quitter le mode sécurisé", "Fehler anzeigen und abgesicherten Modus verlassen"),
    "查看首次连接前的独立隐私说明" to SupplementalTranslation("View the separate privacy notice before the first connection", "檢視首次連線前的獨立隱私說明", "初回接続前のプライバシー説明を表示", "Voir l’avis de confidentialité avant la première connexion", "Datenschutzhinweis vor der ersten Verbindung anzeigen"),
    "检测中…" to SupplementalTranslation("Testing…", "檢測中…", "テスト中…", "Test en cours…", "Wird getestet…"),
    "检测结果反映当前 VPN 出口，不等同于网站信誉或绝对匿名性。地区、ASN 和代理标签来自第三方信息服务，可能存在误判。" to SupplementalTranslation("Results reflect the current VPN exit, not site reputation or absolute anonymity. Region, ASN and proxy labels come from third-party information services and may be wrong."),
    "此操作无法撤销。" to SupplementalTranslation("This action cannot be undone.", "此操作無法復原。", "この操作は取り消せません。", "Cette action est irréversible.", "Diese Aktion kann nicht rückgängig gemacht werden."),
    "永久删除" to SupplementalTranslation("Delete permanently", "永久刪除", "完全に削除", "Supprimer définitivement", "Dauerhaft löschen"),
    "没有匹配的节点" to SupplementalTranslation("No matching nodes", "沒有符合的節點", "一致するノードなし", "Aucun nœud correspondant", "Keine passenden Knoten"),
    "没有取得相机预览，请重试" to SupplementalTranslation("No camera preview was available; try again", "沒有取得相機預覽，請重試", "カメラプレビューを取得できませんでした。再試行してください", "Aucun aperçu caméra ; réessayez", "Keine Kameravorschau; bitte erneut versuchen"),
    "没有可添加的启动器应用" to SupplementalTranslation("No launcher apps can be added", "沒有可新增的啟動器應用程式", "追加できるランチャーアプリなし", "Aucune application de lancement à ajouter", "Keine Launcher-App zum Hinzufügen"),
    "注意" to SupplementalTranslation("Notice", "注意", "注意", "Attention", "Hinweis"),
    "测速中" to SupplementalTranslation("Testing", "測速中", "測定中", "Test en cours", "Messung läuft"),
    "测速并排序" to SupplementalTranslation("Test and sort", "測速並排序", "測定して並べ替え", "Tester et trier", "Prüfen und sortieren"),
    "浏览器外部复核" to SupplementalTranslation("Verify externally in a browser", "在瀏覽器中進行外部複核", "ブラウザーで外部検証", "Vérifier dans un navigateur", "Extern im Browser prüfen"),
    "理解并继续" to SupplementalTranslation("Understand and continue", "瞭解並繼續", "理解して続行", "Comprendre et continuer", "Verstanden, weiter"),
    "相机扫码使用系统相机预览；二维码内容由随包 ZXing 本机识别，Weave 不把二维码内容发送到自己的服务器。" to SupplementalTranslation("QR scanning uses the system camera preview; bundled ZXing decodes it locally and Weave never sends the QR content to its servers."),
    "端口" to SupplementalTranslation("Port", "連接埠", "ポート", "Port", "Port"),
    "端口必须为 1–65535" to SupplementalTranslation("Port must be between 1 and 65535", "連接埠必須為 1–65535", "ポートは 1～65535 にしてください", "Le port doit être compris entre 1 et 65535", "Port muss zwischen 1 und 65535 liegen"),
    "第三方可见性" to SupplementalTranslation("Third-party visibility", "第三方可見性", "第三者から見える情報", "Visibilité pour les tiers", "Sichtbarkeit für Dritte"),
    "签名已验证" to SupplementalTranslation("Signature verified", "簽章已驗證", "署名を検証済み", "Signature vérifiée", "Signatur verifiziert"),
    "系统断网保护" to SupplementalTranslation("System kill switch", "系統斷網保護", "システムキルスイッチ", "Coupe-circuit système", "System-Kill-Switch"),
    "系统相机 / ZXing" to SupplementalTranslation("System camera / ZXing", "系統相機 / ZXing", "システムカメラ / ZXing", "Caméra système / ZXing", "Systemkamera / ZXing"),
    "综合 IP 质量" to SupplementalTranslation("Overall IP quality", "綜合 IP 品質", "総合 IP 品質", "Qualité IP globale", "IP-Gesamtqualität"),
    "网络" to SupplementalTranslation("Network", "網路", "ネットワーク", "Réseau", "Netzwerk"),
    "规则只在本机保存和编译，不依赖远程规则服务器" to SupplementalTranslation("Rules are stored and compiled locally; no remote rule server is required."),
    "规则按列表从上到下匹配。域名和 CIDR 会在连接前编译为 Mihomo 规则，不解析、不上传输入内容。" to SupplementalTranslation("Rules match from top to bottom. Domains and CIDRs are compiled into Mihomo rules before connection; inputs are not resolved or uploaded."),
    "解释这条连接" to SupplementalTranslation("Explain this connection", "解釋此連線", "この接続を説明", "Expliquer cette connexion", "Diese Verbindung erklären"),
    "解除安全模式" to SupplementalTranslation("Leave safe mode", "解除安全模式", "セーフモードを解除", "Quitter le mode sécurisé", "Abgesicherten Modus verlassen"),
    "订阅只接受 HTTPS 或你主动选择的本地文件；DNS 使用 DoH/DoT，应用自发的 53、853 和已知公共 DoH 旁路会被拒绝。" to SupplementalTranslation("Subscriptions accept HTTPS or a local file you choose; DNS uses DoH/DoT and app-initiated port 53, 853 and known public DoH bypasses are rejected."),
    "订阅名称" to SupplementalTranslation("Subscription name", "訂閱名稱", "購読名", "Nom de l’abonnement", "Abonnementname"),
    "订阅地址和正文使用 Android Keystore AES-256-GCM 加密；敏感文件不参与系统备份。" to SupplementalTranslation("Subscription URLs and payloads use Android Keystore AES-256-GCM encryption; sensitive files are excluded from system backup."),
    "订阅安全审计" to SupplementalTranslation("Subscription security audit", "訂閱安全稽核", "購読セキュリティ監査", "Audit de sécurité de l’abonnement", "Sicherheitsaudit des Abonnements"),
    "请在 Android VPN 设置中开启 Always-on 与“阻止无 VPN 连接”；Weave 不伪造系统开关状态。" to SupplementalTranslation("Enable Always-on and Block connections without VPN in Android VPN settings; Weave does not fake system toggle state."),
    "请输入域名" to SupplementalTranslation("Enter a domain", "請輸入網域", "ドメインを入力", "Saisissez un domaine", "Domain eingeben"),
    "责任边界" to SupplementalTranslation("Responsibility boundary", "責任邊界", "責任範囲", "Limites de responsabilité", "Verantwortungsgrenze"),
    "质量矩阵" to SupplementalTranslation("Quality matrix", "品質矩陣", "品質マトリクス", "Matrice de qualité", "Qualitätsmatrix"),
    "超时" to SupplementalTranslation("Timeout", "逾時", "タイムアウト", "Délai dépassé", "Zeitüberschreitung"),
    "路由优先级" to SupplementalTranslation("Routing priority", "路由優先順序", "ルーティング優先度", "Priorité de routage", "Routing-Priorität"),
    "运行时" to SupplementalTranslation("Runtime", "執行階段", "ランタイム", "Exécution", "Laufzeit"),
    "运行状态可恢复" to SupplementalTranslation("Runtime state can be recovered", "執行狀態可復原", "ランタイム状態を復元可能", "État d’exécution récupérable", "Laufzeitstatus wiederherstellbar"),
    "还没有本地规则。你可以先添加广告域名、家庭过滤域名或需要直连的企业网段。" to SupplementalTranslation("No local rules yet. Add an ad domain, family-filter domain or enterprise CIDR that should connect directly."),
    "连接 VPN 后可检测当前运行配置中的节点" to SupplementalTranslation("Connect the VPN to test nodes in the current runtime configuration", "連線 VPN 後可檢測目前執行設定中的節點", "VPN に接続すると現在の設定のノードをテストできます", "Connectez le VPN pour tester les nœuds actifs", "Verbinde das VPN, um aktive Knoten zu testen"),
    "连接 VPN 后点击“检测”，开始读取当前代理出口。" to SupplementalTranslation("Connect the VPN, then tap Test to read the current proxy exit."),
    "连接后测速" to SupplementalTranslation("Test after connecting", "連線後測速", "接続後に測定", "Tester après connexion", "Nach Verbindung testen"),
    "连续 3 轮探测，按中位延迟、抖动与丢包综合排序" to SupplementalTranslation("Three probe rounds; sort by median latency, jitter and packet loss"),
    "选择协议" to SupplementalTranslation("Choose protocol", "選擇協定", "プロトコルを選択", "Choisir le protocole", "Protokoll wählen"),
    "选择文件替换" to SupplementalTranslation("Choose a replacement file", "選擇檔案替換", "置換ファイルを選択", "Choisir un fichier de remplacement", "Ersatzdatei wählen"),
    "选择要同步的订阅；同一订阅会先经过安全审计，再原位更新，不会重复堆叠副本。" to SupplementalTranslation("Choose subscriptions to sync; each source is audited and updated in place without duplicate copies."),
    "选择解析策略" to SupplementalTranslation("Choose resolution strategy", "選擇解析策略", "名前解決戦略を選択", "Choisir la stratégie de résolution", "Auflösungsstrategie wählen"),
    "选择订阅" to SupplementalTranslation("Choose subscription", "選擇訂閱", "購読を選択", "Choisir un abonnement", "Abonnement wählen"),
    "遥测" to SupplementalTranslation("Telemetry", "遙測", "テレメトリ", "Télémétrie", "Telemetrie"),
    "重新检测" to SupplementalTranslation("Test again", "重新檢測", "再テスト", "Tester à nouveau", "Erneut testen"),
    "阻止" to SupplementalTranslation("Block", "阻止", "ブロック", "Bloquer", "Blockieren"),
    "隐私与出口检查" to SupplementalTranslation("Privacy and exit checks", "隱私與出口檢查", "プライバシーと出口の確認", "Contrôles de confidentialité et de sortie", "Datenschutz- und Ausgangsprüfung"),
    "隐藏订阅地址" to SupplementalTranslation("Hide subscription URL", "隱藏訂閱位址", "購読 URL を隠す", "Masquer l’URL d’abonnement", "Abonnement-URL ausblenden"),
    "需要相机权限才能扫描二维码" to SupplementalTranslation("Camera permission is required to scan a QR code", "需要相機權限才能掃描 QR Code", "QR コードをスキャンするにはカメラ権限が必要です", "L’autorisation caméra est requise pour scanner un QR", "Kameraberechtigung zum Scannen eines QR-Codes erforderlich"),
    "默认关闭" to SupplementalTranslation("Off by default", "預設關閉", "デフォルトでオフ", "Désactivé par défaut", "Standardmäßig aus"),
    "一次性链接将在 5 分钟或导入一次后失效" to SupplementalTranslation("The one-time link expires after 5 minutes or one import", "一次性連結將在 5 分鐘或匯入一次後失效", "ワンタイムリンクは 5 分または 1 回のインポート後に失効します", "Le lien à usage unique expire après 5 minutes ou un import", "Der Einmal-Link verfällt nach 5 Minuten oder einem Import"),
    "二维码已读取，请输入发送设备显示的 6 位短码后导入" to SupplementalTranslation("QR code read; enter the 6-digit code shown on the sending device to import", "QR Code 已讀取，請輸入傳送裝置顯示的 6 位短碼後匯入", "QR コードを読み取りました。送信側の 6 桁コードを入力してインポートしてください", "QR lu ; saisissez le code à 6 chiffres de l’appareil émetteur pour importer", "QR gelesen; gib zum Import den 6-stelligen Code des sendenden Geräts ein"),
    "分流规则已删除" to SupplementalTranslation("Routing rule deleted", "分流規則已刪除", "ルーティングルールを削除しました", "Règle de routage supprimée", "Routing-Regel gelöscht"),
    "安全模式已解除，可以重新连接" to SupplementalTranslation("Safe mode cleared; you can reconnect", "安全模式已解除，可以重新連線", "セーフモードを解除しました。再接続できます", "Mode sécurisé désactivé ; vous pouvez vous reconnecter", "Abgesicherter Modus beendet; du kannst dich erneut verbinden"),
    "局域网二维码导入失败" to SupplementalTranslation("LAN QR import failed", "區域網路 QR 匯入失敗", "LAN QR インポートに失敗しました", "Échec de l’import du QR LAN", "LAN-QR-Import fehlgeschlagen"),
    "局域网导入失败" to SupplementalTranslation("LAN import failed", "區域網路匯入失敗", "LAN インポートに失敗しました", "Échec de l’import LAN", "LAN-Import fehlgeschlagen"),
    "所有 DNS 端点都无法完成检测" to SupplementalTranslation("All DNS endpoints failed the test", "所有 DNS 端點都無法完成檢測", "すべての DNS エンドポイントをテストできませんでした", "Tous les points DNS ont échoué au test", "Alle DNS-Endpunkte konnten nicht getestet werden"),
    "无法启动局域网导出" to SupplementalTranslation("Unable to start LAN export", "無法啟動區域網路匯出", "LAN エクスポートを開始できません", "Impossible de démarrer l’export LAN", "LAN-Export konnte nicht gestartet werden"),
    "无法打开订阅" to SupplementalTranslation("Unable to open subscription", "無法開啟訂閱", "購読を開けません", "Impossible d’ouvrir l’abonnement", "Abonnement konnte nicht geöffnet werden"),
    "无法读取策略包" to SupplementalTranslation("Unable to read policy pack", "無法讀取策略包", "ポリシーパックを読み取れません", "Impossible de lire le pack de règles", "Richtlinienpaket konnte nicht gelesen werden"),
    "最后一个代理已删除，连接已安全关闭" to SupplementalTranslation("The last proxy was deleted; the connection was safely closed", "最後一個代理已刪除，連線已安全關閉", "最後のプロキシを削除したため接続を安全に終了しました", "Le dernier proxy a été supprimé ; la connexion a été fermée en sécurité", "Der letzte Proxy wurde gelöscht; die Verbindung wurde sicher beendet"),
    "正在关闭 UDP STUN 阻断" to SupplementalTranslation("Disabling UDP STUN blocking", "正在關閉 UDP STUN 阻擋", "UDP STUN ブロックを無効化中", "Désactivation du blocage UDP STUN", "UDP-STUN-Blockierung wird deaktiviert"),
    "正在关闭国内智能直连" to SupplementalTranslation("Disabling mainland smart direct", "正在關閉國內智慧直連", "中国向けスマートダイレクトを無効化中", "Désactivation du direct local intelligent", "Intelligente Inlands-Direktverbindung wird deaktiviert"),
    "正在启用 UDP STUN 阻断" to SupplementalTranslation("Enabling UDP STUN blocking", "正在啟用 UDP STUN 阻擋", "UDP STUN ブロックを有効化中", "Activation du blocage UDP STUN", "UDP-STUN-Blockierung wird aktiviert"),
    "正在启用国内域名与 IP 直连" to SupplementalTranslation("Enabling direct mainland domains and IPs", "正在啟用國內網域與 IP 直連", "中国向けドメインと IP のダイレクトを有効化中", "Activation du direct pour les domaines et IP locaux", "Direkte Inlands-Domains und -IPs werden aktiviert"),
    "正在安全切换加密 DNS" to SupplementalTranslation("Safely switching encrypted DNS", "正在安全切換加密 DNS", "暗号化 DNS を安全に切り替え中", "Changement sécurisé du DNS chiffré", "Verschlüsseltes DNS wird sicher gewechselt"),
    "正在安全切换默认出口" to SupplementalTranslation("Safely switching the default exit", "正在安全切換預設出口", "デフォルト出口を安全に切り替え中", "Changement sécurisé de la sortie par défaut", "Standardausgang wird sicher gewechselt"),
    "正在安全应用 IP 协议设置" to SupplementalTranslation("Safely applying IP protocol settings", "正在安全套用 IP 協定設定", "IP プロトコル設定を安全に適用中", "Application sécurisée des réglages IP", "IP-Protokolleinstellungen werden sicher angewendet"),
    "正在安全应用新的应用分流" to SupplementalTranslation("Safely applying new app routing", "正在安全套用新的應用程式分流", "新しいアプリルーティングを安全に適用中", "Application sécurisée du nouveau routage d’application", "Neues App-Routing wird sicher angewendet"),
    "正在安全应用新的运行模式" to SupplementalTranslation("Safely applying the new routing mode", "正在安全套用新的執行模式", "新しいルーティングモードを安全に適用中", "Application sécurisée du nouveau mode de routage", "Neuer Routing-Modus wird sicher angewendet"),
    "正在应用 DNS 分流策略" to SupplementalTranslation("Applying DNS split policy", "正在套用 DNS 分流策略", "DNS 分離ポリシーを適用中", "Application de la stratégie DNS séparée", "DNS-Aufteilung wird angewendet"),
    "正在应用 DNS 过滤策略" to SupplementalTranslation("Applying DNS filtering policy", "正在套用 DNS 過濾策略", "DNS フィルターポリシーを適用中", "Application de la stratégie de filtrage DNS", "DNS-Filterrichtlinie wird angewendet"),
    "正在应用新的自动节点策略" to SupplementalTranslation("Applying the new automatic node strategy", "正在套用新的自動節點策略", "新しい自動ノード戦略を適用中", "Application de la nouvelle stratégie automatique", "Neue automatische Knotenstrategie wird angewendet"),
    "正在应用新的订阅策略组范围" to SupplementalTranslation("Applying the new subscription group scope", "正在套用新的訂閱策略群組範圍", "新しい購読グループ範囲を適用中", "Application de la portée des groupes d’abonnements", "Neuer Abonnement-Gruppenbereich wird angewendet"),
    "正在应用自定义加密 DNS" to SupplementalTranslation("Applying custom encrypted DNS", "正在套用自訂加密 DNS", "カスタム暗号化 DNS を適用中", "Application du DNS chiffré personnalisé", "Benutzerdefiniertes verschlüsseltes DNS wird angewendet"),
    "正在检查 HTTPS 远程订阅" to SupplementalTranslation("Checking HTTPS remote subscriptions", "正在檢查 HTTPS 遠端訂閱", "HTTPS リモート購読を確認中", "Vérification des abonnements HTTPS distants", "HTTPS-Abonnements werden geprüft"),
    "正在申请 VPN 权限" to SupplementalTranslation("Requesting VPN permission", "正在申請 VPN 權限", "VPN 権限を要求中", "Demande d’autorisation VPN", "VPN-Berechtigung wird angefordert"),
    "没有可刷新的 HTTPS 远程订阅" to SupplementalTranslation("No HTTPS remote subscriptions to refresh", "沒有可重新整理的 HTTPS 遠端訂閱", "更新できる HTTPS リモート購読はありません", "Aucun abonnement HTTPS distant à actualiser", "Keine HTTPS-Abonnements zum Aktualisieren"),
    "短码不匹配：请让发送设备重新显示当前二维码和短码" to SupplementalTranslation("Code mismatch: ask the sending device to show its current QR code and code again", "短碼不相符：請讓傳送裝置重新顯示目前 QR Code 與短碼", "コードが一致しません。送信側で現在の QR コードとコードを再表示してください", "Code différent : demandez à l’appareil émetteur d’afficher à nouveau son QR et son code", "Code stimmt nicht überein: Bitte das sendende Gerät QR und Code erneut anzeigen lassen"),
    "策略包删除失败" to SupplementalTranslation("Policy pack deletion failed", "策略包刪除失敗", "ポリシーパックの削除に失敗しました", "Échec de la suppression du pack de règles", "Löschen des Richtlinienpakets fehlgeschlagen"),
    "策略包导入失败" to SupplementalTranslation("Policy pack import failed", "策略包匯入失敗", "ポリシーパックのインポートに失敗しました", "Échec de l’import du pack de règles", "Import des Richtlinienpakets fehlgeschlagen"),
    "策略包已停用" to SupplementalTranslation("Policy pack disabled", "策略包已停用", "ポリシーパックを無効化しました", "Pack de règles désactivé", "Richtlinienpaket deaktiviert"),
    "策略包已删除" to SupplementalTranslation("Policy pack deleted", "策略包已刪除", "ポリシーパックを削除しました", "Pack de règles supprimé", "Richtlinienpaket gelöscht"),
    "策略包已启用" to SupplementalTranslation("Policy pack enabled", "策略包已啟用", "ポリシーパックを有効化しました", "Pack de règles activé", "Richtlinienpaket aktiviert"),
    "策略包状态更新失败" to SupplementalTranslation("Policy pack state update failed", "策略包狀態更新失敗", "ポリシーパックの状態更新に失敗しました", "Échec de la mise à jour du pack de règles", "Status des Richtlinienpakets konnte nicht aktualisiert werden"),
    "规则删除失败" to SupplementalTranslation("Rule deletion failed", "規則刪除失敗", "ルールの削除に失敗しました", "Échec de la suppression de la règle", "Löschen der Regel fehlgeschlagen"),
    "规则无效" to SupplementalTranslation("Invalid rule", "規則無效", "無効なルール", "Règle invalide", "Ungültige Regel"),
    "规则更新失败" to SupplementalTranslation("Rule update failed", "規則更新失敗", "ルールの更新に失敗しました", "Échec de la mise à jour de la règle", "Aktualisieren der Regel fehlgeschlagen"),
    "订阅修改失败" to SupplementalTranslation("Subscription edit failed", "訂閱修改失敗", "購読の編集に失敗しました", "Échec de la modification de l’abonnement", "Bearbeiten des Abonnements fehlgeschlagen"),
    "订阅删除失败" to SupplementalTranslation("Subscription deletion failed", "訂閱刪除失敗", "購読の削除に失敗しました", "Échec de la suppression de l’abonnement", "Löschen des Abonnements fehlgeschlagen"),
    "订阅名称已更新" to SupplementalTranslation("Subscription name updated", "訂閱名稱已更新", "購読名を更新しました", "Nom de l’abonnement mis à jour", "Abonnementname aktualisiert"),
    "订阅导入失败" to SupplementalTranslation("Subscription import failed", "訂閱匯入失敗", "購読のインポートに失敗しました", "Échec de l’import de l’abonnement", "Import des Abonnements fehlgeschlagen"),
    "该订阅未被当前运行配置加载；设为出口后可检测" to SupplementalTranslation("This subscription is not loaded by the current runtime; set it as an exit to test it", "此訂閱未被目前執行設定載入；設為出口後可檢測", "この購読は現在の設定に読み込まれていません。出口に設定するとテストできます", "Cet abonnement n’est pas chargé ; définissez-le comme sortie pour le tester", "Dieses Abonnement ist nicht geladen; als Ausgang festlegen, um es zu testen"),
    "连接 VPN 后才能检测代理出口" to SupplementalTranslation("Connect the VPN before testing the proxy exit", "連線 VPN 後才能檢測代理出口", "プロキシ出口をテストするには VPN に接続してください", "Connectez le VPN avant de tester la sortie proxy", "Verbinde das VPN, bevor du den Proxy-Ausgang testest"),
    "连接 VPN 后才能通过当前出口测试节点" to SupplementalTranslation("Connect the VPN before testing nodes through the current exit", "連線 VPN 後才能透過目前出口測試節點", "現在の出口を使ってノードをテストするには VPN に接続してください", "Connectez le VPN avant de tester les nœuds via la sortie actuelle", "Verbinde das VPN, bevor du Knoten über den aktuellen Ausgang testest"),
    "节点检测失败，已保留上次结果" to SupplementalTranslation("Node test failed; the previous result was kept", "節點檢測失敗，已保留上次結果", "ノードテストに失敗したため前回の結果を保持しました", "Échec du test des nœuds ; le résultat précédent est conservé", "Knotentest fehlgeschlagen; vorheriges Ergebnis beibehalten"),
    "未被当前运行配置加载" to SupplementalTranslation("Not loaded by the current runtime", "未被目前執行設定載入", "現在の設定に読み込まれていません", "Non chargé par la configuration actuelle", "Nicht von der aktuellen Laufzeit geladen"),
    "Mihomo 原生库未能加载，已拒绝建立 VPN" to SupplementalTranslation("The Mihomo native library could not load; VPN startup was refused", "Mihomo 原生函式庫無法載入，已拒絕建立 VPN", "Mihomo ネイティブライブラリを読み込めないため VPN の起動を拒否しました", "La bibliothèque native Mihomo n’a pas pu être chargée ; démarrage VPN refusé", "Die native Mihomo-Bibliothek konnte nicht geladen werden; VPN-Start verweigert"),
    "正在载入该订阅，运行配置更新后请再次测速" to SupplementalTranslation("Loading this subscription; test again after the runtime configuration updates", "正在載入此訂閱，執行設定更新後請再次測速", "この購読を読み込み中です。実行設定の更新後に再度測定してください", "Chargement de cet abonnement ; retestez après la mise à jour de la configuration", "Dieses Abonnement wird geladen; nach der Aktualisierung der Laufzeit erneut messen"),
    "订阅已删除" to SupplementalTranslation("Subscription deleted", "訂閱已刪除", "購読を削除しました", "Abonnement supprimé", "Abonnement gelöscht"),
    "局域网订阅已导入" to SupplementalTranslation("LAN subscriptions imported", "區域網路訂閱已匯入", "LAN 購読をインポートしました", "Abonnements LAN importés", "LAN-Abonnements importiert"),
    "策略包已导入" to SupplementalTranslation("Policy pack imported", "策略包已匯入", "ポリシーパックをインポートしました", "Pack de règles importé", "Richtlinienpaket importiert"),
    "策略包状态已变更" to SupplementalTranslation("Policy pack state changed", "策略包狀態已變更", "ポリシーパックの状態を変更しました", "État du pack de règles modifié", "Status des Richtlinienpakets geändert"),
    "本地路由规则已添加" to SupplementalTranslation("Local routing rule added", "本機路由規則已新增", "ローカルルーティングルールを追加しました", "Règle de routage locale ajoutée", "Lokale Routing-Regel hinzugefügt"),
    "本地路由规则已更新" to SupplementalTranslation("Local routing rule updated", "本機路由規則已更新", "ローカルルーティングルールを更新しました", "Règle de routage locale mise à jour", "Lokale Routing-Regel aktualisiert"),
    "本地路由规则已删除" to SupplementalTranslation("Local routing rule deleted", "本機路由規則已刪除", "ローカルルーティングルールを削除しました", "Règle de routage locale supprimée", "Lokale Routing-Regel gelöscht"),
    "订阅刷新完成" to SupplementalTranslation("Subscription refresh complete", "訂閱重新整理完成", "購読の更新が完了しました", "Actualisation des abonnements terminée", "Abonnement-Aktualisierung abgeschlossen"),
    "订阅已导入" to SupplementalTranslation("Subscription imported", "訂閱已匯入", "購読をインポートしました", "Abonnement importé", "Abonnement importiert"),
    "失败的候选配置不会覆盖上一份可用配置；运行快照只保留在应用私有缓存中。" to SupplementalTranslation("A failed candidate configuration never replaces the last usable one; runtime snapshots stay only in the app-private cache."),
    "候选订阅没有节点" to SupplementalTranslation("The candidate subscription has no nodes", "候選訂閱沒有節點", "候補の購読にノードがありません", "L’abonnement candidat ne contient aucun nœud", "Das Kandidaten-Abonnement enthält keine Knoten"),
    "节点数量骤降" to SupplementalTranslation("Node count dropped sharply", "節點數量驟降", "ノード数が急減しました", "Forte baisse du nombre de nœuds", "Knotenanzahl stark gesunken"),
    "移除了超过一半节点" to SupplementalTranslation("More than half of the nodes were removed", "移除了超過一半節點", "ノードの半数以上が削除されました", "Plus de la moitié des nœuds ont été supprimés", "Mehr als die Hälfte der Knoten wurde entfernt"),
    "订阅安全审计通过" to SupplementalTranslation("Subscription security audit passed", "訂閱安全稽核通過", "購読セキュリティ監査に合格しました", "Audit de sécurité de l’abonnement réussi", "Sicherheitsaudit des Abonnements bestanden"),
    "应用规则" to SupplementalTranslation("App rule", "應用程式規則", "アプリルール", "Règle d’application", "App-Regel"),
    "本地规则" to SupplementalTranslation("Local rule", "本機規則", "ローカルルール", "Règle locale", "Lokale Regel"),
    "应用名称（可选）" to SupplementalTranslation("App name (optional)", "應用程式名稱（選填）", "アプリ名（任意）", "Nom de l’application (facultatif)", "App-Name (optional)"),
    "应用包名（用于匹配规则）" to SupplementalTranslation("App package name (used for rule matching)", "應用程式套件名稱（用於匹配規則）", "アプリパッケージ名（ルール照合用）", "Nom du paquet de l’application (pour les règles)", "App-Paketname (für Regelabgleich)"),
    "当前" to SupplementalTranslation("Current", "目前", "現在", "Actuel", "Aktuell"),
    "未知地区" to SupplementalTranslation("Unknown region", "未知地區", "不明な地域", "Région inconnue", "Unbekannte Region"),
    "从另一台设备导入" to SupplementalTranslation("Import from another device", "從另一部裝置匯入", "別の端末からインポート", "Importer depuis un autre appareil", "Von einem anderen Gerät importieren"),
    "支持 HTTPS、URI/Base64、Clash YAML、sing-box JSON、二维码和本地文件；内容仅在本机校验并用 Android Keystore 加密保存。" to SupplementalTranslation(
        "Supports HTTPS, URI/Base64, Clash YAML, sing-box JSON, QR codes and local files; content is validated locally and encrypted with Android Keystore.",
        "支援 HTTPS、URI/Base64、Clash YAML、sing-box JSON、QR Code 與本機檔案；內容只在本機驗證並以 Android Keystore 加密保存。",
        "HTTPS、URI/Base64、Clash YAML、sing-box JSON、QR コード、ローカルファイルに対応。内容は端末内で検証し Android Keystore で暗号化保存します。",
        "Prend en charge HTTPS, URI/Base64, Clash YAML, sing-box JSON, QR et fichiers locaux ; le contenu est vérifié localement et chiffré avec Android Keystore.",
        "Unterstützt HTTPS, URI/Base64, Clash YAML, sing-box JSON, QR-Codes und lokale Dateien; Inhalte werden lokal geprüft und mit Android Keystore verschlüsselt.",
    ),
    "二维码和链接只传输端到端加密密文；成功导入一次或 5 分钟后自动失效。" to SupplementalTranslation(
        "QR codes and links carry only end-to-end encrypted ciphertext; they expire after one successful import or 5 minutes.",
        "QR Code 與連結只傳輸端對端加密密文；成功匯入一次或 5 分鐘後自動失效。",
        "QR コードとリンクはエンドツーエンド暗号文だけを転送し、1 回のインポート成功または 5 分で失効します。",
        "Les QR et liens ne transportent que du chiffré de bout en bout ; ils expirent après un import réussi ou 5 minutes.",
        "QR-Codes und Links übertragen nur Ende-zu-Ende-Chiffretext; sie verfallen nach einem erfolgreichen Import oder 5 Minuten.",
    ),
    "二维码已读入，请核对短码后再次点击导入" to SupplementalTranslation(
        "QR code loaded; verify the short code and tap Import again",
        "QR Code 已讀入，請核對短碼後再次點選匯入",
        "QR コードを読み込みました。短いコードを確認して、もう一度インポートをタップしてください",
        "QR chargé ; vérifiez le code court puis touchez à nouveau Importer",
        "QR geladen; prüfe den Kurzcode und tippe erneut auf Importieren",
    ),
    "默认出口将切换到其他订阅；没有其他订阅时保持断开，避免静默直连。" to SupplementalTranslation(
        "The default exit will switch to another subscription; with none available, Weave stays disconnected to avoid silent direct access.",
        "預設出口將切換至其他訂閱；沒有其他訂閱時保持中斷連線，避免靜默直連。",
        "デフォルト出口は別の購読に切り替わります。利用可能な購読がなければ、意図しない直接接続を避けて切断状態を保ちます。",
        "La sortie par défaut basculera vers un autre abonnement ; s’il n’y en a aucun, Weave reste déconnecté pour éviter une connexion directe silencieuse.",
        "Der Standardausgang wechselt zu einem anderen Abonnement; wenn keines vorhanden ist, bleibt Weave getrennt, um eine stille Direktverbindung zu vermeiden.",
    ),
    "1 · 应用规则" to SupplementalTranslation("1 · App rules", "1 · 應用程式規則", "1 · アプリルール", "1 · Règles d’application", "1 · App-Regeln"),
    "2 · 本地域名 / IP" to SupplementalTranslation("2 · Local domains / IP", "2 · 本機網域 / IP", "2 · ローカルドメイン / IP", "2 · Domaines / IP locaux", "2 · Lokale Domains / IP"),
    "3 · 国内智能直连" to SupplementalTranslation("3 · Mainland smart direct", "3 · 國內智慧直連", "3 · 中国向けスマートダイレクト", "3 · Direct local intelligent", "3 · Intelligente Inlands-Direktverbindung"),
    "4 · 默认出口" to SupplementalTranslation("4 · Default exit", "4 · 預設出口", "4 · デフォルト出口", "4 · Sortie par défaut", "4 · Standardausgang"),
    "每个应用都能选择不同订阅中的固定节点、自动策略、直连或阻止。点按一项可预览切换。" to SupplementalTranslation(
        "Each app can choose a fixed node from any subscription, an automatic strategy, direct access or block; tap an option to preview the switch.",
        "每個應用程式都能選擇不同訂閱中的固定節點、自動策略、直連或阻止；點選選項可預覽切換。",
        "各アプリは任意の購読から固定ノード、自動戦略、直接接続、ブロックを選べます。タップすると切替をプレビューします。",
        "Chaque app peut choisir un nœud fixe de n’importe quel abonnement, une stratégie automatique, le direct ou le blocage ; touchez une option pour prévisualiser.",
        "Jede App kann einen festen Knoten aus beliebigen Abonnements, eine automatische Strategie, Direktzugriff oder Blockieren wählen; antippen, um den Wechsel zu prüfen.",
    ),
    "当前按“应用规则 > 本地域名/IP规则 > 默认出口”匹配；路由解释可在连接前预览命中结果。" to SupplementalTranslation(
        "Matching order is App rules > local domain/IP rules > default exit; Route explanation previews the match before connecting.",
        "目前依「應用程式規則 > 本機網域/IP 規則 > 預設出口」匹配；路由解釋可在連線前預覽命中結果。",
        "照合順序はアプリルール > ローカルドメイン/IP ルール > デフォルト出口。接続前にルート説明で結果を確認できます。",
        "L’ordre est règles d’application > règles domaine/IP locales > sortie par défaut ; l’explication du routage prévisualise le résultat avant connexion.",
        "Die Reihenfolge lautet App-Regeln > lokale Domain/IP-Regeln > Standardausgang; die Routing-Erklärung zeigt das Ergebnis vor dem Verbinden.",
    ),
    "为执行按应用分流，Weave 会在设备内读取连接所属应用、DNS 请求和路由元数据。" to SupplementalTranslation(
        "To route per app, Weave reads the owning app, DNS requests and routing metadata on the device.",
        "為執行按應用程式分流，Weave 會在裝置內讀取連線所屬應用程式、DNS 請求與路由中繼資料。",
        "アプリごとのルーティングのため、Weave は端末内で接続元アプリ、DNS リクエスト、ルーティングメタデータを読み取ります。",
        "Pour le routage par app, Weave lit sur l’appareil l’application propriétaire, les requêtes DNS et les métadonnées de routage.",
        "Für App-Routing liest Weave auf dem Gerät die zugehörige App, DNS-Anfragen und Routing-Metadaten.",
    ),
    "系统级保护 · 需同时开启 Always-on 与阻止无 VPN 连接" to SupplementalTranslation(
        "System protection · enable Always-on and Block connections without VPN together",
        "系統級保護 · 需同時開啟 Always-on 與阻止無 VPN 連線",
        "システム保護 · Always-on と「VPN なしの接続をブロック」を同時に有効化",
        "Protection système · activez Always-on et Bloquer les connexions sans VPN",
        "Systemschutz · Always-on und Verbindungen ohne VPN blockieren gemeinsam aktivieren",
    ),
    "结果是当前网络到加密 DNS 服务端点的实测 RTT；不代表节点延迟，也不会伪造 65553ms 之类的无效值。" to SupplementalTranslation(
        "The result is measured RTT from this network to the encrypted DNS endpoint; it is not node latency and never fabricates invalid values such as 65553 ms.",
        "結果是目前網路到加密 DNS 服務端點的實測 RTT；不代表節點延遲，也不會偽造 65553ms 等無效值。",
        "結果は現在のネットワークから暗号化 DNS エンドポイントまでの実測 RTT です。ノード遅延ではなく、65553ms のような無効値も作りません。",
        "Le résultat est le RTT mesuré entre ce réseau et le point DNS chiffré ; ce n’est pas la latence du nœud et aucune valeur invalide comme 65553 ms n’est fabriquée.",
        "Das Ergebnis ist die gemessene RTT dieses Netzwerks zum verschlüsselten DNS-Endpunkt; es ist keine Knotenlatenz und erfindet keine ungültigen Werte wie 65553 ms.",
    ),
    "继续表示你理解上述数据路径；这不会替代 Android 随后显示的系统 VPN 授权。" to SupplementalTranslation(
        "Continue to confirm that you understand the data path; this does not replace Android’s subsequent system VPN authorization.",
        "繼續表示你理解上述資料路徑；這不會取代 Android 隨後顯示的系統 VPN 授權。",
        "続行するとデータ経路を理解したことを確認します。Android が続けて表示するシステム VPN 許可の代わりにはなりません。",
        "Continuer confirme que vous comprenez le chemin des données ; cela ne remplace pas l’autorisation VPN système affichée ensuite par Android.",
        "Fortfahren bestätigt dein Verständnis des Datenpfads; es ersetzt nicht die anschließende systemweite VPN-Autorisierung von Android.",
    ),
    "配置只在应用私有目录中短期解密，断开或失败后清理。应用不启用外部控制端口。" to SupplementalTranslation("Configuration is decrypted briefly in the app-private directory, then cleared after disconnect or failure; no external control port is enabled."),
    "默认使用 APK 内固定并校验哈希的 GeoIP / GeoSite 数据；关闭后恢复全量代理，不静默联网更新。" to SupplementalTranslation("Uses hash-verified GeoIP / GeoSite data bundled in the APK by default; disabling it restores full proxying and never silently updates over the network."),
    "当前版本不把访问域名、应用规则、节点凭据或流量记录发送到 Weave 服务器。" to SupplementalTranslation("This version does not send visited domains, app rules, node credentials or traffic records to Weave servers."),
    "当前版本没有广告、统计或第三方崩溃上报 SDK，也不上传访问域名、节点地址和应用规则。" to SupplementalTranslation("This version has no ads, analytics or third-party crash-reporting SDK, and uploads no visited domains, node addresses or app rules."),
    "应用内探测无法替代浏览器 DNS、IPv6 或 WebRTC 测试；点击后交给系统浏览器打开公开测试站。" to SupplementalTranslation("In-app probes cannot replace browser DNS, IPv6 or WebRTC tests; tap to open a public test site in the system browser."),
    "已确认表示来自本机状态或已写入的规则；未知/未测试必须用外部 DNS、IPv6、WebRTC 和 QUIC 测试站复核。" to SupplementalTranslation("Confirmed means evidence from local state or saved rules; unknown/untested items must be checked with external DNS, IPv6, WebRTC and QUIC test sites."),
    "尚未导入策略包。策略包必须包含格式、版本、规则和 SHA-256；无签名包会标记为需复核。" to SupplementalTranslation("No policy pack imported. A pack must include format, version, rules and SHA-256; unsigned packs are marked for review."),
    "本机加密保存的域名、关键词和 CIDR 规则；不联网、不上传，可由路由解释预览。" to SupplementalTranslation("Domains, keywords and CIDR rules are encrypted locally; no network or upload is used, and Route explanation can preview them."),
    "每个公开发行版应在同一 GitHub Release 附近提供对应源码、构建说明、校验和与第三方清单。" to SupplementalTranslation("Each public release should provide matching source, build instructions, checksums and the third-party inventory near the same GitHub Release."),
    "本仓库使用 local-open-source 配置：没有 Weave 云端控制、节点中继、内置凭据或应用远程更新；主动选择的第三方端点仍按隐私说明工作。" to SupplementalTranslation("This repository uses the local-open-source profile: no Weave cloud control, node relay, embedded credentials or app remote updates; third-party endpoints you choose still follow their privacy terms."),
    "HTTP 只承载 AES-256-GCM 密文，密钥保存在 weave:// 链接的 fragment 中，不随 HTTP 请求发送。" to SupplementalTranslation("HTTP carries only AES-256-GCM ciphertext; the key is kept in the weave:// link fragment and is not sent in the HTTP request."),
    "Weave 会建立本地 VPN 接口，以便把设备流量交给你选择的规则和代理节点。" to SupplementalTranslation("Weave creates a local VPN interface so device traffic can follow your selected rules and proxy nodes."),
    "系统级保护" to SupplementalTranslation("System protection", "系統級保護", "システム保護", "Protection système", "Systemschutz"),
    "GPL-3.0-or-later。你可以运行、研究、修改和重新分发；本软件不提供任何担保。" to SupplementalTranslation("GPL-3.0-or-later. You may run, study, modify and redistribute this software; it comes without warranty."),
    "GPL-3.0。发行内核来自仓库锁定的源码提交，并记录构建补丁与 SHA-256。" to SupplementalTranslation("GPL-3.0. The released core comes from the repository-pinned source commit with build patches and SHA-256 recorded."),
    "Apache-2.0。用于 Android UI、生命周期和系统兼容。" to SupplementalTranslation("Apache-2.0. Used for Android UI, lifecycle and platform compatibility."),
    "稳定" to SupplementalTranslation("Stable", "穩定", "安定", "Stable", "Stabil"),
    "一般" to SupplementalTranslation("Fair", "一般", "普通", "Moyen", "Mittel"),
    "波动" to SupplementalTranslation("Variable", "波動", "変動", "Variable", "Schwankend"),
    "低（仅健康探测）" to SupplementalTranslation("Low (health probes only)", "低（僅健康探測）", "低（ヘルスプローブのみ）", "Faible (sondes de santé uniquement)", "Niedrig (nur Gesundheitsprüfungen)"),
    "VPN 隧道" to SupplementalTranslation("VPN tunnel", "VPN 通道", "VPN トンネル", "Tunnel VPN", "VPN-Tunnel"),
    "加密 DNS 配置" to SupplementalTranslation("Encrypted DNS configuration", "加密 DNS 設定", "暗号化 DNS 設定", "Configuration DNS chiffrée", "Verschlüsselte DNS-Konfiguration"),
    "DNS 旁路拒绝" to SupplementalTranslation("DNS bypass rejection", "DNS 旁路拒絕", "DNS バイパス拒否", "Refus des contournements DNS", "DNS-Umgehungen abgewiesen"),
    "DNS 旁路保护" to SupplementalTranslation("DNS bypass protection", "DNS 旁路保護", "DNS バイパス保護", "Protection contre le contournement DNS", "DNS-Umgehungsschutz"),
    "广告 / 家庭过滤" to SupplementalTranslation("Ad / family filtering", "廣告 / 家庭過濾", "広告 / ファミリーフィルター", "Filtrage publicitaire / familial", "Werbe- / Familienfilter"),
    "IPv6 旁路" to SupplementalTranslation("IPv6 bypass", "IPv6 旁路", "IPv6 バイパス", "Contournement IPv6", "IPv6-Umgehung"),
    "WebRTC / STUN" to SupplementalTranslation("WebRTC / STUN", "WebRTC / STUN", "WebRTC / STUN", "WebRTC / STUN", "WebRTC / STUN"),
    "隐式直连" to SupplementalTranslation("Implicit direct access", "隱式直連", "暗黙の直接接続", "Connexion directe implicite", "Impliziter Direktzugriff"),
    "断开后清理" to SupplementalTranslation("Cleanup after disconnect", "中斷連線後清理", "切断後のクリーンアップ", "Nettoyage après déconnexion", "Bereinigung nach Trennung"),
    "本地运行状态确认 TUN 已建立" to SupplementalTranslation("Local runtime confirms that the TUN is established"),
    "运行状态异常；不要把当前连接视为受保护" to SupplementalTranslation("Runtime state is abnormal; do not treat the current connection as protected"),
    "正在建立，尚未完成检查" to SupplementalTranslation("Establishing; checks are not complete yet"),
    "未连接，无法确认设备流量受保护" to SupplementalTranslation("Not connected; device traffic protection cannot be confirmed"),
    "自定义配置为空" to SupplementalTranslation("Custom configuration is empty"),
    "本机规则拒绝应用的明文 53、DoT/DoQ 853、已知公共 DoH 与公共 DNS 地址；自定义浏览器 DoH 仍需手动关闭" to SupplementalTranslation("Local rules reject app plaintext port 53, DoT/DoQ 853, known public DoH and public DNS addresses; custom browser DoH must still be disabled manually."),
    "本地拒绝规则已启用；应用自带 DoH/DoT 仍需单独验证" to SupplementalTranslation("Local reject rules are enabled; the app's own DoH/DoT still needs separate verification"),
    "当前配置未启用本地过滤规则" to SupplementalTranslation("Local filtering rules are not enabled in the current configuration"),
    "运行规则拒绝 IPv6；仍建议在真实网络中复测" to SupplementalTranslation("Runtime rules reject IPv6; retest on a real network anyway"),
    "双栈模式；未执行外部 IPv6 泄漏测试" to SupplementalTranslation("Dual-stack mode; no external IPv6 leak test was run"),
    "UDP STUN 端口规则已启用；这不等于所有 WebRTC 实现都被禁用" to SupplementalTranslation("UDP STUN port rules are enabled; this does not mean every WebRTC implementation is disabled"),
    "未启用 STUN 阻断，浏览器策略可能继续暴露候选地址" to SupplementalTranslation("STUN blocking is disabled; browser policy may continue exposing candidate addresses"),
    "全局直连已选择，代理不会接管流量" to SupplementalTranslation("Global direct mode is selected; the proxy will not take over traffic"),
    "默认出口为显式直连" to SupplementalTranslation("The default exit is explicitly direct"),
    "至少一个应用规则选择了显式直连" to SupplementalTranslation("At least one app rule explicitly selects direct access"),
    "未发现显式直连；真实旁路仍需外部测试" to SupplementalTranslation("No explicit direct access found; real bypasses still require external testing"),
    "Weave 会在服务停止时清理运行配置；本报告不读取系统抓包结果" to SupplementalTranslation("Weave clears the runtime configuration when the service stops; this report does not read system packet captures"),
    "IPv4 出口" to SupplementalTranslation("IPv4 exit", "IPv4 出口", "IPv4 出口", "Sortie IPv4", "IPv4-Ausgang"),
    "IPv6 出口" to SupplementalTranslation("IPv6 exit", "IPv6 出口", "IPv6 出口", "Sortie IPv6", "IPv6-Ausgang"),
    "出口一致性" to SupplementalTranslation("Exit consistency", "出口一致性", "出口の一貫性", "Cohérence de la sortie", "Ausgangskonsistenz"),
    "代理 / 数据中心标签" to SupplementalTranslation("Proxy / datacenter labels", "代理 / 資料中心標籤", "プロキシ / データセンターラベル", "Étiquettes proxy / centre de données", "Proxy-/Rechenzentrumskennzeichnung"),
    "HTTPS 可达性" to SupplementalTranslation("HTTPS reachability", "HTTPS 可達性", "HTTPS 到達性", "Accessibilité HTTPS", "HTTPS-Erreichbarkeit"),
    "DNS 泄漏" to SupplementalTranslation("DNS leak", "DNS 洩漏", "DNS リーク", "Fuite DNS", "DNS-Leak"),
    "WebRTC 地址" to SupplementalTranslation("WebRTC address", "WebRTC 位址", "WebRTC アドレス", "Adresse WebRTC", "WebRTC-Adresse"),
    "HTTPS 端点可达" to SupplementalTranslation("HTTPS endpoint reachable", "HTTPS 端點可達", "HTTPS エンドポイントに到達可能", "Point HTTPS accessible", "HTTPS-Endpunkt erreichbar"),
    "响应内容不完整" to SupplementalTranslation("Response content is incomplete"),
    "请求失败" to SupplementalTranslation("Request failed", "請求失敗", "リクエストに失敗しました", "Échec de la requête", "Anfrage fehlgeschlagen"),
    "未取得第三方安全标签" to SupplementalTranslation("No third-party security label obtained"),
    "未发现该服务标记的代理、VPN、Tor 或托管出口" to SupplementalTranslation("The service did not mark this as a proxy, VPN, Tor or hosted exit"),
    "应用内 HTTPS 探测无法证明浏览器或系统 DNS 是否泄漏，请用外部 DNS 测试页复核" to SupplementalTranslation("In-app HTTPS probes cannot prove whether browser or system DNS leaks; verify with an external DNS test page"),
    "WebRTC 需要浏览器 JS 和 UDP 候选测试；本报告不把 HTTP 结果冒充 WebRTC 结论" to SupplementalTranslation("WebRTC requires browser JavaScript and UDP candidate tests; this report does not present HTTP results as a WebRTC conclusion"),
    "Mihomo 原生库未能加载" to SupplementalTranslation("The Mihomo native library could not load", "Mihomo 原生函式庫無法載入", "Mihomo ネイティブライブラリを読み込めません", "La bibliothèque native Mihomo n’a pas pu être chargée", "Die native Mihomo-Bibliothek konnte nicht geladen werden"),
    "IP 质量检测失败" to SupplementalTranslation("IP quality test failed", "IP 品質檢測失敗", "IP 品質テストに失敗しました", "Échec du test de qualité IP", "IP-Qualitätstest fehlgeschlagen"),
    "请输入发送设备显示的 6 位短码" to SupplementalTranslation("Enter the 6-digit code shown on the sending device", "請輸入傳送裝置顯示的 6 位短碼", "送信側に表示された 6 桁コードを入力してください", "Saisissez le code à 6 chiffres affiché par l’appareil émetteur", "Gib den auf dem sendenden Gerät angezeigten 6-stelligen Code ein"),
    "请在 Android VPN 设置中同时开启 Always-on 和“阻止无 VPN 连接”；应用不能读取或代替系统开关" to SupplementalTranslation("Enable Always-on and Block connections without VPN together in Android VPN settings; the app cannot read or replace system switches."),
    "不同探测端点返回了多个出口地址；可能存在代理链或网络切换" to SupplementalTranslation("Different probe endpoints returned multiple exit addresses; a proxy chain or network change may be present."),
    "公开探测端点返回的出口地址没有明显冲突" to SupplementalTranslation("Public probe endpoints returned exit addresses without an obvious conflict."),
    "出口信息" to SupplementalTranslation("Exit information", "出口資訊", "出口情報", "Informations de sortie", "Ausgangsinformationen"),
    "出口信息服务没有返回可比对的地址" to SupplementalTranslation("The exit information service returned no address to compare."),
    "边缘出口" to SupplementalTranslation("Edge exit", "邊緣出口", "エッジ出口", "Sortie périphérique", "Edge-Ausgang"),
    "托管" to SupplementalTranslation("Hosting", "託管", "ホスティング", "Hébergement", "Hosting"),
    "探测响应过大" to SupplementalTranslation("Probe response is too large"),
    "探测端点必须使用 HTTPS" to SupplementalTranslation("Probe endpoints must use HTTPS"),
    "无法创建 HTTPS 连接" to SupplementalTranslation("Unable to create an HTTPS connection"),
    "未取得 IPv6 公网地址；这不能单独证明没有 IPv6 泄漏" to SupplementalTranslation("No public IPv6 address was obtained; this alone cannot prove that there is no IPv6 leak."),
    "不支持的策略包格式" to SupplementalTranslation("Unsupported policy pack format", "不支援的策略包格式", "サポートされていないポリシーパック形式", "Format de pack de règles non pris en charge", "Nicht unterstütztes Richtlinienpaketformat"),
    "无法保存策略包" to SupplementalTranslation("Unable to save policy pack"),
    "策略包 SHA-256 校验失败" to SupplementalTranslation("Policy pack SHA-256 verification failed"),
    "策略包 id 无效" to SupplementalTranslation("Invalid policy pack ID"),
    "策略包不存在" to SupplementalTranslation("Policy pack does not exist"),
    "策略包不是有效 JSON" to SupplementalTranslation("Policy pack is not valid JSON"),
    "策略包名称无效" to SupplementalTranslation("Invalid policy pack name"),
    "策略包必须为 1–512 KiB" to SupplementalTranslation("Policy pack must be 1–512 KiB"),
    "策略包版本无效" to SupplementalTranslation("Invalid policy pack version"),
    "策略包签名校验失败" to SupplementalTranslation("Policy pack signature verification failed"),
    "策略包缺少 rules" to SupplementalTranslation("Policy pack is missing rules"),
    "策略包缺少有效 SHA-256" to SupplementalTranslation("Policy pack is missing a valid SHA-256"),
    "策略包说明过长" to SupplementalTranslation("Policy pack description is too long"),
    "完整域名" to SupplementalTranslation("Full domain", "完整網域", "完全修飾ドメイン", "Domaine complet", "Vollständige Domain"),
    "域名后缀" to SupplementalTranslation("Domain suffix", "網域後綴", "ドメインサフィックス", "Suffixe de domaine", "Domain-Suffix"),
    "域名关键词" to SupplementalTranslation("Domain keyword", "網域關鍵字", "ドメインキーワード", "Mot-clé de domaine", "Domain-Schlüsselwort"),
    "IPv4 网段" to SupplementalTranslation("IPv4 CIDR", "IPv4 網段", "IPv4 CIDR", "CIDR IPv4", "IPv4-CIDR"),
    "IPv6 网段" to SupplementalTranslation("IPv6 CIDR", "IPv6 網段", "IPv6 CIDR", "CIDR IPv6", "IPv6-CIDR"),
    "跟随默认出口" to SupplementalTranslation("Follow default exit", "跟隨預設出口", "デフォルト出口に従う", "Suivre la sortie par défaut", "Standardausgang verwenden"),
    "IPv4 网段无效" to SupplementalTranslation("Invalid IPv4 CIDR"),
    "IPv6 网段无效" to SupplementalTranslation("Invalid IPv6 CIDR"),
    "域名关键词无效" to SupplementalTranslation("Invalid domain keyword"),
    "域名无效" to SupplementalTranslation("Invalid domain", "網域無效", "無効なドメイン", "Domaine invalide", "Ungültige Domain"),
    "无法保存本地路由规则" to SupplementalTranslation("Unable to save local routing rules"),
    "规则 ID 无效" to SupplementalTranslation("Invalid rule ID"),
    "规则 ID 重复" to SupplementalTranslation("Duplicate rule ID"),
    "规则值不能包含换行或逗号" to SupplementalTranslation("Rule values cannot contain newlines or commas"),
    "规则值长度无效" to SupplementalTranslation("Invalid rule value length"),
    "AdGuard DNS + 本地规则：过滤广告、跟踪器与恶意域名" to SupplementalTranslation("AdGuard DNS + local rules: blocks ads, trackers and malicious domains"),
    "AdGuard Family + 本地规则：广告、跟踪器与成人内容过滤" to SupplementalTranslation("AdGuard Family + local rules: filters ads, trackers and adult content"),
    "阿里 DNS + 腾讯 DNS 双上游，不主动过滤内容" to SupplementalTranslation("Ali DNS + Tencent DNS dual upstreams; no active content filtering"),
    "国内网络友好，使用阿里加密 DoH / DoT" to SupplementalTranslation("Mainland-friendly network; uses Ali encrypted DoH / DoT"),
    "国内网络友好，使用腾讯加密 DoH / DoT" to SupplementalTranslation("Mainland-friendly network; uses Tencent encrypted DoH / DoT"),
    "填写自己的加密 DoH 或 DoT 地址" to SupplementalTranslation("Enter your own encrypted DoH or DoT address"),
    "按一致性哈希把不同连接分配到多个可用节点" to SupplementalTranslation("Distribute connections across available nodes using consistent hashing"),
    "HTTPS 远程订阅" to SupplementalTranslation("HTTPS remote subscription", "HTTPS 遠端訂閱", "HTTPS リモート購読", "Abonnement distant HTTPS", "HTTPS-Fernabonnement"),
    "本地文件" to SupplementalTranslation("Local file", "本機檔案", "ローカルファイル", "Fichier local", "Lokale Datei"),
    "二维码" to SupplementalTranslation("QR code", "QR Code", "QR コード", "QR code", "QR-Code"),
    "简体中文界面" to SupplementalTranslation("Simplified Chinese interface", "簡體中文介面", "簡体字中国語インターフェース", "Interface en chinois simplifié", "Vereinfachtes chinesisches Interface"),
    "繁體中文介面" to SupplementalTranslation("Traditional Chinese interface", "繁體中文介面", "繁体字中国語インターフェース", "Interface en chinois traditionnel", "Traditionelles chinesisches Interface"),
    "日本語インターフェース" to SupplementalTranslation("Japanese interface", "日文介面", "日本語インターフェース", "Interface japonaise", "Japanische Oberfläche"),
    "日出·印象" to SupplementalTranslation("Impression · Sunrise", "日出·印象", "印象・日の出", "Impression · Soleil levant", "Impression · Sonnenaufgang"),
    "睡莲" to SupplementalTranslation("Water Lilies", "睡蓮", "睡蓮", "Nymphéas", "Seerosen"),
    "罂粟田" to SupplementalTranslation("Poppy Field", "罌粟田", "ポピー畑", "Champ de coquelicots", "Mohnfeld"),
    "暮色花园" to SupplementalTranslation("Twilight Garden", "暮色花園", "黄昏の庭", "Jardin crépusculaire", "Dämmergarten"),
    "深海蓝" to SupplementalTranslation("Deep Ocean", "深海藍", "ディープオーシャン", "Bleu océan profond", "Tiefsee-Blau"),
    "石墨灰" to SupplementalTranslation("Graphite", "石墨灰", "グラファイト", "Graphite", "Graphit"),
    "夜松青" to SupplementalTranslation("Night Pine", "夜松青", "ナイトパイン", "Pin nocturne", "Nachtkiefer"),
    "雾蓝、海玻璃与一笔暖橙" to SupplementalTranslation("Mist blue, sea glass and a stroke of warm orange"),
    "青绿、薰衣草与水面灰蓝" to SupplementalTranslation("Verdigris, lavender and water-surface blue-gray"),
    "鼠尾草、奶油纸与柔珊瑚" to SupplementalTranslation("Sage, cream paper and soft coral"),
    "靛紫、雾青与黄昏粉棕" to SupplementalTranslation("Indigo, mist teal and dusk rose-brown"),
    "深靛蓝、雾青与低亮银灰，适合夜间阅读" to SupplementalTranslation(
        "Deep indigo, mist teal and low-luminance silver-gray for night reading",
        "深靛藍、霧青與低亮銀灰，適合夜間閱讀",
        "深い藍色、ミストティール、低輝度のシルバーグレー。夜間の読書向け",
        "Indigo profond, bleu brume et gris argenté peu lumineux pour la lecture nocturne",
        "Tiefes Indigo, Nebelblaugrün und gedämpftes Silbergrau zum Lesen bei Nacht",
    ),
    "中性石墨与柔银，克制、清晰、低干扰" to SupplementalTranslation(
        "Neutral graphite and soft silver: restrained, clear and low-distraction",
        "中性石墨與柔銀，克制、清晰、低干擾",
        "ニュートラルなグラファイトと柔らかなシルバー。控えめで明瞭、低刺激",
        "Graphite neutre et argent doux : sobre, lisible et peu distrayant",
        "Neutrales Graphit und weiches Silber: zurückhaltend, klar und ablenkungsarm",
    ),
    "墨绿画布与冷薄荷，柔和但保持对比" to SupplementalTranslation(
        "Ink-green canvas and cool mint: soft while keeping contrast",
        "墨綠畫布與冷薄荷，柔和但保持對比",
        "墨緑のキャンバスとクールミント。柔らかく、コントラストを維持",
        "Toile vert encre et menthe froide : douce tout en gardant le contraste",
        "Dunkelgrüne Fläche und kühles Mint: weich, aber mit ausreichendem Kontrast",
    ),
    "全局直连模式" to SupplementalTranslation("Global direct mode", "全域直連模式", "グローバル直接接続モード", "Mode direct global", "Globaler Direktmodus"),
    "固定节点" to SupplementalTranslation("Fixed node", "固定節點", "固定ノード", "Nœud fixe", "Fester Knoten"),
    "匹配规则" to SupplementalTranslation("Matching rule", "匹配規則", "一致したルール", "Règle correspondante", "Übereinstimmende Regel"),
    "最终出口" to SupplementalTranslation("Final exit", "最終出口", "最終出口", "Sortie finale", "Endgültiger Ausgang"),
    "拒绝（未配置默认出口）" to SupplementalTranslation("Reject (no default exit configured)", "拒絕（未設定預設出口）", "拒否（デフォルト出口未設定）", "Refuser (aucune sortie par défaut configurée)", "Ablehnen (kein Standardausgang konfiguriert)"),
    "未命中可用出口" to SupplementalTranslation("No usable exit matched", "未命中可用出口", "利用可能な出口に一致しません", "Aucune sortie utilisable", "Kein nutzbarer Ausgang gefunden"),
    "DNS 策略" to SupplementalTranslation("DNS policy", "DNS 策略", "DNS ポリシー", "Politique DNS", "DNS-Richtlinie"),
    "UDP / WebRTC" to SupplementalTranslation("UDP / WebRTC", "UDP / WebRTC", "UDP / WebRTC", "UDP / WebRTC", "UDP / WebRTC"),
    "UDP / QUIC" to SupplementalTranslation("UDP / QUIC", "UDP / QUIC", "UDP / QUIC", "UDP / QUIC", "UDP / QUIC"),
    "QUIC" to SupplementalTranslation("QUIC", "QUIC", "QUIC", "QUIC", "QUIC"),
    "隐私提示" to SupplementalTranslation("Privacy notice", "隱私提示", "プライバシーの注意", "Avis de confidentialité", "Datenschutzhinweis"),
    "自定义 DNS 尚未填写" to SupplementalTranslation("Custom DNS is not filled in", "自訂 DNS 尚未填寫", "カスタム DNS が未入力です", "DNS personnalisé non renseigné", "Benutzerdefinierter DNS nicht ausgefüllt"),
    "自定义端点" to SupplementalTranslation("Custom endpoint", "自訂端點", "カスタムエンドポイント", "Point personnalisé", "Benutzerdefinierter Endpunkt"),
    "IPv6 已在运行规则中拒绝" to SupplementalTranslation("IPv6 is rejected by the runtime rules"),
    "双栈已开启；此解释器不执行外部 IPv6 泄漏测试" to SupplementalTranslation("Dual stack is enabled; this explainer does not run an external IPv6 leak test"),
    "这是显式直连，代理节点不会看到该请求" to SupplementalTranslation("This is explicit direct access; proxy nodes will not see this request"),
    "STUN 端口未阻断；是否暴露取决于应用和运行时" to SupplementalTranslation("The STUN port is not blocked; exposure depends on the app and runtime"),
    "UDP/443 不属于当前 STUN 阻断范围；此处不宣称 QUIC 已禁用" to SupplementalTranslation("UDP/443 is outside the current STUN block range; this does not claim that QUIC is disabled"),
    "当前查询不触发专门的 UDP 检查" to SupplementalTranslation("This query does not trigger a dedicated UDP check"),
    "VPN 权限未授予" to SupplementalTranslation("VPN permission not granted", "VPN 權限未授予", "VPN 権限が許可されていません", "Autorisation VPN non accordée", "VPN-Berechtigung nicht erteilt"),
    "没有可检测的 DNS 端点" to SupplementalTranslation("No DNS endpoints to test"),
    "端点可达" to SupplementalTranslation("Endpoint reachable", "端點可達", "エンドポイントに到達可能", "Point accessible", "Endpunkt erreichbar"),
    "端点不可达" to SupplementalTranslation("Endpoint unreachable", "端點不可達", "エンドポイントに到達できません", "Point inaccessible", "Endpunkt nicht erreichbar"),
    "服务端错误" to SupplementalTranslation("Server error", "伺服器錯誤", "サーバーエラー", "Erreur serveur", "Serverfehler"),
    "DoT 缺少主机名" to SupplementalTranslation("DoT is missing a hostname"),
    "TLS 握手成功 · 端点可达" to SupplementalTranslation("TLS handshake succeeded · endpoint reachable"),
    "不支持的 DNS 协议" to SupplementalTranslation("Unsupported DNS protocol"),
    "连接超时" to SupplementalTranslation("Connection timed out", "連線逾時", "接続がタイムアウトしました", "Délai de connexion dépassé", "Verbindungszeitüberschreitung"),
    "TLS 握手失败" to SupplementalTranslation("TLS handshake failed"),
    "主机解析失败" to SupplementalTranslation("Host resolution failed"),
    "连接被拒绝" to SupplementalTranslation("Connection refused"),
    "配置在校验后发生变化，已拒绝建立 TUN" to SupplementalTranslation("Configuration changed after validation; TUN setup was refused"),
    "系统拒绝保护 Mihomo 出站 socket" to SupplementalTranslation("The system refused to protect the Mihomo outbound socket"),
    "测速完成后无法读取节点状态" to SupplementalTranslation("Unable to read node state after the probe completed"),
    "节点检测失败，请稍后重试" to SupplementalTranslation("Node test failed; try again later"),
    "Mihomo 原生库无法加载或初始化" to SupplementalTranslation("The Mihomo native library could not load or initialize"),
    "没有可回退的运行配置" to SupplementalTranslation("No runtime configuration is available for rollback"),
    "候选配置尚未完成" to SupplementalTranslation("Candidate configuration is not complete"),
    "事务快照不完整" to SupplementalTranslation("Transaction snapshot is incomplete"),
    "无法复制运行配置" to SupplementalTranslation("Unable to copy the runtime configuration"),
    "无法创建 Mihomo provider 目录" to SupplementalTranslation("Unable to create the Mihomo provider directory"),
    "该订阅未被当前运行配置加载，请先把它设为默认出口或应用出口" to SupplementalTranslation("This subscription is not loaded by the current runtime; set it as the default or an app exit first"),
    "没有可用订阅，请先导入或选择直连" to SupplementalTranslation("No usable subscription; import one or choose direct access first"),
    "已导入的订阅没有可用配置内容，请重新导入" to SupplementalTranslation("The imported subscription has no usable configuration; import it again"),
    "默认出口不能阻止所有联网" to SupplementalTranslation("The default exit cannot block all network access"),
    "自定义 DNS 地址不能为空" to SupplementalTranslation("Custom DNS address cannot be empty"),
    "自定义 DNS 地址不能包含空格" to SupplementalTranslation("Custom DNS address cannot contain spaces"),
    "自定义 DNS 地址格式无效" to SupplementalTranslation("Invalid custom DNS address format"),
    "自定义 DNS 仅支持 HTTPS DoH 或 TLS DoT" to SupplementalTranslation("Custom DNS supports HTTPS DoH or TLS DoT only"),
    "自定义 DNS 不允许携带用户名或密码" to SupplementalTranslation("Custom DNS cannot contain a username or password"),
    "自定义 DNS 缺少主机名" to SupplementalTranslation("Custom DNS is missing a hostname"),
    "自定义 DNS 端口无效" to SupplementalTranslation("Invalid custom DNS port"),
    "自定义 DNS 不允许携带查询参数或片段" to SupplementalTranslation("Custom DNS cannot contain query parameters or fragments"),
    // Importer, LAN transfer and runtime failures are surfaced through the same snackbar/error
    // path as the Compose copy. Keep them here so switching languages cannot expose a raw
    // exception message in Simplified Chinese.
    "Clash 订阅缺少 proxies 节点列表" to SupplementalTranslation("The Clash subscription has no proxies list"),
    "Mihomo 原生库未加载" to SupplementalTranslation("The Mihomo native library was not loaded"),
    "SSR 节点参数不是有效 Base64" to SupplementalTranslation("SSR node parameters are not valid Base64"),
    "SSR 节点字段不完整" to SupplementalTranslation("SSR node fields are incomplete"),
    "SSR 节点缺少服务器地址" to SupplementalTranslation("SSR node is missing a server address"),
    "SSR 节点缺少端口" to SupplementalTranslation("SSR node is missing a port"),
    "V2Ray outbounds 结构无效" to SupplementalTranslation("The V2Ray outbounds structure is invalid"),
    "V2Ray 中没有可转换的代理出站" to SupplementalTranslation("V2Ray has no convertible proxy outbounds"),
    "VMess 节点参数不是有效 Base64 JSON" to SupplementalTranslation("VMess node parameters are not valid Base64 JSON"),
    "WireGuard URI 需要私钥与地址字段，当前请导入 Clash YAML" to SupplementalTranslation("The WireGuard URI needs private-key and address fields; import a Clash YAML instead"),
    "sing-box outbounds 结构无效" to SupplementalTranslation("The sing-box outbounds structure is invalid"),
    "sing-box 中没有可转换的代理出站" to SupplementalTranslation("sing-box has no convertible proxy outbounds"),
    "ssr 节点" to SupplementalTranslation("SSR node"),
    "vmess 节点" to SupplementalTranslation("VMess node"),
    "不支持的传输协议版本" to SupplementalTranslation("Unsupported transfer protocol version"),
    "二维码内容为空" to SupplementalTranslation("The QR code is empty"),
    "二维码内容过大" to SupplementalTranslation("The QR code payload is too large"),
    "二维码图片不能超过 20 MiB" to SupplementalTranslation("The QR image cannot exceed 20 MiB"),
    "二维码图片识别失败" to SupplementalTranslation("QR image recognition failed"),
    "二维码未包含订阅地址" to SupplementalTranslation("The QR code does not contain a subscription URL"),
    "二维码订阅地址必须使用 HTTPS" to SupplementalTranslation("The subscription URL in the QR code must use HTTPS"),
    "代理内核无法加载，请重新安装应用" to SupplementalTranslation("The proxy core could not load; reinstall the app"),
    "代理配置或内核启动失败，已保留上一份安全状态" to SupplementalTranslation("Proxy configuration or core startup failed; the last safe state was retained"),
    "传输 token 无效" to SupplementalTranslation("Invalid transfer token"),
    "传输中包含重复的同一订阅，已停止同步" to SupplementalTranslation("The transfer contains the same subscription more than once; sync stopped"),
    "传输中包含重复的订阅 ID，已停止同步" to SupplementalTranslation("The transfer contains duplicate subscription IDs; sync stopped"),
    "传输中没有订阅" to SupplementalTranslation("The transfer contains no subscriptions"),
    "传输内容包含多余数据" to SupplementalTranslation("The transfer contains trailing data"),
    "传输内容标识无效" to SupplementalTranslation("Invalid transfer content marker"),
    "传输内容超过 20 MiB 限制" to SupplementalTranslation("Transfer content exceeds the 20 MiB limit"),
    "传输内容超过限制" to SupplementalTranslation("Transfer content exceeds the limit"),
    "传输内容过大" to SupplementalTranslation("Transfer content is too large"),
    "传输包认证失败或已被篡改" to SupplementalTranslation("Transfer package authentication failed or it was tampered with"),
    "传输字段过大" to SupplementalTranslation("A transfer field is too large"),
    "传输密钥无效" to SupplementalTranslation("Invalid transfer key"),
    "传输密钥长度无效" to SupplementalTranslation("Invalid transfer key length"),
    "传输文本不是有效 UTF-8" to SupplementalTranslation("Transfer text is not valid UTF-8"),
    "传输端口无效" to SupplementalTranslation("Invalid transfer port"),
    "加密传输包无效" to SupplementalTranslation("Invalid encrypted transfer package"),
    "发送设备返回无效响应" to SupplementalTranslation("The sending device returned an invalid response"),
    "发送设备拒绝了传输或链接已失效" to SupplementalTranslation("The sending device rejected the transfer or the link expired"),
    "发送设备返回了不受信任的传输类型" to SupplementalTranslation("The sending device returned an untrusted transfer type"),
    "发送设备缺少传输长度" to SupplementalTranslation("The sending device did not provide a transfer length"),
    "发送设备返回的传输长度无效" to SupplementalTranslation("The sending device returned an invalid transfer length"),
    "图片中没有识别到二维码" to SupplementalTranslation("No QR code was found in the image"),
    "局域网地址无效" to SupplementalTranslation("Invalid LAN address"),
    "局域网链接过长" to SupplementalTranslation("The LAN link is too long"),
    "未找到可用的局域网 IPv4 地址" to SupplementalTranslation("No usable LAN IPv4 address was found"),
    "这不是有效的 Weave 局域网链接" to SupplementalTranslation("This is not a valid Weave LAN link"),
    "订阅数量无效" to SupplementalTranslation("Invalid subscription count"),
    "请选择 1–64 个订阅" to SupplementalTranslation("Select 1–64 subscriptions"),
    "候选版本未通过最小安全检查" to SupplementalTranslation("The candidate version failed the minimum safety checks"),
    "候选节点少于旧版本四分之一，旧版本已保留" to SupplementalTranslation("The candidate has fewer than a quarter of the old nodes; the old version was retained"),
    "候选节点超过旧版本四倍，旧版本已保留" to SupplementalTranslation("The candidate has more than four times the old nodes; the old version was retained"),
    "候选配置与上一份配置均无法启动" to SupplementalTranslation("Neither the candidate nor the previous configuration could start"),
    "出现未列入审计白名单的协议" to SupplementalTranslation("A protocol outside the audit allowlist was found"),
    "更新需要人工确认，旧版本仍可回退" to SupplementalTranslation("The update needs manual confirmation; the old version remains available for rollback"),
    "解析器未将该协议列入常规审计白名单" to SupplementalTranslation("The parser did not include this protocol in the routine audit allowlist"),
    "格式变化本身不等于恶意，但需要留意" to SupplementalTranslation("A format change is not malicious by itself, but it deserves attention"),
    "重复项不会被静默合并" to SupplementalTranslation("Duplicates are not merged silently"),
    "重定向或编辑后的来源与旧版本不同" to SupplementalTranslation("The redirected or edited source differs from the old version"),
    "节点数量异常增长" to SupplementalTranslation("The node count grew unexpectedly"),
    "存在同名同协议重复节点" to SupplementalTranslation("Duplicate nodes share the same name and protocol"),
    "出站保护失败，请检查网络" to SupplementalTranslation("Outbound protection failed; check the network"),
    "出站保护正在恢复，Weave 将保持断网保护" to SupplementalTranslation("Outbound protection is recovering; Weave will keep the kill switch active"),
    "出站保护暂时失败，Weave 将继续重试" to SupplementalTranslation("Outbound protection temporarily failed; Weave will keep retrying"),
    "出站保护暂时失败，请检查网络后重试" to SupplementalTranslation("Outbound protection temporarily failed; check the network and retry"),
    "正在恢复出站保护" to SupplementalTranslation("Restoring outbound protection"),
    "正在等待下一次网络重试" to SupplementalTranslation("Waiting for the next network retry"),
    "等待底层网络恢复" to SupplementalTranslation("Waiting for the underlying network to recover"),
    "底层网络已断开，Weave 将在网络恢复后自动重连" to SupplementalTranslation("The underlying network disconnected; Weave will reconnect when it recovers"),
    "底层网络暂时不可用，网络恢复后将自动重连" to SupplementalTranslation("The underlying network is temporarily unavailable; Weave will reconnect when it recovers"),
    "没有可用的 Wi‑Fi 或移动数据网络" to SupplementalTranslation("No usable Wi‑Fi or mobile-data network is available"),
    "没有可用的底层网络" to SupplementalTranslation("No usable underlying network is available"),
    "正在安全应用新规则" to SupplementalTranslation("Safely applying new rules"),
    "正在验证配置" to SupplementalTranslation("Validating configuration"),
    "已断开" to SupplementalTranslation("Disconnected"),
    "安全代理已连接" to SupplementalTranslation("Secure proxy connected"),
    "安全模式已启用，请在恢复中心解除后再连接" to SupplementalTranslation("Safe mode is enabled; clear it in Recovery before connecting"),
    "恢复中心已启用安全模式，请解除后再连接" to SupplementalTranslation("Recovery enabled safe mode; clear it before connecting"),
    "系统拒绝建立 VPN" to SupplementalTranslation("The system refused to establish the VPN"),
    "系统拒绝建立 VPN，请重新授权后再试" to SupplementalTranslation("The system refused to establish the VPN; grant permission again and retry"),
    "系统拒绝建立 VPN TUN 接口" to SupplementalTranslation("The system refused to establish the VPN TUN interface"),
    "系统或其他 VPN 已接管连接；请关闭 Pixel VPN 或其他代理后重试" to SupplementalTranslation("The system or another VPN owns the connection; disable Pixel VPN or another proxy and retry"),
    "所选订阅已不存在，请重新选择出口" to SupplementalTranslation("The selected subscription no longer exists; choose an exit again"),
    "新配置已安全生效" to SupplementalTranslation("The new configuration is active safely"),
    "新配置与原配置均无法启动，VPN 已安全关闭" to SupplementalTranslation("Neither the new nor original configuration could start; the VPN was safely stopped"),
    "正在应用新规则" to SupplementalTranslation("Applying new rules"),
    "无法创建 Mihomo 原生库临时目录" to SupplementalTranslation("Unable to create a temporary directory for the Mihomo native library"),
    "无法创建 Mihomo 数据目录" to SupplementalTranslation("Unable to create the Mihomo data directory"),
    "随包 Geo 数据校验失败" to SupplementalTranslation("Bundled Geo data verification failed"),
    "无法解析订阅服务器地址" to SupplementalTranslation("Unable to resolve the subscription server address"),
    "无法读取所选二维码图片" to SupplementalTranslation("Unable to read the selected QR image"),
    "无法读取所选订阅文件" to SupplementalTranslation("Unable to read the selected subscription file"),
    "订阅不存在" to SupplementalTranslation("Subscription does not exist"),
    "订阅中没有可用节点" to SupplementalTranslation("The subscription has no usable nodes"),
    "订阅内容不存在，请重新导入" to SupplementalTranslation("Subscription content is missing; import it again"),
    "订阅内容不是有效 UTF-8" to SupplementalTranslation("Subscription content is not valid UTF-8"),
    "订阅文本不是有效 UTF-8" to SupplementalTranslation("Subscription text is not valid UTF-8"),
    "订阅内容为空" to SupplementalTranslation("Subscription content is empty"),
    "订阅地址不能包含片段" to SupplementalTranslation("The subscription URL cannot contain a fragment"),
    "订阅地址必须使用 HTTPS" to SupplementalTranslation("The subscription URL must use HTTPS"),
    "订阅地址格式无效" to SupplementalTranslation("Invalid subscription URL format"),
    "订阅地址缺少有效主机名" to SupplementalTranslation("The subscription URL has no valid hostname"),
    "订阅主机名无效" to SupplementalTranslation("Invalid subscription hostname"),
    "订阅连接不是 HTTPS" to SupplementalTranslation("The subscription connection is not HTTPS"),
    "默认不允许访问本机或私有网络订阅" to SupplementalTranslation("Access to local or private-network subscriptions is disabled by default"),
    "订阅服务器解析到了本机或私有网络地址" to SupplementalTranslation("The subscription server resolved to a local or private-network address"),
    "请勿把用户名或密码写入订阅地址" to SupplementalTranslation("Do not put a username or password in the subscription URL"),
    "订阅重定向次数过多" to SupplementalTranslation("Too many subscription redirects"),
    "订阅重定向缺少目标地址" to SupplementalTranslation("The subscription redirect has no target URL"),
    "订阅地址返回的是网页，不是节点配置；请复制完整的 Clash 订阅链接" to SupplementalTranslation("The subscription URL returned a web page, not node configuration; copy the complete Clash subscription URL"),
    "订阅既不是配置，也不是有效的 Base64 节点列表" to SupplementalTranslation("The subscription is neither a configuration nor a valid Base64 node list"),
    "未识别到受支持的节点或配置" to SupplementalTranslation("No supported nodes or configuration were recognized"),
    "未识别到可转换的节点协议" to SupplementalTranslation("No convertible node protocol was recognized"),
    "未命名节点" to SupplementalTranslation("Unnamed node"),
    "未命名订阅" to SupplementalTranslation("Unnamed subscription"),
    "刚刚" to SupplementalTranslation("Just now"),
    "已连接 · 直连规则" to SupplementalTranslation("Connected · direct rules", "已連線 · 直連規則", "接続済み · ダイレクトルール", "Connecté · règles directes", "Verbunden · Direktregeln"),
    "订阅来源主机发生变化" to SupplementalTranslation("The subscription source host changed"),
    "订阅格式发生变化" to SupplementalTranslation("The subscription format changed"),
)

private val translationTableCache = ConcurrentHashMap<WeaveLanguage, Map<String, String>>()

private fun translationTable(language: WeaveLanguage): Map<String, String> =
    translationTableCache.getOrPut(language) {
        commonUiTranslations(language) + supplementalUiTranslations(language) + when (language) {
    WeaveLanguage.TRADITIONAL_CHINESE -> mapOf(
        "連接" to "連接",
        "连接" to "連接",
        "分流" to "分流",
        "订阅" to "訂閱",
        "设置" to "設定",
        "连接与隐私" to "連接與隱私",
        "按应用选择出口" to "按應用選擇出口",
        "本机加密管理" to "本機加密管理",
        "外观" to "外觀",
        "语言" to "語言",
        "简体中文" to "簡體中文",
        "繁體中文" to "繁體中文",
        "连接安全" to "連接安全",
        "保持私密" to "保持私密",
        "当前出口" to "目前出口",
        "运行模式" to "運行模式",
        "规则" to "規則",
        "全局" to "全域",
        "直连" to "直連",
        "最低延迟" to "最低延遲",
        "故障切换" to "故障切換",
        "负载均衡" to "負載平衡",
        "按订阅独立" to "按訂閱獨立",
        "跨订阅自动" to "跨訂閱自動",
        "IPv4 + IPv6" to "IPv4 + IPv6",
        "仅 IPv4" to "僅 IPv4",
        "统一解析" to "統一解析",
        "国内 / 海外分流" to "國內 / 海外分流",
        "极简风" to "極簡風",
        "艺术风" to "藝術風",
        "浅色模式" to "淺色模式",
        "深色模式" to "深色模式",
        "网络与安全" to "網路與安全",
        "高级路由" to "進階路由",
        "国内智能直连" to "國內智慧直連",
        "安全与隐私" to "安全與隱私",
        "隐私观测" to "隱私觀測",
        "恢复中心" to "復原中心",
        "离线策略包" to "離線策略包",
        "本地域名 / IP 规则" to "本地域名 / IP 規則",
        "阻止 UDP STUN" to "阻止 UDP STUN",
        "关于" to "關於",
        "VPN 数据路径说明" to "VPN 資料路徑說明",
        "自动节点策略" to "自動節點策略",
        "策略组范围" to "策略組範圍",
        "Always-on 与断网保护" to "Always-on 與斷網保護",
        "局域网共享" to "區域網路共享",
        "订阅地址只在本机加密保存；诊断包默认移除 URL、凭据、节点地址与访问域名。" to "訂閱地址只在本機加密保存；診斷包預設移除 URL、憑據、節點地址與存取網域。",
        "添加订阅" to "新增訂閱",
        "订阅链接或节点文本" to "訂閱連結或節點文字",
        "名称（可选）" to "名稱（選填）",
        "扫描二维码" to "掃描 QR Code",
        "识别图片" to "辨識圖片",
        "选择文件" to "選擇檔案",
        "导入" to "匯入",
        "正在校验" to "正在驗證",
        "取消" to "取消",
        "关闭" to "關閉",
        "局域网互传" to "區域網路互傳",
        "复制链接" to "複製連結",
        "立即失效" to "立即失效",
        "从链接导入" to "從連結匯入",
        "订阅详情" to "訂閱詳情",
        "保存名称" to "儲存名稱",
        "选择应用" to "選擇應用程式",
        "搜索应用或包名" to "搜尋應用程式或套件名稱",
        "删除" to "刪除",
        "删除规则" to "刪除規則",
        "更换订阅" to "更換訂閱",
        "默认出口" to "預設出口",
        "自动选择" to "自動選擇",
        "阻止联网" to "阻止連線",
        "IP 质量检测" to "IP 品質檢測",
        "完成" to "完成",
        "刷新" to "重新整理",
        "保存" to "儲存",
        "屏蔽广告" to "封鎖廣告",
        "家庭过滤" to "家庭過濾",
        "自定义" to "自訂",
        "自定义 DNS" to "自訂 DNS",
        "解析协议" to "解析協議",
        "解析策略" to "解析策略",
        "测速与可用性" to "測速與可用性",
        "检测 DNS" to "檢測 DNS",
        "检测全部 DNS" to "檢測全部 DNS",
        "地址无效：请使用 https:// 或 tls://，并填写主机名" to "地址無效：請使用 https:// 或 tls://，並填寫主機名稱",
        "返回" to "返回",
        "了解并继续" to "瞭解並繼續",
        "暂不连接" to "暫不連線",
        "网络已连接" to "網路已連線",
        "出站保护正在恢复，Weave 将保持断网保护" to "出站保護正在恢復，Weave 將保持斷網保護",
        "底层网络暂时不可用，网络恢复后将自动重连" to "底層網路暫時不可用，網路恢復後將自動重新連線",
        "出站保护暂时失败，Weave 将继续重试" to "出站保護暫時失敗，Weave 將繼續重試",
        "出站保护暂时失败，请检查网络后重试" to "出站保護暫時失敗，請檢查網路後重試",
        "网络已恢复，代理已重新连接" to "網路已恢復，代理已重新連線",
    )
    WeaveLanguage.ENGLISH -> mapOf(
        "连接" to "Connect",
        "分流" to "Routing",
        "订阅" to "Subscriptions",
        "设置" to "Settings",
        "连接与隐私" to "Connection & privacy",
        "按应用选择出口" to "Choose an exit per app",
        "本机加密管理" to "Encrypted local management",
        "外观" to "Appearance",
        "语言" to "Language",
        "简体中文" to "Simplified Chinese",
        "繁體中文" to "Traditional Chinese",
        "连接安全" to "Connection protected",
        "保持私密" to "Stay private",
        "当前出口" to "Current exit",
        "运行模式" to "Routing mode",
        "规则" to "Rule",
        "全局" to "Global",
        "直连" to "Direct",
        "最低延迟" to "Lowest latency",
        "故障切换" to "Failover",
        "负载均衡" to "Load balancing",
        "按订阅独立" to "Per subscription",
        "跨订阅自动" to "Cross-subscription",
        "仅 IPv4" to "IPv4 only",
        "统一解析" to "Single resolver",
        "国内 / 海外分流" to "Mainland / overseas split",
        "极简风" to "Minimal",
        "艺术风" to "Art",
        "浅色模式" to "Light mode",
        "深色模式" to "Dark mode",
        "网络与安全" to "Network & security",
        "高级路由" to "Advanced routing",
        "国内智能直连" to "Mainland smart direct",
        "安全与隐私" to "Security & privacy",
        "隐私观测" to "Privacy observatory",
        "恢复中心" to "Recovery center",
        "离线策略包" to "Offline policy packs",
        "本地域名 / IP 规则" to "Local domain / IP rules",
        "阻止 UDP STUN" to "Block UDP STUN",
        "关于" to "About",
        "VPN 数据路径说明" to "VPN data path",
        "自动节点策略" to "Automatic node strategy",
        "策略组范围" to "Strategy group scope",
        "Always-on 与断网保护" to "Always-on & kill switch",
        "局域网共享" to "LAN sharing",
        "添加订阅" to "Add subscription",
        "订阅链接或节点文本" to "Subscription link or node text",
        "名称（可选）" to "Name (optional)",
        "扫描二维码" to "Scan QR code",
        "识别图片" to "Decode image",
        "选择文件" to "Choose file",
        "导入" to "Import",
        "正在校验" to "Validating",
        "取消" to "Cancel",
        "关闭" to "Close",
        "局域网互传" to "LAN transfer",
        "复制链接" to "Copy link",
        "立即失效" to "Expire now",
        "从链接导入" to "Import from link",
        "订阅详情" to "Subscription details",
        "保存名称" to "Save name",
        "选择应用" to "Choose app",
        "搜索应用或包名" to "Search app or package",
        "删除" to "Delete",
        "删除规则" to "Delete rule",
        "更换订阅" to "Change subscription",
        "默认出口" to "Default exit",
        "自动选择" to "Automatic",
        "阻止联网" to "Block network",
        "IP 质量检测" to "IP quality",
        "完成" to "Done",
        "刷新" to "Refresh",
        "保存" to "Save",
        "屏蔽广告" to "Block ads",
        "家庭过滤" to "Family filter",
        "自定义" to "Custom",
        "自定义 DNS" to "Custom DNS",
        "解析协议" to "Resolution protocol",
        "解析策略" to "Resolution strategy",
        "测速与可用性" to "Probe & availability",
        "检测 DNS" to "Test DNS",
        "检测全部 DNS" to "Test all DNS",
        "地址无效：请使用 https:// 或 tls://，并填写主机名" to "Invalid address: use https:// or tls:// with a hostname",
        "返回" to "Back",
        "了解并继续" to "Understand and continue",
        "暂不连接" to "Not now",
        "网络已连接" to "Network connected",
        "出站保护正在恢复，Weave 将保持断网保护" to "Outbound protection is recovering; Weave will keep the kill switch active",
        "底层网络暂时不可用，网络恢复后将自动重连" to "The underlying network is temporarily unavailable; Weave will reconnect when it returns",
        "出站保护暂时失败，Weave 将继续重试" to "Outbound protection failed temporarily; Weave will keep retrying",
        "出站保护暂时失败，请检查网络后重试" to "Outbound protection failed; check the network and try again",
        "网络已恢复，代理已重新连接" to "Network restored; proxy reconnected",
    )
    WeaveLanguage.JAPANESE -> mapOf(
        "连接" to "接続",
        "分流" to "ルーティング",
        "订阅" to "サブスクリプション",
        "设置" to "設定",
        "连接与隐私" to "接続とプライバシー",
        "按应用选择出口" to "アプリごとの出口",
        "本机加密管理" to "ローカル暗号化管理",
        "外观" to "外観",
        "语言" to "言語",
        "简体中文" to "簡体字中国語",
        "繁體中文" to "繁体字中国語",
        "连接安全" to "接続は保護されています",
        "保持私密" to "プライバシーを維持",
        "当前出口" to "現在の出口",
        "运行模式" to "ルーティングモード",
        "规则" to "ルール",
        "全局" to "グローバル",
        "直连" to "ダイレクト",
        "最低延迟" to "最低遅延",
        "故障切换" to "フェイルオーバー",
        "负载均衡" to "負荷分散",
        "按订阅独立" to "サブスクリプション単位",
        "跨订阅自动" to "サブスクリプション横断",
        "仅 IPv4" to "IPv4 のみ",
        "统一解析" to "単一 DNS",
        "国内 / 海外分流" to "国内 / 海外分離",
        "极简风" to "ミニマル",
        "艺术风" to "アート",
        "浅色模式" to "ライトモード",
        "深色模式" to "ダークモード",
        "网络与安全" to "ネットワークとセキュリティ",
        "高级路由" to "高度なルーティング",
        "国内智能直连" to "中国向けスマートダイレクト",
        "安全与隐私" to "セキュリティとプライバシー",
        "隐私观测" to "プライバシー観測",
        "恢复中心" to "リカバリーセンター",
        "离线策略包" to "オフラインポリシー",
        "本地域名 / IP 规则" to "ローカルドメイン / IP ルール",
        "阻止 UDP STUN" to "UDP STUN をブロック",
        "关于" to "このアプリについて",
        "VPN 数据路径说明" to "VPN データパス",
        "自动节点策略" to "自動ノード戦略",
        "策略组范围" to "戦略グループの範囲",
        "Always-on 与断网保护" to "Always-on とキルスイッチ",
        "局域网共享" to "LAN 共有",
        "添加订阅" to "サブスクリプションを追加",
        "订阅链接或节点文本" to "サブスクリプション URL またはノードテキスト",
        "名称（可选）" to "名前（任意）",
        "扫描二维码" to "QR コードをスキャン",
        "识别图片" to "画像を読み取る",
        "选择文件" to "ファイルを選択",
        "导入" to "インポート",
        "正在校验" to "検証中",
        "取消" to "キャンセル",
        "关闭" to "閉じる",
        "局域网互传" to "LAN 転送",
        "复制链接" to "リンクをコピー",
        "立即失效" to "今すぐ無効化",
        "从链接导入" to "リンクからインポート",
        "订阅详情" to "サブスクリプション詳細",
        "保存名称" to "名前を保存",
        "选择应用" to "アプリを選択",
        "搜索应用或包名" to "アプリまたはパッケージを検索",
        "删除" to "削除",
        "删除规则" to "ルールを削除",
        "更换订阅" to "サブスクリプションを変更",
        "默认出口" to "デフォルト出口",
        "自动选择" to "自動選択",
        "阻止联网" to "ネットワークをブロック",
        "IP 质量检测" to "IP 品質チェック",
        "完成" to "完了",
        "刷新" to "更新",
        "保存" to "保存",
        "屏蔽广告" to "広告ブロック",
        "家庭过滤" to "ファミリーフィルター",
        "自定义" to "カスタム",
        "自定义 DNS" to "カスタム DNS",
        "解析协议" to "解決プロトコル",
        "解析策略" to "解決ポリシー",
        "测速与可用性" to "測定と可用性",
        "检测 DNS" to "DNS をテスト",
        "检测全部 DNS" to "すべての DNS をテスト",
        "地址无效：请使用 https:// 或 tls://，并填写主机名" to "無効なアドレス：ホスト名を含む https:// または tls:// を使用してください",
        "返回" to "戻る",
        "了解并继续" to "理解して続行",
        "暂不连接" to "今は接続しない",
        "网络已连接" to "ネットワークに接続しました",
        "出站保护正在恢复，Weave 将保持断网保护" to "送信保護を復元中。Weave はキルスイッチを維持します",
        "底层网络暂时不可用，网络恢复后将自动重连" to "基盤ネットワークが一時的に利用できません。復旧後に自動再接続します",
        "出站保护暂时失败，Weave 将继续重试" to "送信保護に一時的な失敗。再試行を続けます",
        "出站保护暂时失败，请检查网络后重试" to "送信保護に失敗しました。ネットワークを確認して再試行してください",
        "网络已恢复，代理已重新连接" to "ネットワークが復旧し、プロキシを再接続しました",
    )
    WeaveLanguage.FRENCH -> mapOf(
        "连接" to "Connexion",
        "分流" to "Routage",
        "订阅" to "Abonnements",
        "设置" to "Réglages",
        "连接与隐私" to "Connexion et confidentialité",
        "按应用选择出口" to "Choisir une sortie par application",
        "本机加密管理" to "Gestion chiffrée locale",
        "外观" to "Apparence",
        "语言" to "Langue",
        "简体中文" to "Chinois simplifié",
        "繁體中文" to "Chinois traditionnel",
        "连接安全" to "Connexion protégée",
        "保持私密" to "Rester privé",
        "当前出口" to "Sortie actuelle",
        "运行模式" to "Mode de routage",
        "规则" to "Règle",
        "全局" to "Global",
        "直连" to "Direct",
        "最低延迟" to "Latence minimale",
        "故障切换" to "Basculement",
        "负载均衡" to "Équilibrage de charge",
        "按订阅独立" to "Par abonnement",
        "跨订阅自动" to "Inter-abonnements",
        "仅 IPv4" to "IPv4 uniquement",
        "统一解析" to "DNS unique",
        "国内 / 海外分流" to "Séparation local / international",
        "极简风" to "Minimal",
        "艺术风" to "Artistique",
        "浅色模式" to "Mode clair",
        "深色模式" to "Mode sombre",
        "网络与安全" to "Réseau et sécurité",
        "高级路由" to "Routage avancé",
        "国内智能直连" to "Direct intelligent local",
        "安全与隐私" to "Sécurité et confidentialité",
        "隐私观测" to "Observatoire de confidentialité",
        "恢复中心" to "Centre de récupération",
        "离线策略包" to "Politiques hors ligne",
        "本地域名 / IP 规则" to "Règles de domaine / IP locales",
        "阻止 UDP STUN" to "Bloquer UDP STUN",
        "关于" to "À propos",
        "VPN 数据路径说明" to "Chemin des données VPN",
        "自动节点策略" to "Stratégie de nœuds automatique",
        "策略组范围" to "Portée du groupe de stratégie",
        "Always-on 与断网保护" to "Always-on et coupe-circuit",
        "局域网共享" to "Partage LAN",
        "添加订阅" to "Ajouter un abonnement",
        "订阅链接或节点文本" to "Lien d’abonnement ou texte du nœud",
        "名称（可选）" to "Nom (facultatif)",
        "扫描二维码" to "Scanner le QR code",
        "识别图片" to "Lire une image",
        "选择文件" to "Choisir un fichier",
        "导入" to "Importer",
        "正在校验" to "Validation",
        "取消" to "Annuler",
        "关闭" to "Fermer",
        "局域网互传" to "Transfert LAN",
        "复制链接" to "Copier le lien",
        "立即失效" to "Expirer maintenant",
        "从链接导入" to "Importer depuis un lien",
        "订阅详情" to "Détails de l’abonnement",
        "保存名称" to "Enregistrer le nom",
        "选择应用" to "Choisir une application",
        "搜索应用或包名" to "Rechercher une application ou un paquet",
        "删除" to "Supprimer",
        "删除规则" to "Supprimer la règle",
        "更换订阅" to "Changer d’abonnement",
        "默认出口" to "Sortie par défaut",
        "自动选择" to "Automatique",
        "阻止联网" to "Bloquer le réseau",
        "IP 质量检测" to "Qualité IP",
        "完成" to "Terminé",
        "刷新" to "Actualiser",
        "保存" to "Enregistrer",
        "屏蔽广告" to "Bloquer les publicités",
        "家庭过滤" to "Filtre familial",
        "自定义" to "Personnalisé",
        "自定义 DNS" to "DNS personnalisé",
        "解析协议" to "Protocole de résolution",
        "解析策略" to "Stratégie de résolution",
        "测速与可用性" to "Test et disponibilité",
        "检测 DNS" to "Tester le DNS",
        "检测全部 DNS" to "Tester tous les DNS",
        "地址无效：请使用 https:// 或 tls://，并填写主机名" to "Adresse invalide : utilisez https:// ou tls:// avec un nom d’hôte",
        "返回" to "Retour",
        "了解并继续" to "Comprendre et continuer",
        "暂不连接" to "Pas maintenant",
        "网络已连接" to "Réseau connecté",
        "出站保护正在恢复，Weave 将保持断网保护" to "La protection sortante se rétablit ; le coupe-circuit reste actif",
        "底层网络暂时不可用，网络恢复后将自动重连" to "Le réseau sous-jacent est temporairement indisponible ; reconnexion automatique à son retour",
        "出站保护暂时失败，Weave 将继续重试" to "Échec temporaire de la protection sortante ; nouvelles tentatives en cours",
        "出站保护暂时失败，请检查网络后重试" to "Échec de la protection sortante ; vérifiez le réseau puis réessayez",
        "网络已恢复，代理已重新连接" to "Réseau rétabli ; proxy reconnecté",
    )
    WeaveLanguage.GERMAN -> mapOf(
        "连接" to "Verbindung",
        "分流" to "Routing",
        "订阅" to "Abonnements",
        "设置" to "Einstellungen",
        "连接与隐私" to "Verbindung & Datenschutz",
        "按应用选择出口" to "Ausgang pro App wählen",
        "本机加密管理" to "Lokale verschlüsselte Verwaltung",
        "外观" to "Darstellung",
        "语言" to "Sprache",
        "简体中文" to "Vereinfachtes Chinesisch",
        "繁體中文" to "Traditionelles Chinesisch",
        "连接安全" to "Verbindung geschützt",
        "保持私密" to "Privat bleiben",
        "当前出口" to "Aktueller Ausgang",
        "运行模式" to "Routing-Modus",
        "规则" to "Regel",
        "全局" to "Global",
        "直连" to "Direkt",
        "最低延迟" to "Niedrigste Latenz",
        "故障切换" to "Failover",
        "负载均衡" to "Lastverteilung",
        "按订阅独立" to "Pro Abonnement",
        "跨订阅自动" to "Abonnementübergreifend",
        "仅 IPv4" to "Nur IPv4",
        "统一解析" to "Einzelner DNS",
        "国内 / 海外分流" to "Inland / Ausland getrennt",
        "极简风" to "Minimal",
        "艺术风" to "Kunst",
        "浅色模式" to "Heller Modus",
        "深色模式" to "Dunkler Modus",
        "网络与安全" to "Netzwerk & Sicherheit",
        "高级路由" to "Erweitertes Routing",
        "国内智能直连" to "Intelligente Direktverbindung",
        "安全与隐私" to "Sicherheit & Datenschutz",
        "隐私观测" to "Datenschutz-Überwachung",
        "恢复中心" to "Wiederherstellungszentrum",
        "离线策略包" to "Offline-Richtlinien",
        "本地域名 / IP 规则" to "Lokale Domain-/IP-Regeln",
        "阻止 UDP STUN" to "UDP STUN blockieren",
        "关于" to "Über",
        "VPN 数据路径说明" to "VPN-Datenpfad",
        "自动节点策略" to "Automatische Knotenstrategie",
        "策略组范围" to "Bereich der Strategiegruppe",
        "Always-on 与断网保护" to "Always-on & Kill-Switch",
        "局域网共享" to "LAN-Freigabe",
        "添加订阅" to "Abonnement hinzufügen",
        "订阅链接或节点文本" to "Abonnement-Link oder Knotentext",
        "名称（可选）" to "Name (optional)",
        "扫描二维码" to "QR-Code scannen",
        "识别图片" to "Bild auslesen",
        "选择文件" to "Datei auswählen",
        "导入" to "Importieren",
        "正在校验" to "Wird geprüft",
        "取消" to "Abbrechen",
        "关闭" to "Schließen",
        "局域网互传" to "LAN-Übertragung",
        "复制链接" to "Link kopieren",
        "立即失效" to "Jetzt ablaufen lassen",
        "从链接导入" to "Aus Link importieren",
        "订阅详情" to "Abonnementdetails",
        "保存名称" to "Namen speichern",
        "选择应用" to "App auswählen",
        "搜索应用或包名" to "App oder Paket suchen",
        "删除" to "Löschen",
        "删除规则" to "Regel löschen",
        "更换订阅" to "Abonnement wechseln",
        "默认出口" to "Standardausgang",
        "自动选择" to "Automatisch",
        "阻止联网" to "Netzwerk blockieren",
        "IP 质量检测" to "IP-Qualität",
        "完成" to "Fertig",
        "刷新" to "Aktualisieren",
        "保存" to "Speichern",
        "屏蔽广告" to "Werbung blockieren",
        "家庭过滤" to "Familienfilter",
        "自定义" to "Benutzerdefiniert",
        "自定义 DNS" to "Benutzerdefinierter DNS",
        "解析协议" to "Auflösungsprotokoll",
        "解析策略" to "Auflösungsstrategie",
        "测速与可用性" to "Messung & Verfügbarkeit",
        "检测 DNS" to "DNS testen",
        "检测全部 DNS" to "Alle DNS testen",
        "地址无效：请使用 https:// 或 tls://，并填写主机名" to "Ungültige Adresse: https:// oder tls:// mit Hostnamen verwenden",
        "返回" to "Zurück",
        "了解并继续" to "Verstanden, weiter",
        "暂不连接" to "Nicht jetzt",
        "网络已连接" to "Netzwerk verbunden",
        "出站保护正在恢复，Weave 将保持断网保护" to "Ausgangsschutz wird wiederhergestellt; der Kill-Switch bleibt aktiv",
        "底层网络暂时不可用，网络恢复后将自动重连" to "Das zugrunde liegende Netzwerk ist vorübergehend nicht verfügbar; automatische Verbindung bei Rückkehr",
        "出站保护暂时失败，Weave 将继续重试" to "Ausgangsschutz vorübergehend fehlgeschlagen; Weave versucht es erneut",
        "出站保护暂时失败，请检查网络后重试" to "Ausgangsschutz fehlgeschlagen; Netzwerk prüfen und erneut versuchen",
        "网络已恢复，代理已重新连接" to "Netzwerk wiederhergestellt; Proxy erneut verbunden",
    )
    WeaveLanguage.SIMPLIFIED_CHINESE -> emptyMap()
        }
    }

/**
 * Strings shared by screens that are fed from runtime state rather than a single screen's
 * literal. Keeping these in one table also covers enum descriptions and status messages while
 * the UI is gradually moved to Android resources.
 */
private fun commonUiTranslations(language: WeaveLanguage): Map<String, String> = when (language) {
    WeaveLanguage.SIMPLIFIED_CHINESE -> emptyMap()
    WeaveLanguage.TRADITIONAL_CHINESE -> mapOf(
        "私密网络" to "私密網路",
        "更多" to "更多",
        "实时流量" to "即時流量",
        "网络延迟" to "網路延遲",
        "可用" to "可用",
        "等待测速" to "等待測速",
        "已保护" to "已保護",
        "正在连接" to "正在連接",
        "需要处理" to "需要處理",
        "未连接" to "未連接",
        "内核已安装" to "核心已安裝",
        "内核不可用" to "核心不可用",
        "保持私密" to "保持私密",
        "本机规则已就绪，连接时按需加载原生内核" to "本機規則已就緒，連線時按需載入原生核心",
        "原生内核加载失败，已禁止建立 VPN" to "原生核心載入失敗，已禁止建立 VPN",
        "断开" to "中斷連線",
        "点击选择默认节点" to "點選選擇預設節點",
        "应用识别正常" to "應用程式識別正常",
        "等待应用流量" to "等待應用程式流量",
        "选择节点" to "選擇節點",
        "按应用选择出口" to "按應用程式選擇出口",
        "应用分流" to "應用程式分流",
        "路由解释" to "路由解釋",
        "添加规则" to "新增規則",
        "代理" to "代理",
        "默认" to "預設",
        "订阅已载入" to "訂閱已載入",
        "本机加密" to "本機加密",
        "正在刷新" to "正在重新整理",
        "订阅地址只在本机加密保存；诊断包默认移除 URL、凭据、节点地址与访问域名。" to "訂閱地址只在本機加密保存；診斷包預設移除 URL、憑據、節點地址與存取網域。",
        "不使用任何代理节点" to "不使用任何代理節點",
        "未命中应用规则时不使用代理" to "未命中應用程式規則時不使用代理",
        "未指定应用的 CN 流量直连" to "未指定應用程式的 CN 流量直連",
        "通过 HTTPS 加密解析，兼容性更好" to "透過 HTTPS 加密解析，相容性更好",
        "通过 TLS 加密解析，协议边界更清晰" to "透過 TLS 加密解析，協議邊界更清晰",
        "国内隐私" to "國內隱私",
        "阿里 DNS" to "阿里 DNS",
        "腾讯 DNS" to "騰訊 DNS",
        "海外隐私导向解析；中国大陆网络可能较慢" to "海外隱私導向解析；中國大陸網路可能較慢",
        "全球通用解析；中国大陆网络可能不可达" to "全球通用解析；中國大陸網路可能無法連線",
        "带恶意域名拦截的安全解析，不记录完整查询日志" to "具惡意網域攔截的安全解析，不記錄完整查詢記錄",
        "隐私导向解析；不主动过滤广告内容" to "隱私導向解析；不主動過濾廣告內容",
        "通过一致性哈希把不同连接分配到多个可用节点" to "透過一致性雜湊將不同連線分配到多個可用節點",
        "定时探测订阅内节点，自动选择延迟最低的可用节点" to "定時探測訂閱內節點，自動選擇延遲最低的可用節點",
        "优先使用订阅中的首个可用节点，故障时自动切换" to "優先使用訂閱中的第一個可用節點，故障時自動切換",
        "每个订阅维护自己的自动节点组，资源占用更低" to "每個訂閱維護自己的自動節點組，資源佔用更低",
        "把当前加载的多个订阅放入同一个测速与故障切换组" to "將目前載入的多個訂閱放入同一個測速與故障切換群組",
        "完整接管 IPv4 与 IPv6 流量" to "完整接管 IPv4 與 IPv6 流量",
        "停用 IPv6 解析并在隧道内拒绝 IPv6，防止旁路" to "停用 IPv6 解析並在通道內拒絕 IPv6，防止旁路",
        "所有域名使用当前选择的加密 DNS" to "所有網域使用目前選擇的加密 DNS",
        "国内域名优先国内上游，海外域名使用隐私上游" to "國內網域優先國內上游，海外網域使用隱私上游",
        "清晰留白与冷暖中性灰，适合日常使用" to "清晰留白與冷暖中性灰，適合日常使用",
        "低亮度深色画布，适合夜间与极客习惯" to "低亮度深色畫布，適合夜間與極客習慣",
        "公网出口、地区、ASN、代理标签与真实 HTTPS 延迟" to "公網出口、地區、ASN、代理標籤與真實 HTTPS 延遲",
        "本地证据检查 · 不生成虚假安全百分比" to "本機證據檢查 · 不產生虛假安全百分比",
        "应用规则优先 · 修改后安全热重载" to "應用程式規則優先 · 修改後安全熱重載",
        "默认开启 · 未指定应用的 CN 流量直连 · 应用分流优先" to "預設開啟 · 未指定應用程式的 CN 流量直連 · 應用程式分流優先",
        "Keystore 加密 · 明文按会话清理" to "Keystore 加密 · 明文按工作階段清理",
        "本机导入、哈希校验、可回滚启停" to "本機匯入、雜湊校驗、可回滾啟停",
        "本机加密保存 · 应用规则优先 · 连接前可解释" to "本機加密保存 · 應用程式規則優先 · 連線前可解釋",
        "降低 WebRTC 暴露风险；可能影响音视频通话" to "降低 WebRTC 暴露風險；可能影響音視訊通話",
        "服务边界" to "服務邊界",
        "Weave 只提供本地客户端，不提供节点、线路、账号、托管 VPN 或集中式控制服务。" to "Weave 只提供本機客戶端，不提供節點、線路、帳號、託管 VPN 或集中式控制服務。",
        "本地开源发行配置：Weave 不运营账户、云端控制、节点中继、遥测或远程更新；用户主动选择的订阅、代理、DNS 和 IP 检测端点仍可能收到完成请求所需的数据。" to "本機開源發行設定：Weave 不營運帳戶、雲端控制、節點中繼、遙測或遠端更新；使用者主動選擇的訂閱、代理、DNS 與 IP 檢測端點仍可能收到完成請求所需的資料。",
    )
    WeaveLanguage.ENGLISH -> mapOf(
        "私密网络" to "Private network",
        "更多" to "More",
        "实时流量" to "Live traffic",
        "网络延迟" to "Network latency",
        "可用" to "Available",
        "等待测速" to "Waiting for probe",
        "已保护" to "Protected",
        "正在连接" to "Connecting",
        "需要处理" to "Needs attention",
        "未连接" to "Disconnected",
        "内核已安装" to "Core installed",
        "内核不可用" to "Core unavailable",
        "保持私密" to "Stay private",
        "本机规则已就绪，连接时按需加载原生内核" to "Local rules are ready; the native core loads on demand when connecting",
        "原生内核加载失败，已禁止建立 VPN" to "The native core failed to load; VPN creation is disabled",
        "断开" to "Disconnect",
        "点击选择默认节点" to "Tap to choose a default node",
        "应用识别正常" to "App attribution is working",
        "等待应用流量" to "Waiting for app traffic",
        "选择节点" to "Choose node",
        "按应用选择出口" to "Choose an exit per app",
        "应用分流" to "App routing",
        "路由解释" to "Route explanation",
        "添加规则" to "Add rule",
        "代理" to "Proxy",
        "默认" to "Default",
        "订阅已载入" to "Subscriptions loaded",
        "本机加密" to "Encrypted locally",
        "正在刷新" to "Refreshing",
        "订阅地址只在本机加密保存；诊断包默认移除 URL、凭据、节点地址与访问域名。" to "Subscription URLs are encrypted locally; diagnostic bundles remove URLs, credentials, node addresses and visited domains by default.",
        "不使用任何代理节点" to "Do not use a proxy node",
        "未命中应用规则时不使用代理" to "Do not use a proxy when no app rule matches",
        "未指定应用的 CN 流量直连" to "Direct mainland traffic for apps without a rule",
        "通过 HTTPS 加密解析，兼容性更好" to "Encrypted resolution over HTTPS with broad compatibility",
        "通过 TLS 加密解析，协议边界更清晰" to "Encrypted resolution over TLS with a clear protocol boundary",
        "国内隐私" to "Mainland privacy",
        "阿里 DNS" to "Ali DNS",
        "腾讯 DNS" to "Tencent DNS",
        "海外隐私导向解析；中国大陆网络可能较慢" to "Privacy-oriented overseas resolver; may be slow from mainland China",
        "全球通用解析；中国大陆网络可能不可达" to "Global resolver; may be unreachable from mainland China",
        "带恶意域名拦截的安全解析，不记录完整查询日志" to "Secure resolver with malicious-domain blocking; does not keep complete query logs",
        "隐私导向解析；不主动过滤广告内容" to "Privacy-oriented resolver; does not actively filter ads",
        "通过一致性哈希把不同连接分配到多个可用节点" to "Distribute connections across available nodes with consistent hashing",
        "定时探测订阅内节点，自动选择延迟最低的可用节点" to "Probe subscription nodes periodically and choose the lowest-latency available node",
        "优先使用订阅中的首个可用节点，故障时自动切换" to "Prefer the first available subscription node and fail over on errors",
        "每个订阅维护自己的自动节点组，资源占用更低" to "Maintain an automatic node group per subscription with lower resource use",
        "把当前加载的多个订阅放入同一个测速与故障切换组" to "Place loaded subscriptions in one probe and failover group",
        "完整接管 IPv4 与 IPv6 流量" to "Take over IPv4 and IPv6 traffic",
        "停用 IPv6 解析并在隧道内拒绝 IPv6，防止旁路" to "Disable IPv6 resolution and reject IPv6 in the tunnel to prevent bypasses",
        "所有域名使用当前选择的加密 DNS" to "Use the selected encrypted DNS for every domain",
        "国内域名优先国内上游，海外域名使用隐私上游" to "Use a mainland upstream for mainland domains and a privacy upstream overseas",
        "清晰留白与冷暖中性灰，适合日常使用" to "Crisp whitespace and neutral gray tones for daily use",
        "低亮度深色画布，适合夜间与极客习惯" to "A low-luminance dark canvas for night use and power users",
        "公网出口、地区、ASN、代理标签与真实 HTTPS 延迟" to "Public egress, region, ASN, proxy signals and real HTTPS latency",
        "本地证据检查 · 不生成虚假安全百分比" to "Local evidence checks · no fabricated security percentage",
        "应用规则优先 · 修改后安全热重载" to "App rules first · safe hot reload after changes",
        "默认开启 · 未指定应用的 CN 流量直连 · 应用分流优先" to "On by default · direct mainland traffic without an app rule · app routing wins",
        "Keystore 加密 · 明文按会话清理" to "Keystore encryption · plaintext cleared per session",
        "本机导入、哈希校验、可回滚启停" to "Local import, hash verification and rollback",
        "本机加密保存 · 应用规则优先 · 连接前可解释" to "Encrypted locally · app rules first · explainable before connect",
        "降低 WebRTC 暴露风险；可能影响音视频通话" to "Reduce WebRTC exposure; may affect audio/video calls",
        "服务边界" to "Service boundary",
        "Weave 只提供本地客户端，不提供节点、线路、账号、托管 VPN 或集中式控制服务。" to "Weave is a local client; it does not provide nodes, lines, accounts, hosted VPN access or a centralized control service.",
        "本地开源发行配置：Weave 不运营账户、云端控制、节点中继、遥测或远程更新；用户主动选择的订阅、代理、DNS 和 IP 检测端点仍可能收到完成请求所需的数据。" to "Local open-source profile: Weave has no account service, cloud control, relay, telemetry or remote updater; endpoints you choose may still receive data needed to complete your request.",
    )
    WeaveLanguage.JAPANESE -> mapOf(
        "私密网络" to "プライベートネットワーク",
        "更多" to "その他",
        "实时流量" to "リアルタイム通信量",
        "网络延迟" to "ネットワーク遅延",
        "可用" to "利用可能",
        "等待测速" to "測定待ち",
        "已保护" to "保護中",
        "正在连接" to "接続中",
        "需要处理" to "要対応",
        "未连接" to "未接続",
        "内核已安装" to "コアインストール済み",
        "内核不可用" to "コアを利用できません",
        "保持私密" to "プライバシーを維持",
        "本机规则已就绪，连接时按需加载原生内核" to "ローカルルールを準備済み。接続時にネイティブコアを必要に応じて読み込みます",
        "原生内核加载失败，已禁止建立 VPN" to "ネイティブコアの読み込みに失敗したため、VPN の作成を無効にしました",
        "断开" to "切断",
        "点击选择默认节点" to "タップしてデフォルトノードを選択",
        "应用识别正常" to "アプリ識別は正常です",
        "等待应用流量" to "アプリ通信を待機中",
        "选择节点" to "ノードを選択",
        "按应用选择出口" to "アプリごとの出口",
        "应用分流" to "アプリルーティング",
        "路由解释" to "ルートの説明",
        "添加规则" to "ルールを追加",
        "代理" to "プロキシ",
        "默认" to "デフォルト",
        "订阅已载入" to "サブスクリプションを読み込みました",
        "本机加密" to "ローカル暗号化",
        "正在刷新" to "更新中",
        "订阅地址只在本机加密保存；诊断包默认移除 URL、凭据、节点地址与访问域名。" to "サブスクリプション URL は端末内で暗号化保存され、診断パッケージから URL、資格情報、ノードアドレス、アクセスドメインを既定で除外します。",
        "不使用任何代理节点" to "プロキシノードを使用しない",
        "未命中应用规则时不使用代理" to "アプリルールに一致しない場合はプロキシを使用しない",
        "未指定应用的 CN 流量直连" to "ルール未指定アプリの中国向け通信を直接接続",
        "通过 HTTPS 加密解析，兼容性更好" to "HTTPS による暗号化 DNS。互換性に優れます",
        "通过 TLS 加密解析，协议边界更清晰" to "TLS による暗号化 DNS。プロトコル境界が明確です",
        "国内隐私" to "中国向けプライバシー",
        "阿里 DNS" to "Ali DNS",
        "腾讯 DNS" to "Tencent DNS",
        "海外隐私导向解析；中国大陆网络可能较慢" to "海外向けプライバシー DNS。中国本土からは遅い場合があります",
        "全球通用解析；中国大陆网络可能不可达" to "グローバル DNS。中国本土から到達できない場合があります",
        "带恶意域名拦截的安全解析，不记录完整查询日志" to "悪意あるドメインをブロックする安全 DNS。完全な検索ログを保存しません",
        "隐私导向解析；不主动过滤广告内容" to "プライバシー重視の DNS。広告は積極的にブロックしません",
        "定时探测订阅内节点，自动选择延迟最低的可用节点" to "サブスクリプションのノードを定期測定し、遅延の低いノードを自動選択",
        "优先使用订阅中的首个可用节点，故障时自动切换" to "最初の利用可能なノードを優先し、障害時に自動切替",
        "每个订阅维护自己的自动节点组，资源占用更低" to "サブスクリプションごとに自動ノードグループを管理し、リソース使用量を削減",
        "把当前加载的多个订阅放入同一个测速与故障切换组" to "読み込み済みサブスクリプションを一つの測定・切替グループに統合",
        "完整接管 IPv4 与 IPv6 流量" to "IPv4 と IPv6 の通信を完全に引き継ぐ",
        "停用 IPv6 解析并在隧道内拒绝 IPv6，防止旁路" to "IPv6 DNS を無効にし、トンネル内で IPv6 を拒否して迂回を防止",
        "所有域名使用当前选择的加密 DNS" to "すべてのドメインで選択した暗号化 DNS を使用",
        "国内域名优先国内上游，海外域名使用隐私上游" to "国内ドメインは国内 DNS、海外ドメインはプライバシー DNS を優先",
        "清晰留白与冷暖中性灰，适合日常使用" to "余白とニュートラルグレーを活かした日常向けデザイン",
        "低亮度深色画布，适合夜间与极客习惯" to "夜間とパワーユーザー向けの低輝度ダークキャンバス",
        "公网出口、地区、ASN、代理标签与真实 HTTPS 延迟" to "公開出口、地域、ASN、プロキシ情報、実測 HTTPS 遅延",
        "本地证据检查 · 不生成虚假安全百分比" to "端末内の証拠チェック · 偽の安全率は表示しません",
        "应用规则优先 · 修改后安全热重载" to "アプリルールを優先 · 変更後は安全にホットリロード",
        "默认开启 · 未指定应用的 CN 流量直连 · 应用分流优先" to "既定で有効 · ルール未指定アプリの中国向け通信は直結 · アプリルーティングを優先",
        "Keystore 加密 · 明文按会话清理" to "Keystore 暗号化 · 平文はセッションごとに消去",
        "本机导入、哈希校验、可回滚启停" to "端末内インポート、ハッシュ検証、ロールバック対応",
        "本机加密保存 · 应用规则优先 · 连接前可解释" to "端末内で暗号化保存 · アプリルール優先 · 接続前に説明可能",
        "降低 WebRTC 暴露风险；可能影响音视频通话" to "WebRTC の露出リスクを低減。音声・ビデオ通話に影響する場合があります",
        "服务边界" to "サービスの範囲",
        "Weave 只提供本地客户端，不提供节点、线路、账号、托管 VPN 或集中式控制服务。" to "Weave は端末内クライアントのみを提供し、ノード、回線、アカウント、ホステッド VPN、集中管理サービスは提供しません。",
        "本地开源发行配置：Weave 不运营账户、云端控制、节点中继、遥测或远程更新；用户主动选择的订阅、代理、DNS 和 IP 检测端点仍可能收到完成请求所需的数据。" to "ローカルオープンソース設定：Weave はアカウント、クラウド制御、リレー、テレメトリ、リモート更新を運用しません。選択した端点にはリクエスト完了に必要なデータが届く場合があります。",
    )
    WeaveLanguage.FRENCH -> mapOf(
        "私密网络" to "Réseau privé",
        "更多" to "Plus",
        "实时流量" to "Trafic en temps réel",
        "网络延迟" to "Latence réseau",
        "可用" to "Disponible",
        "等待测速" to "En attente du test",
        "已保护" to "Protégé",
        "正在连接" to "Connexion…",
        "需要处理" to "Action requise",
        "未连接" to "Déconnecté",
        "内核已安装" to "Noyau installé",
        "内核不可用" to "Noyau indisponible",
        "保持私密" to "Rester privé",
        "本机规则已就绪，连接时按需加载原生内核" to "Les règles locales sont prêtes ; le noyau natif sera chargé à la connexion",
        "原生内核加载失败，已禁止建立 VPN" to "Échec du chargement du noyau natif ; la création du VPN est désactivée",
        "断开" to "Déconnecter",
        "点击选择默认节点" to "Touchez pour choisir le nœud par défaut",
        "应用识别正常" to "Attribution des applications active",
        "等待应用流量" to "En attente du trafic des applications",
        "选择节点" to "Choisir un nœud",
        "按应用选择出口" to "Choisir une sortie par application",
        "应用分流" to "Routage des applications",
        "路由解释" to "Explication du routage",
        "添加规则" to "Ajouter une règle",
        "代理" to "Proxy",
        "默认" to "Par défaut",
        "订阅已载入" to "Abonnements chargés",
        "本机加密" to "Chiffré localement",
        "正在刷新" to "Actualisation…",
        "订阅地址只在本机加密保存；诊断包默认移除 URL、凭据、节点地址与访问域名。" to "Les URL d’abonnement sont chiffrées localement ; les diagnostics retirent par défaut URL, identifiants, adresses de nœuds et domaines visités.",
        "不使用任何代理节点" to "Ne pas utiliser de nœud proxy",
        "未命中应用规则时不使用代理" to "Ne pas utiliser de proxy sans règle d’application correspondante",
        "未指定应用的 CN 流量直连" to "Connexion directe pour le trafic local sans règle d’application",
        "通过 HTTPS 加密解析，兼容性更好" to "Résolution chiffrée par HTTPS, très compatible",
        "通过 TLS 加密解析，协议边界更清晰" to "Résolution chiffrée par TLS, protocole clairement délimité",
        "国内隐私" to "Confidentialité locale",
        "阿里 DNS" to "DNS Ali",
        "腾讯 DNS" to "DNS Tencent",
        "海外隐私导向解析；中国大陆网络可能较慢" to "Résolveur privé à l’étranger ; peut être lent depuis la Chine continentale",
        "全球通用解析；中国大陆网络可能不可达" to "Résolveur mondial ; peut être inaccessible depuis la Chine continentale",
        "带恶意域名拦截的安全解析，不记录完整查询日志" to "Résolveur sécurisé avec blocage des domaines malveillants ; pas de journal complet",
        "隐私导向解析；不主动过滤广告内容" to "Résolveur axé confidentialité ; ne bloque pas activement les publicités",
        "定时探测订阅内节点，自动选择延迟最低的可用节点" to "Sonder régulièrement les nœuds et choisir automatiquement le nœud disponible le moins latent",
        "优先使用订阅中的首个可用节点，故障时自动切换" to "Préférer le premier nœud disponible et basculer automatiquement en cas de panne",
        "每个订阅维护自己的自动节点组，资源占用更低" to "Un groupe automatique par abonnement pour réduire l’usage des ressources",
        "把当前加载的多个订阅放入同一个测速与故障切换组" to "Réunir les abonnements chargés dans un groupe commun de test et de basculement",
        "完整接管 IPv4 与 IPv6 流量" to "Prendre en charge tout le trafic IPv4 et IPv6",
        "停用 IPv6 解析并在隧道内拒绝 IPv6，防止旁路" to "Désactiver la résolution IPv6 et refuser IPv6 dans le tunnel pour éviter les contournements",
        "所有域名使用当前选择的加密 DNS" to "Utiliser le DNS chiffré choisi pour tous les domaines",
        "国内域名优先国内上游，海外域名使用隐私上游" to "Utiliser un résolveur local pour les domaines locaux et un résolveur privé à l’étranger",
        "清晰留白与冷暖中性灰，适合日常使用" to "Des espaces nets et des gris neutres pour le quotidien",
        "低亮度深色画布，适合夜间与极客习惯" to "Une toile sombre peu lumineuse pour la nuit et les utilisateurs avancés",
        "公网出口、地区、ASN、代理标签与真实 HTTPS 延迟" to "Sortie publique, région, ASN, signaux proxy et latence HTTPS réelle",
        "本地证据检查 · 不生成虚假安全百分比" to "Vérifications locales · aucun pourcentage de sécurité inventé",
        "应用规则优先 · 修改后安全热重载" to "Règles d’application prioritaires · rechargement à chaud sécurisé",
        "默认开启 · 未指定应用的 CN 流量直连 · 应用分流优先" to "Activé par défaut · trafic local direct sans règle · routage par application prioritaire",
        "Keystore 加密 · 明文按会话清理" to "Chiffrement Keystore · texte en clair effacé par session",
        "本机导入、哈希校验、可回滚启停" to "Import local, vérification du hachage et retour arrière",
        "本机加密保存 · 应用规则优先 · 连接前可解释" to "Chiffré localement · règles d’application prioritaires · explicable avant connexion",
        "降低 WebRTC 暴露风险；可能影响音视频通话" to "Réduire l’exposition WebRTC ; peut affecter les appels audio/vidéo",
        "服务边界" to "Périmètre du service",
        "Weave 只提供本地客户端，不提供节点、线路、账号、托管 VPN 或集中式控制服务。" to "Weave est un client local ; il ne fournit ni nœuds, ni lignes, ni comptes, ni VPN hébergé, ni service de contrôle centralisé.",
        "本地开源发行配置：Weave 不运营账户、云端控制、节点中继、遥测或远程更新；用户主动选择的订阅、代理、DNS 和 IP 检测端点仍可能收到完成请求所需的数据。" to "Profil open source local : Weave n’exploite ni comptes, ni contrôle cloud, ni relais, ni télémétrie, ni mise à jour distante ; les points choisis peuvent recevoir les données nécessaires à la requête.",
    )
    WeaveLanguage.GERMAN -> mapOf(
        "私密网络" to "Privates Netzwerk",
        "更多" to "Mehr",
        "实时流量" to "Live-Datenverkehr",
        "网络延迟" to "Netzwerklatenz",
        "可用" to "Verfügbar",
        "等待测速" to "Messung ausstehend",
        "已保护" to "Geschützt",
        "正在连接" to "Verbindung wird hergestellt",
        "需要处理" to "Aktion erforderlich",
        "未连接" to "Nicht verbunden",
        "内核已安装" to "Core installiert",
        "内核不可用" to "Core nicht verfügbar",
        "保持私密" to "Privat bleiben",
        "本机规则已就绪，连接时按需加载原生内核" to "Lokale Regeln sind bereit; der native Core wird beim Verbinden geladen",
        "原生内核加载失败，已禁止建立 VPN" to "Der native Core konnte nicht geladen werden; VPN-Aufbau ist deaktiviert",
        "断开" to "Trennen",
        "点击选择默认节点" to "Tippen, um den Standardknoten zu wählen",
        "应用识别正常" to "App-Zuordnung funktioniert",
        "等待应用流量" to "Warten auf App-Datenverkehr",
        "选择节点" to "Knoten wählen",
        "按应用选择出口" to "Ausgang pro App wählen",
        "应用分流" to "App-Routing",
        "路由解释" to "Routing-Erklärung",
        "添加规则" to "Regel hinzufügen",
        "代理" to "Proxy",
        "默认" to "Standard",
        "订阅已载入" to "Abonnements geladen",
        "本机加密" to "Lokal verschlüsselt",
        "正在刷新" to "Wird aktualisiert",
        "订阅地址只在本机加密保存；诊断包默认移除 URL、凭据、节点地址与访问域名。" to "Abonnement-URLs werden lokal verschlüsselt; Diagnosepakete entfernen URLs, Zugangsdaten, Knotenadressen und Besuchsdomänen standardmäßig.",
        "不使用任何代理节点" to "Keinen Proxy-Knoten verwenden",
        "未命中应用规则时不使用代理" to "Ohne passende App-Regel keinen Proxy verwenden",
        "未指定应用的 CN 流量直连" to "Direktverbindung für Inlandverkehr ohne App-Regel",
        "通过 HTTPS 加密解析，兼容性更好" to "Verschlüsselte Auflösung über HTTPS mit hoher Kompatibilität",
        "通过 TLS 加密解析，协议边界更清晰" to "Verschlüsselte Auflösung über TLS mit klarer Protokollgrenze",
        "国内隐私" to "Inland-Datenschutz",
        "阿里 DNS" to "Ali DNS",
        "腾讯 DNS" to "Tencent DNS",
        "海外隐私导向解析；中国大陆网络可能较慢" to "Datenschutzorientierter Auslands-DNS; aus Festlandchina eventuell langsam",
        "全球通用解析；中国大陆网络可能不可达" to "Globaler DNS; aus Festlandchina eventuell nicht erreichbar",
        "带恶意域名拦截的安全解析，不记录完整查询日志" to "Sicherer DNS mit Blockierung schädlicher Domains; keine vollständigen Abfrageprotokolle",
        "隐私导向解析；不主动过滤广告内容" to "Datenschutzorientierter DNS; blockiert Werbung nicht aktiv",
        "定时探测订阅内节点，自动选择延迟最低的可用节点" to "Abonnement-Knoten regelmäßig prüfen und den verfügbaren Knoten mit der geringsten Latenz wählen",
        "优先使用订阅中的首个可用节点，故障时自动切换" to "Ersten verfügbaren Knoten bevorzugen und bei Fehlern automatisch wechseln",
        "每个订阅维护自己的自动节点组，资源占用更低" to "Pro Abonnement eine automatische Knotengruppe mit geringerem Ressourcenverbrauch",
        "把当前加载的多个订阅放入同一个测速与故障切换组" to "Geladene Abonnements in einer gemeinsamen Mess- und Failover-Gruppe bündeln",
        "完整接管 IPv4 与 IPv6 流量" to "IPv4- und IPv6-Datenverkehr vollständig übernehmen",
        "停用 IPv6 解析并在隧道内拒绝 IPv6，防止旁路" to "IPv6-Auflösung deaktivieren und IPv6 im Tunnel ablehnen, um Umgehungen zu verhindern",
        "所有域名使用当前选择的加密 DNS" to "Den gewählten verschlüsselten DNS für alle Domains verwenden",
        "国内域名优先国内上游，海外域名使用隐私上游" to "Inlands-Domains über einen Inland-DNS, Auslands-Domains über einen privaten DNS auflösen",
        "清晰留白与冷暖中性灰，适合日常使用" to "Klare Flächen und neutrale Grautöne für den Alltag",
        "低亮度深色画布，适合夜间与极客习惯" to "Dunkle Oberfläche mit geringer Helligkeit für Nacht und Power-User",
        "公网出口、地区、ASN、代理标签与真实 HTTPS 延迟" to "Öffentlicher Ausgang, Region, ASN, Proxy-Signale und echte HTTPS-Latenz",
        "本地证据检查 · 不生成虚假安全百分比" to "Lokale Nachweise · keine erfundene Sicherheitsquote",
        "应用规则优先 · 修改后安全热重载" to "App-Regeln zuerst · sicheres Hot-Reload nach Änderungen",
        "默认开启 · 未指定应用的 CN 流量直连 · 应用分流优先" to "Standardmäßig aktiv · Inlandverkehr ohne App-Regel direkt · App-Routing hat Vorrang",
        "Keystore 加密 · 明文按会话清理" to "Keystore-Verschlüsselung · Klartext pro Sitzung gelöscht",
        "本机导入、哈希校验、可回滚启停" to "Lokaler Import, Hashprüfung und Rollback",
        "本机加密保存 · 应用规则优先 · 连接前可解释" to "Lokal verschlüsselt · App-Regeln zuerst · vor dem Verbinden erklärbar",
        "降低 WebRTC 暴露风险；可能影响音视频通话" to "WebRTC-Exposition reduzieren; Audio-/Videoanrufe können beeinträchtigt werden",
        "服务边界" to "Dienstgrenze",
        "Weave 只提供本地客户端，不提供节点、线路、账号、托管 VPN 或集中式控制服务。" to "Weave ist ein lokaler Client und bietet keine Knoten, Leitungen, Konten, gehosteten VPN-Zugänge oder zentrale Steuerung.",
        "本地开源发行配置：Weave 不运营账户、云端控制、节点中继、遥测或远程更新；用户主动选择的订阅、代理、DNS 和 IP 检测端点仍可能收到完成请求所需的数据。" to "Lokales Open-Source-Profil: Weave betreibt keine Konten, Cloud-Steuerung, Relays, Telemetrie oder Remote-Updates; von dir gewählte Endpunkte können für die Anfrage notwendige Daten erhalten.",
    )
}
