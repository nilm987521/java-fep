package com.fep.settlement.file;

import com.fep.settlement.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Parser for FISC settlement files.
 * Handles the standard FISC fixed-width format used in Taiwan interbank transactions.
 */
public class FiscSettlementFileParser implements SettlementFileParser {

    private static final Logger log = LoggerFactory.getLogger(FiscSettlementFileParser.class);

    private static final String DEFAULT_ENCODING = "Big5";
    private static final int HEADER_RECORD_LENGTH = 100;
    private static final int DETAIL_RECORD_LENGTH = 200;
    private static final int TRAILER_RECORD_LENGTH = 100;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SettlementFileType fileType;
    private final Charset charset;

    public FiscSettlementFileParser() {
        this(SettlementFileType.DAILY_SETTLEMENT);
    }

    public FiscSettlementFileParser(SettlementFileType fileType) {
        this.fileType = fileType;
        this.charset = Charset.forName(DEFAULT_ENCODING);
    }

    @Override
    public SettlementFile parse(Path filePath) throws SettlementFileParseException {
        try (InputStream is = Files.newInputStream(filePath)) {
            return parse(is, filePath.getFileName().toString());
        } catch (IOException e) {
            throw new SettlementFileParseException("Failed to read file: " + e.getMessage(),
                    filePath.toString(), -1, e);
        }
    }

