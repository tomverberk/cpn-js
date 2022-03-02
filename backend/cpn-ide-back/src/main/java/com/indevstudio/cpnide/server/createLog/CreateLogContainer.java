package com.indevstudio.cpnide.server.createLog;

//import openXES;



import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

import org.cpntools.accesscpn.model.Object;
import org.cpntools.accesscpn.model.impl.ArcImpl;
import org.cpntools.accesscpn.model.impl.PlaceImpl;
import org.cpntools.accesscpn.model.impl.TransitionImpl;
import org.deckfour.xes.factory.XFactory;
import org.deckfour.xes.factory.XFactoryRegistry;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.out.XSerializer;
import org.deckfour.xes.out.XesXmlSerializer;
import org.deckfour.xes.model.*;
import org.cpntools.accesscpn.engine.SimulatorService;
import org.cpntools.accesscpn.engine.highlevel.*;
import org.cpntools.accesscpn.engine.highlevel.checker.Checker;
import org.cpntools.accesscpn.engine.highlevel.instance.Binding;
import org.cpntools.accesscpn.engine.highlevel.instance.Instance;
import org.cpntools.accesscpn.engine.highlevel.instance.ValueAssignment;
import org.cpntools.accesscpn.model.*;
import org.cpntools.accesscpn.model.exporter.DOMGenerator;
import org.cpntools.accesscpn.model.importer.DOMParser;
import org.cpntools.accesscpn.model.monitors.Monitor;
import org.springframework.stereotype.Component;
import org.deckfour.spex.*;

public class CreateLogContainer {

    private XFactory factory;
    private XAttributeMap traceMap;
    private Map<String, XTrace> traces;
    private Boolean isRecording = false;
    private String caseId = "x";
    private PetriNet net;
    private List<String> tauTransitions = new LinkedList<>();
    Queue<Binding> bindingQueue = new LinkedList<>();
    Queue<Binding> backupBindingQueue = new LinkedList<>();

    private static CreateLogContainer single_instance = null;

    private CreateLogContainer(){
        factory = XFactoryRegistry.instance().currentDefault();
        bindingQueue = new LinkedList<>();
    }

    public static CreateLogContainer getInstance() {
        if(single_instance == null){
            single_instance = new CreateLogContainer();
        }

        return single_instance;
    }

    public CreateLogContainer getNewContainer(PetriNet net){
        this.net = net;
        setTauTransitions(net);
        return this;
    }

    public void setTauTransitions(PetriNet net){
        tauTransitions = new LinkedList<>();
        List<org.cpntools.accesscpn.model.Object> objects = net.getPage().get(0).getObject();

        for(Object object: objects){
            System.out.println(object.getClass());
            System.out.println(object.getClass());
            if(object instanceof PlaceImpl){
                System.out.println("this is a place");
            }
            else if (object instanceof TransitionImpl){
                System.out.println(((TransitionImpl) object).getNodeGraphics().getFill().getColor());
                System.out.println("this is a transition");

                if(((TransitionImpl) object).getNodeGraphics().getFill().getColor().equals("Black")){
                    System.out.println("This is a tau transition");
                    this.tauTransitions.add(object.getName().asString());

                }

            } else if (object instanceof ArcImpl){
                System.out.println("this is an arc");
            } else {
                System.out.println("this is something else");
            }
            System.out.println(object.getName().asString());
            object.getClass();
            System.out.println("end object");
        }


    }

    public void destroy(){
        single_instance = null;
    }

    //TODO fix this method
    public void clearLog(){
        System.out.println("Clear the Log");
        bindingQueue = new LinkedList<>();
        single_instance.destroy();
        single_instance = getInstance();
    }

