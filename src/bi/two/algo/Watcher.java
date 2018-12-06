package bi.two.algo;

import bi.two.chart.ITickData;
import bi.two.chart.JoinNonChangedTimesSeriesData;
import bi.two.chart.TickData;
import bi.two.chart.TradeData;
import bi.two.exch.*;
import bi.two.exch.schedule.TradeHours;
import bi.two.exch.schedule.TradeSchedule;
import bi.two.opt.Vary;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TicksTimesSeriesData;
import bi.two.util.MapConfig;
import bi.two.util.Utils;

import java.util.concurrent.TimeUnit;

import static bi.two.algo.BaseAlgo.COLLECT_VALUES_KEY;
import static bi.two.util.Log.*;

public class Watcher extends TicksTimesSeriesData<TradeData> {
    private static final boolean LOG_MOVE = false;
    public static final boolean MONOTONE_TIME_INCREASE_CHECK = STRICT_MONOTONE_TIME_INCREASE_CHECK;

    private static final long FADE_OUT_EOD_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final long FADE_IN_BOD_MILLIS = TimeUnit.MINUTES.toMillis(5);

    private static final boolean DO_FADE_IN_OUT = true; // this is fr data gaps (no ticks available)
    private static final long MIN_GAP_TO_FADE_OUT = TimeUnit.MINUTES.toMillis(1); // start fade out after 1 min
    private static final long FADE_OUT_TIME = TimeUnit.MINUTES.toMillis(20); // fade-out time
    private static final long FADE_IN_TIME = TimeUnit.MINUTES.toMillis(5); // fade-in algo time

    private static int s_orderCounter;

    protected final Exchange m_exch;
    private final Pair m_pair;
    private final ExchPairData m_exchPairData;
    private final double m_commission;
    public final BaseAlgo m_algo;
    private final boolean m_collectValues;
    private final ITimesSeriesData<TickData> m_priceTs;
    private final CurrencyValue m_exchMinOrderToCreate;
    private final boolean m_priceAtSameTick; // apply price from same tick or from the next
    private final boolean m_debugTrades; // paint debug info about trades on chart
    private final boolean m_logGaps;
    private final double m_minOrderMul;
    private final boolean m_hasSchedule;
    private AccountData m_initAcctData;
    private AccountData m_accountData;
    public TopData m_topData = new TopData(0,0,0);
    private TopData m_initTopData;
    private double m_valuateToInit;
    private double m_valuateFromInit;
    private long m_startMillis;
    private long m_lastMillis;
    public int m_tradesNum;
    public double m_tradesSum;
    private ITickData m_savedAdjusted; // saved direction from last tick processing
    private long m_nextTradeCloseTime;
    private float m_lastDirection = 0;
    private float m_lastDirectionWithFade = 0;
    private Boolean m_isUp;
    private int m_changedDirection; // counter

    private TickData m_firstTick;
    private TickData m_lastTick;
    private float m_fadeOutRate = DO_FADE_IN_OUT ? 1f : 0f; // fully faded out at start
    private float m_fadeInRate = 0; // fade in as trades comes

    private TradeHours m_currTradeHours;
    private long m_tradeEndMillis;
    private final TradeSchedule m_tradeSchedule;
    private float m_scheduleFadeOutRate = 1.0f;

    public Watcher(MapConfig config, MapConfig algoConfig, Exchange exch, Pair pair, ITimesSeriesData<TickData> priceTs) {
        super(null);

        m_priceTs = priceTs;
        m_exch = exch;
        m_hasSchedule = m_exch.hasSchedule();
        m_tradeSchedule = new TradeSchedule(exch.m_schedule);
        m_pair = pair;
        m_exchPairData = exch.getPairData(pair);
        Number theCommission = algoConfig.getNumberOrNull(Vary.commission);
        if (theCommission != null) {
            double commission = theCommission.doubleValue();
            if (!Double.isInfinite(commission)) { // override from local config
                m_commission = commission;
            } else {
                m_commission = m_exchPairData.m_commission;
            }
        } else {
            m_commission = m_exchPairData.m_commission;
        }
        m_priceAtSameTick = config.getBooleanOrDefault("priceAtSameTick", Boolean.FALSE); // by def - use price from next tick
        m_debugTrades = config.getBooleanOrDefault("debugTrades", Boolean.FALSE);
        m_logGaps = config.getBooleanOrDefault("logGaps", Boolean.FALSE); // do not log by def
        m_exchMinOrderToCreate = m_exchPairData.m_minOrderToCreate;

        m_minOrderMul = algoConfig.getNumber(Vary.minOrderMul).doubleValue();
        m_collectValues = algoConfig.getBoolean(COLLECT_VALUES_KEY);
        m_algo = createAlgo(priceTs, algoConfig);
        setParent(m_algo);
    }

