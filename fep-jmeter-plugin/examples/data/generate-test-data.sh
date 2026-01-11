#!/bin/bash
# Generate test data for high-volume ATM simulation
# Usage: ./generate-test-data.sh [num_atms] [num_cards]

NUM_ATMS=${1:-2000}
NUM_CARDS=${2:-10000}

LOCATIONS=("Taipei" "Taoyuan" "Hsinchu" "Taichung" "Tainan" "Kaohsiung" "Hualien" "Yilan" "Pingtung" "Nantou")
PLACES=("Main Branch" "Station" "Airport" "Shopping Mall" "Hospital" "University" "Industrial Park" "MRT Station" "HSR Station" "Department Store")

# Generate ATM list
echo "Generating $NUM_ATMS ATMs..."
echo "ATM_ID,ATM_LOCATION,BANK_CODE,BRANCH_CODE" > atm-list-large.csv
for i in $(seq 1 $NUM_ATMS); do
    ATM_ID=$(printf "ATM%05d" $i)
    LOC_IDX=$((RANDOM % ${#LOCATIONS[@]}))
    PLACE_IDX=$((RANDOM % ${#PLACES[@]}))
    LOCATION="${LOCATIONS[$LOC_IDX]} ${PLACES[$PLACE_IDX]}"
    BANK_CODE="012"
    BRANCH_CODE=$(printf "%04d" $((i % 1000)))
    echo "$ATM_ID,$LOCATION,$BANK_CODE,$BRANCH_CODE" >> atm-list-large.csv
done
echo "Created atm-list-large.csv"

# Generate Card list
echo "Generating $NUM_CARDS Cards..."
echo "CARD_NUMBER,CARD_EXPIRY,TRACK2_DATA,PIN_BLOCK,ACCOUNT_NUMBER" > card-list-large.csv
for i in $(seq 1 $NUM_CARDS); do
    # Generate card number with Luhn checksum placeholder (simplified)
    PREFIX=$((4000000000000000 + i))
    CARD_NUMBER=$PREFIX

    # Expiry: random between 2025-2030
    YEAR=$((25 + RANDOM % 6))
    MONTH=$(printf "%02d" $((1 + RANDOM % 12)))
    EXPIRY="${YEAR}${MONTH}"

    # Track2
    TRACK2="${CARD_NUMBER}=${EXPIRY}1011234500001"

    # PIN Block (simulated)
    PIN_BLOCK=$(printf "04%02X%04X%04X%04X" $((RANDOM % 256)) $((RANDOM % 65536)) $((RANDOM % 65536)) $((RANDOM % 65536)))

    # Account number
    ACCOUNT=$(printf "%020d" $i)

    echo "$CARD_NUMBER,$EXPIRY,$TRACK2,$PIN_BLOCK,$ACCOUNT" >> card-list-large.csv
done
echo "Created card-list-large.csv"

echo "Done! Files created:"
echo "  - atm-list-large.csv ($NUM_ATMS ATMs)"
echo "  - card-list-large.csv ($NUM_CARDS Cards)"
