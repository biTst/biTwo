package bi.two.exch;

import bi.two.tre.CurrencyValue;

public class ExchPairData {
    private final Exchange m_exchange;
    public final Pair m_pair;
    public CurrencyValue m_minOrderToCreate = null;
    public CurrencyValue m_minOrderStep = null;
    public double m_minPriceStep = 0;
    public double m_commission = 0;
    public double m_makerCommission = 0;
    public double m_initBalance;
    private OrderBook m_orderBook;
    private LiveOrdersData m_liveOrders;

    public ExchPairData(Exchange exchange, Pair pair) {
        m_exchange = exchange;
        m_pair = pair;
    }

    public CurrencyValue getMinOrderToCreate() {
        if (m_minOrderToCreate != null) {
            return m_minOrderToCreate;
        }
        throw new RuntimeException("no minOrderToCreate defined");
    }

    public OrderBook getOrderBook() {
        if (m_orderBook == null) { // lazy
            m_orderBook = new OrderBook(m_exchange, m_pair);
        }
        return m_orderBook;
    }

    public void onDisconnected() {
        m_orderBook = null;
    }

    public LiveOrdersData getLiveOrders() {
        if (m_liveOrders == null) { // lazy
            m_liveOrders = new LiveOrdersData(m_pair);
        }
        return m_liveOrders;
    }
}
