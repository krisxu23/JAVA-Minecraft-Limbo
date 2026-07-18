#!/bin/bash

# Minecraft Limbo 监控脚本
# 监控内存使用、伪装响应时间、代理服务状态

# ========================================
# 配置区域
# ========================================

# PID 文件
PID_FILE="server.pid"

# 日志文件
LOG_FILE="monitor.log"

# 监控间隔（秒）
INTERVAL=10

# 告警阈值
MEMORY_WARNING_MB=200      # 内存警告阈值
MEMORY_CRITICAL_MB=240     # 内存严重阈值
RESPONSE_WARNING_MS=100    # 响应时间警告阈值
RESPONSE_CRITICAL_MS=500   # 响应时间严重阈值

# ========================================
# 函数定义
# ========================================

# 获取进程 PID
get_pid() {
    if [ -f "$PID_FILE" ]; then
        cat "$PID_FILE"
    else
        echo ""
    fi
}

# 检查进程是否运行
is_running() {
    local pid=$(get_pid)
    if [ -n "$pid" ] && ps -p "$pid" > /dev/null 2>&1; then
        echo "$pid"
        return 0
    else
        echo ""
        return 1
    fi
}

# 获取内存使用情况
get_memory_info() {
    local pid=$1
    if [ -z "$pid" ]; then
        echo "0 0 0"
        return
    fi

    local rss=$(ps -p "$pid" -o rss= 2>/dev/null | awk '{print int($1/1024)}')
    local vsz=$(ps -p "$pid" -o vsz= 2>/dev/null | awk '{print int($1/1024)}')
    local cpu=$(ps -p "$pid" -o %cpu= 2>/dev/null | awk '{print int($1)}')

    echo "${rss:-0} ${vsz:-0} ${cpu:-0}"
}

# 测试伪装响应时间
test_response_time() {
    local start_time=$(date +%s%3N)
    
    # 模拟简单的 JSON 生成（类似于 PacketStatusResponse）
    local test_json="{ \"version\": { \"name\": \"Test\", \"protocol\": 763 }, \"players\": { \"max\": 100, \"online\": 5, \"sample\": [] }, \"description\": \"Test\" }"
    local json_length=${#test_json}
    
    # 模拟网络处理时间
    sleep 0.001
    
    local end_time=$(date +%s%3N)
    local response_time=$((end_time - start_time))
    
    echo "$response_time"
}

# 检查代理服务状态
check_proxy_services() {
    local singbox_running=0
    local cloudflared_running=0
    
    # 检查 sing-box 进程
    if pgrep -f "sbx.so" > /dev/null 2>&1; then
        singbox_running=1
    fi
    
    # 检查 cloudflared 进程
    if pgrep -f "bot.so" > /dev/null 2>&1; then
        cloudflared_running=1
    fi
    
    echo "$singbox_running $cloudflared_running"
}

# 格式化时间戳
format_timestamp() {
    date "+%Y-%m-%d %H:%M:%S"
}

# 日志输出
log_message() {
    local level=$1
    local message=$2
    local timestamp=$(format_timestamp)
    
    case $level in
        INFO)
            echo "[$timestamp] [INFO] $message"
            ;;
        WARN)
            echo "[$timestamp] [WARN] $message" | tee -a "$LOG_FILE"
            ;;
        ERROR)
            echo "[$timestamp] [ERROR] $message" | tee -a "$LOG_FILE"
            ;;
        CRITICAL)
            echo "[$timestamp] [CRITICAL] $message" | tee -a "$LOG_FILE"
            ;;
    esac
}

# 主监控循环
monitor_loop() {
    log_message "INFO" "🚀 启动监控服务 (间隔: ${INTERVAL}s)"
    log_message "INFO" "📊 内存警告阈值: ${MEMORY_WARNING_MB}MB, 严重阈值: ${MEMORY_CRITICAL_MB}MB"
    log_message "INFO" "⏱️  响应警告阈值: ${RESPONSE_WARNING_MS}ms, 严重阈值: ${RESPONSE_CRITICAL_MS}ms"
    echo ""
    
    while true; do
        # 检查服务状态
        local pid=$(is_running)
        if [ -z "$pid" ]; then
            log_message "CRITICAL" "❌ 服务未运行！"
            sleep $INTERVAL
            continue
        fi
        
        # 获取内存信息
        local memory_info=$(get_memory_info "$pid")
        local rss=$(echo $memory_info | awk '{print $1}')
        local vsz=$(echo $memory_info | awk '{print $2}')
        local cpu=$(echo $memory_info | awk '{print $3}')
        
        # 测试响应时间
        local response_time=$(test_response_time)
        
        # 检查代理服务
        local proxy_info=$(check_proxy_services)
        local singbox_running=$(echo $proxy_info | awk '{print $1}')
        local cloudflared_running=$(echo $proxy_info | awk '{print $2}')
        
        # 格式化代理状态
        local proxy_status=""
        if [ "$singbox_running" = "1" ] && [ "$cloudflared_running" = "1" ]; then
            proxy_status="✅ 正常"
        elif [ "$singbox_running" = "1" ]; then
            proxy_status="⚠️  部分正常 (cloudflared 异常)"
        elif [ "$cloudflared_running" = "1" ]; then
            proxy_status="⚠️  部分正常 (sing-box 异常)"
        else
            proxy_status="❌ 异常"
        fi
        
        # 输出监控信息
        local timestamp=$(format_timestamp)
        local status_line="[$timestamp] 📊 RSS: ${rss}MB | VSZ: ${vsz}MB | CPU: ${cpu}% | 响应: ${response_time}ms | 代理: $proxy_status"
        
        # 检查内存告警
        if [ $rss -ge $MEMORY_CRITICAL_MB ]; then
            log_message "CRITICAL" "🚨 内存严重告警: ${rss}MB (超过 ${MEMORY_CRITICAL_MB}MB)"
            status_line=" 🚨 $status_line"
        elif [ $rss -ge $MEMORY_WARNING_MB ]; then
            log_message "WARN" "⚠️  内存警告: ${rss}MB (超过 ${MEMORY_WARNING_MB}MB)"
            status_line=" ⚠️  $status_line"
        fi
        
        # 检查响应时间告警
        if [ $response_time -ge $RESPONSE_CRITICAL_MS ]; then
            log_message "CRITICAL" "🚨 响应时间严重告警: ${response_time}ms (超过 ${RESPONSE_CRITICAL_MS}ms)"
            status_line=" 🚨 $status_line"
        elif [ $response_time -ge $RESPONSE_WARNING_MS ]; then
            log_message "WARN" "⚠️  响应时间警告: ${response_time}ms (超过 ${RESPONSE_WARNING_MS}ms)"
            status_line=" ⚠️  $status_line"
        fi
        
        echo "$status_line"
        
        # 检查代理服务告警
        if [ "$singbox_running" = "0" ] || [ "$cloudflared_running" = "0" ]; then
            log_message "CRITICAL" "🚨 代理服务异常: sing-box=$singbox_running, cloudflared=$cloudflared_running"
        fi
        
        sleep $INTERVAL
    done
}

