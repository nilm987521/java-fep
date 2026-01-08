#!/bin/bash
# FEP 系統滲透測試執行腳本
# 用途：執行安全滲透測試並生成報告

echo "=========================================="
echo "FEP 系統 - 滲透測試執行器"
echo "=========================================="
echo ""

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 測試選項
echo "請選擇測試範圍："
echo "1) 完整滲透測試 (所有 25 個測試)"
echo "2) 認證攻擊測試 (4 個測試)"
echo "3) 授權繞過測試 (4 個測試)"
echo "4) 注入攻擊測試 (4 個測試)"
echo "5) 協議層攻擊測試 (4 個測試)"
echo "6) 業務邏輯攻擊測試 (5 個測試)"
echo "7) DoS 防護測試 (4 個測試)"
echo ""
read -p "請輸入選項 [1-7]: " choice

case $choice in
    1)
        echo -e "${GREEN}執行完整滲透測試...${NC}"
        mvn test -pl fep-transaction -Dtest=PenetrationTest -Dgroups=penetration
        ;;
    2)
        echo -e "${GREEN}執行認證攻擊測試...${NC}"
        mvn test -pl fep-transaction -Dtest=PenetrationTest\$AuthenticationAttackTests
        ;;
    3)
        echo -e "${GREEN}執行授權繞過測試...${NC}"
        mvn test -pl fep-transaction -Dtest=PenetrationTest\$AuthorizationBypassTests
        ;;
    4)
        echo -e "${GREEN}執行注入攻擊測試...${NC}"
        mvn test -pl fep-transaction -Dtest=PenetrationTest\$InjectionAttackTests
        ;;
    5)
        echo -e "${GREEN}執行協議層攻擊測試...${NC}"
        mvn test -pl fep-transaction -Dtest=PenetrationTest\$ProtocolAttackTests
        ;;
    6)
        echo -e "${GREEN}執行業務邏輯攻擊測試...${NC}"
        mvn test -pl fep-transaction -Dtest=PenetrationTest\$BusinessLogicAttackTests
        ;;
    7)
        echo -e "${GREEN}執行 DoS 防護測試...${NC}"
        mvn test -pl fep-transaction -Dtest=PenetrationTest\$DosProtectionTests
        ;;
    *)
        echo -e "${RED}無效的選項${NC}"
        exit 1
        ;;
esac

# 檢查測試結果
if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}=========================================="
    echo "測試執行成功！"
    echo -e "==========================================${NC}"
    echo ""
    echo "測試報告位置："
    echo "  - fep-transaction/target/surefire-reports/"
    echo ""
else
    echo ""
    echo -e "${RED}=========================================="
    echo "測試執行失敗！"
    echo -e "==========================================${NC}"
    echo ""
    exit 1
fi
