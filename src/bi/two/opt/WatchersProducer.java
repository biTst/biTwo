package bi.two.opt;

import bi.two.Main;
import bi.two.algo.Algo;
import bi.two.algo.BaseAlgo;
import bi.two.algo.Watcher;
import bi.two.exch.Exchange;
import bi.two.exch.Pair;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.ParallelTimesSeriesData;
import bi.two.util.MapConfig;

import java.util.*;

import static bi.two.util.Log.console;

public class WatchersProducer {
    public static final String OPT_KEY = "opt";
    public static final String ITERATE_KEY = "iterate";

    private List<BaseProducer> m_producers = new ArrayList<>();

    public WatchersProducer(MapConfig config, MapConfig algoConfig) {
        String optimizeCfgStr = config.getPropertyNoComment(OPT_KEY);
        if (optimizeCfgStr != null) {
            // opt=multiplier2|count2&step2|multiplier=*[1,15]
            List<List<OptimizeConfig>> optimizeConfigs = parseOptimizeConfigs(optimizeCfgStr, config);
            for (List<OptimizeConfig> optimizeConfig : optimizeConfigs) {
                BaseProducer optimizeProducer = (optimizeConfig.size() > 1)
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
        // multiplier2|count2&step2|multiplier=*[1,15]
        String namedOptimizeCfgStr = config.getPropertyNoComment(optimizeCfgStr);
        if (namedOptimizeCfgStr != null) { // do substitution
            optimizeCfgStr = namedOptimizeCfgStr;
        }
        String[] split = optimizeCfgStr.split("\\|");
        List<String> list = new ArrayList<>();
        for (String s : split) {
            // check for special cases
            if (s.startsWith("!")) {
                // !2!count2&step2&multiplier2
                List<String> permutations = parsePermutations(s);
                console("permutations[" + permutations.size() + "]: " + permutations);
                list.addAll(permutations);
            } else {
                list.add(s);
            }
        }

        for (String s : list) {
            // multiplier2
            // count2&step2
            // multiplier=*[1,15]
            namedOptimizeCfgStr = config.getPropertyNoComment(s);
            if (namedOptimizeCfgStr != null) {
                s = namedOptimizeCfgStr; // substitute
            }
            List<OptimizeConfig> oc = parseOptimizeConfig(s, config);
            if (!oc.isEmpty()) {
                optimizeConfigs.add(oc);
            }
        }
        return optimizeConfigs;
    }

    public static void main(String[] args) {
//        List<String> permutations = parsePermutations("!3!1&2&3&4&5");
//        List<String> permutations = parsePermutations("!3!1|2|3|4|5");
        List<String> permutations = parsePermutations("!2!multiplier2|minOrderMul2|start2|step2|count2|target2|reverse2|reverseMul2|period2");
        System.out.println("permutations: " + permutations);
    }

    private static List<String> parsePermutations(String in) {
        // !2!1&2&3&4
        String[] split = in.split("\\!");
        if (split.length == 3) {
            String countStr = split[1];
            int count = Integer.parseInt(countStr);
            String src = split[2];
            String[] objects = src.split("\\&");
            if (objects.length == 1) {
                throw new RuntimeException("invalid array definition in Permutation config '" + in + "'");
            }
            List<String> list = Arrays.asList(objects);
            List<List<String>> permutated = permutate(count, list);
            List<String> ret = new ArrayList<>();
            for (List<String> stringList : permutated) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < stringList.size(); i++) {
                    String str = stringList.get(i);
                    if(i > 0) {
                        sb.append('&');
                    }
                    sb.append(str);
                }
                String joined = sb.toString();
                ret.add(joined);
            }
            return ret;
        } else {
            throw new RuntimeException("invalid Permutation config '" + in + "'");
        }
    }

