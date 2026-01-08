#!/bin/bash
#
# FEP System 部署腳本
# 版本: 1.0.1
# 用途: 自動化部署 FEP 系統至 Kubernetes
#

set -e

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
VERSION="${VERSION:-1.0.1}"
NAMESPACE="${NAMESPACE:-fep-prod}"
REGISTRY="${REGISTRY:-harbor.bank.local/fep}"
TIMEOUT="${TIMEOUT:-300}"

# 函數: 顯示訊息
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 函數: 顯示使用說明
usage() {
    echo "Usage: $0 [command] [options]"
    echo ""
    echo "Commands:"
    echo "  build       建置 Docker 映像"
    echo "  push        推送映像至 Registry"
    echo "  deploy      部署至 Kubernetes"
    echo "  rollback    回滾至前一版本"
    echo "  status      檢查部署狀態"
    echo "  health      執行健康檢查"
    echo "  all         執行完整部署流程"
    echo ""
    echo "Options:"
    echo "  -v VERSION  指定版本 (預設: $VERSION)"
    echo "  -n NAMESPACE 指定命名空間 (預設: $NAMESPACE)"
    echo "  -h          顯示說明"
    echo ""
    echo "Example:"
    echo "  $0 all -v 1.0.1"
    echo "  $0 deploy -n fep-staging"
}

# 函數: 建置 Docker 映像
build() {
    log_info "開始建置 FEP System v${VERSION}..."

    # Maven 建置
    log_info "執行 Maven 建置..."
    mvn clean package -DskipTests -q

    # Docker 建置
    log_info "建置 Docker 映像..."
    docker build -t ${REGISTRY}/fep-system:${VERSION} .
    docker tag ${REGISTRY}/fep-system:${VERSION} ${REGISTRY}/fep-system:latest

    log_success "Docker 映像建置完成: ${REGISTRY}/fep-system:${VERSION}"
}

# 函數: 推送映像
push() {
    log_info "推送映像至 Registry..."

    docker push ${REGISTRY}/fep-system:${VERSION}
    docker push ${REGISTRY}/fep-system:latest

    log_success "映像推送完成"
}

# 函數: 部署至 Kubernetes
deploy() {
    log_info "部署至 Kubernetes (namespace: ${NAMESPACE})..."

    # 確認命名空間存在
    kubectl get namespace ${NAMESPACE} > /dev/null 2>&1 || \
        kubectl create namespace ${NAMESPACE}

    # 套用 ConfigMap
    log_info "套用 ConfigMap..."
    kubectl apply -f k8s/configmap.yaml -n ${NAMESPACE}

    # 套用 Secret
    log_info "套用 Secret..."
    kubectl apply -f k8s/secrets.yaml -n ${NAMESPACE}

    # 套用 Deployment
    log_info "套用 Deployment..."
    kubectl apply -f k8s/deployment.yaml -n ${NAMESPACE}

    # 更新映像版本
    kubectl set image deployment/fep-transaction \
        fep-transaction=${REGISTRY}/fep-system:${VERSION} \
        -n ${NAMESPACE}

    # 套用 Service
    log_info "套用 Service..."
    kubectl apply -f k8s/service.yaml -n ${NAMESPACE}

    # 套用 HPA
    log_info "套用 HPA..."
    kubectl apply -f k8s/hpa.yaml -n ${NAMESPACE}

    # 等待部署完成
    log_info "等待部署完成 (timeout: ${TIMEOUT}s)..."
    kubectl rollout status deployment/fep-transaction \
        -n ${NAMESPACE} --timeout=${TIMEOUT}s

    log_success "部署完成"
}

# 函數: 回滾
rollback() {
    log_warn "開始回滾..."

    kubectl rollout undo deployment/fep-transaction -n ${NAMESPACE}
    kubectl rollout status deployment/fep-transaction -n ${NAMESPACE}

    log_success "回滾完成"
}

# 函數: 檢查狀態
status() {
    log_info "FEP System 部署狀態 (namespace: ${NAMESPACE})"
    echo ""

    echo "=== Deployments ==="
    kubectl get deployments -n ${NAMESPACE}
    echo ""

    echo "=== Pods ==="
    kubectl get pods -n ${NAMESPACE} -o wide
    echo ""

    echo "=== Services ==="
    kubectl get services -n ${NAMESPACE}
    echo ""

    echo "=== HPA ==="
    kubectl get hpa -n ${NAMESPACE}
}

# 函數: 健康檢查
health() {
    log_info "執行健康檢查..."

    # 取得 Service IP
    SERVICE_IP=$(kubectl get service fep-service -n ${NAMESPACE} -o jsonpath='{.spec.clusterIP}')

    # 健康檢查端點
    HEALTH_URL="http://${SERVICE_IP}:8080/actuator/health"

    log_info "檢查端點: ${HEALTH_URL}"

    RESPONSE=$(curl -s ${HEALTH_URL} || echo "FAILED")

    if echo ${RESPONSE} | grep -q '"status":"UP"'; then
        log_success "健康檢查通過"
        echo ${RESPONSE} | jq . 2>/dev/null || echo ${RESPONSE}
        return 0
    else
        log_error "健康檢查失敗"
        echo ${RESPONSE}
        return 1
    fi
}

# 函數: 完整部署流程
all() {
    log_info "=========================================="
    log_info "FEP System 完整部署流程"
    log_info "版本: ${VERSION}"
    log_info "命名空間: ${NAMESPACE}"
    log_info "=========================================="
    echo ""

    # 確認
    read -p "確認要執行部署嗎? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log_warn "部署已取消"
        exit 1
    fi

    START_TIME=$(date +%s)

    # 執行部署步驟
    build
    echo ""
    push
    echo ""
    deploy
    echo ""
    health
    echo ""
    status

    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))

    echo ""
    log_success "=========================================="
    log_success "部署完成!"
    log_success "總耗時: ${DURATION} 秒"
    log_success "=========================================="
}

# 解析參數
while getopts "v:n:h" opt; do
    case ${opt} in
        v)
            VERSION=${OPTARG}
            ;;
        n)
            NAMESPACE=${OPTARG}
            ;;
        h)
            usage
            exit 0
            ;;
        *)
            usage
            exit 1
            ;;
    esac
done

shift $((OPTIND -1))

# 執行命令
COMMAND=${1:-help}

case ${COMMAND} in
    build)
        build
        ;;
    push)
        push
        ;;
    deploy)
        deploy
        ;;
    rollback)
        rollback
        ;;
    status)
        status
        ;;
    health)
        health
        ;;
    all)
        all
        ;;
    *)
        usage
        exit 1
        ;;
esac
