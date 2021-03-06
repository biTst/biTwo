package bi.two.ts;

import bi.two.chart.TickData;
import bi.two.exch.Exchange;
import bi.two.exch.impl.BitMex;
import bi.two.exch.impl.Bitfinex;
import bi.two.exch.impl.CexIo;
import bi.two.util.MapConfig;
import bi.two.util.ReverseListIterator;
import bi.two.util.TimeStamp;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static bi.two.util.Log.console;

public enum TradesReader {
    DIR("dir") {
        @Override public void readTicks(MapConfig config, BaseTicksTimesSeriesData<TickData> ticksTs, Exchange exchange) throws Exception {
            DirTradesReader.readTrades(config, ticksTs, exchange);
        }
    },
    FILE("file") {
        @Override public void readTicks(MapConfig config, BaseTicksTimesSeriesData<TickData> ticksTs, Exchange exchange) throws Exception {
            FileTradesReader.readFileTrades(config, ticksTs);
        }
    },
    BITFINEX("bitfinex") {
        @Override public void readTicks(MapConfig config, BaseTicksTimesSeriesData<TickData> ticksTs, Exchange exchange) throws Exception {
            long period = TimeUnit.DAYS.toMillis(365);
            List<TickData> ticks = Bitfinex.readTicks(config, period);
            feedTicks(ticksTs, ticks);
        }
    },
    CEX("cex") {
        @Override public void readTicks(MapConfig config, BaseTicksTimesSeriesData<TickData> ticksTs, Exchange exchange) throws Exception {
            long period = TimeUnit.DAYS.toMillis(365);
            List<TickData> ticks = CexIo.readTicks(period);
            feedTicks(ticksTs, ticks);
        }
    },
    BITMEX("bitmex") {
        @Override public void readTicks(MapConfig config, BaseTicksTimesSeriesData<TickData> ticksTs, Exchange exchange) throws Exception {
            long period = TimeUnit.DAYS.toMillis(365);
            BitMex.readAndFeedTicks(config, period, ticksTs);
        }
    },
    ;

    private final String m_name;

    TradesReader(String name) {
        m_name = name;
    }

    public static TradesReader get(String name) {
        for (TradesReader tradesReader : values()) {
            if (tradesReader.m_name.equals(name)) {
                return tradesReader;
            }
        }
        throw new RuntimeException("Unknown TradesReader '" + name + "'");
    }

    public void readTicks(MapConfig config, BaseTicksTimesSeriesData<TickData> ticksTs, Exchange exchange) throws Exception {
        throw new RuntimeException("must be overridden");
    }

    private static void feedTicks(BaseTicksTimesSeriesData<TickData> ticksTs, List<TickData> ticks) {
        int size = ticks.size();
        ReverseListIterator<TickData> iterator = new ReverseListIterator<>(ticks);
        feedTicks(ticksTs, iterator, size);
    }

    private static void feedTicks(BaseTicksTimesSeriesData<TickData> ticksTs, Iterable<TickData> iterator, int size) {
        TimeStamp doneTs = new TimeStamp();
        TimeStamp ts = new TimeStamp();
        int count = 0;
        for (TickData tick : iterator) {
            ticksTs.addNewestTick(tick);
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
