package org.matsim.analysis;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.*;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.vehicles.Vehicle;
import java.util.*;

public class GetLinksFromAgentId1Handler implements LinkEnterEventHandler {
        private final List<Id<Link>> personLinks = new ArrayList<>();
        public List<Id<Link>> getPersonLinks() { return personLinks; }
    @Override
    public void handleEvent(LinkEnterEvent e) {
            Id<Vehicle> vehicleId = Id.createVehicleId(1);
            if(e.getVehicleId().equals(vehicleId)){
                personLinks.add(e.getLinkId());
            }
    }
}
