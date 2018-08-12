package bi.two.algo;

import bi.two.chart.ITickData;
import bi.two.chart.JoinNonChangedTimesSeriesData;
import bi.two.chart.TickData;
import bi.two.chart.TradeData;
import bi.two.exch.*;
import bi.two.opt.Vary;
import bi.two.tre.CurrencyValue;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TicksTimesSeriesData;
import bi.two.util.MapConfig;
import bi.two.util.Utils;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static bi.two.algo.BaseAlgo.COLLECT_VALUES_KEY;

public class Watcher extends TicksTimesSeriesData<TradeData> {
    private static final boolean LOG_ALL = false;
    private static final boolean LOG_MOVE = false;
    private static final long ONE_MIN_MILLIS = TimeUnit.MINUTES.toMillis(1);
    private static final long ONE_HOUR_MILLIS = TimeUnit.HOURS.toMillis(1);

    private final Exchange m_exch;
    private final Pair m_pair;
    private final ExchPairData m_exchPairData;
    private final double m_commission;
    public final BaseAlgo m_algo;
    private final boolean m_collectValues;
    private final ITimesSeriesData<TickData> m_priceTs;
    private final CurrencyValue m_exchMinOrderToCreate;
    private final boolean m_priceAtSameTick; // apply price from same tick or from the next
    private final double m_minOrderMul;
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
    private ITickData m_lastAdjusted; // saved direction from last tick processing
    private Date m_nextTradeCloseTime;
    private float m_lastDirection = 0;
    private Boolean m_isUp;
    private int m_changedDirection; // counter

    public Watcher(MapConfig config, MapConfig algoConfig, Exchange exch, Pair pair, ITimesSeriesData<TickData> ts) {
        super(null);
        m_priceTs = ts;
        m_exch = exch;
        m_pair = pair;
        m_exchPairData = exch.getPairData(pair);
        double commission = config.getDoubleOrDefault(BaseAlgo.COMMISSION_KEY, Double.POSITIVE_INFINITY);
        if (Double.isInfinite(commission)) {
            m_commission = m_exchPairData.m_commission;
        } else { // override from local config
            m_commission = commission;
        }
        m_priceAtSameTick = config.getBooleanOrDefault("priceAtSameTick", Boolean.FALSE); // by def - use price from next tick
        m_exchMinOrderToCreate = m_exchPairData.m_minOrderToCreate;

        m_minOrderMul = algoConfig.getNumber(Vary.minOrderMul).doubleValue();
        m_collectValues = algoConfig.getBoolean(COLLECT_VALUES_KEY);
        m_algo = createAlgo(ts, algoConfig);
        setParent(m_algo);
    }

    protected BaseAlgo createAlgo(ITimesSeriesData parent, MapConfig config) {
        return null;
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        // note, here changed is not checked = run on ever tick, to recheck int state often

        TickData latestPriceTick = m_priceTs.getLatestTick();
        if (latestPriceTick == null) {
            return; // skip
        }

        float closePrice = latestPriceTick.getClosePrice();
        m_topData.m_last = closePrice;
        m_topData.m_bid = closePrice;
        m_topData.m_ask = closePrice;

        if (m_initAcctData == null) { // very first tick
            init();
        } else {
            if (m_lastAdjusted != null) { // process delayed first
                process(m_lastAdjusted);
                m_lastAdjusted = null;
            }

            ITickData adjusted = m_algo.getAdjusted();
            if (adjusted != null) {
                if (m_priceAtSameTick) {
                    process(adjusted);
                } else {
                    m_lastAdjusted = adjusted; // save to process on next tick
                }
            }
        }
    }

