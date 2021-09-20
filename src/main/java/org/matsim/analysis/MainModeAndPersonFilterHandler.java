package org.matsim.analysis;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.*;
import org.matsim.api.core.v01.events.handler.*;
import org.matsim.api.core.v01.population.Person;
import org.matsim.vehicles.Vehicle;
import java.util.*;

public class MainModeAndPersonFilterHandler implements TransitDriverStartsEventHandler, PersonDepartureEventHandler, ActivityEndEventHandler, LinkEnterEventHandler, PersonEntersVehicleEventHandler {

    private static final List<String> modes = List.of(TransportMode.walk, TransportMode.bike, TransportMode.ride, TransportMode.car, TransportMode.pt, TransportMode.airplane);
    private final Set<Id<Person>> transitDrivers = new HashSet<>();
    private final Map<Id<Person>, List<String>> personTrips = new HashMap<>();
    List<Id<Vehicle>> vehiclesOnA100 = new ArrayList<>();
    Map<Id<Person>, Id<Vehicle>> vehicleToPersonMap = new HashMap<>();
    List<Id<Person>> personIds = new ArrayList<>();
    List<String> a100Links = new ArrayList<>();
    public MainModeAndPersonFilterHandler(List<String> links) {
        a100Links = links;
    }

    public void filterPersonIdsAndPersonTrips() {
        Map<Id<Person>, List<String>> personTripsFiltered = new HashMap<>();
        for (Map.Entry<Id<Person>, List<String>> person : personTrips.entrySet()){
            if(vehiclesOnA100.contains(vehicleToPersonMap.get(person.getKey()))) {
                personTripsFiltered.put(person.getKey(), person.getValue());
                personIds.add(person.getKey());
            }
        }
    }
    public List<Id<Person>> getPersonIds(){
        return personIds;
    }
    public Map<Id<Person>, List<String>> getPersonTrips(){
        return personTrips;
    }


    @Override
    public void handleEvent(LinkEnterEvent e){
        if(a100Links.contains(e.getLinkId().toString())){
            vehiclesOnA100.add(e.getVehicleId());
        }

    }
    @Override
    public void handleEvent(PersonEntersVehicleEvent e){
        vehicleToPersonMap.put(e.getPersonId(), e.getVehicleId());
    }

    @Override
    public void handleEvent(ActivityEndEvent e) {

        if (transitDrivers.contains(e.getPersonId()) || isInteraction(e.getActType())) return;

        personTrips.computeIfAbsent(e.getPersonId(), id -> new ArrayList<>()).add("");
    }

    @Override
    public void handleEvent(PersonDepartureEvent e) {

        if (transitDrivers.contains(e.getPersonId())) return;

        var trips = personTrips.get(e.getPersonId());

        var mainMode = getMainMode(getLast(trips), e.getLegMode());
        setLast(trips, mainMode);
    }

    @Override
    public void handleEvent(TransitDriverStartsEvent transitDriverStartsEvent) {

        transitDrivers.add(transitDriverStartsEvent.getDriverId());
    }

    private boolean isInteraction(String type) {
        return type.endsWith(" interaction");
    }

    private String getMainMode(String current, String newMode) {

        var currentIndex = modes.indexOf(current);
        var newIndex = modes.indexOf(newMode);

        return currentIndex > newIndex ? current : newMode;
    }

    private String getLast(List<String> from) {
        return from.get(from.size() - 1);
    }

    private void setLast(List<String> to, String value) {
        to.set(to.size() - 1, value);
    }

}

