/*
 *   *********************************************************************** *
 *   project: org.matsim.*
 *   *********************************************************************** *
 *                                                                           *
 *   copyright       : (C)  by the members listed in the COPYING,        *
 *                     LICENSE and WARRANTY file.                            *
 *   email           : info at matsim dot org                                *
 *                                                                           *
 *   *********************************************************************** *
 *                                                                           *
 *     This program is free software; you can redistribute it and/or modify  *
 *     it under the terms of the GNU General Public License as published by  *
 *     the Free Software Foundation; either version 2 of the License, or     *
 *     (at your option) any later version.                                   *
 *     See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                           *
 *   ***********************************************************************
 *
 */

package org.matsim.contrib.freight.carrier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import com.google.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.freight.carrier.CarrierCapabilities.Builder;
import org.matsim.contrib.freight.carrier.CarrierCapabilities.FleetSize;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.utils.io.MatsimXmlParser;
import org.matsim.core.utils.misc.Time;
import org.matsim.utils.objectattributes.AttributeConverter;
import org.matsim.utils.objectattributes.attributable.AttributesXmlReaderDelegate;
import org.matsim.vehicles.*;
import org.xml.sax.Attributes;

class CarrierPlanXmlParserV2 extends MatsimXmlParser {

	public static final  Logger logger = LogManager.getLogger(CarrierPlanXmlParserV2.class);

	public static final String CARRIERS = "carriers";
	public static final String CARRIER = "carrier";
	public static final String LINKID = "linkId";
	public static final String SHIPMENTS = "shipments";
	public static final String SHIPMENT = "shipment";
	static final String SERVICES = "services";
	static final String SERVICE = "service";
	public static final String ID = "id";
	public static final String FROM = "from";
	public static final String TO = "to";
	public static final String SIZE = "size";
	public static final String ACTIVITY = "act";
	public static final String TYPE = "type";
	public static final String SHIPMENTID = "shipmentId";
	public static final String START = "start";
	public static final String VEHICLE = "vehicle";
	public static final String VEHICLES = "vehicles";
	private static final String VEHICLE_EARLIEST_START = "earliestStart";
	private static final String VEHICLE_LATEST_END = "latestEnd";
	private static final String VEHICLE_TYPES_MSG = "It used to be possible to have vehicle types both in the plans file, and in a separate file.  The " +
										  "first option is no longer possible." ;
	private static final String ATTRIBUTES = "attributes";
	private static final String ATTRIBUTE = "attribute";

	private Carrier currentCarrier = null;
	private CarrierVehicle currentVehicle = null;
	private CarrierService currentService = null;
	private CarrierShipment currentShipment = null;
	private Tour.Builder currentTourBuilder = null;
	private Id<Link> previousActLoc = null;
	private String previousRouteContent;
	private Map<String, CarrierShipment> currentShipments = null;
	private Map<String, CarrierVehicle> vehicles = null;
	private Collection<ScheduledTour> scheduledTours = null;
	private Double currentScore;
	private boolean selected;
	private final Carriers carriers;
	private final CarrierVehicleTypes carrierVehicleTypes;
	private double currentLegTransTime;
	private double currentLegDepTime;
	private Builder capabilityBuilder;

	private double currentStartTime;

	private Map<Id<CarrierService>, CarrierService> serviceMap;

	private final AttributesXmlReaderDelegate attributesReader = new AttributesXmlReaderDelegate();
	private org.matsim.utils.objectattributes.attributable.Attributes currAttributes =
			new org.matsim.utils.objectattributes.attributable.AttributesImpl();

	/**
	 * Constructs a reader with an empty carriers-container for the carriers to be constructed. 
	 *
	 * @param carriers which is a map that stores carriers
	 * @param carrierVehicleTypes
	 */
	CarrierPlanXmlParserV2( Carriers carriers, CarrierVehicleTypes carrierVehicleTypes ) {
		super();
		this.carriers = carriers;
		this.carrierVehicleTypes = carrierVehicleTypes;
	}

	public void putAttributeConverter( final Class<?> clazz , AttributeConverter<?> converter ) {
		attributesReader.putAttributeConverter( clazz , converter );
	}

	@Inject
	public void putAttributeConverters( final Map<Class<?>, AttributeConverter<?>> converters ) {
		attributesReader.putAttributeConverters( converters );
	}