    private void process(ITickData tickAdjusted) {
        boolean toLog = LOG_ALL;

        float direction = tickAdjusted.getClosePrice(); // UP/DOWN
        if (m_lastDirection == direction) {
            return; // todo: do not process same value twice in simulation
        }
        m_lastDirection = direction;

        if((m_isUp == null) || (m_isUp && (direction==-1)) || (!m_isUp && (direction==1)) ) {
            m_isUp = (direction>0);
            m_changedDirection++;
        }

        if (m_exch.hasSchedule()) {
            long tickTime = tickAdjusted.getTimestamp();
            if ((m_nextTradeCloseTime == null) || (m_nextTradeCloseTime.getTime() < tickTime)) {
                m_nextTradeCloseTime = m_exch.getNextTradeCloseTime(tickTime);
            }
            long tradeCloseTime = m_nextTradeCloseTime.getTime();
            long timeToTradeClose = tradeCloseTime - tickTime;
if (timeToTradeClose < 0) {
    throw new RuntimeException("timeToTradeClose<0: =" + timeToTradeClose + "; m_nextTradeCloseTime=" + m_nextTradeCloseTime + "; tickTime=" + tickTime);
}
            if (timeToTradeClose < ONE_HOUR_MILLIS) {
                if (timeToTradeClose < ONE_MIN_MILLIS) {
                    direction = 0; // do not trade last minute
                } else {
                    float rate = ((float) timeToTradeClose) / ONE_HOUR_MILLIS;
                    direction *= rate;
                }
            }
        }

        Currency currencyFrom = m_pair.m_from;
        Currency currencyTo = m_pair.m_to;
        String pairToName = currencyTo.m_name;

        if (toLog) {
            System.out.println("Watcher.process() direction=" + direction);
        }
        double needBuyTo = m_accountData.calcNeedBuyTo(m_pair, direction);
        if (toLog) {
            System.out.println(" needBuy=" + Utils.format8(needBuyTo) + " " + pairToName);
        }

        needBuyTo *= 0.95;

        double absOrderSize = Math.abs(needBuyTo);
        OrderSide needOrderSide = (needBuyTo >= 0) ? OrderSide.SELL : OrderSide.BUY;
        if (toLog) {
            System.out.println("   needOrderSide=" + needOrderSide + "; absOrderSize=" + Utils.format8(absOrderSize));
        }

        double exchMinOrderToCreateValue = m_exchMinOrderToCreate.m_value;
        if(m_exchMinOrderToCreate.m_currency != currencyTo) {
            exchMinOrderToCreateValue = m_accountData.convert(currencyFrom, currencyTo, exchMinOrderToCreateValue);
        }

        exchMinOrderToCreateValue = exchMinOrderToCreateValue * m_minOrderMul;
        TickData latestPriceTick = m_priceTs.getLatestTick();
        long timestamp = latestPriceTick.getTimestamp();
        if (absOrderSize >= exchMinOrderToCreateValue) {
            double amountFrom = m_accountData.convert(currencyTo, currencyFrom, needBuyTo);

            boolean toLogMove = LOG_MOVE || toLog;
            if (toLogMove) {
                System.out.println("Watcher.process() direction=" + direction
                        + "; needBuy=" + Utils.format8(needBuyTo) + " " + pairToName
                        + "; needSell=" + Utils.format8(amountFrom) + " " + currencyFrom.m_name
                        + "; needOrderSide=" + needOrderSide + "; absOrderSize=" + Utils.format8(absOrderSize));
            }

            m_accountData.move(m_pair, needBuyTo, m_commission);
            m_tradesNum++;
            m_tradesSum += Math.abs(needBuyTo);

            if (toLogMove) {
                double gain = totalPriceRatio(true);
                System.out.println("    trade[" + m_tradesNum + "]: gain: " + Utils.format8(gain) + " .....................................");
            }

            if (m_collectValues) {
                double price = latestPriceTick.getClosePrice();
                addNewestTick(new TradeData(timestamp, (float) price, (float) needBuyTo, needOrderSide));
            }
        }
        m_lastMillis = timestamp;
    }

    public long getProcessedPeriod() {
        return m_lastMillis - m_startMillis;
    }

    public double totalPriceRatio() {
        return totalPriceRatio(false);
    }

    public double totalPriceRatio(boolean toLog) {
        Currency currencyFrom = m_pair.m_from; // cnh=from
        Currency currencyTo = m_pair.m_to;     // btc=to
        if (m_accountData == null) { // error
            return 1;
        }
        double valuateToNow = m_accountData.evaluateAll(currencyTo);
        double valuateFromNow = m_accountData.evaluateAll(currencyFrom);

        double gainTo = valuateToNow / m_valuateToInit;
        double gainFrom = valuateFromNow / m_valuateFromInit;

        double gainAvg = (gainTo + gainFrom) / 2;

        if (toLog) {
            if (LOG_MOVE || LOG_ALL) {
                System.out.println("totalPriceRatio() accountData=" + m_accountData
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


    private void init() {
        m_startMillis = m_priceTs.getLatestTick().getTimestamp();
        if (LOG_ALL) {
            System.out.println("init() topData = " + m_topData);
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
        if (LOG_ALL) {
            System.out.println(" valuate[" + currencyTo.m_name + "]=" + m_valuateToInit + "; valuate[" + currencyFrom.name() + "]=" + m_valuateFromInit);
        }
    }

    public String log() {
        return "Watcher["
                + "\n ticksNum=" + m_ticks.size()
                + "\n]";
    }

    public TicksTimesSeriesData<TickData> getGainTs() {
        return new GainTimesSeriesData(this);
    }

    public double getAvgTradeSize() {
        return (m_tradesNum == 0) ? 0 : m_tradesSum / m_tradesNum;
    }

    public String getGainLogStr(String prefix, double gain) {
        String key = m_algo.key(false);
        return prefix + "GAIN[" + key + "]: " + Utils.format8(gain)
                + "   trades=" + m_tradesNum
                + "; avgTrade=" + Utils.format5(this.getAvgTradeSize())
                + "; turns=" + m_changedDirection
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
}
