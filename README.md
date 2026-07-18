# Minecraft Limbo Proxy Wrapper

A Java application that wraps **sing-box** proxy behind a **Minecraft Limbo server** disguise.
Perfect for deploying proxy nodes on free Java containers (Serv00, CT8, Hostuno, etc.).

## Architecture

`
Client (Minecraft) --> NanoLimbo (Minecraft Protocol) --> sing-box (Proxy Core)
                                      |
                              Cloudflare Argo Tunnel
                                      |
                              WebSocket -> Public Endpoint
`

## Features

- **Full Minecraft Protocol Support**: 1.7.2 ~ 1.21
- **Vanilla Disguise**: MOTD rotation, fake player simulation, proper dimension codec
- **Multi-Protocol Proxy**: VMess+WS, VLESS+Reality, Hysteria2, TUIC, SOCKS5, AnyTLS
- **Cloudflare Argo Tunnel**: Temporary or fixed tunnel support
- **Auto Node Generation**: players.dat with Base64-encoded subscription links
- **Minimal Memory Footprint**: ~100MB Java heap + native binaries

## Configuration

Set environment variables before running:

| Variable | Default | Description |
|----------|---------|-------------|
| UUID | random | Client UUID for proxy auth |
| DOMAIN | auto | Server domain/IP |
| PORT | 25565 | Minecraft listen port |
| NAME | Node | Display name for nodes |
| ARGO_PORT | 8001 | VMess WebSocket port |
| REALITY_PORT | (empty) | VLESS+Reality port |
| HY2_PORT | (empty) | Hysteria2 port |
| TUIC_PORT | (empty) | TUIC port |
| S5_PORT | (empty) | SOCKS5 port |
| ANYTLS_PORT | (empty) | AnyTLS port |
| ARGO_AUTH | (empty) | Cloudflare token |
| ARGO_DOMAIN | (empty) | Fixed tunnel domain |
| DISABLE_ARGO | false | Disable Argo tunnel |
| SB_VERSION | 1.10.0-alpha.7 | Sing-box version |

## Building

`ash
cd minecraft-limbo
./gradlew clean shadowJar
java -XX:MaxRAMPercentage=50 -XX:+UseZGC -jar build/libs/server.jar
`

## Output

- players.dat - Base64-encoded subscription links
- lib/config.json - Sing-box configuration
- lib/native.log - Sing-box connection logs
- lib/ - Downloaded binaries (sing-box, cloudflared)
