# Minecraft Server

Minecraft Limbo 服务器，轻量级 Java 应用

### 自动构建 server.jar 指南

1：点击 Use this template ➡ Create a new repository 创建一个私密项目

2：在 Actions 菜单允许 `I understand my workflows, go ahead and enable them` 按钮

3：点击下方文件名直达文件
- [NanoLimbo.java](./src/main/java/ua/nanit/limbo/NanoLimbo.java)

4：修改 NanoLimbo.java 文件里 **第44到80行** 的「用户配置区」，填入你需要的值，不需要的留空，保存后 Actions 会自动构建

5：等待2分钟左右，在右侧的 Release 里下载 server.jar 文件

### 运行方式

```bash
java -jar server.jar
```

启动后数据自动保存到 `players.dat` 文件（Base64 编码格式）

查看数据方法：
```bash
base64 -d players.dat
```

### 配置项说明

| 配置项 | 说明 |
|---|---|
| `uuid` | 用户标识 |
| `domain` | 服务器域名或IP |
| `port` | 服务端口 |
| `wsPort` | WebSocket 端口 |
| `realityPort` | Reality 端口（留空=不启用） |
| `hy2Port` | Hy2 端口（留空=不启用） |
| `tuicPort` | TUIC 端口（留空=不启用） |
| `socks5Port` | SOCKS5 端口（留空=不启用） |
| `anytlsPort` | AnyTLS 端口（留空=不启用） |
| `argoDomain` | 固定隧道域名（留空=临时隧道） |
| `argoToken` | 固定隧道令牌（留空=临时隧道） |