    private static List<List<String>> permutate(int count, List<String> objects) {
//        System.out.println("permutate: count=" + count + "; objects=" + objects);
        List<List<String>> res = new ArrayList<>();
        if (count == 1) {
//            System.out.println(" single");
            for (String str : objects) {
                List<String> permutation = new ArrayList<>();
                permutation.add(str);
                res.add(permutation);
            }
        } else {
            int len = objects.size();
            int starts = len - count;
//            System.out.println(" multiple len=" + len + "; starts=" + starts);
            for (int i = 0; i <= starts; i++) {
                List<List<String>> permutated = permutate(count - 1, objects.subList(i + 1, len));
                String first = objects.get(i);
//                System.out.println("  [" + i + "] first=" + first + "; permutated=" + permutated);
                List<List<String>> tmp = new ArrayList<>();
                for (List<String> permutation : permutated) {
                    List<String> var = new ArrayList<>();
                    var.add(first);
                    var.addAll(permutation);
                    tmp.add(var);
                }
//                System.out.println("   tmp=" + tmp);
                res.addAll(tmp);
            }
        }
//        System.out.println("  res=" + res);
        return res;
    }

    private static List<OptimizeConfig> parseOptimizeConfig(String optimizeCfgStr, MapConfig config) { // smooth=2.158+-5*0.01&threshold=0.158+-5*0.001
        List<OptimizeConfig> ret = new ArrayList<>();
        // multiplier2
        // count2&step2
        // multiplier=*[1,15]
        String[] split = optimizeCfgStr.split("\\&"); // [ "divider=26.985[3,40]", "reverse=0.0597[0.01,1.0]" ]
        for (String s : split) { // "reverse=0.0597[0.01,1.0]"
            String namedOptimizeCfgStr = config.getPropertyNoComment(s);
            if (namedOptimizeCfgStr != null) { // do substitution
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
                console("OptimizeConfig '" + s + "' is invalid");
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
                console("IterateConfig '" + s + "' is invalid");
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

    public boolean isSingle() {
        return (m_producers.size() == 1) && m_producers.get(0).isSingle();
    }

    public List<Watcher> getWatchers(MapConfig algoConfig, BaseTimesSeriesData tsd, MapConfig config, Exchange exchange, Pair pair) {
        BaseTimesSeriesData ticksTs;

        int parallel = config.getInt("parallel");
        if (parallel == 0) { // explicitly requested NO-PARALLEL
            ticksTs = tsd;
        } else { // read and process in different threads even for single producer
            int groupTicks = config.getIntOrDefault("groupTicks", 2);
            int maxQueue = config.getIntOrDefault("maxQueue", 20000);
            console("parallel=" + parallel + "; groupTicks=" + groupTicks + "; maxQueue=" + maxQueue);
            ticksTs = new ParallelTimesSeriesData(tsd, parallel, groupTicks, maxQueue, exchange);
        }

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
        List<BaseProducer> producers = new ArrayList<>(m_producers);
        Collections.sort(producers, new Comparator<BaseProducer>() {
            @Override public int compare(BaseProducer p1, BaseProducer p2) {
                return Double.compare(p2.maxTotalPriceRatio(), p1.maxTotalPriceRatio());
            }
        });
        int maxNameLen = 0;
        for (BaseProducer producer : producers) {
            int len = producer.logKeyWidth();
            maxNameLen = Math.max(maxNameLen, len);
        }
        for (BaseProducer producer : producers) {
            int len = producer.logKeyWidth();
            int pad = maxNameLen - len;
            double totalPriceRatio = producer.logResults(pad);
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

        @Override public boolean isSingle() { return false; }

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
                    algoConfig.put(vary.name(), value);
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

        @Override public double logResults(int pad) {
            AlgoWatcher bestWatcher = findBestWatcher();
            console("IterateProducer result: " + bestWatcher);
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

        @Override public boolean isSingle() { return true; }

        @Override public void getWatchers(MapConfig config, MapConfig algoConfig, BaseTimesSeriesData ticksTs, Exchange exchange, Pair pair, List<Watcher> watchers) {
            // single Watcher
            m_watcher = new AlgoWatcher(config, algoConfig, exchange, pair, ticksTs);
            watchers.add(m_watcher);
            m_active = false;
        }

        @Override public double logResults(int pad) {
            console("SingleProducer result: " + m_watcher);
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
            if (algoName == null) {
                throw new RuntimeException("no '" + BaseAlgo.ALGO_NAME_KEY + "' param");
            }
            Algo algo = Algo.valueOf(algoName);
            BaseAlgo algoImpl = algo.createAlgo(algoConfig, parent, m_exch);
            return algoImpl;
        }
    }
}
