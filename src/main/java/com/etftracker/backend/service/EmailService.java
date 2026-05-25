package com.etftracker.backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 電子郵件發送服務
 * 負責透過 JavaMailSender 將排版好的 HTML 報表安全寄送出去
 */
@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    /**
     * 發送精美的 HTML 格式電子郵件
     *
     * @param to          收件人信箱
     * @param subject     郵件標題
     * @param htmlContent HTML 郵件內容 (支援 CSS 樣式與表格)
     * @throws MessagingException 若郵件封裝或傳送失敗
     */
    public void sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        
        // 使用 UTF-8 確保中文不亂碼，並設定為 multipart 格式
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        
        helper.setFrom(senderEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        
        // 設定 text 且 isHtml = true，使郵件客戶端正確解析 HTML
        helper.setText(htmlContent, true);
        
        mailSender.send(mimeMessage);
    }
}
