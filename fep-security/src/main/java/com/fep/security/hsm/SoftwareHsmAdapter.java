package com.fep.security.hsm;

import com.fep.security.crypto.CryptoService;
import com.fep.security.key.KeyManager;
import com.fep.security.mac.MacAlgorithm;
import com.fep.security.mac.MacService;
import com.fep.security.pin.PinBlock;
import com.fep.security.pin.PinBlockFormat;
import com.fep.security.pin.PinBlockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.UUID;

/**
 * Software-based HSM adapter for development and testing.
 * This implementation provides HSM-like functionality using software cryptography.
 *
 * WARNING: This should NOT be used in production environments.
 * Use hardware HSM adapters (Thales, Utimaco, SafeNet) for production.
 */
public class SoftwareHsmAdapter implements HsmAdapter {

    private static final Logger log = LoggerFactory.getLogger(SoftwareHsmAdapter.class);

    private final CryptoService cryptoService;
    private final KeyManager keyManager;
    private final PinBlockService pinBlockService;
    private final MacService macService;

    private volatile boolean connected = false;

    public SoftwareHsmAdapter(CryptoService cryptoService, KeyManager keyManager,
                               PinBlockService pinBlockService, MacService macService) {
        this.cryptoService = cryptoService;
        this.keyManager = keyManager;
        this.pinBlockService = pinBlockService;
        this.macService = macService;
    }

    @Override
    public HsmVendor getVendor() {
        return HsmVendor.SOFTWARE;
    }

    @Override
    public void connect() throws HsmException {
        connected = true;
        log.info("Software HSM connected (development mode)");
    }

