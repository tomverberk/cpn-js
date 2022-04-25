package com.indevstudio.cpnide.server.createLog;

//import openXES;



import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Array;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import com.indevstudio.cpnide.server.model.PlaceMark;
import com.indevstudio.cpnide.server.model.SimInfo;

//import javafx.util.Pair;
import javafx.util.Pair;
import org.apache.commons.lang3.tuple.MutablePair;
import org.cpntools.accesscpn.model.Object;
import org.cpntools.accesscpn.model.impl.ArcImpl;
import org.cpntools.accesscpn.model.impl.PlaceImpl;
import org.cpntools.accesscpn.model.impl.TransitionImpl;
import org.deckfour.xes.factory.XFactory;
import org.deckfour.xes.factory.XFactoryRegistry;
import org.deckfour.xes.model.XAttributeMap;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.deckfour.xes.out.XSerializer;
import org.deckfour.xes.out.XesXmlSerializer;
import org.deckfour.xes.model.*;
import org.cpntools.accesscpn.engine.highlevel.instance.Binding;
import org.cpntools.accesscpn.engine.highlevel.instance.ValueAssignment;
import org.cpntools.accesscpn.model.*;

public class CreateLogContainer {

    private XLog log;
    private XFactory factory;
    private XAttributeMap traceMap;
    private Map<String, XTrace> traces;
    private Boolean isRecording = false;
    private Boolean isRecordingTime = true;
    private String caseId = "x";
    private PetriNet net;
    private List<String> tauTransitions = new LinkedList<>();
    private String pathName;
    private String folderName;
    private long startTimeLong;
    private String timeUnit;
    private Long timeUnitMultiplier;
    private String recordedEvents;
    private Boolean timeHasIncreased = false;
    private Double lastEventTime;
    Queue<Event> bindingQueue = new LinkedList<>();
    Queue<Event> backupBindingQueue = new LinkedList<>();

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

    public void setConfig(CreateLogConfig config) throws Exception {
        setCaseId(config.caseId);
        setStartTime(config.startDateTime);
        setTimeUnits(config.timeUnit);
        setRecordedEvents(config.getRecordedEvents());
    }

    public void setRecordedEvents(String recordedEvents){
        this.recordedEvents = recordedEvents;
    }

    public void setTimeUnits(String timeUnit) throws Exception {
        String timeUnitLowerCase = timeUnit.toLowerCase();
        switch(timeUnitLowerCase){
            case "years":
                this.timeUnit = "year";
                this.timeUnitMultiplier = (long) 1000 * 60 * 60 * 24 * 365;
                break;
            case "months":
                this.timeUnit = "month";
                this.timeUnitMultiplier = (long) 1000 * 60 * 60 * 24 * 30;
                break;
            case "weeks":
                this.timeUnit = "week";
                this.timeUnitMultiplier = (long) 1000 * 60 * 60 * 24 * 7;
                break;
            case "days":
                this.timeUnit = "days";
                this.timeUnitMultiplier = (long) 1000 * 60 * 60 * 24;
                break;
            case "hours":
                this.timeUnit = "week";
                this.timeUnitMultiplier = (long) 1000 * 60 * 60;
                break;
            case "minutes":
                this.timeUnit = "minute";
                this.timeUnitMultiplier = (long) 1000 * 60;
                break;
            case "seconds":
                this.timeUnit = "second";
                this.timeUnitMultiplier = (long) 1000;
                break;
            default:
                System.out.println("no correct timeUnit given");
                throw new IllegalArgumentException(timeUnit + " is not a valid timeunit");
        }
    }

