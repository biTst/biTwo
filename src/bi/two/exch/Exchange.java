package bi.two.exch;

import bi.two.tre.CurrencyValue;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

public class Exchange {
    public static final List<Exchange> s_exchanges = new ArrayList<Exchange>();
    public static final Map<String,Exchange> s_exchangesMap = new HashMap<String, Exchange>();

    public final String m_name;
    public final Currency m_baseCurrency;
    public final AccountData m_accountData;
    public BaseExchImpl m_impl;
    public Map<Pair,ExchPairData> m_pairsMap = new HashMap<Pair, ExchPairData>();
    public Schedule m_schedule;
    private Map<Pair, OrderBook> m_orderBooks = new HashMap<>();
    public IAccountListener m_accountListener;

    public Exchange(String name, Currency baseCurrency) {
        m_name = name;
        m_baseCurrency = baseCurrency;
        s_exchanges.add(this);
        s_exchangesMap.put(name, this);
        m_accountData = new AccountData(this);
    }

    @NotNull public static Exchange get(String name) {
        Exchange exchange = s_exchangesMap.get(name);
        if (exchange == null) {
            throw new RuntimeException("Exchange '" + name + "' not found");
        }
        return exchange;
    }

    @Override public String toString() {
        return "Exchange[" + m_name + ']';
    }

    public ExchPairData addPair(Pair pair) {
        ExchPairData value = new ExchPairData();
        m_pairsMap.put(pair, value);
        return value;
    }

    public ExchPairData getPairData(Pair pair) { return m_pairsMap.get(pair); }

    public boolean supportPair(Currency from, Currency to) {
        for (Pair pair : m_pairsMap.keySet()) {
            if ((pair.m_from == from) && (pair.m_to == to)) {
                return true;
            }
            if ((pair.m_to == from) && (pair.m_from == to)) {
                return true;
            }
        }
        return false;
    }

    public boolean supportPair(Pair pair) {
        return m_pairsMap.containsKey(pair);
    }

    public CurrencyValue getMinOrderToCreate(Pair pair) {
        ExchPairData pairData = getPairData(pair);
        return pairData.getMinOrderToCreate();
    }

    public Date getNextTradeCloseTime(long tickTime) {
        if (m_schedule != null) {
            return m_schedule.getNextTradeCloseTime(tickTime);
        }
        return null;
    }

    public boolean hasSchedule() {
        return (m_schedule != null);
    }

    public void connect(IExchangeConnectListener iExchangeConnectListener) throws Exception {
        m_impl.connect(iExchangeConnectListener);
    }

    public OrderBook getOrderBook(Pair pair) {
        OrderBook orderBook = m_orderBooks.get(pair);
        if (orderBook == null) {
            orderBook = new OrderBook(this, pair);
            m_orderBooks.put(pair, orderBook);
        }
        return orderBook;
    }

    public void subscribeOrderBook(OrderBook orderBook, int depth) throws Exception {
        m_impl.subscribeOrderBook(orderBook, depth);
    }

    public void queryOrderBookSnapshot(OrderBook orderBook, int depth) throws Exception {
        m_impl.queryOrderBookSnapshot(orderBook, depth);
    }

    public void queryAccount(IAccountListener iAccountListener) throws IOException, InterruptedException {
        m_accountListener = iAccountListener;
        m_impl.queryAccount();
    }


    //----------------------------------------------------------------------------------------
    public interface IExchangeConnectListener {
        void onConnected();
    }


    //----------------------------------------------------------------------------------------
    public interface IAccountListener {
        void onUpdated() throws Exception;
    }
}
