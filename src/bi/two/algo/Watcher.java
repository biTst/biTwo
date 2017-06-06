package bi.two.algo;

import bi.two.chart.ITimesSeriesData;
import bi.two.chart.TickData;
import bi.two.chart.TimesSeriesData;
import bi.two.exch.*;

public class Watcher extends TimesSeriesData<TickData> {
    private final BaseAlgo m_algo;
    private final Exchange m_exch;
    private final Pair m_pair;
    private final Exchange.ExchPairData m_pairData;
    private AccountData m_initAcctData;

    public Watcher(BaseAlgo algo, Exchange exch, Pair pair) {
        m_algo = algo;
        m_exch = exch;
        m_pair = pair;
        m_pairData = exch.getPairData(pair);

        algo.addListener(new ITimesSeriesListener() {
            @Override public void onChanged(ITimesSeriesData ts) {
                onAlgoChanged();
            }
        });
    }

    private void onAlgoChanged() {
        TickData tickAdjusted = m_algo.getTickAdjusted();
        if (tickAdjusted != null) {
            if (m_initAcctData == null) { // first run
                init();
            }
        }
    }

    private void init() {
        TopData topData = m_pairData.m_topData;
        if (topData != null) {
//            double bid = topData.m_bid;
//            double ask = topData.m_ask;
            double lastPrice = topData.m_last;

            Currency currencyFrom = m_pair.m_from;
            Currency currencyTo = m_pair.m_to;

            m_initAcctData = new AccountData(m_exch);
            m_initAcctData.setAvailable(currencyFrom, lastPrice);
            m_initAcctData.setAvailable(currencyTo, 1);

        }
    }
}
