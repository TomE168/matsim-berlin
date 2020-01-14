/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package org.matsim.run;

import java.util.List;
import java.util.Random;

import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.av.robotaxi.fares.drt.DrtFareConfigGroup;
import org.matsim.contrib.av.robotaxi.fares.drt.DrtFaresConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.utils.misc.Time;
import org.matsim.run.BerlinExperimentalConfigGroup.IntermodalAccessEgressModeUtilityRandomization;

import com.google.inject.Inject;

import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.RaptorParameters;

/**
 * A default implementation of {@link RaptorIntermodalAccessEgress} returning a new RIntermodalAccessEgress,
 * which contains a list of legs (same as in the input), the associated travel time as well as the disutility.
 *
 * @author pmanser / SBB
 */
public class BerlinRaptorIntermodalAccessEgress implements RaptorIntermodalAccessEgress {
	
	Config config;	
	BerlinExperimentalConfigGroup berlinCfg;
	DrtFaresConfigGroup drtFaresConfigGroup;
	
	Random random = MatsimRandom.getLocalInstance();
	
	@Inject
    BerlinRaptorIntermodalAccessEgress(Config config) {
		this.config = config;
		this.berlinCfg = ConfigUtils.addOrGetModule(config, BerlinExperimentalConfigGroup.class);
		this.drtFaresConfigGroup = ConfigUtils.addOrGetModule(config, DrtFaresConfigGroup.class);
	}

	@Override
    public RIntermodalAccessEgress calcIntermodalAccessEgress(final List<? extends PlanElement> legs, RaptorParameters params, Person person) {
        double utility = 0.0;
        double tTime = 0.0;
        for (PlanElement pe : legs) {
            if (pe instanceof Leg) {
                String mode = ((Leg) pe).getMode();
                double travelTime = ((Leg) pe).getTravelTime();
                
                // overrides individual parameters per person; use default scoring parameters
                if (Time.getUndefinedTime() != travelTime) {
                    tTime += travelTime;
                    utility += travelTime * (config.planCalcScore().getModes().get(mode).getMarginalUtilityOfTraveling() + (-1) * config.planCalcScore().getPerforming_utils_hr()) / 3600;
                }
                Double distance = ((Leg) pe).getRoute().getDistance();
                if (distance != null && distance != 0.) {
                	utility += distance * config.planCalcScore().getModes().get(mode).getMarginalUtilityOfDistance();
                	utility += distance * config.planCalcScore().getModes().get(mode).getMonetaryDistanceRate() * config.planCalcScore().getMarginalUtilityOfMoney();
                }
                utility += config.planCalcScore().getModes().get(mode).getConstant();
                
                // account for drt fares
                for (DrtFareConfigGroup drtFareConfigGroup : drtFaresConfigGroup.getDrtFareConfigGroups()) {
                	if (drtFareConfigGroup.getMode().equals(mode)) {
                        double fare = 0.;
                		if (distance != null && distance != 0.) {
                        	fare += drtFareConfigGroup.getDistanceFare_m() * distance;
                        }
                                                
                        if (Time.getUndefinedTime() != travelTime) {
                            fare += drtFareConfigGroup.getTimeFare_h() * travelTime / 3600.;

                        }
                        
                        fare += drtFareConfigGroup.getBasefare(); 
                        fare = Math.max(fare, drtFareConfigGroup.getMinFarePerTrip());
                        utility += -1. * fare * config.planCalcScore().getMarginalUtilityOfMoney();
                	}
                }
                
                // apply randomization to utility if applicable;
                IntermodalAccessEgressModeUtilityRandomization randomization = berlinCfg.getIntermodalAccessEgressModeUtilityRandomization(mode);
                if (randomization != null) {
                	double utilityRandomizationSigma = berlinCfg.getIntermodalAccessEgressModeUtilityRandomization(mode).getAdditiveRandomizationWidth();
                	utility += (random.nextDouble() - 0.5) * utilityRandomizationSigma;
                }
            }
        }
        return new RIntermodalAccessEgress(legs, -utility, tTime);
    }
}
