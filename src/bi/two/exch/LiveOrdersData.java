package bi.two.exch;

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
        return "LiveOrdersData{" + "pair=" + m_pair + "; orders.num=" + m_orders.size() + '}';
    }

    public void addOrder(OrderData orderData) {
        String orderId = orderData.m_orderId;
        if (orderId == null) {
            throw new RuntimeException("can not add order with null orderId");
        }
        boolean containsKey = m_orders.containsKey(orderId);
        if (containsKey) {
            throw new RuntimeException("order with orderId=" + orderId + " already exist");
        }
        String clientOrderId = orderData.m_clientOrderId;
        if (clientOrderId != null) {
            m_clientOrders.remove(clientOrderId);
        }
        m_orders.put(orderId, orderData);
    }

    public void removeOrder(OrderData orderData) {
        String orderId = orderData.m_orderId;
        if (orderId == null) {
            throw new RuntimeException("can not куьщму order with null orderId");
        }
        OrderData removed = m_orders.remove(orderId);
        if (removed == null) {
            throw new RuntimeException("unknown order with orderId=" + orderId + "; known keys: " + m_orders.keySet());
        }
        String clientOrderId = orderData.m_clientOrderId;
        if (clientOrderId != null) {
            m_clientOrders.remove(clientOrderId);
        }
    }

    public void notifyListener() {
        if (m_ordersListener != null) {
            m_ordersListener.onUpdated(m_orders);
        }
    }

    public void submitOrder(OrderData orderData) throws Exception {
        m_clientOrders.put(orderData.m_clientOrderId, orderData);
        m_exchange.m_impl.submitOrder(orderData);
    }

    public void submitOrderReplace(String orderId, OrderData orderData) throws Exception {
        OrderData parentOrderData = m_orders.get(orderId);
        if (parentOrderData == null) {
            throw new RuntimeException("no order with id=" + orderId);
        }
        // crosslink
        parentOrderData.m_replaceOrder = orderData;
        orderData.m_cancelOrder = parentOrderData;

        m_clientOrders.put(orderData.m_clientOrderId, orderData);
        m_exchange.m_impl.submitOrderReplace(orderId, orderData);
    }

    public OrderData getOrder(String oid) {
        return m_orders.get(oid);
    }

    public void onDisconnected() {
        m_orders.clear();
        m_clientOrders.clear();
    }
}
