package bi.two.ts;

import bi.two.chart.TickData;
import bi.two.exch.impl.BitMex;
import bi.two.exch.impl.Bitfinex;
import bi.two.exch.impl.CexIo;
import bi.two.util.Log;
import bi.two.util.MapConfig;
import bi.two.util.ReverseListIterator;
import bi.two.util.TimeStamp;

import java.util.List;
import java.util.concurrent.TimeUnit;

public enum TickReader {
    DIR("dir") {

    },
    FILE("file") {
        @Override public void readTicks(MapConfig config, BaseTicksTimesSeriesData<TickData> ticksTs, Runnable callback) throws Exception {
            FileTickReader.readFileTicks( config, ticksTs, callback);
        }
    },
    BITFINEX("bitfinex") {
        @Override public void readTicks(MapConfig config, BaseTicksTimesSeriesData<TickData> ticksTs, Runnable callback) throws Exception {
//            long period = TimeUnit.HOURS.toMillis(10);
            long period = TimeUnit.DAYS.toMillis(365);
            List<TickData> ticks = Bitfinex.readTicks(config, period);
            feedTicks(ticksTs, callback, ticks);
        }
    },
    CEX("cex") {
        @Override public void readTicks(MapConfig config, BaseTicksTimesSeriesData<TickData> ticksTs, Runnable callback) throws Exception {
//            long period = TimeUnit.MINUTES.toMillis(200);
            long period = TimeUnit.DAYS.toMillis(365);
            List<TickData> ticks = CexIo.readTicks(period);
            feedTicks(ticksTs, callback, ticks);
        }
    },
    BITMEX("bitmex") {
        @Override public void readTicks(MapConfig config, BaseTicksTimesSeriesData<TickData> ticksTs, Runnable callback) throws Exception {
            long period = TimeUnit.DAYS.toMillis(365);
            TicksTimesSeriesData<TickData> fileTs = BitMex.readTicks(config, period);
            int size = fileTs.getTicksNum();
            Iterable<TickData> iterable = fileTs.getReverseTicksIterable();
            feedTicks(ticksTs, callback, iterable, size);
        }
    },
    ;

    private final String m_name;

    private static void console(String s) { Log.console(s); }

    TickReader(String name) {
        m_name = name;
    }

    public static TickReader get(String name) {
        for (TickReader tickReader : values()) {
            if (tickReader.m_name.equals(name)) {
                return tickReader;
            }
        }
        throw new RuntimeException("Unknown TickReader '" + name + "'");
    }

    public void readTicks(MapConfig config, BaseTicksTimesSeriesData<TickData> ticksTs, Runnable callback) throws Exception {
        throw new RuntimeException("must be overridden");
    }

    private static void feedTicks(BaseTicksTimesSeriesData<TickData> ticksTs, Runnable callback, List<TickData> ticks) {
        int size = ticks.size();
        ReverseListIterator<TickData> iterator = new ReverseListIterator<>(ticks);
        feedTicks(ticksTs, callback, iterator, size);
    }

    private static void feedTicks(BaseTicksTimesSeriesData<TickData> ticksTs, Runnable callback, Iterable<TickData> iterator, int size) {
        TimeStamp doneTs = new TimeStamp();
        TimeStamp ts = new TimeStamp();
        int count = 0;
        for (TickData tick : iterator) {
            ticksTs.addNewestTick(tick);
            if (callback != null) {
                callback.run();
            }
            if (ts.getPassedMillis() > 30000) {
                ts.restart();
                console("feedTicks() " + count + " from " + size + " (" + (((float) count) / size) + ") total " + doneTs.getPassed());
            }
            count++;
        }
        console("feedTicks() done in " + doneTs.getPassed());
        ticksTs.notifyNoMoreTicks();
    }
}
