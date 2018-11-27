package bi.two.algo.impl;

import bi.two.calc.SlidingTicksRegressor;
import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.exch.Exchange;
import bi.two.opt.Vary;
import bi.two.ts.*;
import bi.two.util.MapConfig;

import java.util.ArrayList;
import java.util.List;

abstract class BaseRibbonAlgo1 extends BaseRibbonAlgo0 {
    protected final Exchange m_exchange;
    protected final long m_joinTicks;

    protected final float m_turnLevel; // time in bars to confirm turn
    protected final long m_turnSize; // time in millis to confirm turn
    protected final double m_commission;

    protected BaseTimesSeriesData[] m_emas;
    protected int m_emasNum;
    protected final ITimesSeriesData m_wrappedInTs;
    private boolean m_dirty;
    protected Float m_adj = 0F;
    private Boolean m_goUp;
    private float m_maxRibbonSpread;
    private Float m_ribbonSpreadTop;
    private Float m_ribbonSpreadBottom;
    private long m_directionChangeTime;
    private int m_turnsCount;

    @Override public int getTurnsCount() { return m_turnsCount; }

    protected abstract void recalc2(float lastPrice, float emasMin, float emasMax, float leadEmaValue, boolean goUp, boolean directionChanged,
                                    float ribbonSpread, float maxRibbonSpread, float ribbonSpreadTop, float ribbonSpreadBottom);

    BaseRibbonAlgo1(MapConfig algoConfig, ITimesSeriesData inTsd, Exchange exchange) {
        super(null, algoConfig);

        m_exchange = exchange;
        m_joinTicks = algoConfig.getNumber(Vary.joinTicks).longValue();

        m_commission = algoConfig.getNumber(Vary.commission).doubleValue();

        m_turnLevel = algoConfig.getNumber(Vary.turn).floatValue(); // time in bars to confirm turn
        m_turnSize = (long) (m_barSize * m_turnLevel);

        m_wrappedInTs = wrapIfNeededTs(inTsd);

        boolean hasSchedule = exchange.hasSchedule();
        createRibbon(m_wrappedInTs, m_collectValues, hasSchedule);

        setParent(m_wrappedInTs);
    }

    protected ITimesSeriesData wrapIfNeededTs(ITimesSeriesData inTsd) {
        return (m_joinTicks > 0) ? new TickJoinerTimesSeriesData(inTsd, m_joinTicks) : inTsd;
    }

    private Float recalc(float lastPrice, long timestamp) {
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
            Boolean canBeUp = (leadEmaValue == emasMax)
                                ? Boolean.TRUE // go up
                                : ((leadEmaValue == emasMin)
                                    ? Boolean.FALSE // go down
                                    : m_goUp); // do not change
            boolean canDirectionChange = (canBeUp != m_goUp);

            Boolean goUp;
            boolean directionChanged;
            if (m_turnSize > 0) {
                goUp = m_goUp;
                directionChanged = false;
                long directionChangeTime = m_directionChangeTime;
                if (canDirectionChange) {
                    if (directionChangeTime == 0) { // first detected directionChange
                        m_directionChangeTime = timestamp; // just save directionChangeTime
                    } else {
                        long passed = timestamp - directionChangeTime;
                        if (passed >= m_turnSize) { // turn confirmed ?
                            goUp = canBeUp;
                            directionChanged = true;
                            m_directionChangeTime = 0; // reset
                        }
                    }
                } else {
                    if (directionChangeTime != 0) { // directionChange was detected
                        m_directionChangeTime = 0; // but not confirmed - reset
                    }
                }
            } else {
                goUp = canBeUp;
                directionChanged = canDirectionChange;
            }

            if (goUp != null) {
                m_goUp = goUp;

                float ribbonSpread = emasMax - emasMin;
                float maxRibbonSpread;
                if (directionChanged) {
                    maxRibbonSpread = ribbonSpread; //reset
                    m_turnsCount++;
                } else {
                    maxRibbonSpread = (ribbonSpread >= m_maxRibbonSpread) ? ribbonSpread : m_maxRibbonSpread; //  Math.max(ribbonSpread, m_maxRibbonSpread);
                }
                m_maxRibbonSpread = maxRibbonSpread;
                float ribbonSpreadTop = goUp ? emasMin + maxRibbonSpread : emasMax;
                float ribbonSpreadBottom = goUp ? emasMin : emasMax - maxRibbonSpread;
                m_ribbonSpreadTop = ribbonSpreadTop;
                m_ribbonSpreadBottom = ribbonSpreadBottom;

                // todo: check what is faster, to use m_ribbonSpreadTop or pass as param value into functions chain recalc2->recalc3->recalc4
                recalc2(lastPrice, emasMin, emasMax, leadEmaValue, goUp, directionChanged, ribbonSpread, maxRibbonSpread, ribbonSpreadTop, ribbonSpreadBottom);
            }
        }
        return m_adj;
    }

    @Override public void reset() {
        m_goUp = null;
        m_adj = 0F;
        m_maxRibbonSpread = 0f;
        m_ribbonSpreadTop = null;
        m_ribbonSpreadBottom = null;
    }

    private void createRibbon(ITimesSeriesData tsd, boolean collectValues, boolean hasSchedule) {
        ITimesSeriesListener listener = new RibbonTsListener();

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

    @Override public ITickData getAdjusted() {
        if (m_dirty) {
            ITickData parentLatestTick = m_parent.getLatestTick();
            if (parentLatestTick != null) {
                float lastPrice = parentLatestTick.getClosePrice();
                long timestamp = parentLatestTick.getTimestamp();
                Float adj = recalc(lastPrice, timestamp);
                if (adj != null) {
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
        if (m_tickData != null) {
            m_tickData.onTimeShift(shift);
        }
        if (m_directionChangeTime != 0) {
            m_directionChangeTime += shift;
        }
//        super.onTimeShift(shift);
    }


    TicksTimesSeriesData<TickData> getRibbonSpreadTopTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_ribbonSpreadTop; } }; }
    TicksTimesSeriesData<TickData> getRibbonSpreadBottomTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_ribbonSpreadBottom; } }; }
    TicksTimesSeriesData<TickData> getDirectionTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_adj; } }; }


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
