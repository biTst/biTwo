package bi.two.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;

public class StreamParser {
    private final PushbackInputStream m_pbis;
    private final StringBuilder m_sharedSb = new StringBuilder();

    public StreamParser(InputStream inputStream) {
        m_pbis = new PushbackInputStream(inputStream);
    }

    public int read() throws IOException {
        return m_pbis.read();
    }

    public boolean readChar(char c) throws IOException {
        int ch = m_pbis.read();
        boolean got = (ch == c);
        if (!got) {
            m_pbis.unread(ch);
        }
        return got;
    }

    public long readLong() throws IOException {
        m_sharedSb.setLength(0);
        int read;
        while ((read = m_pbis.read()) != -1) {
            if (!Character.isDigit(read)) {
                m_pbis.unread(read);
                break;
            }
            m_sharedSb.append((char)read);
        }
        return Long.parseLong(m_sharedSb.toString());
    }

    public double readDouble() throws IOException {
        readNumber();
        return Double.parseDouble(m_sharedSb.toString());
    }

    public float readFloat() throws IOException {
        readNumber();
        return Float.parseFloat(m_sharedSb.toString());
    }

    private void readNumber() throws IOException {
        m_sharedSb.setLength(0);
        int read;
        while ((read = m_pbis.read()) != -1) {
            if (Character.isDigit(read) || (read=='.') || (read=='-')) {
                m_sharedSb.append((char)read);
            } else {
                m_pbis.unread(read);
                break;
            }
        }
    }

    public String readLine() throws IOException {
        m_sharedSb.setLength(0);
        int read;
        while ((read = m_pbis.read()) != -1) {
            if ((read == '\n') || (read == '\r')) {
                m_pbis.unread(read);
                break;
            }
            m_sharedSb.append((char)read);
        }
        return m_sharedSb.toString();
    }

    public void skipDigits() throws IOException {
        int read;
        while ((read = m_pbis.read()) != -1) {
            if (!Character.isDigit(read)) {
                m_pbis.unread(read);
                return;
            }
        }
    }

    public void close() throws IOException {
        m_pbis.close();
    }
}
