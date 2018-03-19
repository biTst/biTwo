package bi.two;

import bi.two.exch.ExchPairData;
import bi.two.exch.Exchange;
import bi.two.exch.MarketConfig;
import bi.two.exch.Pair;
import bi.two.util.Log;
import bi.two.util.MapConfig;

import java.io.IOException;

public class Main2 {

    private static void console(String s) { Log.console(s); }
    private static void log(String s) { Log.log(s); }
    private static void err(String s, Throwable t) { Log.err(s, t); }

    public static void main(final String[] args) {
        Log.s_impl = new Log.StdLog();
        MarketConfig.initMarkets(false);

        new Thread("MAIN") {
            @Override public void run() {
                setPriority(Thread.NORM_PRIORITY - 1); // smaller prio
                runMain();
            }
        }.start();
    }

    private static void runMain() {
        try {
            String file = "cfg\\main2.properties";
            MapConfig config = new MapConfig();
//            config.loadAndEncrypted(file);
            config.load(file);

            String exchangeName = config.getString("exchange");
            Exchange exchange = Exchange.get(exchangeName);
            String pairName = config.getString("pair");
            Pair pair = Pair.getByName(pairName);
            ExchPairData pairData = exchange.getPairData(pair);
        } catch (IOException e) {
            err("main error: " + e, e);
        }
    }
}
