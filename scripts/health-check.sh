#!/bin/bash
#
# FEP System 健康檢查腳本
# 版本: 1.0.1
# 用途: 執行完整系統健康檢查
#

set -e

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 配置
FEP_SERVICE_URL="${FEP_SERVICE_URL:-http://localhost:8080}"
FISC_HOST="${FISC_HOST:-fisc.gateway.tw}"
FISC_PORT="${FISC_PORT:-8583}"

# 計數器
TOTAL_CHECKS=0
PASSED_CHECKS=0
FAILED_CHECKS=0

# 函數: 顯示訊息
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_pass() {
    echo -e "${GREEN}[PASS]${NC} $1"
    ((PASSED_CHECKS++))
    ((TOTAL_CHECKS++))
}

log_fail() {
    echo -e "${RED}[FAIL]${NC} $1"
    ((FAILED_CHECKS++))
    ((TOTAL_CHECKS++))
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

# 函數: 檢查應用程式健康
check_application_health() {
    log_info "檢查應用程式健康狀態..."

    RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" ${FEP_SERVICE_URL}/actuator/health 2>/dev/null || echo "000")

    if [ "$RESPONSE" == "200" ]; then
        log_pass "應用程式健康狀態正常"
    else
        log_fail "應用程式健康狀態異常 (HTTP: $RESPONSE)"
    fi
}

# 函數: 檢查資料庫連線
check_database() {
    log_info "檢查資料庫連線..."

    RESPONSE=$(curl -s ${FEP_SERVICE_URL}/actuator/health/db 2>/dev/null || echo "{}")

    if echo "$RESPONSE" | grep -q '"status":"UP"'; then
        log_pass "資料庫連線正常"
    else
        log_fail "資料庫連線異常"
    fi
}

# 函數: 檢查 Redis 連線
check_redis() {
    log_info "檢查 Redis 連線..."

    RESPONSE=$(curl -s ${FEP_SERVICE_URL}/actuator/health/redis 2>/dev/null || echo "{}")

    if echo "$RESPONSE" | grep -q '"status":"UP"'; then
        log_pass "Redis 連線正常"
    else
        log_warn "Redis 連線異常或未配置"
        ((TOTAL_CHECKS++))
    fi
}

# 函數: 檢查財金連線
check_fisc_connection() {
    log_info "檢查財金公司連線..."

    if nc -z -w5 ${FISC_HOST} ${FISC_PORT} 2>/dev/null; then
        log_pass "財金公司連線正常 (${FISC_HOST}:${FISC_PORT})"
    else
        log_fail "財金公司連線異常"
    fi
}

# 函數: 檢查系統資源
check_system_resources() {
    log_info "檢查系統資源..."

    # CPU 使用率
    CPU_USAGE=$(top -l 1 | grep "CPU usage" | awk '{print $3}' | sed 's/%//' 2>/dev/null || echo "0")
    if [ -z "$CPU_USAGE" ]; then
        CPU_USAGE=$(uptime | awk -F'load average:' '{ print $2 }' | cut -d',' -f1 | tr -d ' ')
    fi
    log_info "CPU 使用率: ${CPU_USAGE}%"

    # 記憶體使用率
    if [[ "$OSTYPE" == "darwin"* ]]; then
        MEM_USAGE=$(vm_stat | perl -ne '/Pages active:\s+(\d+)/ and print $1*4096/1024/1024/1024')
        log_info "記憶體使用: ${MEM_USAGE:.2f} GB"
    else
        MEM_USAGE=$(free | grep Mem | awk '{print $3/$2 * 100.0}')
        log_info "記憶體使用率: ${MEM_USAGE}%"
    fi

    log_pass "系統資源檢查完成"
}

# 函數: 檢查磁碟空間
check_disk_space() {
    log_info "檢查磁碟空間..."

    DISK_USAGE=$(df -h / | awk 'NR==2 {print $5}' | sed 's/%//')

    if [ "$DISK_USAGE" -lt 80 ]; then
        log_pass "磁碟空間正常 (使用率: ${DISK_USAGE}%)"
    elif [ "$DISK_USAGE" -lt 90 ]; then
        log_warn "磁碟空間警告 (使用率: ${DISK_USAGE}%)"
        ((TOTAL_CHECKS++))
    else
        log_fail "磁碟空間不足 (使用率: ${DISK_USAGE}%)"
    fi
}

# 函數: Echo Test
check_echo_test() {
    log_info "執行 Echo Test..."

    RESPONSE=$(curl -s -X POST ${FEP_SERVICE_URL}/api/v1/test/echo 2>/dev/null || echo "{}")

    if echo "$RESPONSE" | grep -q '"success":true'; then
        log_pass "Echo Test 成功"
    else
        log_warn "Echo Test 未執行或失敗"
        ((TOTAL_CHECKS++))
    fi
}

# 函數: 檢查日誌錯誤
check_recent_errors() {
    log_info "檢查最近錯誤..."

    ERROR_COUNT=$(curl -s "${FEP_SERVICE_URL}/actuator/metrics/logback.events?tag=level:error" 2>/dev/null | grep -o '"value":[0-9]*' | cut -d':' -f2 || echo "0")

    if [ -z "$ERROR_COUNT" ] || [ "$ERROR_COUNT" == "0" ]; then
        log_pass "最近無錯誤記錄"
    else
        log_warn "發現 ${ERROR_COUNT} 個錯誤記錄"
        ((TOTAL_CHECKS++))
    fi
}

# 函數: 顯示摘要
show_summary() {
    echo ""
    echo "=========================================="
    echo "健康檢查摘要"
    echo "=========================================="
    echo "總檢查數: ${TOTAL_CHECKS}"
    echo -e "通過: ${GREEN}${PASSED_CHECKS}${NC}"
    echo -e "失敗: ${RED}${FAILED_CHECKS}${NC}"
    echo "=========================================="

    if [ "$FAILED_CHECKS" -eq 0 ]; then
        echo -e "${GREEN}系統狀態: 健康${NC}"
        return 0
    else
        echo -e "${RED}系統狀態: 需要關注${NC}"
        return 1
    fi
}

# 主程式
main() {
    echo "=========================================="
    echo "FEP System 健康檢查"
    echo "時間: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "=========================================="
    echo ""

    check_application_health
    check_database
    check_redis
    check_fisc_connection
    check_system_resources
    check_disk_space
    check_echo_test
    check_recent_errors

    show_summary
}

# 執行
main "$@"
