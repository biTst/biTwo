package bi.two.tre;

import bi.two.exch.Currency;
import bi.two.exch.*;
import bi.two.util.Utils;

import java.util.*;
import java.util.concurrent.TimeUnit;

class RoundData implements OrderBook.IOrderBookListener {
    private static final long RECALC_TIME = TimeUnit.MINUTES.toMillis(5);

    private static List<RoundPlan> s_bestPlans = new ArrayList<>();
    private static HashSet<RoundPlan> s_allPlans = new HashSet<>();

    public final Round m_round;
    private final Exchange m_exchange;
    public final List<RoundDirectedData> m_directedRounds = new ArrayList<>();
    public final List<PairData> m_pds = new ArrayList<>();
    public boolean m_allLive;
    public Map<Pair, CurrencyValue> m_minPassThruOrdersSize = new HashMap<>();
    private long m_minPassThruOrdersSizeRecalcTime;

    public RoundData(Currency[] currencies, Exchange exchange) {
        m_round = Round.get(currencies[0], currencies[1], currencies[2]);
        m_exchange = exchange;

        int length = currencies.length;
        for(int i = 0; i < length; i++) {
            add(i, true);
            add(i, false);

            int j = (i + 1) % length;
            Pair pair = Pair.get(currencies[i], currencies[j]);
            PairData pairData = PairData.get(pair);
            m_pds.add(pairData);
            pairData.addOrderBookListener(this);
        }
    }

    @Override public String toString() {
        return m_round.toString();
    }

    private void add(int startIndex, boolean forward) {
        Currency[] roundCurrencies = m_round.m_currencies;
        int length = roundCurrencies.length;
        Currency[] currencies = new Currency[length];
        int index = startIndex;
        for (int i = 0; i < length; i++) {
            Currency currency = roundCurrencies[index];
            currencies[i] = currency;
            index = (index + (forward ? 1 : -1) + length) % length;
        }

        RoundDirectedData roundDirectedData = new RoundDirectedData(this, currencies);
        m_directedRounds.add(roundDirectedData);
    }

    public void getPairDatas(List<PairData> pds) {
        for (RoundDirectedData directedRound : m_directedRounds) {
            directedRound.getPairDatas(pds);
        }
    }

    @Override public void onOrderBookUpdated(OrderBook orderBook) {
        if (!m_allLive) { // recheck allLive
            boolean allLive = true;
            for (PairData pairData : m_pds) {
                if (!pairData.m_orderBookIsLive) {
                    allLive = false;
                    break;
                }
            }
            m_allLive = allLive;
            if (m_allLive) {
                onBecomesLive();
            }
        }
        if (m_allLive) {
            List<RoundPlan> plans = new ArrayList<>(); // plans related to this book update only
            for (RoundDirectedData directedRound : m_directedRounds) {
                directedRound.onUpdated(m_exchange, plans);
            }

            Collections.sort(plans, RoundPlan.BY_RATE_COMPARATOR);
            List<RoundPlan> last6Plans = plans.subList(0, 6);
            logRates(" last :: ", last6Plans);

            for (RoundPlan plan1 : plans) {
                boolean add = true;
                for (RoundPlan plan2 : s_allPlans) {
                    if (plan1.equals(plan2)) { // same direction and type
                        if (plan1.m_roundRate == plan2.m_roundRate) { // same roundRate
                            plan2.m_liveTime = plan1.m_timestamp - plan2.m_timestamp; // just update liveTime
                            add = false;
                            break;
                        }
                    }
                }
                if (add) {
                    s_allPlans.add(plan1); // add to set - will update existing entry
                }
            }

            List<RoundPlan> allPlans = new ArrayList<>(s_allPlans);
            Collections.sort(allPlans, RoundPlan.BY_RATE_COMPARATOR);
            List<RoundPlan> all6Plans = allPlans.subList(0, 6);
            logRates(" all  :: ", all6Plans);

            List<RoundPlan> unique = new ArrayList<>();
            for (RoundPlan plan1 : all6Plans) {
                boolean add = true;
                for (RoundPlan plan2 : s_bestPlans) {
                    if (plan1.equals(plan2)) { // same direction and type
                        if (plan1.m_roundRate == plan2.m_roundRate) { // do not include twice with same roundRate
                            add = false;
                            continue;
                        }
                    }
                }
                if (add) {
                    unique.add(plan1);
                }
            }

            if (!unique.isEmpty()) {
                s_bestPlans.addAll(unique);
                Collections.sort(s_bestPlans, RoundPlan.BY_RATE_COMPARATOR);
                s_bestPlans = s_bestPlans.subList(0, 6);
            }
            logRates(" best :: ", s_bestPlans);
        }
    }

