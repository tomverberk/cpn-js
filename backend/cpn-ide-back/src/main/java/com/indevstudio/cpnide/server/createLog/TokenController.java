package com.indevstudio.cpnide.server.createLog;

import com.indevstudio.cpnide.server.model.PlaceMark;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TokenController {

    private static TokenController single_instance = null;
    private List<PlaceMark> lastMarking = null;
    private Map<String, List<TimedToken>> placeMapping = new HashMap<>();
    private List<String> idOfPlacesChangedOnLastStep = new ArrayList<>();
    private Double lastTime= 0.0;

    private TokenController(){

    }

    /**
     * Singleton class
     * @return the tokenController if it exists otherwise creates a new one
     */
    public static TokenController getInstance() {
        if(single_instance == null){
            single_instance = new TokenController();
        }
        return single_instance;
    }

    public TokenController getNewController(){
        return this;
    }

    public void destroy(){
        single_instance = null;
    }

    /**
     * Update the marking currently saved in the tokenController.
     * @param newMarking the new marking
     * @throws Exception
     */
    public void updateMarking(List<PlaceMark> newMarking) throws Exception{
        idOfPlacesChangedOnLastStep.clear();
        if(lastMarking == null){
            SetMarking(newMarking);
        } else {
            SetDifference(newMarking);
        }
        this.lastMarking = newMarking;
        setSmallestTimeTokenLastChanged();
    }

    /**
     * Set a marking
     * @param marking
     * @throws Exception
     */
    public void SetMarking(List<PlaceMark> marking) throws Exception{
        SetPlaceInfos(marking);
    }

    /**
     * Set the difference between the new marking and the old marking
     * @param newMarking
     * @throws Exception
     */
    public void SetDifference(List<PlaceMark> newMarking) throws Exception{
        List<PlaceMark> result = new ArrayList<>();
        List<PlaceMark> copyLastMarking = lastMarking.stream().collect(Collectors.toList());
        List<PlaceMark> copyNewMarking = newMarking.stream().collect(Collectors.toList());
        copyLastMarking.removeAll(newMarking);
        copyNewMarking.removeAll(lastMarking);
        SetPlaceInfos(copyNewMarking, copyLastMarking);
    }

    /**
     * Set the info for each place given the new marking
     */
    public void SetPlaceInfos(List<PlaceMark> newMarking) throws Exception{
        for(int i = 0; i< newMarking.size(); i++){
            SetPlaceInfo(newMarking.get(i), "");
        }
    }

    /**
     * Set the info of the place given the new marking and the old marking
     * @param newMarking
     * @param oldMarking
     * @throws Exception
     */
    public void SetPlaceInfos(List<PlaceMark> newMarking, List<PlaceMark> oldMarking) throws Exception{
        for(int i = 0; i< newMarking.size(); i++){
            SetPlaceInfo(newMarking.get(i), oldMarking.get(i).getMarking());
        }
    }

    /**
     * Function that sets the time of the smallest that is present in the current marking, but was not present in the last marking. This is thus the lowest time of the any of the newly created tokens
     */
    public void setSmallestTimeTokenLastChanged(){
        Double lowestTime = Double.MAX_VALUE;
        for(String id: idOfPlacesChangedOnLastStep){
            List<TimedToken> timedTokens = placeMapping.get(id);
            for(TimedToken timedToken: timedTokens){
                if(timedToken.time != null) {
                    lowestTime = Double.min(lowestTime, Double.parseDouble(timedToken.time));
                }
            }
        }
        if(lowestTime != Double.MAX_VALUE){
            lastTime = lowestTime;
        }
    }

    public Double getLastTime(){
        return this.lastTime;
    }

    /**
     * Set the info of a place given the old marking for the place and the new marking for the place
     * @param placeMarkNew
     * @param markingOld
     * @throws Exception
     */
    public void SetPlaceInfo(PlaceMark placeMarkNew, String markingOld) throws Exception{
        if(placeMarkNew.getMarking().equals("empty")){
            placeMapping.put(placeMarkNew.getId(), null);
            return;
        }
        List<TimedToken> newTimedTokens = getNewTimedTokens(placeMarkNew.getMarking(), markingOld);
        idOfPlacesChangedOnLastStep.add(placeMarkNew.getId());
        placeMapping.put(placeMarkNew.getId(), newTimedTokens);
    }

    /**
     * Gets all the token that are new in the marking of a place, thus that were not present in the old marking of that place.
     * Seperates the entire marking of a place on the "++" sign
     * @param markingNew
     * @param markingOld
     * @return A list of tokens present in the new marking and not present in the old marking
     * @throws Exception
     */
    public List<TimedToken> getNewTimedTokens(String markingNew, String markingOld) throws Exception{
        List<TimedToken> timedTokens = new ArrayList<TimedToken>();
        while(markingNew.contains("++")){
            Integer indexOfPlusPlus = markingNew.indexOf("++");
            String newTokenString = markingNew.substring(0, indexOfPlusPlus);
            if(!markingOld.contains(newTokenString)) {
                TimedToken token = createSingleTokenFromSingleMarking(newTokenString);
                timedTokens.add(token);
            }
            Integer indexOfN = markingNew.indexOf("\n");
            markingNew = markingNew.substring(indexOfN + 1);


        }
        if(!markingOld.contains(markingNew)) {
            TimedToken token = createSingleTokenFromSingleMarking(markingNew);
            timedTokens.add(token);
        }

        return timedTokens;
    }


    /**
     * Gets the token from part of a place
     * @param marking
     * @return
     * @throws Exception
     */
    public TimedToken createSingleTokenFromSingleMarking(String marking) throws Exception{
        List<TimedToken> timedTokens = new ArrayList<TimedToken>();
        Integer indexOfApostrofe = marking.indexOf("`");
        Integer amount = Integer.parseInt(marking.substring(0, indexOfApostrofe));
        if (amount != 1){
            //throw new IllegalArgumentException("Amount other than 1 not yet implemented");
        }
        List<String> attributes = new ArrayList<String>();
        TimedToken token;
        if(!marking.contains("@")){
            attributes.add(marking.substring(indexOfApostrofe+1));
            token = new TimedToken(attributes, null);
        } else {
            Integer indexOfAt = marking.indexOf("@");
            attributes.add(marking.substring(indexOfApostrofe+1, indexOfAt));
            token = new TimedToken(attributes, marking.substring(indexOfAt+1));
        }

        return token;

    }


}
