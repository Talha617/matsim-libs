/* *********************************************************************** *
 * project: org.matsim.*
 * CharyparNagelOpenTimesScoringFunctionFactory.java
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

package org.matsim.core.scoring.charyparNagel;

import org.matsim.api.basic.v01.TransportMode;
import org.matsim.core.api.population.Leg;
import org.matsim.core.api.population.Plan;
import org.matsim.core.api.population.Route;
import org.matsim.core.scoring.CharyparNagelScoringParameters;
import org.matsim.core.scoring.interfaces.BasicScoring;
import org.matsim.core.scoring.interfaces.LegScoring;

/**
 * This is a re-implementation of the original CharyparNagel function, based on a
 * modular approach.
 * 
 * @author rashid_waraich
 */
public class LegScoringFunction implements LegScoring, BasicScoring {

	protected final Plan plan;

	protected double score;
	private double lastTime;
	private int index; // the current position in plan.actslegs

	private static final double INITIAL_LAST_TIME = 0.0;
	private static final int INITIAL_INDEX = 1;
	private static final double INITIAL_SCORE = 0.0;

	/** The parameters used for scoring */
	protected final CharyparNagelScoringParameters params;

	public LegScoringFunction(final Plan plan, final CharyparNagelScoringParameters params) {
		this.params = params;
		this.reset();

		this.plan = plan;
	}

	public void reset() {
		this.lastTime = INITIAL_LAST_TIME;
		this.index = INITIAL_INDEX;
		this.score = INITIAL_SCORE;
	}

	public void startLeg(final double time, final Leg leg) {
		this.lastTime = time;
	}

	public void endLeg(final double time) {
		handleLeg(time);
		this.lastTime = time;
	}

	public void finish() {

	}

	public double getScore() {
		return this.score;
	}

	protected double calcLegScore(final double departureTime, final double arrivalTime, final Leg leg) {
		double tmpScore = 0.0;
		double travelTime = arrivalTime - departureTime; // traveltime in
															// seconds

		/*
		 * we only as for the route when we have to calculate a distance cost,
		 * because route.getDist() may calculate the distance if not yet
		 * available, which is quite an expensive operation
		 */
		double dist = 0.0; // distance in meters

		if (TransportMode.car.equals(leg.getMode())) {
			if (this.params.marginalUtilityOfDistanceCar != 0.0) {
				Route route = leg.getRoute();
				dist = route.getDistance();
				/*
				 * TODO the route-distance does not contain the length of the
				 * first or last link of the route, because the route doesn't
				 * know those. Should be fixed somehow, but how? MR, jan07
				 */
				/*
				 * TODO in the case of within-day replanning, we cannot be sure
				 * that the distance in the leg is the actual distance driven by
				 * the agent.
				 */
			}
			tmpScore += travelTime * this.params.marginalUtilityOfTraveling + this.params.marginalUtilityOfDistanceCar * dist;
		} else if (TransportMode.pt.equals(leg.getMode())) {
			if (this.params.marginalUtilityOfDistancePt != 0.0) {
				dist = leg.getRoute().getDistance();
			}
			tmpScore += travelTime * this.params.marginalUtilityOfTravelingPT + this.params.marginalUtilityOfDistancePt * dist;
		} else if (TransportMode.walk.equals(leg.getMode())) {
			if (this.params.marginalUtilityOfDistanceWalk != 0.0) {
				dist = leg.getRoute().getDistance();
			}
			tmpScore += travelTime * this.params.marginalUtilityOfTravelingWalk + this.params.marginalUtilityOfDistanceWalk * dist;
		} else {
			if (this.params.marginalUtilityOfDistanceCar != 0.0) {
				dist = leg.getRoute().getDistance();
			}
			// use the same values as for "car"
			tmpScore += travelTime * this.params.marginalUtilityOfTraveling + this.params.marginalUtilityOfDistanceCar * dist;
		}

		return tmpScore;
	}

	private void handleLeg(final double time) {
		Leg leg = (Leg) this.plan.getPlanElements().get(this.index);
		this.score += calcLegScore(this.lastTime, time, leg);
		this.index += 2;
	}

}