	@Override
	public void startTag(String name, Attributes atts, Stack<String> context) {
		switch (name) {
			case CARRIER: {
				String id = atts.getValue(ID);
				if (id == null) throw new IllegalStateException("carrierId is missing.");
				currentCarrier = CarrierUtils.createCarrier(Id.create(id, Carrier.class));
				break;
			}

			//services
			case SERVICES:
				serviceMap = new HashMap<>();
				break;
			case SERVICE: {
				String idString = atts.getValue("id");
				if (idString == null) throw new IllegalStateException("service.id is missing.");
				Id<CarrierService> id = Id.create(idString, CarrierService.class);
				String toLocation = atts.getValue("to");
				if (toLocation == null) throw new IllegalStateException("service.to is missing. ");
				Id<Link> to = Id.create(toLocation, Link.class);
				CarrierService.Builder serviceBuilder = CarrierService.Builder.newInstance(id, to);
				String capDemandString = atts.getValue("capacityDemand");
				if (capDemandString != null) serviceBuilder.setCapacityDemand(getInt(capDemandString));
				String startString = atts.getValue("earliestStart");
				double start = parseTimeToDouble(startString);
				double end;
				String endString = atts.getValue("latestEnd");
				end = parseTimeToDouble(endString);
				serviceBuilder.setServiceStartTimeWindow(TimeWindow.newInstance(start, end));
				String serviceTimeString = atts.getValue("serviceDuration");
				if (serviceTimeString != null) serviceBuilder.setServiceDuration(parseTimeToDouble(serviceTimeString));
				currentService = serviceBuilder.build();
				serviceMap.put(currentService.getId(), currentService);
				CarrierUtils.addService(currentCarrier, currentService);
				break;
			}

			//shipments
			case SHIPMENTS:
				currentShipments = new HashMap<>();
				break;
			case SHIPMENT: {
				String idString = atts.getValue("id");
				if (idString == null) throw new IllegalStateException("shipment.id is missing.");
				Id<CarrierShipment> id = Id.create(idString, CarrierShipment.class);
				String from = atts.getValue(FROM);
				if (from == null) throw new IllegalStateException("shipment.from is missing.");
				String to = atts.getValue(TO);
				if (to == null) throw new IllegalStateException("shipment.to is missing.");
				String sizeString = atts.getValue(SIZE);
				if (sizeString == null) throw new IllegalStateException("shipment.size is missing.");
				int size = getInt(sizeString);
				CarrierShipment.Builder shipmentBuilder = CarrierShipment.Builder.newInstance(id, Id.create(from, Link.class), Id.create(to, Link.class), size);

				String startPickup = atts.getValue("startPickup");
				String endPickup = atts.getValue("endPickup");
				String startDelivery = atts.getValue("startDelivery");
				String endDelivery = atts.getValue("endDelivery");
				String pickupServiceTime = atts.getValue("pickupServiceTime");
				String deliveryServiceTime = atts.getValue("deliveryServiceTime");

				if (startPickup != null && endPickup != null)
					shipmentBuilder.setPickupTimeWindow(TimeWindow.newInstance(parseTimeToDouble(startPickup), parseTimeToDouble(endPickup)));
				if (startDelivery != null && endDelivery != null)
					shipmentBuilder.setDeliveryTimeWindow(TimeWindow.newInstance(parseTimeToDouble(startDelivery), parseTimeToDouble(endDelivery)));
				if (pickupServiceTime != null)
					shipmentBuilder.setPickupServiceTime(parseTimeToDouble(pickupServiceTime));
				if (deliveryServiceTime != null)
					shipmentBuilder.setDeliveryServiceTime(parseTimeToDouble(deliveryServiceTime));

				currentShipment = shipmentBuilder.build();
				currentShipments.put(atts.getValue(ID), currentShipment);
				CarrierUtils.addShipment(currentCarrier, currentShipment);
				break;
			}

			//capabilities
			case "capabilities":
				String fleetSize = atts.getValue("fleetSize");
				if (fleetSize == null) throw new IllegalStateException("fleetSize is missing.");
				this.capabilityBuilder = Builder.newInstance();
				if (fleetSize.toUpperCase().equals(FleetSize.FINITE.toString())) {
					this.capabilityBuilder.setFleetSize(FleetSize.FINITE);
				} else {
					this.capabilityBuilder.setFleetSize(FleetSize.INFINITE);
				}
				break;

			//vehicle-type
			case "vehicleType":
				throw new RuntimeException(VEHICLE_TYPES_MSG);
			case "engineInformation":
				throw new RuntimeException(VEHICLE_TYPES_MSG);
			case "costInformation":
				throw new RuntimeException(VEHICLE_TYPES_MSG);


				//vehicle
			case VEHICLES:
				vehicles = new HashMap<>();
				break;
			case VEHICLE:

				String vId = atts.getValue(ID);
				if (vId == null) throw new IllegalStateException("vehicleId is missing.");

				String depotLinkId = atts.getValue("depotLinkId");
				if (depotLinkId == null) throw new IllegalStateException("depotLinkId of vehicle is missing.");

				String typeId = atts.getValue("typeId");
				if (typeId == null) throw new IllegalStateException("vehicleTypeId is missing.");
				VehicleType vehicleType = this.carrierVehicleTypes.getVehicleTypes().get(Id.create(typeId, VehicleType.class));
				if (vehicleType == null) {
					throw new RuntimeException("vehicleTypeId=" + typeId + " is missing.");
				}

				CarrierVehicle.Builder vehicleBuilder = CarrierVehicle.Builder.newInstance(Id.create(vId, Vehicle.class), Id.create(depotLinkId, Link.class), vehicleType);
				String startTime = atts.getValue(VEHICLE_EARLIEST_START);
				if (startTime != null) vehicleBuilder.setEarliestStart(parseTimeToDouble(startTime));
				String endTime = atts.getValue(VEHICLE_LATEST_END);
				if (endTime != null) vehicleBuilder.setLatestEnd(parseTimeToDouble(endTime));

				CarrierVehicle vehicle = vehicleBuilder.build();
				capabilityBuilder.addVehicle(vehicle);
				vehicles.put(vId, vehicle);
				break;

			//plans
			case "plan":
				String score = atts.getValue("score");
				if (score != null) currentScore = parseTimeToDouble(score);
				String selected = atts.getValue("selected");
				if (selected == null) this.selected = false;
				else this.selected = selected.equals("true");
				scheduledTours = new ArrayList<>();
				break;
			case "tour":
				String vehicleId = atts.getValue("vehicleId");
				if (vehicleId == null) throw new IllegalStateException("vehicleId is missing in tour.");
				currentVehicle = vehicles.get(vehicleId);
				if (currentVehicle == null)
					throw new IllegalStateException("vehicle to vehicleId " + vehicleId + " is missing.");
				currentTourBuilder = Tour.Builder.newInstance();
				break;
			case "leg":
				String depTime = atts.getValue("expected_dep_time");
				if (depTime == null) depTime = atts.getValue("dep_time");
				if (depTime == null) throw new IllegalStateException("leg.expected_dep_time is missing.");
				currentLegDepTime = parseTimeToDouble(depTime);
				String transpTime = atts.getValue("expected_transp_time");
				if (transpTime == null) transpTime = atts.getValue("transp_time");
				if (transpTime == null) throw new IllegalStateException("leg.expected_transp_time is missing.");
				currentLegTransTime = parseTimeToDouble(transpTime);
				break;
			case ACTIVITY:
				String type = atts.getValue(TYPE);
				if (type == null) throw new IllegalStateException("activity type is missing");
				String actEndTime = atts.getValue("end_time");
				switch (type) {
					case "start":
						if (actEndTime == null)
							throw new IllegalStateException("endTime of activity \"" + type + "\" missing.");
						currentStartTime = parseTimeToDouble(actEndTime);
						previousActLoc = currentVehicle.getLinkId();
						currentTourBuilder.scheduleStart(currentVehicle.getLinkId(), TimeWindow.newInstance(currentVehicle.getEarliestStartTime(), currentVehicle.getLatestEndTime() ) );

						break;
					case "pickup": {
						String id = atts.getValue(SHIPMENTID);
						if (id == null) throw new IllegalStateException("pickup.shipmentId is missing.");
						CarrierShipment s = currentShipments.get(id);
						finishLeg(s.getFrom());
						currentTourBuilder.schedulePickup(s);
						previousActLoc = s.getFrom();
						break;
					}
					case "delivery": {
						String id = atts.getValue(SHIPMENTID);
						if (id == null) throw new IllegalStateException("delivery.shipmentId is missing.");
						CarrierShipment s = currentShipments.get(id);
						finishLeg(s.getTo());
						currentTourBuilder.scheduleDelivery(s);
						previousActLoc = s.getTo();
						break;
					}
					case "service": {
						String id = atts.getValue("serviceId");
						if (id == null) throw new IllegalStateException("act.serviceId is missing.");
						CarrierService s = serviceMap.get(Id.create(id, CarrierService.class));
						if (s == null) throw new IllegalStateException("serviceId is not known.");
						finishLeg(s.getLocationLinkId());
						currentTourBuilder.scheduleService(s);
						previousActLoc = s.getLocationLinkId();
						break;
					}
					case "end":
						finishLeg(currentVehicle.getLinkId() );
						currentTourBuilder.scheduleEnd(currentVehicle.getLinkId(), TimeWindow.newInstance(currentVehicle.getEarliestStartTime(), currentVehicle.getLatestEndTime() ) );
						break;
				}
				break;
			case ATTRIBUTES:
				switch (context.peek()) {
					case CARRIER:
						currAttributes = currentCarrier.getAttributes();
						break;
					case SERVICE:
						currAttributes = currentService.getAttributes();
						break;
					case SHIPMENT:
						currAttributes = currentShipment.getAttributes();
						break;
					default:
						throw new RuntimeException("could not derive context for attributes. context=" + context.peek());
				}
				attributesReader.startTag(name, atts, context, currAttributes);
				break;
			case ATTRIBUTE:
				attributesReader.startTag(name, atts, context, currAttributes);
				break;
			case "route":
				// do nothing
				break ;
			default:
				logger.warn("Unexpected value while reading in. This field will be ignored: " + name);
		}
	}

