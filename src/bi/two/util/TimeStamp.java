package bi.two.util;

public class TimeStamp {
    private long m_millis;

    public TimeStamp() {
        this(System.currentTimeMillis());
    }

    public TimeStamp(long millis) {
        m_millis = millis;
    }

    public void reset() {
        m_millis = 0;
    }

    public long getPassedMillis() {
        return (m_millis == 0) ? 0 : System.currentTimeMillis() - m_millis;
    }

    public String getPassed() {
        return Utils.millisToYDHMSStr(getPassedMillis());
    }

    public void startIfNeeded() {
        if (m_millis == 0) {
            m_millis = System.currentTimeMillis();
        }
    }

    public boolean isStarted() {
        return (m_millis != 0);
    }
}
