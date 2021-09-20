package org.matsim.analysis;

import org.apache.commons.csv.CSVFormat;
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

public class RunMainModeAndPersonFilterHandler {

    public static void main(String[] args) {
        List<String> a100Links = new ArrayList<>();
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader("C:\\Users\\tekuh\\OneDrive\\Desktop\\A100-links.csv"))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String[] values = line.split(",");
                for (String value:values){
                    a100Links.add(value);
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        var managerBaseCase = EventsUtils.createEventsManager();
        var handlerBaseCase = new MainModeAndPersonFilterHandler(a100Links);
        managerBaseCase.addHandler(handlerBaseCase);
        EventsUtils.readEvents(managerBaseCase, "C:\\Users\\tekuh\\OneDrive\\Master\\Matsim\\matsim-berlin-homework2\\scenarios\\berlin-v5.5-1pct\\output-berlin-v5.5-1pct\\berlin-v5.5.3-1pct.output_events.xml.gz");
        handlerBaseCase.filterPersonIdsAndPersonTrips();
        var personTripsBaseCase = handlerBaseCase.getPersonTrips();
        var personIds = handlerBaseCase.getPersonIds();


        try (var writer = Files.newBufferedWriter(Paths.get("C:\\Users\\tekuh\\OneDrive\\Desktop\\PersonIds.csv")); var printer = CSVFormat.DEFAULT.withDelimiter(',').print(writer)) {
            printer.printRecord(personIds);
        } catch (IOException e) {
            e.printStackTrace();
        }
        var modesBaseCase = personTripsBaseCase.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(mode -> mode, mode -> 1, Integer::sum));

        var totalTripsBaseCase = modesBaseCase.values().stream()
                .mapToDouble(d -> d)
                .sum();

        try (var writer = Files.newBufferedWriter(Paths.get("C:\\Users\\tekuh\\OneDrive\\Desktop\\modesBaseCase.csv")); var printer = CSVFormat.DEFAULT.withDelimiter(',').withHeader("Mode", "Count", "Share").print(writer)) {

            for (var entry : modesBaseCase.entrySet()) {
                printer.printRecord(entry.getKey(), entry.getValue(), entry.getValue() / totalTripsBaseCase);
            }

            printer.printRecord("total", totalTripsBaseCase, 1.0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
