package bi.two.ts.join;

import bi.two.ts.ITimesSeriesData;

public enum TickJoiner {
    avg {
        @Override public BaseTickJoiner createBaseTickJoiner(ITimesSeriesData parent, long size, boolean collectTicks) {
            return new AvgTickJoiner( parent,  size,  collectTicks);
        }
    },
    close {
        @Override public BaseTickJoiner createBaseTickJoiner(ITimesSeriesData parent, long size, boolean collectTicks) {
            return new CloseTickJoiner( parent,  size,  collectTicks);
        }
    };

    public abstract BaseTickJoiner createBaseTickJoiner(ITimesSeriesData parent, long size, boolean collectTicks);

    public static TickJoiner get(String name) {
        for (TickJoiner joiner : values()) {
            if (joiner.name().equals(name)) {
                return joiner;
            }
        }
        throw new RuntimeException("no TickJoiner found with name '" + name + "'");
    }
}
