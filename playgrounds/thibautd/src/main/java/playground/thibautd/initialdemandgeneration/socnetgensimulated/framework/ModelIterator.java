/* *********************************************************************** *
 * project: org.matsim.*
 * ModelIterator.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2015 by the members listed in the COPYING,        *
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
package playground.thibautd.initialdemandgeneration.socnetgensimulated.framework;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

import org.apache.log4j.Logger;

import playground.thibautd.initialdemandgeneration.socnetgen.framework.SnaUtils;
import playground.thibautd.socnetsim.population.SocialNetwork;

/**
 * @author thibautd
 */
public class ModelIterator {
	private static final Logger log =
		Logger.getLogger(ModelIterator.class);


	private final double targetClustering;
	private final double targetDegree;

	private final double precisionClustering;
	private final double precisionDegree;

	private final double initialPrimaryStep;
	private final double initialSecondaryStep;

	private final int stagnationLimit;
	private final int maxIterations;

	// TODO: make adaptive (the closer to the target value,
	// the more precise is should get)
	private double samplingRateClustering = 1;
	private final List<EvolutionListener> listeners = new ArrayList< >();

	public ModelIterator( final SocialNetworkGenerationConfigGroup config ) {
		this.targetClustering = config.getTargetClustering();
		this.targetDegree = config.getTargetDegree();

		listeners.add( new EvolutionLogger() );

		setSamplingRateClustering( config.getSamplingRateForClusteringEstimation() );
		this.precisionClustering = config.getPrecisionClustering();
		this.precisionDegree = config.getPrecisionDegree();

		this.initialPrimaryStep = config.getInitialPrimaryStep();
		this.initialSecondaryStep = config.getInitialSecondaryStep();

		this.stagnationLimit = config.getStagnationLimit();
		this.maxIterations = config.getMaxIterations();
	}

	public SocialNetwork iterateModelToTarget(
			final ModelRunner runner,
			final Thresholds initialThresholds ) {
		final ThresholdMemory memory = new ThresholdMemory( initialThresholds );

		int stagnationCount = 0;
		Thresholds currentBest = initialThresholds;

		for ( int iter=1; iter < maxIterations; iter++ ) {
			log.info( "Iteration # "+iter );
			final long start = System.currentTimeMillis();
			final Thresholds thresholds =
					memory.createNewThresholds( );

			log.info( "generate network for "+thresholds );
			final SocialNetwork sn = runner.runModel( thresholds );

			thresholds.setResultingAverageDegree( SnaUtils.calcAveragePersonalNetworkSize( sn ) );
			thresholds.setResultingClustering( estimateClustering( sn ) );

			final Thresholds newBest = memory.add( thresholds );

			log.info( "Iteration # "+iter+" took "+(System.currentTimeMillis() - start)+" ms" );
			if ( isAcceptable( thresholds ) ) {
				log.info( "END - "+thresholds+" fulfills the precision criteria!" );
				return sn;
			}

			if ( newBest == currentBest && stagnationCount++ > stagnationLimit ) {
				log.warn( "stop iterations before reaching the required precision level!" );
				log.info( "END - stagnating on "+currentBest+" since "+stagnationCount+" iterations." );
				return runner.runModel( currentBest );
			}
			else {
				stagnationCount = 0;
				currentBest = newBest;
			}
		}

		log.warn( "stop iterations before reaching the required precision level!" );
		log.info( "END - Maximum number of iterations reached. Best so far: "+currentBest );
		return runner.runModel( currentBest );
	}

	private double estimateClustering( final SocialNetwork sn ) {
		final double estimate = SnaUtils.estimateClusteringCoefficient( samplingRateClustering , sn );

		return Math.abs( targetClustering - estimate ) > 10 * precisionClustering ? estimate : SnaUtils.calcClusteringCoefficient( sn );
	}

	public void addListener( final EvolutionListener l ) {
		listeners.add( l );
	}

