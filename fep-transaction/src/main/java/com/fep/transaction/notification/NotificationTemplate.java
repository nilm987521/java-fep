package com.fep.transaction.notification;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Notification message templates.
 */
@Data
@Builder
public class NotificationTemplate {

    /** Template ID */
    private String templateId;

    /** Notification type */
    private NotificationType type;

    /** Channel */
    private NotificationChannel channel;

    /** Language code */
    private String language;

    /** Subject/Title template (for email/push) */
    private String subjectTemplate;

    /** Body template */
    private String bodyTemplate;

    /** Whether this template is active */
    @Builder.Default
    private boolean active = true;

    /** Variable placeholder pattern: ${variableName} */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * Renders the subject with provided variables.
     */
    public String renderSubject(Map<String, String> variables) {
        return render(subjectTemplate, variables);
    }

    /**
     * Renders the body with provided variables.
     */
    public String renderBody(Map<String, String> variables) {
        return render(bodyTemplate, variables);
    }

    /**
     * Renders template with variables.
     */
    private String render(String template, Map<String, String> variables) {
        if (template == null) {
            return "";
        }
        if (variables == null || variables.isEmpty()) {
            return template;
        }

        StringBuffer result = new StringBuffer();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);

        while (matcher.find()) {
            String variableName = matcher.group(1);
            String replacement = variables.getOrDefault(variableName, "${" + variableName + "}");
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    // Pre-defined templates

    /**
     * Creates SMS transaction success template (Chinese).
     */
    public static NotificationTemplate smsTransactionSuccessZh() {
        return NotificationTemplate.builder()
                .templateId("SMS_TXN_SUCCESS_ZH")
                .type(NotificationType.TRANSACTION_SUCCESS)
                .channel(NotificationChannel.SMS)
                .language("zh-TW")
                .bodyTemplate("【${bankName}】您的帳戶${maskedAccount}於${transactionTime}完成${transactionType}交易，金額${currency}${amount}，餘額${balance}。如非本人操作請撥${servicePhone}。")
                .build();
    }

    /**
     * Creates SMS transaction success template (English).
     */
    public static NotificationTemplate smsTransactionSuccessEn() {
        return NotificationTemplate.builder()
                .templateId("SMS_TXN_SUCCESS_EN")
                .type(NotificationType.TRANSACTION_SUCCESS)
                .channel(NotificationChannel.SMS)
                .language("en-US")
                .bodyTemplate("[${bankName}] Your account ${maskedAccount} completed ${transactionType} of ${currency}${amount} at ${transactionTime}. Balance: ${balance}. Contact ${servicePhone} if unauthorized.")
                .build();
    }

    /**
     * Creates SMS transaction failed template (Chinese).
     */
    public static NotificationTemplate smsTransactionFailedZh() {
        return NotificationTemplate.builder()
                .templateId("SMS_TXN_FAILED_ZH")
                .type(NotificationType.TRANSACTION_FAILED)
                .channel(NotificationChannel.SMS)
                .language("zh-TW")
                .bodyTemplate("【${bankName}】您的帳戶${maskedAccount}於${transactionTime}之${transactionType}交易失敗，原因：${failureReason}。如有疑問請撥${servicePhone}。")
                .build();
    }

    /**
     * Creates SMS OTP template (Chinese).
     */
    public static NotificationTemplate smsOtpZh() {
        return NotificationTemplate.builder()
                .templateId("SMS_OTP_ZH")
                .type(NotificationType.OTP_CODE)
                .channel(NotificationChannel.SMS)
                .language("zh-TW")
                .bodyTemplate("【${bankName}】您的驗證碼為${otpCode}，有效期限${expiryMinutes}分鐘。請勿將驗證碼告知他人。")
                .build();
    }

    /**
     * Creates email transaction success template (Chinese).
     */
    public static NotificationTemplate emailTransactionSuccessZh() {
        return NotificationTemplate.builder()
                .templateId("EMAIL_TXN_SUCCESS_ZH")
                .type(NotificationType.TRANSACTION_SUCCESS)
                .channel(NotificationChannel.EMAIL)
                .language("zh-TW")
                .subjectTemplate("【${bankName}】交易通知 - ${transactionType}成功")
                .bodyTemplate("""
                    親愛的客戶 ${customerName} 您好：

                    您的帳戶已完成以下交易：

                    交易類型：${transactionType}
                    交易帳號：${maskedAccount}
                    交易金額：${currency} ${amount}
                    交易時間：${transactionTime}
                    交易後餘額：${currency} ${balance}
                    交易序號：${transactionId}

                    如有任何疑問，請撥打客服專線：${servicePhone}

                    ${bankName} 敬上

                    ※ 此為系統自動發送之通知，請勿直接回覆。
                    """)
                .build();
    }

    /**
     * Creates push notification transaction success template (Chinese).
     */
    public static NotificationTemplate pushTransactionSuccessZh() {
        return NotificationTemplate.builder()
                .templateId("PUSH_TXN_SUCCESS_ZH")
                .type(NotificationType.TRANSACTION_SUCCESS)
                .channel(NotificationChannel.APP_PUSH)
                .language("zh-TW")
                .subjectTemplate("交易成功")
                .bodyTemplate("${transactionType} ${currency}${amount} 已完成，餘額 ${currency}${balance}")
                .build();
    }

    /**
     * Creates SMS large amount alert template (Chinese).
     */
    public static NotificationTemplate smsLargeAmountAlertZh() {
        return NotificationTemplate.builder()
                .templateId("SMS_LARGE_AMOUNT_ZH")
                .type(NotificationType.LARGE_AMOUNT_ALERT)
                .channel(NotificationChannel.SMS)
                .language("zh-TW")
                .bodyTemplate("【${bankName}】提醒您，帳戶${maskedAccount}於${transactionTime}有一筆大額${transactionType}交易，金額${currency}${amount}。如非本人操作請立即撥打${servicePhone}。")
                .build();
    }

    /**
     * Creates SMS security alert template (Chinese).
     */
    public static NotificationTemplate smsSecurityAlertZh() {
        return NotificationTemplate.builder()
                .templateId("SMS_SECURITY_ALERT_ZH")
                .type(NotificationType.SECURITY_ALERT)
                .channel(NotificationChannel.SMS)
                .language("zh-TW")
                .bodyTemplate("【${bankName}】安全警示！帳戶${maskedAccount}偵測到異常活動：${alertDescription}。如非本人操作請立即撥打${servicePhone}凍結帳戶。")
                .build();
    }
}