    protected BaseAlgo createAlgo(ITimesSeriesData parent, MapConfig config) {
        return null;
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        // note, here changed are not checked = run on ever tick, to recheck int state often

        TickData latestPriceTick = m_priceTs.getLatestTick();
        if (latestPriceTick == null) { // todo: check, do we really have such case ?
            return; // skip
        }
        long currTimestamp = latestPriceTick.getTimestamp();

        if (DO_FADE_IN_OUT) { // fade out if no ticks for long time
            if (m_lastTick != null) {
                long lastTimestamp = m_lastTick.getTimestamp();
                long gap = currTimestamp - lastTimestamp;

                if (gap > MIN_GAP_TO_FADE_OUT) { // we had no ticks some dangerous amount of time. start fading out
                    float fadeOutRate = ((float) (gap - MIN_GAP_TO_FADE_OUT)) / FADE_OUT_TIME;
                    fadeOutRate = (1 <= fadeOutRate) ? 1 : fadeOutRate;  //  Math.min(1, fadeOutRate); // [0->1]
                    m_fadeOutRate = 1 - (1 - fadeOutRate) * (1 - m_fadeOutRate);
                    if (m_logGaps) {
                        log("got GAP: " + Utils.millisToYDHMSStr(gap) + "; fadeOutRate=" + fadeOutRate + "; total fadeOutRate=" + m_fadeOutRate);
                    }
                    m_fadeInRate = 0;
                    processWithFade(m_lastDirection);
                    m_firstTick = null; // reset first tick

                    if (m_fadeOutRate == 1) { // full fade out
                        m_algo.reset();
                    }
                }
            }

            if (m_firstTick != null) {
                long firstTimestamp = m_firstTick.getTimestamp();
                long gap = currTimestamp - firstTimestamp;
                float fadeInRate = ((float) gap) / FADE_IN_TIME;
                fadeInRate = (1 <= fadeInRate) ? 1 : fadeInRate;  // Math.min(1, fadeInRate); // [0 -> 1]
                if (MONOTONE_TIME_INCREASE_CHECK) {
                    if (fadeInRate < 0) {
                        throw new RuntimeException("negative fadeInRate=" + fadeInRate);
                    }
                }
                m_fadeInRate = fadeInRate;
                if (fadeInRate == 1) {
                    m_fadeOutRate = 0; // no fade out - trades are flowing fine
                }
            } else {
                m_firstTick = m_hasSchedule ? new TickData(latestPriceTick) : latestPriceTick;
            }
        }

        if (m_hasSchedule) {
            if (m_tradeEndMillis < currTimestamp) {
                TradeHours nextTradeHours = m_tradeSchedule.getTradeHours(currTimestamp);
                boolean inside = nextTradeHours.isInsideOfTradingHours(currTimestamp);
                if (!inside) {
                    String dateTime = m_tradeSchedule.formatLongDateTime(currTimestamp);
                    throw new RuntimeException("next trade is not inside of trading day: dateTime=" + dateTime + "; nextTradeHours=" + nextTradeHours);
                }
                if (m_currTradeHours != null) {
                    long tradePause = nextTradeHours.m_tradeStartMillis - m_tradeEndMillis;
                    if (tradePause < 0) {
                        String dateTime = m_tradeSchedule.formatLongDateTime(currTimestamp);
                        throw new RuntimeException("negative tradePause=" + tradePause + "; dateTime=" + dateTime + "; currTradeHours=" + m_currTradeHours + "; nextTradeHours=" + nextTradeHours);
                    }
                }
                m_currTradeHours = nextTradeHours;
                m_tradeEndMillis = nextTradeHours.m_tradeEndMillis;
            }

            long tradeStartMillis = m_currTradeHours.m_tradeStartMillis;
            long passedFromStart = currTimestamp - tradeStartMillis;
            if (passedFromStart < FADE_IN_BOD_MILLIS) {
                float rate = ((float) passedFromStart) / FADE_OUT_EOD_MILLIS;
                if (rate < 0) {
                    rate = 0;
                }
                m_scheduleFadeOutRate = rate;
            } else {
                long tradeEndMillis = m_currTradeHours.m_tradeEndMillis;
                long missedTillEnd = tradeEndMillis - currTimestamp;
                if (missedTillEnd < FADE_OUT_EOD_MILLIS) {
                    float rate = ((float) missedTillEnd) / FADE_OUT_EOD_MILLIS;
                    if (rate < 0) {
                        rate = 0;
                    }
                    m_scheduleFadeOutRate = rate;
                } else {
                    m_scheduleFadeOutRate = 1.0f;
                }
            }
        }

        m_lastTick = m_hasSchedule ? new TickData(latestPriceTick) : latestPriceTick;

        float closePrice = latestPriceTick.getClosePrice();
        m_topData.m_last = closePrice;
        m_topData.m_bid = closePrice;
        m_topData.m_ask = closePrice;

        if (m_initAcctData == null) { // very first tick
            init(currTimestamp);
        } else {
            if (m_priceAtSameTick) {
                ITickData adjusted = m_algo.getAdjusted();
                if (adjusted != null) {
                    try {
                        process(adjusted);
                    } catch (AccountData.AccountMoveException ame) {
                        err("acct error: " + ame, ame);
                        adjusted = m_algo.getAdjusted();
                    }
                }
            } else {
                if (m_savedAdjusted != null) { // process delayed first
                    process(m_savedAdjusted);
                }
                ITickData adjusted = m_algo.getAdjusted();
                m_savedAdjusted = adjusted; // save to process on next tick
            }
        }
    }

