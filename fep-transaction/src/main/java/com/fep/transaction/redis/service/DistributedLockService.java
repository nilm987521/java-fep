package com.fep.transaction.redis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 分散式鎖服務
 *
 * <p>使用 Redis 實作分散式鎖，支援：
 * <ul>
 *   <li><b>交易級別鎖</b>: 防止同一筆交易被重複處理</li>
 *   <li><b>Scanner Leader Election</b>: 確保多實例部署時只有一個 Scanner 執行</li>
 * </ul>
 *
 * <p>實作方式：
 * <ul>
 *   <li>SET NX EX 指令實作原子性獲取鎖</li>
 *   <li>Lua Script 實作安全釋放鎖（只有持有者能釋放）</li>
 *   <li>TTL 機制避免死鎖</li>
 * </ul>
 *
 * <p>使用範例：
 * <pre>
 * // 嘗試獲取鎖
 * String lockValue = lockService.tryLock("fep:lock:txn:123", Duration.ofSeconds(30));
 * if (lockValue != null) {
 *     try {
 *         // 執行關鍵區段
 *     } finally {
 *         lockService.unlock("fep:lock:txn:123", lockValue);
 *     }
 * }
 *
 * // Scanner Leader Election
 * boolean isLeader = lockService.tryAcquireLeadership("fep:scanner:leader", instanceId, Duration.ofSeconds(30));
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${fep.pending.redis-key-prefix:fep}")
    private String keyPrefix;

    /**
     * 解鎖 Lua Script
     *
     * <p>只有持有該 lockValue 的客戶端才能解鎖，避免誤解鎖
     */
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "    return redis.call('del', KEYS[1]) " +
                    "else " +
                    "    return 0 " +
                    "end";

    /**
     * 續租 Lua Script
     *
     * <p>延長鎖的 TTL，只有持有者能續租
     */
    private static final String RENEW_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "    return redis.call('pexpire', KEYS[1], ARGV[2]) " +
                    "else " +
                    "    return 0 " +
                    "end";

    // ==================== Transaction Lock ====================

    /**
     * 取得交易鎖 Key
     */
    private String getTxnLockKey(String transactionId) {
        return keyPrefix + ":lock:txn:" + transactionId;
    }

    /**
     * 嘗試獲取交易鎖
     *
     * @param transactionId 交易 ID
     * @param ttl 鎖的 TTL
     * @return lockValue 如果成功，null 如果失敗
     */
    public String tryLockTransaction(String transactionId, Duration ttl) {
        String lockKey = getTxnLockKey(transactionId);
        return tryLock(lockKey, ttl);
    }

    /**
     * 釋放交易鎖
     *
     * @param transactionId 交易 ID
     * @param lockValue 鎖的值（必須與獲取時相同）
     * @return true 如果成功釋放
     */
    public boolean unlockTransaction(String transactionId, String lockValue) {
        String lockKey = getTxnLockKey(transactionId);
        return unlock(lockKey, lockValue);
    }

    // ==================== General Lock ====================

    /**
     * 嘗試獲取鎖
     *
     * @param lockKey 鎖的 Key
     * @param ttl 鎖的 TTL
     * @return lockValue 如果成功，null 如果失敗
     */
    public String tryLock(String lockKey, Duration ttl) {
        String lockValue = generateLockValue();

        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, ttl);

        if (Boolean.TRUE.equals(success)) {
            log.debug("Acquired lock: key={}, value={}, ttl={}",
                    lockKey, lockValue, ttl);
            return lockValue;
        }

        log.debug("Failed to acquire lock: key={}", lockKey);
        return null;
    }

    /**
     * 釋放鎖
     *
     * @param lockKey 鎖的 Key
     * @param lockValue 鎖的值（必須與獲取時相同）
     * @return true 如果成功釋放
     */
    public boolean unlock(String lockKey, String lockValue) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(UNLOCK_SCRIPT);
        script.setResultType(Long.class);

        Long result = stringRedisTemplate.execute(
                script,
                Collections.singletonList(lockKey),
                lockValue
        );

        boolean success = result != null && result == 1L;
        if (success) {
            log.debug("Released lock: key={}, value={}", lockKey, lockValue);
        } else {
            log.debug("Failed to release lock (not owner or expired): key={}", lockKey);
        }

        return success;
    }

    /**
     * 續租鎖
     *
     * @param lockKey 鎖的 Key
     * @param lockValue 鎖的值
     * @param ttl 新的 TTL
     * @return true 如果成功續租
     */
    public boolean renewLock(String lockKey, String lockValue, Duration ttl) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(RENEW_SCRIPT);
        script.setResultType(Long.class);

        Long result = stringRedisTemplate.execute(
                script,
                Collections.singletonList(lockKey),
                lockValue,
                String.valueOf(ttl.toMillis())
        );

        boolean success = result != null && result == 1L;
        if (success) {
            log.debug("Renewed lock: key={}, ttl={}", lockKey, ttl);
        } else {
            log.debug("Failed to renew lock: key={}", lockKey);
        }

        return success;
    }

    // ==================== Leader Election ====================

    /**
     * 取得 Scanner Leader 鎖 Key
     */
    private String getScannerLeaderKey() {
        return keyPrefix + ":scanner:leader";
    }

    /**
     * 嘗試獲取 Scanner Leader 身份
     *
     * <p>用於多實例部署時的 Leader Election。
     * 只有一個實例能成為 Leader 並執行 timeout 掃描。
     *
     * @param instanceId 實例 ID
     * @param ttl Leader 身份的 TTL
     * @return true 如果成為 Leader
     */
    public boolean tryAcquireLeadership(String instanceId, Duration ttl) {
        String leaderKey = getScannerLeaderKey();
        String leaderValue = instanceId + ":" + System.currentTimeMillis();

        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(leaderKey, leaderValue, ttl);

        if (Boolean.TRUE.equals(success)) {
            log.debug("Acquired leadership: instanceId={}", instanceId);
            return true;
        }

        // 檢查是否已是 Leader（之前獲取的）
        String currentLeader = stringRedisTemplate.opsForValue().get(leaderKey);
        if (currentLeader != null && currentLeader.startsWith(instanceId + ":")) {
            // 續租 Leader 身份
            stringRedisTemplate.expire(leaderKey, ttl);
            log.debug("Renewed leadership: instanceId={}", instanceId);
            return true;
        }

        log.trace("Not leader: instanceId={}, currentLeader={}", instanceId, currentLeader);
        return false;
    }

    /**
     * 檢查是否為 Leader
     *
     * @param instanceId 實例 ID
     * @return true 如果是 Leader
     */
    public boolean isLeader(String instanceId) {
        String leaderKey = getScannerLeaderKey();
        String currentLeader = stringRedisTemplate.opsForValue().get(leaderKey);
        return currentLeader != null && currentLeader.startsWith(instanceId + ":");
    }

    /**
     * 放棄 Leader 身份
     *
     * @param instanceId 實例 ID
     */
    public void releaseLeadership(String instanceId) {
        String leaderKey = getScannerLeaderKey();
        String currentLeader = stringRedisTemplate.opsForValue().get(leaderKey);

        if (currentLeader != null && currentLeader.startsWith(instanceId + ":")) {
            stringRedisTemplate.delete(leaderKey);
            log.debug("Released leadership: instanceId={}", instanceId);
        }
    }

    // ==================== Helper Methods ====================

    /**
     * 產生唯一的鎖值
     */
    private String generateLockValue() {
        return UUID.randomUUID().toString();
    }
}
