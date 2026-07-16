# JAVA-Minecraft-Limbo

一个伪装成 Minecraft Limbo 服务器的代理节点部署工具。

## 原理

在免费 Java 容器（如 Serv00、CT8、Hostuno 等）上运行本程序时，它会：
1. 启动 **Minecraft Limbo 服务器**（NanoLimbo）监听 `25565` 端口
2. 在后台启动 **sing-box** 代理核心，提供 VLESS/VMess/Hysteria2/Tuic/Socks5/AnyTLS
3. 通过 **Cloudflare Argo Tunnel** 将 WebSocket 流量转发
4. 可选启动 **HTTP 伪装博客**，进一步混淆流量

## 快速开始

### 配置变量

所有配置在 `ServerConfig.java` 构造函数的 [**配置区域**](https://github.com/krisxu23/JAVA-Minecraft-Limbo/blob/main/src/main/java/ua/nanit/limbo/net/ServerConfig.java#L63-L96) 直接填写：

```java
// 修改这里即可
this.uuid = "b7766341-a06b-4957-bf2c-4e5b8b3d5e2e"; // 客户端 UUID
this.port = "25565";           // Minecraft 服务器端口
this.wsPort = "8001";          // VMess+WebSocket 端口
this.realityPort = "12345";    // VLESS+Reality 端口
this.argoDomain = "";          // Argo 固定域名（留空用临时隧道）
this.nezhaServer = "";         // 哪吒监控域名
// ... 更多变量见下方表格
```

完整变量列表见[**环境变量配置**](#环境变量配置)。

### 构建并运行

```bash
git clone https://github.com/krisxu23/JAVA-Minecraft-Limbo.git
cd JAVA-Minecraft-Limbo
chmod +x gradlew
./gradlew clean shadowJar

# 方式一：使用自适应启动脚本（推荐）
./start.sh

# 方式二：手动带 JVM 参数运行
java -XX:MaxRAMPercentage=40 -XX:+UseZGC -XX:+ZGenerational \
     -XX:+AlwaysPreTouch -XX:+DisableExplicitGC \
     -jar build/libs/server.jar
```

#### 内存说明

本程序在单进程内同时运行 JVM（Minecraft 伪装）和 sing-box/cloudflared 代理核心（通过 JNA 加载的 native .so），所有内存都在一个进程内共享。

| 组件 | 内存类型 | 典型占用 | 控制方式 |
|------|---------|---------|---------|
| Minecraft 伪装 | Java 堆 | **~8-10MB** | 代码已优化：NBT 懒加载 + 按需编码 |
| JVM 自身 | 堆 + 非堆 | 见下方配置 | `-XX:MaxRAMPercentage` |
| sing-box / cloudflared | native 内存 | **30-80MB** (看流量) | 不受 JVM 参数控制 |

**重点：** sing-box 和 cloudflared 的 native 内存不由 JVM 管理。`MaxRAMPercentage` 设得太高会导致 native 部分内存不足 → 进程被 OOM killer 杀死。

建议根据容器总内存选择：

| 总内存 | 建议 `MaxRAMPercentage` | Java 堆 | 留给 native |
|--------|------------------------|---------|------------|
| 256MB | 35-40% | ~90-102MB | ~154-166MB |
| 512MB | 45-50% | ~230-256MB | ~256-282MB |
| 1GB | 55-60% | ~563-614MB | ~410-461MB |
| 4GB+ | 65-70% | ~2.6-2.8GB | ~1.2-1.4GB |

`start.sh` 脚本自动按此策略计算。也可通过 `JVM_ARGS` 环境变量覆盖：

```bash
JVM_ARGS="-Xmx128M -XX:+UseZGC" ./start.sh
```

## 环境变量配置

> 直接在 `ServerConfig.java` 构造函数中填写，无需设置系统环境变量。
> 代码跳转：[ServerConfig.java → 配置区域](https://github.com/krisxu23/JAVA-Minecraft-Limbo/blob/main/src/main/java/ua/nanit/limbo/net/ServerConfig.java#L63-L96)

| 字段 | 默认值 | 说明 |
|------|--------|------|
| `domain` | 自动获取 | 服务器域名或 IP |
| `port` | `25565` | Minecraft 服务器端口 |
| `uuid` | 自动生成 | 客户端 UUID |
| `realityPort` | | VLESS+Reality 端口（TCP） |
| `hy2Port` | | Hysteria2 端口（UDP） |
| `tuicPort` | | Tuic 端口（UDP） |
| `socks5Port` | | Socks5 端口（TCP） |
| `anytlsPort` | | AnyTLS 端口（TCP） |
| `wsPort` | `8001` | VMess+WebSocket 端口（内部 Argo） |
| `argoToken` | | Argo Tunnel Token |
| `argoDomain` | | Argo 隧道固定域名 |
| `nezhaServer` | | 哪吒监控域名 |
| `nezhaKey` | | 哪吒监控 Key |
| `cfIp` | `www.shopify.com` | Cloudflare 优选 IP |
| `cfPort` | `443` | Cloudflare 优选端口 |
| `webPort` | | HTTP 伪装博客端口 |
| `remarksPrefix` | `xah` | 节点备注前缀 |

## 伪装特性

- ✅ Minecraft Limbo 服务器监听 25565 端口
- ✅ 在线人数模拟：3-20 人每 25 分钟波动 ±3，50 个假玩家池
- ✅ 假玩家列表：服务器列表返回真实 UUID + 随机选取的假玩家名
- ✅ MOTD 动态轮换：每 2-4 分钟从 8 条描述池中随机切换
- ✅ 最大玩家数随机：启动时随机选 50~500
- ✅ HTTP 个人博客伪装（可选）
- ✅ Reality TLS 证书伪装
- ✅ KeepAlive 请求间隔随机化（3-8 分钟），User-Agent 模拟搜索引擎

## 输出文件

运行后会在当前目录生成：
- `players.data` - 节点订阅链接（Base64 编码）
- `lib/` - 运行时依赖目录
- `lib/config.json` - sing-box 配置
- `lib/cert.pem` / `lib/key.pem` - 自签名证书
