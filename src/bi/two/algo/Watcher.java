package bi.two.algo;

import bi.two.chart.ITickData;
import bi.two.chart.JoinNonChangedTimesSeriesData;
import bi.two.chart.TickData;
import bi.two.chart.TradeData;
import bi.two.exch.*;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TimesSeriesData;
import bi.two.util.MapConfig;
import bi.two.util.Utils;

import static bi.two.algo.BaseAlgo.COLLECT_VALUES_KEY;

public class Watcher extends TimesSeriesData<TradeData> {
    private static final boolean LOG_ALL = false;
    private static final boolean LOG_MOVE = false;
    private static final double MIN_MOVE = 100; // 100usd

    private final Exchange m_exch;
    private final Pair m_pair;
    private final ExchPairData m_exchPairData;
    private final double m_commission;
    public final BaseAlgo m_algo;
    private final boolean m_collectValues;
    private final ITimesSeriesData<TickData> m_priceTs;
    private AccountData m_initAcctData;
    private AccountData m_accountData;
    public TopData m_topData = new TopData(0,0,0);
    private TopData m_initTopData;
    private double m_valuateToInit;
    private double m_valuateFromInit;
    private long m_startMillis;
    private long m_lastMillis;
    public int m_tradesNum;

    public Watcher(MapConfig config, Exchange exch, Pair pair, ITimesSeriesData<TickData> ts) {
        super(null);
        m_priceTs = ts;
        m_exch = exch;
        m_pair = pair;
        m_exchPairData = exch.getPairData(pair);
        m_commission = m_exchPairData.m_commission;
        m_collectValues = config.getBoolean(COLLECT_VALUES_KEY);
        m_algo = createAlgo(ts, config);
        setParent(m_algo);
    }

    protected BaseAlgo createAlgo(ITimesSeriesData parent, MapConfig config) {
        return null;
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        if (changed) {
            float closePrice = m_priceTs.getLatestTick().getClosePrice();
            m_topData.m_last = closePrice;
            m_topData.m_bid = closePrice;
            m_topData.m_ask = closePrice;

            if (m_initAcctData == null) { // first tick
                init();
            } else {
                ITickData adjusted = m_algo.getAdjusted();
                if (adjusted != null) {
                    process(adjusted);
                }
            }
        }
    }

    private void process(ITickData tickAdjusted) {
        float direction = tickAdjusted.getClosePrice(); // UP/DOWN

        Currency currencyFrom = m_pair.m_from;
        Currency currencyTo = m_pair.m_to;
        String pairToName = currencyTo.m_name;

        log("Watcher.process() direction=" + direction);
        double needBuyTo = m_accountData.calcNeedBuyTo(m_pair, direction);
        log(" needBuy=" + Utils.format8(needBuyTo) + " " + pairToName);

        needBuyTo *= 0.95;

//        double orderPrice = (needBuyTo > 0) ? ask: bid;

        double absOrderSize = Math.abs(needBuyTo);
        OrderSide needOrderSide = (needBuyTo >= 0) ? OrderSide.SELL : OrderSide.BUY;
        log("   needOrderSide=" + needOrderSide + "; absOrderSize=" + Utils.format8(absOrderSize));

        double exchMinOrderToCreate = m_exch.minOrderToCreate(m_pair);
        TickData latestPriceTick = m_priceTs.getLatestTick();
        long timestamp = latestPriceTick.getTimestamp();
        if ((absOrderSize >= exchMinOrderToCreate) && (absOrderSize >= MIN_MOVE)) {

            double amountFrom = m_accountData.convert(currencyTo, currencyFrom, needBuyTo);

            logMove("Watcher.process() direction=" + direction
                    + "; needBuy=" + Utils.format8(needBuyTo) + " " + pairToName
                    + "; needSell=" + Utils.format8(amountFrom) + " " + currencyFrom.m_name
                    + "; needOrderSide=" + needOrderSide + "; absOrderSize=" + Utils.format8(absOrderSize));

            m_accountData.move(m_pair, needBuyTo, m_commission);
            m_tradesNum++;

            double gain = totalPriceRatio(true);
            logMove("    trade[" + m_tradesNum + "]: gain: " + Utils.format8(gain) + " .....................................");

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
            logMove("totalPriceRatio() accountData=" + m_accountData
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
        return gainAvg;
    }


    private void init() {
        m_startMillis = m_priceTs.getLatestTick().getTimestamp();
        log("init() topData = " + m_topData);

        m_initTopData = new TopData(m_topData);
        double lastPrice = m_topData.m_last;

        Currency currencyFrom = m_pair.m_from;
        Currency currencyTo = m_pair.m_to;

        m_initAcctData = new AccountData(m_exch);
        m_initAcctData.m_topDatas.put(m_pair, m_initTopData);
        m_initAcctData.setAvailable(currencyFrom, 1);
        m_initAcctData.setAvailable(currencyTo, lastPrice);

        m_accountData = m_initAcctData.copy();
        m_accountData.m_topDatas.put(m_pair, m_topData);

        m_valuateToInit = m_initAcctData.evaluateAll(currencyTo);
        m_valuateFromInit = m_initAcctData.evaluateAll(currencyFrom);
        log(" valuate[" + currencyTo.m_name + "]=" + m_valuateToInit + "; valuate[" + currencyFrom.name() + "]=" + m_valuateFromInit);
    }

    private void log(String s) {
        if (LOG_ALL) {
            System.out.println(s);
        }
    }

    private void logMove(String s) {
        if (LOG_MOVE || LOG_ALL) {
            System.out.println(s);
        }
    }

    public String log() {
        return "Watcher["
                + "\n ticksNum=" + m_ticks.size()
                + "\n]";
    }

    public TimesSeriesData<TickData> getGainTs() {
        return new GainTimesSeriesData(this);
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
