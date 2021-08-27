package org.matsim.analysis;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.*;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.events.handler.EventHandler;
import org.matsim.vehicles.Vehicle;

import java.util.*;

public class AnalysePersonID1Handler implements LinkEnterEventHandler {
        private static final List<String> modes = List.of(TransportMode.walk, TransportMode.bike, TransportMode.ride, TransportMode.car, TransportMode.pt, TransportMode.airplane);
        private final Set<Id<Person>> transitDrivers = new HashSet<>();
        private final List<Id> personLinks = new ArrayList<>();

        public List<Id> getPersonLinks() {
            return personLinks;
        }
/*    @Override
    public void handleEvent(ActivityEndEvent e) {
            if (transitDrivers.contains(e.getPersonId()) || isInteraction(e.getActType())) return;
            personLinks.add(e.getLinkId());
    }*/
    @Override
    public void handleEvent(LinkEnterEvent e) {
            Id<Vehicle> vehicleId = Id.createVehicleId(1);
            if(e.getVehicleId().equals(vehicleId)){
                personLinks.add(e.getLinkId());
            }

    }

/*    @Override
    public void handleEvent(PersonDepartureEvent e) {
            if (transitDrivers.contains(e.getPersonId())) return;
        personLinks.add(e.getLinkId());
        }

    @Override
    public void handleEvent(TransitDriverStartsEvent transitDriverStartsEvent) {
        transitDrivers.add(transitDriverStartsEvent.getDriverId());
    }
*/

        private boolean isInteraction(String type) {
            return type.endsWith(" interaction");
        }


}
