package bi.two.opt;

import bi.two.algo.Watcher;
import bi.two.exch.Exchange;
import bi.two.exch.Pair;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import bi.two.util.MapConfig;

import java.util.List;

public abstract class OptimizeProducer extends BaseProducer implements Runnable {
    final List<OptimizeConfig> m_optimizeConfigs;
    final MapConfig m_algoConfig;
    private Thread m_thread;
    final Object m_sync = new Object();
    State m_state = State.optimizerCalculation;
    double m_onFinishTotalPriceRatio;
    WatchersProducer.AlgoWatcher m_lastWatcher;


    public OptimizeProducer(List<OptimizeConfig> optimizeConfigs, MapConfig algoConfig) {
        m_optimizeConfigs = optimizeConfigs;
        m_algoConfig = algoConfig.copy();
    }

    void startThread() {
        m_thread = new Thread(this);
        m_thread.setPriority(Thread.NORM_PRIORITY - 1); // smaller prio
        m_thread.start();
    }

    @Override public void getWatchers(MapConfig algoConfig, BaseTimesSeriesData ticksTs, Exchange exchange, Pair pair, List<Watcher> watchers) {
        synchronized (m_sync) {
            while (m_state == State.optimizerCalculation) {
                try {
                    m_sync.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (m_state == State.finished) {
                return;
            }
        }
        m_lastWatcher = new WatchersProducer.AlgoWatcher(m_algoConfig, exchange, pair, ticksTs);
        m_lastWatcher.addListener(new ITimesSeriesData.ITimesSeriesListener() {
            @Override public void onChanged(ITimesSeriesData ts, boolean changed) { }
            @Override public void waitWhenFinished() { }
            @Override public void notifyFinished() {
                m_onFinishTotalPriceRatio = m_lastWatcher.totalPriceRatio();
                synchronized (m_sync) {
                    m_sync.notify();
                }
            }
        });
        watchers.add(m_lastWatcher);
    }

    @Override public double logResults() {
        System.out.println("OptimizeProducer result: " + m_lastWatcher + "; m_totalPriceRatio=" + m_onFinishTotalPriceRatio);
        return m_onFinishTotalPriceRatio;
    }

    //--------------------------------------------------------------------------
    protected enum State {
        optimizerCalculation,
        waitingResult,
        finished
    }
}
