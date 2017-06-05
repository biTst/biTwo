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
                Exchange ex = new Exchange(name);
                initExchange(prop, name, ex);
            }
        } else {
            throw new RuntimeException("exchanges list not found in cfg");
        }
    }

    private static void initExchange(Properties prop, String name, Exchange ex) {
        String pairsStr = prop.getProperty(name + ".pairs");
        System.out.println("  pairs=" + pairsStr);
        if (pairsStr != null) {
            String[] pairs = pairsStr.split(";");
            for (String pairName : pairs) {
                System.out.println("   pair=" + pairName);
                Pair pair = Pair.getByNameInt(pairName);
                if (pair == null) { // create on demand
                    String[] currencies = pairName.split("_");
                    String from = currencies[0];
                    Currency fromCur = Currency.getByName(from);
                    System.out.println("    from=" + from + "; curr=" + fromCur);
                    if (fromCur != null) {
                        String to = currencies[1];
                        System.out.println("    to=" + to);
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
            }
        } else {
            throw new RuntimeException(name + " exchange Pairs not found in cfg");
        }
    }
}
