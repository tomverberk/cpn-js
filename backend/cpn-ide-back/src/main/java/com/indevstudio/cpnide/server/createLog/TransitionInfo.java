package com.indevstudio.cpnide.server.createLog;

public class TransitionInfo {
    String key;
    String value;


    public TransitionInfo(String s, String transitionString) {
        key = s;
        value = transitionString;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}
