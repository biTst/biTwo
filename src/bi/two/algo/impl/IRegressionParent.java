package bi.two.algo.impl;

import org.jetbrains.annotations.Nullable;

// --------------------------------------------------------
interface IRegressionParent {
    @Nullable Float update(long timestamp, float lastPrice);
}
