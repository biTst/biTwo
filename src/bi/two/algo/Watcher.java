package bi.two.algo;

import bi.two.chart.ITimesSeriesData;
import bi.two.chart.TickData;
import bi.two.chart.TimesSeriesData;
import bi.two.exch.*;
import bi.two.util.Utils;

public class Watcher extends TimesSeriesData<TickData> {
    private static final double MIN_MOVE = 0.015;
    
    private final BaseAlgo m_algo;
    private final Exchange m_exch;
    private final Pair m_pair;
    private final ExchPairData m_exchPairData;
    private AccountData m_initAcctData;
    private AccountData m_accountData;
    private TopData m_initTopData;
    private double m_valuateToInit;
    private double m_valuateFromInit;
    private long m_startMillis;
    private long m_lastMillis;

    public Watcher(BaseAlgo algo, Exchange exch, Pair pair) {
        super(algo);
        m_algo = algo;
        m_exch = exch;
        m_pair = pair;
        m_exchPairData = exch.getPairData(pair);
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        if (changed) {
            if (m_initAcctData == null) { // first run
                init();
            } else {
                TickData adjusted = m_algo.getAdjusted();
                if (adjusted != null) {
                    process(adjusted);
                }
            }
        }
    }

    private void process(TickData tickAdjusted) {
        float direction = tickAdjusted.getPrice(); // UP/DOWN
        System.out.println("Watcher.process() direction=" + direction);

        double needBuyTo = m_accountData.calcNeedBuyTo(m_pair, direction);
        System.out.println(" needBuy=" + Utils.format8(needBuyTo) + " " + m_pair.m_to.m_name);

        needBuyTo *= 0.95;

//        double orderPrice = (needBuyTo > 0) ? ask: bid;

        double absOrderSize = Math.abs(needBuyTo);
        OrderSide needOrderSide = (needBuyTo >= 0) ? OrderSide.BUY : OrderSide.SELL;
        System.out.println("   needOrderSide=" + needOrderSide + "; absOrderSize=" + Utils.format8(absOrderSize));

        double exchMinOrderToCreate = m_exch.minOrderToCreate(m_pair);
        if ((absOrderSize >= exchMinOrderToCreate) && (absOrderSize >= MIN_MOVE)) {
            m_accountData.move(m_pair, needBuyTo);

            double gain = totalPriceRatio();
            System.out.println("    gain: " + Utils.format8(gain) + " .....................................");
        }
        m_lastMillis = m_exchPairData.m_newestTick.getTimestamp();
    }

    public long getProcessedPeriod() {
       return m_lastMillis - m_startMillis;
    }

    public double totalPriceRatio() {
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
        return gainAvg;
    }


    private void init() {
        m_startMillis = m_exchPairData.m_newestTick.getTimestamp();
        TopData topData = m_exchPairData.m_topData;
        System.out.println("init() topData = " + topData);

        if (topData != null) {
            m_initTopData = new TopData(topData);
//            double bid = topData.m_bid;
//            double ask = topData.m_ask;
            double lastPrice = topData.m_last;

            Currency currencyFrom = m_pair.m_from;
            Currency currencyTo = m_pair.m_to;

            m_initAcctData = new AccountData(m_exch);
            m_initAcctData.setAvailable(currencyFrom, 1);
            m_initAcctData.setAvailable(currencyTo, lastPrice);

            m_accountData = m_initAcctData.copy();

            m_valuateToInit = m_initAcctData.evaluateAll(currencyTo);
            m_valuateFromInit = m_initAcctData.evaluateAll(currencyFrom);
            System.out.println(" valuate["+currencyTo.m_name+"]=" + m_valuateToInit + "; valuate["+currencyFrom.name()+"]="+m_valuateFromInit);
        }
    }
}
