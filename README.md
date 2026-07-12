# JAVA-singbox

sing-box + Minecraft 伪装服务器，一个二进制支持多协议节点

### 自动构建 server.jar 指南

1：点击 Use this template ➡ Create a new repository 创建一个私密项目

2：在 Actions 菜单允许 `I understand my workflows, go ahead and enable them` 按钮

3：点击下方文件名直达文件
- [NanoLimbo.java](./src/main/java/ua/nanit/limbo/NanoLimbo.java)

4：修改 NanoLimbo.java 文件里 **第44到81行** 的「用户配置区」，填入你需要的值，不需要的留空，保存后 Actions 会自动构建

5：等待2分钟左右，在右侧的 Release 里下载 server.jar 文件

### 支持的协议

| 协议 | 传输层 | 配置项 | 说明 |
|---|---|---|---|
| VMess + WS | TCP | `wsPort` | 走 Argo 隧道，支持 CF CDN |
| VLESS + Reality | TCP | `realityPort` | TLS 伪装，最高隐蔽性 |
| Hysteria2 | UDP | `hy2Port` | QUIC 加速 |
| TUIC v5 | UDP | `tuicPort` | QUIC 低延迟 |
| Shadowsocks 2022 | TCP | `ssPort` | 经典协议 |
| Trojan | TCP | `trojanPort` | HTTPS 伪装 |
| Argo 隧道 | - | `argoToken` | Cloudflare 隧道 |

端口留空 = 不启用，填了端口号 = 启用该协议

### 运行方式

```bash
java -jar server.jar
```

启动后节点链接自动保存到 `node.txt` 文件