	public void setSamplingRateClustering( final double rate ) {
		if ( rate < 0 || rate > 1 ) throw new IllegalArgumentException( rate+" is not in [0;1]" );
		this.samplingRateClustering = rate;
	}

	private boolean isAcceptable(
			final Thresholds thresholds ) {
		return distClustering( thresholds ) < precisionClustering &&
			distDegree( thresholds ) < precisionDegree;
	}

	private double distClustering( final Thresholds thresholds ) {
		return Math.abs( targetClustering -  thresholds.getResultingClustering() );
	}

	private double distDegree( final Thresholds thresholds ) {
		return Math.abs( targetDegree -  thresholds.getResultingAverageDegree() );
	}

	// TODO: write moves to file, to be able to plot tree
	private class ThresholdMemory {

		private final Queue<Move> queue =
			new PriorityQueue< >(
					10,
					 new Comparator<Move>() {
							@Override
							public int compare( Move o1 , Move o2 ) {
								// Note: priority queue returns the LOWEST
								// element (which is awfully confusing
								// when one thinks in terms of comparing
								// PRIORITY...)
								if ( !equivalent( o1.getParent() , o2.getParent() ) ) {
									// if not same parent, starting from lower function is better (try to minimize)
									return Double.compare( function( o1.getParent() ) , function( o2.getParent() ) );
								}

								final double parentDegree = o1.getParent().getResultingAverageDegree();
								final double parentClustering = o1.getParent().getResultingClustering();

								final int primaryCompare = Double.compare( o1.getChild().getPrimaryThreshold() , o2.getChild().getPrimaryThreshold() );
								final int secondaryCompare = Double.compare(o1.getChild().getSecondaryReduction() , o2.getChild().getSecondaryReduction() );

								if ( primaryCompare != 0 ) {
									// if degree too high, bigger threshold increase move is better
									return parentDegree > targetDegree ? -primaryCompare : primaryCompare;
								}

								if ( secondaryCompare != 0 ) {
									// if clustering too low, bigger reduction is better
									return parentClustering < targetClustering ? -secondaryCompare : secondaryCompare;
								}

								return 0;
							}
						} );

		// we want an improvement of one precision unit to result in the same effect
		// on mono-objective version.
		private final double factorClustering = precisionDegree / precisionClustering;

		private final double exponent = 1;
		private final double contractionFactor = 2;
		private final double expansionFactor = 1;
		private final double flatExpansionFactor = 2;

		private Move lastMove = null;

		private Thresholds initial;

		public ThresholdMemory( final Thresholds initial ) {
			this.initial = initial;
		}

		/**
		 * @return the current best thresholds
		 */
		public Thresholds add( final Thresholds t ) {
			if ( lastMove != null && t != lastMove.getChild() ) throw new IllegalArgumentException();

			//final boolean hadDegreeImprovement = lastMove == null || distDegree( t ) < distDegree( lastMove.parent );
			//final boolean hadClusteringImprovement = lastMove == null || distClustering( t ) < distClustering( lastMove.parent );

			// no improvement means "overshooting": decrease step sizes
			final double primaryStepSize = lastMove == null ? initialPrimaryStep : lastMove.stepSizePrimary;
			final double secondaryStepSize = lastMove == null ? initialSecondaryStep : lastMove.stepSizeSecondary;

			log.info( "New step Sizes:" );
			log.info( "primary : "+primaryStepSize );
			log.info( "secondary : "+secondaryStepSize );

			if ( lastMove == null || isBetter( t ) ) {

				log.info( "improvement with "+t+" compared to "+( lastMove == null ? null : lastMove.getParent() ) );
				log.info( "new value "+function( t ) );
				fillQueueWithChildren(
						t,
						primaryStepSize * expansionFactor,
						secondaryStepSize * expansionFactor );

				if (lastMove != null) for ( EvolutionListener l : listeners ) l.handleMove( lastMove , true );

				return t;
			}

			// TODO: if exactly the same value, search *further*
			log.info( "new value "+function( t ) );

			if ( equivalent( t, lastMove.getParent() ) ) {
				log.info( "exactly same value as parent: probably in a flat area --- step and expand" );
				addToStack(
						new Move(
							t,
							lastMove.getStepPrimary() * flatExpansionFactor,
							lastMove.getStepSecondary() * flatExpansionFactor,
							primaryStepSize * flatExpansionFactor,
							secondaryStepSize * flatExpansionFactor ) );
			}
			else {
				log.info( "no improvement with " + t + " compared to " + lastMove.getParent() + ": contract steps" );
				fillQueueWithChildren(
						lastMove.getParent(),
						primaryStepSize / contractionFactor,
						secondaryStepSize / contractionFactor );
			}

			for ( EvolutionListener l : listeners ) l.handleMove( lastMove , false );

			return lastMove.getParent();
		}

