package bi.two.tre;

import bi.two.exch.*;
import bi.two.util.Log;

class BaseOrderWatcher implements Tre.IWatcher {
    public final Exchange m_exchange;
    public final RoundNodePlan.RoundStep m_roundStep;
    public final PairData m_pairData;
    public final ExchPairData m_exchPairData;
    public OrderData m_orderData;
    public State m_state = State.none;

    @Override public String toString() {
        return "BaseOrderWatcher: state=" + m_state + "; order=" + m_orderData;
    }

    OrderData.IOrderListener m_orderListener = new OrderData.IOrderListener() { // todo: seems need to remove listener when done
        @Override public void onOrderUpdated(OrderData orderData) {
            onOrderUpdated(orderData);
        }
    };

    private final OrderBook.IOrderBookListener m_orderBookListener = new OrderBook.IOrderBookListener() {
        @Override public void onOrderBookUpdated(OrderBook orderBook) {
            onBookUpdated(orderBook);
        }
    };

    public BaseOrderWatcher(Exchange exchange, RoundNodePlan.RoundStep roundStep) {
        m_exchange = exchange;
        m_roundStep = roundStep;
        Pair pair = roundStep.m_pair;
        m_orderData = new OrderData(m_exchange, null, pair, m_roundStep.m_orderSide, OrderType.LIMIT, m_roundStep.m_rate, m_roundStep.m_orderSize);

        m_pairData = PairData.get(pair);
        m_pairData.addOrderBookListener(m_orderBookListener);
        m_exchPairData = exchange.getPairData(pair);
    }

    void console(String s) { Log.console(s); }
    void log(String s) { Log.log(s); }
    void err(String s, Throwable t) { Log.err(s, t); }

    public boolean isFinal() { return (m_state == State.done) || (m_state == State.error); }
    public boolean isError() { return (m_state == State.error); }

    protected void onBookUpdatedInt(OrderBook orderBook) { }
    protected boolean onTimerInt() { return false; }

    protected void onOrderUpdated(OrderData orderData) {
        console("Order.onOrderUpdated() orderData=" + orderData);
        boolean isFilled = orderData.isFilled();
        if (isFilled) {
            console(" order is FILLED => DONE");
            m_state = State.done;
        } else if (orderData.m_status == OrderStatus.CANCELLED) {
            console(" order is CANCELLED => FINISHING");
            m_state = State.done;
        } else if (orderData.m_status == OrderStatus.ERROR) {
            console(" order in ERROR => FINISHING");
            m_state = State.error;
        }
        if (isFinal()) {
            onFinish();
        }
    }

    protected void onFinish() {
        m_pairData.removeOrderBookListener(m_orderBookListener);
    }

    public void start() {
        try {
            console("OrderWatcher.start()");
            m_orderData.addOrderListener(m_orderListener);
            console(" submitOrder: " + m_orderData);
            boolean sent = m_exchange.submitOrder(m_orderData);
            if (sent) {
                m_state = OrderWatcher.State.submitted;
            }
        } catch (Exception e) {
            String msg = "OrderWatcher.start() error: " + e;
            console(msg);
            err(msg, e);
        }
    }

    @Override public boolean onTimer() {
        if (m_state == State.done) {
            return true;
        }
        OrderStatus orderStatus = m_orderData.m_status;
        if (orderStatus == OrderStatus.NEW) {
            console("onTimer: order not yet submitted");
            return false;
        }
        if (orderStatus == OrderStatus.FILLED) {
            console("onTimer: order is already filled");
            m_state = State.done;
            return true;
        }
        return onTimerInt();
    }

    void onBookUpdated(OrderBook orderBook) {
//        console("OrderWatcher.onOrderBookUpdated() orderBook=" + orderBook);

        OrderStatus orderStatus = m_orderData.m_status;
//if (orderStatus == OrderStatus.NEW) {
//    console(" order not yet submitted");
//    return;
//}
        if (orderStatus == OrderStatus.FILLED) {
            console(" order is already filled");
            m_state = State.done;
            return;
        }
        if (orderStatus == OrderStatus.CANCELLED) {
            console(" order is already cancelled");
            m_state = State.done;
            return;
        }
        onBookUpdatedInt(orderBook);
    }

    //-------------------------------------------------------------------
    enum State {
        none,
        submitted,
        done,
        error,
    }
}