    /**
     *
     */
    public void CreateLog(String caseId) throws Exception {
        setCaseId(caseId);
        XAttributeMap logMap = factory.createAttributeMap();
        XLog log = factory.createLog(logMap);
        traceMap = factory.createAttributeMap();
        traces = new HashMap<>();
        Binding b;
        while(!bindingQueue.isEmpty()){
            b = bindingQueue.poll();
            createActivityFromFiredBinding(b);
            backupBindingQueue.add(b);
        }

        for(XTrace trace : traces.values())
        {
            log.add(trace);
        }

        File file = new File("test.xes");
        try
        {
            export(log, file);
        } catch(Exception e) {
            System.out.println("Something went wrong in writing a file");
        }

        bindingQueue = backupBindingQueue;
        backupBindingQueue = new LinkedList<>();

    }

    public XEvent createEvent(String key, String value){

        XAttributeLiteral xAttributeLiteral = factory.createAttributeLiteral(key, value, null);
        XAttributeTimestamp xAttributeTimestamp = factory.createAttributeTimestamp("time", new Date(), null);

        XAttributeMap event1AttributeMap = factory.createAttributeMap();

        event1AttributeMap.put("ActivityName", xAttributeLiteral);
        event1AttributeMap.put("time", xAttributeTimestamp);
        XEvent event = factory.createEvent(event1AttributeMap);

        return event;
    }

    public XTrace createTrace(){
        XAttributeMap trace1AttributeMap = factory.createAttributeMap();
        XTrace trace = factory.createTrace(trace1AttributeMap);

        for(int i=0; i<5; i++){
            System.out.println(i);
            double random = Math.random()*5;
            Math.floor(random);
            int gerry = (int) random;
            System.out.println(gerry);
            switch(gerry){
                case 0:
                    trace.add(createEvent("ActivityName", "A"));
                    System.out.println("ADD A");
                    break;
                case 1:
                    trace.add(createEvent("ActivityName", "B"));
                    System.out.println("ADD B");
                    break;
                case 2:
                    trace.add(createEvent("ActivityName", "C"));
                    System.out.println("ADD C");
                    break;
                case 3:
                    trace.add(createEvent("ActivityName", "D"));
                    System.out.println("ADD D");
                    break;
                default:
                    trace.add(createEvent("ActivityName", "E"));
                    System.out.println("ADD E");
                    break;
            }
        }

        return trace;
    }

    public void addEventToLog(String value, String Id){
        XEvent event = createEvent("EventName", value);
        //trace.add(event);
    }

    public Boolean isTauTransition(Binding b){
        String transitionName = b.getTransitionInstance().getNode().getName().asString();
        for(String tauTransition : tauTransitions){
            if(tauTransition.equals(transitionName)){
                return true;
            }
        }
        return false;
    }

    public void createActivityFromFiredBinding(Binding b) throws Exception{
        String id = null;
        if(isTauTransition(b)){
            return;
        }

        System.out.println(b.getTransitionInstance().getNode().getName().asString());
        for(ValueAssignment assignment: b.getAllAssignments())
        {
            if(assignment.getName().equals(caseId)){
                id = assignment.getValue();
            }
        }
        if(id == null){
            System.out.println("Exception time");
            throw new Exception("All bindings should have an variable 'x' as part of the binding");
        }
        XTrace trace = traces.get(id);
        System.out.println(trace);
        XEvent event = createEvent("concept:name", b.getTransitionInstance().getNode().getName().asString());
        if(trace == null){
            trace = factory.createTrace(traceMap);
            traces.put(id, trace);
        }
        trace.add(event);
    }

    public void recordActivity(Binding b){
        if(!isRecording) {
            return;
        }
        bindingQueue.add(b);
    }

    public static void export(XLog log, File file) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        XSerializer logSerializer = new XesXmlSerializer();
        logSerializer.serialize(log, out);
        out.close();
    }

    public void setRecording(Boolean bool){
        this.isRecording = bool;
    }

    public void setCaseId(String caseId){
        System.out.println(caseId);
        caseId = caseId.substring(11, caseId.length()-2);
        System.out.println(caseId);
        if(!caseId.equals(this.caseId)){

        }
        this.caseId = caseId;
    }

}
