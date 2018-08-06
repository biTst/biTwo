package bi.two.exch;

import bi.two.util.Utils;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class OrderData {
    private static long s_clientOrderIdCounter = System.currentTimeMillis();

    public final Exchange m_exchange;
    public String m_orderId;
    public String m_clientOrderId;
    public long m_submitTime;
    public final Pair m_pair;
    public final OrderSide m_side;
    public final OrderType m_type;
    public OrderStatus m_status = OrderStatus.NEW;
    public double m_price;
    public double m_amount;

    public long m_placeTime;
    public double m_filled; // filled amount / cumQty
    private DecimalFormat m_priceFormat;
    private DecimalFormat m_sizeFormat;
    private List<IOrderListener> m_listeners;
    public String m_error;
    public List<Execution> m_executions = new ArrayList<>();
    public OrderData m_replaceOrder; // cancel-replace link
    public OrderData m_cancelOrder; // cancel-replace link


    public OrderData(Exchange exchange, String orderId, Pair pair, OrderSide side, OrderType orderType, double price, double amount) {
        // Pair.BTC_USD OrderSide.BUY meant buy BTC for USD
        m_exchange = exchange;
        m_orderId = orderId;
        m_type = orderType; // like OrderType.LIMIT
        m_side = side; // like OrderSide.BUY
        m_pair = pair; // like Pair.BTC_USD
        m_price = price;
        m_amount = amount;
        synchronized (getClass()) {
            s_clientOrderIdCounter++;
            m_clientOrderId = Long.toString(s_clientOrderIdCounter);
        }
    }

    public OrderData copyForReplace() {
        OrderData orderData = new OrderData(m_exchange, null, m_pair, m_side, m_type, m_price, remained());
        return orderData;
    }

    public boolean isActive() { return m_status.isActive(); }
    public boolean canCancel() { return m_status.isActive(); }
    public long placeTime() { return m_placeTime; }
    public double remained() { return m_amount - m_filled; } // leavesQty

    public boolean isFilled() {
        boolean statusOk = (m_status == OrderStatus.FILLED);
        boolean filledOk = (m_filled == m_amount) && (m_filled > 0);
        if (statusOk == filledOk) {
            return statusOk;
        }
        throw new RuntimeException("Error order state: status not matches filled qty: " + this);
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("OrderData{");
        if (m_orderId != null) {
            sb.append("id=").append(m_orderId).append(' ');
        }
        sb.append("status=").append(m_status);
        sb.append(", pair=").append(m_pair);
        sb.append(", side=").append(m_side);
        sb.append(", type=").append(m_type);
        sb.append(", amount=").append(formatSize(m_amount));
        sb.append(", price=").append(formatPrice(m_price));
        sb.append(", filled=").append(formatSize(m_filled));
        if (m_error != null) {
            sb.append(", ").append(m_error);
        }
        sb.append('}');
        return sb.toString();
    }

    public void setFilled(double filled) {
        if (m_filled != filled) {
            m_filled = filled;
            if (filled > 0) { // something is executed
                m_status = (filled == m_amount)
                        ? OrderStatus.FILLED
                        : OrderStatus.PARTIALLY_FILLED;
            }
        }
    }

    public void setFormats(DecimalFormat priceFormat, DecimalFormat sizeFormat) {
        m_priceFormat = priceFormat;
        m_sizeFormat = sizeFormat;
    }

    public String formatPrice(double price) {
        return (m_priceFormat == null) ? Utils.format8(price) : m_priceFormat.format(price);
    }

    public String formatSize(double size) {
        return (m_sizeFormat == null) ? Utils.format8(size) : m_sizeFormat.format(size);
    }

    public void addOrderListener(IOrderListener listener) {
        if (m_listeners == null) { // lazy
            m_listeners = new ArrayList<>();
        }
        m_listeners.add(listener);
    }

    public void notifyListeners() {
        if (m_listeners != null) {
            for (IOrderListener listener : m_listeners) {
                listener.onOrderUpdated(this);
            }
        }
    }

    public void addExecution(Execution execution) {
        m_executions.add(execution);
    }



    //-----------------------------------------------------------
    public interface IOrderListener {
        void onOrderUpdated(OrderData orderData);
    }
}
