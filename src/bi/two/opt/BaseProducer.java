package bi.two.opt;

import bi.two.algo.Watcher;
import bi.two.exch.Exchange;
import bi.two.exch.Pair;
import bi.two.telegram.TheBot;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.util.MapConfig;

import java.util.List;

public abstract class BaseProducer {
    boolean m_active = true;

    public boolean isActive() { return m_active; }

    abstract void getWatchers(MapConfig config, MapConfig algoConfig, BaseTimesSeriesData ticksTs, Exchange exchange, Pair pair, List<Watcher> watchers);

    public abstract double logResults(int pad);

    public void logResultsEx(TheBot theBot, String botKey) { }

    public abstract boolean isSingle();

    public double maxTotalPriceRatio() { throw new RuntimeException("not implemented"); }

    public int logKeyWidth() { return 0; }
}
