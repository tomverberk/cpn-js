package com.indevstudio.cpnide.server.net;

import com.indevstudio.cpnide.server.createLog.CreateLogContainer;
import com.indevstudio.cpnide.server.model.PlaceMark;
import org.cpntools.accesscpn.engine.highlevel.instance.Marking;
import org.cpntools.accesscpn.model.PetriNet;
import org.cpntools.accesscpn.model.Place;

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

    public void updateMarking(List<PlaceMark> newMarking) throws Exception{
        idOfPlacesChangedOnLastStep.clear();
        if(lastMarking == null){
            SetMarking(newMarking);
            this.lastMarking = newMarking;
            return;
        }
        SetDifference(newMarking);
        this.lastMarking = newMarking;
        setSmallestTimeTokenLastChanged();
    }

    public void SetMarking(List<PlaceMark> marking) throws Exception{
        SetPlaceInfos(marking);
    }

    public void SetDifference(List<PlaceMark> newMarking) throws Exception{
        List<PlaceMark> result = new ArrayList<>();
        List<PlaceMark> copyLastMarking = lastMarking.stream().collect(Collectors.toList());
        List<PlaceMark> copyNewMarking = newMarking.stream().collect(Collectors.toList());
        copyLastMarking.removeAll(newMarking);
        copyNewMarking.removeAll(lastMarking);
        SetPlaceInfos(copyNewMarking, copyLastMarking);
    }

    public void SetPlaceInfos(List<PlaceMark> newMarking) throws Exception{
        for(int i = 0; i< newMarking.size(); i++){
            SetPlaceInfo(newMarking.get(i), "");
        }
    }

    public void SetPlaceInfos(List<PlaceMark> newMarking, List<PlaceMark> oldMarking) throws Exception{
        for(int i = 0; i< newMarking.size(); i++){
            SetPlaceInfo(newMarking.get(i), oldMarking.get(i).getMarking());
        }
    }

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

    public void SetPlaceInfo(PlaceMark placeMarkNew, String markingOld) throws Exception{
        if(placeMarkNew.getMarking().equals("empty")){
            placeMapping.put(placeMarkNew.getId(), null);
            return;
        }
        List<TimedToken> newTimedTokens = getNewTimedTokens(placeMarkNew.getMarking(), markingOld);
        idOfPlacesChangedOnLastStep.add(placeMarkNew.getId());
        placeMapping.put(placeMarkNew.getId(), newTimedTokens);
    }

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

    //TODO MAKE THIS INTO AN ACTUAL LIST OF ELEMENTS

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
