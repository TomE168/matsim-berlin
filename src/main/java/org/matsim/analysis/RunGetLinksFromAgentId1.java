package org.matsim.analysis;

import org.apache.commons.csv.CSVFormat;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class RunGetLinksFromAgentId1 {
    public static void main(String[] args){
        var manager = EventsUtils.createEventsManager();
        var handler = new GetLinksFromAgentId1Handler();
        manager.addHandler(handler);
        EventsUtils.readEvents(manager, "C:\\Users\\tekuh\\OneDrive\\Master\\Matsim\\matsim-berlin-homework2\\scenarios\\berlin-1person\\output-berlin-1person\\ITERS\\it.0\\berlin-v5.5-1pct.0.events.xml.gz");
        var personLinks = handler.getPersonLinks();
        List<Id<Link>> linksOnlyMotorway = new ArrayList<>();

        String inputNetworkFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.5-10pct/input/berlin-v5.5-network.xml.gz";
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        MatsimNetworkReader networkReader = new MatsimNetworkReader(scenario.getNetwork());
        networkReader.readFile(inputNetworkFile);

        for (Id<Link> personLink : personLinks) {
            for (Link networkLink : scenario.getNetwork().getLinks().values()) {
                if (personLink.equals(networkLink.getId()) && networkLink.getAttributes().toString().contains("motorway")) {
                    linksOnlyMotorway.add(networkLink.getId());
                }
            }
        }

        try (var writer = Files.newBufferedWriter(Paths.get("C:\\Users\\tekuh\\OneDrive\\Desktop\\A100-links.csv"));
             var printer = CSVFormat.DEFAULT.withDelimiter(',').print(writer)) {
                printer.printRecord(linksOnlyMotorway);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
