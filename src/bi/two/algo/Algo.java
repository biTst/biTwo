package bi.two.algo;

import bi.two.algo.impl.EmaTrendAlgo;
import bi.two.algo.impl.Mmar3Algo;
import bi.two.algo.impl.MmarAlgo;
import bi.two.algo.impl.RegressionAlgo;
import bi.two.ts.ITimesSeriesData;
import bi.two.util.MapConfig;

public enum Algo {
    regression {
        @Override public BaseAlgo createAlgo(MapConfig algoConfig, ITimesSeriesData parent) {
            return new RegressionAlgo(algoConfig, parent);
        }
    },
    emaTrend {
        @Override public BaseAlgo createAlgo(MapConfig algoConfig, ITimesSeriesData parent) {
            return new EmaTrendAlgo(algoConfig, parent);
        }
    },
    mmar {
        @Override public BaseAlgo createAlgo(MapConfig algoConfig, ITimesSeriesData parent) {
            return new MmarAlgo(algoConfig, parent);
        }
        @Override public void resetIterationCache() {
            MmarAlgo.resetIterationCache();
        }
    },
    mmar3 {
        @Override public BaseAlgo createAlgo(MapConfig algoConfig, ITimesSeriesData parent) {
            return new Mmar3Algo(algoConfig, parent);
        }
    },
    ;

    public BaseAlgo createAlgo(MapConfig algoConfig, ITimesSeriesData parent) {
        throw new RuntimeException("should be overridden");
    }

    public void resetIterationCache() {}

    public static void resetIterationCaches() {
        for (Algo algo : values()) {
            algo.resetIterationCache();
        }
    }
}
