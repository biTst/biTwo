package bi.two.algo.impl;

import bi.two.ChartCanvas;
import bi.two.algo.BaseAlgo;
import bi.two.algo.Watcher;
import bi.two.calc.ExponentialMovingBarAverager;
import bi.two.calc.FadingTicksAverager;
import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.opt.Vary;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TimesSeriesData;
import bi.two.util.MapConfig;

public class EmaTrendAlgo extends BaseAlgo {
    private final long m_barSize;
    private final int m_length;
    private final ExponentialMovingBarAverager m_ema;
    private final FadingTicksAverager m_emaShort;
    private final Adjuster m_adjuster;

    public EmaTrendAlgo(MapConfig config, ITimesSeriesData tsd) {
        super(null);

        m_barSize = config.getNumber(Vary.period).longValue();
        m_length = config.getNumber(Vary.bars).intValue();

        m_ema = new ExponentialMovingBarAverager(tsd, m_length, m_barSize);
        m_emaShort = new FadingTicksAverager(tsd, 2 * m_barSize);

        m_adjuster = new Adjuster(m_ema, m_emaShort);
        m_adjuster.addListener(this);
    }

    @Override public void setupChart(boolean collectValues, ChartCanvas chartCanvas, TimesSeriesData ticksTs, Watcher firstWatcher) {
        // ...
    }

    //----------------------------------------------------------
    public static class Adjuster extends BaseTimesSeriesData<ITickData> {
        private final BaseTimesSeriesData<ITickData> m_ema;
        private final FadingTicksAverager m_emaShort;
        private boolean m_dirty;
        private TickData m_tickData;

        Adjuster(BaseTimesSeriesData<ITickData> ema, FadingTicksAverager emaShort) {
            super(null);
            m_ema = ema;
            m_emaShort = emaShort;

            ema.getActive().addListener(this);
            emaShort.getActive().addListener(this);
        }

        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            if (changed) {
                m_dirty = true;
            }
            super.onChanged(this, changed); // notifyListeners
        }


        @Override public ITickData getLatestTick() {
            if (m_dirty) {
                ITickData latestEma = m_ema.getLatestTick();
                if(latestEma != null) {
                    ITickData latestEmaShort = m_emaShort.getLatestTick();
                    if(latestEmaShort != null) {
                        float ema = latestEma.getClosePrice();
                        float emaShort = latestEmaShort.getClosePrice();
                        float diff = emaShort - ema;
                        long timestamp = Math.max(latestEma.getTimestamp(), latestEmaShort.getTimestamp());
                        m_tickData = new TickData(timestamp, diff);
                        m_dirty = false;
                    }
                }
            }
            return m_tickData;
        }
    }
}