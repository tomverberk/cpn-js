package com.indevstudio.cpnide.server.createLog;

//import openXES;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.indevstudio.cpnide.server.model.SimInfo;

//import javafx.util.Pair;
import javafx.util.Pair;
import lombok.extern.java.Log;
import org.cpntools.accesscpn.model.Object;
import org.cpntools.accesscpn.model.impl.ArcImpl;
import org.cpntools.accesscpn.model.impl.PlaceImpl;
import org.cpntools.accesscpn.model.impl.TransitionImpl;

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

import static org.junit.Assert.assertTrue;

public class LogCreationController {

    LogCreationConfig config;

    Map<String, String> varDeclarations;

    private Boolean isRecording = false;
    private Boolean isRecordingTime = true;
    private List<String> tauTransitions = new LinkedList<>();
    private String pathName;
    private StringFixer stringFixer;

    private XLog XESLog;
    private List<String[]> CSVLog;

    private String folderName;

    private LogCreator logCreator;

    Queue<LogEvent> bindingQueue = new LinkedList<>();
    Queue<LogEvent> backupBindingQueue = new LinkedList<>();

    private static LogCreationController single_instance = null;

    private LogCreationController(){
        //TODO
    }

    public static LogCreationController getInstance() {
        if(single_instance == null){
            single_instance = new LogCreationController();
        }

        return single_instance;
    }

    public LogCreationController getNewContainer(PetriNet net){
        logCreator = LogCreator.getInstance();
        stringFixer = new StringFixer();
        setTauTransitions(net);
        saveDeclarations(net);
        return this;
    }

    public void saveDeclarations(PetriNet net){
        varDeclarations = new HashMap<>();
        for(Label label: net.getLabel()){
            if(isVarDeclaration(label)){
                addDeclarationToVarDeclarations(label);
            }
        }
        logCreator.setVarDeclarations(varDeclarations);
        // if it is a var, add to varDeclaration dataset as a pair <var, colorset>
    }

    public boolean isVarDeclaration(Label label){
        String declaration  = label.asString();
        return declaration.startsWith("var");
    }

    public void addDeclarationToVarDeclarations(Label label){
        String declaration  = label.asString();
        declaration = stringFixer.removeSpaces(declaration);
        Integer indexOfSeperator = declaration.indexOf(":");
        String var = declaration.substring(3, indexOfSeperator);
        String colorSet = declaration.substring(indexOfSeperator + 1, declaration.length()-1);
        varDeclarations.put(var, colorSet);
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


    public void setConfig(LogCreationConfig config) throws Exception {
        //setCaseId(config.caseId);
        //setStartTime(config.startDateTime);
        //setTimeUnits(config.timeUnit);
    }



//    public void setTimeUnits(String timeUnit) throws Exception {
//        logCreator.setTimeUnit(timeUnit);
//        switch(timeUnit){
//            case "years":
//                timeUnitMultiplier = (long) 1000 * 60 * 60 * 24 * 365;
//                break;
//            case "months":
//                timeUnitMultiplier = (long) 1000 * 60 * 60 * 24 * 30;
//                break;
//            case "weeks":
//                timeUnitMultiplier = (long) 1000 * 60 * 60 * 24 * 7;
//                break;
//            case "days":
//                timeUnitMultiplier = (long) 1000 * 60 * 60 * 24;
//                break;
//            case "hours":
//                timeUnitMultiplier = (long) 1000 * 60 * 60;
//                break;
//            case "minutes":
//                timeUnitMultiplier = (long) 1000 * 60;
//                break;
//            case "seconds":
//                timeUnitMultiplier = (long) 1000;
//                break;
//            default:
//                System.out.println("no correct timeUnit given");
//                throw new IllegalArgumentException(timeUnit + " is not a valid timeunit");
//        }
//        logCreator.setTimeUnitMultiplier(timeUnitMultiplier);
//    }

    public void createLog(LogCreationConfig config, Double timeLastUpdatedEvent) throws Exception{
        this.config = config;
        setConfig(config);
        config.setTimeHasIncreased(timeLastUpdatedEvent);
        logCreator.setBindingQueue(bindingQueue);
        if(config.exportType.equals("csv")){
            CreateCSVLog(config);
        } else if(config.exportType.equals("xes")){
            CreateXESLog(config);
        }
    }



    public void exportCSV(File file) throws IOException{
        try (PrintWriter pw = new PrintWriter(file)) {
            CSVLog.stream()
                    .map(this::convertToCSV)
                    .forEach(pw::println);
        }
        assertTrue(file.exists());
    }

    public String convertToCSV(String[] data) {
        return Stream.of(data)
                .map(this::escapeSpecialCharacters)
                .collect(Collectors.joining(","));
    }

    public String escapeSpecialCharacters(String data) {
        String escapedData = data.replaceAll("\\R", " ");
        if (data.contains(",") || data.contains("\"") || data.contains("'")) {
            data = data.replace("\"", "\"\"");
            escapedData = "\"" + data + "\"";
        }
        return escapedData;
    }







//    public Boolean lifeCycleIsInTransitionName(){
//        return config.recordedEvents.equals("in transition name");
//    }



//    public Boolean recordCompleteEvent(){
//        return config.recordedEvents.contains("complete");
//    }

//    public Boolean recordStartEvent(){
//        return config.recordedEvents.contains("start") || config.recordedEvents.equals("in transition name");
//    }

    /**
     *
     */
    public void CreateXESLog(LogCreationConfig config) throws Exception {
        XESLog = logCreator.CreateXESLog(config);
        bindingQueue = logCreator.bindingQueue;

        File directory = new File(folderName);
        directory.mkdirs();

        String fileName = pathName + ".xes";

        File file = new File(fileName);

        System.out.println(fileName);


        try
        {
            exportLog(file);
        } catch(Exception e) {
            System.out.println(e);
        }

    }

    public void CreateCSVLog(LogCreationConfig config) throws Exception{
        CSVLog = logCreator.CreateCSVLog(config);
        bindingQueue = logCreator.bindingQueue;

        File directory = new File(folderName);
        directory.mkdirs();

        String fileName = pathName + ".csv";

        File file = new File(fileName);

        System.out.println(fileName);


        try
        {
            exportCSV(file);
        } catch(Exception e) {
            System.out.println(e);
        }

    }

    public void clearLog(){
        System.out.println("Clear the Log");
        bindingQueue = new LinkedList<>();
        single_instance.destroy();
        single_instance = getInstance();
    }

    public void exportLog(File file) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        XSerializer logSerializer = new XesXmlSerializer();

        logSerializer.serialize(XESLog, out);
        out.close();
    }