    private void process(ITickData tickAdjusted) {
        float direction = tickAdjusted.getClosePrice(); // UP/DOWN
        m_lastDirection = direction;
        processWithFade(direction);
    }

    private void processWithFade(float directionIn) {
        float directionWithFade = applyFadeRate(directionIn);

        if (m_lastDirectionWithFade == directionWithFade) {
            return; // todo: do not process same value twice in simulation
        }
        m_lastDirectionWithFade = directionWithFade;

        if ((m_isUp == null) || (m_isUp && (directionWithFade == -1)) || (!m_isUp && (directionWithFade == 1))) {
            m_isUp = (directionWithFade > 0);
            m_changedDirection++;
        }

        process(directionWithFade);
    }

    private float applyFadeRate(float directionIn) {
        float direction;
        if (DO_FADE_IN_OUT) {
            float fadeRate = (1 - m_fadeOutRate) + (m_fadeOutRate * m_fadeInRate);
            direction = directionIn * fadeRate;
        } else {
            direction = directionIn;
        }
        direction *= m_scheduleFadeOutRate;
        return direction;
    }

    private void process(float directionWithFade) {
        boolean toLog = m_debugTrades;

        Currency currencyFrom = m_pair.m_from;
        Currency currencyTo = m_pair.m_to;

        if (toLog) {
            double acctDirection = m_accountData.calcDirection(m_pair);
            console("Watcher.process() direction=" + Utils.format8((double) directionWithFade) + "; acctDirection=" + Utils.format8(acctDirection));
        }
        double needBuyTo = m_accountData.calcNeedBuyTo(m_pair, directionWithFade);
        double needSellFrom = m_accountData.convert(currencyTo, currencyFrom, needBuyTo);

        if (toLog) { console(" needBuy=" + Utils.format8(needBuyTo) + " " + currencyTo.m_name + "; needSell=" + Utils.format8(needSellFrom) + " " + currencyFrom.m_name); }

        needBuyTo *= 0.98; // leave some pennies on account

        double absOrderSize = Math.abs(needBuyTo);
        OrderSide needOrderSide = (needBuyTo >= 0) ? OrderSide.SELL : OrderSide.BUY;
        if (toLog) { console("   needOrderSide=" + needOrderSide + "; absOrderSize=" + Utils.format8(absOrderSize)); }

        double exchMinOrderToCreateValue = m_exchMinOrderToCreate.m_value;
        if (m_exchMinOrderToCreate.m_currency != currencyTo) {
            exchMinOrderToCreateValue = m_accountData.convert(currencyFrom, currencyTo, exchMinOrderToCreateValue);
        }

        exchMinOrderToCreateValue = exchMinOrderToCreateValue * m_minOrderMul;
        TickData latestPriceTick = m_priceTs.getLatestTick();
        long timestamp = latestPriceTick.getTimestamp();
        if (absOrderSize >= exchMinOrderToCreateValue) {

            boolean toLogMove = LOG_MOVE || toLog;
            if (toLogMove) {
                console("Watcher.process(" + s_orderCounter + ") direction=" + Utils.format8((double) directionWithFade)
                        + "; needBuy=" + Utils.format8(needBuyTo) + " " + currencyTo.m_name
                        + "; needSell=" + Utils.format8(needSellFrom) + " " + currencyFrom.m_name
                        + "; needOrderSide=" + needOrderSide + "; absOrderSize=" + Utils.format8(absOrderSize));
            }

            m_accountData.move(m_pair, needBuyTo, m_commission);
            m_tradesNum++;
            m_tradesSum += Math.abs(needBuyTo);

            if (toLogMove) {
                double acctDirection = m_accountData.calcDirection(m_pair);
                double gain = totalPriceRatio(true);
                console("    trade[" + m_tradesNum + "]: gain: " + Utils.format8(gain) + "; acctDirection=" + Utils.format8(acctDirection) + " .....................................");
            }

            if (m_collectValues) {
                double price = latestPriceTick.getClosePrice();
                TradeData tradeData = m_debugTrades
                        ? new TradeData.DebugTradeData(timestamp, (float) price, (float) needBuyTo, needOrderSide,
                            "o" + s_orderCounter + " d" + Utils.format8((double)directionWithFade) + " n" + Utils.format8(needBuyTo)
                            )
                        : new TradeData(timestamp, (float) price, (float) needBuyTo, needOrderSide);
                addNewestTick(tradeData);
            }
            s_orderCounter++;
        }
        m_lastMillis = timestamp;
    }