    @Override
    public SettlementFile parse(InputStream inputStream, String fileName) throws SettlementFileParseException {
        log.info("Parsing settlement file: {}", fileName);

        SettlementFile settlementFile = SettlementFile.builder()
                .fileId(generateFileId())
                .fileName(fileName)
                .fileType(fileType)
                .source("FISC")
                .receivedAt(LocalDateTime.now())
                .processingStartedAt(LocalDateTime.now())
                .processingStatus(FileProcessingStatus.PARSING)
                .records(new ArrayList<>())
                .build();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charset))) {
            List<String> lines = reader.lines().toList();

            if (lines.isEmpty()) {
                throw new SettlementFileParseException("Empty file", fileName);
            }

            int lineNumber = 0;
            boolean headerParsed = false;
            boolean trailerParsed = false;

            for (String line : lines) {
                lineNumber++;

                if (line.trim().isEmpty()) {
                    continue;
                }

                String recordType = getRecordType(line);

                switch (recordType) {
                    case "H" -> {
                        if (headerParsed) {
                            throw new SettlementFileParseException("Duplicate header found",
                                    fileName, lineNumber);
                        }
                        settlementFile.setHeader(parseHeader(line));
                        headerParsed = true;
                    }
                    case "D" -> {
                        if (!headerParsed) {
                            throw new SettlementFileParseException("Detail record before header",
                                    fileName, lineNumber);
                        }
                        SettlementRecord record = parseRecord(line, lineNumber);
                        record.setSequenceNumber(settlementFile.getTotalRecordCount() + 1);
                        settlementFile.addRecord(record);
                    }
                    case "T" -> {
                        settlementFile.setTrailer(parseTrailer(line));
                        trailerParsed = true;
                    }
                    default -> log.warn("Unknown record type '{}' at line {}", recordType, lineNumber);
                }
            }

            if (!headerParsed) {
                throw new SettlementFileParseException("Missing header record", fileName);
            }
            if (!trailerParsed) {
                throw new SettlementFileParseException("Missing trailer record", fileName);
            }

            // Extract settlement date from header
            if (settlementFile.getHeader() != null &&
                settlementFile.getHeader().getCreationDate() != null) {
                settlementFile.setSettlementDate(settlementFile.getHeader().getCreationDate());
            }

            // Validate file integrity
            if (!settlementFile.validateIntegrity()) {
                log.warn("File integrity check failed for {}", fileName);
                settlementFile.setProcessingStatus(FileProcessingStatus.VALIDATION_FAILED);
            } else {
                settlementFile.setProcessingStatus(FileProcessingStatus.RECONCILING);
            }

            settlementFile.setProcessingCompletedAt(LocalDateTime.now());
            settlementFile.setChecksum(calculateChecksum(lines));

            log.info("Parsed {} records from file {}", settlementFile.getTotalRecordCount(), fileName);

            return settlementFile;

        } catch (IOException e) {
            throw new SettlementFileParseException("Error reading file: " + e.getMessage(),
                    fileName, -1, e);
        }
    }

    @Override
    public SettlementRecord parseRecord(String line, int lineNumber) throws SettlementFileParseException {
        try {
            // FISC settlement record format (fixed width):
            // Positions are 1-based for readability
            // 1: Record type (1)
            // 2-9: Transaction date (8) YYYYMMDD
            // 10-21: Transaction reference (12)
            // 22-27: STAN (6)
            // 28-39: RRN (12)
            // 40-43: Transaction type (4)
            // 44-50: Acquiring bank code (7)
            // 51-57: Issuing bank code (7)
            // 58-73: Card number (16)
            // 74-85: Amount (12) - 2 decimal places implied
            // 86-88: Currency code (3)
            // 89-100: Fee amount (12) - 2 decimal places implied
            // 101-108: Terminal ID (8)
            // 109-123: Merchant ID (15)
            // 124-129: Auth code (6)
            // 130-131: Response code (2)
            // 132: Reversal flag (1) Y/N
            // 133-144: Original transaction ref (12)
            // 145-150: Channel (6)

            String recordType = safeSubstring(line, 0, 1);
            if (!"D".equals(recordType)) {
                throw new SettlementFileParseException("Invalid record type for detail: " + recordType,
                        null, lineNumber);
            }

            String txnDateStr = safeSubstring(line, 1, 9);
            String txnRefNo = safeSubstring(line, 9, 21).trim();
            String stan = safeSubstring(line, 21, 27).trim();
            String rrn = safeSubstring(line, 27, 39).trim();
            String txnType = safeSubstring(line, 39, 43).trim();
            String acquiringBank = safeSubstring(line, 43, 50).trim();
            String issuingBank = safeSubstring(line, 50, 57).trim();
            String cardNumber = safeSubstring(line, 57, 73).trim();
            String amountStr = safeSubstring(line, 73, 85).trim();
            String currency = safeSubstring(line, 85, 88).trim();
            String feeStr = safeSubstring(line, 88, 100).trim();
            String terminalId = safeSubstring(line, 100, 108).trim();
            String merchantId = safeSubstring(line, 108, 123).trim();
            String authCode = safeSubstring(line, 123, 129).trim();
            String responseCode = safeSubstring(line, 129, 131).trim();
            String reversalFlag = safeSubstring(line, 131, 132).trim();
            String origTxnRef = safeSubstring(line, 132, 144).trim();
            String channel = safeSubstring(line, 144, 150).trim();

            LocalDateTime txnDateTime = null;
            if (!txnDateStr.isBlank()) {
                try {
                    LocalDate txnDate = LocalDate.parse(txnDateStr, DATE_FORMAT);
                    txnDateTime = txnDate.atStartOfDay();
                } catch (Exception e) {
                    log.warn("Failed to parse date '{}' at line {}", txnDateStr, lineNumber);
                }
            }

            BigDecimal amount = parseAmount(amountStr);
            BigDecimal fee = parseAmount(feeStr);
            boolean isReversal = "Y".equalsIgnoreCase(reversalFlag);

            SettlementRecord record = SettlementRecord.builder()
                    .transactionRefNo(txnRefNo)
                    .stan(stan)
                    .rrn(rrn)
                    .transactionType(txnType)
                    .acquiringBankCode(acquiringBank)
                    .issuingBankCode(issuingBank)
                    .cardNumber(cardNumber)
                    .amount(amount)
                    .currencyCode(currency.isEmpty() ? "TWD" : currency)
                    .feeAmount(fee)
                    .terminalId(terminalId)
                    .merchantId(merchantId)
                    .authCode(authCode)
                    .responseCode(responseCode)
                    .reversal(isReversal)
                    .originalTransactionRef(origTxnRef)
                    .channel(channel)
                    .transactionDateTime(txnDateTime)
                    .status(SettlementStatus.PENDING)
                    .rawData(line)
                    .build();

            record.setNetAmount(record.calculateNetAmount());

            return record;

        } catch (Exception e) {
            throw new SettlementFileParseException("Error parsing record: " + e.getMessage(),
                    null, lineNumber, e);
        }
    }

    @Override
    public SettlementFile.FileHeader parseHeader(String headerLine) throws SettlementFileParseException {
        try {
            // Header format:
            // 1: Record type (1) = 'H'
            // 2-9: File ID (8)
            // 10-12: Version (3)
            // 13-20: Creation date (8) YYYYMMDD
            // 21-27: Creating bank (7)
            // 28-34: Receiving bank (7)
            // 35-36: File type code (2)

            String recordType = safeSubstring(headerLine, 0, 1);
            if (!"H".equals(recordType)) {
                throw new SettlementFileParseException("Invalid header record type: " + recordType);
            }

            String fileId = safeSubstring(headerLine, 1, 9).trim();
            String version = safeSubstring(headerLine, 9, 12).trim();
            String creationDateStr = safeSubstring(headerLine, 12, 20).trim();
            String creatingBank = safeSubstring(headerLine, 20, 27).trim();
            String receivingBank = safeSubstring(headerLine, 27, 34).trim();
            String fileTypeCode = safeSubstring(headerLine, 34, 36).trim();

            LocalDate creationDate = null;
            if (!creationDateStr.isBlank()) {
                try {
                    creationDate = LocalDate.parse(creationDateStr, DATE_FORMAT);
                } catch (Exception e) {
                    log.warn("Failed to parse header date: {}", creationDateStr);
                }
            }

            return SettlementFile.FileHeader.builder()
                    .fileId(fileId)
                    .version(version)
                    .creationDate(creationDate)
                    .creatingBank(creatingBank)
                    .receivingBank(receivingBank)
                    .fileType(fileTypeCode)
                    .rawData(headerLine)
                    .build();

        } catch (Exception e) {
            throw new SettlementFileParseException("Error parsing header: " + e.getMessage(), e);
        }
    }

    @Override
    public SettlementFile.FileTrailer parseTrailer(String trailerLine) throws SettlementFileParseException {
        try {
            // Trailer format:
            // 1: Record type (1) = 'T'
            // 2-9: Record count (8)
            // 10-25: Total amount (16) - 2 decimal places implied
            // 26-41: Total debit amount (16)
            // 42-57: Total credit amount (16)
            // 58-65: Debit count (8)
            // 66-73: Credit count (8)
            // 74-105: Checksum (32)

            String recordType = safeSubstring(trailerLine, 0, 1);
            if (!"T".equals(recordType)) {
                throw new SettlementFileParseException("Invalid trailer record type: " + recordType);
            }

            String recordCountStr = safeSubstring(trailerLine, 1, 9).trim();
            String totalAmountStr = safeSubstring(trailerLine, 9, 25).trim();
            String totalDebitStr = safeSubstring(trailerLine, 25, 41).trim();
            String totalCreditStr = safeSubstring(trailerLine, 41, 57).trim();
            String debitCountStr = safeSubstring(trailerLine, 57, 65).trim();
            String creditCountStr = safeSubstring(trailerLine, 65, 73).trim();
            String checksum = safeSubstring(trailerLine, 73, 105).trim();

            return SettlementFile.FileTrailer.builder()
                    .recordCount(parseInteger(recordCountStr))
                    .totalAmount(parseAmount(totalAmountStr))
                    .totalDebitAmount(parseAmount(totalDebitStr))
                    .totalCreditAmount(parseAmount(totalCreditStr))
                    .debitCount(parseInteger(debitCountStr))
                    .creditCount(parseInteger(creditCountStr))
                    .checksum(checksum)
                    .rawData(trailerLine)
                    .build();

        } catch (Exception e) {
            throw new SettlementFileParseException("Error parsing trailer: " + e.getMessage(), e);
        }
    }

    @Override
    public SettlementFileType getFileType() {
        return fileType;
    }

    @Override
    public FileValidationResult validateFormat(Path filePath) {
        FileValidationResult result = new FileValidationResult();
        result.setValid(true);

        try {
            if (!Files.exists(filePath)) {
                return FileValidationResult.failure("File does not exist: " + filePath);
            }

            result.setFileSizeBytes(Files.size(filePath));

            if (result.getFileSizeBytes() == 0) {
                return FileValidationResult.failure("File is empty");
            }

            List<String> lines = Files.readAllLines(filePath, charset);
            result.setLineCount(lines.size());

            if (lines.isEmpty()) {
                return FileValidationResult.failure("File has no readable lines");
            }

            // Check header
            String firstLine = lines.get(0);
            if (!firstLine.startsWith("H")) {
                result.addError("File does not start with header record", 1);
            }

            // Check trailer
            String lastLine = lines.get(lines.size() - 1);
            if (!lastLine.startsWith("T")) {
                result.addError("File does not end with trailer record", lines.size());
            }

            // Check for detail records
            boolean hasDetails = lines.stream()
                    .skip(1)
                    .limit(lines.size() - 2)
                    .anyMatch(l -> l.startsWith("D"));

            if (!hasDetails) {
                result.addWarning("No detail records found in file");
            }

            result.setDetectedEncoding(DEFAULT_ENCODING);
            result.setDetectedFileType(fileType.getCode());

        } catch (IOException e) {
            return FileValidationResult.failure("Error reading file: " + e.getMessage());
        }

        return result;
    }

    @Override
    public String getEncoding() {
        return DEFAULT_ENCODING;
    }

    @Override
    public int getExpectedRecordLength() {
        return DETAIL_RECORD_LENGTH;
    }

    // Helper methods

    private String getRecordType(String line) {
        if (line == null || line.isEmpty()) {
            return "";
        }
        return line.substring(0, 1);
    }

    private String safeSubstring(String str, int start, int end) {
        if (str == null) {
            return "";
        }
        int actualEnd = Math.min(end, str.length());
        if (start >= actualEnd) {
            return "";
        }
        return str.substring(start, actualEnd);
    }

    private BigDecimal parseAmount(String amountStr) {
        if (amountStr == null || amountStr.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            // Amount is stored as cents (implied 2 decimal places)
            long cents = Long.parseLong(amountStr.trim());
            return BigDecimal.valueOf(cents, 2);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse amount: {}", amountStr);
            return BigDecimal.ZERO;
        }
    }

    private int parseInteger(String str) {
        if (str == null || str.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String generateFileId() {
        return "SF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String calculateChecksum(List<String> lines) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String line : lines) {
                md.update(line.getBytes(charset));
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
