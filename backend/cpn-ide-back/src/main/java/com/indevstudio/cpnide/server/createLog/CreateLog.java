package com.indevstudio.cpnide.server.createLog;

//import openXES;



import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

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

public class CreateLog {

    private XFactory factory = XFactoryRegistry.instance().currentDefault();



    private XAttributeMap traceMap = factory.createAttributeMap();
    private Map<String, XTrace> traces = new HashMap<>();

    /**
     *
     */
    public void CreateLog(){
        XAttributeMap logMap = factory.createAttributeMap();
        XLog log = factory.createLog(logMap);
        for(XTrace trace : traces.values())
        {
            System.out.println("hi");
            log.add(trace);
        }
        //EMPTY TRACES TODO
        traces = new HashMap<>();

        File file = new File("test.xes");
        try
        {
            export(log, file);
        } catch(Exception e) {
            System.out.println("Something went wrong in writing a file");
        }

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

    public void createActivityFromFiredBinding(Binding b){
        String id = null;
        for(ValueAssignment assignment: b.getAllAssignments())
        {
            if(assignment.getName().equals("x")){
                id = assignment.getValue();
            }
        }
        System.out.println(id);
        XTrace trace = traces.get(id);
        System.out.println(trace);
        XEvent event = createEvent("x", id);
        if(trace == null){
            trace = factory.createTrace(traceMap);
            traces.put(id, trace);
        }
        trace.add(event);



    }

    public static void export(XLog log, File file) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        XSerializer logSerializer = new XesXmlSerializer();
        logSerializer.serialize(log, out);
        out.close();
    }

}
