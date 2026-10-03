package io.weave.client.ui

import io.weave.client.domain.WeaveLanguage

/**
 * Translations for copy added with the process split, scheduled updates, rule sets, custom
 * groups, backups and the diagnostic tool screens. Keyed by the Simplified Chinese source like
 * the rest of the table; merged after the older supplemental entries.
 */
internal val V2_TRANSLATIONS: Map<String, SupplementalTranslation> = mapOf(
    // Runtime and process
    "VPN 进程已停止，请重新连接" to SupplementalTranslation("The VPN process stopped. Please reconnect.", "VPN 行程已停止，請重新連線", "VPN プロセスが停止しました。再接続してください。", "Le processus VPN s’est arrêté. Reconnectez-vous.", "Der VPN-Prozess wurde beendet. Bitte erneut verbinden."),
    "正在恢复代理连接" to SupplementalTranslation("Restoring the proxy connection", "正在恢復代理連線", "プロキシ接続を復元中", "Rétablissement de la connexion proxy", "Proxy-Verbindung wird wiederhergestellt"),
    "已连接" to SupplementalTranslation("Connected", "已連線", "接続済み", "Connecté", "Verbunden"),
    "自定义策略组" to SupplementalTranslation("Custom groups", "自訂策略組", "カスタムグループ", "Groupes personnalisés", "Eigene Gruppen"),
    "链式" to SupplementalTranslation("chained", "鏈式", "チェーン", "en chaîne", "verkettet"),
    "入口" to SupplementalTranslation("Entry", "入口", "入口", "Entrée", "Eingang"),

    // Settings
    "跟随系统" to SupplementalTranslation("Follow system", "跟隨系統", "システムに従う", "Suivre le système", "Systemsprache"),
    "使用手机的系统语言；也可在系统“应用语言”中单独设置" to SupplementalTranslation("Use the phone’s language. You can also set it per app in system settings.", "使用手機的系統語言；也可在系統「應用程式語言」中單獨設定", "端末の言語を使用します。システムの「アプリの言語」でも個別に設定できます。", "Utilise la langue du téléphone. Réglable aussi par application dans les paramètres système.", "Verwendet die Telefonsprache. Auch pro App in den Systemeinstellungen einstellbar."),
    "直连应用绕过 VPN" to SupplementalTranslation("Direct apps bypass the VPN", "直連應用繞過 VPN", "ダイレクトのアプリは VPN を経由しない", "Apps directes hors VPN", "Direkte Apps umgehen das VPN"),
    "规则模式下选为直连的应用不进入隧道，更省电；Always-on 阻断时可能无法联网" to SupplementalTranslation("In rule mode, apps set to Direct skip the tunnel to save power. With Always-on blocking they may lose connectivity.", "規則模式下選為直連的應用不進入通道，更省電；Always-on 阻斷時可能無法連網", "ルールモードでダイレクトに設定したアプリはトンネルを通らず省電力になります。Always-on のブロック時は接続できない場合があります。", "En mode règle, les apps en direct évitent le tunnel pour économiser la batterie. Avec le blocage Always-on, elles peuvent perdre la connexion.", "Im Regelmodus umgehen direkte Apps den Tunnel und sparen Akku. Mit Always-on-Sperre haben sie ggf. keine Verbindung."),
    "系统 HTTP 代理" to SupplementalTranslation("System HTTP proxy", "系統 HTTP 代理", "システム HTTP プロキシ", "Proxy HTTP système", "System-HTTP-Proxy"),
    "浏览器可直接使用本机 127.0.0.1:7890；本机其他应用也能访问此端口" to SupplementalTranslation("Browsers can use 127.0.0.1:7890 directly; other apps on this phone can reach this port too", "瀏覽器可直接使用本機 127.0.0.1:7890；本機其他應用也能存取此連接埠", "ブラウザは 127.0.0.1:7890 を直接利用できます。この端末の他のアプリもこのポートにアクセスできます", "Les navigateurs peuvent utiliser 127.0.0.1:7890 ; les autres apps du téléphone peuvent aussi joindre ce port", "Browser können 127.0.0.1:7890 direkt nutzen; andere Apps auf dem Telefon erreichen diesen Port ebenfalls"),
    "局域网代理共享" to SupplementalTranslation("LAN proxy sharing", "區域網路代理共享", "LAN プロキシ共有", "Partage du proxy sur le LAN", "Proxy im LAN freigeben"),
    "已开启 · 需要用户名和密码" to SupplementalTranslation("On · username and password required", "已開啟 · 需要使用者名稱和密碼", "オン · ユーザー名とパスワードが必要", "Activé · identifiant et mot de passe requis", "An · Benutzername und Passwort nötig"),
    "关闭 · 可供热点或同一 Wi‑Fi 的设备使用" to SupplementalTranslation("Off · lets hotspot or same Wi‑Fi devices use this phone", "關閉 · 可供熱點或同一 Wi‑Fi 的裝置使用", "オフ · テザリングや同じ Wi‑Fi の端末が利用できます", "Désactivé · pour les appareils du point d’accès ou du même Wi‑Fi", "Aus · für Hotspot- oder WLAN-Geräte"),
    "订阅与数据" to SupplementalTranslation("Subscriptions & data", "訂閱與資料", "購読とデータ", "Abonnements et données", "Abos & Daten"),
    "自动更新订阅" to SupplementalTranslation("Auto-update subscriptions", "自動更新訂閱", "購読の自動更新", "Mise à jour auto des abonnements", "Abos automatisch aktualisieren"),
    "关闭 · 仅手动刷新" to SupplementalTranslation("Off · manual refresh only", "關閉 · 僅手動重新整理", "オフ · 手動更新のみ", "Désactivé · actualisation manuelle", "Aus · nur manuell"),
    "仅不计流量网络" to SupplementalTranslation("Unmetered networks only", "僅不計流量網路", "従量制でないネットワークのみ", "Réseaux non limités uniquement", "Nur ungemessene Netze"),
    "任意网络" to SupplementalTranslation("Any network", "任意網路", "任意のネットワーク", "Tout réseau", "Jedes Netz"),
    "备份与恢复" to SupplementalTranslation("Backup & restore", "備份與還原", "バックアップと復元", "Sauvegarde et restauration", "Sichern & wiederherstellen"),
    "导出为带密码的加密文件，可在新设备恢复" to SupplementalTranslation("Export a password-encrypted file and restore it on a new device", "匯出為含密碼的加密檔案，可在新裝置還原", "パスワード付きの暗号化ファイルに書き出し、新しい端末で復元できます", "Exporter un fichier chiffré par mot de passe, restaurable sur un nouvel appareil", "Als passwortverschlüsselte Datei exportieren und auf neuem Gerät wiederherstellen"),
    "引导 DNS" to SupplementalTranslation("Bootstrap DNS", "引導 DNS", "ブートストラップ DNS", "DNS d’amorçage", "Bootstrap-DNS"),
    "仅用于解析加密 DNS 服务器地址" to SupplementalTranslation("only resolves encrypted DNS server names", "僅用於解析加密 DNS 伺服器位址", "暗号化 DNS サーバー名の解決にのみ使用", "résout uniquement les serveurs DNS chiffrés", "löst nur verschlüsselte DNS-Server auf"),
    "明文，仅解析 DoH/DoT 服务器域名" to SupplementalTranslation("plaintext; only resolves DoH/DoT server names", "明文，僅解析 DoH/DoT 伺服器網域", "平文。DoH/DoT サーバー名の解決のみ", "en clair ; résout uniquement les serveurs DoH/DoT", "Klartext; löst nur DoH/DoT-Server auf"),
    "中国大陆（阿里 / 腾讯）" to SupplementalTranslation("Mainland China (Alibaba / Tencent)", "中國大陸（阿里 / 騰訊）", "中国本土（Alibaba / Tencent）", "Chine continentale (Alibaba / Tencent)", "Festlandchina (Alibaba / Tencent)"),
    "海外（Cloudflare / Quad9）" to SupplementalTranslation("Global (Cloudflare / Quad9)", "海外（Cloudflare / Quad9）", "海外（Cloudflare / Quad9）", "International (Cloudflare / Quad9)", "International (Cloudflare / Quad9)"),
    "远程规则集" to SupplementalTranslation("Remote rule sets", "遠端規則集", "リモートルールセット", "Jeux de règles distants", "Remote-Regelsätze"),
    "HTTPS 下载 · 逐条校验 · SHA-256 记录" to SupplementalTranslation("HTTPS download · every entry validated · SHA-256 recorded", "HTTPS 下載 · 逐條驗證 · 記錄 SHA-256", "HTTPS でダウンロード · 全件検証 · SHA-256 を記録", "Téléchargement HTTPS · chaque entrée validée · SHA-256 enregistré", "HTTPS-Download · jeder Eintrag geprüft · SHA-256 gespeichert"),
    "自定义策略组与链式代理" to SupplementalTranslation("Custom groups & proxy chains", "自訂策略組與鏈式代理", "カスタムグループとプロキシチェーン", "Groupes personnalisés et chaînes de proxy", "Eigene Gruppen & Proxy-Ketten"),
    "跨订阅挑选节点，可经入口节点二跳转发" to SupplementalTranslation("Pick nodes across subscriptions, optionally via an entry node (two hops)", "跨訂閱挑選節點，可經入口節點二跳轉發", "購読をまたいでノードを選び、入口ノード経由の 2 ホップにも対応", "Choisissez des nœuds de plusieurs abonnements, avec un nœud d’entrée en option (deux sauts)", "Knoten aus mehreren Abos wählen, optional über einen Eingangsknoten (zwei Hops)"),
    "诊断" to SupplementalTranslation("Diagnostics", "診斷", "診断", "Diagnostic", "Diagnose"),
    "实时连接" to SupplementalTranslation("Live connections", "即時連線", "リアルタイム接続", "Connexions en direct", "Live-Verbindungen"),
    "查看内核当前连接、命中规则与链路，可逐条断开" to SupplementalTranslation("See the core’s current connections, matched rules and chains; close any of them", "檢視核心目前的連線、命中規則與鏈路，可逐條中斷", "コアの現在の接続、一致したルールと経路を表示し、個別に切断できます", "Voir les connexions actuelles, règles et chaînes du moteur ; fermer chacune", "Aktuelle Verbindungen, Regeln und Ketten des Kerns ansehen und einzeln trennen"),
    "内核日志" to SupplementalTranslation("Core log", "核心日誌", "コアログ", "Journal du moteur", "Kern-Protokoll"),
    "临时开启，只在内存中显示；复制内容默认脱敏" to SupplementalTranslation("Temporary and in memory only; copies are redacted", "暫時開啟，只在記憶體中顯示；複製內容預設去識別化", "一時的にメモリ内でのみ表示。コピーは匿名化されます", "Temporaire et en mémoire uniquement ; les copies sont expurgées", "Nur vorübergehend im Speicher; Kopien werden bereinigt"),
    "3 · 远程规则集" to SupplementalTranslation("3 · Remote rule sets", "3 · 遠端規則集", "3 · リモートルールセット", "3 · Jeux de règles distants", "3 · Remote-Regelsätze"),
    "你添加的 HTTPS 规则集，下载后逐条校验并记录 SHA-256；只在手动刷新时联网。" to SupplementalTranslation("HTTPS rule sets you add are validated entry by entry with a recorded SHA-256; they only go online when you refresh them.", "你新增的 HTTPS 規則集，下載後逐條驗證並記錄 SHA-256；只在手動重新整理時連網。", "追加した HTTPS ルールセットは全件検証され SHA-256 が記録されます。通信するのは手動更新時のみです。", "Les jeux de règles HTTPS ajoutés sont validés entrée par entrée avec un SHA-256 ; ils ne se connectent qu’à l’actualisation.", "Hinzugefügte HTTPS-Regelsätze werden Eintrag für Eintrag geprüft (SHA-256); Netzzugriff nur beim Aktualisieren."),
    "4 · 国内智能直连" to SupplementalTranslation("4 · Mainland smart direct", "4 · 國內智慧直連", "4 · 中国向けスマートダイレクト", "4 · Direct intelligent local", "4 · Intelligente Direktverbindung"),
    "5 · 默认出口" to SupplementalTranslation("5 · Default exit", "5 · 預設出口", "5 · デフォルト出口", "5 · Sortie par défaut", "5 · Standardausgang"),
    "未命中前面规则的流量使用连接页选择的订阅、节点或自定义策略组。" to SupplementalTranslation("Traffic that matches no earlier rule uses the subscription, node or custom group chosen on the Connect tab.", "未命中前面規則的流量使用連線頁選擇的訂閱、節點或自訂策略組。", "前のルールに一致しない通信は、接続タブで選んだ購読、ノード、カスタムグループを使います。", "Le trafic sans règle correspondante utilise l’abonnement, le nœud ou le groupe choisi dans l’onglet Connexion.", "Nicht erfasster Verkehr nutzt das im Tab „Verbindung“ gewählte Abo, den Knoten oder die Gruppe."),
    "局域网互传" to SupplementalTranslation("LAN transfer", "區域網路互傳", "LAN 転送", "Transfert LAN", "LAN-Übertragung"),

    // Connections / logs
    "按应用、域名或规则筛选" to SupplementalTranslation("Filter by app, domain or rule", "按應用、網域或規則篩選", "アプリ、ドメイン、ルールで絞り込み", "Filtrer par app, domaine ou règle", "Nach App, Domain oder Regel filtern"),
    "无法读取内核连接；请确认 VPN 已连接。" to SupplementalTranslation("Cannot read core connections. Make sure the VPN is connected.", "無法讀取核心連線；請確認 VPN 已連線。", "コアの接続を読み取れません。VPN が接続されているか確認してください。", "Impossible de lire les connexions. Vérifiez que le VPN est connecté.", "Verbindungen nicht lesbar. Ist das VPN verbunden?"),
    "当前没有活动连接" to SupplementalTranslation("No active connections", "目前沒有活動連線", "アクティブな接続はありません", "Aucune connexion active", "Keine aktiven Verbindungen"),
    "断开全部" to SupplementalTranslation("Close all", "全部中斷", "すべて切断", "Tout fermer", "Alle trennen"),
    "断开连接" to SupplementalTranslation("Close connection", "中斷連線", "接続を切断", "Fermer la connexion", "Verbindung trennen"),
    "暂停" to SupplementalTranslation("Pause", "暫停", "一時停止", "Pause", "Pause"),
    "继续" to SupplementalTranslation("Resume", "繼續", "再開", "Reprendre", "Fortsetzen"),
    "清除" to SupplementalTranslation("Clear", "清除", "クリア", "Effacer", "Leeren"),
    "复制脱敏日志" to SupplementalTranslation("Copy redacted log", "複製去識別化日誌", "匿名化したログをコピー", "Copier le journal expurgé", "Bereinigtes Protokoll kopieren"),
    "隐藏 IP 地址" to SupplementalTranslation("Hide IP addresses", "隱藏 IP 位址", "IP アドレスを隠す", "Masquer les adresses IP", "IP-Adressen ausblenden"),
    "无法读取内核日志；请确认 VPN 已连接。" to SupplementalTranslation("Cannot read the core log. Make sure the VPN is connected.", "無法讀取核心日誌；請確認 VPN 已連線。", "コアログを読み取れません。VPN が接続されているか確認してください。", "Impossible de lire le journal. Vérifiez que le VPN est connecté.", "Protokoll nicht lesbar. Ist das VPN verbunden?"),

    // External import, auto update, LAN proxy
    "导入外部订阅？" to SupplementalTranslation("Import an external subscription?", "匯入外部訂閱？", "外部の購読をインポートしますか？", "Importer un abonnement externe ?", "Externes Abonnement importieren?"),
    "另一个应用请求 Weave 从下面的 HTTPS 地址下载订阅。只有在你信任此来源时才导入。" to SupplementalTranslation("Another app asked Weave to download a subscription from the HTTPS address below. Import it only if you trust this source.", "另一個應用請求 Weave 從下面的 HTTPS 位址下載訂閱。只有在你信任此來源時才匯入。", "別のアプリが下記の HTTPS アドレスから購読をダウンロードするよう求めています。信頼できる場合のみインポートしてください。", "Une autre app demande à Weave de télécharger un abonnement depuis l’adresse HTTPS ci-dessous. Importez-le seulement si vous faites confiance à cette source.", "Eine andere App möchte ein Abonnement von der HTTPS-Adresse unten laden. Nur importieren, wenn du der Quelle vertraust."),
    "另一个应用分享了一个订阅文件。导入前会完整校验，不执行其中的规则或控制面配置。" to SupplementalTranslation("Another app shared a subscription file. It is fully validated before import; its rules and control settings are not executed.", "另一個應用分享了一個訂閱檔案。匯入前會完整驗證，不執行其中的規則或控制面設定。", "別のアプリが購読ファイルを共有しました。インポート前に完全に検証し、含まれるルールや制御設定は実行しません。", "Une autre app a partagé un fichier d’abonnement. Il est entièrement validé ; ses règles et réglages de contrôle ne sont pas exécutés.", "Eine andere App hat eine Abo-Datei geteilt. Sie wird vollständig geprüft; Regeln und Steuerung darin werden nicht ausgeführt."),
    "另一个应用分享了一段订阅文本。导入前会完整校验。" to SupplementalTranslation("Another app shared subscription text. It is fully validated before import.", "另一個應用分享了一段訂閱文字。匯入前會完整驗證。", "別のアプリが購読テキストを共有しました。インポート前に完全に検証します。", "Une autre app a partagé un texte d’abonnement. Il est entièrement validé avant l’import.", "Eine andere App hat Abo-Text geteilt. Er wird vor dem Import vollständig geprüft."),
    "来源" to SupplementalTranslation("Source", "來源", "取得元", "Source", "Quelle"),
    "只刷新 HTTPS 远程订阅。节点变化通过安全审计后才会生效；影响已固定出口的更新会保留旧版本并提醒你预览。" to SupplementalTranslation("Only HTTPS subscriptions are refreshed. Node changes apply after the safety audit; updates that affect pinned exits keep the old version and ask you to preview.", "只重新整理 HTTPS 遠端訂閱。節點變化通過安全稽核後才會生效；影響已固定出口的更新會保留舊版本並提醒你預覽。", "HTTPS の購読のみ更新します。ノードの変更は安全監査後に反映され、固定した出口に影響する更新は旧版を保持してプレビューを求めます。", "Seuls les abonnements HTTPS sont actualisés. Les changements passent l’audit de sécurité ; ceux qui touchent des sorties épinglées gardent l’ancienne version et demandent un aperçu.", "Nur HTTPS-Abos werden aktualisiert. Änderungen gelten nach der Sicherheitsprüfung; betrifft ein Update festgelegte Ausgänge, bleibt die alte Version und du wirst um eine Vorschau gebeten."),
    "仅在不计流量的网络" to SupplementalTranslation("Only on unmetered networks", "僅在不計流量的網路", "従量制でないネットワークのみ", "Uniquement sur réseau non limité", "Nur in ungemessenen Netzen"),
    "通常是 Wi‑Fi；避免消耗移动数据" to SupplementalTranslation("Usually Wi‑Fi; avoids using mobile data", "通常是 Wi‑Fi；避免消耗行動數據", "通常は Wi‑Fi。モバイルデータを消費しません", "Généralement le Wi‑Fi ; évite les données mobiles", "Meist WLAN; spart mobile Daten"),
    "开启后，同一 Wi‑Fi 或连接你热点的设备可以把这台手机当作 HTTP/SOCKS 代理。其他设备必须使用下方的用户名和密码；这些设备的流量会按你的默认出口转发。" to SupplementalTranslation("When on, devices on the same Wi‑Fi or your hotspot can use this phone as an HTTP/SOCKS proxy. They must use the username and password below; their traffic follows your default exit.", "開啟後，同一 Wi‑Fi 或連線你熱點的裝置可以把這支手機當作 HTTP/SOCKS 代理。其他裝置必須使用下方的使用者名稱和密碼；這些裝置的流量會按你的預設出口轉發。", "オンにすると、同じ Wi‑Fi やテザリング中の端末がこの端末を HTTP/SOCKS プロキシとして使えます。下記のユーザー名とパスワードが必要で、通信はデフォルト出口に従います。", "Activé, les appareils du même Wi‑Fi ou de votre point d’accès peuvent utiliser ce téléphone comme proxy HTTP/SOCKS, avec l’identifiant et le mot de passe ci-dessous ; leur trafic suit votre sortie par défaut.", "Wenn aktiv, können Geräte im selben WLAN oder Hotspot dieses Telefon als HTTP/SOCKS-Proxy nutzen – mit Benutzername und Passwort unten; ihr Verkehr folgt deinem Standardausgang."),
    "开启共享" to SupplementalTranslation("Share", "開啟共享", "共有する", "Partager", "Freigeben"),
    "端口" to SupplementalTranslation("Port", "連接埠", "ポート", "Port", "Port"),
    "用户名" to SupplementalTranslation("Username", "使用者名稱", "ユーザー名", "Identifiant", "Benutzername"),
    "密码" to SupplementalTranslation("Password", "密碼", "パスワード", "Mot de passe", "Passwort"),
    "地址使用这台手机在当前 Wi‑Fi 或热点中的 IP。修改后需要重新连接才会生效。" to SupplementalTranslation("Use this phone’s IP on the current Wi‑Fi or hotspot. Changes take effect after reconnecting.", "位址使用這支手機在目前 Wi‑Fi 或熱點中的 IP。修改後需要重新連線才會生效。", "アドレスは現在の Wi‑Fi またはテザリングでのこの端末の IP です。変更は再接続後に反映されます。", "Utilisez l’IP de ce téléphone sur le Wi‑Fi ou le point d’accès actuel. Les changements s’appliquent après reconnexion.", "Verwende die IP dieses Telefons im aktuellen WLAN oder Hotspot. Änderungen gelten nach dem Neuverbinden."),

    // Rule sets
    "域名列表" to SupplementalTranslation("Domain list", "網域清單", "ドメインリスト", "Liste de domaines", "Domainliste"),
    "IP 网段列表" to SupplementalTranslation("IP range list", "IP 網段清單", "IP 範囲リスト", "Liste de plages IP", "IP-Bereichsliste"),
    "经典规则" to SupplementalTranslation("Classic rules", "經典規則", "クラシックルール", "Règles classiques", "Klassische Regeln"),
    "规则只在你手动添加或刷新时下载；每条都会先校验" to SupplementalTranslation("Rules are downloaded only when you add or refresh them; every entry is validated first", "規則只在你手動新增或重新整理時下載；每條都會先驗證", "ルールは追加・更新時のみダウンロードされ、全件を先に検証します", "Les règles ne sont téléchargées qu’à l’ajout ou l’actualisation ; chaque entrée est validée", "Regeln werden nur beim Hinzufügen oder Aktualisieren geladen; jeder Eintrag wird geprüft"),
    "全部刷新" to SupplementalTranslation("Refresh all", "全部重新整理", "すべて更新", "Tout actualiser", "Alle aktualisieren"),
    "添加规则集" to SupplementalTranslation("Add rule set", "新增規則集", "ルールセットを追加", "Ajouter un jeu de règles", "Regelsatz hinzufügen"),
    "编辑规则集" to SupplementalTranslation("Edit rule set", "編輯規則集", "ルールセットを編集", "Modifier le jeu de règles", "Regelsatz bearbeiten"),
    "正在下载并校验" to SupplementalTranslation("Downloading and validating", "正在下載並驗證", "ダウンロードして検証中", "Téléchargement et validation", "Wird geladen und geprüft"),
    "还没有规则集" to SupplementalTranslation("No rule sets yet", "還沒有規則集", "ルールセットはまだありません", "Aucun jeu de règles", "Noch keine Regelsätze"),
    "添加 HTTPS 地址的域名列表、IP 网段列表或经典规则，选择直连、代理或阻止。应用规则仍然优先。" to SupplementalTranslation("Add a domain list, IP range list or classic rules from an HTTPS address and choose direct, proxy or block. App rules still come first.", "新增 HTTPS 位址的網域清單、IP 網段清單或經典規則，選擇直連、代理或阻止。應用規則仍然優先。", "HTTPS アドレスのドメインリスト、IP 範囲リスト、クラシックルールを追加し、ダイレクト・プロキシ・ブロックを選びます。アプリのルールが優先されます。", "Ajoutez une liste de domaines, de plages IP ou des règles classiques depuis une adresse HTTPS, puis choisissez direct, proxy ou blocage. Les règles d’app restent prioritaires.", "Füge eine Domain-, IP-Bereichsliste oder klassische Regeln per HTTPS hinzu und wähle Direkt, Proxy oder Blockieren. App-Regeln haben Vorrang."),
    "名称" to SupplementalTranslation("Name", "名稱", "名前", "Nom", "Name"),
    "HTTPS 地址" to SupplementalTranslation("HTTPS address", "HTTPS 位址", "HTTPS アドレス", "Adresse HTTPS", "HTTPS-Adresse"),
    "格式" to SupplementalTranslation("Format", "格式", "形式", "Format", "Format"),
    "命中后" to SupplementalTranslation("When matched", "命中後", "一致したとき", "En cas de correspondance", "Bei Treffer"),
    "支持每行一条的文本列表或 YAML payload；二进制 .mrs 格式不受支持。" to SupplementalTranslation("Plain text (one entry per line) or a YAML payload; binary .mrs files are not supported.", "支援每行一條的文字清單或 YAML payload；不支援二進位 .mrs 格式。", "1 行 1 件のテキストか YAML の payload に対応。バイナリの .mrs 形式は非対応です。", "Texte (une entrée par ligne) ou payload YAML ; les fichiers binaires .mrs ne sont pas pris en charge.", "Text (ein Eintrag pro Zeile) oder YAML-Payload; binäre .mrs-Dateien werden nicht unterstützt."),
    "下载并保存" to SupplementalTranslation("Download & save", "下載並儲存", "ダウンロードして保存", "Télécharger et enregistrer", "Laden & speichern"),
    "规则集 YAML 格式无效" to SupplementalTranslation("The rule set YAML is invalid", "規則集 YAML 格式無效", "ルールセットの YAML が無効です", "YAML du jeu de règles invalide", "Regelsatz-YAML ist ungültig"),
    "规则集缺少 payload 列表" to SupplementalTranslation("The rule set has no payload list", "規則集缺少 payload 清單", "ルールセットに payload リストがありません", "Le jeu de règles n’a pas de liste payload", "Dem Regelsatz fehlt die payload-Liste"),
    "规则集没有有效条目" to SupplementalTranslation("The rule set has no valid entries", "規則集沒有有效條目", "ルールセットに有効な項目がありません", "Le jeu de règles n’a aucune entrée valide", "Der Regelsatz hat keine gültigen Einträge"),
    "规则集包含无法校验的条目，已拒绝导入" to SupplementalTranslation("The rule set contains entries that cannot be validated and was rejected", "規則集包含無法驗證的條目，已拒絕匯入", "検証できない項目を含むため、ルールセットを拒否しました", "Le jeu contient des entrées non vérifiables et a été refusé", "Der Regelsatz enthält nicht prüfbare Einträge und wurde abgelehnt"),
    "请填写规则集名称" to SupplementalTranslation("Enter a rule set name", "請填寫規則集名稱", "ルールセット名を入力してください", "Saisissez un nom", "Namen eingeben"),
    "规则集地址必须使用 HTTPS" to SupplementalTranslation("The rule set address must use HTTPS", "規則集位址必須使用 HTTPS", "ルールセットのアドレスは HTTPS が必要です", "L’adresse doit utiliser HTTPS", "Die Adresse muss HTTPS verwenden"),
    "正在应用远程规则集" to SupplementalTranslation("Applying remote rule sets", "正在套用遠端規則集", "リモートルールセットを適用中", "Application des jeux de règles distants", "Remote-Regelsätze werden angewendet"),
    "规则集更新失败" to SupplementalTranslation("Rule set update failed", "規則集更新失敗", "ルールセットの更新に失敗しました", "Échec de la mise à jour du jeu de règles", "Regelsatz-Aktualisierung fehlgeschlagen"),

    // Custom groups
    "跨订阅组合节点；设置入口节点后成为链式代理" to SupplementalTranslation("Combine nodes across subscriptions; add an entry node to make a proxy chain", "跨訂閱組合節點；設定入口節點後成為鏈式代理", "購読をまたいでノードを組み合わせ、入口ノードを設定するとプロキシチェーンになります", "Combinez des nœuds de plusieurs abonnements ; ajoutez un nœud d’entrée pour créer une chaîne", "Knoten aus mehreren Abos kombinieren; mit Eingangsknoten entsteht eine Proxy-Kette"),
    "新建策略组" to SupplementalTranslation("New group", "新增策略組", "グループを作成", "Nouveau groupe", "Neue Gruppe"),
    "编辑策略组" to SupplementalTranslation("Edit group", "編輯策略組", "グループを編集", "Modifier le groupe", "Gruppe bearbeiten"),
    "还没有自定义策略组" to SupplementalTranslation("No custom groups yet", "還沒有自訂策略組", "カスタムグループはまだありません", "Aucun groupe personnalisé", "Noch keine eigenen Gruppen"),
    "新建后可在默认出口或应用分流中选择。链式代理的路径为：本机 → 入口节点 → 成员节点 → 目标网站。" to SupplementalTranslation("Once created, pick it as the default exit or for an app route. A chain goes: this phone → entry node → member node → website.", "新增後可在預設出口或應用分流中選擇。鏈式代理的路徑為：本機 → 入口節點 → 成員節點 → 目標網站。", "作成後はデフォルト出口やアプリのルートで選べます。チェーンの経路：この端末 → 入口ノード → メンバーノード → 目的のサイト。", "Une fois créé, choisissez-le comme sortie par défaut ou pour une app. Une chaîne : ce téléphone → nœud d’entrée → nœud membre → site.", "Nach dem Erstellen als Standardausgang oder für eine App wählbar. Eine Kette: Telefon → Eingangsknoten → Mitgliedsknoten → Website."),
    "链式代理" to SupplementalTranslation("Proxy chain", "鏈式代理", "プロキシチェーン", "Chaîne de proxy", "Proxy-Kette"),
    "先连接入口节点，再由成员节点访问目标；延迟会叠加" to SupplementalTranslation("Connects to the entry node first, then the member node reaches the site; latency adds up", "先連線入口節點，再由成員節點存取目標；延遲會疊加", "先に入口ノードに接続し、メンバーノードが目的地にアクセスします。遅延は加算されます", "Connexion au nœud d’entrée, puis le membre atteint le site ; la latence s’additionne", "Erst Eingangsknoten, dann erreicht der Mitgliedsknoten das Ziel; Latenzen addieren sich"),
    "选择入口节点" to SupplementalTranslation("Choose entry node", "選擇入口節點", "入口ノードを選択", "Choisir le nœud d’entrée", "Eingangsknoten wählen"),
    "删除策略组？" to SupplementalTranslation("Delete this group?", "刪除策略組？", "このグループを削除しますか？", "Supprimer ce groupe ?", "Diese Gruppe löschen?"),
    "默认出口或应用分流若在使用它，将改为阻止联网，直到你重新选择出口。" to SupplementalTranslation("If the default exit or an app route uses it, that traffic is blocked until you choose a new exit.", "預設出口或應用分流若在使用它，將改為阻止連網，直到你重新選擇出口。", "デフォルト出口やアプリのルートで使用中の場合、新しい出口を選ぶまで通信をブロックします。", "Si la sortie par défaut ou une app l’utilise, ce trafic sera bloqué jusqu’au choix d’une nouvelle sortie.", "Nutzt der Standardausgang oder eine App diese Gruppe, wird der Verkehr blockiert, bis du einen neuen Ausgang wählst."),
    "入口节点不能同时作为出口成员" to SupplementalTranslation("The entry node cannot also be a member", "入口節點不能同時作為出口成員", "入口ノードはメンバーにできません", "Le nœud d’entrée ne peut pas être membre", "Der Eingangsknoten kann kein Mitglied sein"),
    "策略组 ID 无效" to SupplementalTranslation("Invalid group ID", "策略組 ID 無效", "グループ ID が無効です", "ID de groupe invalide", "Ungültige Gruppen-ID"),
    "请填写策略组名称" to SupplementalTranslation("Enter a group name", "請填寫策略組名稱", "グループ名を入力してください", "Saisissez un nom de groupe", "Gruppennamen eingeben"),
    "请至少选择一个节点" to SupplementalTranslation("Select at least one node", "請至少選擇一個節點", "ノードを 1 つ以上選択してください", "Sélectionnez au moins un nœud", "Mindestens einen Knoten wählen"),
    "策略组 ID 重复" to SupplementalTranslation("Duplicate group ID", "策略組 ID 重複", "グループ ID が重複しています", "ID de groupe en double", "Doppelte Gruppen-ID"),
    "无法保存策略组" to SupplementalTranslation("Could not save the group", "無法儲存策略組", "グループを保存できません", "Impossible d’enregistrer le groupe", "Gruppe konnte nicht gespeichert werden"),
    "正在应用自定义策略组" to SupplementalTranslation("Applying custom groups", "正在套用自訂策略組", "カスタムグループを適用中", "Application des groupes personnalisés", "Eigene Gruppen werden angewendet"),

    // Backup
    "PBKDF2 + AES-256-GCM 加密；Weave 无法找回忘记的密码" to SupplementalTranslation("PBKDF2 + AES-256-GCM encryption; Weave cannot recover a forgotten password", "PBKDF2 + AES-256-GCM 加密；Weave 無法找回忘記的密碼", "PBKDF2 + AES-256-GCM で暗号化。忘れたパスワードは復元できません", "Chiffrement PBKDF2 + AES-256-GCM ; Weave ne peut pas récupérer un mot de passe oublié", "PBKDF2- + AES-256-GCM-Verschlüsselung; vergessene Passwörter sind nicht wiederherstellbar"),
    "备份密码" to SupplementalTranslation("Backup password", "備份密碼", "バックアップのパスワード", "Mot de passe de sauvegarde", "Backup-Passwort"),
    "至少 8 个字符" to SupplementalTranslation("At least 8 characters", "至少 8 個字元", "8 文字以上", "Au moins 8 caractères", "Mindestens 8 Zeichen"),
    "再次输入（导出时）" to SupplementalTranslation("Repeat (for export)", "再次輸入（匯出時）", "もう一度入力（書き出し時）", "Confirmer (pour l’export)", "Wiederholen (für Export)"),
    "导出备份" to SupplementalTranslation("Export backup", "匯出備份", "バックアップを書き出す", "Exporter la sauvegarde", "Backup exportieren"),
    "读取备份" to SupplementalTranslation("Open backup", "讀取備份", "バックアップを開く", "Ouvrir une sauvegarde", "Backup öffnen"),
    "包含订阅、应用分流、默认出口、DNS 与路由设置、本地规则、远程规则集和自定义策略组。不包含离线策略包、系统 HTTP 代理和局域网共享设置。" to SupplementalTranslation("Includes subscriptions, app routes, default exit, DNS and routing settings, local rules, remote rule sets and custom groups. Excludes offline policy packs, the system HTTP proxy and LAN sharing.", "包含訂閱、應用分流、預設出口、DNS 與路由設定、本機規則、遠端規則集和自訂策略組。不包含離線策略包、系統 HTTP 代理和區域網路共享設定。", "購読、アプリのルート、デフォルト出口、DNS とルーティング設定、ローカルルール、リモートルールセット、カスタムグループを含みます。オフラインポリシー、システム HTTP プロキシ、LAN 共有は含みません。", "Inclut abonnements, routes d’app, sortie par défaut, DNS et routage, règles locales, jeux distants et groupes. Exclut les politiques hors ligne, le proxy HTTP système et le partage LAN.", "Enthält Abos, App-Routen, Standardausgang, DNS- und Routing-Einstellungen, lokale Regeln, Remote-Regelsätze und eigene Gruppen. Ohne Offline-Richtlinien, System-HTTP-Proxy und LAN-Freigabe."),
    "备份内容" to SupplementalTranslation("Backup contents", "備份內容", "バックアップの内容", "Contenu de la sauvegarde", "Backup-Inhalt"),
    "恢复会覆盖同一订阅和现有设置；同名订阅按安全审计原位更新。" to SupplementalTranslation("Restoring overwrites matching subscriptions and current settings; matching subscriptions are updated in place after the safety audit.", "還原會覆蓋同一訂閱和現有設定；同名訂閱按安全稽核原位更新。", "復元すると同じ購読と現在の設定を上書きします。同じ購読は安全監査のうえで更新されます。", "La restauration écrase les abonnements correspondants et les réglages ; ils sont mis à jour après l’audit de sécurité.", "Wiederherstellen überschreibt passende Abos und Einstellungen; Abos werden nach der Sicherheitsprüfung aktualisiert."),
    "恢复" to SupplementalTranslation("Restore", "還原", "復元", "Restaurer", "Wiederherstellen"),
    "备份已导出；请妥善保管文件和密码" to SupplementalTranslation("Backup exported. Keep the file and password safe.", "備份已匯出；請妥善保管檔案和密碼", "バックアップを書き出しました。ファイルとパスワードを安全に保管してください。", "Sauvegarde exportée. Conservez le fichier et le mot de passe en lieu sûr.", "Backup exportiert. Datei und Passwort sicher aufbewahren."),
    "备份导出失败" to SupplementalTranslation("Backup export failed", "備份匯出失敗", "バックアップの書き出しに失敗しました", "Échec de l’export", "Backup-Export fehlgeschlagen"),
    "无法写入所选文件" to SupplementalTranslation("Cannot write the selected file", "無法寫入所選檔案", "選択したファイルに書き込めません", "Impossible d’écrire le fichier", "Datei kann nicht geschrieben werden"),
    "无法读取所选文件" to SupplementalTranslation("Cannot read the selected file", "無法讀取所選檔案", "選択したファイルを読み取れません", "Impossible de lire le fichier", "Datei kann nicht gelesen werden"),
    "无法读取备份" to SupplementalTranslation("Cannot read the backup", "無法讀取備份", "バックアップを読み取れません", "Impossible de lire la sauvegarde", "Backup kann nicht gelesen werden"),
    "恢复失败，已尽量保留现有数据" to SupplementalTranslation("Restore failed; existing data was kept where possible", "還原失敗，已盡量保留現有資料", "復元に失敗しました。既存のデータは可能な限り保持しています", "Échec de la restauration ; les données existantes ont été conservées autant que possible", "Wiederherstellung fehlgeschlagen; vorhandene Daten wurden möglichst behalten"),
    "已从备份恢复；设备相关设置（系统代理、局域网共享）需重新开启" to SupplementalTranslation("Restored from backup. Device-specific settings (system proxy, LAN sharing) need to be turned on again.", "已從備份還原；裝置相關設定（系統代理、區域網路共享）需重新開啟", "バックアップから復元しました。端末固有の設定（システムプロキシ、LAN 共有）は再度オンにしてください。", "Restauré. Les réglages propres à l’appareil (proxy système, partage LAN) doivent être réactivés.", "Wiederhergestellt. Geräteeinstellungen (System-Proxy, LAN-Freigabe) bitte erneut aktivieren."),
    "正在应用恢复的配置" to SupplementalTranslation("Applying the restored configuration", "正在套用還原的設定", "復元した設定を適用中", "Application de la configuration restaurée", "Wiederhergestellte Konfiguration wird angewendet"),
    "这不是有效的 Weave 备份" to SupplementalTranslation("This is not a valid Weave backup", "這不是有效的 Weave 備份", "有効な Weave バックアップではありません", "Ce n’est pas une sauvegarde Weave valide", "Kein gültiges Weave-Backup"),
    "不支持的备份版本" to SupplementalTranslation("Unsupported backup version", "不支援的備份版本", "未対応のバックアップ形式です", "Version de sauvegarde non prise en charge", "Nicht unterstützte Backup-Version"),
    "密码错误或备份已损坏" to SupplementalTranslation("Wrong password or damaged backup", "密碼錯誤或備份已損毀", "パスワードが違うか、バックアップが破損しています", "Mot de passe incorrect ou sauvegarde endommagée", "Falsches Passwort oder beschädigtes Backup"),
    "备份内容格式无效" to SupplementalTranslation("The backup contents are invalid", "備份內容格式無效", "バックアップの内容が無効です", "Contenu de sauvegarde invalide", "Backup-Inhalt ist ungültig"),
    "备份内容过大" to SupplementalTranslation("The backup contents are too large", "備份內容過大", "バックアップの内容が大きすぎます", "Contenu de sauvegarde trop volumineux", "Backup-Inhalt ist zu groß"),
    "备份文件过大" to SupplementalTranslation("The backup file is too large", "備份檔案過大", "バックアップファイルが大きすぎます", "Fichier de sauvegarde trop volumineux", "Backup-Datei ist zu groß"),
    "备份密码至少 8 个字符" to SupplementalTranslation("The backup password needs at least 8 characters", "備份密碼至少 8 個字元", "バックアップのパスワードは 8 文字以上必要です", "Le mot de passe doit comporter au moins 8 caractères", "Das Backup-Passwort braucht mindestens 8 Zeichen"),
    "备份缺少清单" to SupplementalTranslation("The backup has no manifest", "備份缺少清單", "バックアップにマニフェストがありません", "La sauvegarde n’a pas de manifeste", "Dem Backup fehlt das Manifest"),

    // Status messages
    "正在应用直连应用绕过设置" to SupplementalTranslation("Applying the direct-app bypass", "正在套用直連應用繞過設定", "ダイレクトアプリのバイパスを適用中", "Application du contournement des apps directes", "Umgehung für direkte Apps wird angewendet"),
    "正在应用引导 DNS" to SupplementalTranslation("Applying bootstrap DNS", "正在套用引導 DNS", "ブートストラップ DNS を適用中", "Application du DNS d’amorçage", "Bootstrap-DNS wird angewendet"),
    "正在启用系统 HTTP 代理" to SupplementalTranslation("Turning on the system HTTP proxy", "正在啟用系統 HTTP 代理", "システム HTTP プロキシを有効化中", "Activation du proxy HTTP système", "System-HTTP-Proxy wird aktiviert"),
    "正在关闭系统 HTTP 代理" to SupplementalTranslation("Turning off the system HTTP proxy", "正在關閉系統 HTTP 代理", "システム HTTP プロキシを無効化中", "Désactivation du proxy HTTP système", "System-HTTP-Proxy wird deaktiviert"),
    "正在开启局域网共享" to SupplementalTranslation("Turning on LAN sharing", "正在開啟區域網路共享", "LAN 共有を有効化中", "Activation du partage LAN", "LAN-Freigabe wird aktiviert"),
    "正在关闭局域网共享" to SupplementalTranslation("Turning off LAN sharing", "正在關閉區域網路共享", "LAN 共有を無効化中", "Désactivation du partage LAN", "LAN-Freigabe wird deaktiviert"),

    // Subscription card
    "已用" to SupplementalTranslation("Used", "已用", "使用済み", "Utilisé", "Verbraucht"),
    "到期" to SupplementalTranslation("Expires", "到期", "期限", "Expire le", "Läuft ab"),
    "已到期" to SupplementalTranslation("Expired", "已到期", "期限切れ", "Expiré le", "Abgelaufen"),
    "刚刚更新" to SupplementalTranslation("Updated just now", "剛剛更新", "たった今更新", "Mis à jour à l’instant", "Gerade aktualisiert"),

    // Connect tab: status overview, exit guidance, mode descriptions and live data
    "连接状态" to SupplementalTranslation("Connection status", "連線狀態", "接続状態", "État de la connexion", "Verbindungsstatus"),
    "连接未建立" to SupplementalTranslation("Connection failed", "連線未建立", "接続できませんでした", "Connexion non établie", "Verbindung nicht hergestellt"),
    "正在建立隧道并校验配置，请稍候。" to SupplementalTranslation("Setting up the tunnel and validating the configuration. Please wait.", "正在建立通道並驗證設定，請稍候。", "トンネルを確立し、設定を検証しています。しばらくお待ちください。", "Établissement du tunnel et vérification de la configuration. Patientez.", "Tunnel wird aufgebaut und Konfiguration geprüft. Bitte warten."),
    "连接未能建立。可直接重试，或更换出口后再连接。" to SupplementalTranslation("The connection could not be established. Retry, or change the exit and connect again.", "連線未能建立。可直接重試，或更換出口後再連線。", "接続を確立できませんでした。再試行するか、出口を変更してから接続してください。", "La connexion n’a pas pu être établie. Réessayez ou changez de sortie avant de vous reconnecter.", "Die Verbindung konnte nicht hergestellt werden. Erneut versuchen oder Ausgang wechseln und neu verbinden."),
    "下一步：在下方选择出口，然后连接。" to SupplementalTranslation("Next: choose an exit below, then connect.", "下一步：在下方選擇出口，然後連線。", "次へ：下で出口を選んでから接続します。", "Étape suivante : choisissez une sortie ci-dessous, puis connectez-vous.", "Nächster Schritt: unten einen Ausgang wählen, dann verbinden."),
    "重试连接" to SupplementalTranslation("Retry", "重試連線", "再接続", "Réessayer", "Erneut verbinden"),
    "更换出口" to SupplementalTranslation("Change exit", "更換出口", "出口を変更", "Changer de sortie", "Ausgang wechseln"),
    "尚未选择出口" to SupplementalTranslation("No exit selected", "尚未選擇出口", "出口が未選択です", "Aucune sortie choisie", "Kein Ausgang gewählt"),
    "未选择时自动使用第一个可用订阅" to SupplementalTranslation("Until you choose, the first usable subscription is used automatically", "未選擇時自動使用第一個可用訂閱", "未選択の間は、最初に利用可能な購読を自動で使います", "Sans choix, le premier abonnement utilisable est utilisé automatiquement", "Ohne Auswahl wird automatisch das erste nutzbare Abo verwendet"),
    "直连模式下不使用出口" to SupplementalTranslation("Not used in direct mode", "直連模式下不使用出口", "ダイレクトモードでは出口を使いません", "Non utilisée en mode direct", "Im Direktmodus nicht verwendet"),
    "应用与域名规则优先，其余流量走默认出口" to SupplementalTranslation("App and domain rules first; other traffic uses the default exit", "應用與網域規則優先，其餘流量走預設出口", "アプリとドメインのルールを優先し、それ以外はデフォルト出口を通ります", "Règles d’app et de domaine d’abord ; le reste passe par la sortie par défaut", "App- und Domainregeln zuerst; übriger Verkehr nutzt den Standardausgang"),
    "应用分流暂停，流量统一走默认出口" to SupplementalTranslation("App routes paused; all traffic uses the default exit", "應用分流暫停，流量統一走預設出口", "アプリのルートを一時停止し、すべての通信がデフォルト出口を通ります", "Routes d’app suspendues ; tout le trafic passe par la sortie par défaut", "App-Routen pausiert; gesamter Verkehr nutzt den Standardausgang"),
    "实时数据与检测" to SupplementalTranslation("Live data & checks", "即時資料與檢測", "リアルタイムデータと検査", "Données en direct et vérifications", "Live-Daten & Prüfungen"),
    "连接后显示" to SupplementalTranslation("Shown once connected", "連線後顯示", "接続後に表示", "Affiché une fois connecté", "Nach dem Verbinden sichtbar"),

    // Settings tiers and expandable groups
    "常用" to SupplementalTranslation("Everyday", "常用", "よく使う設定", "Essentiel", "Häufig genutzt"),
    "高级" to SupplementalTranslation("Advanced", "進階", "詳細設定", "Avancé", "Erweitert"),
    "高级选项默认收起，展开即可查看全部设置" to SupplementalTranslation("Advanced options are collapsed by default; expand a group to see every setting", "進階選項預設收合，展開即可查看全部設定", "詳細設定は折りたたまれています。グループを開くとすべての設定が表示されます", "Les options avancées sont repliées ; dépliez un groupe pour voir tous les réglages", "Erweiterte Optionen sind eingeklappt; eine Gruppe aufklappen, um alle Einstellungen zu sehen"),
    "外观与语言" to SupplementalTranslation("Appearance & language", "外觀與語言", "外観と言語", "Apparence et langue", "Darstellung & Sprache"),
    "DNS 与连接" to SupplementalTranslation("DNS & connection", "DNS 與連線", "DNS と接続", "DNS et connexion", "DNS & Verbindung"),
    "安全保护" to SupplementalTranslation("Protection", "安全保護", "保護", "Protection", "Schutz"),
    "数据管理" to SupplementalTranslation("Data management", "資料管理", "データ管理", "Gestion des données", "Datenverwaltung"),
    "连接进阶" to SupplementalTranslation("More connection options", "連線進階", "接続の詳細設定", "Connexion avancée", "Weitere Verbindungsoptionen"),
    "路由与规则" to SupplementalTranslation("Routing & rules", "路由與規則", "ルーティングとルール", "Routage et règles", "Routing & Regeln"),
    "已开启" to SupplementalTranslation("On", "已開啟", "オン", "Activé", "An"),
    "展开" to SupplementalTranslation("Expand", "展開", "展開", "Déplier", "Aufklappen"),
    "收起" to SupplementalTranslation("Collapse", "收合", "折りたたむ", "Replier", "Einklappen"),

    // Subscriptions: empty state, import options and refresh status
    "远程订阅" to SupplementalTranslation("remote subscriptions", "遠端訂閱", "リモート購読", "abonnements distants", "Remote-Abos"),
    "其他导入方式" to SupplementalTranslation("Other ways to import", "其他匯入方式", "その他のインポート方法", "Autres méthodes d’import", "Weitere Importwege"),
    "从另一台设备接收或发送订阅" to SupplementalTranslation("Receive or send subscriptions with another device", "從另一台裝置接收或傳送訂閱", "別の端末と購読を送受信", "Recevoir ou envoyer des abonnements avec un autre appareil", "Abos mit einem anderen Gerät empfangen oder senden"),
    "还没有订阅" to SupplementalTranslation("No subscriptions yet", "還沒有訂閱", "購読はまだありません", "Aucun abonnement", "Noch keine Abos"),
    "添加订阅链接、文件或二维码后，即可在连接页选择出口并连接。" to SupplementalTranslation("Add a subscription link, file or QR code, then choose an exit on the Connect tab and connect.", "新增訂閱連結、檔案或 QR 碼後，即可在連線頁選擇出口並連線。", "購読リンク、ファイル、QR コードを追加すると、接続タブで出口を選んで接続できます。", "Ajoutez un lien, un fichier ou un code QR d’abonnement, puis choisissez une sortie dans l’onglet Connexion et connectez-vous.", "Füge einen Abo-Link, eine Datei oder einen QR-Code hinzu, wähle dann im Tab „Verbindung“ einen Ausgang und verbinde dich."),
    "已停用" to SupplementalTranslation("Disabled", "已停用", "無効", "Désactivé", "Deaktiviert"),
    "重试" to SupplementalTranslation("Retry", "重試", "再試行", "Réessayer", "Erneut versuchen"),

    // Subscription library: search, source filter, per-source update, failed-only retry, expiry
    "搜索订阅名称" to SupplementalTranslation("Search subscription names", "搜尋訂閱名稱", "購読名を検索", "Rechercher un abonnement", "Abo-Namen suchen"),
    "清除搜索" to SupplementalTranslation("Clear search", "清除搜尋", "検索をクリア", "Effacer la recherche", "Suche leeren"),
    "全部来源" to SupplementalTranslation("All sources", "全部來源", "すべてのソース", "Toutes les sources", "Alle Quellen"),
    "远程来源" to SupplementalTranslation("Remote", "遠端來源", "リモート", "Distantes", "Remote"),
    "本地来源" to SupplementalTranslation("Local", "本機來源", "ローカル", "Locales", "Lokal"),
    "没有匹配的订阅" to SupplementalTranslation("No matching subscriptions", "沒有符合的訂閱", "一致する購読はありません", "Aucun abonnement correspondant", "Keine passenden Abos"),
    "试试其他名称，或清除搜索和筛选" to SupplementalTranslation("Try another name, or clear the search and filters", "試試其他名稱，或清除搜尋和篩選", "別の名前を試すか、検索と絞り込みをクリアしてください", "Essayez un autre nom ou effacez la recherche et les filtres", "Anderen Namen versuchen oder Suche und Filter zurücksetzen"),
    "清除搜索和筛选" to SupplementalTranslation("Clear search and filters", "清除搜尋和篩選", "検索と絞り込みをクリア", "Effacer recherche et filtres", "Suche und Filter zurücksetzen"),
    "更新订阅" to SupplementalTranslation("Update subscription", "更新訂閱", "購読を更新", "Mettre à jour l’abonnement", "Abo aktualisieren"),
    "更新失败" to SupplementalTranslation("Update failed", "更新失敗", "更新に失敗", "Échec de la mise à jour", "Aktualisierung fehlgeschlagen"),
    "已更新" to SupplementalTranslation("Updated", "已更新", "更新済み", "Mis à jour", "Aktualisiert"),
    "以下订阅更新失败" to SupplementalTranslation("These subscriptions failed to update", "以下訂閱更新失敗", "次の購読を更新できませんでした", "Ces abonnements n’ont pas pu être mis à jour", "Diese Abos konnten nicht aktualisiert werden"),
    "仅重试失败项" to SupplementalTranslation("Retry failed only", "僅重試失敗項目", "失敗分のみ再試行", "Réessayer les échecs", "Nur Fehlgeschlagene wiederholen"),
    "即将到期" to SupplementalTranslation("Expiring soon", "即將到期", "まもなく期限", "Expire bientôt", "Läuft bald ab"),

    // UI refinement: fixed-size connection hero and shorter copy. The hero's supporting lines
    // must stay short enough for a single line at the narrowest supported width.
    "准备就绪" to SupplementalTranslation("Ready", "準備就緒", "準備完了", "Prêt", "Bereit"),
    "正在建立隧道" to SupplementalTranslation("Setting up the tunnel", "正在建立隧道", "トンネルを準備中", "Mise en place du tunnel", "Tunnel wird aufgebaut"),
    // Plain fact for "tunnel up, no reachability evidence": not a success claim, not a failure and
    // not a promise that a check is running. Keep these free of "verified"/"secure"/"available".
    "连通性未检测" to SupplementalTranslation("Internet access not checked", "連通性未檢測", "ネット通信は未確認", "Accès Internet non testé", "Internetzugang ungeprüft"),
    "出口已失效" to SupplementalTranslation("Exit no longer valid", "出口已失效", "出口が無効です", "Sortie invalide", "Ausgang ungültig"),
    "请检查出口或网络" to SupplementalTranslation("Check the exit or network", "請檢查出口或網路", "出口とネットワークを確認", "Vérifiez la sortie ou le réseau", "Ausgang oder Netzwerk prüfen"),
    "实时数据" to SupplementalTranslation("Live data", "即時資料", "リアルタイムデータ", "Données en direct", "Live-Daten"),
    "未开启" to SupplementalTranslation("Off", "未開啟", "オフ", "Désactivé", "Aus"),

    // UI refinement: settings and subscriptions copy that kept only its essential clause.
    "需同时开启 Always-on 与阻止无 VPN 连接" to SupplementalTranslation("Turn on both Always-on and “Block connections without VPN”", "需同時開啟 Always-on 與「封鎖無 VPN 的連線」", "Always-on と「VPN なしの接続をブロック」の両方をオンにしてください", "Activez à la fois Always-on et le blocage des connexions sans VPN", "Always-on und „Verbindungen ohne VPN blockieren“ beide aktivieren"),
    "选为直连的应用不进入隧道；Always-on 阻断时可能无法联网" to SupplementalTranslation("Apps set to Direct skip the tunnel. With Always-on blocking they may lose connectivity.", "選為直連的應用不進入隧道；Always-on 阻斷時可能無法連網", "ダイレクトに設定したアプリはトンネルを通りません。Always-on のブロック時は接続できない場合があります。", "Les apps en direct évitent le tunnel. Avec le blocage Always-on, elles peuvent perdre la connexion.", "Als direkt gesetzte Apps umgehen den Tunnel. Bei Always-on-Sperre kann die Verbindung ausfallen."),
    "127.0.0.1:7890，本机其他应用也能访问" to SupplementalTranslation("127.0.0.1:7890; other apps on this phone can reach it too", "127.0.0.1:7890；本機其他應用也能存取", "127.0.0.1:7890。この端末の他のアプリもアクセスできます", "127.0.0.1:7890 ; les autres apps du téléphone peuvent aussi y accéder", "127.0.0.1:7890; andere Apps auf dem Telefon erreichen den Port ebenfalls"),
    "仅内存显示，复制默认脱敏" to SupplementalTranslation("Shown in memory only; copies are redacted", "僅在記憶體顯示，複製預設去識別化", "メモリ内でのみ表示。コピーは匿名化されます", "Affichés en mémoire uniquement ; copies expurgées", "Nur im Speicher; Kopien werden bereinigt"),
    "开源许可与第三方组件" to SupplementalTranslation("Open-source licenses and third-party components", "開源授權與第三方元件", "オープンソースライセンスとサードパーティコンポーネント", "Licences open source et composants tiers", "Open-Source-Lizenzen und Drittkomponenten"),
    "添加订阅链接、文件或二维码" to SupplementalTranslation("Add a subscription link, file or QR code", "新增訂閱連結、檔案或 QR Code", "購読リンク、ファイル、QR コードを追加", "Ajoutez un lien, un fichier ou un QR code d’abonnement", "Abo-Link, Datei oder QR-Code hinzufügen"),
    "订阅地址仅在本机加密保存" to SupplementalTranslation("Subscription URLs are stored encrypted on this device only", "訂閱位址僅在本機加密保存", "購読 URL はこの端末内で暗号化して保存されます", "Les URL d’abonnement sont chiffrées et stockées sur cet appareil uniquement", "Abo-URLs werden nur verschlüsselt auf diesem Gerät gespeichert"),
)

