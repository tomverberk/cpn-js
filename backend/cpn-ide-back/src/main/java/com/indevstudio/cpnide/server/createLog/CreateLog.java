package com.indevstudio.cpnide.server.createLog;

//import openXES;



import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

import org.deckfour.xes.factory.XFactory;
import org.deckfour.xes.factory.XFactoryRegistry;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.out.XSerializer;
import org.deckfour.xes.out.XesXmlSerializer;
import org.deckfour.xes.model.*;
import org.deckfour.spex.*;

public class CreateLog {

    private XFactory factory = XFactoryRegistry.instance().currentDefault();

    private XAttributeMap logMap = factory.createAttributeMap();
    private XLog log = factory.createLog(logMap);

    /**
     *
     */
    public void AddToLog(){
        XAttributeMap log1AttributeMap = factory.createAttributeMap();
        XLog log1 = factory.createLog(log1AttributeMap);

        XTrace trace1 = createTrace();
        log1.add(trace1);
        File file = new File("test.xes");
        try
        {
            export(log1, file);
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

    public static void export(XLog log, File file) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        XSerializer logSerializer = new XesXmlSerializer();
        logSerializer.serialize(log, out);
        out.close();
    }

}
