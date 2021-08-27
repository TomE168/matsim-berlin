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

import java.util.*;

public class NetworkModifier_A100 {

    public static void main(String[] args) {
        String inputNetworkFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.5-10pct/input/berlin-v5.5-network.xml.gz";
        String outputNetworkFile = "C:\\Users\\tekuh\\OneDrive\\Master\\Matsim\\matsim-berlin-homework2\\scenarios\\berlin-1person\\input\\berlin-network-without-a100.xml.gz";
        List<String> a100Links = new ArrayList<>(Arrays.asList("42792","42800","53981","33388","160566","160565","141743","78979","141715","21750","55214","5770","35322","35317","78997","147562","78982","60623","60638","47278","47275","82150","94290","94289","147559","147563","146985","146982","77422","134577","41432","24140","149457","149454","77475","39886","149474","50024","50068","60890","14180","40244","138536","138537","109396","109395","77827","120966","120963","47276","47286","130663","130668","52955","56841","56838","52958","89819","98320","98319","92662","3202","24141","140219","140222","117508","117506","51588","51587","146740","84487","146735","85098","118242","47283","27286","27288","47299","113016","118241","27285","27284","40199","150778","150781","85103","93509","93512","93510","85114","85982","93511","152320","93679","152294","34909","85133","34899","85981","15795","15796","150777","150774","113022","137667","40200","151779","118239","137673","137685","47277","47287","85087","118238","146741","84480","146739","84446","117510","117507","15764","144032","137690","137692","137653","98321","98324","1383","77825","137664","137691","52950","130675","130671","52949","82578","79230","120965","137674","109389","109394","82587","137679","14208","137658","137660","83600","122421","137695","137677","137659","149472","137678","77478","137683","137686","149434","149435","137663","41443","15765","41434","26588","77430","146988","74561","137211","41418","63902","77419","137665","69865","18917","147290","18916","63901","14526","91958","47460","145000","144999","77498","125781","125798","55241","40380","33418","40377","101712","141729","141718","141746","121781","121787","5631","125775","121768","89215","85767","33476","33475","70976","70968","46085","89214","94726","80495"));


        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        MatsimNetworkReader networkReader = new MatsimNetworkReader(scenario.getNetwork());
        networkReader.readFile(inputNetworkFile);

        // Add bicycles to all link which can be traversed by cars
        for (Link link : scenario.getNetwork().getLinks().values()) {
            if (a100Links.contains(link.getId().toString()) && link.getAttributes().toString().contains("motorway") ){
                link.setFreespeed(0.1);
                link.setCapacity(1);
                link.setNumberOfLanes(1);
            }
        }

        NetworkWriter networkWriter = new NetworkWriter(scenario.getNetwork());
        networkWriter.write(outputNetworkFile);
    }
}
