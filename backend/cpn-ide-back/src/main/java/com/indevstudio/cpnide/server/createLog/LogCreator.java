package com.indevstudio.cpnide.server.createLog;

import javafx.util.Pair;
import org.cpntools.accesscpn.engine.highlevel.instance.Binding;
import org.cpntools.accesscpn.engine.highlevel.instance.ValueAssignment;
import org.cpntools.accesscpn.model.Arc;
import org.cpntools.accesscpn.model.PetriNet;
import org.deckfour.xes.factory.XFactoryRegistry;
import org.deckfour.xes.factory.XFactory;
import org.deckfour.xes.model.*;

import java.text.ParseException;
import java.util.*;
import java.util.regex.Pattern;

public class LogCreator {

    //Config Info
    LogCreationConfig config;

    //ModelInfo
    Map<String, String> varDeclarations;

    //Class Variables
    private XLog log;
    private XAttributeMap traceMap;
    private Map<String, XTrace> traces;

    //Auxilerary classes
    private XFactory factory;

    //SingleTon class variables
    private static LogCreator single_instance = null;





    private List<String[]> arrayWithCSVInfo;

    private StringFixer stringFixer;

    Queue<LogEvent> bindingQueue;
    Queue<LogEvent> backupBindingQueue = new LinkedList<>();

    private LogCreator(){
        factory = XFactoryRegistry.instance().currentDefault();
        bindingQueue = new LinkedList<>();
        stringFixer = new StringFixer();
    }

    public static LogCreator getInstance() {
        if(single_instance == null){
            single_instance = new LogCreator();
        }

        return single_instance;
    }

    public LogCreator getNewContainer(PetriNet net){
        return this;
    }

    public void destroy(){
        single_instance = null;
    }

