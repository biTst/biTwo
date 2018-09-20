package bi.two.ts;

import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.util.MapConfig;

import java.io.IOException;
import java.util.Comparator;
import java.util.Iterator;

import static bi.two.util.Log.err;

// -------------------------------------------------------------------------------
public class TradesWriterTicksTs extends BaseTicksTimesSeriesData<TickData> {
    private final TradesWriter m_tradesWriter;
    private final BaseTicksTimesSeriesData<TickData> m_proxyTicksTs;
    private final TradesWriter.IInstance m_instance;

    public TradesWriterTicksTs(BaseTicksTimesSeriesData<TickData> proxyTicksTs, TradesWriter tradesWriter, MapConfig config) {
        super(null);
        m_proxyTicksTs = proxyTicksTs;
        m_tradesWriter = tradesWriter;
        m_instance = tradesWriter.instance(config);
    }

    public void addNewestTick(TickData tickData) {
        try {
            m_tradesWriter.writeTick(m_instance, tickData);
        } catch (IOException e) {
            err("error writing tick: " + tickData + " : " + e, e);
        }
        m_proxyTicksTs.addNewestTick(tickData);
    }

    @Override public void notifyNoMoreTicks() {
        try {
            m_instance.close();
        } catch (IOException e) {
            err("error closing ticks writer : " + e, e);
        }
        m_proxyTicksTs.notifyNoMoreTicks();
    }

    public void addOlderTick(TickData tickData) {
        throw new RuntimeException("not implemented");
    }

    @Override public TickData getTick(int index) {
        throw new RuntimeException("not implemented");
    }

    @Override public Iterator<TickData> getTicksIterator() {
        throw new RuntimeException("not implemented");
    }

    @Override public Iterable<TickData> getTicksIterable() {
        throw new RuntimeException("not implemented");
    }

    @Override public Iterator<TickData> getReverseTicksIterator() {
        throw new RuntimeException("not implemented");
    }

    @Override public Iterable<TickData> getReverseTicksIterable() {
        throw new RuntimeException("not implemented");
    }

    @Override public int getTicksNum() {
        throw new RuntimeException("not implemented");
    }

    @Override public Object syncObject() {
        throw new RuntimeException("not implemented");
    }

    @Override public int binarySearch(TickData o, Comparator<ITickData> comparator) {
        throw new RuntimeException("not implemented");
    }
}
