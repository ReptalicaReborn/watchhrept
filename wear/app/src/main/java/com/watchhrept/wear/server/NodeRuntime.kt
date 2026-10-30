package com.watchhrept.wear.server

/** Entry point into the embedded Node.js (nodejs-mobile) via src/main/cpp/native-lib.cpp. */
object NodeRuntime {
    init {
        System.loadLibrary("node")
        System.loadLibrary("nodebridge")
    }

    /** Runs Node with [args] (argv, starting with "node") and blocks until it exits. Only once per process. */
    external fun startNode(args: Array<String>): Int
}
