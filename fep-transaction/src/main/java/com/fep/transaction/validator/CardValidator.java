package com.fep.transaction.validator;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.exception.TransactionException;

import java.time.LocalDateTime;

/**
 * Validates card-related information in transaction requests.
 */
public class CardValidator implements TransactionValidator {

    @Override
    public void validate(TransactionRequest request) {
        validatePan(request.getPan());
        validateExpirationDate(request.getExpirationDate());
    }

    /**
     * Validates the PAN using Luhn algorithm.
     */
    private void validatePan(String pan) {
        if (pan == null || pan.isBlank()) {
            throw TransactionException.invalidCard("PAN is required");
        }

        if (pan.length() < 13 || pan.length() > 19) {
            throw TransactionException.invalidCard("Invalid PAN length");
        }

        if (!pan.matches("\\d+")) {
            throw TransactionException.invalidCard("PAN must contain only digits");
        }

        if (!isValidLuhn(pan)) {
            throw TransactionException.invalidCard("Invalid PAN checksum");
        }
    }

    /**
     * Validates card expiration date.
     */
    private void validateExpirationDate(String expDate) {
        if (expDate == null || expDate.length() != 4) {
            // Expiration date is optional for some transactions
            return;
        }

        try {
            int year = Integer.parseInt(expDate.substring(0, 2));
            int month = Integer.parseInt(expDate.substring(2, 4));

            if (month < 1 || month > 12) {
                throw TransactionException.invalidCard("Invalid expiration month");
            }

            // Check if card is expired (YYMM format)
            LocalDateTime now = LocalDateTime.now();
            int currentYear = now.getYear() % 100;
            int currentMonth = now.getMonthValue();

            // Card is valid through the end of expiration month
            if (year < currentYear || (year == currentYear && month < currentMonth)) {
                throw TransactionException.expiredCard();
            }

        } catch (NumberFormatException e) {
            throw TransactionException.invalidCard("Invalid expiration date format");
        }
    }

    /**
     * Validates a number using the Luhn algorithm.
     *
     * @param number the number to validate
     * @return true if valid
     */
    private boolean isValidLuhn(String number) {
        int sum = 0;
        boolean alternate = false;

        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(number.charAt(i));

            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }

            sum += digit;
            alternate = !alternate;
        }

        return sum % 10 == 0;
    }

    @Override
    public int getOrder() {
        return 10; // Run early
    }
}
