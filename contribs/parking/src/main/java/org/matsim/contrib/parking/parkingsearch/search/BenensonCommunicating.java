/*
package org.matsim.contrib.parking.parkingsearch.search;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.parking.parkingsearch.ParkingUtils;
import org.matsim.contrib.parking.parkingsearch.manager.ParkingSearchManager;
import org.matsim.contrib.parking.parkingsearch.sim.ParkingSearchConfigGroup;
import org.matsim.core.network.NetworkUtils;

import java.util.HashSet;

public class BenensonCommunicating extends BenensonParkingSearchLogic{
	private HashSet<Id<Link>> knownLinks;
	private Network network;
	private ParkingSearchConfigGroup configGroup;
	private final ParkingSearchManager parkingManager;



	public BenensonCommunicating(Network network, ParkingSearchConfigGroup cfgGroup, ParkingSearchManager parkingManager){

		this.network = network;
		this.configGroup = cfgGroup;
		this.parkingManager = parkingManager;
		this.knownLinks = new HashSet<Id<Link>>();

	}


	public Id<Link> getNextLinkBenensonRouting(Id<Link> currentLinkId, Id<Link> destinationLinkId, String mode) {
		super.getNextLinkBenensonRouting(Id<Link> currentLinkId, Id<Link> destinationLinkId, String mode);
		Link currentLink = network.getLinks().get(currentLinkId);

		//calculate the distance to fromNode of destination link instead of distance to activity
		Node destination = network.getLinks().get(destinationLinkId).getFromNode();

		double distanceToDest = Double.MAX_VALUE;

		Node nextNode;
		Id<Link> nextLinkId = null;

		for (Link outLink : ParkingUtils.getOutgoingLinksForMode(currentLink, mode)) {
			Id<Link> outLinkId = outLink.getId();

			if (outLinkId.equals(destinationLinkId)) {
				return outLinkId;
			}
			nextNode = outLink.getToNode();
			double dd = NetworkUtils.getEuclideanDistance(destination.getCoord(),nextNode.getCoord());
			if( dd < distanceToDest){
				nextLinkId = outLinkId;
				distanceToDest = dd;
			}
			else if(dd == distanceToDest){
				if (Math.random() > 0.5){
					nextLinkId = outLinkId;
				}
			}
		}
		this.knownLinks.add(nextLinkId);
		return nextLinkId;
	}








}
*/
