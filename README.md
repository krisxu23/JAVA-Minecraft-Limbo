# JAVA-Minecraft-Limbo

MC Limbo 代理伪装服务器。核心代理由 **cloudflared**（Argo 隧道）+ **sing-box**（所有代理协议）处理，Java 层提供高度逼真的 Minecraft 服务器伪装。

## 工作原理

```
客户端看到的: Minecraft 1.21.10 服务器（有图标、MOTD、在线人数）
实际运行的: cloudflared (Argo) + sing-box (代理) + MC 伪装外壳
```

## 自动构建

1. Fork 或 Use this template 创建私密仓库
2. 启用 Actions
3. 修改 [NanoLimbo.java](./src/main/java/ua/nanit/limbo/NanoLimbo.java) 中的环境变量（[直达配置段](./src/main/java/ua/nanit/limbo/NanoLimbo.java#L525-L549)）
4. 修改 [settings.yml](./src/main/resources/settings.yml) 中的伪装配置（[直达disguise段](./src/main/resources/settings.yml#L141-L169)）
5. Actions 自动构建，Release 下载 server.jar

## 伪装配置

编辑 [settings.yml](./src/main/resources/settings.yml) 的 `disguise` 段：

```yaml
disguise:
  enable: true
  versionName: '1.21.10'      # 伪装的MC版本
  protocolId: 773              # 协议版本号
  iconPath: 'server-icon.png'  # 服务器图标（64x64 PNG）
  motd: '&bA Minecraft Server' # 服务器列表描述
  onlineMin: 4                 # 模拟在线人数下限
  onlineMax: 20                # 模拟在线人数上限
  playerSimulation: true       # 玩家运动模拟
  timeUpdates: true            # 时间更新包
  keepAlive: true              # Keep-Alive包
```

## 代理配置

编辑 [NanoLimbo.java](./src/main/java/ua/nanit/limbo/NanoLimbo.java) 中的 `loadEnvVars()` 方法（[直达配置段](./src/main/java/ua/nanit/limbo/NanoLimbo.java#L525-L549)）：

### 通用配置

| 变量 | 说明 | 示例 |
|------|------|------|
| `UUID` | 节点 UUID（所有协议共用） | `fe7431cb-ab1b-4205-a14c-d056f821b383` |
| `NAME` | 节点备注名称 | `sbx` |
| `CFIP` | 优选域名/IP | `spring.io` |
| `CFPORT` | 优选端口 | `443` |

### 协议端口（留空 = 不启用）

| 变量 | 说明 | 示例 |
|------|------|------|
| `S5_PORT` | SOCKS5 端口 | `1080` |
| `HY2_PORT` | Hysteria2 端口（UDP） | `443` |
| `TUIC_PORT` | TUIC 端口（UDP） | `8443` |
| `REALITY_PORT` | VLESS Reality 端口（TCP） | `443` |

### Argo 隧道（cloudflared）

| 变量 | 说明 | 示例 |
|------|------|------|
| `DISABLE_ARGO` | 是否关闭 Argo（`true` 关闭） | `false` |
| `ARGO_PORT` | Argo 转发端口（cloudflared → sing-box） | `8001` |
| `ARGO_DOMAIN` | Argo 固定隧道域名（留空自动获取） | `example.com` |
| `ARGO_AUTH` | Argo 隧道 token（留空 = 快速隧道） | `eyJ...` |
| `CF_VERSION` | cloudflared 版本 | `2025.10.0` |

### Reality 配置（可选，留空自动生成）

| 变量 | 说明 | 示例 |
|------|------|------|
| `REALITY_PRIVATE_KEY` | Reality 私钥（留空自动生成） | `PRIVATE_KEY` |
| `REALITY_SHORT_ID` | Reality Short ID（留空自动生成） | `abcdef12` |

### 哪吒监控（可选）

| 变量 | 说明 | 示例 |
|------|------|------|
| `NEZHA_SERVER` | 哪吒面板地址 | `nezha.example.com:8008` |
| `NEZHA_PORT` | 哪吒 v0 端口（v1 留空） | |
| `NEZHA_KEY` | 哪吒密钥 | `xxx` |

## 订阅链接

启动后自动生成 `sub.txt`，包含所有启用协议的节点链接：

```
vless://UUID@DOMAIN:443?...ws...#NAME-ws-argo      ← Argo VLESS WS
hysteria2://UUID@SERVER:PORT?...#H2-NAME            ← Hysteria2
tuic://UUID:UUID@SERVER:PORT?...#TUIC-NAME          ← TUIC
vless://UUID@SERVER:PORT?...reality&pbk=KEY...#Reality-NAME ← VLESS Reality
socks5://UUID@SERVER:PORT#S5-NAME                   ← SOCKS5
```

同时输出 Base64 订阅格式，可直接导入 Clash/v2rayN。

## 伪装效果

- 服务器列表显示真实 MC 图标 + 版本号 + MOTD
- 在线人数每30分钟平滑波动（min/max 配置）
- 模拟玩家随机游走运动（坐标/地形/速度变化）
- 每15秒发送 Keep-Alive（原版行为）
- 每1秒发送 Time Update
- 每2秒发送玩家位置更新
- DPI 检测：标准 Minecraft 协议流量

## 架构

```
NanoLimbo (Java)
├── MC 伪装层 (minewire 移植)
│   ├── PacketStatusResponse — 伪装状态响应（图标+版本+在线人数）
│   ├── PlayerCountSimulator — 在线人数波动模拟
│   ├── PlayerMotion — 玩家随机运动
│   └── ServerTickSimulator — Keep-Alive + 时间更新
├── cloudflared (Argo 隧道)
│   ├── 快速隧道：自动获取 trycloudflare.com 域名
│   └── 固定隧道：使用 ARGO_AUTH token
└── sing-box (代理核心)
    ├── VLESS WS (Argo 转发, 127.0.0.1:8001)
    ├── Hysteria2 (TLS)
    ├── TUIC (TLS)
    ├── VLESS Reality
    └── SOCKS5
```

## 依赖的外部二进制

| 二进制 | 用途 | 自动下载 |
|--------|------|----------|
| `sing-box` v1.13.14 | 代理核心（Hysteria2/TUIC/Reality/SOCKS5） | ✅ |
| `cloudflared` | Cloudflare Argo 隧道 | ✅ |
| `openssl` | TLS 证书生成 | 系统自带 |

## 构建

```bash
./gradlew shadowJar
# 输出: build/libs/NanoLimbo-1.21.1-disguise-all.jar
```

## 运行

```bash
java -jar NanoLimbo-1.21.1-disguise-all.jar
```

## License

GPL-3.0 (基于 [NanoLimbo](https://github.com/eooce/NanoLimbo))，伪装技术参考 [minewire](https://github.com/dmitrymodder/minewire)，代理方案参考 [java-xah](https://github.com/krisxu23/java-xah)
