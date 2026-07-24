# JAVA-Minecraft-Limbo

Minecraft 伪装代理节点。对外呈现为正常 Paper 服务器，对内承载 sing-box + cloudflared 代理隧道。

```
客户端 → Minecraft（伪装版本/MOTD/在线人数/Keep-Alive/Time Update）
                ↓
         sing-box（Hysteria2 / TUIC / VLESS Reality / SOCKS5）
                ↓
         cloudflared（Argo 隧道，隐藏真实 IP）
```

## 快速开始（自动构建 server.jar）

1. 点击 **Use this template** → **Create a new repository** 创建一个私密项目
2. 在 Actions 选项卡点击 **`I understand my workflows, go ahead and enable them`**
3. ⬇️ 点击下方文件名直达变量填写位置 ⬇️
   - **[`NanoLimbo.java` → 第 78–106 行（在此处填写变量）](./src/main/java/ua/nanit/limbo/NanoLimbo.java)**
4. 修改变量值（不需要的留空 `""`），保存后 Actions 会自动构建
5. 等待 2 分钟左右，在右侧 **Release** 下载 `server.jar`

> ⚠️ **切记**：不要在公开仓库提交真实密钥（`ARGO_AUTH`、`BOT_TOKEN` 等）。  
> 可改用 `.env` 文件（见下方说明）或系统环境变量传递敏感值。

### 变量填写方式

| 方式 | 说明 | 优先级 |
|------|------|--------|
| Java 源码硬编码 | 直接修改 [`NanoLimbo.java`](./src/main/java/ua/nanit/limbo/NanoLimbo.java) 第 78–106 行 | 最低（作为安全默认值） |
| 系统环境变量 | 启动前设置同名环境变量 | 中间 |
| `.env` 文件 | 在 jar 同目录放 `.env` 文件 | **最高**（覆盖前两者） |

**.env 文件示例：**
```env
UUID=你的-节点-UUID
NAME=我的节点
ARGO_AUTH=你的Cloudflare隧道Token或JSON
ARGO_DOMAIN=你的固定隧道域名
HY2_PORT=30093
S5_PORT=30093
DISABLE_ARGO=false
SBX_LIB_SHA256=sbx.so的sha256（可选校验）
```

### 编辑伪装配置

见 [`settings.yml`](./src/main/resources/settings.yml)（运行后也会生成在工作目录）

## 代理变量一览

| 变量 | 说明 |
|------|------|
| `UUID` | 节点 UUID（不填则每次启动随机生成；生产环境建议固定） |
| `NAME` | 节点名 |
| `PORT` | Minecraft 监听端口（默认 25565） |
| `FILE_PATH` | 运行时文件目录（默认 `./world`，含下载的原生库） |
| `CFIP` / `CFPORT` | Cloudflare 优选 IP/端口 |
| `HY2_PORT` / `TUIC_PORT` / `REALITY_PORT` / `S5_PORT` | 协议端口（留空=不启用） |
| `ARGO_AUTH` / `ARGO_DOMAIN` | 固定隧道 token/JSON 与域名（都空则尝试临时隧道） |
| `ARGO_PORT` | Argo 本地入口端口（默认 8001） |
| `REALITY_PRIVATE_KEY` / `REALITY_SHORT_ID` | Reality 密钥（留空且启用 REALITY_PORT 时自动生成） |
| `DISABLE_ARGO` | `true` 关闭 Argo |
| `CF_VERSION` | 历史兼容字段（原生方案下一般可不改） |
| `SBX_LIB_SHA256` / `BOT_LIB_SHA256` | 可选：原生库 SHA-256 校验 |

启动后会在工作目录生成 `sub.txt`（订阅链接，可导入 Clash / v2rayN 等）。

## 伪装配置

见 [`settings.yml`](./src/main/resources/settings.yml) 中的 disguise 相关段落：

- **版本号 + 协议 ID** — 显示为指定 MC 版本
- **MOTD / 图标** — 自定义服务器列表展示
- **在线人数模拟** — 多时间尺度随机波动
- **Keep-Alive** — 随机间隔（降低指纹）
- **Time Update** — 模拟真实世界时间
- **出生点随机化** — 每次重启坐标变化
- **假玩家模拟** — 随机移动

## 原生组件说明

当前方案通过 JNA 加载本机原生库（按架构从配置源下载并缓存到 `FILE_PATH`）：

| 组件 | 用途 |
|------|------|
| **sbx.so** | sing-box 原生入口 |
| **bot.so** | cloudflared 原生入口 |
| **openssl** | 生成 TLS 证书（系统自带，部分协议需要） |

关闭进程时会调用原生 Stop 接口；异常退出后需人工或外部进程管理重启（不再使用子进程 watchdog）。

## 构建 & 运行

```bash
./gradlew shadowJar
# 在 jar 同目录准备好 .env 后：
java -jar build/libs/*-all.jar
```

或直接运行 Release 中的 `server.jar`：

```bash
java -jar server.jar
```

## License

GPL-3.0 · 基于 [NanoLimbo](https://github.com/eooce/NanoLimbo) · 参考 [minewire](https://github.com/dmitrymodder/minewire)
