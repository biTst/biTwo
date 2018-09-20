package bi.two.exch;

import bi.two.exch.schedule.Schedule;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import static bi.two.util.Log.console;

public class MarketConfig {
    private static final String CONFIG_FILE = "mkt_cfg.properties";

    public static void initMarkets(boolean verbose) {
        Properties properties = new Properties();
        File file = new File(CONFIG_FILE);
        boolean exists = file.exists();
        if (exists) {
            try {
                FileInputStream inStream = new FileInputStream(file);
                try {
                    properties.load(inStream);
                } finally {
                    inStream.close();
                }
                init(properties, verbose);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            throw new RuntimeException("cfg file not found: " + file.getAbsolutePath());
        }
    }

    private static void init(Properties prop, boolean verbose) {
        String exchangesStr = prop.getProperty("exchanges");
        if (verbose) {
            console("exchanges=" + exchangesStr);
        }
        if (exchangesStr != null) {
            String[] exchanges = exchangesStr.split(";");
            for (String name : exchanges) {
                if (verbose) {
                    console(" exchange[" + name + "]");
                }
                initExchange(prop, name, verbose);
            }
        } else {
            throw new RuntimeException("exchanges list not found in cfg");
        }
    }

    private static void initExchange(Properties prop, String name, boolean verbose) {
        String prefix = "exchange." + name;
        String impl = prop.getProperty(prefix + ".impl");
        String baseCurrencyName = prop.getProperty(prefix + ".baseCurrency");
        if (verbose) {
            console("  impl=" + impl + "; baseCurrencyName=" + baseCurrencyName);
        }
        if (baseCurrencyName != null) {
            Currency baseCurrency = Currency.getByName(baseCurrencyName);
            if (verbose) {
                console("   baseCurrency=" + baseCurrency);
            }
            Exchange ex = new Exchange(name, impl, baseCurrency);
            String scheduleStr = prop.getProperty(prefix + ".schedule");
            if (scheduleStr != null) {
                Schedule schedule = Schedule.valueOf(scheduleStr);
                if (verbose) {
                    console("  schedule=" + scheduleStr + " => " + schedule);
                }
                ex.m_schedule = schedule;
            }
            double exchCommission = 0;
            String exchCommissionStr = prop.getProperty(prefix + ".commission");
            if (exchCommissionStr != null) {
                if (verbose) {
                    console("  exchCommissionStr: " + exchCommissionStr);
                }
                exchCommission = Double.parseDouble(exchCommissionStr);
            }
            double exchMakerCommission = exchCommission;
            String exchMakerCommissionStr = prop.getProperty(prefix + ".makerCommission");
            if (exchMakerCommissionStr != null) {
                if (verbose) {
                    console("  makerCommissionStr: " + exchMakerCommissionStr);
                }
                exchMakerCommission = Double.parseDouble(exchMakerCommissionStr);
            }
            String pairsStr = prop.getProperty(prefix + ".pairs");
            if (verbose) {
                console("  pairs=" + pairsStr);
            }
            if (pairsStr != null) {
                String[] pairs = pairsStr.split(";");
                for (String pairName : pairs) {
                    Pair pair = Pair.getByNameInt(pairName);
                    if (verbose) {
                        console("   pair '" + pairName + "; = " + pair);
                    }
                    if (pair == null) { // create on demand
                        String[] currencies = pairName.split("_");
                        String from = currencies[0];
                        Currency fromCur = Currency.getByName(from);
                        if (verbose) {
                            console("    from=" + from + "; curr=" + fromCur);
                        }
                        String to = currencies[1];
                        Currency toCur = Currency.getByName(to);
                        if (verbose) {
                            console("    to=" + to + "; curr=" + toCur);
                        }
                        pair = Pair.get(fromCur, toCur);
                        if (verbose) {
                            console("     pair created: " + pair);
                        }
                    }
                    ExchPairData exchPairData = ex.addPair(pair);
                    String pairPrefix = prefix + ".pair." + pairName;

                    String minOrderStr = prop.getProperty(pairPrefix + ".minOrder"); // 0.01btc
                    if (minOrderStr != null) {
                        if (verbose) {
                            console("    minOrderStr: " + minOrderStr);
                        }
                        CurrencyValue currencyValue = parseCurrencyValue(minOrderStr, pair, baseCurrency);
                        if (verbose) {
                            console("     currencyValue: " + currencyValue);
                        }
                        exchPairData.m_minOrderToCreate = currencyValue;
                    }

                    String minOrderStepStr = prop.getProperty(pairPrefix + ".minOrderStep"); // 0.01btc
                    if (minOrderStepStr != null) {
                        if (verbose) {
                            console("    minOrderStepStr: " + minOrderStepStr);
                        }
                        CurrencyValue currencyValue = parseCurrencyValue(minOrderStepStr, pair, baseCurrency);
                        if (verbose) {
                            console("     currencyValue: " + currencyValue);
                        }
                        exchPairData.setMinOrderStep(currencyValue);
                    }

                    String minPriceStepStr = prop.getProperty(pairPrefix + ".minPriceStep");
                    if (minPriceStepStr != null) {
                        if (verbose) {
                            console("    minPriceStepStr: " + minPriceStepStr);
                        }
                        exchPairData.setMinPriceStep(Double.parseDouble(minPriceStepStr));
                    }

                    String initBalanceStr = prop.getProperty(pairPrefix + ".initBalance");
                    if (initBalanceStr != null) {
                        if (verbose) {
                            console("    initBalanceStr: " + initBalanceStr);
                        }
                        exchPairData.m_initBalance = Double.parseDouble(initBalanceStr);
                    }

                    double commission = exchCommission;
                    double makerCommission = exchMakerCommission;
                    String commissionStr = prop.getProperty(pairPrefix + ".commission");
                    if (commissionStr != null) {
                        if (verbose) {
                            console("    commissionStr: " + commissionStr);
                        }
                        commission = Double.parseDouble(commissionStr);
                        makerCommission = commission;
                    }
                    exchPairData.m_commission = commission;

                    String makerCommissionStr = prop.getProperty(pairPrefix + ".makerCommission");
                    if (makerCommissionStr != null) {
                        if (verbose) {
                            console("    makerCommissionStr: " + makerCommissionStr);
                        }
                        makerCommission = Double.parseDouble(makerCommissionStr);
                    }
                    exchPairData.m_makerCommission = makerCommission;
                }
            } else {
                throw new RuntimeException(name + " exchange Pairs not found in cfg");
            }

        } else {
            throw new RuntimeException("no baseCurrency specified for exchange " + name);
        }
    }

    private static CurrencyValue parseCurrencyValue(String str, Pair pair, Currency baseCurrency) {
        Currency currencyFrom = pair.m_from;
        String srcCurName = currencyFrom.m_name;
        if (str.endsWith(srcCurName)) {
            String value = str.substring(0, str.length() - srcCurName.length());
            double doubleValue = Double.parseDouble(value);
            return new CurrencyValue(doubleValue, currencyFrom);
        } else {
            Currency currencyTo = pair.m_to;
            String dstCurName = currencyTo.m_name;
            if (str.endsWith(dstCurName)) {
                String value = str.substring(0, str.length() - dstCurName.length());
                double doubleValue = Double.parseDouble(value);
                return new CurrencyValue(doubleValue, currencyTo);
            } else {
                double value = Double.parseDouble(str);
                return new CurrencyValue(value, baseCurrency);
            }
        }
    }
}
