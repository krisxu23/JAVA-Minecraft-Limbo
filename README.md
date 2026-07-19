# JAVA-Minecraft-Limbo

Minecraft 伪装代理节点。对外呈现为正常 Paper 服务器，对内承载 sing-box 代理 + cloudflared Argo 隧道。

```
客户端 → Minecraft（假图标/版本/MOTD/在线人数/时间/Keep-Alive）
                ↓
         sing-box（Hysteria2 / TUIC / VLESS Reality / SOCKS5）
                ↓
         cloudflared（Argo 隧道，隐藏真实 IP）
```

## 快速开始

1. 编辑 **[代理配置](./src/main/java/ua/nanit/limbo/proxy/EnvLoader.java#L36-L59)**
2. 编辑 **[伪装配置](./src/main/resources/settings.yml#L141-L169)**
3. Actions 自动构建，下载 Release jar 运行

> 也可放 `.env` 到 jar 同目录，会覆盖代码里的默认值。

## 代理配置

直达：[EnvLoader.java](./src/main/java/ua/nanit/limbo/proxy/EnvLoader.java#L36-L59)

| 变量 | 说明 |
|------|------|
| `UUID` | 节点 UUID |
| `NAME` | 节点名 |
| `CFIP` / `CFPORT` | Cloudflare 优选 IP/端口 |
| `HY2_PORT` / `TUIC_PORT` / `REALITY_PORT` / `S5_PORT` | 协议端口（留空=不启用） |
| `ARGO_AUTH` / `ARGO_DOMAIN` | 固定隧道 token/域名 |
| `DISABLE_ARGO` | `true` 关闭 Argo |

启动后自动生成 `sub.txt`（Clash/v2rayN 可导入）。

## 伪装配置

直达：[settings.yml#L141-L169](./src/main/resources/settings.yml#L141-L169)

- 版本号 + 协议 ID — 显示为指定 MC 版本
- MOTD / 图标 — 自定义服务器列表展示
- 在线人数模拟 — 多时间尺度随机波动（5min 基噪 + 30min 波浪 + 60min 峰谷偏移 + 均值回归）
- Keep-Alive — 13–17 秒随机间隔（防 DPI 指纹）
- Time Update — 每秒发送，模拟真实世界时间
- 出生点随机化 — 每次重启 X(-100~99) Y(300~499) Z(-100~99)
- 假玩家模拟 — 随机移动，产生位置更新和区块坐标变化
- 假加载 — ~2 秒快速加载画面

## 进程守护

NanoLimbo 每 30 秒检查 sing-box 和 cloudflared 进程状态，异常崩溃时自动重启（最多 5 次）。

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
