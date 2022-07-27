package com.indevstudio.cpnide.server.createLog;

//import openXES;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Array;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

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
    private XTrace currentTrace;
    private Map<String, String> varDeclarations;
    private Boolean informationLevelIsEvent;
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
        saveDeclarations(net);
        return this;
    }

    public void saveDeclarations(PetriNet net){
        varDeclarations = new HashMap<String, String>();
        for(Label label: net.getLabel()){
            if(isVarDeclaration(label)){
                addDeclarationToVarDeclarations(label);
            }
        }
        // if it is a var, add to varDeclaration dataset as a pair <var, colorset>
    }

    public boolean isVarDeclaration(Label label){
        String declaration  = label.asString();
        return declaration.startsWith("var");
    }

    public void addDeclarationToVarDeclarations(Label label){
        String declaration  = label.asString();
        declaration = removeSpaces(declaration);
        Integer indexOfSeperator = declaration.indexOf(":");
        String var = declaration.substring(3, indexOfSeperator);
        String colorSet = declaration.substring(indexOfSeperator + 1, declaration.length()-1);
        varDeclarations.put(var, colorSet);
    }

    public String removeSpaces(String string){
        return string.replace(" ", "");
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
        setInformationLevel(config.informationLevelIsEvent);
        setRecordedEvents(config.getRecordedEvents());
    }

    public void setInformationLevel(Boolean informationLevelIsEvent){
        this.informationLevelIsEvent = informationLevelIsEvent;
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
            if(!informationLevelIsEvent) {

                Queue<XAttribute> traceAttributes = findTraceAttributes(trace);
                // FILL WITH ALL ATTRIBUTES OF FIRST EVENT


                // REMOVE THE ATTRIBUTES FROM THE EVENTS
                for (int i = 0; i < trace.size(); i++) {
                    XEvent Xevent = trace.get(i);
                    XAttributeMap xAttributeMap = Xevent.getAttributes();
                    for (XAttribute attributeSeenInAllEvents : traceAttributes) {
                        xAttributeMap.remove(attributeSeenInAllEvents.getKey());
                    }
                }


                XAttributeMap traceMap = factory.createAttributeMap();
                for (XAttribute xAttribute : traceAttributes) {
                    String attributeValue = xAttribute.toString();
                    if (xAttribute.getKey().equals("traceId")) {
                        XAttributeLiteral literal = factory.createAttributeLiteral("concept:name", attributeValue, null);
                        traceMap.put("concept:name", literal);
                    } else {
                        XAttributeLiteral literal = factory.createAttributeLiteral(xAttribute.getKey(), attributeValue, null);
                        traceMap.put(xAttribute.getKey(), literal);
                    }
                }
                trace.setAttributes(traceMap);
            }
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

    public Queue<XAttribute> findTraceAttributes(XTrace trace){
        Queue<XAttribute> attributesSeenInFirstEvent = findPossibleTraceAttributesOfFirstEvent(trace);

        Queue<XAttribute> attributesNotSeenInAllEvents = findAttributesNotInAllEvents(trace, attributesSeenInFirstEvent);

        Queue<XAttribute> attributesSeenInAllEvents = attributesSeenInFirstEvent;
        attributesSeenInAllEvents.removeAll(attributesNotSeenInAllEvents);

        return attributesSeenInAllEvents;
    }

    public Queue<XAttribute> findPossibleTraceAttributesOfFirstEvent(XTrace trace){
        Queue<XAttribute> attributesSeenInFirstEvent = new LinkedList<>();
        XEvent firstXEvent = trace.get(0);
        XAttributeMap xAttributeMapFirstEvent = firstXEvent.getAttributes();
        Set<String> keySet = xAttributeMapFirstEvent.keySet();
        for (String key : keySet) {
            if(key != "concept:name" && key != "lifecycle:transition" && key != "time:timestamp") {
                attributesSeenInFirstEvent.add(xAttributeMapFirstEvent.get(key));
            }
        }
        return attributesSeenInFirstEvent;
    }

    public Queue<XAttribute> findAttributesNotInAllEvents(XTrace trace, Queue<XAttribute> attributesSeenInFirstEvent){
        Queue<XAttribute> attributesNotSeenInAllEvents = new LinkedList<>();
        for (int i = 0; i < trace.size(); i++) {
            XEvent Xevent = trace.get(i);
            XAttributeMap xAttributeMap = Xevent.getAttributes();
            Set<String> keySet = xAttributeMap.keySet();
            for (XAttribute possibleTraceAttribute : attributesSeenInFirstEvent) {
                Boolean isSeen = false;
                for (String key : keySet) {
                    XAttribute attributeOfEvent = xAttributeMap.get(key);
                    if (attributeOfEvent.equals(possibleTraceAttribute)) {
                        isSeen = true;
                    }
                }
                if (!isSeen) {
                    if (!attributesNotSeenInAllEvents.contains(possibleTraceAttribute)) {
                        attributesNotSeenInAllEvents.add(possibleTraceAttribute);
                    }
                }
            }
        }
        return attributesNotSeenInAllEvents;
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

        printBinding(binding);
        currentTrace = trace;
        List<String> allTargetArcs = getAllTargetArcs(binding);
        //Integer addedTimeByBinding = getTimeFromBinding(binding);
        if (trace != null) {
            printTrace(trace);
        }

        // Add time to the event
        event1AttributeMap = addTimeToEventMap(event1AttributeMap, event);

        // Add predetermined lifecycle transition
        event1AttributeMap = addLifeCycleTransitionToEventMap(event1AttributeMap, event);


        // Add  other attributes
        event1AttributeMap = addOtherAttributesToEventMap(event1AttributeMap, event, binding, trace);

        // ADD traceID to the event
        XAttributeLiteral xAttributeLiteralCaseId = factory.createAttributeLiteral("traceId", binding.getValueAssignment(caseId).getValue(), null);
        event1AttributeMap.put("traceId", xAttributeLiteralCaseId);

        // create the event from the eventMap
        XEvent xEvent = factory.createEvent(event1AttributeMap);

        return xEvent;
    }

    public XAttributeMap addTimeToEventMap(XAttributeMap eventMap, Event event){
        if(timeHasIncreased) {
            Double eventTime = event.getTime();
            long eventTimeTransformed = (long) (eventTime * timeUnitMultiplier + startTimeLong);
            XAttributeTimestamp xAttributeTimestamp = factory.createAttributeTimestamp("time:timestamp", eventTimeTransformed, null);
            eventMap.put("time", xAttributeTimestamp);
        }
        return eventMap;
    }

    public XAttributeMap addLifeCycleTransitionToEventMap(XAttributeMap eventMap, Event event){
        if(!lifeCycleIsInTransitionName()) {
            if (event.isStartEvent()) {
                XAttributeLiteral xAttributeLiteral = factory.createAttributeLiteral("lifecycle:transition", event.getLifeCycleTransition(), null);
                eventMap.put("lifecycle:transition", xAttributeLiteral);
            } else if (event.isCompleteEvent()) {
                XAttributeLiteral xAttributeLiteral = factory.createAttributeLiteral("lifecycle:transition", event.getLifeCycleTransition(), null);
                eventMap.put("lifecycle:transition", xAttributeLiteral);
            }
        }
        return eventMap;
    }

    public XAttributeMap addOtherAttributesToEventMap(XAttributeMap eventMap, Event event, Binding binding, XTrace trace){
        Queue<Pair<String, String>> transitionInfo = getTransitionInfoFromBinding(binding, trace ,event);
        eventMap = placeTransitionInfoInEventMap(eventMap, transitionInfo);
        return eventMap;
    }

    public XAttributeMap placeTransitionInfoInEventMap(XAttributeMap eventMap, Queue<Pair<String, String>> transitionInfo){
        for(Pair<String, String> pair: transitionInfo){
            if(hasUniqueKey(pair.getKey(), transitionInfo)){
                XAttributeLiteral xAttributeLiteral = factory.createAttributeLiteral(pair.getKey(), pair.getValue(), null);
                eventMap.put(pair.getKey(), xAttributeLiteral);
            } else {
                addListOfDuplicateKeysToEventMap(eventMap, transitionInfo, pair);
            }
        }
        return eventMap;
    }

    public XAttributeMap addListOfDuplicateKeysToEventMap(XAttributeMap eventMap, Queue<Pair<String,String>> transitionInfo, Pair<String, String> pair){
        XAttributeList xAttributeList = factory.createAttributeList(pair.getKey(), null);
        for(Pair<String, String> pair2: transitionInfo){
            int i = 0;
            if(pair2.getKey().equals(pair.getKey())) {
                XAttributeLiteral xAttributeLiteral = factory.createAttributeLiteral("r" + i, pair2.getValue(), null);
                xAttributeList.addToCollection(xAttributeLiteral);
                i++;
            }
        }
        eventMap.put(pair.getKey(), xAttributeList);
        return eventMap;
    }

    public Boolean hasUniqueKey(String key, Queue<Pair<String, String>> transitionInfo){
        Integer count = 0;
        for(Pair<String, String> pair: transitionInfo){
            if(pair.getKey().equals(key)){
                count ++;
            }
        }
        return count == 1;
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
            if(isLifeCycleTransition(transitionSubString) && lifeCycleIsInTransitionName()) {
                info.add(new Pair("concept_name", transitionString.substring(0, indexOfLastPlus + plusCount - 1)));
                info.add(new Pair("lifecycle:transition", transitionSubString));
            } else {
                info.add(new Pair("concept:name", transitionString));
            }
        }

        return info;
    }

    public Queue<Pair<String, String>> getTransitionInfoFromBinding(Binding b, XTrace trace, Event event){
        String transitionString = b.getTransitionInstance().getNode().getName().asString();
        Queue<Pair<String, String>> transitionInfo = getTransitionInfoFromTransitionLabel(transitionString);
        List<Arc> targetArcs = b.getTransitionInstance().getNode().getTargetArc();
        for(Arc arc: targetArcs){
            Queue<Pair<String, String>> arcInfo = getTransitionInfoFromTargetArc(arc, b, trace);
            if(arcInfo != null) {
                for (Pair<String, String> pair : arcInfo) {
                    transitionInfo.add(pair);
                }
            }
        }

        return transitionInfo;
    }

    public Queue<Pair<String, String>> getTransitionInfoFromTargetArc (Arc arc, Binding b, XTrace trace) {
        if (arc.getHlinscription().getText().equals(caseId)) {
            return null;
        }

        Node place = arc.getSource();
        String placeName = place.getName().asString();
        String arcInscription = arc.getHlinscription().getText();
        arcInscription = removeSpaces(arcInscription);
        return getTransitionInfoFromPlaceNameAndThingies(placeName, arcInscription, b, trace);


    }

    public Queue<Pair<String, String>> getTransitionInfoFromPlaceNameAndThingies (String placeName, String arcInscription, Binding b, XTrace trace) {
        Queue gainedInfo = new LinkedList<>();
        if (arcInscription.contains("++")) {
            String[] parts = arcInscription.split(Pattern.quote("++"));
            gainedInfo = mergeQueus(gainedInfo, getTransitionInfoFromListOfStrings(placeName, parts, b, trace));
        } else if (arcInscription.contains(",")) {
            arcInscription = arcInscription.replaceAll("\\(+|\\)", "");
            String[] parts = arcInscription.split(Pattern.quote(","));
            gainedInfo = mergeQueus(gainedInfo, getTransitionInfoFromCommaSeperatedIds(placeName, parts, b, trace));
        } else if (arcInscription.contains("`")) {
            String[] parts2 = arcInscription.split(Pattern.quote("`"));
            Integer amount = Integer.parseInt(parts2[0]);
            for (int i = 0; i < amount; i++) {
                gainedInfo.add(getTransitionInfoFromPlaceNameAndArcInscription(placeName, parts2[1], b));
            }
        } else{
            gainedInfo.add(getTransitionInfoFromPlaceNameAndArcInscription(placeName, arcInscription, b));
        }

        return gainedInfo;
    }

    Queue<Pair<String, String>> mergeQueus (Queue<Pair<String, String>> originalQueue, Queue<Pair<String,String>> newQueue){
        if(newQueue == null){
            return originalQueue;
        }
        for(Pair<String, String> element: newQueue){
            if(element != null) {
                originalQueue.add(element);
            }
        }
        return originalQueue;
    }


    public Queue<Pair<String, String>> getTransitionInfoFromCommaSeperatedIds(String placeName, String[] parts, Binding b, XTrace trace) {
        Queue<Pair<String, String>> gainedInfo = new LinkedList<>();
        List<String> partsList = new ArrayList<>(Arrays.asList(parts));
        if(partsList.contains(caseId)){
            for(String part: parts) {
                if(!part.equals(caseId)) {
                    gainedInfo.add(getInfoFromTrace(b.getValueAssignment(part).getValue().replaceAll("^\"+|\"+$", ""), trace));
                }
            }
            // This ARC contains th caseId DO STUFF
        } else {
            return getTransitionInfoFromListOfStrings(placeName, parts, b, trace);
        }
        return gainedInfo;
    }

    public Pair<String, String> getInfoFromTrace (String part, XTrace trace){
        XEvent oldEvent = currentTrace.get(currentTrace.size()-1);
        XAttributeMap attrMap = oldEvent.getAttributes();
        Set<String> keySet = attrMap.keySet();
        for(String key: keySet){
            String object = String.valueOf(attrMap.get(key));
            if(part.equals(object)){
                return new Pair<>(key, part);
            }
        }
        return null;
    }

    public Queue<Pair<String, String>> getTransitionInfoFromListOfStrings(String placeName, String[] parts, Binding b, XTrace trace) {
        Queue gainedInfo = new LinkedList<>();
        for (String part : parts) {
            gainedInfo = mergeQueus(gainedInfo, getTransitionInfoFromPlaceNameAndThingies(placeName, part, b, trace));
        }
        return gainedInfo;
    }



    public Pair<String, String> getTransitionInfoFromPlaceNameAndArcInscription (String placeName, String arcInscription, Binding b){

        if(arcInscription.equals(caseId)){
            return null;
        }
        // TODO check if this is a resourcePlace
        String valueName = b.getValueAssignment(arcInscription).getValue().replaceAll("^\"+|\"+$", "");
        String colorName = varDeclarations.get(arcInscription);
        String key = colorName + ":" + arcInscription;
        return new Pair(key, valueName);

    }


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

        XAttributeLiteral xAttributeLiteral = factory.createAttributeLiteral("concept:name", value, null);
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
            XAttributeMap xAttributeMap = factory.createAttributeMap();
            trace = factory.createTrace(traceMap);
            trace.add(xEvent);
            if(!informationLevelIsEvent) {
                trace = addCaseIdToTrace(trace, event);
            }
            traces.put(id, trace);
        } else {
            XEvent xEvent = createEventFromBinding(event, traces.get(id));
            trace.add(xEvent);
        }

    }

    public XTrace addCaseIdToTrace(XTrace trace, Event event){
        XAttributeMap traceMap = factory.createAttributeMap();

        // ADD traceID to the event
        XAttributeLiteral xAttributeLiteralCaseId = factory.createAttributeLiteral("concept:name", event.getBinding().getValueAssignment(caseId).getValue(), null);
        traceMap.put("concept:name", xAttributeLiteralCaseId);
        return trace;
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
        return this.recordedEvents.contains("start") || this.recordedEvents.equals("in transition name");
    }

    public Boolean recordCompleteEvent(){
        return this.recordedEvents.contains("complete");
    }

    public Boolean lifeCycleIsInTransitionName(){
        return this.recordedEvents.equals("in transition name");
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
