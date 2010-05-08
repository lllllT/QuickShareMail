package org.tamanegi.quicksharemail.content;

import java.util.Date;

public class MessageContent
{
    private long id;
    private String type;
    private String subject_format;
    private AddressInfo[] address;
    private Date date;
    private String text;
    private String stream;

    public MessageContent()
    {
    }

    public long getId()
    {
        return id;
    }

    public void setId(long id)
    {
        this.id = id;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public String getSubjectFormat()
    {
        return subject_format;
    }

    public void setSubjectFormat(String subjectFormat)
    {
        subject_format = subjectFormat;
    }

    public int getAddressCount()
    {
        return (address != null ? address.length : 0);
    }

    public AddressInfo getAddressInfo(int idx)
    {
        return address[idx];
    }

    public AddressInfo[] getAddressInfo()
    {
        return address;
    }

    public void setAddress(String[] address)
    {
        this.address = new AddressInfo[address.length];

        for(int i = 0; i < address.length; i++) {
            this.address[i] = new AddressInfo(0, address[i]);
        }
    }

    public void setAddress(AddressInfo[] address)
    {
        this.address = address;
    }

    public Date getDate()
    {
        return date;
    }

    public void setDate(Date date)
    {
        this.date = date;
    }

    public String getText()
    {
        return text;
    }

    public void setText(String text)
    {
        this.text = text;
    }

    public String getStream()
    {
        return stream;
    }

    public void setStream(String stream)
    {
        this.stream = stream;
    }

    public static class AddressInfo
    {
        private long id;
        private String address;
        private boolean valid;
        private boolean processed;

        public AddressInfo(long id, String address)
        {
            this.id = id;
            this.address = address;
            this.valid = false;
            this.processed = false;
        }

        public long getId()
        {
            return id;
        }

        public String getAddress()
        {
            return address;
        }

        public boolean isValid()
        {
            return valid;
        }

        public void setValid(boolean valid)
        {
            this.valid = valid;
        }

        public boolean isProcessed()
        {
            return processed;
        }

        public void setProcessed(boolean processed)
        {
            this.processed = processed;
        }
    }
}
