package com.fep.message.transform;

import com.fep.common.message.InternalMessage;
import com.fep.message.channel.ChannelSchemaRegistry;
import com.fep.message.generic.message.GenericMessage;
import com.fep.message.generic.schema.MessageSchema;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 預設的訊息轉換器實作
 *
 * <p>使用配置化的欄位映射進行 GenericMessage 與 InternalMessage 之間的轉換
 */
@Slf4j
public class DefaultMessageTransformer implements MessageTransformer {

    private final FieldMappingConfig config;
    private final SchemaProvider schemaProvider;
    private final Map<String, FieldTransformer> fieldTransformers;

    /**
     * Schema 提供者介面
     */
    @FunctionalInterface
    public interface SchemaProvider {
        MessageSchema get(String schemaName);
    }

    /**
     * 建構函數（使用 ChannelSchemaRegistry）
     */
    public DefaultMessageTransformer(FieldMappingConfig config, ChannelSchemaRegistry schemaRegistry) {
        this.config = config;
        // 使用 ChannelSchemaRegistry 的 getDefaultRequestSchema 方法
        this.schemaProvider = schemaName -> {
            if (schemaRegistry == null || schemaName == null) {
                return null;
            }
            // 嘗試從 registry 取得 schema
            return schemaRegistry.getDefaultRequestSchema(schemaName);
        };
        this.fieldTransformers = initFieldTransformers();
    }

    /**
     * 建構函數（使用 Map）
     */
    public DefaultMessageTransformer(FieldMappingConfig config, Map<String, MessageSchema> schemaMap) {
        this.config = config;
        Map<String, MessageSchema> registry = schemaMap != null ? schemaMap : new HashMap<>();
        this.schemaProvider = registry::get;
        this.fieldTransformers = initFieldTransformers();
    }

    /**
     * 建構函數（無 Schema 提供者）
     */
    public DefaultMessageTransformer(FieldMappingConfig config) {
        this(config, (Map<String, MessageSchema>) null);
    }

    /**
     * 初始化欄位轉換器
     */
    private Map<String, FieldTransformer> initFieldTransformers() {
        Map<String, FieldTransformer> transformers = new HashMap<>();

        // MTI 轉換
        transformers.put("MTI_TO_ENUM", new MtiToEnumTransformer());

        // 字串 → 數字
        transformers.put("STRING_TO_LONG", new StringToLongTransformer());

        // 數字 → 補零字串
        transformers.put("LONG_TO_PADDED_STRING", new LongToPaddedStringTransformer());

        // 日期時間組合
        transformers.put("COMBINE_DATETIME", new CombineDateTimeTransformer());

        // 日期時間拆分
        transformers.put("SPLIT_DATETIME", new SplitDateTimeTransformer());

        return transformers;
    }

    @Override
    public InternalMessage toInternal(GenericMessage external, String sourceChannelId) throws TransformException {
        if (external == null) {
            throw new TransformException("External message is null");
        }

        FieldMappingConfig.ChannelMapping mapping = config.getChannelMapping(sourceChannelId);
        if (mapping == null) {
            log.warn("No mapping found for channel: {}, using direct mapping", sourceChannelId);
            return directToInternal(external, sourceChannelId);
        }

        log.debug("Transforming GenericMessage to InternalMessage: channel={}", sourceChannelId);

        InternalMessage internal = new InternalMessage();
        internal.setSourceChannelId(sourceChannelId);
        internal.setRawData(external.getRawData());
        internal.setSourceSchemaName(external.getSchema() != null ? external.getSchema().getName() : null);

        // 應用欄位映射
        Map<String, String> toInternalMap = mapping.getToInternal();
        for (Map.Entry<String, String> entry : toInternalMap.entrySet()) {
            String externalField = entry.getKey();
            String internalField = entry.getValue();

            // 跳過特殊欄位 (以 _ 開頭的是組合欄位)
            if (internalField.startsWith("_")) {
                continue;
            }

            Object value = external.getField(externalField);
            if (value != null) {
                setInternalField(internal, internalField, value.toString());
            }
        }

        // 應用轉換規則
        applyTransformations(external, internal, mapping, "toInternal");

        // 設定 MessageType
        String mti = external.getFieldAsString("mti");
        if (mti != null && internal.getMessageType() == null) {
            internal.setMessageType(InternalMessage.MessageType.fromMti(mti));
        }

        log.debug("Transformed to InternalMessage: traceNumber={}, messageType={}",
                internal.getTraceNumber(), internal.getMessageType());

        return internal;
    }

