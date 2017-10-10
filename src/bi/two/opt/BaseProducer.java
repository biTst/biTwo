package bi.two.opt;

import bi.two.algo.Watcher;
import bi.two.exch.Exchange;
import bi.two.exch.Pair;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.util.MapConfig;

import java.util.List;

public abstract class BaseProducer {
    boolean m_active = true;

    public boolean isActive() { return m_active; }

    abstract void getWatchers(MapConfig algoConfig, BaseTimesSeriesData ticksTs, Exchange exchange, Pair pair, List<Watcher> watchers);

    public abstract double logResults();

    public void logResultsEx() { }
}