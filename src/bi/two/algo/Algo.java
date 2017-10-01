package bi.two.algo;

import bi.two.algo.impl.EmaTrendAlgo;
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
    };

    public BaseAlgo createAlgo(MapConfig algoConfig, ITimesSeriesData parent) {
        throw new RuntimeException("should be overridden");
    }
}