    public void setOutputPath(String path, String outputDir){
        outputDir = outputDir.replace("/cygdrive/C", "");
        outputDir = outputDir.replace("model_out", "log_out");
        this.folderName = outputDir;
        this.pathName = outputDir + "/" + path;
    }



//    public void setStartTime(String startDateTime) throws ParseException {
//        String modifiedStartDateTimeString = startDateTime.replace("T", " ");
//        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
//        Date date = formatter.parse(modifiedStartDateTimeString);
//        logCreator.setStartTimeLong(date.getTime());
//        this.startTimeLong = date.getTime();
//    }





//    public Integer getTimeFromBinding(Binding binding){
//        String string = binding.getTransitionInstance().getNode().getTime().asString();
//        string = string.replace("@", "").replace(" ", "").replace("+", "");
//        Integer addedTime = 0;
//        if(string.equals("")){
//            return 0;
//        } else {
//            try {
//                addedTime = Integer.parseInt(string);
//            } catch (NumberFormatException e) {
//                e.printStackTrace();
//            }
//        }
//
//        return addedTime;
//    }












    public Boolean isTauTransition(Binding b){
        String transitionName = b.getTransitionInstance().getNode().getName().asString();
        for(String tauTransition : tauTransitions){
            if(tauTransition.equals(transitionName)){
                return true;
            }
        }
        return false;
    }





    public Boolean isRecordingTime(){
        return this.isRecordingTime;
    }

    public void recordActivity(Binding b, SimInfo info, Double endTimeActivity){
        if(!isRecording) {
            return;
        }
        if(isTauTransition(b)){
            return;
        }
        System.out.println(info.getTime());
        Double simulatorTime = makeTimeDouble(info.getTime());

        bindingQueue.add(new LogEvent(b, simulatorTime, "start"));
        if (endTimeActivity != null) {
            bindingQueue.add(new LogEvent(b, endTimeActivity, "complete"));
        } else {
            bindingQueue.add(new LogEvent(b, simulatorTime, "complete"));
        }

    }

    public Double makeTimeDouble(String stringTime){
        if(stringTime == ""){
            return 0.0;
        } else {
            return Double.parseDouble(stringTime);
        }
    }





//    public void setOutputPath(String correctPath){
//        this.pathNameWithoutcygDrive = correctPath.replace("/cygdrive/C", "");
//    }

    public String getOutputPath(){
        return this.pathName;
    }

    //TODO currently not in use
    public XLog getLog(){
        System.out.println(XESLog.toString());
        return this.XESLog;
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
        if(config.exportType.equals("csv")){
            //This should not happen, as the program will only give an error for no recorded events
            return CSVLog.size() == 1;
        } else if (config.exportType.equals("xes")) {
            if (XESLog == null) {
                return true;
            } else {
                return XESLog.isEmpty();
            }
        } else {
            // should not happen
            return true;
        }
    }



//    public void setCaseId(String caseId){
//        logCreator.setCaseId(caseId);
//    }

}
