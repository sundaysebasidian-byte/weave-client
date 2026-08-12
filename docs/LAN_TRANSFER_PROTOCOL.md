# Weave LAN Transfer v1

Weave 的局域网互传用于把订阅从用户已控制的一台设备迁移到另一台设备。传输服务器只在
用户明确点击“生成传输码”后启动，默认 5 分钟过期，成功下载一次后立即停止。

## 安全模型

- HTTP 只承载 AES-256-GCM 密文，不承载订阅 URL、节点或凭据明文。
- 32 字节随机密钥只放在 `weave://` 链接的 fragment 中；fragment 不会发送给 HTTP
  服务器，也不会出现在请求日志里。
- HTTP endpoint 使用独立 128-bit 随机 token；接收端只允许连接 RFC1918、IPv4
  link-local 或本机地址。
- 服务器只响应精确的 `GET /v1/<token>`，返回 `Cache-Control: no-store`，拒绝其他方法、
  Host/path 和超长请求。
- 解密前限制密文大小，解密后再次限制订阅数量、字段大小、总 payload 和 UTF-8。
- 二维码和复制链接具有相同权限；界面明确显示倒计时，可手动立即失效。

## 链接

```text
weave://lan/v1/<token>?host=<private-ip>&port=<port>#<base64url-key>
```

`token` 为 32 个小写十六进制字符。`key` 为无 padding 的 base64url 编码 32 字节密钥。

## 密文包

```text
8 bytes  ASCII "WVENC001"
12 bytes AES-GCM nonce
N bytes  ciphertext
16 bytes AES-GCM tag
```

AAD 固定为 UTF-8 `weave-lan-transfer-v1`。

## 明文包

所有整数为 unsigned big-endian。

```text
8 bytes  ASCII "WVLAN001"
u32      subscription count
repeat count:
  u32 + UTF-8 bytes  name
  u32 + UTF-8 bytes  source
  u32 + UTF-8 bytes  subscription payload
```

v1 最多 64 个订阅；单个名称 320 bytes、source 8192 bytes、payload 5 MiB，总明文 20 MiB。
