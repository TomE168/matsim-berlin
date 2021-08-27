package org.matsim.analysis;

import org.apache.commons.csv.CSVFormat;
import org.matsim.core.events.EventsUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.stream.Collectors;

public class RunAnalysePersonID1 {

    public static void main(String[] args){
        var manager = EventsUtils.createEventsManager();
        var handler = new AnalysePersonID1Handler();
        manager.addHandler(handler);
        EventsUtils.readEvents(manager, "C:\\Users\\tekuh\\OneDrive\\Master\\Matsim\\matsim-berlin-homework2\\scenarios\\berlin-1person\\output-berlin-1person\\ITERS\\it.0\\berlin-v5.5-1pct.0.events.xml.gz");

        var personLinks = handler.getPersonLinks();

        try (var writer = Files.newBufferedWriter(Paths.get("C:\\Users\\tekuh\\OneDrive\\Desktop\\A100-links.csv")); var printer = CSVFormat.DEFAULT.withDelimiter(',').withHeader("Links").print(writer)) {

                printer.printRecord(personLinks);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
