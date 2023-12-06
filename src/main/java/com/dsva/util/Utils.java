package com.dsva.util;

import com.dsva.model.Constants;

public class Utils {

    private Utils() {
        throw new UnsupportedOperationException("Can not init static class");
    }

    public static int getNodePortFromNodeId(int nodeId) {
        return nodeId + Constants.DEFAULT_PORT;
    }
}