private data class CountTemplate(val pattern: String, val translation: SupplementalTranslation)

/** `%d` in each translation is replaced by the captured number. */
private val COUNT_TEMPLATES = listOf(
    CountTemplate("^检测到 (\\d+) 个兼容客户端$", SupplementalTranslation("%d compatible clients detected", "偵測到 %d 個相容用戶端", "互換クライアントを %d 個検出", "%d clients compatibles détectés", "%d kompatible Clients erkannt")),
    CountTemplate("^每 (\\d+) 小时$", SupplementalTranslation("Every %d h", "每 %d 小時", "%d 時間ごと", "Toutes les %d h", "Alle %d Std.")),
    CountTemplate("^(\\d+) 分钟前更新$", SupplementalTranslation("Updated %d min ago", "%d 分鐘前更新", "%d 分前に更新", "Mis à jour il y a %d min", "Vor %d Min. aktualisiert")),
    CountTemplate("^(\\d+) 小时前更新$", SupplementalTranslation("Updated %d h ago", "%d 小時前更新", "%d 時間前に更新", "Mis à jour il y a %d h", "Vor %d Std. aktualisiert")),
    CountTemplate("^(\\d+) 天前更新$", SupplementalTranslation("Updated %d d ago", "%d 天前更新", "%d 日前に更新", "Mis à jour il y a %d j", "Vor %d T. aktualisiert")),
    CountTemplate("^(\\d+) 小时$", SupplementalTranslation("%d h", "%d 小時", "%d 時間", "%d h", "%d Std.")),
    CountTemplate("^(\\d+) 条$", SupplementalTranslation("%d entries", "%d 條", "%d 件", "%d entrées", "%d Einträge")),
    CountTemplate("^(\\d+) 个订阅$", SupplementalTranslation("%d subscription(s)", "%d 個訂閱", "購読 %d 件", "%d abonnement(s)", "%d Abonnement(s)")),
    // Two numbers: `%d` is the first capture and `%t` the second.
    CountTemplate("^显示 (\\d+) / (\\d+) 个订阅$", SupplementalTranslation("Showing %d of %t subscription(s)", "顯示 %d / %t 個訂閱", "購読 %t 件中 %d 件を表示", "%d sur %t abonnement(s) affiché(s)", "%d von %t Abonnement(s) angezeigt")),
    CountTemplate("^另有 (\\d+) 个$", SupplementalTranslation("+%d more", "另有 %d 個", "ほか %d 件", "+%d autres", "+%d weitere")),
    CountTemplate("^(\\d+) 条应用分流$", SupplementalTranslation("%d app route(s)", "%d 條應用分流", "アプリのルート %d 件", "%d route(s) d’app", "%d App-Route(n)")),
    CountTemplate("^(\\d+) 条本地规则$", SupplementalTranslation("%d local rule(s)", "%d 條本機規則", "ローカルルール %d 件", "%d règle(s) locale(s)", "%d lokale Regel(n)")),
    CountTemplate("^(\\d+) 个规则集$", SupplementalTranslation("%d rule set(s)", "%d 個規則集", "ルールセット %d 件", "%d jeu(x) de règles", "%d Regelsatz/-sätze")),
    CountTemplate("^(\\d+) 个策略组$", SupplementalTranslation("%d group(s)", "%d 個策略組", "グループ %d 件", "%d groupe(s)", "%d Gruppe(n)")),
    CountTemplate("^有 (\\d+) 个规则集更新失败，已保留旧版本$", SupplementalTranslation("%d rule set(s) failed to update; the old versions are kept", "有 %d 個規則集更新失敗，已保留舊版本", "%d 件のルールセットを更新できませんでした。旧版を保持しています", "%d jeu(x) de règles n’ont pas pu être mis à jour ; anciennes versions conservées", "%d Regelsatz/-sätze nicht aktualisiert; alte Versionen bleiben")),
    CountTemplate("^规则集条目超过 (\\d+) 条上限$", SupplementalTranslation("The rule set exceeds the %d-entry limit", "規則集條目超過 %d 條上限", "ルールセットが上限の %d 件を超えています", "Le jeu dépasse la limite de %d entrées", "Der Regelsatz überschreitet %d Einträge")),
    CountTemplate("^所有规则集合计超过 (\\d+) 条上限$", SupplementalTranslation("All rule sets together exceed the %d-entry limit", "所有規則集合計超過 %d 條上限", "全ルールセットの合計が上限の %d 件を超えています", "L’ensemble des jeux dépasse %d entrées", "Alle Regelsätze zusammen überschreiten %d Einträge")),
    CountTemplate("^最多添加 (\\d+) 个规则集$", SupplementalTranslation("You can add up to %d rule sets", "最多新增 %d 個規則集", "ルールセットは最大 %d 件です", "Jusqu’à %d jeux de règles", "Höchstens %d Regelsätze")),
    CountTemplate("^每个策略组最多 (\\d+) 个节点$", SupplementalTranslation("Each group can have up to %d nodes", "每個策略組最多 %d 個節點", "各グループのノードは最大 %d 件です", "Jusqu’à %d nœuds par groupe", "Höchstens %d Knoten pro Gruppe")),
    CountTemplate("^最多保存 (\\d+) 个策略组$", SupplementalTranslation("You can save up to %d groups", "最多儲存 %d 個策略組", "グループは最大 %d 件です", "Jusqu’à %d groupes", "Höchstens %d Gruppen")),
)

private val COUNT_REGEXES = COUNT_TEMPLATES.map { Regex(it.pattern) to it.translation }

internal fun translateV2Patterns(text: String, language: WeaveLanguage): String? {
    if (language == WeaveLanguage.SIMPLIFIED_CHINESE) return null
    COUNT_REGEXES.forEach { (regex, translation) ->
        regex.matchEntire(text)?.let { match ->
            return translation.resolve(language)
                .replace("%d", match.groupValues[1])
                .replace("%t", match.groupValues.getOrElse(2) { "" })
        }
    }
    return null
}
