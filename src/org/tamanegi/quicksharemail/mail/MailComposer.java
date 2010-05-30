package org.tamanegi.quicksharemail.mail;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.event.TransportEvent;
import javax.mail.event.TransportListener;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;

import org.tamanegi.quicksharemail.content.MessageContent;

public class MailComposer
{
    private static final String DEFAULT_CONNECT_TIMEOUT = "60000";
    private static final String DEFAULT_IO_TIMEOUT = "60000";

    private SmtpConfig smtpConfig;
    private MailConfig mailConfig;

    public static class SmtpConfig
    {
        public static final int SECURITY_TYPE_NONE = 0;
        public static final int SECURITY_TYPE_SSL = 1;
        public static final int SECURITY_TYPE_STARTTLS = 2;

        private String host;
        private String port;
        private int sec_type;

        private boolean auth;
        private String user;
        private String password;

        public SmtpConfig(String host, String port, int sec_type)
        {
            this.host = host;
            this.port = port;
            this.sec_type = sec_type;
        }

        public void setAuth(String user, String password)
        {
            auth = (user != null || password != null);
            this.user = user;
            this.password = password;
        }
    }

    public static class MailConfig
    {
        private String fromAddr;
        private MessageContent.AddressInfo[] toAddrs;
        private String subject;
        private Date date;

        private DataSource body;
        private ArrayList<DataSource> attach;

        public MailConfig(String fromAddr,
                          MessageContent.AddressInfo[] toAddrs,
                          String subject, Date date)
        {
            this.fromAddr = fromAddr;
            this.toAddrs = toAddrs;
            this.subject = subject;
            this.date = date;
            this.attach = new ArrayList<DataSource>();
        }

        public void setBody(DataSource body_src)
        {
            this.body = body_src;
        }

        public void appendPart(DataSource attach_src)
        {
            attach.add(attach_src);
        }
    }

    public MailComposer(SmtpConfig smtpConfig, MailConfig mailConfig)
    {
        this.smtpConfig = smtpConfig;
        this.mailConfig = mailConfig;
    }

    public void send() throws MessagingException
    {
        String transport_protocol = "smtp";
        if(smtpConfig.sec_type == SmtpConfig.SECURITY_TYPE_SSL) {
            transport_protocol = "smtps";
        }
        String prop_prefix = "mail." + transport_protocol;

        Properties prop = new Properties();
        prop.put(prop_prefix + ".host", smtpConfig.host);
        prop.put(prop_prefix + ".port", smtpConfig.port);
        prop.put(prop_prefix + ".auth", String.valueOf(smtpConfig.auth));
        if(smtpConfig.sec_type == SmtpConfig.SECURITY_TYPE_STARTTLS) {
            prop.put(prop_prefix + ".starttls.enable", "true");
            prop.put(prop_prefix + ".starttls.required", "true");
        }

        prop.put(prop_prefix + ".connectiontimeout", DEFAULT_CONNECT_TIMEOUT);
        prop.put(prop_prefix + ".timeout", DEFAULT_IO_TIMEOUT);

        Session session = Session.getInstance(prop);
        MimeMessage message = new MimeMessage(session);

        // set headers
        message.setFrom(new InternetAddress(mailConfig.fromAddr));
        message.setSubject(mailConfig.subject);
        for(int i = 0; i < mailConfig.toAddrs.length; i++) {
            message.addRecipient(
                Message.RecipientType.TO,
                new InternetAddress(mailConfig.toAddrs[i].getAddress()));
        }
        message.setSentDate(mailConfig.date);

        if(mailConfig.attach.size() < 1) {
            message.setDataHandler(new DataHandler(mailConfig.body));
        }
        else {
            MimeMultipart bodypart = new MimeMultipart();

            MimeBodyPart first_part = new MimeBodyPart();
            first_part.setDataHandler(new DataHandler(mailConfig.body));
            bodypart.addBodyPart(first_part);

            for(Iterator<DataSource> i = mailConfig.attach.iterator();
                i.hasNext(); ) {
                DataSource attach = i.next();
                MimeBodyPart attach_part = new MimeBodyPart();

                String encoded_name;
                try {
                    encoded_name = MimeUtility.encodeText(attach.getName());
                }
                catch(UnsupportedEncodingException e) {
                    e.printStackTrace();
                    encoded_name = attach.getName();
                }

                attach_part.setDataHandler(new DataHandler(attach));
                if(encoded_name.length() > 0) {
                    attach_part.setFileName(encoded_name);
                }

                bodypart.addBodyPart(attach_part);
            }

            message.setContent(bodypart);
        }

        Transport transport = null;
        try {
            // start transport
            transport = session.getTransport(transport_protocol);
            transport.addTransportListener(new MailTransportListener());

            if(smtpConfig.auth) {
                transport.connect(smtpConfig.user, smtpConfig.password);
            }
            else {
                transport.connect();
            }
            transport.sendMessage(message, message.getAllRecipients());
        }
        finally {
            if(transport != null) {
                transport.close();
            }
        }
    }

    private class MailTransportListener implements TransportListener
    {
        public void messageDelivered(TransportEvent e)
        {
            processEvent(e);
        }

        public void messageNotDelivered(TransportEvent e)
        {
            processEvent(e);
        }

        public void messagePartiallyDelivered(TransportEvent e)
        {
            processEvent(e);
        }

        private void processEvent(TransportEvent e)
        {
            setFlags(e.getValidSentAddresses(), true, true);
            setFlags(e.getValidUnsentAddresses(), true, false);
            setFlags(e.getInvalidAddresses(), false, true);
        }

        private void setFlags(Address[] addrs, boolean valid, boolean processed)
        {
            for(int i = 0; i < mailConfig.toAddrs.length; i++) {
                for(int j = 0; j < addrs.length; j++) {
                    if(addrs[j].toString().equals(
                           mailConfig.toAddrs[i].getAddress())) {
                        mailConfig.toAddrs[i].setValid(valid);
                        mailConfig.toAddrs[i].setProcessed(processed);
                    }
                }
            }
        }
    }
}
