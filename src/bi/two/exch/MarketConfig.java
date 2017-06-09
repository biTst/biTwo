package bi.two.exch;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class MarketConfig {
    public static void initMarkets() {
        Properties properties = new Properties();
        File file = new File("cfg.cfg");
        boolean exists = file.exists();
        if (exists) {
            try {
                properties.load(new FileInputStream(file));
                init(properties);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            throw new RuntimeException("cfg file not found: " + file.getAbsolutePath());
        }
    }

    private static void init(Properties prop) {
        String exchangesStr = prop.getProperty("exchanges");
        System.out.println("exchanges=" + exchangesStr);
        if (exchangesStr != null) {
            String[] exchanges = exchangesStr.split(";");
            for (String name : exchanges) {
                System.out.println(" exchange[" + name + "]");
                initExchange(prop, name);
            }
        } else {
            throw new RuntimeException("exchanges list not found in cfg");
        }
    }

    private static void initExchange(Properties prop, String name) {
        String prefix = "exchange." + name;
        String baseCurrencyName = prop.getProperty(prefix + ".baseCurrency");
        System.out.println("  baseCurrencyName=" + baseCurrencyName);
        if (baseCurrencyName != null) {
            Currency baseCurrency = Currency.getByName(baseCurrencyName);
            System.out.println("   baseCurrency=" + baseCurrency);
            if (baseCurrency != null) {
                Exchange ex = new Exchange(name, baseCurrency);
                String pairsStr = prop.getProperty(prefix + ".pairs");
                System.out.println("  pairs=" + pairsStr);
                if (pairsStr != null) {
                    String[] pairs = pairsStr.split(";");
                    for (String pairName : pairs) {
                        Pair pair = Pair.getByNameInt(pairName);
                        System.out.println("   pair '" + pairName + "; = " + pair);
                        if (pair == null) { // create on demand
                            String[] currencies = pairName.split("_");
                            String from = currencies[0];
                            Currency fromCur = Currency.getByName(from);
                            System.out.println("    from=" + from + "; curr=" + fromCur);
                            if (fromCur != null) {
                                String to = currencies[1];
                                Currency toCur = Currency.getByName(to);
                                System.out.println("    to=" + to + "; curr=" + toCur);
                                if (toCur != null) {
                                    pair = new Pair(fromCur, toCur);
                                    System.out.println("     pair created: " + pair);

                                } else {
                                    throw new RuntimeException("unsupported currency '" + to + "'");
                                }
                            } else {
                                throw new RuntimeException("unsupported currency '" + from + "'");
                            }
                        }
                        ExchPairData exchPairData = ex.addPair(pair);
                        String minOrderStr = prop.getProperty(prefix + ".pair."+pairName+".minOrder");
                        System.out.println("    minOrderStr: " + minOrderStr);
                        exchPairData.minOrderToCreate = Double.parseDouble(minOrderStr);
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
