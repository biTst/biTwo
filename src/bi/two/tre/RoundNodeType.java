package bi.two.tre;

import bi.two.exch.*;
import bi.two.util.Log;

import java.util.List;

public enum RoundNodeType {
    MKT {
        @Override public String getPrefix() { return "mkt"; }
        @Override public double fee(ExchPairData exchPairData) { return exchPairData.m_commission; }
        @Override public double distribute(PairData pd, RoundData roundData, OrderSide orderSide, OrderBook orderBook, CurrencyValue value, List<RoundNodePlan.RoundStep> steps) {
            boolean log = Tre.LOG_MKT_DISTRIBUTION;

            ExchPairData exchPairData = pd.m_exchPairData;
            // we should match book size
            OrderSide oppositeSide = orderSide.opposite();
            Pair pair = exchPairData.m_pair;
            Currency bookCurrency = pair.m_from;
            Currency bookCurrency2 = pair.m_to;

            CurrencyValue minPassThruOrderSize = (roundData != null)
                    ? roundData.m_minPassThruOrdersSize.get(pair)
                    : exchPairData.m_minOrderToCreate;

            List<OrderBook.OrderBookEntry> bookSide = orderSide.isBuy()
                    ? orderBook.m_asks // byu
                    : orderBook.m_bids; // sell

            CurrencyValue minOrderStep = exchPairData.m_minOrderStep;
            if (log) {
                log("          rate for " + value + "; minOrderStep=" + minOrderStep.format8() + "; pair=" + pair + "; bookSide: " + bookSide);
            }

            double volume = 0;
            int index = 0;
            Currency valueCurrency = value.m_currency;
            double toDistribute = value.m_value;
            double remainedSize = toDistribute;
            while(remainedSize > 0) {
                int bookSize = bookSide.size();
                if (index == bookSize) {
                    log("          weak book: index=" + index + "; bookSize=" + bookSize);
                    return 0;
                }
                OrderBook.OrderBookEntry bookEntry = bookSide.get(index);
                double entrySize = bookEntry.m_size;
                double entryPrice = bookEntry.m_price;
                if (log) {
                    log("          " + orderSide + ": book entry[" + index + "]: " + bookEntry +
                            "; we can " + orderSide + " " + entrySize + " " + bookCurrency + " @ " + entryPrice + " " + bookCurrency2 + " per " + bookCurrency);
                }
                if (orderSide.isSell()) {
                    if (entrySize < remainedSize) {
                        if (log) {
                            log("           sell book entry has not enough. want " + remainedSize + " " + bookCurrency + " but have only " + entrySize + " " + bookCurrency
                                    + "; minPassThruOrderSize=" + minPassThruOrderSize);
                        }
                        double minPassThruOrderSizeValue = minPassThruOrderSize.m_value;
                        if ((entrySize < minPassThruOrderSizeValue) || (index > 0)) {
                            if (log) {
                                log("            entry has " + entrySize + " but minPassThru is " + minPassThruOrderSizeValue + " -> need use next book level");
                            }
                            double entryVolume = entrySize * entryPrice;
                            volume += entryVolume;
                            remainedSize -= entrySize;
                            createRoundStep(pd, orderSide, new CurrencyValue(entrySize, valueCurrency), entryPrice, steps);
                            if (log) {
                                log("             entry gives " + entryVolume + " " + bookCurrency2 + "; volume=" + volume + "; remainedSize=" + remainedSize);
                            }
                        } else {
                            double scaleRate = entrySize / remainedSize;
                            if (log) {
                                log("            sell entry has enough " + entrySize + " for minPassThru " + minPassThruOrderSizeValue + " -> need scale @ rate: "
                                        + entrySize + " / " + remainedSize + " = " + scaleRate);
                            }
                            return -scaleRate;
                        }
                    } else {
                        if (log) {
                            log("           book entry has enough. want " + orderSide + " " + remainedSize + " " + bookCurrency + ", available " + entrySize + " " + bookCurrency);
                        }
                        double sizeVolume = remainedSize * entryPrice;
                        volume += sizeVolume;
                        createRoundStep(pd, orderSide, new CurrencyValue(remainedSize, valueCurrency), entryPrice, steps);
                        remainedSize = 0;
                        if (log) {
                            log("            remained gives " + sizeVolume + " " + bookCurrency2 + "; volume=" + volume);
                        }
                    }
                } else { // buy case
                    double entryGives = entrySize * entryPrice;
                    if (log) {
                        log("           buy book entry " + entrySize + " " + bookCurrency + " gives " + entryGives + " " + bookCurrency2 );
                    }
                    String oppositeName = oppositeSide.toString().toLowerCase();
                    if (entryGives < remainedSize) {
                        if (log) {
                            log("           buy book entry has not enough. want " + oppositeName + " " + remainedSize + " " + bookCurrency2
                                    + " entry gives only " + entryGives + " " + bookCurrency2  + "; minPassThruOrderSize=" + minPassThruOrderSize);
                        }
                        double minPassThruOrderSizeValue = minPassThruOrderSize.m_value;
                        if ((entrySize < minPassThruOrderSizeValue) || (index > 0)) {
                            if (log) {
                                log("            entry has " + entrySize + " " + bookCurrency + " but minPassThru is " + minPassThruOrderSizeValue + " -> need use next book level");
                            }
                            volume += entrySize;
                            remainedSize -= entryGives;
                            createRoundStep(pd, orderSide, new CurrencyValue(entryGives, valueCurrency), entryPrice, steps);
                            if (log) {
                                log("             entry gives " + entryGives + " " + bookCurrency2 + "; volume=" + volume + "; remainedSize=" + remainedSize);
                            }
                        } else {
                            double scaleRate = entryGives/remainedSize;
                            if (log) {
                                log("            buy entry has enough " + entrySize + " " + bookCurrency + " for minPassThru " + minPassThruOrderSizeValue + " -> need scale @ rate: "
                                        + entryGives + "/" + remainedSize + " = " + scaleRate);
                            }
                            return -scaleRate;
                        }
                    } else {
                        double sizeVolume = remainedSize / entryPrice;
                        if (log) {
                            log("            book entry gives enough: want " + oppositeName + " " + remainedSize + " " + bookCurrency2
                                    + " can " + oppositeName + " " + entryGives + " " + bookCurrency2);
                        }
                        volume += sizeVolume;
                        createRoundStep(pd, orderSide, new CurrencyValue(remainedSize, valueCurrency), entryPrice, steps);
                        remainedSize = 0;
                        if (log) {
                            log("             remained gives " + sizeVolume + " " + bookCurrency + "; volume=" + volume);
                        }
                    }
                }
                index++;
            }

            if (remainedSize == 0) {
                double rate = orderSide.isSell()
                              ? volume / toDistribute
                              : toDistribute / volume ;
                if (log) {
                    log("          all distributed(steps=" + index + "): volume=" + volume + "; toDistribute=" + toDistribute + " => rate=" + rate);
                }
                return rate;
            }

            double rate = bookSide.get(0).m_price;
            log("          NOT all distributed(steps=" + index + "): volume=" + volume + "; toDistribute=" + toDistribute + " => rate=" + rate);
            return rate;
        }
    },
    LMT { // best limit price
        @Override public String getPrefix() { return "lmt"; }
        @Override public double fee(ExchPairData exchPairData) { return exchPairData.m_makerCommission; }
        @Override public double distribute(PairData pd, RoundData roundData, OrderSide orderSide, OrderBook orderBook, CurrencyValue value, List<RoundNodePlan.RoundStep> steps) {
System.out.println("distribute: pd=" + pd + "; roundData=" + roundData + "; orderSide=" + orderSide + "; value=" + value);
            OrderBook.Spread topSpread = orderBook.getTopSpread();
            ExchPairData exchPairData = pd.m_exchPairData;
            double step = exchPairData.m_minPriceStep;
            double rate = orderSide.isBuy()
                    ? orderBook.getTopBidPrice() + step
                    : orderBook.getTopAskPrice() - step;
System.out.println(" topSpread=" + topSpread + "; step=" + step + "; rate=" + rate);

            createRoundStep(pd, orderSide, value, rate, steps);
            return rate;
        }
    },
    TCH {
        @Override public String getPrefix() { return "tch"; }
        @Override public double fee(ExchPairData exchPairData) { return exchPairData.m_makerCommission; }
        @Override public double distribute(PairData pd, RoundData roundData, OrderSide orderSide, OrderBook orderBook, CurrencyValue value, List<RoundNodePlan.RoundStep> steps) {
            ExchPairData exchPairData = pd.m_exchPairData;
            double step = exchPairData.m_minPriceStep;
            double rate = orderSide.isBuy()
                    ? orderBook.getTopAskPrice() - step // byu
                    : orderBook.getTopBidPrice() + step; // sell

            createRoundStep(pd, orderSide, value, rate, steps);
            return rate;
        }
    },
    ;

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Throwable t) { Log.err(s, t); }

    public abstract String getPrefix();
    public abstract double fee(ExchPairData exchPairData);
    public abstract double distribute(PairData pd, RoundData roundData, OrderSide orderSide, OrderBook orderBook, CurrencyValue value, List<RoundNodePlan.RoundStep> steps);

    @Override public String toString() { return getPrefix(); }

    protected void createRoundStep(PairData pd, OrderSide orderSide, CurrencyValue value, double rate, List<RoundNodePlan.RoundStep> steps) {
System.out.println("createRoundStep pd=" + pd + "; orderSide=" + orderSide + "; value=" + value + "; rate=" + rate);
        Pair pair = pd.m_pair;
        Currency valueCurrency = value.m_currency;
        boolean isBuy = orderSide.isBuy();
        Currency inCurrency = isBuy ? pair.m_to : pair.m_from;
        Currency outCurrency = isBuy ? pair.m_from : pair.m_to;
        boolean distributeSource = (valueCurrency == inCurrency);
System.out.println(" isBuy=" + isBuy + "; valueCurrency=" + valueCurrency + "; inCurrency=" + inCurrency + "; outCurrency=" + outCurrency + "; distributeSource=" + distributeSource);

        double startValueValue = value.m_value;
        double fee = fee(pd.m_exchPairData); // 0.0023

        // BUY 1 BTC @ 1000 (with 0.001 fee) meant: -1001 USD; +1 BTC
        // SELL 1 BTC @ 1000 (with 0.001 fee) meant: -1 BTC; +999 USD
        double translatedValue = distributeSource
                                    ? isBuy
                                        ? startValueValue / rate
                                        : startValueValue * rate
                                    : isBuy
                                        ? startValueValue * rate
                                        : startValueValue / rate;

        double afterFeeValue = translatedValue * (distributeSource ? (1 - fee) : (1 + fee));
System.out.println(" translatedValue=" + translatedValue + "; afterFeeValue=" + afterFeeValue);

        CurrencyValue inValue = new CurrencyValue(distributeSource ? startValueValue : afterFeeValue, inCurrency);
        CurrencyValue outValue = new CurrencyValue(distributeSource ? afterFeeValue : startValueValue, outCurrency);
System.out.println("  inValue=" + inValue + "; outValue=" + outValue);

        double size = distributeSource
                        ? isBuy ? afterFeeValue : startValueValue
                        : isBuy ? startValueValue : afterFeeValue;
System.out.println("   size=" + size);
        RoundNodePlan.RoundStep roundStep = new RoundNodePlan.RoundStep(pair, orderSide, size, rate, inValue, outValue);
        steps.add(roundStep);
    }
}
