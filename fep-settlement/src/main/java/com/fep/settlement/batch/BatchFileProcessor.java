package com.fep.settlement.batch;

import com.fep.settlement.clearing.ClearingService;
import com.fep.settlement.domain.*;
import com.fep.settlement.file.FileValidationResult;
import com.fep.settlement.file.FiscSettlementFileParser;
import com.fep.settlement.file.SettlementFileParser;
import com.fep.settlement.reconciliation.ReconciliationConfig;
import com.fep.settlement.reconciliation.ReconciliationResult;
import com.fep.settlement.reconciliation.ReconciliationService;
import com.fep.settlement.repository.SettlementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * Service for processing settlement files in batch.
 */
public class BatchFileProcessor {

    private static final Logger log = LoggerFactory.getLogger(BatchFileProcessor.class);

    private final SettlementRepository repository;
    private final ReconciliationService reconciliationService;
    private final ClearingService clearingService;
    private final Map<SettlementFileType, SettlementFileParser> parsers;
    private final ExecutorService executor;
    private final BatchConfig config;

    public BatchFileProcessor(SettlementRepository repository,
                             ReconciliationService reconciliationService,
                             ClearingService clearingService,
                             BatchConfig config) {
        this.repository = repository;
        this.reconciliationService = reconciliationService;
        this.clearingService = clearingService;
        this.config = config;
        this.executor = Executors.newFixedThreadPool(config.getParallelism());

        // Initialize parsers for each file type
        this.parsers = new EnumMap<>(SettlementFileType.class);
        for (SettlementFileType type : SettlementFileType.values()) {
            parsers.put(type, new FiscSettlementFileParser(type));
        }
    }

    /**
     * Process all files in a directory.
     */
    public BatchResult processDirectory(Path directory) {
        log.info("Processing files in directory: {}", directory);

        BatchResult result = BatchResult.builder()
                .batchId(generateBatchId())
                .startTime(LocalDateTime.now())
                .build();

        try {
            List<Path> files = findSettlementFiles(directory);
            log.info("Found {} settlement files to process", files.size());

            result.setTotalFiles(files.size());

            if (files.isEmpty()) {
                result.complete();
                return result;
            }

            if (config.isParallelProcessing()) {
                processFilesParallel(files, result);
            } else {
                processFilesSequential(files, result);
            }

            result.complete();

        } catch (Exception e) {
            log.error("Batch processing failed: {}", e.getMessage(), e);
            result.fail(e.getMessage());
        }

        return result;
    }

    /**
     * Process a single file.
     */
    public FileProcessResult processFile(Path filePath) {
        log.info("Processing file: {}", filePath);

        FileProcessResult result = FileProcessResult.builder()
                .filePath(filePath.toString())
                .fileName(filePath.getFileName().toString())
                .startTime(LocalDateTime.now())
                .build();

        try {
            // Determine file type
            SettlementFileType fileType = detectFileType(filePath);
            result.setFileType(fileType);

            // Get appropriate parser
            SettlementFileParser parser = parsers.get(fileType);
            if (parser == null) {
                throw new IllegalStateException("No parser available for file type: " + fileType);
            }

            // Validate file format
            FileValidationResult validation = parser.validateFormat(filePath);
            if (!validation.isValid()) {
                result.setStatus(FileProcessStatus.VALIDATION_FAILED);
                result.setErrorMessage("Validation failed: " +
                        validation.getErrors().stream()
                                .map(FileValidationResult.ValidationError::getMessage)
                                .reduce((a, b) -> a + "; " + b)
                                .orElse("Unknown error"));
                return result;
            }

            // Parse file
            SettlementFile settlementFile = parser.parse(filePath);
            result.setRecordCount(settlementFile.getTotalRecordCount());

            // Save file
            repository.saveFile(settlementFile);

            // Reconcile if configured
            if (config.isAutoReconcile()) {
                ReconciliationResult reconcileResult = reconciliationService.reconcile(
                        settlementFile, ReconciliationConfig.defaultConfig()
                );
                result.setMatchedCount(reconcileResult.getMatchedCount());
                result.setDiscrepancyCount(reconcileResult.getDiscrepancyCount());
            }

            // Move to processed directory
            if (config.getMoveToProcessed() != null) {
                moveToProcessed(filePath, config.getMoveToProcessed());
            }

            result.setStatus(FileProcessStatus.SUCCESS);
            result.setSettlementFileId(settlementFile.getFileId());

        } catch (Exception e) {
            log.error("Error processing file {}: {}", filePath, e.getMessage(), e);
            result.setStatus(FileProcessStatus.FAILED);
            result.setErrorMessage(e.getMessage());

            // Move to error directory
            if (config.getMoveToError() != null) {
                try {
                    moveToProcessed(filePath, config.getMoveToError());
                } catch (Exception moveError) {
                    log.error("Failed to move file to error directory: {}", moveError.getMessage());
                }
            }
        }

        result.setEndTime(LocalDateTime.now());
        return result;
    }

