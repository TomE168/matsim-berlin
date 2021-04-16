/* *********************************************************************** *
 * project: org.matsim.*
 * Controler.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.urbanEV;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import one.util.streamex.StreamEx;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.ev.charging.ChargingLogic;
import org.matsim.contrib.ev.charging.ChargingPower;
import org.matsim.contrib.ev.discharging.AuxEnergyConsumption;
import org.matsim.contrib.ev.discharging.DriveEnergyConsumption;
import org.matsim.contrib.ev.fleet.ElectricFleetSpecification;
import org.matsim.contrib.ev.fleet.ElectricVehicle;
import org.matsim.contrib.ev.fleet.ElectricVehicleImpl;
import org.matsim.contrib.ev.fleet.ElectricVehicleSpecification;
import org.matsim.contrib.ev.infrastructure.*;
import org.matsim.contrib.util.StraightLineKnnFinder;
import org.matsim.core.config.Config;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.framework.events.MobsimInitializedEvent;
import org.matsim.core.mobsim.framework.listeners.MobsimInitializedListener;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.mobsim.qsim.agents.WithinDayAgentUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.*;
import org.matsim.core.router.util.TravelTime;
import org.matsim.facilities.FacilitiesUtils;
import org.matsim.facilities.Facility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;
import org.matsim.withinday.utils.EditPlans;

import javax.inject.Provider;
import java.util.*;

import static java.util.stream.Collectors.*;
import static org.matsim.urbanEV.MATSimVehicleWrappingEVSpecificationProvider.getWrappedElectricVehicleId;

class UrbanEVTripsPlanner implements MobsimInitializedListener {

	@Inject
	private Provider<TripRouter> tripRouterProvider;

	@Inject
	Scenario scenario;

	@Inject
	Vehicles vehicles;

	@Inject
	private SingleModeNetworksCache singleModeNetworksCache;

	@Inject
	private ElectricFleetSpecification electricFleetSpecification;

	@Inject
	private ChargingInfrastructureSpecification chargingInfrastructureSpecification;

	@Inject
	private DriveEnergyConsumption.Factory driveConsumptionFactory;

	@Inject
	private AuxEnergyConsumption.Factory auxConsumptionFactory;

	@Inject
	private ChargingPower.Factory chargingPowerFactory;

	@Inject
	private ChargingLogic.Factory chargingLogicFactory;

	@Inject
	private Map<String, TravelTime> travelTimes;

	@Inject
	ActivityWhileChargingFinder activityWhileChargingFinder;

	@Inject
	Config config;

	private QSim qsim;

	private static final Logger log = Logger.getLogger(UrbanEVTripsPlanner.class);

	@Override
	public void notifyMobsimInitialized(MobsimInitializedEvent e) {
 		if(! (e.getQueueSimulation() instanceof QSim)){
			throw new IllegalStateException(UrbanEVTripsPlanner.class.toString() + " only works with a mobsim of type " + QSim.class);
		}
 		//collect all selected plans that contain ev legs and map them to the set of ev used
		Map<Plan, Set<Id<Vehicle>>> selectedEVPlans = StreamEx.of(scenario.getPopulation().getPersons().values())
				.mapToEntry(p -> p.getSelectedPlan(), p -> getUsedEV(p.getSelectedPlan()))
				.filterValues(evSet -> !evSet.isEmpty())
				.collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

		this.qsim = (QSim) e.getQueueSimulation();
		processPlans(selectedEVPlans);
	}

	/**
	 * retrieve all used EV in the given plan
	 * @param plan
	 * @return
	 */
	private Set<Id<Vehicle>> getUsedEV(Plan plan){
		return TripStructureUtils.getLegs(plan).stream()
				.map(leg -> VehicleUtils.getVehicleId(plan.getPerson(), leg.getMode()))
				.filter(vehicleId -> isEV(vehicleId))
				.collect(toSet());
	}

	private boolean isEV(Id<Vehicle> vehicleId) {
		return this.electricFleetSpecification.getVehicleSpecifications().containsKey(getWrappedElectricVehicleId(vehicleId));
	}

	private void processPlans(Map<Plan, Set<Id<Vehicle>>> selectedEVPlans) {

		UrbanEVConfigGroup configGroup = (UrbanEVConfigGroup) config.getModules().get(UrbanEVConfigGroup.GROUP_NAME);

		for (Plan plan : selectedEVPlans.keySet()) {

			//from here we deal with the modifiable plan (only!?)
			MobsimAgent mobsimagent = qsim.getAgents().get(plan.getPerson().getId());
			Plan modifiablePlan = WithinDayAgentUtils.getModifiablePlan(mobsimagent);

			for(Id<Vehicle> ev : selectedEVPlans.get(plan)){
				//only replan cnt times per vehicle and person. otherwise, there might be a leg which is just too long and we end up in an infinity loop...
				int cnt = configGroup.getMaximumChargingProceduresPerAgent();

				/*
				 * i had all of this implemented without so many if-statements and without do-while-loop. However, i felt like when replanning takes place, we need to start
				 * consumption estimation all over. The path to avoid this would be by complicated date/method structure, which would also be bad (especially to maintain...)
				 * ts, nov' 27, 2020
				  */

				ElectricVehicleSpecification electricVehicleSpecification = electricFleetSpecification.getVehicleSpecifications()
						.get(getWrappedElectricVehicleId(ev));
				Leg legWithCriticalSOC;
				do{
					 legWithCriticalSOC = getLegWithCriticalSOC(modifiablePlan, ev, electricVehicleSpecification);
					 if(legWithCriticalSOC != null){
					 	replanPrecedentAndCurrentEVLegs(mobsimagent, modifiablePlan, electricVehicleSpecification, legWithCriticalSOC);
						cnt --;
					 }
				} while (legWithCriticalSOC != null && cnt > 0);
			}

		}
	}

	/**
	 * retruns leg for which the crtitical soc is exceeded or null energy is estimated to be sufficient
	 *
	 * @param modifiablePlan
	 * @param ev
	 * @param electricVehicleSpecification
	 * @return
	 */
	private Leg getLegWithCriticalSOC(Plan modifiablePlan, Id<Vehicle> ev, ElectricVehicleSpecification electricVehicleSpecification) {
		UrbanEVConfigGroup configGroup = (UrbanEVConfigGroup) config.getModules().get(UrbanEVConfigGroup.GROUP_NAME);

		ElectricVehicle pseudoVehicle = ElectricVehicleImpl.create(electricVehicleSpecification, driveConsumptionFactory, auxConsumptionFactory, chargingPowerFactory);
		double capacityThreshold = electricVehicleSpecification.getBatteryCapacity() * (configGroup.getCriticalRelativeSOC()); //TODO randomize? Might also depend on the battery size!

		Double chargingBegin = null;

		Set<String> modesWithVehicles = new HashSet<>(scenario.getConfig().qsim().getMainModes());
		modesWithVehicles.addAll(scenario.getConfig().plansCalcRoute().getNetworkModes());

		for (PlanElement planElement : modifiablePlan.getPlanElements()) {
			if(planElement instanceof Leg){

				Leg leg = (Leg) planElement;
				if( modesWithVehicles.contains(leg.getMode()) && VehicleUtils.getVehicleId(modifiablePlan.getPerson(), leg.getMode()).equals(ev)){
					emulateVehicleDischarging(pseudoVehicle,leg);
					if (pseudoVehicle.getBattery().getSoc() <= capacityThreshold) {
						return leg;
					}
				}
			} else if (planElement instanceof Activity){
				if(((Activity) planElement).getType().contains(UrbanVehicleChargingHandler.PLUGIN_INTERACTION)){
					Leg legToCharger = (Leg) modifiablePlan.getPlanElements().get(modifiablePlan.getPlanElements().indexOf(planElement) - 1);
					chargingBegin =  legToCharger.getDepartureTime().seconds() + legToCharger.getTravelTime().seconds();

				} else if(((Activity) planElement).getType().contains(UrbanVehicleChargingHandler.PLUGOUT_INTERACTION)){

					Leg legFromCharger = (Leg) modifiablePlan.getPlanElements().get(modifiablePlan.getPlanElements().indexOf(planElement) + 1);
					if(chargingBegin == null) throw new IllegalStateException();
					double chargingDuration = legFromCharger.getDepartureTime().seconds() - chargingBegin;

					ChargerSpecification chargerSpecification = chargingInfrastructureSpecification.getChargerSpecifications()
							.values()
							.stream()
							.filter(charger -> charger.getLinkId().equals(((Activity) planElement).getLinkId()))
							.filter(charger -> electricVehicleSpecification.getChargerTypes().contains(charger.getChargerType()))
							.findAny().orElseThrow();

					pseudoVehicle.getBattery().changeSoc(pseudoVehicle.getChargingPower().calcChargingPower(chargerSpecification) * chargingDuration);
				}
			} else throw new IllegalArgumentException();
		}

		//no crtitcal soc estimated
		return null;
	}

	/**
	 *
	 *
	 * @param mobsimagent
	 * @param modifiablePlan
	 * @param electricVehicleSpecification
	 * @param leg
	 */
	private void replanPrecedentAndCurrentEVLegs(MobsimAgent mobsimagent, Plan modifiablePlan, ElectricVehicleSpecification electricVehicleSpecification, Leg leg) {
		Network modeNetwork = this.singleModeNetworksCache.getSingleModeNetworksCache().get(leg.getMode());

		String routingMode = TripStructureUtils.getRoutingMode(leg);
		int legIndex = modifiablePlan.getPlanElements().indexOf(leg);
		Preconditions.checkState(legIndex > -1, "could not locate leg in plan");

		//find suitable non-stage activity before SOC threshold passover
		Activity actWhileCharging = activityWhileChargingFinder.findActivityWhileChargingBeforeLeg(modifiablePlan, (Leg) modifiablePlan.getPlanElements().get(legIndex));

		Preconditions.checkNotNull(actWhileCharging, "could not insert plugin activity in plan of agent " + mobsimagent.getId() +
		".\n One reason could be that the agent has no suitable activity prior to the leg for which the " +
				" energy threshold is expected to be exceeded. \n" +
				" Another reason  might be that it's vehicle is running beyond energy threshold during the first leg of the day." +
		"That could possibly be avoided by using EVNetworkRoutingModule..."); //TODO let the sim just run and let the ev run empty!?

		//TODO what if actWhileCharging does not hold a link id?
		ChargerSpecification selectedCharger = selectChargerNearToLink(actWhileCharging.getLinkId(), electricVehicleSpecification, modeNetwork);
		Link chargingLink = modeNetwork.getLinks().get(selectedCharger.getLinkId());

		Activity pluginTripOrigin = EditPlans.findRealActBefore(mobsimagent, modifiablePlan.getPlanElements().indexOf(actWhileCharging));

//		critical leg should always be the plugout leg, shouldn't it? tschlenther jan' '21
//		Leg plugoutLeg = activityWhileChargingFinder.getNextLegOfRoutingModeAfterActivity(ImmutableList.copyOf(modifiablePlan.getPlanElements()), actWhileCharging, routingMode);
		Leg plugoutLeg = leg;
//		Activity plugoutTripOrigin = EditPlans.findRealActBefore(mobsimagent, modifiablePlan.getPlanElements().indexOf(plugoutLeg));
//		Activity plugoutTripDestination = EditPlans.findRealActAfter(mobsimagent, modifiablePlan.getPlanElements().indexOf(plugoutLeg));
		Activity plugoutTripOrigin = findRealOrChargingActBefore(mobsimagent, modifiablePlan.getPlanElements().indexOf(plugoutLeg));
		Activity plugoutTripDestination = findRealOrChargingActAfter(mobsimagent, modifiablePlan.getPlanElements().indexOf(plugoutLeg));

		{	//some consistency checks.. //TODO consider to put in a JUnit test..
			Preconditions.checkNotNull(pluginTripOrigin, "pluginTripOrigin is null. should never happen..");
			Preconditions.checkState(!pluginTripOrigin.equals(actWhileCharging), "pluginTripOrigin is equal to actWhileCharging. should never happen..");

			PlanElement legToBeReplaced = modifiablePlan.getPlanElements().get(modifiablePlan.getPlanElements().indexOf(pluginTripOrigin) + 1);
			Preconditions.checkState(legToBeReplaced instanceof Leg);
			Preconditions.checkState(TripStructureUtils.getRoutingMode((Leg) legToBeReplaced).equals(routingMode), "leg after pluginTripOrigin has the wrong routing mode. should not happen..");

			legToBeReplaced = modifiablePlan.getPlanElements().get(modifiablePlan.getPlanElements().indexOf(actWhileCharging) - 1);
			Preconditions.checkState(legToBeReplaced instanceof Leg);
			Preconditions.checkState(TripStructureUtils.getRoutingMode((Leg) legToBeReplaced).equals(routingMode), "leg before actWhileCharging has the wrong routing mode. should not happen..");

			Preconditions.checkState(!plugoutTripDestination.equals(actWhileCharging), "plugoutTripDestination is equal to actWhileCharging. should never happen..");

			Preconditions.checkState(modifiablePlan.getPlanElements().indexOf(pluginTripOrigin) < modifiablePlan.getPlanElements().indexOf(actWhileCharging));
			Preconditions.checkState(modifiablePlan.getPlanElements().indexOf(actWhileCharging) <= modifiablePlan.getPlanElements().indexOf(plugoutTripOrigin));
			Preconditions.checkState(modifiablePlan.getPlanElements().indexOf(plugoutTripOrigin) < modifiablePlan.getPlanElements().indexOf(plugoutTripDestination));

			legToBeReplaced = modifiablePlan.getPlanElements().get(modifiablePlan.getPlanElements().indexOf(plugoutTripOrigin) + 1);
			Preconditions.checkState(legToBeReplaced instanceof Leg);
			Preconditions.checkState(TripStructureUtils.getRoutingMode((Leg) legToBeReplaced).equals(routingMode), "leg after plugoutTripOrigin has the wrong routing mode. should not happen..");

			legToBeReplaced = modifiablePlan.getPlanElements().get(modifiablePlan.getPlanElements().indexOf(plugoutTripDestination) - 1);
			Preconditions.checkState(legToBeReplaced instanceof Leg);
			Preconditions.checkState(TripStructureUtils.getRoutingMode((Leg) legToBeReplaced).equals(routingMode), "leg before plugoutTripDestination has the wrong routing mode. should not happen..");
		}

		TripRouter tripRouter = tripRouterProvider.get();
		planPluginTrip(modifiablePlan, routingMode, pluginTripOrigin, actWhileCharging, chargingLink, tripRouter);
		planPlugoutTrip(modifiablePlan, routingMode, plugoutTripOrigin, plugoutTripDestination, chargingLink, tripRouter, PlanRouter.calcEndOfActivity(plugoutTripOrigin, modifiablePlan, config));
	}

	private void planPlugoutTrip(Plan plan, String routingMode, Activity origin, Activity destination, Link chargingLink, TripRouter tripRouter, double now) {
		Facility fromFacility = FacilitiesUtils.toFacility(origin, scenario.getActivityFacilities());
		Facility chargerFacility = new LinkWrapperFacility(chargingLink);
		Facility toFacility = FacilitiesUtils.toFacility(destination, scenario.getActivityFacilities());

		List<? extends PlanElement> routedSegment;
		//actually destination can not be null based on how we determine the actWhileCharging = origin at the moment...
		if(destination == null) throw new RuntimeException("should not happen");

		List<PlanElement> trip  = new ArrayList<>();

		//add leg to charger
		routedSegment = tripRouter.calcRoute(TransportMode.walk,fromFacility, chargerFacility,
				now, plan.getPerson());
		Leg accessLeg = (Leg)routedSegment.get(0);
		now = TripRouter.calcEndOfPlanElement(now, accessLeg, config);
		TripStructureUtils.setRoutingMode(accessLeg, routingMode);
		trip.add(accessLeg);

		//add plugout act
		Activity plugOutAct = PopulationUtils.createStageActivityFromCoordLinkIdAndModePrefix(chargingLink.getCoord(),
				chargingLink.getId(), routingMode + UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER);
		trip.add(plugOutAct);
		now = TripRouter.calcEndOfPlanElement(now, plugOutAct, config);

		//add leg to destination
		routedSegment = tripRouter.calcRoute(routingMode, chargerFacility, toFacility, now, plan.getPerson());
		Leg mainLeg = (Leg) routedSegment.get(0);
		trip.add(mainLeg);
		now = TripRouter.calcEndOfPlanElement(now, mainLeg, config);

		//insert trip
		TripRouter.insertTrip(plan, origin, trip, destination ) ;

		//reset activity end time
		destination.setEndTime(PopulationUtils.decideOnActivityEndTime(destination, now, config ).seconds());
	}

	private void planPluginTrip(Plan plan, String routingMode, Activity actBeforeCharging, Activity actWhileCharging, Link chargingLink, TripRouter tripRouter) {
		Facility fromFacility = FacilitiesUtils.toFacility(actBeforeCharging, scenario.getActivityFacilities());
		Facility chargerFacility = new LinkWrapperFacility(chargingLink);
		Facility toFacility = FacilitiesUtils.toFacility(actWhileCharging, scenario.getActivityFacilities());

		List<PlanElement> trip = new ArrayList<>();
		//add leg to charger
		List<? extends PlanElement> routedSegment = tripRouter.calcRoute(routingMode,fromFacility, chargerFacility,
				PlanRouter.calcEndOfActivity(actBeforeCharging, plan, config), plan.getPerson());
		Leg mainLeg = (Leg)routedSegment.get(0);
		double now = mainLeg.getDepartureTime().seconds() + mainLeg.getRoute().getTravelTime().seconds();
		trip.add(mainLeg);

		//add plugin act
		Activity pluginAct = PopulationUtils.createStageActivityFromCoordLinkIdAndModePrefix(chargingLink.getCoord(),
				chargingLink.getId(), routingMode + UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER);
		trip.add(pluginAct);
		now = TripRouter.calcEndOfPlanElement(now, pluginAct, config);

		//add walk leg to destination
		routedSegment = tripRouter.calcRoute(TransportMode.walk,chargerFacility, toFacility, now, plan.getPerson());
		Leg egress = (Leg) routedSegment.get(0);
		TripStructureUtils.setRoutingMode(egress, routingMode);
		trip.add(egress);
		now = TripRouter.calcEndOfPlanElement(now, egress, config);

		//insert trip
		TripRouter.insertTrip(plan, actBeforeCharging, trip, actWhileCharging ) ;

		//reset activity end time
		actWhileCharging.setEndTime(PopulationUtils.decideOnActivityEndTime(actWhileCharging, now, config ).seconds());
	}


	//TODO possibly put behind interface
	private ChargerSpecification selectChargerNearToLink(Id<Link> linkId, ElectricVehicleSpecification vehicleSpecification, Network network){
		StraightLineKnnFinder<Link, ChargerSpecification> straightLineKnnFinder = new StraightLineKnnFinder<>(
				1, l -> l.getFromNode().getCoord(), s -> network.getLinks().get(s.getLinkId()).getToNode().getCoord()); //TODO get closest X chargers and choose randomly?
		List<ChargerSpecification> nearestChargers = straightLineKnnFinder.findNearest(network.getLinks().get(linkId),
				chargingInfrastructureSpecification.getChargerSpecifications()
						.values()
						.stream()
						.filter(charger -> vehicleSpecification.getChargerTypes().contains(charger.getChargerType())));
		if (nearestChargers.isEmpty()){
			throw new RuntimeException("no charger could be found for vehicle type " + vehicleSpecification.getVehicleType());
		}
		return nearestChargers.get(0);
	}

	/**
	 * this method has the side effect that the soc of the ev is altered by estimated energy consumption of the leg
	 * @param ev
	 * @param leg
	 */
	private void emulateVehicleDischarging(ElectricVehicle ev, Leg leg) {
		//retrieve mode specific network
		Network network = this.singleModeNetworksCache.getSingleModeNetworksCache().get(leg.getMode());
		//retrieve routin mode specific travel time
		String routingMode = TripStructureUtils.getRoutingMode(leg);
		TravelTime travelTime = this.travelTimes.get(routingMode);
		if (travelTime == null) {
			throw new RuntimeException("No TravelTime bound for mode " + routingMode + ".");
		}

//		Map<Link, Double> consumptions = new LinkedHashMap<>();
		NetworkRoute route = (NetworkRoute)leg.getRoute();
		List<Link> links = NetworkUtils.getLinks(network, route.getLinkIds());

		DriveEnergyConsumption driveEnergyConsumption = ev.getDriveEnergyConsumption();
		AuxEnergyConsumption auxEnergyConsumption = ev.getAuxEnergyConsumption();
		double linkEnterTime = leg.getDepartureTime().seconds();
		for (Link l : links) {
			double travelT = travelTime.getLinkTravelTime(l, leg.getDepartureTime().seconds(), null, null);

			double driveConsumption = driveEnergyConsumption.calcEnergyConsumption(l, travelT, linkEnterTime);
			double auxConsumption = auxEnergyConsumption.calcEnergyConsumption(leg.getDepartureTime().seconds(), travelT, l.getId());
//			double consumption = driveEnergyConsumption.calcEnergyConsumption(l, travelT, linkEnterTime)
//					+ auxEnergyConsumption.calcEnergyConsumption(leg.getDepartureTime().seconds(), travelT, l.getId());
			double consumption = driveConsumption + auxConsumption;
			ev.getBattery().changeSoc(-consumption);
			linkEnterTime += travelT;
		}
	}

