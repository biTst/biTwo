package bi.two.exch;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class LiveOrdersData {
    public final Exchange m_exchange;
    public final Pair m_pair;
    public final Map<String, OrderData> m_orders = new HashMap<>(); // orderId -> order
    public final Map<String, OrderData> m_clientOrders = new HashMap<>(); // clientOderId -> order; until order confirmed we have no orderId
    private Exchange.IOrdersListener m_ordersListener;

    public void setOrdersListener(Exchange.IOrdersListener listener) { m_ordersListener = listener; }

    public LiveOrdersData(Exchange exchange, Pair pair) {
        m_exchange = exchange;
        m_pair = pair;
    }

    @Override public String toString() {
        return "LiveOrdersData{" + "pair=" + m_pair + '}';
    }

    public void addOrder(OrderData orderData) {
        m_orders.put(orderData.m_orderId, orderData);
    }

    public void notifyListener() {
        if (m_ordersListener != null) {
            m_ordersListener.onUpdated(m_orders);
        }
    }

    public void submitOrder(OrderData orderData) throws IOException {
        m_clientOrders.put(orderData.m_clientOrderId, orderData);
        m_exchange.m_impl.submitOrder(orderData);
    }
}
