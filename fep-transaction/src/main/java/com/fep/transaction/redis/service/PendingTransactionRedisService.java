package com.fep.transaction.redis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fep.transaction.redis.dto.PendingTransactionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Pending Transaction Redis 服務
 *
 * <p>提供 Redis ZSET 和 HASH 操作，用於高 TPS 交易處理架構。
 *
 * <p>Redis 資料結構：
 * <ul>
 *   <li><b>ZSET (fep:pending:txns)</b>: 用於 timeout 掃描
 *       <ul>
 *         <li>Score: expireTime (epoch ms)</li>
 *         <li>Member: transactionId</li>
 *       </ul>
 *   </li>
 *   <li><b>HASH (fep:txn:{txnId})</b>: 交易詳情快取
 *       <ul>
 *         <li>完整交易資訊的 JSON 序列化</li>
 *         <li>TTL: 完成後設定 5 分鐘</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p>使用範例：
 * <pre>
 * // 儲存 pending 交易
 * redisService.savePendingTransaction(dto);
 *
 * // 掃描超時交易
 * List&lt;String&gt; timeouts = redisService.scanTimeoutTransactions(100);
 *
 * // 載入交易上下文
 * Optional&lt;PendingTransactionDTO&gt; txn = redisService.loadTransaction(txnId);
 *
 * // 更新狀態並設定 TTL
 * redisService.updateStatusAndSetTtl(txnId, TransactionStatus.COMPLETED);
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingTransactionRedisService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${fep.pending.redis-key-prefix:fep}")
    private String keyPrefix;

    @Value("${fep.pending.completed-ttl-seconds:300}")
    private long completedTtlSeconds;

    /**
     * ZSET Key: 用於 timeout 掃描
     */
    private static final String PENDING_ZSET_SUFFIX = ":pending:txns";

    /**
     * HASH Key 前綴: 交易詳情
     */
    private static final String TXN_HASH_PREFIX = ":txn:";

    /**
     * STAN 到 TransactionId 映射前綴
     */
    private static final String STAN_MAPPING_PREFIX = ":stan:";

    // ==================== ZSET Operations ====================

    /**
     * 取得 ZSET Key
     */
    private String getPendingZsetKey() {
        return keyPrefix + PENDING_ZSET_SUFFIX;
    }

    /**
     * 取得交易 HASH Key
     */
    private String getTxnHashKey(String transactionId) {
        return keyPrefix + TXN_HASH_PREFIX + transactionId;
    }

    /**
     * 取得 STAN 映射 Key
     */
    private String getStanMappingKey(String stan) {
        return keyPrefix + STAN_MAPPING_PREFIX + stan;
    }

    /**
     * 新增交易至 Pending ZSET
     *
     * @param transactionId 交易 ID
     * @param expireTimeMs 過期時間 (epoch ms)
     */
    public void addToPendingZset(String transactionId, long expireTimeMs) {
        String key = getPendingZsetKey();
        Boolean result = stringRedisTemplate.opsForZSet().add(key, transactionId, expireTimeMs);
        if (log.isDebugEnabled()) {
            log.debug("Added to pending ZSET: txnId={}, expireAt={}, result={}",
                    transactionId, expireTimeMs, result);
        }
    }

    /**
     * 從 Pending ZSET 移除交易
     *
     * @param transactionId 交易 ID
     */
    public void removeFromPendingZset(String transactionId) {
        String key = getPendingZsetKey();
        Long removed = stringRedisTemplate.opsForZSet().remove(key, transactionId);
        if (log.isDebugEnabled()) {
            log.debug("Removed from pending ZSET: txnId={}, removed={}", transactionId, removed);
        }
    }

    /**
     * 掃描超時交易
     *
     * <p>使用 ZRANGEBYSCORE 取得 score <= currentTime 的交易 ID
     *
     * @param limit 最大取得數量
     * @return 超時交易 ID 列表
     */
    public List<String> scanTimeoutTransactions(int limit) {
        String key = getPendingZsetKey();
        long now = System.currentTimeMillis();

        Set<String> results = stringRedisTemplate.opsForZSet()
                .rangeByScore(key, 0, now, 0, limit);

        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }

        log.debug("Scanned timeout transactions: count={}", results.size());
        return results.stream().collect(Collectors.toList());
    }

    /**
     * 取得 Pending 交易數量
     */
    public long getPendingCount() {
        String key = getPendingZsetKey();
        Long size = stringRedisTemplate.opsForZSet().zCard(key);
        return size != null ? size : 0;
    }

    // ==================== HASH Operations ====================

    /**
     * 儲存 Pending 交易至 Redis
     *
     * <p>同時儲存：
     * <ul>
     *   <li>HASH (交易詳情)</li>
     *   <li>ZSET (timeout 掃描)</li>
     *   <li>STAN 映射 (回應關聯)</li>
     * </ul>
     *
     * @param dto 交易資料
     */
    public void savePendingTransaction(PendingTransactionDTO dto) {
        String txnId = dto.getTransactionId();
        String hashKey = getTxnHashKey(txnId);

        try {
            // 1. 儲存交易詳情至 HASH
            String json = objectMapper.writeValueAsString(dto);
            stringRedisTemplate.opsForValue().set(hashKey, json);

            // 2. 新增至 Pending ZSET
            if (dto.getExpireAt() != null) {
                addToPendingZset(txnId, dto.getExpireAt());
            }

            // 3. 建立 STAN 映射
            if (dto.getStan() != null) {
                String stanKey = getStanMappingKey(dto.getStan());
                stringRedisTemplate.opsForValue().set(stanKey, txnId,
                        Duration.ofMillis(dto.getExpireAt() - System.currentTimeMillis() + 60000));
            }

            log.debug("Saved pending transaction: txnId={}, stan={}", txnId, dto.getStan());

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize transaction: txnId={}", txnId, e);
            throw new RuntimeException("Failed to save pending transaction", e);
        }
    }

    /**
     * 載入交易資料
     *
     * @param transactionId 交易 ID
     * @return 交易資料，若不存在則返回 empty
     */
    public Optional<PendingTransactionDTO> loadTransaction(String transactionId) {
        String hashKey = getTxnHashKey(transactionId);
        String json = stringRedisTemplate.opsForValue().get(hashKey);

        if (json == null) {
            log.debug("Transaction not found in Redis: txnId={}", transactionId);
            return Optional.empty();
        }

        try {
            PendingTransactionDTO dto = objectMapper.readValue(json, PendingTransactionDTO.class);
            return Optional.of(dto);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize transaction: txnId={}", transactionId, e);
            return Optional.empty();
        }
    }

    /**
     * 透過 STAN 載入交易資料
     *
     * @param stan STAN
     * @return 交易資料，若不存在則返回 empty
     */
    public Optional<PendingTransactionDTO> loadTransactionByStan(String stan) {
        String stanKey = getStanMappingKey(stan);
        String transactionId = stringRedisTemplate.opsForValue().get(stanKey);

        if (transactionId == null) {
            log.debug("Transaction not found by STAN: stan={}", stan);
            return Optional.empty();
        }

        return loadTransaction(transactionId);
    }

    /**
     * 取得 STAN 對應的交易 ID
     *
     * @param stan STAN
     * @return 交易 ID，若不存在則返回 null
     */
    public String getTransactionIdByStan(String stan) {
        String stanKey = getStanMappingKey(stan);
        return stringRedisTemplate.opsForValue().get(stanKey);
    }

    /**
     * 更新交易資料
     *
     * @param dto 更新後的交易資料
     */
    public void updateTransaction(PendingTransactionDTO dto) {
        String txnId = dto.getTransactionId();
        String hashKey = getTxnHashKey(txnId);

        try {
            String json = objectMapper.writeValueAsString(dto);
            stringRedisTemplate.opsForValue().set(hashKey, json);
            log.debug("Updated transaction: txnId={}, status={}", txnId, dto.getStatus());
        } catch (JsonProcessingException e) {
            log.error("Failed to update transaction: txnId={}", txnId, e);
            throw new RuntimeException("Failed to update transaction", e);
        }
    }

    /**
     * 更新交易狀態並設定 TTL
     *
     * <p>用於交易完成後，設定 HASH 的 TTL 並從 ZSET 移除
     *
     * @param transactionId 交易 ID
     * @param status 新狀態
     */
    public void updateStatusAndSetTtl(String transactionId, PendingTransactionDTO.TransactionStatus status) {
        Optional<PendingTransactionDTO> optDto = loadTransaction(transactionId);
        if (optDto.isEmpty()) {
            log.warn("Cannot update status, transaction not found: txnId={}", transactionId);
            return;
        }

        PendingTransactionDTO dto = optDto.get();
        dto.setStatus(status);

        String hashKey = getTxnHashKey(transactionId);

        try {
            String json = objectMapper.writeValueAsString(dto);
            stringRedisTemplate.opsForValue().set(hashKey, json, completedTtlSeconds, TimeUnit.SECONDS);

            // 從 ZSET 移除
            removeFromPendingZset(transactionId);

            log.debug("Updated status and set TTL: txnId={}, status={}, ttl={}s",
                    transactionId, status, completedTtlSeconds);

        } catch (JsonProcessingException e) {
            log.error("Failed to update status: txnId={}", transactionId, e);
            throw new RuntimeException("Failed to update status", e);
        }
    }

    /**
     * 清理交易相關資料
     *
     * <p>清理 HASH、ZSET、STAN 映射
     *
     * @param transactionId 交易 ID
     * @param stan STAN
     */
    public void cleanup(String transactionId, String stan) {
        // 1. 刪除 HASH
        String hashKey = getTxnHashKey(transactionId);
        stringRedisTemplate.delete(hashKey);

        // 2. 從 ZSET 移除
        removeFromPendingZset(transactionId);

        // 3. 刪除 STAN 映射
        if (stan != null) {
            String stanKey = getStanMappingKey(stan);
            stringRedisTemplate.delete(stanKey);
        }

        log.debug("Cleaned up transaction: txnId={}, stan={}", transactionId, stan);
    }

    /**
     * 檢查交易是否存在
     *
     * @param transactionId 交易 ID
     * @return true 如果存在
     */
    public boolean exists(String transactionId) {
        String hashKey = getTxnHashKey(transactionId);
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(hashKey));
    }
}
