package org.matsim.analysis;

import org.apache.commons.csv.CSVFormat;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.events.EventsUtils;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class RunMainModeHandlerWithFilter {
    public static void main(String[] args) {
        List<Id<Person>> personIds = new ArrayList<>();
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader("C:\\Users\\tekuh\\OneDrive\\Desktop\\PersonIds.csv"))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String[] values = line.split(",");
                for (String value:values){
                    Id<Person> idValue = Id.createPersonId(value);
                    personIds.add(idValue);
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        var manager = EventsUtils.createEventsManager();
        var handler = new MainModeHandlerWithFilter(personIds);
        manager.addHandler(handler);
        EventsUtils.readEvents(manager, "C:\\Users\\tekuh\\OneDrive\\Master\\Matsim\\matsim-berlin-homework2\\scenarios\\berlin-v5.5-1pct-without_a100\\output-berlin-v5.5-1pct\\berlin-v5.5-1pct.output_events.xml.gz");

        var personTrips = handler.getPersonTrips();
        var modes = personTrips.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(mode -> mode, mode -> 1, Integer::sum));

        var totalTrips = modes.values().stream()
                .mapToDouble(d -> d)
                .sum();

        try (var writer = Files.newBufferedWriter(Paths.get("C:\\Users\\tekuh\\OneDrive\\Desktop\\modesPolicyCase.csv")); var printer = CSVFormat.DEFAULT.withDelimiter(',').withHeader("Mode", "Count", "Share").print(writer)) {

            for (var entry : modes.entrySet()) {
                printer.printRecord(entry.getKey(), entry.getValue(), entry.getValue() / totalTrips);
            }

            printer.printRecord("total", totalTrips, 1.0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
