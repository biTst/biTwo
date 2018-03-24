package bi.two.exch;

import bi.two.tre.CurrencyValue;
import bi.two.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;

public class Exchange {
    public static final List<Exchange> s_exchanges = new ArrayList<Exchange>();
    public static final Map<String,Exchange> s_exchangesMap = new HashMap<String, Exchange>();

    public final String m_name;
    public final Currency m_baseCurrency;
    public final AccountData m_accountData;
    public BaseExchImpl m_impl;
    public Map<Pair,ExchPairData> m_pairsMap = new HashMap<Pair, ExchPairData>();
    public Schedule m_schedule;
    public IAccountListener m_accountListener;
    public ExecutorService m_threadPool;
    public boolean m_live; // true if connected

    public void setThreadPool(ExecutorService threadPool) { m_threadPool = threadPool; }

    private static void console(String s) { Log.console(s); }

    public Exchange(String name, String impl, Currency baseCurrency) {
        m_name = name;
        m_baseCurrency = baseCurrency;
        s_exchanges.add(this);
        s_exchangesMap.put(name, this);
        m_accountData = new AccountData(this);

        if (impl != null) {
            Class<?> aClass;
            try {
                aClass = Class.forName(impl);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("impl class '" + impl + "' not found: " + e, e);
            }
            Object o;
            try {
                o = aClass.getDeclaredConstructor(Exchange.class).newInstance(this);
            } catch (Exception e) {
                throw new RuntimeException("exch impl class '" + impl + "' newInstance error: " + e, e);
            }
            if (o instanceof BaseExchImpl) {
                m_impl = (BaseExchImpl) o;
            } else {
                throw new RuntimeException("exch impl class '" + impl + "' not extends BaseExchImpl");
            }
        }
    }

    @NotNull public static Exchange get(String name) {
        Exchange exchange = s_exchangesMap.get(name);
        if (exchange == null) { // todo: make lazy
            throw new RuntimeException("Exchange '" + name + "' not found");
        }
        return exchange;
    }

    @Override public String toString() {
        return "Exchange[" + m_name + ']';
    }

    public ExchPairData addPair(Pair pair) {
        ExchPairData value = new ExchPairData(this, pair);
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
        ExchPairData exchPairData = getPairData(pair);
        return exchPairData.getOrderBook();
    }

    public void subscribeOrderBook(OrderBook orderBook, int depth) throws Exception {
        m_impl.subscribeOrderBook(orderBook, depth);
    }

    public void queryOrderBookSnapshot(OrderBook orderBook, int depth) throws Exception {
        m_impl.queryOrderBookSnapshot(orderBook, depth);
    }

    public void subscribeTrades(Pair pair) throws Exception {
        ExchPairData pairData = getPairData(pair);
        ExchPairData.TradesData trades = pairData.getTrades();
        m_impl.subscribeTrades(trades);
    }

    public void queryAccount(IAccountListener iAccountListener) throws Exception {
        m_accountListener = iAccountListener;
        m_impl.queryAccount();
    }

    public void onDisconnected() {
        for (ExchPairData exchPairData : m_pairsMap.values()) {
            exchPairData.onDisconnected();
        }
    }

    public void queryOrders(Pair pair, IOrdersListener listener) throws Exception {
        LiveOrdersData liveOrders = getLiveOrders(pair);
        liveOrders.setOrdersListener(listener);
        m_impl.queryOrders(liveOrders);
    }

    private LiveOrdersData getLiveOrders(Pair pair) {
        ExchPairData exchPairData = m_pairsMap.get(pair);
        return exchPairData.getLiveOrders();
    }

    public boolean submitOrder(OrderData orderData) throws IOException {
        console("Exchange.submitOrder: orderData=" + orderData);
        if (m_live) {
            ExchPairData exchPairData = m_pairsMap.get(orderData.m_pair);
            exchPairData.submitOrder(orderData);
            return true;
        } else {
            console("Exchange.submitOrder error: exchange not connected; orderData=" + orderData);
            return false;
        }
    }

    public boolean submitOrderReplace(String orderId, OrderData orderData) throws IOException {
        console("Exchange.submitOrderReplace: orderId=" + orderId + "; orderData=" + orderData);
        if (m_live) {
            ExchPairData exchPairData = m_pairsMap.get(orderData.m_pair);
            exchPairData.submitOrderReplace(orderId, orderData);
            return true;
        } else {
            console("Exchange.submitOrderReplace error: exchange not connected; orderData=" + orderData);
            return false;
        }
    }

    public void cancelOrder(OrderData orderData) throws IOException {
        m_impl.cancelOrder(orderData);
    }

    public void rateLimiterActive(boolean active) {
        m_impl.rateLimiterActive(active);
    }


    //----------------------------------------------------------------------------------------
    public interface IExchangeConnectListener {
        void onConnected();
        void onDisconnected();
    }


    //----------------------------------------------------------------------------------------
    public interface IAccountListener {
        void onUpdated() throws Exception;
    }


    //----------------------------------------------------------------------------------------
    public interface IOrdersListener {
        void onUpdated(Map<String, OrderData> m_orders);
    }
}
