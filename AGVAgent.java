/*
 * Copyright (C) 2011-2016 Rinde van Lon, iMinds-DistriNet, KU Leuven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *		 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

import org.apache.commons.math3.random.RandomGenerator;

import com.github.rinde.rinsim.core.model.comm.CommDevice;
import com.github.rinde.rinsim.core.model.comm.CommDeviceBuilder;
import com.github.rinde.rinsim.core.model.comm.CommUser;
import com.github.rinde.rinsim.core.model.comm.Message;
import com.github.rinde.rinsim.core.model.road.CollisionGraphRoadModel;
import com.github.rinde.rinsim.core.model.road.DeadlockException;
import com.github.rinde.rinsim.core.model.road.MovingRoadUser;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.time.TickListener;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

class AGVAgent implements TickListener, MovingRoadUser, CommUser {
	private static int loadingTime = 30;
	private int loadingCountDown;
	private final RandomGenerator rng;
	private Optional<CollisionGraphRoadModel> roadModel;
	private Optional<Point> destination;
	private Optional<CommDevice> device;
	private static double range = 15d;
	private HashMap<String, Proposal> offers = new HashMap<String, Proposal>();
	private Proposal currentContract;
	private Proposal switchedContract;
	
	private Optional<Point> rerouteDestination;
	
	private static int AVGCounter = 0;
	
	ArrayList<CallForProposalMessage> incomingCFPs = new ArrayList<CallForProposalMessage>();
	ArrayList<ProtocolMessage> accepts = new ArrayList<ProtocolMessage>();
	ArrayList<ProtocolMessage> aborts = new ArrayList<ProtocolMessage>();
	
	private Queue<Point> path;
	private int AVGId;
	
	
	public enum AGVState { 
		VOTING, 
		INTENTIONAL,
		SWITCH_INITIATOR,
		EXECUTING 
	};
	AGVState state;

	public enum AGVHeading {
		TO_PICKUP,
		PICKUP_TO_DROP,
		RANDOM,
		REROUTE,
		END_REROUTE
	}
	
	AGVHeading heading, rerouteHeading;

	public AGVState getState() {
		return state;
	}


	AGVAgent(RandomGenerator r) {
		rng = r;
		roadModel = Optional.absent();
		destination = Optional.absent();
		path = new LinkedList<>();
		device = Optional.absent();
		currentContract = null;
		heading = AGVHeading.RANDOM;
		state = AGVState.VOTING;
		rerouteDestination = Optional.absent();
		rerouteHeading = null;
		
		AVGId = AVGCounter;
		AVGCounter++;
	}

	@Override
	public void initRoadUser(RoadModel model) {
		roadModel = Optional.of((CollisionGraphRoadModel) model);
		Point p;
		do {
			p = getRandomDestination().get();
		} while (roadModel.get().isOccupied(p));	
	
		roadModel.get().addObjectAt(this, p);

	}
	


	@Override
	public double getSpeed() {
		return 1;
	}
	
	/**
	 * select random destination that is not within bi-directional region near 
	 * transport agent
	 * 
	 * @return
	 */
	private Optional<Point> getRandomDestination()
	{
		Optional<Point> to;
		do
		{
			to = Optional.of(roadModel.get().getRandomPosition(rng));			
		}
		while (!to.isPresent() 
			|| TransportAgent.isTransportAgentLocation(to));
		
		return to;
	}

	/**
	 * determines next destination based on whether contract is being executed
	 * 
	 */
	void nextDestination() 
	{
		boolean newHeading = false;
		if (currentContract == null)
		{
			heading = AGVHeading.RANDOM;
			destination = getRandomDestination();			
		}
		else
		{
			switch (heading)
			{
			case REROUTE:
				if (!rerouteDestination.isPresent())
					rerouteDestination = destination;
				do
				{
					destination = getRandomDestination();			
				}
				while (rerouteDestination.get().equals(destination.get()));
				//heading = AGVHeading.END_REROUTE;
				break;
			case RANDOM:
				destination = Optional.of(currentContract.getOrigin());
				newHeading = true;
				heading = AGVHeading.TO_PICKUP;
				loadingCountDown = loadingTime;
				break;
				
			case TO_PICKUP:
				if (loadingCountDown == 0)
				{
					destination = Optional.of(currentContract.getDestination());
					sendBound(currentContract);
					newHeading = true;
					heading = AGVHeading.PICKUP_TO_DROP;
					loadingCountDown = loadingTime;
				}
				else
				{
					loadingCountDown--;
				}
				break;

			case PICKUP_TO_DROP:
				if (loadingCountDown == 0)
				{
					destination = getRandomDestination();
					newHeading = true;
					heading = AGVHeading.RANDOM;
					currentContract = null;
				}
				else
				{
					loadingCountDown--;
				}
				break;
			case END_REROUTE:
				destination = rerouteDestination;
				newHeading = true;
				heading = rerouteHeading;
				rerouteDestination = Optional.absent();
				break;
			default:
				break;
			}
		}
		if (!destination.isPresent())
			System.out.println("Uh-oh");
		path = new LinkedList<>(roadModel.get().getShortestPathTo(this,
			destination.get()));

		if (newHeading)
		{
			StringBuilder b = new StringBuilder();
			b.append("[AGV] New heading: { AGV: ");
			b.append(this.AVGId);
			b.append(", contractId: ");
			b.append(currentContract != null ? currentContract.getContractId() : "<none>");
			b.append(", heading: ");
			b.append(heading);
			b.append(", dest: ");
			b.append(destination.get());
			b.append(" }");
			b.append(" [" + getPosition().get() + "]"); 
			
			System.out.println(b.toString());
		}
	}
	
	/**
	 * process all waiting messages
	 * 
	 * build lists of messages of each type, and process separately
	 */
	void processMessages()
	{
		ImmutableList<Message> unread = device.get().getUnreadMessages();
		incomingCFPs.clear();
		accepts.clear();
		
		for (Message m : unread)
		{
			ProtocolMessage contents = (ProtocolMessage) m.getContents();
			switch (contents.getType())
			{
			case CALL_FOR_PROPOSAL:
				incomingCFPs.add((CallForProposalMessage) contents);
				break;
				 
			case PROVISIONAL_ACCEPT:
				accepts.add(contents);
				break;
				 
			case ABORT:
				receiveAbort(contents);
				break;
				
			default:
				break;
				 
			};
		}
		
		receiveCallForProposal();
		receiveProvisionalAccept();
	}
	
	/**
	 * calculate minimum length of path
	 * @param path
	 * @return
	 */
	public static double pathLength(Queue<Point> path)
	{
		double len = 0d;
		Point last = null;
		
		for (Point p : path)
		{
			if (last == null)
			{
				last = p;
				continue;
			}
			else
			{
				len += Math.sqrt(Math.pow(last.x-p.x, 2d) + Math.pow(last.y-p.y, 2d));
				last = p;
			}
		}
		
		return len;
	}

	
	private void reroute(Exception e)
	{
		if (heading != AGVHeading.REROUTE)
		{
			rerouteHeading = heading;
			heading = AGVHeading.REROUTE;

			StringBuilder b = new StringBuilder();
			b.append("[AGV] Reroute: ");
			b.append(AVGId);
			if (e != null)
			{
				b.append(", ");
				b.append(e);
			}
			System.out.println(b.toString());
		}	
		nextDestination();		
	}
	
	
	private boolean destinationBlocked()
	{
		boolean retVal = false;
		
		if (!(heading == AGVHeading.TO_PICKUP || heading == AGVHeading.PICKUP_TO_DROP))
			return retVal;
		
		if (path.size() > 3)
			return retVal;
		
		// if destination is occupied by someone other than this AGV
		if (roadModel.get().isOccupied(destination.get()) 
				&& !roadModel.get().isOccupiedBy(destination.get(), this))
			retVal = true;
		
		return retVal;
	}
	
	
	@Override
	public void tick(TimeLapse timeLapse) 
	{
		processMessages();
		
		if (!destination.isPresent()) {
			nextDestination();
		}
		
		// move; if not possible, reroute;
		// if rerouted, signal end
		try {
			roadModel.get().followPath(this, path, timeLapse);

			// if progress possible under reroute, revert to prior destination
			if (heading == AGVHeading.REROUTE)
			{
				heading = AGVHeading.END_REROUTE;
				nextDestination();
			}
		} catch (DeadlockException e)
		{
			reroute(e);
		}
		catch (IllegalArgumentException i)
		{
			reroute(i);
		}/*
		catch (VerifyException v)
		{
			reroute(v);
		}*/
		
	
		if (roadModel.get().getPosition(this).equals(destination.get())) {
			nextDestination();
			
			if (currentContract == null)
				state = AGVState.VOTING;
				
		}
		else if (destinationBlocked())
		{
			reroute(null);
		}
		
		// send retracted message and move back to ASSIGNED/INTENTIONAL
		if (state == AGVState.SWITCH_INITIATOR)
		{
			sendRetracted(currentContract, false); 
			currentContract = switchedContract;
			
			state = AGVState.INTENTIONAL;
		}
	}

	
	@Override
	public void afterTick(TimeLapse timeLapse) {}

	@Override
	public Optional<Point> getPosition() {
		return Optional.of(roadModel.get().getPosition(this));
	}
	
	@Override
	public void setCommDevice(CommDeviceBuilder builder) {
	    if (range >= 0) {
	        builder.setMaxRange(range);
	      }	    device = Optional.of(builder
	    	      .build());		
	}
	
	/**
	 * send a proposal for all waiting calls
	 */
	void receiveCallForProposal()
	{
		// if we are currently bound to a contract, or there are no incomingCFPs do nothing
		if (state == AGVState.EXECUTING || incomingCFPs.isEmpty())
			return;
		
		double len;
		
		// determine the cost of the proposal
		for (CallForProposalMessage cfp : incomingCFPs)
		{
			len = getContractCost(cfp);
			
			sendProposal(cfp, len);
		}
	}
	
	double getContractCost(ProtocolMessage pm)
	{
		Queue<Point> currToPickup = new LinkedList<>(roadModel.get()
				.getShortestPathTo(this, pm.getOrigin()));
		Queue<Point> pickupToDelivery = new LinkedList<>(roadModel.get()
				.getShortestPathTo(pm.getOrigin(), pm.getDestination()));
		return AGVAgent.pathLength(currToPickup) + AGVAgent.pathLength(pickupToDelivery);
		
	}
	
	void receiveProvisionalAccept()
	{
		boolean newContract = false;
		
		// if we are currently bound to a contract, or there are no incomingCFPs do nothing
		if (state == AGVState.EXECUTING || 
			state == AGVAgent.AGVState.SWITCH_INITIATOR || 
			accepts.isEmpty())
			return;

		double len, minCost = Double.MAX_VALUE;
		ProtocolMessage chosen = null;
		
		// accept only the easiest one, reject the others
		for (ProtocolMessage pa : accepts)
		{
			len = getContractCost(pa);
			minCost = Math.min(len, minCost);
			if (len == minCost)
				chosen = pa; 
		}
		
		
		// if idle, switch to best contract
		if (state == AGVAgent.AGVState.VOTING)
		{
			state = AGVAgent.AGVState.INTENTIONAL;			
			currentContract = offers.get(chosen.getContractId());
			
			newContract = true;
			accepts.remove(chosen);
		}
		// we are in INTENTIONAL state; consider switching
		else if (!(chosen.getContractId().equals(currentContract.getContractId()))
			&& minCost < getContractCost(currentContract))
		{
			state = AGVAgent.AGVState.SWITCH_INITIATOR;

			switchedContract = offers.get(chosen.getContractId());

			//newContract = true;
			accepts.remove(chosen);
		}
		// best contract is current one
		else if (chosen.getContractId().equals(currentContract.getContractId()))
		{
			accepts.remove(chosen);
		}
		
		// retract all the others we received
		for (ProtocolMessage pa : accepts)
		{
			sendRetracted(pa, true);
		}
		
		if (newContract)
		{
			// start heading towards new pickup point
			//heading = AGVHeading.TO_PICKUP;
		}
	}
	
	void receiveAbort(ProtocolMessage abort)
	{
		if (currentContract != null &&
			abort.getContractId().equals(currentContract.getContractId())
			&& state == AGVState.EXECUTING)
		{
			sendRefuseAbort(abort);
		}
		else
		{
			sendAcceptAbort(abort);
		}
	}
	
	/**
	 * send proposed cost to sender of CFP
	 * 
	 * @param cfp
	 * @param cost
	 */
	void sendProposal(CallForProposalMessage cfp, double cost)
	{
		// if proposal has been sent before, update cost
		Proposal p = offers.get(cfp.getContractId());
		if (p != null)
		{
			p.setCost(cost);
		}
		else // create new proposal
		{
			p = new Proposal(this, cfp, AVGId, cost);
			offers.put(cfp.getContractId(), p);

			StringBuilder b = new StringBuilder();
			b.append("[AGV] Proposal: { AGV: ");
			b.append(this.AVGId);
			b.append(", ");
			b.append(p);
			b.append(" }");
			b.append(" [" + getPosition().get() + "]"); 
			
			System.out.println(b.toString());
		}
		device.get().send(p, cfp.getSender());
	}
	
	void sendRetracted(ProtocolMessage cfp, boolean isResponse)
	{
		ProtocolMessage p = new ProtocolMessage(this, ProtocolMessage.MessageType.RETRACTED, cfp, isResponse);
		device.get().send(p, p.getReceiver());
		
		// reset contract if needed
		if (cfp.getContractId().equals(currentContract.getContractId()))
		{
			currentContract = null;
		}
		
		StringBuilder b = new StringBuilder();
		b.append("[AGV] Retracted: { AGV: ");
		b.append(this.AVGId);
		b.append(", ");
		b.append(p);
		b.append(" }");
		b.append(" [" + getPosition().get() + "]"); 
		
		System.out.println(b.toString());
		
	}
	
	/**
	 * send bound message to transport agent
	 * 
	 * @param cfp
	 */
	void sendBound(ProtocolMessage cfp)
	{
		ProtocolMessage p = new ProtocolMessage(this, ProtocolMessage.MessageType.BOUND, cfp, false);
		device.get().send(p, p.getReceiver());
		
		state = AGVAgent.AGVState.EXECUTING;

		StringBuilder b = new StringBuilder();
		b.append("[AGV] Send Bound: { AGV: ");
		b.append(AVGId);
		b.append(", ");
		b.append(p);
		b.append(" }");
		b.append(" [" + getPosition().get() + "]"); 
		
		System.out.println(b.toString());
	
	}
	
	/**
	 * send ACCEPT_ABORT to transport agent
	 * 
	 * @param abort
	 */
	void sendAcceptAbort(ProtocolMessage abort)
	{
		ProtocolMessage p = new ProtocolMessage(this, ProtocolMessage.MessageType.ACCEPT_ABORT, abort, true);
		device.get().send(p, abort.getSender());

		StringBuilder b = new StringBuilder();
		b.append("[AGV] Send AcceptAbort: { AGV: ");
		b.append(AVGId);
		b.append(", ");
		b.append(p);
		b.append(" }");
		b.append(" [" + getPosition().get() + "]"); 
		
		System.out.println(p.toString());
				
	}
	
	void sendRefuseAbort(ProtocolMessage abort)
	{
		ProtocolMessage p = new ProtocolMessage(this, ProtocolMessage.MessageType.REFUSE_ABORT, abort, true);
		device.get().send(p, abort.getSender());
		
		StringBuilder b = new StringBuilder();
		b.append("[AGV] Send RefuseAbort: { AGV: ");
		b.append(AVGId);
		b.append(", ");
		b.append(abort);
		b.append(" }");
		b.append(" [" + getPosition().get() + "]"); 
		
		System.out.println(p.toString());	
	}


	/**
	 * @return the aVGId
	 */
	public int getAVGId() {
		return AVGId;
	}


	/**
	 * @return the heading
	 */
	public AGVHeading getHeading() {
		return heading;
	}



}
