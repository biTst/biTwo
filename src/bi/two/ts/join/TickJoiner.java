package bi.two.ts.join;

import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.ts.BaseTicksTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import bi.two.util.MapConfig;

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

    public static BaseTicksTimesSeriesData<? extends ITickData> wrapIfNeeded(TickJoiner joiner, BaseTicksTimesSeriesData<TickData> ticksTs, Integer joinTicksInReader, boolean collectTicks) {
        return (joiner != null)
                ? joiner.createBaseTickJoiner(ticksTs, joinTicksInReader, collectTicks)
                : ticksTs;
    }

    public static TickJoiner get(MapConfig config) {
        String joinerName = config.getString("joiner");
        return get(joinerName);
    }

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
