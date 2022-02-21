package com.indevstudio.cpnide.server.createLog;

//import openXES;



import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.deckfour.xes.factory.XFactory;
import org.deckfour.xes.factory.XFactoryRegistry;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.out.XSerializer;
import org.deckfour.xes.out.XesXmlSerializer;
import org.deckfour.xes.model.*;

public class CreateLog {
    /**
     *
     */
    public void AddToLog(){
        XFactory factory = XFactoryRegistry.instance().currentDefault();
        XAttributeBoolean xAttributeBoolean = factory.createAttributeBoolean("boolean", true, null);

        SXDocument
        XAttributeMap log1AttributeMap = factory.createAttributeMap();
        XLog log1 = factory.createLog(log1AttributeMap);
        XAttributeMap trace1AttributeMap = factory.createAttributeMap();
        XTrace trace1 = factory.createTrace(trace1AttributeMap);
        XAttributeMap event1AttributeMap = factory.createAttributeMap();
        event1AttributeMap.put("boolean", xAttributeBoolean);
        XEvent event1 = factory.createEvent(event1AttributeMap);
        trace1.add(event1);
        log1.add(trace1);
        File file = new File("test.xes");
        try
        {
            export(log1, file);
        } catch(Exception e) {
            System.out.println("Something went wrong in writing a file");
        }

    }

    public static void export(XLog log, File file) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        XSerializer logSerializer = new XesXmlSerializer();
        logSerializer.serialize(log, out);
        out.close();
    }

}
