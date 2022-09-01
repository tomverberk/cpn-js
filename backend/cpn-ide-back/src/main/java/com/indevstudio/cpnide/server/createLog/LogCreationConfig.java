package com.indevstudio.cpnide.server.createLog;

import lombok.Data;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;


public class LogCreationConfig {
    // These values are given from the frontend
    String caseId;
    String exportType;
    Boolean informationLevelIsEvent;
    String recordedEvents;
    String startDateTime;
    String timeUnit;


    // These values need to be initialized from other values
    private long timeUnitMultiplier;
    private long startTimeLong;
    private boolean timeHasIncreased;

    private boolean isInitialized = false;

    public void initializeConfig(Double timeLastUpdatedEvent) throws ParseException {
        setTimeUnitMultiplier();
        setStartTime();
        setTimeHasIncreased(timeLastUpdatedEvent);
        isInitialized = true;
    }

    public long getTimeUnitMultiplier(){
        // RETURN ERROR IF NOT INITIALIZED
        if(!isInitialized){
            setTimeUnitMultiplier();
        }
        return timeUnitMultiplier;
    }

    public void setTimeUnitMultiplier(){
        switch(timeUnit){
            case "years":
                timeUnitMultiplier = (long) 1000 * 60 * 60 * 24 * 365;
                break;
            case "months":
                timeUnitMultiplier = (long) 1000 * 60 * 60 * 24 * 30;
                break;
            case "weeks":
                timeUnitMultiplier = (long) 1000 * 60 * 60 * 24 * 7;
                break;
            case "days":
                timeUnitMultiplier = (long) 1000 * 60 * 60 * 24;
                break;
            case "hours":
                timeUnitMultiplier = (long) 1000 * 60 * 60;
                break;
            case "minutes":
                timeUnitMultiplier = (long) 1000 * 60;
                break;
            case "seconds":
                timeUnitMultiplier = (long) 1000;
                break;
            default:
                System.out.println("no correct timeUnit given");
                throw new IllegalArgumentException(timeUnit + " is not a valid timeunit");
        }
    }

    public long getStartTimeLong() throws ParseException {
        //RETURN ERROR IF NOT INITIALIZED
        if(!isInitialized){
            setStartTime();
        }
        return timeUnitMultiplier;
    }

    public void setStartTime() throws ParseException {
        //TODO CATCH THE EXCEPTION OF THROWS
        String modifiedStartDateTimeString = startDateTime.replace("T", " ");
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        Date date = formatter.parse(modifiedStartDateTimeString);
        this.startTimeLong = date.getTime();
    }

    public Boolean getTimeHasIncreased(){
        //RETURN ERROR IF NOT INITIALIZED
        return timeHasIncreased;
    }

    public void setTimeHasIncreased(Double timeLastUpdatedEvent){
        if(timeLastUpdatedEvent == 0.0){
            this.timeHasIncreased = false;
        } else {
            this.timeHasIncreased = true;
        }
    }
}
