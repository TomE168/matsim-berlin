package org.matsim.prepare;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.bicycle.BicycleUtils;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class NetworkModifier_A100 {

    public static void main(String[] args) {
        String inputNetworkFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.5-10pct/input/berlin-v5.5-network.xml.gz";
        String outputNetworkFile = "C:\\Users\\tekuh\\OneDrive\\Desktop\\berlin-network-without-a100-TEST.xml.gz";
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
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        MatsimNetworkReader networkReader = new MatsimNetworkReader(scenario.getNetwork());
        networkReader.readFile(inputNetworkFile);
        for (Link link : scenario.getNetwork().getLinks().values()) {
            if (a100Links.contains(link.getId().toString())){
                link.setFreespeed(0.1);
                link.setCapacity(1);
                link.setNumberOfLanes(1);
            }
        }
        NetworkWriter networkWriter = new NetworkWriter(scenario.getNetwork());
        networkWriter.write(outputNetworkFile);
    }
}
