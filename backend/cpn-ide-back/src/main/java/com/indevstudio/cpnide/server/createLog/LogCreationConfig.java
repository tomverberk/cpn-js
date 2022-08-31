package com.indevstudio.cpnide.server.createLog;

import lombok.Data;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

@Data
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
    private int calendarValue;

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

    public int getCalendarValue(){
        return calendarValue;
    }

    public void setTimeUnitMultiplier(){
        switch(timeUnit){
            case "years":
                timeUnitMultiplier = (long) 1000 * 60 * 60 * 24 * 365;
                calendarValue = Calendar.YEAR;
                break;
            case "months":
                timeUnitMultiplier = (long) 1000 * 60 * 60 * 24 * 31;
                calendarValue = Calendar.MONTH;
                break;
            case "weeks":
                timeUnitMultiplier = (long) 1000 * 60 * 60 * 24 * 7;
                calendarValue = Calendar.WEEK_OF_YEAR;
                break;
            case "days":
                timeUnitMultiplier = (long) 1000 * 60 * 60 * 24;
                calendarValue = Calendar.DAY_OF_YEAR;
                break;
            case "hours":
                timeUnitMultiplier = (long) 1000 * 60 * 60;
                calendarValue = Calendar.HOUR_OF_DAY;
                break;
            case "minutes":
                timeUnitMultiplier = (long) 1000 * 60;
                calendarValue = Calendar.MINUTE;
                break;
            case "seconds":
                timeUnitMultiplier = (long) 1000;
                calendarValue = Calendar.SECOND;
                break;
            default:
                System.out.println("no correct timeUnit given");
                throw new IllegalArgumentException(timeUnit + " is not a valid timeunit");
        }
    }

    public long getStartTimeLong(){
        //RETURN ERROR IF NOT INITIALIZED
        if(!isInitialized){
            setStartTime();
        }
        return startTimeLong;
    }

    public void setStartTime(){
        //TODO CATCH THE EXCEPTION OF THROWS
        String modifiedStartDateTimeString = startDateTime.replace("T", " ");
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        Date date = null;
        try {
            date = formatter.parse(modifiedStartDateTimeString);
        } catch (ParseException e) {
            e.printStackTrace();
        }
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
