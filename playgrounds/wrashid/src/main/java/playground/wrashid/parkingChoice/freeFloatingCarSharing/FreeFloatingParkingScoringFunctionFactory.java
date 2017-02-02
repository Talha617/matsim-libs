package playground.wrashid.parkingChoice.freeFloatingCarSharing;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.contrib.parking.parkingchoice.PC2.scoring.ParkingScoreManager;
import org.matsim.contrib.parking.parkingchoice.PC2.scoring.ParkingScoringFunction;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.scoring.SumScoringFunction.BasicScoring;
import org.matsim.core.scoring.functions.CharyparNagelActivityScoring;
import org.matsim.core.scoring.functions.CharyparNagelAgentStuckScoring;
import org.matsim.core.scoring.functions.CharyparNagelLegScoring;
import org.matsim.core.scoring.functions.CharyparNagelMoneyScoring;
import org.matsim.core.scoring.functions.CharyparNagelScoringParametersForPerson;
import org.matsim.core.scoring.functions.SubpopulationCharyparNagelScoringParameters;

import com.google.inject.Inject;

public class FreeFloatingParkingScoringFunctionFactory implements ScoringFunctionFactory{

	private ParkingScoreManager parkingScoreManager;
	private Scenario scenario;
	private CharyparNagelScoringParametersForPerson parameters;

	//@Inject
	//private ScoreTrackingListener tracker;

	@Inject
	public FreeFloatingParkingScoringFunctionFactory(
			Scenario scenario, ParkingScoreManager parkingScoreManager) {
		this.scenario = scenario;
		this.parkingScoreManager = parkingScoreManager;
		this.parameters = new SubpopulationCharyparNagelScoringParameters( scenario );
	}

	@Override
	public ScoringFunction createNewScoringFunction(Person person) {
		final SumScoringFunction scoringFunctionSum = new SumScoringFunction();
		
		addScoringFunction(person.getId(), scoringFunctionSum, new CharyparNagelMoneyScoring(
				parameters.getScoringParameters( person ) ) );
		
		addScoringFunction(person.getId(), scoringFunctionSum, new CharyparNagelLegScoring(
				  parameters.getScoringParameters( person ),				  
				  this.scenario.getNetwork()));
		addScoringFunction(person.getId(), scoringFunctionSum, new CharyparNagelActivityScoring(				
				parameters.getScoringParameters( person )));
		
		addScoringFunction(person.getId(), scoringFunctionSum, new CharyparNagelAgentStuckScoring(
				parameters.getScoringParameters( person ) ) );	
		
		addScoringFunction(person.getId(), scoringFunctionSum, new ParkingScoringFunction(person
				.getSelectedPlan(),parkingScoreManager) );		
		
		return scoringFunctionSum;
	}
	
	
	private void addScoringFunction(
			final Id<Person> person,
			final SumScoringFunction function,
			final BasicScoring element ) {
		//tracker.addScoringFunction(person, element);
		function.addScoringFunction(element);
	}

}