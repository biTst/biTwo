package bi.two.exch;

import java.util.HashMap;
import java.util.Map;

public class LiveOrdersData {
    public Pair m_pair;
    public Map<String, OrderData> m_orders = new HashMap<>();
    private Exchange.IOrdersListener m_ordersListener;

    public void setOrdersListener(Exchange.IOrdersListener listener) { m_ordersListener = listener; }

    public LiveOrdersData(Pair pair) {
        m_pair = pair;
    }

    @Override public String toString() {
        return "LiveOrdersData{" + "pair=" + m_pair + '}';
    }

    public void addOrder(OrderData orderData) {
        m_orders.put(orderData.m_orderId, orderData);
    }

    public void notifyListener() {
        if(m_ordersListener != null) {
            m_ordersListener.onUpdated();
        }
    }
}