    /**
     *
     */
    public void CreateLog(CreateLogConfig config, Double timeLastUpdatedEvent) throws Exception {

        setConfig(config);
        XAttributeMap logMap = factory.createAttributeMap();
        log = factory.createLog(logMap);
        traceMap = factory.createAttributeMap();
        traces = new HashMap<String, XTrace>();

        setTimeHasIncreased(timeLastUpdatedEvent);

        System.out.println("timeLastUpdatedEvent");
        System.out.println(timeLastUpdatedEvent);
        System.out.println("timeHasIncreased");
        System.out.println(timeHasIncreased);


        Event event;
        while(!bindingQueue.isEmpty()){
            event = bindingQueue.poll();
            if((event.isCompleteEvent() && recordCompleteEvent()) || (event.isStartEvent() && recordStartEvent())){
                createActivityFromFiredBinding(event);
            }
            backupBindingQueue.add(event);
        }

        for(XTrace trace : traces.values())
        {
            log.add(trace);
        }

        //TODO REREPLACE THIS WITH FILENAME
        File directory = new File(folderName);
        directory.mkdirs();

        File file = new File(pathName);

        //File file2 = new File(pathNameWithoutcygDrive);
        System.out.println(pathName);
        // value = \Users\tomve\CPN_IDE\model_out\CPN_IDE_SESSION_1649252354753\mynet.xes

        // value = \Users\tomve\CPN_IDE\model_out\CPN_IDE_SESSION_1649252354753\mynet.xes

        try
        {
            export(file);
        } catch(Exception e) {
            System.out.println(e);
        }

        bindingQueue = backupBindingQueue;
        backupBindingQueue = new LinkedList<>();

    }

    public void setTimeHasIncreased(Double timeLastUpdatedEvent){
        if(timeLastUpdatedEvent == 0.0){
            this.timeHasIncreased = false;
        } else {
            this.timeHasIncreased = true;
        }
    }

    public void setStartTime(String startDateTime) throws ParseException {
        String modifiedStartDateTimeString = startDateTime.replace("T", " ");
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        Date date = formatter.parse(modifiedStartDateTimeString);
        startTimeLong = date.getTime();
    }

    public void printBinding(Binding binding){
        System.out.println("binding= " + binding);
        System.out.println("getAllAssignments =" + binding.getAllAssignments());
        System.out.println("transition name = " + binding.getTransitionInstance().getNode().getName().asString());
        System.out.println("time hopefully = " + binding.getTransitionInstance().getNode().getTime());
        //TODO THIS LINE THROWS AN ERROR WHEN THERE IS NO TIME
        //System.out.println("time hopefully = " + binding.getTransitionInstance().getNode().getTime().asString().replace("@", "").replace(" ", "").replace("+", ""));
        List<Arc> allTargetArcs = binding.getTransitionInstance().getNode().getTargetArc();
        for(Arc arc: allTargetArcs){
            System.out.println("an targetarc = " + arc);
            String arcInscription = arc.getHlinscription().getText();
            System.out.println(arcInscription);
        }
        System.out.println("targetNodes hopefully = " + allTargetArcs);

    }

    public void printTrace(XTrace trace){
        System.out.println("Trace = " + trace);
        System.out.println("Last event = " + trace.get(trace.size()-1));
    }

