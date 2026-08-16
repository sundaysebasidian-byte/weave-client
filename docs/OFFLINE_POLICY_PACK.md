# Weave 离线策略包

Android 第二阶段支持本地 `weave-policy/v1` JSON。策略包在导入边界校验后，以
Keystore 加密文件保存；启用或停用只会重新编译本地 Mihomo 规则，不会从远端下载规则。

最小格式如下：

```json
{
  "format": "weave-policy/v1",
  "id": "privacy-core",
  "name": "隐私基础",
  "version": 1,
  "description": "本地广告和跟踪器规则",
  "rules": [
    {"type": "domain_suffix", "value": "ads.example.com", "action": "reject"},
    {"type": "process_name", "value": "com.example.browser", "action": "default"}
  ],
  "sha256": "<canonical payload 的 SHA-256 小写十六进制值>"
}
```

`sha256` 覆盖 `format/id/name/version/description/rules` 的确定性 JSON 载荷，不包含
`sha256` 或 `signature` 字段。`signature` 可选，当前只接受 Ed25519：

```json
"signature": {
  "algorithm": "Ed25519",
  "publicKey": "<base64 DER SubjectPublicKeyInfo>",
  "value": "<base64 签名>"
}
```

支持的规则类型为 `domain`、`domain_suffix`、`domain_keyword`、`ip_cidr`、`ip_cidr6`
和 `process_name`；动作是 `default`、`direct`、`reject`。域名、进程名、CIDR、规则数量和
文件大小均有上限，含换行或逗号的值会被拒绝。无签名包仍可由用户明确导入，但界面始终标记为
“无签名·需复核”，不会伪装成受信规则集。