		private boolean equivalent(
				final Thresholds t,
				final Thresholds parent ) {
			return Math.abs( t.getResultingAverageDegree() - parent.getResultingAverageDegree() ) < 1E-9 &&
					Math.abs( t.getResultingClustering() - parent.getResultingClustering() ) < 1E-9;
		}

		private boolean isBetter( Thresholds t ) {
			return function( t ) < function( lastMove.getParent() );
		}

		private double function( Thresholds t ) {
			return Math.pow( distDegree( t ) , exponent ) +
				Math.pow( factorClustering * distClustering( t ) , exponent );
		}

		public Thresholds createNewThresholds() {
			if ( initial != null ) {
				final Thresholds v = initial;
				initial = null;
				return v;
			}

			lastMove = queue.remove();
			return lastMove.getChild();
		}

		private boolean fillQueueWithChildren( final Thresholds point , final double stepDegree , final double stepSecondary ) {
			//addToStack( moveByStep( point , stepDegree , stepSecondary ) );
			boolean smth = false;

			smth = smth | addToStack( new Move( point , 0 , stepSecondary , stepDegree ,stepSecondary ) );
			smth = smth | addToStack( new Move( point , stepDegree , 0 , stepDegree ,stepSecondary ) );
			smth = smth | addToStack( new Move( point , 0 , -stepSecondary , stepDegree ,stepSecondary ) );
			smth = smth | addToStack( new Move( point , -stepDegree , 0 , stepDegree ,stepSecondary ) );

			return smth;
		}
		
		private boolean addToStack( final Move move  ) {
			queue.add( move );
			return true;
		}
	}

	public static class Move {
		private final Thresholds parent;
		private final double stepPrimary;
		private final double stepSecondary;
		private final Thresholds child;

		private final double stepSizePrimary;
		private final double stepSizeSecondary;

		private Move(
				final Thresholds parent,
				final double stepPrimary,
				final double stepSecondary,
				final double stepSizePrimary,
				final double stepSizeSecondary ) {
			this.parent = parent;
			this.stepPrimary = stepPrimary;
			this.stepSecondary = stepSecondary;
			this.child = new Thresholds(
					parent.getPrimaryThreshold() + stepPrimary,
					parent.getSecondaryReduction() + stepSecondary );
			this.stepSizePrimary = stepSizePrimary;
			this.stepSizeSecondary = stepSizeSecondary;
		}

		public Thresholds getParent() {
			return parent;
		}

		public double getStepPrimary() {
			return stepPrimary;
		}

		public double getStepSecondary() {
			return stepSecondary;
		}

		public Thresholds getChild() {
			return child;
		}
	}

	public static interface EvolutionListener {
		public void handleMove( Move m , boolean improved );
	}

	private static class EvolutionLogger implements EvolutionListener {
		@Override
		public void handleMove( final Move m , final boolean improved ) {
			log.info( "generated network for "+m.getChild() );
		}
	}
}

