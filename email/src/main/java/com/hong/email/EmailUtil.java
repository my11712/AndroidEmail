package com.hong.email;


import android.os.Looper;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Address;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;

//https://www.jianshu.com/p/e7f72da4644d
//http://www.126.com/help/mscan_06.htm 126邮箱参数设置
//https://www.cnblogs.com/shihuc/p/5069783.html 126邮箱需要开启第三方登录
//https://blog.csdn.net/richiezhu/article/details/79578483
public class EmailUtil {



    private EmailAccount account;
    private List<String> toAddress;
    private List<String> copyToAddress;

    public EmailUtil(EmailAccount account) {
        this.account = account;
    }


    public EmailUtil setToAddress(List<String> toAddress) {
        this.toAddress = toAddress;
        return this;
    }

    public EmailUtil setAccount(EmailAccount account) {
        this.account = account;
        return this;
    }

    public EmailUtil setCopyToAddress(List<String> copyToAddress) {
        this.copyToAddress = copyToAddress;
        return this;
    }


    /**
     * 发送邮件
     *
     * @param emailMessage  发送的消息
     * @param emailListener 回调
     */
    public void sendEmail(EmailMessage emailMessage, EmailListener emailListener) {
        Transport transport = null;
        try {
            Session emailSession = getEmailSession(account);
            MimeMultipart mimeMultipart = buildContent(emailMessage.getContent(), emailMessage.getContentType(), emailMessage.getFiles());
            MimeMessage message = new MimeMessage(emailSession);

            //发送人
            message.setFrom(new InternetAddress(account.getFrom()));

            if (toAddress.isEmpty()) {
                emailListener.onFail(ErrorCode.ERROR_Receive_EMPTY, ErrorCode.ERROR_RECEIVE_EMPTY_MSG);
                return;
            }

            //接收
            for (String addressStr : toAddress) {
                Address address = new InternetAddress(addressStr);
                message.addRecipient(Message.RecipientType.TO, address);
            }

            //抄送
            if (copyToAddress != null) {
                for (String addressStr : copyToAddress) {
                    Address address = new InternetAddress(addressStr);
                    message.addRecipient(Message.RecipientType.CC, address);
                }
            }
            if (isMainThread()) {
                emailListener.onFail(ErrorCode.ERROR_MAIN_THREAD, ErrorCode.ERROR_MAIN_THREAD_MSG);
                return;
            }

            message.setSentDate(new Date());
            message.setSubject(emailMessage.getTitle());
            message.setContent(mimeMultipart);
            message.saveChanges();
            transport = emailSession.getTransport();
            transport.addTransportListener(new TransListener(emailListener));
            transport.connect();
            transport.sendMessage(message, message.getAllRecipients());
        } catch (Exception e) {
            e.printStackTrace();
            if (emailListener != null) emailListener.onFail(-1, e.toString());
        } finally {
            if (transport != null) {
                try {
                    transport.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }


    private boolean isMainThread() {
        return Looper.getMainLooper() == Looper.myLooper();
    }


    private static Session getEmailSession(final EmailAccount emailAccount) throws AddressException {
        // 获取系统属性
        Properties properties = new Properties();
        properties.put("mail.smtp.user", new InternetAddress(emailAccount.getFrom()));//登录邮件服务器的用户名
        properties.setProperty("mail.transport.protocol", emailAccount.getProtocol());
        properties.setProperty("mail.smtp.host", emailAccount.getHost());
        properties.setProperty("mail.smtp.port", emailAccount.getPort());
        properties.setProperty("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.connectiontimeout", emailAccount.getConnectTimeOut());
        properties.setProperty("mail.smtp.timeout", emailAccount.getTimeout());
        if (emailAccount.isSsl()) {
            properties.put("mail.smtp.ssl.enable", true);
        }
        properties.put("mail.debug", true);

        properties.setProperty("mail.transport", "smtp");
        // 获取默认session对象
        return Session.getDefaultInstance(properties,
                new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        // 登陆邮件发送服务器的用户名和密码
                        return new PasswordAuthentication(emailAccount.getFrom(), emailAccount.getPassword());
                    }
                });
    }


    /**
     * 邮件内容
     *
     * @param content     内容
     * @param contentType 内容格式
     * @param files       附件
     * @return
     * @throws MessagingException
     * @throws UnsupportedEncodingException
     */
    private MimeMultipart buildContent(String content, String contentType, File[] files) throws MessagingException, UnsupportedEncodingException {
        MimeMultipart mp = new MimeMultipart();
        MimeBodyPart mimeBodyPart = new MimeBodyPart();
        if (contentType == null) {
            mimeBodyPart.setText(content);
        } else {
            mimeBodyPart.setContent(content, contentType);//"text/html;charset=UTF-8"
        }
        mp.addBodyPart(mimeBodyPart);

        if (files != null && files.length > 0) {
            //设置附件
            mp.addBodyPart(createFileBodyPart(files));
        }
        mp.setSubType(EmailMessage.SUBTYPE_MIXED);
        return mp;
    }

    private MimeBodyPart createFileBodyPart(File[] files) throws MessagingException, UnsupportedEncodingException {
        if (files == null || files.length <= 0) {
            return null;
        }
        MimeBodyPart fileBodyPart = new MimeBodyPart();
        //===内嵌 related  把多文件打包起来
        MimeMultipart fileMultipart = new MimeMultipart(EmailMessage.SUBTYPE_RELATED);
        for (File file : files) {
            MimeBodyPart fileBodyPartTemp = new MimeBodyPart();
            FileDataSource fileDataSource = new FileDataSource(file);
            fileBodyPartTemp.setDataHandler(new DataHandler(fileDataSource));
            fileBodyPartTemp.setFileName(MimeUtility.encodeText(file.getName()));
            fileMultipart.addBodyPart(fileBodyPartTemp);
        }
        fileBodyPart.setContent(fileMultipart);
        return fileBodyPart;
    }


}
