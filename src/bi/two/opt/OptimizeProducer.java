package bi.two.opt;

import bi.two.algo.Watcher;
import bi.two.exch.Exchange;
import bi.two.exch.Pair;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.util.MapConfig;

import java.util.List;

public abstract class OptimizeProducer implements WatchersProducer.IProducer, Runnable {
    final List<OptimizeConfig> m_optimizeConfigs;
    final MapConfig m_algoConfig;
    private Thread m_thread;
    State m_state = State.optimizerCalculation;
    double m_totalPriceRatio;


    public OptimizeProducer(List<OptimizeConfig> optimizeConfigs, MapConfig algoConfig) {
        m_optimizeConfigs = optimizeConfigs;
        m_algoConfig = algoConfig.copy();
    }

    void startThread() {
        m_thread = new Thread(this);
        m_thread.start();
    }

    @Override public boolean getWatchers(MapConfig algoConfig, BaseTimesSeriesData ticksTs, Exchange exchange, Pair pair, List<Watcher> watchers) {
        synchronized (this) {
            while (m_state == State.optimizerCalculation) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (m_state == State.finished) {
                return false;
            }
        }
        Watcher watcher = new WatchersProducer.RegressionAlgoWatcher(m_algoConfig, exchange, pair, ticksTs) {
            @Override public void notifyFinished() {
                super.notifyFinished();
                m_totalPriceRatio = totalPriceRatio();
                synchronized (OptimizeProducer.this) {
                    OptimizeProducer.this.notify();
                }
            }
        };
        watchers.add(watcher);

        return true;
    }


    //--------------------------------------------------------------------------
    protected enum State {
        optimizerCalculation,
        waitingResult,
        finished
    }
}