    public long getProcessedPeriod() {
        return m_lastMillis - m_startMillis;
    }

    public String getProcessedPeriodStr() {
        return "[startMillis:" + m_startMillis + " lastMillis:" + m_lastMillis + "]";
    }

    public double totalPriceRatio() {
        return totalPriceRatio(false);
    }

    public double totalPriceRatio(boolean toLog) {
        if (m_accountData == null) { // error
            return 1;
        }
        Currency currencyFrom = m_pair.m_from; // cnh=from
        Currency currencyTo = m_pair.m_to;     // btc=to
        double valuateToNow = m_accountData.evaluateAll(currencyTo);
        double valuateFromNow = m_accountData.evaluateAll(currencyFrom);

        double gainTo = valuateToNow / m_valuateToInit;
        double gainFrom = valuateFromNow / m_valuateFromInit;

        double gainAvg = (gainTo + gainFrom) / 2;

        if (toLog) {
            if (LOG_MOVE || m_debugTrades) {
                console("totalPriceRatio() accountData=" + m_accountData
                        + "; from=" + currencyFrom.m_name
                        + ": valuateInit=" + m_valuateFromInit
                        + ", valuateNow=" + valuateFromNow
                        + ", gain=" + gainFrom
                        + "; to=" + currencyTo.m_name
                        + ": valuateToInit=" + m_valuateToInit
                        + ", valuateToNow=" + valuateToNow
                        + ", gainTo=" + gainTo
                        + "; gainAvg=" + gainAvg);
            }
        }
        return gainAvg;
    }


