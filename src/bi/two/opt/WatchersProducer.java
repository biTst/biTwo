package bi.two.opt;

import bi.two.Main;
import bi.two.algo.BaseAlgo;
import bi.two.algo.Watcher;
import bi.two.algo.impl.RegressionAlgo;
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
import java.util.ListIterator;

public class WatchersProducer {
    private List<IProducer> m_producers = new ArrayList<>();

    public WatchersProducer(MapConfig config, MapConfig algoConfig) {
        String optimizeCfgStr = config.getPropertyNoComment("opt");
        if (optimizeCfgStr != null) {
            List<List<OptimizeConfig>> optimizeConfigs = parseOptimizeConfigs(optimizeCfgStr, config);
            for (List<OptimizeConfig> optimizeConfig : optimizeConfigs) {
                IProducer optimizeProducer = new SingleDimensionalOptimizeProducer(optimizeConfig, algoConfig);
                m_producers.add(optimizeProducer);
            }
        } else {
            String iterateCfgStr = config.getPropertyNoComment("iterate");
            if (iterateCfgStr != null) {
                List<List<IterateConfig>> iterateConfigs = parseIterateConfigs(iterateCfgStr, config);
                IProducer iterateProducer = new IterateProducer(iterateConfigs);
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
            List<OptimizeConfig> oc = parseOptimizeConfig(s);
            if (!oc.isEmpty()) {
                optimizeConfigs.add(oc);
            }
        }
        return optimizeConfigs;
    }

    private static List<OptimizeConfig> parseOptimizeConfig(String optimizeCfgStr) { // smooth=2.158+-5*0.01&threshold=0.158+-5*0.001
        List<OptimizeConfig> ret = new ArrayList<>();
        String[] split = optimizeCfgStr.split("\\&"); // [ "divider=26.985[3,40]", "reverse=0.0597[0.01,1.0]" ]
        for (String s : split) { // "reverse=0.0597[0.01,1.0]"
            String[] split2 = s.split("\\=");
            if (split2.length == 2) {
                String name = split2[0];
                String cfg = split2[1];
                try {
                    Vary vary = Vary.valueOf(name);
                    OptimizeConfig optimizeConfig = OptimizeConfig.parseOptimize(cfg, vary);
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
        return !m_producers.isEmpty();
    }

    public List<Watcher> getWatchers(MapConfig algoConfig, TimesSeriesData<TickData> ticksTs0, MapConfig config, Exchange exchange, Pair pair) {
        int parallel = config.getInt("parallel");
        BaseTimesSeriesData ticksTs = new ParallelTimesSeriesData(ticksTs0, parallel);

        List<Watcher> watchers = new ArrayList<>();
        for(ListIterator<IProducer> listIterator = m_producers.listIterator(); listIterator.hasNext(); ) {
            IProducer producer = listIterator.next();
            boolean active = producer.getWatchers(algoConfig, ticksTs, exchange, pair, watchers);
            if(!active) {
                listIterator.remove(); // remove once done
            }
        }

        return watchers;
    }

    //=============================================================================================
    public interface IProducer {
        boolean getWatchers(MapConfig algoConfig, BaseTimesSeriesData ticksTs, Exchange exchange, Pair pair, List<Watcher> watchers);
    }

    //=============================================================================================
    private static class IterateProducer implements IProducer {
        private final List<List<IterateConfig>> m_iterateConfigs;

        public IterateProducer(List<List<IterateConfig>> iterateConfigs) {
            m_iterateConfigs = iterateConfigs;
        }

        @Override public boolean getWatchers(MapConfig algoConfig, BaseTimesSeriesData ticksTs, Exchange exchange, Pair pair, List<Watcher> watchers) {
            for (List<IterateConfig> iterateConfig : m_iterateConfigs) {
                MapConfig localAlgoConfig = algoConfig.copy();
                doIterate(iterateConfig, 0, localAlgoConfig, ticksTs, exchange, pair, watchers);
            }
            return false; // one pass
        }

        private static void doIterate(final List<IterateConfig> iterateConfigs, int index, final MapConfig algoConfig, final BaseTimesSeriesData ticksTs,
                                      final Exchange exchange, final Pair pair, final List<Watcher> watchers) {
            final int nextIndex = index + 1;
            final IterateConfig iterateConfig = iterateConfigs.get(index);
            final Vary vary = iterateConfig.m_vary;
            vary.m_varyType.iterate(iterateConfig, new Main.IParamIterator<Number>() {
                @Override public void doIteration(Number value) {
                    algoConfig.put(vary.m_key, value);
                    if (nextIndex < iterateConfigs.size()) {
                        doIterate(iterateConfigs, nextIndex, algoConfig, ticksTs, exchange, pair, watchers);
                    } else {
                        Watcher watcher = new RegressionAlgoWatcher(algoConfig, exchange, pair, ticksTs);
                        watchers.add(watcher);
                    }
                }
            });
        }
    }

    //=============================================================================================
    private static class SingleProducer implements IProducer {
        @Override public boolean getWatchers(MapConfig algoConfig, BaseTimesSeriesData ticksTs, Exchange exchange, Pair pair, List<Watcher> watchers) {
            // single Watcher
            Watcher watcher = new RegressionAlgoWatcher(algoConfig, exchange, pair, ticksTs);
            watchers.add(watcher);
            return false; // one pass
        }
    }

    //=============================================================================================
    protected static class RegressionAlgoWatcher extends Watcher {
        public RegressionAlgoWatcher(MapConfig algoConfig, Exchange exchange, Pair pair, BaseTimesSeriesData ticksTs) {
            super(algoConfig, exchange, pair,
                    ticksTs.getActive()); // get next active TS for paralleler
        }

        @Override protected BaseAlgo createAlgo(ITimesSeriesData parent, MapConfig algoConfig) {
            return new RegressionAlgo(algoConfig, parent);
        }
    }
}
