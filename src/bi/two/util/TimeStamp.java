package bi.two.util;

public class TimeStamp {
    private final long m_millis;

    public TimeStamp() {
        m_millis = System.currentTimeMillis();
    }

    public long getPassedMillis() {
        return System.currentTimeMillis() - m_millis;
    }

    public String getPassed() {
        return Utils.millisToYDHMSStr(getPassedMillis());
    }
}
