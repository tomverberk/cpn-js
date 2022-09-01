package com.indevstudio.cpnide.server.createLog;

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

    /**
     *
     */
    private LogCreator(){
        factory = XFactoryRegistry.instance().currentDefault();
        bindingQueue = new LinkedList<>();
        stringFixer = new StringFixer();
    }

    /**
     * Getinstance method
     * @return the logCreator class
     */
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

    /**
     * Function that creates a XES log using the recorded events
     * @param config, the configuration settings of the log
     * @return a log with all its attributes as a XLog
     * @throws Exception
     */
    public XLog CreateXESLog(LogCreationConfig config) throws Exception {
        System.out.println("Enter create log");
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
                createTraceAttributes(trace);
            }
            log.add(trace);
        }

        bindingQueue = backupBindingQueue;
        backupBindingQueue = new LinkedList<>();

        return log;
    }

    /**
     * This method checks wether an attribute is part of all events in a trace, if this is the case the
     * attribute is removed from all events in the trace and added to the trace.
     * This is not the case when the element is "transition-name", "lifecycle attribute", or "Timestamp"
     * @param trace
     * @return a trace in which attributes that are part of all events are now part of the trace
     */
    public XTrace createTraceAttributes(XTrace trace){
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


        traceMap = trace.getAttributes();
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
        return trace;
    }

    /**
     * Function that creates a XES log using the recorded events
     * TODO I don't know if this works
     * @param config, the configuration settings of the log
     * @return a log in List<String[]> format
     * @throws Exception
     */
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

    /**
     * Method used to determine what the how an Empty CSV file should be
     */
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

    /**
     * Method that adds an event to the CSV file
     * @param event
     * @throws ParseException
     */
    public void addEventToCSVInput(LogEvent event) throws ParseException {
        String timeStampEvent = getTimeStampFromEvent(event).toString();
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

    /**
     * Method that gets the timestamp for from the simulator time of the event using the configurations
     * @param event
     * @return Real life time stamp of the event
     * @throws ParseException
     */
    public Date getTimeStampFromEvent(LogEvent event) throws ParseException {
        double eventTime = event.getTime();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date(config.getStartTimeLong()));
        calendar.add(config.getCalendarValue(), (int) eventTime);
        if(!config.getTimeUnit().equals("years") && !config.getTimeUnit().equals(("months"))){
            calendar.add(Calendar.MILLISECOND, (int) (eventTime % 1 * config.getTimeUnitMultiplier()));
        } else { //value becomes to large for int
            //Months and decimal numbers don't work well together
            if(config.getTimeUnit().equals("months") && eventTime % 1 != 0){
                int month = calendar.get(Calendar.MONTH);
                double monthMultiplier;
                if(month == 1){
                    monthMultiplier = 28.0/31.0;
                } else if (month == 3 || month == 5 || month == 8 || month == 10){
                    monthMultiplier = 30.0/31.0;
                } else {
                    monthMultiplier = 1;
                }
                calendar.add(Calendar.SECOND, (int) (eventTime % 1 * monthMultiplier * (config.getTimeUnitMultiplier()/1000)));
            } else {
                calendar.add(Calendar.SECOND, (int) (eventTime % 1 * (config.getTimeUnitMultiplier() / 1000)));
            }
        }
        return calendar.getTime();
    }

    /**
     * Function that gets the lifecycle transition element from a transition
     * @param event
     * @return the lifecycle transition from the event
     */
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

    /**
     * Get the concept name from an event
     * @param event
     * @param lifeCycleTransitionValue
     * @return the concept name of an event
     */
    public String getConceptNameFromEvent(LogEvent event, String lifeCycleTransitionValue){
        String result = "";
        if(!lifeCycleIsInTransitionName()) {
            result = event.getBinding().getTransitionInstance().getNode().getName().asString();
        } else {
            result = event.getBinding().getTransitionInstance().getNode().getName().asString().replace("+" + lifeCycleTransitionValue, "");
        }
        return result;
    }

    /**
     * Gets all the value declarations from an event (all elements in the binding)
     * @param event
     * @return a list of value declarations
     */
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

    /**
     * Method that checks if the key is binded to an event
     * @param key
     * @param event
     * @return true if the key is binded, false if the key is not binded
     */
    public Boolean keyIsBinded(String key, LogEvent event){
        return event.getBinding().getValueAssignment(key) != null;
    }


    /**
     * Method that finds attributes that are part of all events in the trace
     * @param trace
     * @return a Queue of attributes that are part of all events in a trace
     */
    public Queue<XAttribute> findTraceAttributes(XTrace trace){
        Queue<XAttribute> attributesSeenInFirstEvent = findPossibleTraceAttributesOfFirstEvent(trace);

        Queue<XAttribute> attributesNotSeenInAllEvents = findAttributesNotInAllEvents(trace, attributesSeenInFirstEvent);

        Queue<XAttribute> attributesSeenInAllEvents = attributesSeenInFirstEvent;
        attributesSeenInAllEvents.removeAll(attributesNotSeenInAllEvents);

        return attributesSeenInAllEvents;
    }

    /**
     * Gets all the attributes of the first event, which are not the conceptname, lifecycl and timestamp attributes
     * @param trace
     * @return a queue of attributes that are part of the first event (without the above mentioned)
     */
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

    /**
     * Check if the attributes in the first events are also in the other events
     * @param trace
     * @param attributesSeenInFirstEvent
     * @return A queue of elements that are not seen in all events.
     */
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

    /**
     * Method that prints attributes of a binding, build for Debug purposes
     * @param binding
     */
    public void printBinding(Binding binding){
        System.out.println("binding= " + binding);
        System.out.println("getAllAssignments =" + binding.getAllAssignments());
        System.out.println("transition name = " + binding.getTransitionInstance().getNode().getName().asString());
        System.out.println("time hopefully = " + binding.getTransitionInstance().getNode().getTime());
        List<Arc> allTargetArcs = binding.getTransitionInstance().getNode().getTargetArc();
        for(Arc arc: allTargetArcs){
            System.out.println("an targetarc = " + arc);
            String arcInscription = arc.getHlinscription().getText();
            System.out.println(arcInscription);
        }
        System.out.println("targetNodes hopefully = " + allTargetArcs);

    }

    /**
     * Method that creates an Xevent from a LogEvent
     * @param logEvent
     * @return an XEvent which will be placed in a trace
     * @throws ParseException
     */
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
        //XAttributeLiteral xAttributeLiteralCaseId = factory.createAttributeLiteral("traceId", binding.getValueAssignment(config.caseId).getValue(), null);
        //event1AttributeMap.put("traceId", xAttributeLiteralCaseId);

        // create the event from the eventMap
        XEvent xEvent = factory.createEvent(event1AttributeMap);

        return xEvent;
    }

    /**
     * Method that adds time to the eventMap
     * @param eventMap the eventMap time is added to
     * @param logEvent the logEvent the time comes from
     * @return the original eventMap with time added to it
     * @throws ParseException
     */
    public XAttributeMap addTimeToEventMap(XAttributeMap eventMap, LogEvent logEvent) throws ParseException {
        if(config.getTimeHasIncreased()) {
            Date date = getTimeStampFromEvent(logEvent);
            XAttributeTimestamp xAttributeTimestamp = factory.createAttributeTimestamp("time:timestamp", date, null);
            eventMap.put("time", xAttributeTimestamp);
        }
        return eventMap;
    }

    /**
     * Method that adds the lifecycle transtition to the event map BASED ON THE CONFIG
     * this function does nothing when the lifecycle transition attribute is "in transition name"
     * @param eventMap the eventMap lifecycle transition is added to
     * @param logEvent the logEvent the time  comes from
     * @return the original eventMap with lifecycle transition added to it.
     */
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

    /**
     * Method that adds the remaining attributes to the eventMap
     * @param eventMap The eventmap the remaining attributes are added to
     * @param binding The binding of the event, used to get information needed
     * @return the original eventmap with added attributes
     */
    public XAttributeMap addRemainingAttributesToEventMap(XAttributeMap eventMap, Binding binding){
        Queue<TransitionInfo> transitionInfo = getTransitionInfoFromBinding(binding);
        eventMap = placeTransitionInfoInEventMap(eventMap, transitionInfo);
        return eventMap;
    }

    /**
     * Function that places all the aquired transitionInfo in an eventMap
     * @param eventMap the eventmap the info should be added to
     * @param transitionInfoQueue All the info that needs to be added to the event
     * @return the original eventMap with the all the elements from the transitionInfoQueue
     */
    public XAttributeMap placeTransitionInfoInEventMap(XAttributeMap eventMap, Queue<TransitionInfo> transitionInfoQueue){
        List<String> duplicateKeys = new LinkedList<>();
        for(TransitionInfo transitionInfo: transitionInfoQueue){
            if(hasUniqueKey(transitionInfo.getKey(), transitionInfoQueue) || duplicateKeys.contains(transitionInfo.getKey())){
                XAttributeLiteral xAttributeLiteral = factory.createAttributeLiteral(transitionInfo.getKey(), transitionInfo.getValue(), null);
                eventMap.put(transitionInfo.getKey(), xAttributeLiteral);
            } else {
                addListOfDuplicateKeysToEventMap(eventMap, transitionInfoQueue, transitionInfo);
                duplicateKeys.add(transitionInfo.getKey());
            }
        }
        return eventMap;
    }

    /**
     * When there is more than one element of the same type and on the same variable add a list of variables instead of a singular variable
     * @param eventMap the eventMap the list should be added to
     * @param transitionInfoQueue to check how many values should be added
     * @param transitionInfo The info that has a duplicate
     * @return the eventMap with the duplicate info added
     */
    public XAttributeMap addListOfDuplicateKeysToEventMap(XAttributeMap eventMap, Queue<TransitionInfo> transitionInfoQueue, TransitionInfo transitionInfo){
        XAttributeList xAttributeList = factory.createAttributeList(transitionInfo.getKey(), null);
        int i = 0;
        for(TransitionInfo transitionInfo2: transitionInfoQueue){
            if(transitionInfo2.getKey().equals(transitionInfo.getKey())) {
                XAttributeLiteral xAttributeLiteral = factory.createAttributeLiteral("r" + i, transitionInfo2.getValue(), null);
                xAttributeList.addToCollection(xAttributeLiteral);
                i++;
            }
        }
        eventMap.put(transitionInfo.getKey(), xAttributeList);
        return eventMap;
    }

    /**
     * Method that checks if the key is unique in the transitionQueue
     * @param key
     * @param transitionInfoQueue
     * @return True when the key is only once in the Queue, false if it is more than once in the queue
     */
    public Boolean hasUniqueKey(String key, Queue<TransitionInfo> transitionInfoQueue){
        Integer count = 0;
        for(TransitionInfo transitionInfo: transitionInfoQueue){
            if(transitionInfo.getKey().equals(key)){
                count ++;
            }
        }
        return count == 1;
    }

    /**
     * Checks if a string is a lifecycle transition string
     * @param transitionSubString
     * @return True if the string is a lifecycle transition string, false otherwise
     */
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

    /**
     * Gets information about the transition from the string in the transition,
     * this info is the name of the transition and the lifecycle transition attribute
     * @param transitionString
     * @return Info containing the transitionName and possible lifecycle transition attribute
     */
    public Queue<TransitionInfo> getTransitionInfoFromTransitionLabel(String transitionString){
        Queue<TransitionInfo> info = new LinkedList<>();
        String transitionSubString = transitionString;
        Integer indexOfLastPlus = 0;
        Integer plusCount = 0;
        if(!transitionString.contains("+")){
            info.add(new TransitionInfo("concept:name", transitionString));
        } else {
            while(transitionSubString.contains("+")){
                Integer indexOfPlus = transitionSubString.indexOf("+");
                indexOfLastPlus = indexOfLastPlus + indexOfPlus;
                transitionSubString = transitionSubString.substring(indexOfPlus+1);
                plusCount ++;
            }
            if(isLifeCycleTransition(transitionSubString) && lifeCycleIsInTransitionName()) {
                info.add(new TransitionInfo("concept_name", transitionString.substring(0, indexOfLastPlus + plusCount - 1)));
                info.add(new TransitionInfo("lifecycle:transition", transitionSubString));
            } else {
                info.add(new TransitionInfo("concept:name", transitionString));
            }
        }

        return info;
    }

    /**
     * Gets transition info from the binding
     * @param b the binding of the fired transition
     * @return A queue with all information except CaseId from all incoming edges
     */
    public Queue<TransitionInfo> getTransitionInfoFromBinding(Binding b){
        String transitionString = b.getTransitionInstance().getNode().getName().asString();

        Queue<TransitionInfo> transitionInfoQueue = getTransitionInfoFromTransitionLabel(transitionString);

        List<Arc> targetArcs = b.getTransitionInstance().getNode().getTargetArc();
        for(Arc arc: targetArcs){
            Queue<TransitionInfo> targetArcTransitionInfoQueue = getTransitionInfoFromTargetArc(arc, b);
            if(targetArcTransitionInfoQueue != null) {
                for (TransitionInfo transitionInfo : targetArcTransitionInfoQueue) {
                    transitionInfoQueue.add(transitionInfo);
                }
            }
        }

        return transitionInfoQueue;
    }

    /**
     * Gets the transitionInfo from a specific target Arc
     * @param arc The arc we want to get the information from
     * @param b The binding of the fired transition
     * @return the information on the arc without the caseId
     */
    public Queue<TransitionInfo> getTransitionInfoFromTargetArc(Arc arc, Binding b) {
        if (arc.getHlinscription().getText().equals(config.caseId)) {
            return null;
        }
        String arcInscription = arc.getHlinscription().getText();
        arcInscription = stringFixer.removeSpaces(arcInscription);
        return getTransitionInfoFromArcInscriptionNew(arcInscription, b);
    }

    /**
     * Get the information from the inscription of an arc
     * @param arcInscription the inscription we want information from
     * @param b the binding
     * @return All information of the inscription except the caseId
     */
    public Queue<TransitionInfo> getTransitionInfoFromArcInscriptionNew(String arcInscription, Binding b) {
        Queue gainedInfo = new LinkedList<>();
        String[] parts = SeperateArcInScriptionInListOfStrings(arcInscription);
        for(String part: parts) {
            if (arcInscription.contains("`")) {
                String[] arrayWithAmountAndBindingVariable = part.split(Pattern.quote("`"));
                Integer amount = Integer.parseInt(arrayWithAmountAndBindingVariable[0]);
                if(arrayWithAmountAndBindingVariable.length > 1) {
                    for (int i = 0; i < amount; i++) {
                        if (!partIsCaseId(arrayWithAmountAndBindingVariable[1])) {
                            gainedInfo.add(getTransitionInfoFromBindingVariable(arrayWithAmountAndBindingVariable[1], b));
                        }
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

    /**
     * Checks wethr a string is the caseId string
     * @param string
     * @return true if the string is the caseId false otherwise
     */
    public Boolean partIsCaseId(String string){
        return string.equals(config.caseId);
    }

    /**
     * Seperates the arcInscription into a list of strings, seperates on both "+" sign and "," sign
     * @param arcInscription
     * @return a list of strings from the arcInscription
     */
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

    /**
     * Merges 2 arrays
     * @param originalArray
     * @param toAddArray
     * @return the merging of the two arrays
     */
    public String[] mergeArrays(String[] originalArray, String[] toAddArray){
        int originalArrayLength = originalArray.length;
        int toAddArrayLength = toAddArray.length;
        String[] outputArray = new String[originalArrayLength + toAddArrayLength];
        System.arraycopy(originalArray, 0, outputArray, 0, originalArrayLength);
        System.arraycopy(toAddArray, 0, outputArray, originalArrayLength, toAddArrayLength);

        return outputArray;
    }

    /**
     * Gets the transitionInfo from the bindingVariable
     * @param arcInscription
     * @param b
     * @return the information from the bindingVariables excluding the value assigned for the caseId binding
     */
    public TransitionInfo getTransitionInfoFromBindingVariable(String arcInscription, Binding b){
        if(arcInscription.equals(config.caseId)){
            System.out.println("I need to add an error here. This should not happen");
            return null;
        }
        // TODO check if this is a resourcePlace
        String valueName = b.getValueAssignment(arcInscription).getValue().replaceAll("^\"+|\"+$", "");
        String colorName = varDeclarations.get(arcInscription);
        String key = colorName + ":" + arcInscription;
        return new TransitionInfo(key, valueName);

    }

    /**
     * Adds an LogEvent to the log
     * @param logEvent
     * @throws Exception
     */
    public void addEventToLog(LogEvent logEvent) throws Exception{
        Binding b = logEvent.getBinding();
        String id = null;

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

        System.out.println("start createXEventFromBinding");
        XEvent xEvent = createXEventFromBinding(logEvent);
        System.out.println("end createXEventFromBinding");

        if(trace == null){
            trace = factory.createTrace(traceMap);
            trace = addCaseIdToTraceAttributes(trace, logEvent);
            traces.put(id, trace);
        }

        trace.add(xEvent);

    }

    /**
     * Adds the caseId attribute to a trace
     * @param trace
     * @param logEvent this event contains the caseId variable
     * @return the trace with as attribute the caseId and its value
     */
    public XTrace addCaseIdToTraceAttributes(XTrace trace, LogEvent logEvent){
        traceMap = trace.getAttributes();

        // ADD traceID to the event
        XAttributeLiteral xAttributeLiteralCaseId = factory.createAttributeLiteral("concept:name", logEvent.getBinding().getValueAssignment(config.caseId).getValue(), null);
        traceMap.put("concept:name", xAttributeLiteralCaseId);
        return trace;
    }


    /**
     * Boolean that shows wether we want to show the startEvents in the log
     * @return true if we want startEvents in the log false otherwise
     */
    public Boolean recordStartEvent(){
        return config.recordedEvents.contains("start") || config.recordedEvents.equals("in transition name");
    }

    /**
     * Boolean that shows wether we want to show the completeEvents in the log
     * @return true if we want completeEvents in the log false otherwise
     */
    public Boolean recordCompleteEvent(){
        return config.recordedEvents.contains("complete");
    }

    /**
     * Checks wether we want to record in transition Name for the recordedEvents attribute.
     * @return
     */
    public Boolean lifeCycleIsInTransitionName(){
        return config.recordedEvents.equals("in transition name");
    }

    /**
     * Sets the varDeclarations
     * @param varDeclarations
     */
    public void setVarDeclarations(Map<String, String> varDeclarations) {
        this.varDeclarations = varDeclarations;
    }

    /**
     * Sets the bindingqueue
     * @param bindingQueue
     */
    public void setBindingQueue(Queue<LogEvent> bindingQueue) {
        this.bindingQueue = bindingQueue;
    }

}
