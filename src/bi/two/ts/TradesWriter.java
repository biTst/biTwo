package bi.two.ts;

public enum TradesWriter {
    simple("simple") {

    };

    private final String m_name;

    TradesWriter(String name) {
        m_name = name;
    }

    public static TradesWriter get(String name) {
        for (TradesWriter tradesWriter : values()) {
            if (tradesWriter.m_name.equals(name)) {
                return tradesWriter;
            }
        }
        throw new RuntimeException("Unknown TradesWriter '" + name + "'");
    }
}
