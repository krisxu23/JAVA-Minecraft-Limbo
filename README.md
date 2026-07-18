# JAVA-Minecraft-Limbo

MC Limbo 代理伪装服务器。核心代理由 sbx (sing-box) 处理，Java 层提供高度逼真的 Minecraft 服务器伪装。

## 工作原理

```
客户端看到的: Minecraft 1.21.10 服务器（有图标、MOTD、在线人数）
实际运行的: sbx (sing-box) 代理 + MC 伪装外壳
```

## 自动构建

1. Fork 或 Use this template 创建私密仓库
2. 启用 Actions
3. 修改 [NanoLimbo.java](./src/main/java/ua/nanit/limbo/NanoLimbo.java) 中的环境变量（[直达第126-146行](./src/main/java/ua/nanit/limbo/NanoLimbo.java#L126-L146)）
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

编辑 [NanoLimbo.java](./src/main/java/ua/nanit/limbo/NanoLimbo.java) 中的环境变量（[直达配置段](./src/main/java/ua/nanit/limbo/NanoLimbo.java#L126-L146)）：

| 变量 | 说明 | 示例 |
|------|------|------|
| `S5_PORT` | SOCKS5 端口 | `1080` |
| `HY2_PORT` | Hysteria2 端口 | `443` |
| `TUIC_PORT` | TUIC 端口 | `8443` |
| `REALITY_PORT` | Reality 端口 | `443` |
| `ANYTLS_PORT` | AnyTLS 端口 | `8443` |
| `ARGO_PORT` | Cloudflare Argo 隧道端口 | `8001` |
| `ARGO_DOMAIN` | Argo 固定隧道域名 | `example.com` |
| `ARGO_AUTH` | Argo 隧道密钥/token | `eyJ...` |
| `CFIP` | 优选域名/IP | `spring.io` |
| `CFPORT` | 优选端口 | `443` |
| `NEZHA_SERVER` | 哪吒面板地址 | `nezha.example.com:8008` |
| `NEZHA_KEY` | 哪吒密钥 | `xxx` |

留空 = 不启用该协议。

## 伪装效果

- 服务器列表显示真实 MC 图标 + 版本号 + MOTD
- 在线人数每30分钟平滑波动
- 模拟玩家随机游走运动
- 每15秒发送 Keep-Alive（原版行为）
- 每1秒发送 Time Update
- DPI 检测：标准 Minecraft 协议流量

## 构建

```bash
./gradlew build
# 输出: build/libs/NanoLimbo-1.21.1-disguise-all.jar
```

## 运行

```bash
java -jar NanoLimbo-1.21.1-disguise-all.jar
```

## License

GPL-3.0 (基于 [NanoLimbo](https://github.com/eooce/NanoLimbo))，伪装技术参考 [minewire](https://github.com/dmitrymodder/minewire)