    @Override
    public GenericMessage toExternal(InternalMessage internal, String targetChannelId) throws TransformException {
        if (internal == null) {
            throw new TransformException("Internal message is null");
        }

        FieldMappingConfig.ChannelMapping mapping = config.getChannelMapping(targetChannelId);
        if (mapping == null) {
            log.warn("No mapping found for channel: {}, using direct mapping", targetChannelId);
            return directToExternal(internal, targetChannelId);
        }

        log.debug("Transforming InternalMessage to GenericMessage: channel={}", targetChannelId);

        // 取得目標 Schema
        MessageSchema schema = schemaProvider.get(mapping.getSchemaName());
        GenericMessage external = new GenericMessage(schema);

        // 應用欄位映射
        Map<String, String> fromInternalMap = mapping.getFromInternal();
        for (Map.Entry<String, String> entry : fromInternalMap.entrySet()) {
            String internalField = entry.getKey();
            String externalField = entry.getValue();

            Object value = getInternalField(internal, internalField);
            if (value != null) {
                external.setField(externalField, value.toString());
            }
        }

        // 應用轉換規則
        applyTransformations(internal, external, mapping, "fromInternal");

        // 設定 MTI
        if (internal.getMessageType() != null && external.getField("mti") == null) {
            external.setField("mti", internal.getMessageType().getMtiCode());
        }

        log.debug("Transformed to GenericMessage: schema={}, mti={}",
                schema != null ? schema.getName() : "null",
                external.getFieldAsString("mti"));

        return external;
    }

    @Override
    public boolean supportsChannel(String channelId) {
        return config.hasChannelMapping(channelId);
    }

    /**
     * 直接映射（無配置時使用）
     */
    private InternalMessage directToInternal(GenericMessage external, String sourceChannelId) {
        InternalMessage internal = new InternalMessage();
        internal.setSourceChannelId(sourceChannelId);
        internal.setRawData(external.getRawData());

        // 常見欄位直接映射
        internal.setMessageType(InternalMessage.MessageType.fromMti(external.getFieldAsString("mti")));
        internal.setTransactionCode(external.getFieldAsString("processingCode"));
        internal.setTraceNumber(external.getFieldAsString("stan"));
        internal.setReferenceNumber(external.getFieldAsString("rrn"));
        internal.setCardNumber(external.getFieldAsString("pan"));
        internal.setSourceAccount(external.getFieldAsString("sourceAccount"));
        internal.setDestinationAccount(external.getFieldAsString("destAccount"));
        internal.setTerminalId(external.getFieldAsString("terminalId"));
        internal.setMerchantId(external.getFieldAsString("merchantId"));
        internal.setResponseCode(external.getFieldAsString("responseCode"));
        internal.setAuthorizationCode(external.getFieldAsString("authCode"));

        // 金額轉換
        String amount = external.getFieldAsString("amount");
        if (amount != null) {
            try {
                internal.setTransactionAmount(Long.parseLong(amount.trim()));
            } catch (NumberFormatException e) {
                log.warn("Cannot parse amount: {}", amount);
            }
        }

        // 擴充欄位 - 複製所有未映射的欄位
        for (Map.Entry<String, Object> entry : external.getAllFields().entrySet()) {
            String key = entry.getKey();
            if (!isStandardField(key)) {
                internal.setExtendedField(key, entry.getValue());
            }
        }

        return internal;
    }

    /**
     * 直接映射（無配置時使用）
     */
    private GenericMessage directToExternal(InternalMessage internal, String targetChannelId) {
        GenericMessage external = new GenericMessage(null);

        // 常見欄位直接映射
        if (internal.getMessageType() != null) {
            external.setField("mti", internal.getMessageType().getMtiCode());
        }
        setIfNotNull(external, "processingCode", internal.getTransactionCode());
        setIfNotNull(external, "stan", internal.getTraceNumber());
        setIfNotNull(external, "rrn", internal.getReferenceNumber());
        setIfNotNull(external, "pan", internal.getCardNumber());
        setIfNotNull(external, "sourceAccount", internal.getSourceAccount());
        setIfNotNull(external, "destAccount", internal.getDestinationAccount());
        setIfNotNull(external, "terminalId", internal.getTerminalId());
        setIfNotNull(external, "merchantId", internal.getMerchantId());
        setIfNotNull(external, "responseCode", internal.getResponseCode());
        setIfNotNull(external, "authCode", internal.getAuthorizationCode());

        // 金額轉換
        if (internal.getTransactionAmount() != null) {
            external.setField("amount", String.format("%012d", internal.getTransactionAmount()));
        }

        // 擴充欄位
        for (Map.Entry<String, Object> entry : internal.getExtendedFields().entrySet()) {
            external.setField(entry.getKey(), entry.getValue());
        }

        return external;
    }

