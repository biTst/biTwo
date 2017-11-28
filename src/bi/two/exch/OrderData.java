package bi.two.exch;

import bi.two.util.Utils;

public class OrderData {
    public final Exchange m_exchange;
    public final Pair m_pair;
    public final OrderSide m_side;
    public final OrderType m_type;
    public OrderStatus m_status = OrderStatus.NEW;
    public double m_price;
    public double m_amount;

    public long m_placeTime;
    public String m_orderId;
    public double m_filled; // filled amount

    public OrderData(Exchange exchange, Pair pair, OrderSide side, OrderType orderType, double price, double amount) {
        // Pair.BTC_USD OrderSide.BUY meant buy BTC for USD
        m_exchange = exchange;
        m_type = orderType; // like OrderType.LIMIT
        m_side = side; // like OrderSide.BUY
        m_pair = pair; // like Pair.BTC_USD
        m_price = price;
        m_amount = amount;
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
}