    @Override
    public void disconnect() {
        connected = false;
        log.info("Software HSM disconnected");
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public HsmResponse execute(HsmRequest request) throws HsmException {
        ensureConnected();

        long startTime = System.currentTimeMillis();
        String requestId = request.getRequestId() != null ? request.getRequestId() : generateRequestId();

        try {
            HsmResponse response = switch (request.getCommand()) {
                case GENERATE_KEY -> handleGenerateKey(request);
                case IMPORT_KEY -> handleImportKey(request);
                case EXPORT_KEY -> handleExportKey(request);
                case TRANSLATE_PIN_BLOCK -> handleTranslatePinBlock(request);
                case GENERATE_MAC -> handleGenerateMac(request);
                case VERIFY_MAC -> handleVerifyMac(request);
                case ENCRYPT_DATA -> handleEncryptData(request);
                case DECRYPT_DATA -> handleDecryptData(request);
                case GET_DIAGNOSTICS -> handleGetDiagnostics();
                case GET_STATUS -> handleGetStatus();
                default -> HsmResponse.failure(requestId, "99", "Unsupported command");
            };

            response.setRequestId(requestId);
            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            return response;

        } catch (Exception e) {
            log.error("Software HSM command failed: {}", request.getCommand(), e);
            return HsmResponse.failure(requestId, "99", e.getMessage());
        }
    }

    @Override
    public HsmResponse generateKey(String keyType, String keyId) throws HsmException {
        HsmRequest request = HsmRequest.builder()
                .command(HsmCommand.GENERATE_KEY)
                .build()
                .addParameter("keyType", keyType)
                .addParameter("keyId", keyId);
        return execute(request);
    }

    @Override
    public HsmResponse importKey(String keyType, byte[] encryptedKey, String keyId) throws HsmException {
        HsmRequest request = HsmRequest.builder()
                .command(HsmCommand.IMPORT_KEY)
                .build()
                .addParameter("keyType", keyType)
                .addParameter("encryptedKey", encryptedKey)
                .addParameter("keyId", keyId);
        return execute(request);
    }

    @Override
    public HsmResponse exportKey(String keyId, String kekId) throws HsmException {
        HsmRequest request = HsmRequest.builder()
                .command(HsmCommand.EXPORT_KEY)
                .build()
                .addParameter("keyId", keyId)
                .addParameter("kekId", kekId);
        return execute(request);
    }

    @Override
    public HsmResponse translatePinBlock(byte[] pinBlock, String sourceKeyId, String destKeyId,
                                          String sourceFormat, String destFormat, String pan) throws HsmException {
        HsmRequest request = HsmRequest.builder()
                .command(HsmCommand.TRANSLATE_PIN_BLOCK)
                .build()
                .addParameter("pinBlock", pinBlock)
                .addParameter("sourceKeyId", sourceKeyId)
                .addParameter("destKeyId", destKeyId)
                .addParameter("sourceFormat", sourceFormat)
                .addParameter("destFormat", destFormat)
                .addParameter("pan", pan);
        return execute(request);
    }

    @Override
    public HsmResponse generateMac(byte[] data, String keyId, String algorithm) throws HsmException {
        HsmRequest request = HsmRequest.builder()
                .command(HsmCommand.GENERATE_MAC)
                .build()
                .addParameter("data", data)
                .addParameter("keyId", keyId)
                .addParameter("algorithm", algorithm);
        return execute(request);
    }

    @Override
    public HsmResponse verifyMac(byte[] data, byte[] mac, String keyId, String algorithm) throws HsmException {
        HsmRequest request = HsmRequest.builder()
                .command(HsmCommand.VERIFY_MAC)
                .build()
                .addParameter("data", data)
                .addParameter("mac", mac)
                .addParameter("keyId", keyId)
                .addParameter("algorithm", algorithm);
        return execute(request);
    }

    @Override
    public HsmResponse encryptData(byte[] data, String keyId, String algorithm) throws HsmException {
        HsmRequest request = HsmRequest.builder()
                .command(HsmCommand.ENCRYPT_DATA)
                .build()
                .addParameter("data", data)
                .addParameter("keyId", keyId)
                .addParameter("algorithm", algorithm);
        return execute(request);
    }

    @Override
    public HsmResponse decryptData(byte[] encryptedData, String keyId, String algorithm) throws HsmException {
        HsmRequest request = HsmRequest.builder()
                .command(HsmCommand.DECRYPT_DATA)
                .build()
                .addParameter("encryptedData", encryptedData)
                .addParameter("keyId", keyId)
                .addParameter("algorithm", algorithm);
        return execute(request);
    }

    @Override
    public HsmResponse getDiagnostics() throws HsmException {
        return execute(HsmRequest.builder().command(HsmCommand.GET_DIAGNOSTICS).build());
    }

    @Override
    public HsmResponse getStatus() throws HsmException {
        return execute(HsmRequest.builder().command(HsmCommand.GET_STATUS).build());
    }

    // Handler methods

    private HsmResponse handleGenerateKey(HsmRequest request) {
        byte[] key = cryptoService.generateTdesKey();
        String kcv = keyManager.calculateKcv(key);

        return HsmResponse.success(request.getRequestId())
                .addData("key", key)
                .addData("kcv", kcv);
    }

    private HsmResponse handleImportKey(HsmRequest request) {
        // In software mode, we just return success
        // Real HSM would encrypt under LMK
        return HsmResponse.success(request.getRequestId());
    }

    private HsmResponse handleExportKey(HsmRequest request) {
        String keyId = request.getParameter("keyId");
        String kekId = request.getParameter("kekId");

        byte[] key = keyManager.getKey(keyId);
        byte[] kek = keyManager.getKey(kekId);

        if (key == null) {
            return HsmResponse.failure(request.getRequestId(), "10", "Key not found");
        }
        if (kek == null) {
            return HsmResponse.failure(request.getRequestId(), "10", "KEK not found");
        }

        byte[] encryptedKey = cryptoService.encryptTdes(kek, key);

        return HsmResponse.success(request.getRequestId())
                .addData("encryptedKey", encryptedKey);
    }

    private HsmResponse handleTranslatePinBlock(HsmRequest request) {
        byte[] pinBlockData = request.getParameter("pinBlock");
        String sourceKeyId = request.getParameter("sourceKeyId");
        String destKeyId = request.getParameter("destKeyId");
        String sourceFormatStr = request.getParameter("sourceFormat");
        String destFormatStr = request.getParameter("destFormat");
        String pan = request.getParameter("pan");

        PinBlockFormat sourceFormat = PinBlockFormat.valueOf(sourceFormatStr);
        PinBlockFormat destFormat = PinBlockFormat.valueOf(destFormatStr);

        // Create source PIN block
        PinBlock sourcePinBlock = PinBlock.builder()
                .format(sourceFormat)
                .data(pinBlockData)
                .encrypted(true)
                .keyId(sourceKeyId)
                .build();

        // Translate
        PinBlock translatedBlock = pinBlockService.translatePinBlock(sourcePinBlock, sourceKeyId, destKeyId);

        // If format conversion is needed
        if (sourceFormat != destFormat) {
            PinBlock decrypted = pinBlockService.decryptPinBlock(translatedBlock);
            translatedBlock = pinBlockService.convertFormat(decrypted, destFormat, pan);
            translatedBlock = pinBlockService.encryptPinBlock(translatedBlock, destKeyId);
        }

        return HsmResponse.success(request.getRequestId())
                .addData("pinBlock", translatedBlock.getData());
    }

    private HsmResponse handleGenerateMac(HsmRequest request) {
        byte[] data = request.getParameter("data");
        String keyId = request.getParameter("keyId");
        String algorithm = request.getParameter("algorithm");

        MacAlgorithm macAlgorithm = MacAlgorithm.valueOf(algorithm);
        byte[] mac = macService.calculateMac(macAlgorithm, keyId, data);

        return HsmResponse.success(request.getRequestId())
                .addData("mac", mac);
    }

    private HsmResponse handleVerifyMac(HsmRequest request) {
        byte[] data = request.getParameter("data");
        byte[] mac = request.getParameter("mac");
        String keyId = request.getParameter("keyId");
        String algorithm = request.getParameter("algorithm");

        MacAlgorithm macAlgorithm = MacAlgorithm.valueOf(algorithm);
        boolean valid = macService.verifyMac(macAlgorithm, keyId, data, mac);

        return HsmResponse.success(request.getRequestId())
                .addData("valid", valid);
    }

    private HsmResponse handleEncryptData(HsmRequest request) {
        byte[] data = request.getParameter("data");
        String keyId = request.getParameter("keyId");

        byte[] key = keyManager.getKey(keyId);
        if (key == null) {
            return HsmResponse.failure(request.getRequestId(), "10", "Key not found");
        }

        // Pad to 8-byte boundary
        byte[] paddedData = padData(data, 8);
        byte[] encrypted = cryptoService.encryptTdes(key, paddedData);

        return HsmResponse.success(request.getRequestId())
                .addData("encryptedData", encrypted);
    }

    private HsmResponse handleDecryptData(HsmRequest request) {
        byte[] encryptedData = request.getParameter("encryptedData");
        String keyId = request.getParameter("keyId");

        byte[] key = keyManager.getKey(keyId);
        if (key == null) {
            return HsmResponse.failure(request.getRequestId(), "10", "Key not found");
        }

        byte[] decrypted = cryptoService.decryptTdes(key, encryptedData);

        return HsmResponse.success(request.getRequestId())
                .addData("data", decrypted);
    }

    private HsmResponse handleGetDiagnostics() {
        return HsmResponse.success(null)
                .addData("vendor", "Software HSM")
                .addData("version", "1.0.0")
                .addData("mode", "Development")
                .addData("keyCount", keyManager.getKeyStats().get("totalKeys"));
    }

    private HsmResponse handleGetStatus() {
        return HsmResponse.success(null)
                .addData("status", "OK")
                .addData("connected", connected)
                .addData("vendor", HsmVendor.SOFTWARE.getVendorName());
    }

    private void ensureConnected() throws HsmException {
        if (!connected) {
            throw new HsmException("Software HSM not connected");
        }
    }

    private String generateRequestId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private byte[] padData(byte[] data, int blockSize) {
        int padLength = blockSize - (data.length % blockSize);
        if (padLength == blockSize) {
            return data;
        }
        byte[] padded = new byte[data.length + padLength];
        System.arraycopy(data, 0, padded, 0, data.length);
        return padded;
    }
}