    public Integer getTimeFromBinding(Binding binding){
        String string = binding.getTransitionInstance().getNode().getTime().asString();
        string = string.replace("@", "").replace(" ", "").replace("+", "");
        Integer addedTime = 0;
        if(string.equals("")){
            return 0;
        } else {
            try {
                addedTime = Integer.parseInt(string);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }

        return addedTime;
    }

    public List<String> getAllTargetArcs(Binding b){
        return null;
    }

    public XEvent createEventFromBinding(Event event, XTrace trace){
        XAttributeMap event1AttributeMap = factory.createAttributeMap();
        Binding binding = event.getBinding();
        Double eventTime = event.getTime();
        printBinding(binding);
        List<String> allTargetArcs = getAllTargetArcs(binding);
        //Integer addedTimeByBinding = getTimeFromBinding(binding);
        if (trace != null) {
            printTrace(trace);
        }

        // Add time to the event
        if(timeHasIncreased) {
            long eventTimeTransformed = (long) (eventTime * timeUnitMultiplier + startTimeLong);
            XAttributeTimestamp xAttributeTimestamp = factory.createAttributeTimestamp("time:timestamp", eventTimeTransformed, null);
            event1AttributeMap.put("time", xAttributeTimestamp);
        }

        // Add lifeCycle transition
        if(event.isStartEvent()){
            XAttributeLiteral xAttributeLiteral = factory.createAttributeLiteral("lifecycle:transition", event.getLifeCycleTransition(), null);
            event1AttributeMap.put("lifecycle:transition", xAttributeLiteral);
        } else if(event.isCompleteEvent()){
            XAttributeLiteral xAttributeLiteral = factory.createAttributeLiteral("lifecycle:transition", event.getLifeCycleTransition(), null);
            event1AttributeMap.put("lifecycle:transition", xAttributeLiteral);
        }

        // Add concept:name and other attributes
        Queue<Pair<String, String>> transitionInfo = getTransitionInfoFromBinding(binding);
        for(Pair<String, String> pair: transitionInfo){
            XAttributeLiteral xAttributeLiteral = factory.createAttributeLiteral(pair.getKey(), pair.getValue(), null);
            event1AttributeMap.put(pair.getKey(), xAttributeLiteral);
        }

        // ADD traceID to the event
        XAttributeLiteral xAttributeLiteralCaseId = factory.createAttributeLiteral("traceId", binding.getValueAssignment(caseId).getValue(), null);
        event1AttributeMap.put("traceId", xAttributeLiteralCaseId);

        // create the event from the eventMap
        XEvent xEvent = factory.createEvent(event1AttributeMap);

        return xEvent;
    }

    public Queue<Pair<String, String>> getTransitionInfoFromTransitionLabel(String transitionString){
        Queue<Pair<String, String>> info = new LinkedList<Pair<String, String>>();
        String transitionSubString = transitionString;
        Integer indexOfLastPlus = 0;
        Integer plusCount = 0;
        if(!transitionString.contains("+")){
            info.add(new Pair("concept:name", transitionString));
        } else {
            while(transitionSubString.contains("+")){
                Integer indexOfPlus = transitionSubString.indexOf("+");
                indexOfLastPlus = indexOfLastPlus + indexOfPlus;
                transitionSubString = transitionSubString.substring(indexOfPlus+1);
                plusCount ++;
            }
            if(isLifeCycleTransition(transitionSubString)) {
                info.add(new Pair("concept_name", transitionString.substring(0, indexOfLastPlus + plusCount - 1)));
                info.add(new Pair("lifecycle:transition", transitionSubString));
            } else {
                info.add(new Pair("concept:name", transitionString));
            }
        }

        return info;
    }

    public Queue<Pair<String, String>> getTransitionInfoFromBinding(Binding b){
        String transitionString = b.getTransitionInstance().getNode().getName().asString();
        Queue<Pair<String, String>> transitionInfo = getTransitionInfoFromTransitionLabel(transitionString);
        List<Arc> targetArcs = b.getTransitionInstance().getNode().getTargetArc();
        for(Arc arc: targetArcs){
            Pair<String, String> arcInfo = getTransitionInfoFromTargetArc(arc);
            if(arcInfo != null){
                transitionInfo.add(arcInfo);
            }
        }

        return transitionInfo;
    }

    public Pair<String, String> getTransitionInfoFromTargetArc(Arc arc){
        if(arc.getHlinscription().getText().equals(caseId)){
            return null;
        }
        // Arc comes from a resource
        Node place = arc.getSource();
        String placeString = place.getName().asString();
        if(placeString.contains("r")){
            String resourceNr = placeString.replace("r", "");
            try{
                Integer.parseInt(resourceNr);
                return new Pair("org:resource", resourceNr);
            } catch (Exception e) {
                return null;
            }

        }

        return null;


    };


    public Boolean isLifeCycleTransition(String transitionSubString){
        switch(transitionSubString){
            case "schedule" :
            case "assign":
            case "reassign":
            case "start":
            case "suspend":
            case "resume":
            case "complete":
            case "withdraw":
            case "autoskip":
            case "manualskip":
            case "abort_activity":
            case "abort_case":
                return true;
            default:
                return false;
        }
    }

    public XEvent createEvent(String value){

        XAttributeLiteral xAttributeLiteral = factory.createAttributeLiteral("concept:Name", value, null);
        XAttributeTimestamp xAttributeTimestamp = factory.createAttributeTimestamp("time:timestamp", new Date(), null);

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
                    trace.add(createEvent("A"));
                    System.out.println("ADD A");
                    break;
                case 1:
                    trace.add(createEvent( "B"));
                    System.out.println("ADD B");
                    break;
                case 2:
                    trace.add(createEvent("C"));
                    System.out.println("ADD C");
                    break;
                case 3:
                    trace.add(createEvent("D"));
                    System.out.println("ADD D");
                    break;
                default:
                    trace.add(createEvent("E"));
                    System.out.println("ADD E");
                    break;
            }
        }

