package bi.two.opt;

import bi.two.Main;
import bi.two.algo.Algo;
import bi.two.algo.BaseAlgo;
import bi.two.algo.Watcher;
import bi.two.chart.TickData;
import bi.two.exch.Exchange;
import bi.two.exch.Pair;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.ParallelTimesSeriesData;
import bi.two.ts.TimesSeriesData;
import bi.two.util.MapConfig;

import java.util.ArrayList;
import java.util.List;

public class WatchersProducer {
    public static final String OPT_KEY = "opt";
    public static final String ITERATE_KEY = "iterate";

    private List<BaseProducer> m_producers = new ArrayList<>();

    public WatchersProducer(MapConfig config, MapConfig algoConfig) {
        String optimizeCfgStr = config.getPropertyNoComment(OPT_KEY);
        if (optimizeCfgStr != null) {
            List<List<OptimizeConfig>> optimizeConfigs = parseOptimizeConfigs(optimizeCfgStr, config);
            for (List<OptimizeConfig> optimizeConfig : optimizeConfigs) {
                BaseProducer optimizeProducer;
                optimizeProducer = (optimizeConfig.size() > 1)
                        ? new MultiDimensionalOptimizeProducer(optimizeConfig, algoConfig)
                        : new SingleDimensionalOptimizeProducer(optimizeConfig, algoConfig);
                m_producers.add(optimizeProducer);
            }
        } else {
            String iterateCfgStr = config.getPropertyNoComment(ITERATE_KEY);
            if (iterateCfgStr != null) {
                List<List<IterateConfig>> iterateConfigs = parseIterateConfigs(iterateCfgStr, config);
                BaseProducer iterateProducer = new IterateProducer(iterateConfigs);
                m_producers.add(iterateProducer);
            } else {
                m_producers.add(new SingleProducer());
            }
        }
    }

    private static List<List<OptimizeConfig>> parseOptimizeConfigs(String optimizeCfgStr, MapConfig config) {
        List<List<OptimizeConfig>> optimizeConfigs = new ArrayList<>();
        String[] split = optimizeCfgStr.split("\\|");
        for (String s : split) {
            String namedOptimizeCfgStr = config.getPropertyNoComment(s);
            if (namedOptimizeCfgStr != null) {
                s = namedOptimizeCfgStr;
            }
            List<OptimizeConfig> oc = parseOptimizeConfig(s, config);
            if (!oc.isEmpty()) {
                optimizeConfigs.add(oc);
            }
        }
        return optimizeConfigs;
    }

    private static List<OptimizeConfig> parseOptimizeConfig(String optimizeCfgStr, MapConfig config) { // smooth=2.158+-5*0.01&threshold=0.158+-5*0.001
        List<OptimizeConfig> ret = new ArrayList<>();
        String[] split = optimizeCfgStr.split("\\&"); // [ "divider=26.985[3,40]", "reverse=0.0597[0.01,1.0]" ]
        for (String s : split) { // "reverse=0.0597[0.01,1.0]"
            String namedOptimizeCfgStr = config.getPropertyNoComment(s);
            if (namedOptimizeCfgStr != null) {
                s = namedOptimizeCfgStr;
            }
            String[] split2 = s.split("\\=");
            if (split2.length == 2) {
                String name = split2[0];
                String cfg = split2[1];
                try {
                    Vary vary = Vary.valueOf(name);
                    OptimizeConfig optimizeConfig = OptimizeConfig.parseOptimize(cfg, vary, config);
                    ret.add(optimizeConfig);
                } catch (IllegalArgumentException e) {
                    throw new RuntimeException("vary not found with name " + name + " for config: " + s);
                }
            } else {
                System.out.println("IterateConfig '" + s + "' is invalid");
            }
        }
        return ret;
    }

    private static List<List<IterateConfig>> parseIterateConfigs(String iterateCfgStr, MapConfig config) {
        List<List<IterateConfig>> iterateConfigs = new ArrayList<>();
        String[] split = iterateCfgStr.split("\\|");
        for (String s : split) {
            String namedIterateCfgStr = config.getPropertyNoComment(s);
            if (namedIterateCfgStr != null) {
                s = namedIterateCfgStr;
            }
            List<IterateConfig> ic = parseIterateConfig(s);
            if (!ic.isEmpty()) {
                iterateConfigs.add(ic);
            }
        }
        return iterateConfigs;
    }

    private static List<IterateConfig> parseIterateConfig(String iterateCfg) { // smooth=2.158+-5*0.01&threshold=0.158+-5*0.001
        List<IterateConfig> ret = new ArrayList<>();
        String[] split = iterateCfg.split("\\&"); // [ "smooth=2.158+-5*0.01", "threshold=0.158+-5*0.001" ]
        for (String s : split) { // "smooth=2.158+-5*0.01"
            String[] split2 = s.split("\\=");
            if (split2.length == 2) {
                String name = split2[0];
                String cfg = split2[1];
                try {
                    Vary vary = Vary.valueOf(name);
                    IterateConfig iterateConfig = IterateConfig.parseIterate(cfg, vary);
                    ret.add(iterateConfig);
                } catch (IllegalArgumentException e) {
                    throw new RuntimeException("vary not found with name " + name + " for config: " + s);
                }
            } else {
                System.out.println("IterateConfig '" + s + "' is invalid");
            }
        }
        return ret;
    }

