package bi.two.exch;

import bi.two.util.Utils;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class OrderData {
    public final Exchange m_exchange;
    public String m_orderId;
    public String m_clientOrderId;
    public final Pair m_pair;
    public final OrderSide m_side;
    public final OrderType m_type;
    public OrderStatus m_status = OrderStatus.NEW;
    public double m_price;
    public double m_amount;

    public long m_placeTime;
    public double m_filled; // filled amount
    private DecimalFormat m_priceFormat;
    private DecimalFormat m_sizeFormat;
    private List<IOrderListener> m_listeners;

    public OrderData(Exchange exchange, String orderId, Pair pair, OrderSide side, OrderType orderType, double price, double amount) {
        // Pair.BTC_USD OrderSide.BUY meant buy BTC for USD
        m_exchange = exchange;
        m_orderId = orderId;
        m_type = orderType; // like OrderType.LIMIT
        m_side = side; // like OrderSide.BUY
        m_pair = pair; // like Pair.BTC_USD
        m_price = price;
        m_amount = amount;
        m_clientOrderId = Long.toString(System.currentTimeMillis());
    }

    public boolean isActive() { return m_status.isActive(); }
    public boolean canCancel() { return m_status.isActive(); }
    public long placeTime() { return m_placeTime; }
    public double remained() { return m_amount - m_filled; }

    public boolean isFilled() {
        boolean statusOk = (m_status == OrderStatus.FILLED);
        boolean filledOk = (m_filled == m_amount) && (m_filled > 0);
        if (statusOk == filledOk) {
            return statusOk;
        }
        throw new RuntimeException("Error order state: status not matches filled qty: " + this);
    }

    @Override public String toString() {
        return "OrderData{" +
                ((m_orderId != null) ? "id=" + m_orderId + " " : "") +
                "status=" + m_status +
                ", pair=" + m_pair +
                ", side=" + m_side +
                ", type=" + m_type+
                ", amount=" + Utils.format5(m_amount) +
                ", price=" + Utils.format5(m_price) +
                ", filled=" + Utils.format5(m_filled) +
                '}';
    }

    public void setFilled(double filled) {
        m_filled = filled;
        if (filled > 0) { // something is executed
            m_status = OrderStatus.PARTIALLY_FILLED;
        }
    }

    public void setFormats(DecimalFormat priceFormat, DecimalFormat sizeFormat) {
        m_priceFormat = priceFormat;
        m_sizeFormat = sizeFormat;
    }

    public String formatPrice(double price) {
        return m_priceFormat.format(price);
    }

    public String formatSize(double size) {
        return m_sizeFormat.format(size);
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
                listener.onUpdated(this);
            }
        }
    }


    //-----------------------------------------------------------
    public interface IOrderListener {
        void onUpdated(OrderData orderData);
    }
}