        return trace;
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

    public void createActivityFromFiredBinding(Event event) throws Exception{
        Binding b = event.getBinding();

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
            System.out.println("Ignore this binding");
            return;
        }
        XTrace trace = traces.get(id);

        if(trace == null){
            XEvent xEvent = createEventFromBinding(event, null);
            trace = factory.createTrace(traceMap);
            trace.add(xEvent);
            traces.put(id, trace);
        } else {
            XEvent xEvent = createEventFromBinding(event, traces.get(id));
            trace.add(xEvent);
        }
    }

    public Boolean isRecordingTime(){
        return this.isRecordingTime;
    }

    public void recordActivity(Binding b, SimInfo info, Double endTimeActivity){
        if(!isRecording) {
            return;
        }
        System.out.println(info.getTime());
        Double simulatorTime = makeTimeDouble(info.getTime());

        bindingQueue.add(new Event(b, simulatorTime, "start"));
        if (endTimeActivity != null) {
            bindingQueue.add(new Event(b, endTimeActivity, "complete"));
        } else {
            bindingQueue.add(new Event(b, simulatorTime, "complete"));
        }

    }

    public Double makeTimeDouble(String stringTime){
        if(stringTime == ""){
            return 0.0;
        } else {
            return Double.parseDouble(stringTime);
        }
    }

    public void export(File file) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        XSerializer logSerializer = new XesXmlSerializer();

        logSerializer.serialize(log, out);
        out.close();
    }

    public void setOutputPath(String path, String outputDir){
        outputDir = outputDir.replace("/cygdrive/C", "");
        outputDir = outputDir.replace("model_out", "log_out");
        this.folderName = outputDir;
        this.pathName = outputDir + "/" + path;
        //setOutputPath(this.pathName);
    }

//    public void setOutputPath(String correctPath){
//        this.pathNameWithoutcygDrive = correctPath.replace("/cygdrive/C", "");
//    }

    public String getOutputPath(){
        return this.pathName;
    }

    public XLog getLog(){
        System.out.println(log.toString());
        return this.log;
    }

    public void setRecordActivities(Boolean bool){
        this.isRecording = bool;
        this.isRecordingTime = false;
    }

    public void setRecordTime(Boolean bool) {
        this.isRecordingTime = bool;
    }

    public Boolean hasRecordedEvents(){
        if (bindingQueue.size() == 0){
            return false;
        } else {
            return true;
        }
    }

    public Boolean isLogEmpty(){
        System.out.println(log.isEmpty());
        return(log.isEmpty());
    }

    public Boolean recordStartEvent(){
        return this.recordedEvents.contains("start");
    }

    public Boolean recordCompleteEvent(){
        return this.recordedEvents.contains("complete");
    }

    public void setCaseId(String caseId){
//        System.out.println(caseId);
//        caseId = caseId.substring(11, caseId.length()-2);
//        System.out.println(caseId);
//        if(!caseId.equals(this.caseId)){
//
//        }
        this.caseId = caseId;
    }

}