    /**
     * 設定 InternalMessage 欄位
     */
    private void setInternalField(InternalMessage internal, String fieldName, String value) {
        try {
            switch (fieldName) {
                case "messageType" -> {
                    // 已在別處處理
                }
                case "transactionCode" -> internal.setTransactionCode(value);
                case "traceNumber" -> internal.setTraceNumber(value);
                case "referenceNumber" -> internal.setReferenceNumber(value);
                case "businessKey" -> internal.setBusinessKey(value);
                case "sourceChannelId" -> internal.setSourceChannelId(value);
                case "sourceClientId" -> internal.setSourceClientId(value);
                case "targetChannelId" -> internal.setTargetChannelId(value);
                case "cardNumber" -> internal.setCardNumber(value);
                case "sourceAccount" -> internal.setSourceAccount(value);
                case "destinationAccount" -> internal.setDestinationAccount(value);
                case "sourceBankCode" -> internal.setSourceBankCode(value);
                case "destinationBankCode" -> internal.setDestinationBankCode(value);
                case "transactionAmount" -> {
                    try {
                        internal.setTransactionAmount(Long.parseLong(value.trim()));
                    } catch (NumberFormatException e) {
                        log.warn("Cannot parse amount: {}", value);
                    }
                }
                case "currencyCode" -> internal.setCurrencyCode(value);
                case "terminalId" -> internal.setTerminalId(value);
                case "merchantId" -> internal.setMerchantId(value);
                case "merchantName" -> internal.setMerchantName(value);
                case "responseCode" -> internal.setResponseCode(value);
                case "responseMessage" -> internal.setResponseMessage(value);
                case "authorizationCode" -> internal.setAuthorizationCode(value);
                case "traceId" -> internal.setTraceId(value);
                case "processKey" -> internal.setProcessKey(value);
                default -> internal.setExtendedField(fieldName, value);
            }
        } catch (Exception e) {
            log.warn("Error setting internal field '{}': {}", fieldName, e.getMessage());
        }
    }

    /**
     * 取得 InternalMessage 欄位值
     */
    private Object getInternalField(InternalMessage internal, String fieldName) {
        return switch (fieldName) {
            case "messageType" -> internal.getMessageType() != null ? internal.getMessageType().getMtiCode() : null;
            case "transactionCode" -> internal.getTransactionCode();
            case "traceNumber" -> internal.getTraceNumber();
            case "referenceNumber" -> internal.getReferenceNumber();
            case "businessKey" -> internal.getBusinessKey();
            case "sourceChannelId" -> internal.getSourceChannelId();
            case "sourceClientId" -> internal.getSourceClientId();
            case "targetChannelId" -> internal.getTargetChannelId();
            case "cardNumber" -> internal.getCardNumber();
            case "sourceAccount" -> internal.getSourceAccount();
            case "destinationAccount" -> internal.getDestinationAccount();
            case "sourceBankCode" -> internal.getSourceBankCode();
            case "destinationBankCode" -> internal.getDestinationBankCode();
            case "transactionAmount" -> internal.getTransactionAmount();
            case "currencyCode" -> internal.getCurrencyCode();
            case "terminalId" -> internal.getTerminalId();
            case "merchantId" -> internal.getMerchantId();
            case "merchantName" -> internal.getMerchantName();
            case "responseCode" -> internal.getResponseCode();
            case "responseMessage" -> internal.getResponseMessage();
            case "authorizationCode" -> internal.getAuthorizationCode();
            case "traceId" -> internal.getTraceId();
            case "processKey" -> internal.getProcessKey();
            default -> internal.getExtendedField(fieldName);
        };
    }

    /**
     * 應用轉換規則
     */
    private void applyTransformations(Object source, Object target,
                                       FieldMappingConfig.ChannelMapping mapping,
                                       String direction) {
        List<FieldMappingConfig.Transformation> transformations = mapping.getTransformations();
        if (transformations == null) {
            return;
        }

        for (FieldMappingConfig.Transformation transformation : transformations) {
            String transformDirection = transformation.getDirection();
            if (!"both".equals(transformDirection) && !direction.equals(transformDirection)) {
                continue;
            }

            String type = transformation.getType();
            FieldTransformer transformer = fieldTransformers.get(type);
            if (transformer == null) {
                log.warn("Unknown transformation type: {}", type);
                continue;
            }

            try {
                transformer.transform(source, target, transformation);
            } catch (Exception e) {
                log.warn("Transformation failed: type={}, field={}, error={}",
                        type, transformation.getField(), e.getMessage());
            }
        }
    }