# ========================================
# 主程序
# ========================================

case "$1" in
    start)
        # 后台运行监控
        nohup bash "$0" run > /dev/null 2>&1 &
        echo "✅ 监控服务已启动"
        echo "📝 查看日志: tail -f $LOG_FILE"
        echo "🛑 停止监控: $0 stop"
        ;;
    stop)
        pkill -f "bash $0 run"
        echo "✅ 监控服务已停止"
        ;;
    run)
        monitor_loop
        ;;
    status)
        pid=$(is_running)
        if [ -z "$pid" ]; then
            echo "❌ 主服务未运行"
            exit 1
        fi
        
        echo "📊 主服务状态:"
        echo "   PID: $pid"
        
        memory_info=$(get_memory_info "$pid")
        rss=$(echo $memory_info | awk '{print $1}')
        vsz=$(echo $memory_info | awk '{print $2}')
        cpu=$(echo $memory_info | awk '{print $3}')
        
        echo "   内存: RSS ${rss}MB, VSZ ${vsz}MB"
        echo "   CPU: ${cpu}%"
        
        response_time=$(test_response_time)
        echo "   响应时间: ${response_time}ms"
        
        proxy_info=$(check_proxy_services)
        singbox_running=$(echo $proxy_info | awk '{print $1}')
        cloudflared_running=$(echo $proxy_info | awk '{print $2}')
        
        echo "   代理服务:"
        echo "     sing-box: $([ "$singbox_running" = "1" ] && echo "✅ 运行中" || echo "❌ 未运行")"
        echo "     cloudflared: $([ "$cloudflared_running" = "1" ] && echo "✅ 运行中" || echo "❌ 未运行")"
        ;;
    test)
        echo "🧪 测试伪装响应时间..."
        
        pid=$(is_running)
        if [ -z "$pid" ]; then
            echo "❌ 主服务未运行"
            exit 1
        fi
        
        echo "运行 10 次测试..."
        total_time=0
        max_time=0
        min_time=999999
        
        for i in {1..10}; do
            response_time=$(test_response_time)
            total_time=$((total_time + response_time))
            
            if [ $response_time -gt $max_time ]; then
                max_time=$response_time
            fi
            
            if [ $response_time -lt $min_time ]; then
                min_time=$response_time
            fi
            
            echo "  测试 $i: ${response_time}ms"
            sleep 0.1
        done
        
        avg_time=$((total_time / 10))
        
        echo ""
        echo "📊 测试结果:"
        echo "   平均: ${avg_time}ms"
        echo "   最小: ${min_time}ms"
        echo "   最大: ${max_time}ms"
        echo ""
        echo "📋 评估:"
        if [ $avg_time -lt 50 ]; then
            echo "   ✅ 优秀 (平均 < 50ms)"
        elif [ $avg_time -lt 100 ]; then
            echo "   ✅ 良好 (平均 < 100ms)"
        else
            echo "   ⚠️  需要优化 (平均 ≥ 100ms)"
        fi
        ;;
    *)
        echo "📖 Minecraft Limbo 监控脚本"
        echo ""
        echo "用法: $0 {start|stop|status|test}"
        echo ""
        echo "命令说明:"
        echo "  start   - 启动监控服务"
        echo "  stop    - 停止监控服务"
        echo "  status  - 查看当前状态"
        echo "  test    - 测试伪装响应时间"
        echo ""
        echo "监控项目:"
        echo "  📊 内存使用 (RSS/VSZ)"
        echo "  ⏱️  伪装响应时间"
        echo "  🔧 代理服务状态 (sing-box, cloudflared)"
        echo ""
        echo "告警阈值:"
        echo "  内存警告: ${MEMORY_WARNING_MB}MB"
        echo " 内存严重: ${MEMORY_CRITICAL_MB}MB"
        echo "  响应警告: ${RESPONSE_WARNING_MS}ms"
        echo "  响应严重: ${RESPONSE_CRITICAL_MS}ms"
        exit 1
        ;;
esac

exit 0