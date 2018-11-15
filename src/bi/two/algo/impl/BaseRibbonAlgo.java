package bi.two.algo.impl;

import bi.two.algo.BaseAlgo;
import bi.two.calc.SlidingTicksRegressor;
import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.exch.Exchange;
import bi.two.opt.Vary;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.ITimesSeriesListener;
import bi.two.ts.TickJoinerTimesSeriesData;
import bi.two.util.MapConfig;

import java.util.ArrayList;
import java.util.List;

abstract class BaseRibbonAlgo extends BaseAlgo<TickData> {
    protected final Exchange m_exchange;
    protected final long m_joinTicks;

    protected final float m_start;
    protected final float m_step;
    protected final float m_count;
    protected final long m_barSize;
    protected final float m_linRegMultiplier;
    protected final boolean m_collectValues;
    protected final double m_commission;

    protected BaseTimesSeriesData[] m_emas;
    protected int m_emasNum;
    protected final ITimesSeriesData m_wrappedInTs;
    private boolean m_dirty;
    private TickData m_tickData;
    protected Float m_adj;

    protected abstract void recalc2(float lastPrice, float emasMin, float emasMax, float leadEmaValue);

    BaseRibbonAlgo(MapConfig algoConfig, ITimesSeriesData inTsd, Exchange exchange) {
        super(null);

        m_exchange = exchange;
        m_joinTicks = algoConfig.getNumber(Vary.joinTicks).longValue();

        m_start = algoConfig.getNumber(Vary.start).floatValue();
        m_step = algoConfig.getNumber(Vary.step).floatValue();
        m_count = algoConfig.getNumber(Vary.count).floatValue();
        m_barSize = algoConfig.getNumber(Vary.period).longValue();
        m_collectValues = algoConfig.getBoolean(BaseAlgo.COLLECT_VALUES_KEY);
        m_linRegMultiplier = algoConfig.getNumber(Vary.multiplier).floatValue();
        m_commission = algoConfig.getNumber(Vary.commission).doubleValue();

        m_wrappedInTs = wrapIfNeededTs(inTsd);

        boolean hasSchedule = exchange.hasSchedule();
        createRibbon(m_wrappedInTs, m_collectValues, hasSchedule);

        setParent(m_wrappedInTs);
    }

    protected ITimesSeriesData wrapIfNeededTs(ITimesSeriesData inTsd) {
        return (m_joinTicks > 0) ? new TickJoinerTimesSeriesData(inTsd, m_joinTicks) : inTsd;
    }

    private Float recalc(float lastPrice) {
        float emasMin = Float.POSITIVE_INFINITY;
        float emasMax = Float.NEGATIVE_INFINITY;
        boolean allDone = true;
        float leadEmaValue = 0;
        for (int i = 0; i < m_emasNum; i++) {
            ITimesSeriesData ema = m_emas[i];
            ITickData lastTick = ema.getLatestTick();
            if (lastTick != null) {
                float value = lastTick.getClosePrice();
                emasMin = (emasMin <= value) ? emasMin : value;  // Math.min(emasMin, value);
                emasMax = (emasMax >= value) ? emasMax : value;  // Math.max(emasMax, value);
                if (i == 0) {
                    leadEmaValue = value;
                }
            } else {
                allDone = false;
                break; // not ready yet
            }
        }

        if (allDone) {
            recalc2(lastPrice, emasMin, emasMax, leadEmaValue);
        }
        return m_adj;
    }

    @Override public void reset() {
        m_adj = 0F;
    }

    private void createRibbon(ITimesSeriesData tsd, boolean collectValues, boolean hasSchedule) {

        ITimesSeriesListener listener = new RibbonTsListener();

//        long period = (long) (m_start * m_barSize * m_linRegMultiplier);
//        SlidingTicksRegressor sliding0 = new SlidingTicksRegressor(tsd, period, false);
//        m_sliding = new TicksSMA(sliding0, m_barSize);

        List<BaseTimesSeriesData> list = new ArrayList<>();
        float length = m_start;
        int len = (int) m_count;
        for (int i = 0; i < len; i++) {
            BaseTimesSeriesData ema = getOrCreateEma(tsd, m_barSize, length, collectValues, hasSchedule);
            ema.getActive().addListener(listener);
            list.add(ema);
            length += m_step;  // todo: add progressive step
        }
        if (m_count != len) {
            float fraction = m_count - len;
            float fractionLength = length - m_step + (m_step * fraction);
            BaseTimesSeriesData ema = getOrCreateEma(tsd, m_barSize, fractionLength, collectValues, hasSchedule);
            ema.getActive().addListener(listener);
            list.add(ema);
            len++;
        }

        m_emasNum = len;
        m_emas = list.toArray(new BaseTimesSeriesData[len]);
    }

    private BaseTimesSeriesData getOrCreateEma(ITimesSeriesData tsd, long barSize, float length, boolean collectValues, boolean hasSchedule) {
        long period = (long) (length * barSize * m_linRegMultiplier);
        return new SlidingTicksRegressor(tsd, period, false, hasSchedule);

//        return new BarsRegressor(tsd, (int) length, (long) (barSize * m_linRegMultiplier), m_linRegMultiplier*5);

//        BarsRegressor r = new BarsRegressor(tsd, (int) length, (long) (barSize * m_linRegMultiplier), m_linRegMultiplier * 5);
//        return new TicksSMA(r, m_barSize/2);

//        return new BarsEMA(tsd, length, barSize);
//        return new BarsDEMA(tsd, length, barSize);
//        return new BarsTEMA(tsd, length, barSize);
    }

    @Override public TickData getLatestTick() {
        return m_tickData;
    }

    @Override public ITickData getAdjusted() {
        if (m_dirty) {
            ITickData parentLatestTick = m_parent.getLatestTick();
            if (parentLatestTick != null) {
                float lastPrice = parentLatestTick.getClosePrice();
                Float adj = recalc(lastPrice);
                if (adj != null) {
                    long timestamp = parentLatestTick.getTimestamp();
                    m_tickData = new TickData(timestamp, adj); // todo: every time here new object, even if value is not changed
                    m_dirty = false;
                    return m_tickData;
                }
                // else - not ready yet
            }
        }
        return m_tickData;
    }

    @Override public void onTimeShift(long shift) {
        // todo: call super
        notifyOnTimeShift(shift);
        m_tickData.onTimeShift(shift);
//        super.onTimeShift(shift);
    }


    //-------------------------------------------------------------------------------------------
    private class RibbonTsListener implements ITimesSeriesListener {
        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            if (changed) {
                m_dirty = true;
            }
        }

        @Override public void waitWhenAllFinish() { }
        @Override public void notifyNoMoreTicks() {}
        @Override public void onTimeShift(long shift) { /*noop*/ }
    }
}