    private void init(long currTimestamp) {
        m_startMillis = currTimestamp;
        if (m_debugTrades) {
            console("init() topData = " + m_topData);
        }

        m_initTopData = new TopData(m_topData);
        double lastPrice = m_topData.m_last;

        Currency currencyFrom = m_pair.m_from;
        Currency currencyTo = m_pair.m_to;

        m_initAcctData = new AccountData(m_exch);
        m_initAcctData.m_topDatas.put(m_pair, m_initTopData);

        double initBalance = m_exchPairData.m_initBalance;
        m_initAcctData.setAvailable(currencyFrom, initBalance); // like btc in btc_usd pair
        m_initAcctData.setAvailable(currencyTo, initBalance * lastPrice);

        m_accountData = m_initAcctData.copy();
        m_accountData.m_topDatas.put(m_pair, m_topData);

        m_valuateToInit = m_initAcctData.evaluateAll(currencyTo);
        m_valuateFromInit = m_initAcctData.evaluateAll(currencyFrom);
        if (m_debugTrades) {
            console(" valuate[" + currencyTo.m_name + "]=" + m_valuateToInit + "; valuate[" + currencyFrom.name() + "]=" + m_valuateFromInit);
        }
    }

    @Override public void onTimeShift(long shift) {
//        super.onTimeShift(shift);
        if (m_lastTick != null) {
            m_lastTick = m_lastTick.newTimeShifted(shift);
        }
        notifyOnTimeShift(shift);
    }

    @Override protected void notifyOnTimeShift(long shift) {
        m_lastTick.onTimeShift(shift);
        m_firstTick.onTimeShift(shift);
        if (m_savedAdjusted != null) {
            m_savedAdjusted.onTimeShift(shift);
        }
        super.notifyOnTimeShift(shift);
    }

    public String toLog() {
        return "Watcher["
                + "\n ticksNum=" + getTicksNum()
                + "\n]";
    }

    public TicksTimesSeriesData<TickData> getGainTs() { return new GainTimesSeriesData(this); }
    public TicksTimesSeriesData<TickData> getFadeOutTs() { return new FadeOutTimesSeriesData(m_priceTs); }
    public TicksTimesSeriesData<TickData> getFadeInTs() { return new FadeInTimesSeriesData(m_priceTs); }

    public double getAvgTradeSize() {
        return (m_tradesNum == 0) ? 0 : m_tradesSum / m_tradesNum;
    }

    public String getGainLogStr(String prefix, double gain) {
        String key = m_algo.key(false);

        long processedPeriod = getProcessedPeriod();
//        String processedPeriodStr = getProcessedPeriodStr();

        int turns = m_algo.getTurnsCount();
        if(turns == 0 ) {
            turns = m_changedDirection;
        }

        return prefix + "GAIN[" + key + "]: " + Utils.format8(gain)
                + "   trades=" + m_tradesNum
                + "; avgTrade=" + Utils.format5(this.getAvgTradeSize())
                + "; turns=" + turns
                + "; processedPeriod=" + Utils.millisToYDHMSStr(processedPeriod)
//                + "  " + processedPeriodStr
                + " .....................................";
    }


    //----------------------------------------------------------
    public class GainTimesSeriesData extends JoinNonChangedTimesSeriesData {
        GainTimesSeriesData(ITimesSeriesData parent) {
            super(parent);
        }

        @Override protected ITickData getTickValue() {
            TradeData latestTick = Watcher.this.getLatestTick();
            if (latestTick != null) {
                long timestamp = latestTick.getTimestamp();
                double ratio = totalPriceRatio();
                return new TickData(timestamp, (float) ratio);
            }
            return null;
        }
    }


    //----------------------------------------------------------
    public class FadeOutTimesSeriesData extends JoinNonChangedTimesSeriesData {
        FadeOutTimesSeriesData(ITimesSeriesData parent) {
            super(parent);
        }

        @Override protected ITickData getTickValue() {
            TradeData latestTick = Watcher.this.getLatestTick();
            if (latestTick != null) {
                long timestamp = latestTick.getTimestamp();
                return new TickData(timestamp, m_fadeOutRate);
            }
            return null;
        }
    }


    //----------------------------------------------------------
    public class FadeInTimesSeriesData extends JoinNonChangedTimesSeriesData {
        FadeInTimesSeriesData(ITimesSeriesData parent) {
            super(parent);
        }

        @Override protected ITickData getTickValue() {
            TradeData latestTick = Watcher.this.getLatestTick();
            if (latestTick != null) {
                long timestamp = latestTick.getTimestamp();
                return new TickData(timestamp, m_fadeInRate);
            }
            return null;
        }
    }
}
