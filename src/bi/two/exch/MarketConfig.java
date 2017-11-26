package bi.two.exch;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class MarketConfig {
    public static void initMarkets(boolean verbose) {
        Properties properties = new Properties();
        File file = new File("cfg.cfg");
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
            System.out.println("exchanges=" + exchangesStr);
        }
        if (exchangesStr != null) {
            String[] exchanges = exchangesStr.split(";");
            for (String name : exchanges) {
                if (verbose) {
                    System.out.println(" exchange[" + name + "]");
                }
                initExchange(prop, name, verbose);
            }
        } else {
            throw new RuntimeException("exchanges list not found in cfg");
        }
    }

    private static void initExchange(Properties prop, String name, boolean verbose) {
        String prefix = "exchange." + name;
        String baseCurrencyName = prop.getProperty(prefix + ".baseCurrency");
        if (verbose) {
            System.out.println("  baseCurrencyName=" + baseCurrencyName);
        }
        if (baseCurrencyName != null) {
            Currency baseCurrency = Currency.getByName(baseCurrencyName);
            if (verbose) {
                System.out.println("   baseCurrency=" + baseCurrency);
            }
            if (baseCurrency != null) {
                Exchange ex = new Exchange(name, baseCurrency);
                String scheduleStr = prop.getProperty(prefix + ".schedule");
                if (scheduleStr != null) {
                    Schedule schedule = Schedule.valueOf(scheduleStr);
                    if (verbose) {
                        System.out.println("  schedule=" + scheduleStr + " => " + schedule);
                    }
                    ex.m_schedule = schedule;
                }
                double exchCommission = 0;
                String exchCommissionStr = prop.getProperty(prefix + ".commission");
                if (exchCommissionStr != null) {
                    if (verbose) {
                        System.out.println("  exchCommissionStr: " + exchCommissionStr);
                    }
                    exchCommission = Double.parseDouble(exchCommissionStr);
                }
                double exchMakerCommission = exchCommission;
                String exchMakerCommissionStr = prop.getProperty(prefix + ".makerCommission");
                if (exchMakerCommissionStr != null) {
                    if (verbose) {
                        System.out.println("  makerCommissionStr: " + exchMakerCommissionStr);
                    }
                    exchMakerCommission = Double.parseDouble(exchMakerCommissionStr);
                }
                String pairsStr = prop.getProperty(prefix + ".pairs");
                if (verbose) {
                    System.out.println("  pairs=" + pairsStr);
                }
                if (pairsStr != null) {
                    String[] pairs = pairsStr.split(";");
                    for (String pairName : pairs) {
                        Pair pair = Pair.getByNameInt(pairName);
                        if (verbose) {
                            System.out.println("   pair '" + pairName + "; = " + pair);
                        }
                        if (pair == null) { // create on demand
                            String[] currencies = pairName.split("_");
                            String from = currencies[0];
                            Currency fromCur = Currency.getByName(from);
                            if (verbose) {
                                System.out.println("    from=" + from + "; curr=" + fromCur);
                            }
                            if (fromCur != null) {
                                String to = currencies[1];
                                Currency toCur = Currency.getByName(to);
                                if (verbose) {
                                    System.out.println("    to=" + to + "; curr=" + toCur);
                                }
                                if (toCur != null) {
                                    pair = new Pair(fromCur, toCur);
                                    if (verbose) {
                                        System.out.println("     pair created: " + pair);
                                    }
                                } else {
                                    throw new RuntimeException("unsupported currency '" + to + "'");
                                }
                            } else {
                                throw new RuntimeException("unsupported currency '" + from + "'");
                            }
                        }
                        ExchPairData exchPairData = ex.addPair(pair);
                        String pairPrefix = prefix + ".pair." + pairName;
                        String minOrderStr = prop.getProperty(pairPrefix + ".minOrder");
                        if (minOrderStr != null) {
                            if (verbose) {
                                System.out.println("    minOrderStr: " + minOrderStr);
                            }
                            exchPairData.m_minOrderToCreate = Double.parseDouble(minOrderStr);
                        }

                        String initBalanceStr = prop.getProperty(pairPrefix + ".initBalance");
                        if (initBalanceStr != null) {
                            if (verbose) {
                                System.out.println("    initBalanceStr: " + initBalanceStr);
                            }
                            exchPairData.m_initBalance = Double.parseDouble(initBalanceStr);
                        }

                        double commission = exchCommission;
                        String commissionStr = prop.getProperty(pairPrefix + ".commission");
                        if (commissionStr != null) {
                            if (verbose) {
                                System.out.println("    commissionStr: " + commissionStr);
                            }
                            commission = Double.parseDouble(commissionStr);
                        }
                        exchPairData.m_commission = commission;

                        double makerCommission = commission;
                        String makerCommissionStr = prop.getProperty(pairPrefix + ".makerCommission");
                        if (makerCommissionStr != null) {
                            if (verbose) {
                                System.out.println("    makerCommissionStr: " + makerCommissionStr);
                            }
                            makerCommission = Double.parseDouble(makerCommissionStr);
                        }
                        exchPairData.m_makerCommission = makerCommission;
                    }
                } else {
                    throw new RuntimeException(name + " exchange Pairs not found in cfg");
                }
            } else {
                throw new RuntimeException("invalid baseCurrency '" + baseCurrencyName + "' specified for exchange " + name);
            }
        } else {
            throw new RuntimeException("no baseCurrency specified for exchange " + name);
        }
    }
}