    /**
     * 判斷是否為標準欄位
     */
    private boolean isStandardField(String fieldName) {
        return switch (fieldName) {
            case "mti", "processingCode", "stan", "rrn", "pan", "sourceAccount", "destAccount",
                 "amount", "terminalId", "merchantId", "responseCode", "authCode",
                 "localTime", "localDate", "currencyCode" -> true;
            default -> false;
        };
    }

    private void setIfNotNull(GenericMessage msg, String field, String value) {
        if (value != null) {
            msg.setField(field, value);
        }
    }

    // ==================== 欄位轉換器 ====================

    /**
     * 欄位轉換器介面
     */
    interface FieldTransformer {
        void transform(Object source, Object target, FieldMappingConfig.Transformation transformation);
    }

    /**
     * MTI → MessageType 轉換
     */
    static class MtiToEnumTransformer implements FieldTransformer {
        @Override
        public void transform(Object source, Object target, FieldMappingConfig.Transformation transformation) {
            if (source instanceof GenericMessage external && target instanceof InternalMessage internal) {
                String mti = external.getFieldAsString("mti");
                if (mti != null) {
                    internal.setMessageType(InternalMessage.MessageType.fromMti(mti));
                }
            }
        }
    }

    /**
     * 字串 → Long 轉換
     */
    static class StringToLongTransformer implements FieldTransformer {
        @Override
        public void transform(Object source, Object target, FieldMappingConfig.Transformation transformation) {
            if (source instanceof GenericMessage external && target instanceof InternalMessage internal) {
                String field = transformation.getField();
                String value = external.getFieldAsString(field);
                if (value != null) {
                    try {
                        internal.setTransactionAmount(Long.parseLong(value.trim()));
                    } catch (NumberFormatException e) {
                        log.warn("Cannot parse {} as Long: {}", field, value);
                    }
                }
            }
        }
    }

    /**
     * Long → 補零字串轉換
     */
    static class LongToPaddedStringTransformer implements FieldTransformer {
        @Override
        public void transform(Object source, Object target, FieldMappingConfig.Transformation transformation) {
            if (source instanceof InternalMessage internal && target instanceof GenericMessage external) {
                Long amount = internal.getTransactionAmount();
                if (amount != null) {
                    Map<String, Object> params = transformation.getParams();
                    int length = params.containsKey("length") ? ((Number) params.get("length")).intValue() : 12;
                    String padChar = params.containsKey("padChar") ? params.get("padChar").toString() : "0";
                    String formatted = String.format("%" + padChar + length + "d", amount);
                    external.setField("amount", formatted);
                }
            }
        }
    }

    /**
     * 日期時間組合轉換
     */
    static class CombineDateTimeTransformer implements FieldTransformer {
        @Override
        public void transform(Object source, Object target, FieldMappingConfig.Transformation transformation) {
            if (source instanceof GenericMessage external && target instanceof InternalMessage internal) {
                Map<String, Object> params = transformation.getParams();
                String timeField = (String) params.get("timeField");
                String dateField = (String) params.get("dateField");

                String time = external.getFieldAsString(timeField);
                String date = external.getFieldAsString(dateField);

                if (time != null && date != null) {
                    try {
                        // HHmmss + MMdd → LocalDateTime
                        int year = LocalDateTime.now().getYear();
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HHmmss MMdd yyyy");
                        LocalDateTime dateTime = LocalDateTime.parse(time + " " + date + " " + year, formatter);
                        internal.setTransactionTime(dateTime);
                    } catch (Exception e) {
                        log.warn("Cannot combine datetime: time={}, date={}", time, date);
                    }
                }
            }
        }
    }

    /**
     * 日期時間拆分轉換
     */
    static class SplitDateTimeTransformer implements FieldTransformer {
        @Override
        public void transform(Object source, Object target, FieldMappingConfig.Transformation transformation) {
            if (source instanceof InternalMessage internal && target instanceof GenericMessage external) {
                LocalDateTime dateTime = internal.getTransactionTime();
                if (dateTime == null) {
                    dateTime = LocalDateTime.now();
                }

                Map<String, Object> params = transformation.getParams();
                String timeField = (String) params.get("timeField");
                String dateField = (String) params.get("dateField");

                external.setField(timeField, dateTime.format(DateTimeFormatter.ofPattern("HHmmss")));
                external.setField(dateField, dateTime.format(DateTimeFormatter.ofPattern("MMdd")));
            }
        }
    }
}