    public XLog CreateXESLog(LogCreationConfig config) throws Exception {
        this.config = config;
        XAttributeMap logMap = factory.createAttributeMap();
        log = factory.createLog(logMap);
        traceMap = factory.createAttributeMap();

        traces = new HashMap<String, XTrace>();

        LogEvent nextEvent;
        while(!bindingQueue.isEmpty()){
            nextEvent = bindingQueue.poll();
            if((nextEvent.isCompleteEvent() && recordCompleteEvent()) || (nextEvent.isStartEvent() && recordStartEvent())){
                addEventToLog(nextEvent);
            }
            backupBindingQueue.add(nextEvent);
        }

        for(XTrace trace : traces.values())
        {
            if(!config.informationLevelIsEvent) {

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

        bindingQueue = backupBindingQueue;
        backupBindingQueue = new LinkedList<>();

        //TODO REREPLACE THIS WITH FILENAME
        return log;

    }

    public List<String[]> CreateCSVLog(LogCreationConfig config) throws Exception {
        this.config = config;

        arrayWithCSVInfo = new ArrayList<>();

        determineColumnValuesOfCSV();

        LogEvent nextEvent;
        while(!bindingQueue.isEmpty()){
            nextEvent = bindingQueue.poll();
            if((nextEvent.isCompleteEvent() && recordCompleteEvent()) || (nextEvent.isStartEvent() && recordStartEvent())){
                addEventToCSVInput(nextEvent);
            }
            backupBindingQueue.add(nextEvent);
        }
        bindingQueue = backupBindingQueue;
        backupBindingQueue = new LinkedList<>();

        return arrayWithCSVInfo;

    }

    public void determineColumnValuesOfCSV(){
        //Timestamp
        //LifeCycleTransition
        //RemainingAttributes
        Set<String> keySet = varDeclarations.keySet();
        String[] keyArray = keySet.toArray(new String[0]);
        String[] firstRow = new String[keySet.size()+3];
        firstRow[0] = "TimeStamp";
        firstRow[1] = "LifecycleTranstion";
        firstRow[2] = "Concept:name";
        System.arraycopy(keyArray, 0, firstRow, 3, keySet.size());
        arrayWithCSVInfo.add(firstRow);
    }

    public void addEventToCSVInput(LogEvent event) throws ParseException {
        String timeStampEvent = getTimeStampFromEvent(event);
        String lifeCycleTransition = getLifeCycleTransitionFromEvent(event);
        String conceptName = getConceptNameFromEvent(event, lifeCycleTransition);
        String[] varDeclarationsArray = getVarDeclarationsFromEvent(event);
        String[] newRow = new String[varDeclarationsArray.length+3];
        newRow[0] = timeStampEvent;
        newRow[1] = lifeCycleTransition;
        newRow[2] = conceptName;
        System.arraycopy(varDeclarationsArray, 0, newRow, 3, varDeclarationsArray.length);
        arrayWithCSVInfo.add(newRow);
    }

    public String getTimeStampFromEvent(LogEvent event) throws ParseException {
        String result = "";
        Double eventTime = event.getTime();
        long eventTimeTransformed = (long) (eventTime * config.getTimeUnitMultiplier() + config.getStartTimeLong());
        Date date = new Date(eventTimeTransformed);
        result = result + date;
        return result;
    }

    public String getLifeCycleTransitionFromEvent(LogEvent event){
        String result = "";
        if(!lifeCycleIsInTransitionName()) {
            result = event.getLifeCycleTransition();
        } else {
            String transitionString = event.getBinding().getTransitionInstance().getNode().getName().asString();
            String[] parts = transitionString.split(Pattern.quote("+"));
            Integer sizeOfParts = parts.length;
            if(sizeOfParts <= 1){
                return result;
            } else {
                String possibleLifeCycle = parts[sizeOfParts-1];
                if(isLifeCycleTransition(possibleLifeCycle)){
                    return possibleLifeCycle;
                }
                return result;
            }
        }
        return result;
    }

    public String getConceptNameFromEvent(LogEvent event, String lifeCycleTransitionValue){
        String result = "";
        if(!lifeCycleIsInTransitionName()) {
            result = event.getBinding().getTransitionInstance().getNode().getName().asString();
        } else {
            result = event.getBinding().getTransitionInstance().getNode().getName().asString().replace("+" + lifeCycleTransitionValue, "");
        }
        return result;
    }

    public String[] getVarDeclarationsFromEvent(LogEvent event){
        Set<String> keySet = varDeclarations.keySet();
        String[] result = new String[keySet.size()];
        int i = 0;
        for(String key: keySet){
            if(keyIsBinded(key, event)) {
                String value = event.getBinding().getValueAssignment(key).getValue();
                result[i] = value;
            } else {
                result[i] = "";
            }
            i++;
        }
        return result;
    }

    public Boolean keyIsBinded(String key, LogEvent event){
        return event.getBinding().getValueAssignment(key) != null;
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

    public List<String> getAllTargetArcs(Binding b){
        return null;
    }

    public void printTrace(XTrace trace){
        System.out.println("Trace = " + trace);
        System.out.println("Last event = " + trace.get(trace.size()-1));
    }

    public XEvent createXEventFromBinding(LogEvent logEvent) throws ParseException {
        XAttributeMap event1AttributeMap = factory.createAttributeMap();
        Binding binding = logEvent.getBinding();

        // Add time to the event
        event1AttributeMap = addTimeToEventMap(event1AttributeMap, logEvent);

        // Add predetermined lifecycle transition
        event1AttributeMap = addLifeCycleTransitionFromConfigToEventMap(event1AttributeMap, logEvent);

        // Add other attributes
        event1AttributeMap = addRemainingAttributesToEventMap(event1AttributeMap, binding);

        // ADD traceID to the event
        XAttributeLiteral xAttributeLiteralCaseId = factory.createAttributeLiteral("traceId", binding.getValueAssignment(config.caseId).getValue(), null);
        event1AttributeMap.put("traceId", xAttributeLiteralCaseId);

        // create the event from the eventMap
        XEvent xEvent = factory.createEvent(event1AttributeMap);

        return xEvent;
    }

    public XAttributeMap addTimeToEventMap(XAttributeMap eventMap, LogEvent logEvent) throws ParseException {
        if(config.getTimeHasIncreased()) {
            Double eventTime = logEvent.getTime();
            long eventTimeTransformed = (long) (eventTime * config.getTimeUnitMultiplier() + config.getStartTimeLong());
            XAttributeTimestamp xAttributeTimestamp = factory.createAttributeTimestamp("time:timestamp", eventTimeTransformed, null);
            eventMap.put("time", xAttributeTimestamp);
        }
        return eventMap;
    }

    public XAttributeMap addLifeCycleTransitionFromConfigToEventMap(XAttributeMap eventMap, LogEvent logEvent){
        if(!lifeCycleIsInTransitionName()) {
            if (logEvent.isStartEvent()) {
                XAttributeLiteral xAttributeLiteral = factory.createAttributeLiteral("lifecycle:transition", logEvent.getLifeCycleTransition(), null);
                eventMap.put("lifecycle:transition", xAttributeLiteral);
            } else if (logEvent.isCompleteEvent()) {
                XAttributeLiteral xAttributeLiteral = factory.createAttributeLiteral("lifecycle:transition", logEvent.getLifeCycleTransition(), null);
                eventMap.put("lifecycle:transition", xAttributeLiteral);
            }
        }
        return eventMap;
    }

    public XAttributeMap addRemainingAttributesToEventMap(XAttributeMap eventMap, Binding binding){
        Queue<Pair<String, String>> transitionInfo = getTransitionInfoFromBinding(binding);
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
        int i = 0;
        for(Pair<String, String> pair2: transitionInfo){

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

    public Queue<Pair<String, String>> getTransitionInfoFromTransitionLabel(String transitionString){
        Queue<Pair<String, String>> info = new LinkedList<>();
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
            //TODO MAYBE SPLIT THIS
            if(isLifeCycleTransition(transitionSubString) && lifeCycleIsInTransitionName()) {
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
            Queue<Pair<String, String>> arcInfo = getTransitionInfoFromTargetArc(arc, b);
            if(arcInfo != null) {
                for (Pair<String, String> pair : arcInfo) {
                    transitionInfo.add(pair);
                }
            }
        }

        return transitionInfo;
    }

    public Queue<Pair<String, String>> getTransitionInfoFromTargetArc(Arc arc, Binding b) {
        if (arc.getHlinscription().getText().equals(config.caseId)) {
            return null;
        }
        String arcInscription = arc.getHlinscription().getText();
        arcInscription = stringFixer.removeSpaces(arcInscription);
        return getTransitionInfoFromArcInscriptionNew(arcInscription, b);
    }

//    public Queue<Pair<String, String>> getTransitionInfoFromArcInscription(String arcInscription, Binding b, XTrace trace) {
//        Queue gainedInfo = new LinkedList<>();
//        if (arcInscription.contains("++")) {
//            String[] parts = arcInscription.split(Pattern.quote("++"));
//            gainedInfo = mergeQueus(gainedInfo, getTransitionInfoFromListOfStrings(parts, b, trace));
//        } else if (arcInscription.contains(",")) {
//            arcInscription = arcInscription.replaceAll("\\(+|\\)", "");
//            String[] parts = arcInscription.split(Pattern.quote(","));
//            gainedInfo = mergeQueus(gainedInfo, getTransitionInfoFromCommaSeperatedIds(parts, b, trace));
//        } else if (arcInscription.contains("`")) {
//            String[] parts2 = arcInscription.split(Pattern.quote("`"));
//            Integer amount = Integer.parseInt(parts2[0]);
//            for (int i = 0; i < amount; i++) {
//                gainedInfo.add(getTransitionInfoFromBindingVariable(parts2[1], b));
//            }
//        } else{
//            gainedInfo.add(getTransitionInfoFromBindingVariable(arcInscription, b));
//        }
//
//        return gainedInfo;
//    }

    public Queue<Pair<String, String>> getTransitionInfoFromArcInscriptionNew(String arcInscription, Binding b) {
        Queue gainedInfo = new LinkedList<>();
        String[] parts = SeperateArcInScriptionInListOfStrings(arcInscription);
        for(String part: parts) {
            if (arcInscription.contains("`")) {
                String[] arrayWithAmountAndBindingVariable = part.split(Pattern.quote("`"));
                Integer amount = Integer.parseInt(arrayWithAmountAndBindingVariable[0]);
                for (int i = 0; i < amount; i++) {
                    if(!partIsCaseId(arrayWithAmountAndBindingVariable[1])) {
                        gainedInfo.add(getTransitionInfoFromBindingVariable(arrayWithAmountAndBindingVariable[1], b));
                    }
                }
            } else {
                if(!partIsCaseId(part)) {
                    gainedInfo.add(getTransitionInfoFromBindingVariable(part, b));
                }
            }
        }

        return gainedInfo;
    }

    public Boolean partIsCaseId(String part){
        return part.equals(config.caseId);
    }

    public String[] SeperateArcInScriptionInListOfStrings(String arcInscription){
        arcInscription = arcInscription.replaceAll("\\(+|\\)", "");
        String[] listOfSeperatedStrings = {};
        String[] plusSeperatedStrings = arcInscription.split(Pattern.quote("++"));
        for(String plusSeperatedString: plusSeperatedStrings){
            String[] commaSeperatedString = plusSeperatedString.split(Pattern.quote(","));
            listOfSeperatedStrings = mergeArrays(listOfSeperatedStrings, commaSeperatedString);
        }
        return listOfSeperatedStrings;
    }

    public String[] mergeArrays(String[] originalArray, String[] toAddArray){
        int originalArrayLength = originalArray.length;
        int toAddArrayLength = toAddArray.length;
        String[] outputArray = new String[originalArrayLength + toAddArrayLength];
        System.arraycopy(originalArray, 0, outputArray, 0, originalArrayLength);
        System.arraycopy(toAddArray, 0, outputArray, originalArrayLength, toAddArrayLength);

        return outputArray;
    }

    public Pair<String, String> getTransitionInfoFromBindingVariable(String arcInscription, Binding b){
        if(arcInscription.equals(config.caseId)){
            System.out.println("I need to add an error here. This should not happen");
            return null;
        }
        // TODO check if this is a resourcePlace
        String valueName = b.getValueAssignment(arcInscription).getValue().replaceAll("^\"+|\"+$", "");
        String colorName = varDeclarations.get(arcInscription);
        String key = colorName + ":" + arcInscription;
        return new Pair(key, valueName);

    }

//    Queue<Pair<String, String>> mergeQueus (Queue<Pair<String, String>> originalQueue, Queue<Pair<String,String>> newQueue){
//        if(newQueue == null){
//            return originalQueue;
//        }
//        for(Pair<String, String> element: newQueue){
//            if(element != null) {
//                originalQueue.add(element);
//            }
//        }
//        return originalQueue;
//    }

//    public Queue<Pair<String, String>> getTransitionInfoFromCommaSeperatedIds(String[] parts, Binding b, XTrace trace) {
//        Queue<Pair<String, String>> gainedInfo = new LinkedList<>();
//        List<String> partsList = new ArrayList<>(Arrays.asList(parts));
//        if(partsList.contains(caseId)){
//            for(String part: parts) {
//                if(!part.equals(caseId)) {
//                    gainedInfo.add(getInfoFromTrace(b.getValueAssignment(part).getValue().replaceAll("^\"+|\"+$", ""), trace));
//                }
//            }
//            // This ARC contains th caseId DO STUFF
//        } else {
//            return getTransitionInfoFromListOfStrings(parts, b, trace);
//        }
//        return gainedInfo;
//    }

//    public Pair<String, String> getInfoFromTrace (String part, XTrace trace){
//        XEvent oldEvent = currentTrace.get(currentTrace.size()-1);
//        XAttributeMap attrMap = oldEvent.getAttributes();
//        Set<String> keySet = attrMap.keySet();
//        for(String key: keySet){
//            String object = String.valueOf(attrMap.get(key));
//            if(part.equals(object)){
//                return new Pair<>(key, part);
//            }
//        }
//        return null;
//    }

//    public Queue<Pair<String, String>> getTransitionInfoFromListOfStrings(String[] parts, Binding b, XTrace trace) {
//        Queue gainedInfo = new LinkedList<>();
//        for (String part : parts) {
//            gainedInfo = mergeQueus(gainedInfo, getTransitionInfoFromArcInscription(part, b, trace));
//        }
//        return gainedInfo;
//    }

    public void addEventToLog(LogEvent logEvent) throws Exception{
        Binding b = logEvent.getBinding();
        String id = null;

        //System.out.println(b.getTransitionInstance().getNode().getName().asString());
        for(ValueAssignment assignment: b.getAllAssignments())
        {
            if(assignment.getName().equals(config.caseId)){
                id = assignment.getValue();
            }
        }
        if(id == null){
            System.out.println("Ignore this binding");
            return;
        }
        XTrace trace = traces.get(id);

        XEvent xEvent = createXEventFromBinding(logEvent);

        if(trace == null){
            trace = factory.createTrace(traceMap);
            if(!config.informationLevelIsEvent) {
                trace = addCaseIdToTraceAttributes(trace, logEvent);
            }
            traces.put(id, trace);
        }

        trace.add(xEvent);

    }

    public XTrace addCaseIdToTraceAttributes(XTrace trace, LogEvent logEvent){
        XAttributeMap traceMap = factory.createAttributeMap();

        // ADD traceID to the event
        XAttributeLiteral xAttributeLiteralCaseId = factory.createAttributeLiteral("concept:name", logEvent.getBinding().getValueAssignment(config.caseId).getValue(), null);
        traceMap.put("concept:name", xAttributeLiteralCaseId);
        return trace;
    }


    public Boolean recordStartEvent(){
        return config.recordedEvents.contains("start") || config.recordedEvents.equals("in transition name");
    }

    public Boolean recordCompleteEvent(){
        return config.recordedEvents.contains("complete");
    }

    public Boolean lifeCycleIsInTransitionName(){
        return config.recordedEvents.equals("in transition name");
    }

    public void setVarDeclarations(Map<String, String> varDeclarations) {
        this.varDeclarations = varDeclarations;
    }

    public void setBindingQueue(Queue<LogEvent> bindingQueue) {
        this.bindingQueue = bindingQueue;
    }

}
