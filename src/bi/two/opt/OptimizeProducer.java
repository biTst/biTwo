package bi.two.opt;

import bi.two.algo.Watcher;
import bi.two.exch.Exchange;
import bi.two.exch.Pair;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.util.MapConfig;

import java.util.List;

public abstract class OptimizeProducer extends WatchersProducer.BaseProducer implements Runnable {
    final List<OptimizeConfig> m_optimizeConfigs;
    final MapConfig m_algoConfig;
    private Thread m_thread;
    protected Object m_sync = new Object();
    State m_state = State.optimizerCalculation;
    double m_totalPriceRatio;
    private WatchersProducer.RegressionAlgoWatcher m_lastWatcher;


    public OptimizeProducer(List<OptimizeConfig> optimizeConfigs, MapConfig algoConfig) {
        m_optimizeConfigs = optimizeConfigs;
        m_algoConfig = algoConfig.copy();
    }

    void startThread() {
        m_thread = new Thread(this);
        m_thread.start();
    }

    @Override public boolean getWatchers(MapConfig algoConfig, BaseTimesSeriesData ticksTs, Exchange exchange, Pair pair, List<Watcher> watchers) {
        synchronized (m_sync) {
            while (m_state == State.optimizerCalculation) {
                try {
                    m_sync.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (m_state == State.finished) {
                return false;
            }
        }
        m_lastWatcher = new WatchersProducer.RegressionAlgoWatcher(m_algoConfig, exchange, pair, ticksTs) {
            @Override public void notifyFinished() {
                super.notifyFinished();
                m_totalPriceRatio = totalPriceRatio();
                synchronized (m_sync) {
                    m_sync.notify();
                }
            }
        };
        watchers.add(m_lastWatcher);

        return true;
    }

    @Override public void logResults() {
        System.out.println("OptimizeProducer result: " + m_lastWatcher + "; m_totalPriceRatio=" + m_totalPriceRatio);
    }

    //--------------------------------------------------------------------------
    protected enum State {
        optimizerCalculation,
        waitingResult,
        finished
    }
}
