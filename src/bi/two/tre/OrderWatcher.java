package bi.two.tre;

import bi.two.exch.*;
import bi.two.util.Log;
import bi.two.util.TimeStamp;
import bi.two.util.Utils;

import java.util.concurrent.TimeUnit;

// -----------------------------------------------------------------------------------------------------------
class OrderWatcher {
    public static final long MAX_LIVE_OUT_OF_SPREAD = TimeUnit.SECONDS.toMillis(4);
    public static final long MAX_LIVE_ON_SPREAD_WITH_OTHERS = TimeUnit.SECONDS.toMillis(6);
    public static final long MAX_LIVE_ON_SPREAD = TimeUnit.SECONDS.toMillis(8);

    public final Exchange m_exchange;
    public final RoundNodePlan.RoundStep m_roundStep;
    private final PairData m_pairData;
    public State m_state = State.none;
    public final ExchPairData m_exchPairData;
    private OrderData m_orderData;
    public TimeStamp m_outOfTopSpreadStamp = new TimeStamp(0); // time when order becomes ot of spread
    public TimeStamp m_onTopSpreadAloneStamp = new TimeStamp(0);
    public TimeStamp m_onTopSpreadWithOthersStamp = new TimeStamp(0);
    private OrderBook.Spread m_lastTopSpread;
    private OrderData.IOrderListener m_orderListener = new OrderData.IOrderListener() {
        @Override public void onUpdated(OrderData orderData) {
            onOrderUpdated(orderData);
        }
    };
    private final OrderBook.IOrderBookListener m_orderBookListener = new OrderBook.IOrderBookListener() {
        @Override public void onOrderBookUpdated(OrderBook orderBook) {
            onBookUpdated(orderBook);
        }
    };

    private static void console(String s) { Log.console(s); }
    private static void log(String s) { Log.log(s); }
    private static void err(String s, Throwable t) { Log.err(s, t); }

    public OrderWatcher(Exchange exchange, RoundNodePlan.RoundStep roundStep) {
        m_exchange = exchange;
        m_roundStep = roundStep;
        Pair pair = roundStep.m_pair;
        m_orderData = new OrderData(m_exchange, null, pair, m_roundStep.m_orderSide, OrderType.LIMIT, m_roundStep.m_rate, m_roundStep.m_orderSize);

        m_pairData = PairData.get(pair);
        m_pairData.addOrderBookListener(m_orderBookListener);
        m_exchPairData = exchange.getPairData(pair);
    }

    @Override public String toString() {
        return "OrderWatcher: " + m_orderData;
    }

    public void start() {
        try {
            console("OrderWatcher.start()");
            m_orderData.addOrderListener(m_orderListener);
            console(" submitOrder: " + m_orderData);
            m_exchange.submitOrder(m_orderData);
            m_state = State.submitted;
        } catch (Exception e) {
            String msg = "OrderWatcher.start() error: " + e;
            console(msg);
            err(msg, e);
        }
    }

    boolean onSecTimer() {
        if (m_state == State.done) {
            return true;
        }
        OrderStatus orderStatus = m_orderData.m_status;
        if (orderStatus == OrderStatus.NEW) {
            console("onSecTimer: order not yet submitted");
            return false;
        }
        if (orderStatus == OrderStatus.FILLED) {
            console("onSecTimer: order is already filled");
            m_state = State.done;
            return true;
        }
        try {
            double priceStepRate = 0;
            boolean move = false;
            long outOfSpreadOld = m_outOfTopSpreadStamp.getPassedMillis();
            if (outOfSpreadOld > MAX_LIVE_OUT_OF_SPREAD) {
                console("onSecTimer: order out of top spread already " + outOfSpreadOld + " ms. moving...");
                move = true;
                priceStepRate = 0.05;
            } else {
                long onTopSpreadWithOthersOld = m_onTopSpreadWithOthersStamp.getPassedMillis();
                if (onTopSpreadWithOthersOld > MAX_LIVE_ON_SPREAD_WITH_OTHERS) {
                    console("onSecTimer: order on top spread with others already " + onTopSpreadWithOthersOld + " ms. moving...");
                    move = true;
                    priceStepRate = 0.1;
                } else {
                    long onTopSpreadOld = m_onTopSpreadAloneStamp.getPassedMillis();
                    if (onTopSpreadOld > MAX_LIVE_ON_SPREAD) {
                        console("onSecTimer: order on top spread alone already " + onTopSpreadOld + " ms. moving...");
                        move = true;
                        priceStepRate = 0.15;
                    }
                }
            }

            if (move) {
                double minOrderToCreate = m_exchPairData.m_minOrderToCreate.m_value;
                double remained = m_orderData.remained();
                console(" remained=" + remained + "; minOrderToCreate=" + minOrderToCreate);
                if (remained < minOrderToCreate) {
                    console("  remained qty is too low for move order" );
                }

                OrderBook orderBook = m_exchPairData.getOrderBook();
                OrderBook.Spread topSpread = orderBook.getTopSpread();
                double orderPrice = m_orderData.m_price;
                double minPriceStep = m_exchPairData.m_minPriceStep;
                console(" orderBook.topSpread=" + topSpread + "; orderPrice=" + orderPrice + "; minPriceStep=" + Utils.format8(minPriceStep));
                OrderSide side = m_orderData.m_side;
                boolean isBuy = side.isBuy();

                double askPrice = topSpread.m_askEntry.m_price;
                double bidPrice = topSpread.m_bidEntry.m_price;
                double topSpreadDiff = askPrice - bidPrice;
                double priceStep = topSpreadDiff * priceStepRate;
                console(" topSpreadDiff=" + Utils.format8(topSpreadDiff) + ";priceStepRate=" + priceStepRate + ";  priceStep=" + Utils.format8(priceStep));
                if (priceStep < minPriceStep) {
                    priceStep = minPriceStep;
                    console("  priceStep fixed to minPriceStep=" + minPriceStep);
                }

                double price = isBuy ? bidPrice + priceStep : askPrice - priceStep;
                console("   price=" + price + ";  spread=" + topSpread);

                String replaceOrderId = m_orderData.m_orderId;
                OrderData orderData = m_orderData.copyForReplace();
                orderData.m_price = price;
                console("    submitOrderReplace: " + orderData);
                if (Tre.SEND_REPLACE) {
                    orderData.addOrderListener(m_orderListener);
                    m_orderData = orderData;
                    m_exchange.submitOrderReplace(replaceOrderId, orderData);
                    m_outOfTopSpreadStamp.reset(); // reset
                    m_onTopSpreadAloneStamp.reset(); // reset
                    m_onTopSpreadWithOthersStamp.reset(); // reset
                }
            }
        } catch (Exception e) {
            String msg = "OrderWatcher.onSecTimer() error: " + e;
            console(msg);
            err(msg, e);
        }
        return (m_state == State.done);
    }

