package bi.two.algo;

import bi.two.algo.impl.*;
import bi.two.exch.Exchange;
import bi.two.ts.ITimesSeriesData;
import bi.two.util.MapConfig;

public enum Algo {
    regression { @Override public BaseAlgo createAlgo(MapConfig algoConfig, ITimesSeriesData parent, Exchange exchange) { return new RegressionAlgo(algoConfig, parent); } },
    emaTrend { @Override public BaseAlgo createAlgo(MapConfig algoConfig, ITimesSeriesData parent, Exchange exchange) { return new EmaTrendAlgo(algoConfig, parent); } },
    mmar {
        @Override public BaseAlgo createAlgo(MapConfig algoConfig, ITimesSeriesData parent, Exchange exchange) { return new MmarAlgo(algoConfig, parent); }
        @Override public void resetIterationCache() {
            MmarAlgo.resetIterationCache();
        } },
    mmar3 { @Override public BaseAlgo createAlgo(MapConfig algoConfig, ITimesSeriesData parent, Exchange exchange) { return new Mmar3Algo(algoConfig, parent); } },
    ummar { @Override public BaseAlgo createAlgo(MapConfig algoConfig, ITimesSeriesData parent, Exchange exchange) { return new UmmarAlgo(algoConfig, parent); } },
    ummar2 { @Override public BaseAlgo createAlgo(MapConfig algoConfig, ITimesSeriesData parent, Exchange exchange) { return new Ummar2Algo(algoConfig, parent, exchange); } },
    ummar3 { @Override public BaseAlgo createAlgo(MapConfig algoConfig, ITimesSeriesData parent, Exchange exchange) { return new Ummar3Algo(algoConfig, parent, exchange); } },
    qummar { @Override public BaseAlgo createAlgo(MapConfig algoConfig, ITimesSeriesData parent, Exchange exchange) { return new QummarAlgo(algoConfig, parent, exchange); } },
    qummar2 { @Override public BaseAlgo createAlgo(MapConfig algoConfig, ITimesSeriesData parent, Exchange exchange) { return new QummarAlgo2(algoConfig, parent, exchange); } },
    fast { @Override public BaseAlgo createAlgo(MapConfig algoConfig, ITimesSeriesData parent, Exchange exchange) { return new FastAlgo(algoConfig, parent, exchange); } },
    test { @Override public BaseAlgo createAlgo(MapConfig algoConfig, ITimesSeriesData parent, Exchange exchange) { return new TestAlgo(algoConfig, parent, exchange); } },
    midvel { @Override public BaseAlgo createAlgo(MapConfig algoConfig, ITimesSeriesData parent, Exchange exchange) { return new MidvelAlgo(algoConfig, parent, exchange); } },
    midvel2 { @Override public BaseAlgo createAlgo(MapConfig algoConfig, ITimesSeriesData parent, Exchange exchange) { return new Midvel2Algo(algoConfig, parent, exchange); } },
    midvel3 { @Override public BaseAlgo createAlgo(MapConfig algoConfig, ITimesSeriesData parent, Exchange exchange) { return new Midvel3Algo(algoConfig, parent, exchange); } },
    ray { @Override public BaseAlgo createAlgo(MapConfig algoConfig, ITimesSeriesData parent, Exchange exchange) { return new RayAlgo(algoConfig, parent, exchange); } },
    ;

    public BaseAlgo createAlgo(MapConfig algoConfig, ITimesSeriesData parent, Exchange exchange) {
        throw new RuntimeException("should be overridden");
    }

    public void resetIterationCache() {}

    public static void resetIterationCaches() {
        for (Algo algo : values()) {
            algo.resetIterationCache();
        }
    }
}
