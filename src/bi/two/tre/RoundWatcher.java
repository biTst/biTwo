package bi.two.tre;

import bi.two.exch.Exchange;
import bi.two.exch.OrderBook;
import bi.two.exch.OrderData;
import bi.two.exch.OrderSide;
import bi.two.util.Log;
import bi.two.util.TimeStamp;
import bi.two.util.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RoundWatcher implements Tre.IWatcher {
    private final RoundPlan m_lllPlan;
    private final List<RoundNodeWatcher> m_nodeWatchers = new ArrayList<>();

    static void console(String s) { Log.console(s); }
    static void log(String s) { Log.log(s); }
    static void err(String s, Throwable t) { Log.err(s, t); }

    public RoundWatcher(Exchange exchange, RoundPlan plan, RoundPlan lllPlan) {
        m_lllPlan = lllPlan;
        console("about to start plan: " + plan.toString());
        console(" lllPlan: " + lllPlan.toString());

        List<RoundNodePlan> rnps = lllPlan.m_roundNodePlans;
        for (RoundNodePlan rnp : rnps) {
            RoundNodeWatcher rmw = new RoundNodeWatcher(this, exchange, rnp);
            m_nodeWatchers.add(rmw);
        }

        logNodePlans();
    }

    private void logNodePlans() {
        CurrencyValue startValue = m_lllPlan.m_startValue;
        console("  startValue: " + startValue);

        for (RoundNodeWatcher nodeWatcher : m_nodeWatchers) {
            nodeWatcher.logNodePlan();
        }
    }

    @Override public boolean onTimer() {
        for (RoundNodeWatcher nodeWatcher : m_nodeWatchers) {
            boolean orderSent = nodeWatcher.onNodeTimer();
            if (orderSent) {
                break; // send only one order on every cycle
            }
        }
        return (countCompleted() == m_nodeWatchers.size());
    }

    private int countCompleted() {
        int completedCount = 0;
        for (RoundNodeWatcher nodeWatcher : m_nodeWatchers) {
            boolean isCompleted = nodeWatcher.isFinal();
            if (isCompleted) {
                completedCount++;
            }
        }
        return completedCount;
    }

    private void cancelOnError() throws IOException {
        for (RoundNodeWatcher nodeWatcher : m_nodeWatchers) {
            nodeWatcher.cancelOnError();
        }
    }

    public void start() {
        for (RoundNodeWatcher nodeWatcher : m_nodeWatchers) {
//            nodeWatcher.start();
        }
    }


    // -----------------------------------------------------------------------------------------------------------
    private static class RoundNodeWatcher extends BaseOrderWatcher {
        public static final long MAX_LIVE_OUT_OF_SPREAD = TimeUnit.SECONDS.toMillis(2);
        public static final long MAX_LIVE_ON_SPREAD_WITH_OTHERS = TimeUnit.SECONDS.toMillis(4);
        public static final long MAX_LIVE_ON_SPREAD = TimeUnit.SECONDS.toMillis(6);
        public static final double OUT_OF_SPREAD_PRICE_STEP_RATE = 0.10;
        public static final double ON_SPREAD_WITH_OTHERS_PRICE_STEP_RATE = 0.15;
        public static final double ON_SPREAD_PRICE_STEP_RATE = 0.20;

        private final RoundWatcher m_roundWatcher;
        private final RoundNodePlan m_rnpInitial;
        private final OrderBook.Spread m_initialSpread;
        private RoundNodePlan m_rnp;

        public TimeStamp m_outOfTopSpreadStamp = new TimeStamp(0); // time when order becomes out of spread
        public TimeStamp m_onTopSpreadAloneStamp = new TimeStamp(0); // time when order appears on spread alone
        public TimeStamp m_onTopSpreadWithOthersStamp = new TimeStamp(0); // time when order appears on spread with other
        private OrderBook.Spread m_lastTopSpread;

        void console(String s) { super.console(m_pairData + ": " + s); }
        void log(String s) { super.log(m_pairData + ": " + s); }
        void err(String s, Throwable t) { super.err(m_pairData + ": " + s, t); }

        public RoundNodeWatcher(RoundWatcher roundWatcher, Exchange exchange, RoundNodePlan rnp) {
            super(exchange, rnp.m_steps.get(0));
            m_roundWatcher = roundWatcher;
            m_rnpInitial = rnp;
            m_rnp = rnp;
            m_initialSpread = m_pairData.m_orderBook.getTopSpread();
        }

        public void logNodePlan() {
            console("   " + m_rnp);
            console("    " + m_rnp.m_steps.get(0));
            console("     " + m_orderData);
            console("      " + m_pairData.m_orderBook.getTopSpread());

//            double rate = RoundNodeType.FIXED.distribute(pairData, null, orderSide, orderBook, needValue, steps, null);
//            log("         distribute() rate=" + Utils.format8(rate));
//
//            for (RoundNodePlan.RoundStep step : steps) {
//                StringBuilder sb = new StringBuilder();
//                sb.append("          ");
//                step.log(sb);
//                console(sb.toString());
//            }

        }

        boolean onNodeTimer() {
            boolean sent = false;
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
                    console(" remained=" + Utils.format8(remained) + "; minOrderToCreate=" + minOrderToCreate);
                    if (remained >= minOrderToCreate) {
                        OrderBook orderBook = m_exchPairData.getOrderBook();
                        OrderBook.Spread topSpread = orderBook.getTopSpread();
                        double orderPrice = m_orderData.m_price;
                        double minPriceStep = m_exchPairData.m_minPriceStep;
                        console(" orderBook.topSpread=" + topSpread + "; orderPrice=" + orderPrice + "; minPriceStep=" + Utils.format8(minPriceStep));
                        OrderSide side = m_orderData.m_side;
                        boolean isBuy = side.isBuy();

                        int completedCount = m_roundWatcher.countCompleted();
                        if (completedCount > 0) {
                            priceStepRate *= completedCount; // go faster if some nodes are done
                            console(" go faster - some nodes are done; completedCount=" + completedCount);
                        }

                        double askPrice = topSpread.m_askEntry.m_price;
                        double bidPrice = topSpread.m_bidEntry.m_price;
                        double topSpreadDiff = askPrice - bidPrice;
                        double priceStep = topSpreadDiff * priceStepRate;
                        console(" topSpreadDiff=" + Utils.format8(topSpreadDiff) + "; priceStepRate=" + priceStepRate + ";  priceStep=" + Utils.format8(priceStep));
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
//if (Tre.SEND_REPLACE) {
if (false) {
                            orderData.addOrderListener(m_orderListener);
                            m_orderData = orderData;
                            m_exchange.submitOrderReplace(replaceOrderId, orderData);
                            sent = true;
                            m_outOfTopSpreadStamp.reset(); // reset
                            m_onTopSpreadAloneStamp.reset(); // reset
                            m_onTopSpreadWithOthersStamp.reset(); // reset
                        }
                    } else {
                        console("  remained qty is too low for move order" );
                    }
                }
            } catch (Exception e) {
                String msg = "OrderWatcher.onTimer() error: " + e;
                console(msg);
                err(msg, e);
            }
            return sent;
        }

        @Override protected void onBookUpdatedInt(OrderBook orderBook) {
            console("RoundNodeWatcher.onBookUpdatedInt() orderBook=" + orderBook);
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

        @Override protected void onOrderUpdated(OrderData orderData) {
            try {
                super.onOrderUpdated(orderData);
                if (isError()) {
                    console(" error order state -> cancel all other");
                    m_roundWatcher.cancelOnError();
                }
            } catch (Exception e) {
                String msg = "RoundNodeWatcher.onOrderUpdated() error: " + e;
                console(msg);
                err(msg, e);
            }
        }

        public void cancelOnError() throws IOException {
            console("cancelOnError() " + this);
            if (!isFinal()) {
                console(" cancelOrder: " + m_orderData);
                m_exchange.cancelOrder(m_orderData);
            }
        }
    }
}
