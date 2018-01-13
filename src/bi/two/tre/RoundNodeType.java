package bi.two.tre;

import bi.two.exch.*;

import java.util.List;

public enum RoundNodeType {
    MKT {
        @Override public String getPrefix() { return "mkt"; }
        @Override public double fee(ExchPairData exchPairData) { return exchPairData.m_commission; }
        @Override public double rate(PairData pd, RoundData roundData, OrderSide orderSide, OrderBook orderBook, CurrencyValue value) {
            ExchPairData exchPairData = pd.m_exchPairData;
            // we should match book size
            OrderSide oppositeSide = orderSide.opposite();
            Pair pair = exchPairData.m_pair;
            Currency bookCurrency = pair.m_from;
            Currency bookCurrency2 = pair.m_to;

            List<OrderBook.OrderBookEntry> bookSide = orderSide.isBuy()
                    ? orderBook.m_asks // byu
                    : orderBook.m_bids; // sell

            CurrencyValue minOrderStep = exchPairData.m_minOrderStep;
            if (LOG_MKT_DISTRIBUTION) {
                System.out.println("          rate for " + value + "; minOrderStep=" + minOrderStep.format8() + "; pair=" + pair + "; bookSide: " + bookSide);
            }

            double volume = 0;
            int index = 0;
            double toDistribute = value.m_value;
            double remainedSize = toDistribute;
            while(remainedSize > 0) {
                int bookSize = bookSide.size();
                if (index == bookSize) {
                    System.out.println("          weak book: index=" + index + "; bookSize=" + bookSize);
                    return 0;
                }
                OrderBook.OrderBookEntry bookEntry = bookSide.get(index);
                double entrySize = bookEntry.m_size;
                double entryPrice = bookEntry.m_price;
                if (LOG_MKT_DISTRIBUTION) {
                    System.out.println("          " + orderSide + ": book entry[" + index + "]: " + bookEntry +
                            "; we can " + orderSide + " " + entrySize + " " + bookCurrency + " @ " + entryPrice + " " + bookCurrency2 + " per " + bookCurrency);
                }
                if (orderSide.isSell()) {
                    if (entrySize < remainedSize) {
                        CurrencyValue minPassThruOrderSize = roundData.m_minPassThruOrdersSize.get(pair);
                        if (LOG_MKT_DISTRIBUTION) {
                            System.out.println("           sell book entry has not enough. want " + remainedSize + " " + bookCurrency + " but have only " + entrySize + " " + bookCurrency
                                    + "; minPassThruOrderSize=" + minPassThruOrderSize);
                        }
                        double minPassThruOrderSizeValue = minPassThruOrderSize.m_value;
                        if ((entrySize < minPassThruOrderSizeValue) || (index > 0)) {
                            if (LOG_MKT_DISTRIBUTION) {
                                System.out.println("            entry has " + entrySize + " but minPassThru is " + minPassThruOrderSizeValue + " -> need use next book level");
                            }
                            double entryVolume = entrySize * entryPrice;
                            volume += entryVolume;
                            remainedSize -= entrySize;
                            if (LOG_MKT_DISTRIBUTION) {
                                System.out.println("             entry gives " + entryVolume + " " + bookCurrency2 + "; volume=" + volume + "; remainedSize=" + remainedSize);
                            }
                        } else {
                            double scaleRate = entrySize / remainedSize;
                            if (LOG_MKT_DISTRIBUTION) {
                                System.out.println("            sell entry has enough " + entrySize + " for minPassThru " + minPassThruOrderSizeValue + " -> need scale @ rate: "
                                        + entrySize + " / " + remainedSize + " = " + scaleRate);
                            }
                            return -scaleRate;
                        }
                    } else {
                        if (LOG_MKT_DISTRIBUTION) {
                            System.out.println("           book entry has enough. want " + orderSide + " " + remainedSize + " " + bookCurrency + ", available " + entrySize + " " + bookCurrency);
                        }
                        double sizeVolume = remainedSize * entryPrice;
                        volume += sizeVolume;
                        remainedSize = 0;
                        if (LOG_MKT_DISTRIBUTION) {
                            System.out.println("            remained gives " + sizeVolume + " " + bookCurrency2 + "; volume=" + volume);
                        }
                    }
                } else { // buy case
                    double entryGives = entrySize * entryPrice;
                    if (LOG_MKT_DISTRIBUTION) {
                        System.out.println("           buy book entry " + entrySize + " " + bookCurrency + " gives " + entryGives + " " + bookCurrency2 );
                    }
                    String oppositeName = oppositeSide.toString().toLowerCase();
                    if (entryGives < remainedSize) {
                        CurrencyValue minPassThruOrderSize = roundData.m_minPassThruOrdersSize.get(pair);
                        if (LOG_MKT_DISTRIBUTION) {
                            System.out.println("           buy book entry has not enough. want " + oppositeName + " " + remainedSize + " " + bookCurrency2
                                    + " entry gives only " + entryGives + " " + bookCurrency2  + "; minPassThruOrderSize=" + minPassThruOrderSize);
                        }
                        double minPassThruOrderSizeValue = minPassThruOrderSize.m_value;
                        if ((entrySize < minPassThruOrderSizeValue) || (index > 0)) {
                            if (LOG_MKT_DISTRIBUTION) {
                                System.out.println("            entry has " + entrySize + " " + bookCurrency + " but minPassThru is " + minPassThruOrderSizeValue + " -> need use next book level");
                            }
                            volume += entrySize;
                            remainedSize -= entryGives;
                            if (LOG_MKT_DISTRIBUTION) {
                                System.out.println("             entry gives " + entryGives + " " + bookCurrency2 + "; volume=" + volume + "; remainedSize=" + remainedSize);
                            }
                        } else {
                            double scaleRate = entryGives/remainedSize;
                            if (LOG_MKT_DISTRIBUTION) {
                                System.out.println("            buy entry has enough " + entrySize + " " + bookCurrency + " for minPassThru " + minPassThruOrderSizeValue + " -> need scale @ rate: "
                                        + entryGives + "/" + remainedSize + " = " + scaleRate);
                            }
                            return -scaleRate;
                        }
                    } else {
                        double sizeVolume = remainedSize / entryPrice;
                        if (LOG_MKT_DISTRIBUTION) {
                            System.out.println("            book entry gives enough: want " + oppositeName + " " + remainedSize + " " + bookCurrency2
                                    + " can " + oppositeName + " " + entryGives + " " + bookCurrency2);
                        }
                        volume += sizeVolume;
                        remainedSize = 0;
                        if (LOG_MKT_DISTRIBUTION) {
                            System.out.println("             remained gives " + sizeVolume + " " + bookCurrency + "; volume=" + volume);
                        }
                    }
                }
                index++;
            }

            if (remainedSize == 0) {
                double rate = orderSide.isSell()
                              ? volume / toDistribute
                              : toDistribute / volume ;
                if (LOG_MKT_DISTRIBUTION) {
                    System.out.println("          all distributed(steps=" + index + "): volume=" + volume + "; toDistribute=" + toDistribute + " => rate=" + rate);
                }
                return rate;
            }

            double rate = bookSide.get(0).m_price;
            System.out.println("          NOT all distributed(steps=" + index + "): volume=" + volume + "; toDistribute=" + toDistribute + " => rate=" + rate);
            return rate;
        }
    },
    LMT { // best limit price
        @Override public String getPrefix() { return "lmt"; }
        @Override public double fee(ExchPairData exchPairData) { return exchPairData.m_makerCommission; }
        @Override public double rate(PairData pd, RoundData roundData, OrderSide orderSide, OrderBook orderBook, CurrencyValue value) {
            ExchPairData exchPairData = pd.m_exchPairData;
            double step = exchPairData.m_minPriceStep;
            return orderSide.isBuy()
                    ? orderBook.getTopBidPrice() + step
                    : orderBook.getTopAskPrice() - step;
        }
    },
    TCH {
        @Override public String getPrefix() { return "tch"; }
        @Override public double fee(ExchPairData exchPairData) { return exchPairData.m_makerCommission; }
        @Override public double rate(PairData pd, RoundData roundData, OrderSide orderSide, OrderBook orderBook, CurrencyValue value) {
            ExchPairData exchPairData = pd.m_exchPairData;
            double step = exchPairData.m_minPriceStep;
            return orderSide.isBuy()
                    ? orderBook.getTopAskPrice() - step // byu
                    : orderBook.getTopBidPrice() + step; // sell
        }
    },
    ;

    private static boolean LOG_MKT_DISTRIBUTION = Tre.LOG_MKT_DISTRIBUTION;

    public abstract String getPrefix();
    public abstract double fee(ExchPairData exchPairData);
    public abstract double rate(PairData pd, RoundData roundData, OrderSide orderSide, OrderBook orderBook, CurrencyValue value);

    @Override public String toString() { return getPrefix(); }
}