    /**
     * Process files sequentially.
     */
    private void processFilesSequential(List<Path> files, BatchResult batchResult) {
        for (Path file : files) {
            FileProcessResult result = processFile(file);
            batchResult.addFileResult(result);

            if (result.getStatus() == FileProcessStatus.SUCCESS) {
                batchResult.incrementSuccess();
            } else {
                batchResult.incrementFailed();
                if (!config.isContinueOnError()) {
                    break;
                }
            }
        }
    }

    /**
     * Process files in parallel.
     */
    private void processFilesParallel(List<Path> files, BatchResult batchResult) {
        List<Future<FileProcessResult>> futures = new ArrayList<>();

        for (Path file : files) {
            futures.add(executor.submit(() -> processFile(file)));
        }

        for (Future<FileProcessResult> future : futures) {
            try {
                FileProcessResult result = future.get(config.getFileTimeoutSeconds(), TimeUnit.SECONDS);
                batchResult.addFileResult(result);

                if (result.getStatus() == FileProcessStatus.SUCCESS) {
                    batchResult.incrementSuccess();
                } else {
                    batchResult.incrementFailed();
                }

            } catch (TimeoutException e) {
                log.error("File processing timed out");
                batchResult.incrementFailed();
            } catch (Exception e) {
                log.error("Error getting file processing result: {}", e.getMessage());
                batchResult.incrementFailed();
            }
        }
    }

    /**
     * Find settlement files in directory.
     */
    private List<Path> findSettlementFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Not a directory: " + directory);
        }

        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(this::isSettlementFile)
                    .sorted()
                    .toList();
        }
    }

    /**
     * Check if file is a settlement file.
     */
    private boolean isSettlementFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        return fileName.endsWith(".txt") ||
               fileName.endsWith(".dat") ||
               fileName.endsWith(".set");
    }

    /**
     * Detect file type from file name or content.
     */
    private SettlementFileType detectFileType(Path filePath) {
        String fileName = filePath.getFileName().toString().toUpperCase();

        // Try to detect from filename prefix
        for (SettlementFileType type : SettlementFileType.values()) {
            if (fileName.startsWith(type.getCode())) {
                return type;
            }
        }

        // Default to daily settlement
        return SettlementFileType.DAILY_SETTLEMENT;
    }

    /**
     * Move file to processed directory.
     */
    private void moveToProcessed(Path file, Path targetDir) throws IOException {
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        Path target = targetDir.resolve(file.getFileName());
        Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
        log.debug("Moved {} to {}", file, target);
    }

    /**
     * Shutdown the processor.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private String generateBatchId() {
        return "BATCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Batch processing configuration.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BatchConfig {
        @lombok.Builder.Default
        private boolean parallelProcessing = false;

        @lombok.Builder.Default
        private int parallelism = 4;

        @lombok.Builder.Default
        private boolean autoReconcile = true;

        @lombok.Builder.Default
        private boolean continueOnError = true;

        @lombok.Builder.Default
        private int fileTimeoutSeconds = 300;

        private Path moveToProcessed;
        private Path moveToError;

        public static BatchConfig defaultConfig() {
            return BatchConfig.builder().build();
        }
    }

    /**
     * Batch processing result.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BatchResult {
        private String batchId;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        @lombok.Builder.Default
        private BatchStatus status = BatchStatus.IN_PROGRESS;
        private int totalFiles;
        private int successCount;
        private int failedCount;
        @lombok.Builder.Default
        private List<FileProcessResult> fileResults = new ArrayList<>();
        private String errorMessage;

        public void addFileResult(FileProcessResult result) {
            if (fileResults == null) {
                fileResults = new ArrayList<>();
            }
            fileResults.add(result);
        }

        public void incrementSuccess() {
            successCount++;
        }

        public void incrementFailed() {
            failedCount++;
        }

        public void complete() {
            this.endTime = LocalDateTime.now();
            this.status = failedCount > 0 ? BatchStatus.COMPLETED_WITH_ERRORS : BatchStatus.COMPLETED;
        }

        public void fail(String errorMessage) {
            this.endTime = LocalDateTime.now();
            this.status = BatchStatus.FAILED;
            this.errorMessage = errorMessage;
        }
    }

    /**
     * Single file processing result.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FileProcessResult {
        private String filePath;
        private String fileName;
        private SettlementFileType fileType;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        @lombok.Builder.Default
        private FileProcessStatus status = FileProcessStatus.PENDING;
        private String settlementFileId;
        private int recordCount;
        private int matchedCount;
        private int discrepancyCount;
        private String errorMessage;
    }

    /**
     * Batch status.
     */
    public enum BatchStatus {
        PENDING, IN_PROGRESS, COMPLETED, COMPLETED_WITH_ERRORS, FAILED
    }

    /**
     * File process status.
     */
    public enum FileProcessStatus {
        PENDING, PROCESSING, SUCCESS, VALIDATION_FAILED, FAILED
    }
}
