package org.tamanegi.util;

public class StringCustomFormatter
{
    private IdValue[] id_values;

    public StringCustomFormatter(IdValue[] id_values)
    {
        this.id_values = id_values;
    }

    public String format(String fmt)
    {
        StringBuilder res = new StringBuilder();
        int len = fmt.length();
        int idx = 0;
        int next;

        while(idx >= 0) {
            next = fmt.indexOf("%", idx);
            if(next >= 0 && next + 1 < len) {
                res.append(fmt.substring(idx, next));
                res.append(findValue(fmt.charAt(next + 1)));
                idx = next + 2;
            }
            else if(next >= 0) {
                res.append(fmt.substring(idx, next + 1));
                idx = next + 1;
            }
            else {
                res.append(fmt.substring(idx));
                idx = next;
            }
        }

        return res.toString();
    }

    private CharSequence findValue(char id)
    {
        for(int i = 0; i < id_values.length; i++) {
            if(id_values[i].id == id) {
                return (id_values[i].value != null ? id_values[i].value : "");
            }
        }

        if(id == '%') {
            return "%";
        }

        return "%" + id;
    }

    public static class IdValue
    {
        private char id;
        private CharSequence value;

        public IdValue(char id, CharSequence value)
        {
            this.id = id;
            this.value = value;
        }
    }
}