    private void onBookUpdated(OrderBook orderBook) {
//        console("OrderWatcher.onOrderBookUpdated() orderBook=" + orderBook);

        OrderStatus orderStatus = m_orderData.m_status;
        if (orderStatus == OrderStatus.NEW) {
            console(" order not yet submitted");
            return;
        }
        if (orderStatus == OrderStatus.FILLED) {
            console(" order is already filled");
            m_state = State.done;
            return;
        }

        OrderBook.Spread topSpread = orderBook.getTopSpread();
        if ((m_lastTopSpread == null) || !topSpread.equals(m_lastTopSpread)) {
            double orderPrice = m_orderData.m_price;
            console("OrderWatcher.onOrderBookUpdated()  changed topSpread=" + topSpread + "; orderPrice: " + Utils.format8(orderPrice));
            m_lastTopSpread = topSpread;

            OrderSide side = m_orderData.m_side;
            boolean isBuy = side.isBuy();
            OrderBook.OrderBookEntry entry = isBuy ? topSpread.m_bidEntry : topSpread.m_askEntry;
            double sidePrice = entry.m_price;
            double delta = sidePrice - orderPrice;
            double minPriceStep = m_exchPairData.m_minPriceStep;
            console("  side price " + (isBuy ? "buy" : "ask") + "Price=" + Utils.format8(sidePrice)
                    + "; delta=" + Utils.format12(delta) + "; minPriceStep=" + Utils.format8(minPriceStep));

            if (Math.abs(delta) < (minPriceStep / 2)) {
                double entrySize = entry.m_size;
                double orderRemained = m_orderData.remained();
                console("  order is on spread side. entrySize=" + entrySize + "; orderRemained=" + orderRemained);
                if (entrySize > orderRemained) {
                    m_onTopSpreadWithOthersStamp.startIfNeeded();
                    m_onTopSpreadAloneStamp.reset();
                    console("    !!! some other order on the same price for " + m_onTopSpreadWithOthersStamp.getPassed());
                } else {
                    m_onTopSpreadAloneStamp.startIfNeeded();
                    m_onTopSpreadWithOthersStamp.reset();
                    console("    all fine - order on spread side alone for " + m_onTopSpreadAloneStamp.getPassed());
                }
                m_outOfTopSpreadStamp.reset();
            } else { // need order price update
                m_outOfTopSpreadStamp.startIfNeeded();
                m_onTopSpreadAloneStamp.reset();
                m_onTopSpreadWithOthersStamp.reset();
                console("  !!! need order price update for " + m_outOfTopSpreadStamp.getPassed());
            }
        }
    }

    private void onOrderUpdated(OrderData orderData) {
        console("Order.onUpdated() orderData=" + orderData);
        boolean isFilled = orderData.isFilled();
        if (isFilled) {
            console(" order is FILLED => DONE");
            m_pairData.removeOrderBookListener(m_orderBookListener);
            m_state = State.done;
        } else {
            if (orderData.m_status == OrderStatus.ERROR) {
                console(" order in ERROR => FINISHING");
                m_pairData.removeOrderBookListener(m_orderBookListener);
                m_state = State.error;
            }
        }
    }

    //-------------------------------------------------------------------
    private enum State {
        none,
        submitted,
        done,
        error,
    }
}
