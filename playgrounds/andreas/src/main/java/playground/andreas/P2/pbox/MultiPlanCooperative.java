/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2011 by the members listed in the COPYING,        *
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

package playground.andreas.P2.pbox;

import java.util.LinkedList;
import java.util.List;

import org.matsim.api.core.v01.Id;
import org.matsim.core.gbl.MatsimRandom;

import playground.andreas.P2.helper.PConfigGroup;
import playground.andreas.P2.plan.PPlan;
import playground.andreas.P2.plan.PRouteProvider;
import playground.andreas.P2.replanning.PPlanStrategy;
import playground.andreas.P2.replanning.PStrategyManager;

/**
 * This cooperative has multiple plans. Each is weighted by the number of vehicles associated with.
 * The number of vehicles depends on the score per vehicle and plan. In the end, each plan should have approximately the same score.
 * 
 * @author aneumann
 *
 */
public class MultiPlanCooperative extends AbstractCooperative{
	
	public static final String COOP_NAME = "MultiPlanCooperative";
	
	private List<PPlan> plans;

	public MultiPlanCooperative(Id id, PConfigGroup pConfig, PFranchise franchise){
		super(id, pConfig, franchise);
		this.plans = new LinkedList<PPlan>();
	}
	
	public void init(PRouteProvider pRouteProvider, PPlanStrategy initialStrategy, int iteration, double initialBudget) {
		super.init(pRouteProvider, initialStrategy, iteration, initialBudget);
		this.plans.add(this.bestPlan);
		this.bestPlan = null;
	}
	
	@Override
	public List<PPlan> getAllPlans(){
		return this.plans;		
	}
	
	@Override
	public PPlan getBestPlan() {
		if (this.bestPlan == null) {
			
			// will not return the best plan, but one random plan selected from all plans with at least two vehicles
			List<PPlan> plansWithAtLeastTwoVehicles = new LinkedList<PPlan>();
			int numberOfVehicles = 0;
			for (PPlan pPlan : this.plans) {
				if (pPlan.getNVehicles() > 1) {
					plansWithAtLeastTwoVehicles.add(pPlan);
					numberOfVehicles += pPlan.getNVehicles();
				}
			}

			double accumulatedWeight = 0.0;
			for (PPlan pPlan : plansWithAtLeastTwoVehicles) {
				accumulatedWeight += pPlan.getNVehicles();
				if (MatsimRandom.getRandom().nextDouble() * numberOfVehicles <= accumulatedWeight) {
					this.bestPlan = pPlan;
					return this.bestPlan;
				}
			}
		}
		
		return this.bestPlan;
	}

	public void replan(PStrategyManager pStrategyManager, int iteration) {	
		this.currentIteration = iteration;
		
		// First, balance the budget
		if(this.budget < 0){
			// insufficient, sell vehicles
			int numberOfVehiclesToSell = -1 * Math.min(-1, (int) Math.floor(this.budget / this.costPerVehicleSell));
			
			while (numberOfVehiclesToSell > 0) {
				this.findWorstPlanAndRemoveOneVehicle(this.plans);
				this.budget += this.costPerVehicleSell;
				numberOfVehiclesToSell--;
			}
		}

		// Second, buy vehicles
		
		int numberOfNewVehicles = 0;		
		while (this.getBudget() > this.getCostPerVehicleBuy()) {
			// budget ok, buy one
			this.setBudget(this.getBudget() - this.getCostPerVehicleBuy());
			numberOfNewVehicles++;
		}
		// distribute them among the plans
		while (numberOfNewVehicles > 0) {
			PPlan bestPlan = null;
			// find plan with best score per vehicle
			for (PPlan plan : this.plans) {
				if (bestPlan == null) {
					bestPlan = plan;
				} else {
					if (plan.getPlannedScorePerVehicle() > bestPlan.getPlannedScorePerVehicle()) {
						bestPlan = plan;
					}
				}
			}

			// add one vehicle to best plan
			bestPlan.setNVehicles(bestPlan.getNVehicles() + 1);
			numberOfNewVehicles--;
		}

		
		// Third, replan
		this.getBestPlan();
		if (this.bestPlan != null) {
			PPlanStrategy strategy = pStrategyManager.chooseStrategy();
			PPlan newPlan = strategy.run(this);
			if (newPlan != null) {
				// check, if it is a duplicate of an existing plan
				for (PPlan plan : this.plans) {
					if(plan.isSameButVehSize(newPlan)) {
						newPlan = null;
						break;
					}
				}
				
				if (newPlan != null) {
					// remove vehicle from worst plan
					this.findWorstPlanAndRemoveOneVehicle(this.plans);

//					this.bestPlan.setNVehicles(this.bestPlan.getNVehicles() - 1);
					this.bestPlan = null;
					this.plans.add(newPlan);
				}
			}
		}
		
		// Fourth, reinitialize all plan
		for (PPlan plan : this.plans) {
			plan.setLine(this.routeProvider.createTransitLine(this.id, plan.getStartTime(), plan.getEndTime(), plan.getNVehicles(), plan.getStopsToBeServed(), plan.getId()));
		}
		
		this.updateCurrentTransitLine();
	}
	
	/**
	 * Find plan with worst score per vehicle. Removes one vehicle. Removes the whole plan, if no vehicle is left.
	 * 
	 * @param plans
	 * @return
	 */
	private void findWorstPlanAndRemoveOneVehicle(List<PPlan> plans){
		PPlan worstPlan = null;
		for (PPlan plan : this.plans) {
			if (worstPlan == null) {
				worstPlan = plan;
			} else {
				if (plan.getPlannedScorePerVehicle() < worstPlan.getPlannedScorePerVehicle()) {
					worstPlan = plan;
				}
			}
		}
		
		// remove one vehicle
		worstPlan.setNVehicles(worstPlan.getNVehicles() - 1);
		// remove plan, if not served anymore
		if (worstPlan.getNVehicles() == 0) {
			this.plans.remove(worstPlan);
		}
	}
	
}