	@Override
	public void endTag(String name, String content, Stack<String> context) {
		switch (name) {
			case "capabilities":
				currentCarrier.setCarrierCapabilities(capabilityBuilder.build());
				break;
			case "vehicleType":
				throw new RuntimeException("I am confused now if, for carriers, vehicleType is in the plans file, or in a separate file.");
			case "route":
				this.previousRouteContent = content;
				break;
			case "carrier":
				Gbl.assertNotNull(currentCarrier);
				Gbl.assertNotNull(carriers);
				Gbl.assertNotNull(carriers.getCarriers());
				carriers.getCarriers().put(currentCarrier.getId(), currentCarrier);
				currentCarrier = null;
				break;
			case "plan":
				CarrierPlan currentPlan = new CarrierPlan(currentCarrier, scheduledTours);
				currentPlan.setScore(currentScore);
				currentCarrier.getPlans().add(currentPlan);
				if (this.selected) {
					currentCarrier.setSelectedPlan(currentPlan);
				}
				break;
			case "tour":
				ScheduledTour sTour = ScheduledTour.newInstance(currentTourBuilder.build(), currentVehicle, currentStartTime);
				scheduledTours.add(sTour);
				break;
			case "description":
				throw new RuntimeException(VEHICLE_TYPES_MSG);
			case SERVICE:
				this.currentService = null;
				break;
			case SHIPMENT:
				this.currentShipment = null;
				break;
			case ATTRIBUTE:
				this.attributesReader.endTag(name, content, context);
				break;
			case ATTRIBUTES:
				this.currAttributes = null;
				break;
		}
	}

	private void finishLeg(Id<Link> toLocation) {
		NetworkRoute route = null;
		if (previousRouteContent != null) {
			List<Id<Link>> linkIds = NetworkUtils.getLinkIds(previousRouteContent);
			route = RouteUtils.createLinkNetworkRouteImpl(previousActLoc, toLocation);
			if (!linkIds.isEmpty()) {
				route.setLinkIds(previousActLoc, linkIds, toLocation);
			}
		}
		currentTourBuilder.addLeg(currentTourBuilder.createLeg(route, currentLegDepTime,currentLegTransTime));
		previousRouteContent = null;
	}

	private double parseTimeToDouble(String timeString) {
		if (timeString.contains(":")) {
			return Time.parseTime(timeString);
		} else {
			return Double.parseDouble(timeString);
		}
	}

	private int getInt(String value) {
		return Integer.parseInt(value);
	}

}
