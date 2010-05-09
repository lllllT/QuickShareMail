package org.tamanegi.quicksharemail.content;

public class SendToContent
{
    private long id;
    private String label;
    private String subject_format;
    private String body_format;
    private String allow_type;
    private String[] address;
    private int priority;
    private boolean is_enable;
    private boolean is_alternate;

    public long getId()
    {
        return id;
    }

    public void setId(long id)
    {
        this.id = id;
    }

    public String getLabel()
    {
        return label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }

    public String getSubjectFormat()
    {
        return subject_format;
    }

    public void setSubjectFormat(String subjectFormat)
    {
        subject_format = subjectFormat;
    }

    public String getBodyFormat()
    {
        return body_format;
    }

    public void setBodyFormat(String bodyFormat)
    {
        body_format = bodyFormat;
    }

    public String getAllowType()
    {
        return allow_type;
    }

    public void setAllowType(String allowType)
    {
        allow_type = allowType;
    }

    public int getAddressCount()
    {
        return (address != null ? address.length : 0);
    }

    public String getAddress(int idx)
    {
        return address[idx];
    }

    public String[] getAddress()
    {
        return address;
    }

    public void setAddress(String[] address)
    {
        this.address = address;
    }

    public void setAddress(int idx, String address)
    {
        this.address[idx] = address;
    }

    public int getPriority()
    {
        return priority;
    }

    public void setPriority(int priority)
    {
        this.priority = priority;
    }

    public boolean isEnable()
    {
        return is_enable;
    }

    public void setEnable(boolean is_enable)
    {
        this.is_enable = is_enable;
    }

    public boolean isAlternate()
    {
        return is_alternate;
    }

    public void setAlternate(boolean is_alternate)
    {
        this.is_alternate = is_alternate;
    }
}