//	the following methods are modified versions of EditPlans.findRealActBefore() and EditPlans.findRealActAfter()

	private Activity findRealOrChargingActBefore(MobsimAgent agent, int index) {
		Plan plan = WithinDayAgentUtils.getModifiablePlan(agent) ;
		List<PlanElement> planElements = plan.getPlanElements() ;

		Activity prevAct = null ;
		for ( int ii=0 ; ii<index ; ii++ ) {
			if ( planElements.get(ii) instanceof Activity ) {
				Activity act = (Activity) planElements.get(ii) ;
				if ( !StageActivityTypeIdentifier.isStageActivity( act.getType() )  ||
						act.getType().contains(UrbanVehicleChargingHandler.PLUGIN_INTERACTION) ||
						act.getType().contains(UrbanVehicleChargingHandler.PLUGOUT_INTERACTION)) {
					prevAct = act ;
				}
			}
		}
		return prevAct;
	}

	private  Activity findRealOrChargingActAfter(MobsimAgent agent, int index) {
		Plan plan = WithinDayAgentUtils.getModifiablePlan(agent) ;
		List<PlanElement> planElements = plan.getPlanElements() ;
		return (Activity) planElements.get( findIndexOfRealActAfter(agent, index) ) ;
	}

	private int findIndexOfRealActAfter(MobsimAgent agent, int index) {
		Plan plan = WithinDayAgentUtils.getModifiablePlan(agent) ;
		List<PlanElement> planElements = plan.getPlanElements() ;

		int theIndex = -1 ;
		for ( int ii=planElements.size()-1 ; ii>index; ii-- ) {
			if ( planElements.get(ii) instanceof Activity ) {
				Activity act = (Activity) planElements.get(ii) ;
				if ( !StageActivityTypeIdentifier.isStageActivity( act.getType() )   ||
						act.getType().contains(UrbanVehicleChargingHandler.PLUGIN_INTERACTION) ||
						act.getType().contains(UrbanVehicleChargingHandler.PLUGOUT_INTERACTION) ) {
					theIndex = ii ;
				}
			}
		}
		return theIndex ;
	}

}

