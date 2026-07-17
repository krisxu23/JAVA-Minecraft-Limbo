#!/bin/bash

# Minecraft Limbo 启动脚本 (内存优化版本)
# 基于稳定性优先、伪装真实性保持的原则设计

# ========================================
# 配置区域
# ========================================

# Java 可执行文件路径
JAVA_CMD="java"

# JAR 文件路径
JAR_FILE="build/libs/server.jar"

# 日志文件
LOG_FILE="nohup.out"

# PID 文件
PID_FILE="server.pid"

# ========================================
# JVM 参数优化配置
# ========================================

# 内存配置
JAVA_OPTS="-Xms128m -Xmx256m"

# GC 配置 (G1GC，适合实时应用)
JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC"
JAVA_OPTS="$JAVA_OPTS -XX:MaxGCPauseMillis=100"
JAVA_OPTS="$JAVA_OPTS -XX:InitiatingHeapOccupancyPercent=45"

# 内存优化配置
JAVA_OPTS="$JAVA_OPTS -XX:+UseStringDeduplication"
JAVA_OPTS="$JAVA_OPTS -XX:+ParallelRefProcEnabled"
JAVA_OPTS="$JAVA_OPTS -XX:ParallelRefProcThreads=2"
JAVA_OPTS="$JAVA_OPTS -XX:MetaspaceSize=96m"
JAVA_OPTS="$JAVA_OPTS -XX:MaxMetaspaceSize=128m"
JAVA_OPTS="$JAVA_OPTS -XX:CompressedClassSpaceSize=64m"

# 稳定性配置
JAVA_OPTS="$JAVA_OPTS -XX:+DisableExplicitGC"
JAVA_OPTS="$JAVA_OPTS -XX:+ExitOnOutOfMemoryError"

# Netty 优化配置
JAVA_OPTS="$JAVA_OPTS -Dio.netty.leakDetection.level=disabled"
JAVA_OPTS="$JAVA_OPTS -Dio.netty.allocator.type=pooled"

# 日志格式优化
JAVA_OPTS="$JAVA_OPTS -Djava.util.logging.SimpleFormatter.format=%1\$tH:%1\$tM:%1\$tS.%1\$tL %4\$s %5\$s%6\$s%n"

# ========================================
# 函数定义
# ========================================

# 检查进程是否运行
is_running() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p "$PID" > /dev/null 2>&1; then
            return 0
        else
            rm -f "$PID_FILE"
            return 1
        fi
    fi
    return 1
}

# 启动服务
start_server() {
    if is_running; then
        echo "❌ 服务已经在运行 (PID: $(cat $PID_FILE))"
        exit 1
    fi

    echo "🚀 启动 Minecraft Limbo 服务..."
    echo "📋 JVM 参数: $JAVA_OPTS"
    echo "📦 JAR 文件: $JAR_FILE"

    # 检查 JAR 文件是否存在
    if [ ! -f "$JAR_FILE" ]; then
        echo "❌ 错误: JAR 文件不存在: $JAR_FILE"
        echo "💡 请先运行: ./gradlew clean shadowJar"
        exit 1
    fi

    # 启动服务
    nohup $JAVA_CMD $JAVA_OPTS -jar "$JAR_FILE" > "$LOG_FILE" 2>&1 &
    PID=$!

    # 保存 PID
    echo $PID > "$PID_FILE"

    # 等待服务启动
    sleep 3

    # 检查是否成功启动
    if is_running; then
        echo "✅ 服务启动成功 (PID: $PID)"
        echo "📊 内存监控: watch -n 5 'ps aux | grep $PID'"
        echo "📝 日志查看: tail -f $LOG_FILE"
        echo "🛑 停止服务: $0 stop"
    else
        echo "❌ 服务启动失败，请检查日志: $LOG_FILE"
        rm -f "$PID_FILE"
        exit 1
    fi
}