    private void logRates(String prefix, List<RoundPlan> bestPlans) {
        StringBuilder sb = new StringBuilder(prefix);
        for (RoundPlan plan : bestPlans) {
            sb.append("  " + plan.m_roundPlanType.getPrefix() + ":" + plan.m_rdd + ":" + Utils.format8(plan.m_roundRate) + " " + Utils.millisToYDHMSStr(plan.m_liveTime) + ';');
        }
        System.out.println(sb.toString());
    }

    private void onBecomesLive() {
        System.out.println("ALL becomes LIVE for round: " + this);
        long diff = System.currentTimeMillis() - m_minPassThruOrdersSizeRecalcTime;
        if (diff > RECALC_TIME) {
            recalcPassThruOrders();
        }
    }

    private void recalcPassThruOrders() {
        System.out.println("recalcPassThruOrders: " + this);
        for (PairData pairData : m_pds) {
            Pair pair = pairData.m_pair;
            ExchPairData exchPairData = m_exchange.getPairData(pair);
            CurrencyValue minOrder = exchPairData.m_minOrderToCreate;
            System.out.println(" pair[" + pair + "].minOrder=" + minOrder);
            m_minPassThruOrdersSize.put(pair, minOrder);
        }
        int size = m_pds.size();
        for (int i = 0; i < size; i++) {
            PairData pd1 = m_pds.get(i);
            Pair p1 = pd1.m_pair;
            CurrencyValue os1 = m_minPassThruOrdersSize.get(p1);
            PairData pd2 = m_pds.get((i + 1) % size);
            Pair p2 = pd2.m_pair;
            CurrencyValue os2 = m_minPassThruOrdersSize.get(p2);
            System.out.println(" compare: pair[" + p1 + "].minOrder=" + os1 + "  and  pair[" + p2 + "].minOrder=" + os2);
            Currency c1 = os1.m_currency;
            double v1 = os1.m_value;
            Currency c2 = os2.m_currency;
            double v2 = os2.m_value;

            double rate = m_exchange.m_accountData.rate(c1, c2);
            double v1_ = v1 * rate;
            System.out.println("  convert " + Utils.format8(v1) + c1.m_name + " -> " + Utils.format8(v1_) + c2.m_name + "; rate=" + Utils.format8(rate));
            if (v1_ > v2) {
                double factor = v1_ / v2;
                CurrencyValue os2_ = new CurrencyValue(v2 * factor, c2);
                System.out.println("   " + Utils.format8(v1_) + " > " + Utils.format8(v2) + "; factor=" + Utils.format8(factor) + ";  " + os2 + " => " + os2_);
                m_minPassThruOrdersSize.put(p2, os2_);
            } else {
                double factor = v2 / v1_;
                CurrencyValue os1_ = new CurrencyValue(v1 * factor, c1);
                System.out.println("   " + Utils.format8(v1_) + " <= " + Utils.format8(v2) + "; factor=" + Utils.format8(factor) + ";  " + os1 + " => " + os1_);
                m_minPassThruOrdersSize.put(p1, os1_);
            }

            System.out.println("minPassThruOrdersSize=" + m_minPassThruOrdersSize);
        }
        m_minPassThruOrdersSizeRecalcTime = System.currentTimeMillis();
    }
}
