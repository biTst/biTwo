package bi.two.util;

public class StringParser {
    private final String m_str;
    private int m_index;

    public int getIndex() { return m_index; }

    public StringParser(String str) {
        m_str = str;
    }

    public int readFloatLight() {
        int start = m_index;
        read("-");
        int read = readDigits();
        if(read > 0) {
            read(".");
            read = readDigits();
            if(read > 0) {
                int end = m_index;
                return end - start;
            }
        }
        return 0;
    }

    public Float readFloat() {
        int start = m_index;
        int len = readFloatLight();
        if (len > 0) {
            String str = m_str.substring(start, start + len);
            float ret = Float.parseFloat(str);
            return ret;
        }
        return null;
    }

    public int readIntegerLight() {
        int start = m_index;
        read("-");
        int read = readDigits();
        if(read > 0) {
            int end = m_index;
            return end - start;
        }
        return 0;
    }

    public Integer readInteger() {
        int start = m_index;
        int len = readIntegerLight();
        if (len > 0) {
            String str = m_str.substring(start, start + len);
            int ret = Integer.parseInt(str);
            return ret;
        }
        return null;
    }

    private int readDigits() {
        int read = 0;
        while(!atEnd()) {
            char ch = m_str.charAt(m_index);
            if( Character.isDigit(ch) ){
                m_index++;
                read++;
            } else {
                break;
            }
        }
        return read;
    }

    public boolean read(String str) {
        if(!atEnd()) {
            boolean startsWith = m_str.startsWith(str, m_index);
            if (startsWith) {
                m_index += str.length();
            }
            return startsWith;
        }
        return false;
    }

    public boolean atEnd() {
        return m_index >= m_str.length();
    }
}