# 停止服务
stop_server() {
    if ! is_running; then
        echo "❌ 服务未运行"
        exit 1
    fi

    PID=$(cat "$PID_FILE")
    echo "🛑 停止服务 (PID: $PID)..."

    # 发送 TERM 信号
    kill $PID

    # 等待进程结束
    for i in {1..30}; do
        if ! ps -p $PID > /dev/null 2>&1; then
            echo "✅ 服务已停止"
            rm -f "$PID_FILE"
            exit 0
        fi
        sleep 1
    done

    # 如果进程仍在运行，强制杀死
    echo "⚠️  服务未响应，强制停止..."
    kill -9 $PID
    rm -f "$PID_FILE"
    echo "✅ 服务已强制停止"
}

# 重启服务
restart_server() {
    echo "🔄 重启服务..."
    stop_server
    sleep 2
    start_server
}

# 查看状态
status_server() {
    if is_running; then
        PID=$(cat "$PID_FILE")
        echo "✅ 服务正在运行"
        echo "📊 PID: $PID"
        echo "💾 内存使用: $(ps -p $PID -o rss= | awk '{print $1/1024 "MB"}')"
        echo "⏱️  运行时间: $(ps -p $PID -o etime=)"
        echo "📝 日志文件: $LOG_FILE"
    else
        echo "❌ 服务未运行"
        exit 1
    fi
}

# 查看日志
view_logs() {
    if [ -f "$LOG_FILE" ]; then
        tail -f "$LOG_FILE"
    else
        echo "❌ 日志文件不存在: $LOG_FILE"
        exit 1
    fi
}

# 内存监控
monitor_memory() {
    if ! is_running; then
        echo "❌ 服务未运行"
        exit 1
    fi

    PID=$(cat "$PID_FILE")
    echo "📊 内存监控 (PID: $PID)"
    echo "按 Ctrl+C 退出"
    echo ""

    while true; do
        if ps -p $PID > /dev/null 2>&1; then
            MEMORY=$(ps -p $PID -o rss= | awk '{print $1/1024 "MB"}')
            CPU=$(ps -p $PID -o %cpu=)
            TIME=$(date "+%H:%M:%S")
            echo "[$TIME] 内存: $MEMORY | CPU: $CPU%"
        else
            echo "❌ 服务已停止"
            exit 1
        fi
        sleep 5
    done
}

# 伪装测试
test_camouflage() {
    echo "🎮 测试 Minecraft 伪装..."
    echo "📋 请手动使用 Minecraft 客户端连接 localhost:25565"
    echo "🔍 检查项目："
    echo "   - 服务器列表响应速度（应该 < 1秒）"
    echo "   - 在线人数显示正常（3-20人）"
    echo "   - MOTD 显示正常"
    echo "   - 假玩家列表显示正常"
    echo ""
    echo "📊 当前服务状态："
    status_server
}

# ========================================
# 主程序
# ========================================

case "$1" in
    start)
        start_server
        ;;
    stop)
        stop_server
        ;;
    restart)
        restart_server
        ;;
    status)
        status_server
        ;;
    logs)
        view_logs
        ;;
    monitor)
        monitor_memory
        ;;
    test)
        test_camouflage
        ;;
    *)
        echo "📖 Minecraft Limbo 管理脚本"
        echo ""
        echo "用法: $0 {start|stop|restart|status|logs|monitor|test}"
        echo ""
        echo "命令说明:"
        echo "  start    - 启动服务"
        echo "  stop     - 停止服务"
        echo "  restart  - 重启服务"
        echo "  status   - 查看状态"
        echo "  logs     - 查看日志"
        echo "  monitor  - 内存监控"
        echo "  test     - 伪装测试"
        echo ""
        echo "示例:"
        echo "  $0 start      # 启动服务"
        echo "  $0 monitor    # 监控内存使用"
        echo "  $0 test       # 测试伪装效果"
        exit 1
        ;;
esac

exit 0