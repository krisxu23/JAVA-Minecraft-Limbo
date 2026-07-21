# JAVA-Minecraft-Limbo

Minecraft 伪装代理节点。对外呈现为正常 Paper 服务器，对内承载 sing-box + cloudflared 代理隧道。

```
客户端 → Minecraft（伪装版本/MOTD/在线人数/Keep-Alive/Time Update）
                ↓
         sing-box（Hysteria2 / TUIC / VLESS Reality / SOCKS5）
                ↓
         cloudflared（Argo 隧道，隐藏真实 IP）
```

## 快速开始

1. 编辑 **[代理变量](./src/main/java/ua/nanit/limbo/proxy/EnvLoader.java#L17-L24)**（默认值仅作示例，建议用 `.env` 覆盖）
2. 编辑 **[伪装配置](./src/main/resources/settings.yml#L141-L169)**
3. Actions 自动构建，下载 Release jar 运行

> 放 `.env` 到 jar 同目录即可覆盖代码中的默认值。

## 代理变量（EnvLoader）

直达：[ALL_ENV_VARS](./src/main/java/ua/nanit/limbo/proxy/EnvLoader.java#L17-L24)

| 变量 | 说明 |
|------|------|
| `UUID` | 节点 UUID |
| `NAME` | 节点名 |
| `CFIP` / `CFPORT` | Cloudflare 优选 IP/端口 |
| `HY2_PORT` / `TUIC_PORT` / `REALITY_PORT` / `S5_PORT` | 协议端口（留空=不启用） |
| `ARGO_AUTH` / `ARGO_DOMAIN` | 固定隧道 token/域名 |
| `REALITY_PRIVATE_KEY` / `REALITY_SHORT_ID` | Reality 密钥（留空自动生成） |
| `DISABLE_ARGO` | `true` 关闭 Argo |
| `CF_VERSION` | cloudflared 版本（默认 2025.10.0） |

> 完整变量列表见 [EnvLoader.java](./src/main/java/ua/nanit/limbo/proxy/EnvLoader.java#L17-L24)

启动后自动生成 `sub.txt`（Clash / v2rayN 可导入）。

## 伪装配置

直达：[settings.yml disguise 段](./src/main/resources/settings.yml#L141-L169)

- **版本号 + 协议 ID** — 显示为指定 MC 版本
- **MOTD / 图标** — 自定义服务器列表展示
- **在线人数模拟** — 多时间尺度随机波动（5min 基噪 + 30min 波浪 + 60min 峰谷偏移 + 均值回归）
- **Keep-Alive** — 13–17 秒随机间隔（防 DPI 指纹）
- **Time Update** — 每秒发送，模拟真实世界时间
- **出生点随机化** — 每次重启 X(-100~99) Y(300~499) Z(-100~99)
- **假玩家模拟** — 随机移动，产生位置更新和区块坐标变化

## 进程守护

sing-box 和 cloudflared 各运行独立自愈 watchdog。进程异常退出后 3 秒自动重启，正常退出（exit=0）或 JVM 关闭时停止。

## 构建 & 运行

```bash
./gradlew shadowJar
java -jar build/libs/NanoLimbo-1.21.1-disguise-all.jar
```

## 依赖（自动下载）

| 组件 | 用途 |
|------|------|
| **sing-box** v1.13.14 | 代理核心 |
| **cloudflared** | Argo 隧道 |
| **openssl** | TLS 证书（系统自带） |

## License

GPL-3.0 · 基于 [NanoLimbo](https://github.com/eooce/NanoLimbo) · 参考 [minewire](https://github.com/dmitrymodder/minewire)
