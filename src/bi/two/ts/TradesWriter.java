package bi.two.ts;

import bi.two.chart.TickData;

public enum TradesWriter {
    simple("simple") {
        @Override public void writeTick(TickData tickData) {
            throw new RuntimeException("not implemented");
        }
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

    public abstract void writeTick(TickData tickData);
}
