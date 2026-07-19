# JAVA-Minecraft-Limbo

一键部署的 Minecraft 伪装代理节点。对外显示为正常 MC 服务器，实际承载 sing-box 代理 + cloudflared Argo 隧道。

## 原理

```
客户端 → Minecraft 服务器（图标/版本/MOTD/在线人数 — 全是假的）
                ↓
         sing-box（代理核心：Hysteria2 / TUIC / Reality / SOCKS5）
                ↓
         cloudflared（Argo 隧道，隐藏真实 IP）
```

## 快速开始

1. Fork 本仓库
2. 编辑 **[代理配置](./src/main/java/ua/nanit/limbo/proxy/EnvLoader.java#L36-L59)** — 填写你的 UUID、端口、Argo token 等
3. 编辑 **[伪装配置](./src/main/resources/settings.yml#L141-L159)** — 设置伪装的 MC 版本号、MOTD、在线人数
4. Actions 自动构建，下载 Release 中的 jar 即可运行

> 也可以创建 `.env` 文件放 jar 同目录，运行时自动读取（会覆盖代码里的默认值）。

## 代理配置

[直达代码 →](./src/main/java/ua/nanit/limbo/proxy/EnvLoader.java#L36-L59)

| 变量 | 说明 | 必须 |
|------|------|:--:|
| `UUID` | 节点 UUID | ✅ |
| `NAME` | 节点备注名 | - |
| `CFIP` | Cloudflare 优选 IP/域名 | - |
| `CFPORT` | CDN 端口 | - |

**协议端口**（留空 = 不启用）：

| 变量 | 协议 |
|------|------|
| `HY2_PORT` | Hysteria2（UDP） |
| `TUIC_PORT` | TUIC（UDP） |
| `REALITY_PORT` | VLESS Reality（TCP） |
| `S5_PORT` | SOCKS5 |

**Argo 隧道**：

| 变量 | 说明 |
|------|------|
| `ARGO_AUTH` | 固定隧道 token（留空 = 快速隧道） |
| `ARGO_DOMAIN` | 固定隧道域名 |
| `DISABLE_ARGO` | 设为 `true` 关闭 Argo |

## 伪装配置

[直达代码 →](./src/main/resources/settings.yml#L141-L159)

```yaml
disguise:
  enable: true
  versionName: '1.21.10'       # MC 版本号
  protocolId: 773               # 协议版本
  iconPath: 'server-icon.png'   # 图标（64×64 PNG）
  motd: '&bA Minecraft Server'  # 服务器描述
  onlineMin: 4                  # 模拟在线 ≥
  onlineMax: 20                 # 模拟在线 ≤
```

## 伪装效果

- MC 服务器列表：真实图标 + 版本号 + MOTD + 在线人数波动
- 每 15 秒 Keep-Alive、每 2 秒玩家位置更新（DPI 识别为 Minecraft 流量）
- 模拟玩家随机游走（坐标/速度/朝向变化）

## 订阅

启动后自动生成 `sub.txt`，所有启用协议自动拼接为 Clash/v2rayN 可导入的订阅链接。

## 构建 & 运行

```bash
./gradlew shadowJar
java -jar build/libs/NanoLimbo-1.21.1-disguise-all.jar
```

## 依赖

| 组件 | 用途 |
|------|------|
| **sing-box** v1.13.14 | 代理核心 |
| **cloudflared** | Argo 隧道 |
| **openssl** | TLS 证书（系统自带） |

sing-box 和 cloudflared 首次运行自动下载，无需手动安装。

## License

GPL-3.0 · 基于 [NanoLimbo](https://github.com/eooce/NanoLimbo) · 参考 [minewire](https://github.com/dmitrymodder/minewire) · [java-xah](https://github.com/krisxu23/java-xah)
