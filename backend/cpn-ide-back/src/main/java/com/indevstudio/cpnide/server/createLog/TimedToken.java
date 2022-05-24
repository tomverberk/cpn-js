package com.indevstudio.cpnide.server.createLog;

import java.util.List;

public class TimedToken {
    List<String> attributes;
    String time;

    TimedToken(List<String> attributes, String time){
        this.attributes = attributes;
        this.time = time;
    }
}
