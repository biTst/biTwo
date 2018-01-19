package bi.two.exch;

public class Execution {
    public final Type m_type;
    public final String m_details;
    public final long m_time;

    public Execution(Type type, String details) {
        m_type = type;
        m_details = details;
        m_time = System.currentTimeMillis();
    }

    public enum Type {
        acknowledged,
        partialFill,
        fill,
        ;
    }
}