    public boolean isActive() {
        for (BaseProducer producer : m_producers) {
            if (producer.isActive()) {
                return true;
            }
        }
        return false; // all inactive
    }

    public List<Watcher> getWatchers(MapConfig algoConfig, TimesSeriesData<TickData> ticksTs0, MapConfig config, Exchange exchange, Pair pair) {
        int parallel = config.getInt("parallel");
        BaseTimesSeriesData ticksTs = new ParallelTimesSeriesData(ticksTs0, parallel);

        List<Watcher> watchers = new ArrayList<>();
        for (BaseProducer producer : m_producers) {
            if (producer.isActive()) {
                producer.getWatchers(config, algoConfig, ticksTs, exchange, pair, watchers);
            }
        }

        return watchers;
    }

    public BaseProducer logResults() {
        BaseProducer bestProducer = null;
        double bestTotalPriceRatio = 0;
        for (BaseProducer producer : m_producers) {
            double totalPriceRatio = producer.logResults();
            if(totalPriceRatio > bestTotalPriceRatio) {
                bestTotalPriceRatio = totalPriceRatio;
                bestProducer = producer;
            }
        }
        return bestProducer;
    }

    //=============================================================================================
    private static class IterateProducer extends BaseProducer {
        private final List<List<IterateConfig>> m_iterateConfigs;
        private List<AlgoWatcher> m_watchers = new ArrayList<>();

        public IterateProducer(List<List<IterateConfig>> iterateConfigs) {
            m_iterateConfigs = iterateConfigs;
        }

        @Override public void getWatchers(MapConfig config, MapConfig algoConfig, BaseTimesSeriesData ticksTs, Exchange exchange, Pair pair, List<Watcher> watchers) {
            for (List<IterateConfig> iterateConfig : m_iterateConfigs) {
                MapConfig localAlgoConfig = algoConfig.copy();
                doIterate(config, iterateConfig, 0, localAlgoConfig, ticksTs, exchange, pair, watchers);
            }
            m_active = false;
        }

        private void doIterate(final MapConfig config, final List<IterateConfig> iterateConfigs, int index, final MapConfig algoConfig, final BaseTimesSeriesData ticksTs,
                               final Exchange exchange, final Pair pair, final List<Watcher> watchers) {
            final int nextIndex = index + 1;
            final IterateConfig iterateConfig = iterateConfigs.get(index);
            final Vary vary = iterateConfig.m_vary;
            vary.m_varyType.iterate(iterateConfig, new Main.IParamIterator<Number>() {
                @Override public void doIteration(Number value) {
                    algoConfig.put(vary.m_key, value);
                    if (nextIndex < iterateConfigs.size()) {
                        doIterate(config, iterateConfigs, nextIndex, algoConfig, ticksTs, exchange, pair, watchers);
                    } else {
                        AlgoWatcher watcher = new AlgoWatcher(config, algoConfig, exchange, pair, ticksTs);
                        m_watchers.add(watcher);
                        watchers.add(watcher);
                    }
                }
            });
        }

        @Override public double logResults() {
            AlgoWatcher bestWatcher = findBestWatcher();
            System.out.println("IterateProducer result: " + bestWatcher);
            return bestWatcher.totalPriceRatio();
        }

        private AlgoWatcher findBestWatcher() {
            AlgoWatcher bestWatcher = null;
            double bestTotalPriceRatio = 0;
            for (AlgoWatcher watcher : m_watchers) {
                double totalPriceRatio = watcher.totalPriceRatio();
                if(totalPriceRatio > bestTotalPriceRatio) {
                    bestTotalPriceRatio = totalPriceRatio;
                    bestWatcher = watcher;
                }
            }
            return bestWatcher;
        }
    }

    //=============================================================================================
    private static class SingleProducer extends BaseProducer {
        private AlgoWatcher m_watcher;

        @Override public void getWatchers(MapConfig config, MapConfig algoConfig, BaseTimesSeriesData ticksTs, Exchange exchange, Pair pair, List<Watcher> watchers) {
            // single Watcher
            m_watcher = new AlgoWatcher(config, algoConfig, exchange, pair, ticksTs);
            watchers.add(m_watcher);
            m_active = false;
        }

        @Override public double logResults() {
            System.out.println("SingleProducer result: " + m_watcher);
            return m_watcher.totalPriceRatio();
        }
    }

    //=============================================================================================
    protected static class AlgoWatcher extends Watcher {

        public AlgoWatcher(MapConfig config, MapConfig algoConfig, Exchange exchange, Pair pair, BaseTimesSeriesData ticksTs) {
            super(config, algoConfig, exchange, pair, ticksTs.getActive()); // get next active TS for paralleler
        }

        @Override protected BaseAlgo createAlgo(ITimesSeriesData parent, MapConfig algoConfig) {
            String algoName = algoConfig.getPropertyNoComment(BaseAlgo.ALGO_NAME_KEY);
            Algo algo = Algo.valueOf(algoName);
            BaseAlgo algoImpl = algo.createAlgo(algoConfig, parent);
            return algoImpl;
        }
    }
}
