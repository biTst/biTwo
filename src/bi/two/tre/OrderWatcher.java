package bi.two.tre;

import bi.two.exch.Exchange;
import bi.two.exch.OrderBook;
import bi.two.exch.OrderData;
import bi.two.exch.OrderSide;
import bi.two.util.TimeStamp;
import bi.two.util.Utils;

import java.util.concurrent.TimeUnit;

class OrderWatcher extends BaseOrderWatcher {
    public static final long MAX_LIVE_OUT_OF_SPREAD = TimeUnit.SECONDS.toMillis(2);
    public static final long MAX_LIVE_ON_SPREAD_WITH_OTHERS = TimeUnit.SECONDS.toMillis(4);
    public static final long MAX_LIVE_ON_SPREAD = TimeUnit.SECONDS.toMillis(6);
    public static final double OUT_OF_SPREAD_PRICE_STEP_RATE = 0.10;
    public static final double ON_SPREAD_WITH_OTHERS_PRICE_STEP_RATE = 0.15;
    public static final double ON_SPREAD_PRICE_STEP_RATE = 0.20;

    public TimeStamp m_outOfTopSpreadStamp = new TimeStamp(0); // time when order becomes out of spread
    public TimeStamp m_onTopSpreadAloneStamp = new TimeStamp(0); // time when order appears on spread alone
    public TimeStamp m_onTopSpreadWithOthersStamp = new TimeStamp(0); // time when order appears on spread with other
    private OrderBook.Spread m_lastTopSpread;

    public OrderWatcher(Exchange exchange, RoundNodePlan.RoundStep roundStep) {
        super(exchange, roundStep);
    }

    @Override public String toString() {
        return "OrderWatcher: " + m_orderData;
    }

    @Override public boolean onTimerInt() {
        try {
            double priceStepRate = 0;
            boolean move = false;
            long outOfSpreadOld = m_outOfTopSpreadStamp.getPassedMillis();
            if (outOfSpreadOld > MAX_LIVE_OUT_OF_SPREAD) {
                console("onTimer: order out of top spread already " + m_outOfTopSpreadStamp.getPassed() + ". moving...");
                move = true;
                priceStepRate = OUT_OF_SPREAD_PRICE_STEP_RATE;
            } else {
                long onTopSpreadWithOthersOld = m_onTopSpreadWithOthersStamp.getPassedMillis();
                if (onTopSpreadWithOthersOld > MAX_LIVE_ON_SPREAD_WITH_OTHERS) {
                    console("onTimer: order on top spread with others already " + m_onTopSpreadWithOthersStamp.getPassed() + ". moving...");
                    move = true;
                    priceStepRate = ON_SPREAD_WITH_OTHERS_PRICE_STEP_RATE;
                } else {
                    long onTopSpreadOld = m_onTopSpreadAloneStamp.getPassedMillis();
                    if (onTopSpreadOld > MAX_LIVE_ON_SPREAD) {
                        console("onTimer: order on top spread alone already " + m_onTopSpreadAloneStamp.getPassed() + ". moving...");
                        move = true;
                        priceStepRate = ON_SPREAD_PRICE_STEP_RATE;
                    }
                }
            }

            if (move) {
                double minOrderToCreate = m_exchPairData.m_minOrderToCreate.m_value;
                double remained = m_orderData.remained();
                if (remained >= minOrderToCreate) {
                    console(" remained=" + Utils.format8(remained) + "; minOrderToCreate=" + minOrderToCreate);
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
                    console("   price=" + Utils.format8(price) + ";  spread=" + topSpread);

                    String replaceOrderId = m_orderData.m_orderId;
                    OrderData orderData = m_orderData.copyForReplace();
                    orderData.m_price = price;
                    console("    submitOrderReplace: " + orderData);
                    if (Tre.SEND_REPLACE) {
                        orderData.addOrderListener(m_orderListener);
                        m_orderData = orderData;
                        boolean sent = m_exchange.submitOrderReplace(replaceOrderId, orderData);
                        if (sent) {
                            m_outOfTopSpreadStamp.reset(); // reset
                            m_onTopSpreadAloneStamp.reset(); // reset
                            m_onTopSpreadWithOthersStamp.reset(); // reset
                        }
                    }
                } else {
                    console("  remained " + Utils.format8(remained) + " is too low for move order; minOrderToCreate=" + minOrderToCreate);
                }
            }
        } catch (Exception e) {
            String msg = "OrderWatcher.onTimer() error: " + e;
            console(msg);
            err(msg, e);
        }
        return (m_state == State.done);
    }

    @Override protected void onBookUpdatedInt(OrderBook orderBook) {
//        console("OrderWatcher.onOrderBookUpdated() orderBook=" + orderBook);

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
            log("  side price " + (isBuy ? "buy" : "ask") + "Price=" + Utils.format8(sidePrice)
                    + "; delta=" + Utils.format12(delta) + "; minPriceStep=" + Utils.format8(minPriceStep));

            if (Math.abs(delta) < (minPriceStep / 2)) {
                double entrySize = entry.m_size;
                double orderRemained = m_orderData.remained();
                console("  order is on spread side. entrySize=" + Utils.format8(entrySize) + "; orderRemained=" + Utils.format8(orderRemained));
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
}
