package com.indevstudio.cpnide.server.createLog;
import org.cpntools.accesscpn.engine.highlevel.instance.Binding;

public class LogEvent {
    private Binding binding;
    private Double time;
    private String lifeCycleTransition;

    public LogEvent(Binding binding, Double time, String lifeCycleTransition){
        this.binding = binding;
        this.time = time;
        this.lifeCycleTransition = lifeCycleTransition;
    }

    public Binding getBinding(){
        return this.binding;
    }

    public Double getTime(){
        return this.time;
    }

    public Boolean isStartEvent(){
        return lifeCycleTransition.equals("start");
    }

    public Boolean lifeCycleIsInTransitionName(){
        return lifeCycleTransition.equals("in transition name");
    }

    public Boolean isCompleteEvent(){
        return lifeCycleTransition.equals("complete");
    }

    public String getLifeCycleTransition(){
        return this.lifeCycleTransition;
    }

}
