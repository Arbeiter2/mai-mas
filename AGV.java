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
import java.util.LinkedList;
import java.util.Queue;
import org.apache.commons.math3.random.RandomGenerator;
import com.github.rinde.rinsim.core.model.comm.CommDevice;
import com.github.rinde.rinsim.core.model.comm.CommDeviceBuilder;
import com.github.rinde.rinsim.core.model.comm.CommUser;
import com.github.rinde.rinsim.core.model.road.CollisionGraphRoadModel;
import com.github.rinde.rinsim.core.model.road.DeadlockException;
import com.github.rinde.rinsim.core.model.road.MoveProgress;
import com.github.rinde.rinsim.core.model.road.MovingRoadUser;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.time.TickListener;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.base.Optional;

class AGV implements TickListener, MovingRoadUser, CommUser, CNPUser {
	private static int loadingTime = 30;
	private static int moveDeadAGVTime = 90;
	private long timeIndex = 0;

	private int loadingCountDown, moveDeadAGVCountdown;
	private final RandomGenerator rng;
	private Optional<CollisionGraphRoadModel> roadModel;
	private Optional<Point> destination;
	private Optional<CommDevice> device;
	private static double range = 15d;
	
	private Optional<Point> rerouteDestination;
	
	// handles PDP requests
	CNPAGVAgent deliveryAgent;
	
	private static int AVGCounter = 0;
	

	private Queue<Point> path;
	private Optional<Point> initialPosition;
	private int AVGId;
	
	private static final double CHARGE_CAPACITY = 600d;
	private static final double CHARGE_PER_METRE_EMPTY = 0.2d;
	private static final double CHARGE_PER_METRE_LOADED = 0.5d;
	private static final double RECHARGE_RATE = 0.5d;
	private static final double CRITICAL_CHARGE_LEVEL = 60d;
	
	private double chargeLevel = CHARGE_CAPACITY;
	private boolean hasPackage;
	
	
	
	public enum AGVHeading {
		TO_PICKUP,
		PICKUP_TO_DROP,
		RANDOM,
		REROUTE,
		END_REROUTE,
		TO_CHARGING,
		CHARGING,
		BATTERY_DEAD
	}
	
	AGVHeading heading, rerouteHeading;

	public ContractNet.AGVState getState() {
		return deliveryAgent.getState();
	}
	
	public CommUser getCommUser()
	{
		return (CommUser) this;
	}



	AGV(RandomGenerator r, Point loc) {
		rng = r;
		roadModel = Optional.absent();
		destination = Optional.absent();
		initialPosition = Optional.of(loc);
		path = new LinkedList<>();
		device = Optional.absent();
		heading = AGVHeading.RANDOM;
		rerouteDestination = Optional.absent();
		rerouteHeading = null;
		moveDeadAGVCountdown = 0;
		hasPackage = false;
		deliveryAgent = new CNPAGVAgent(this);
		
		AVGId = AVGCounter;
		AVGCounter++;
	}

	@Override
	public void initRoadUser(RoadModel model) {
		roadModel = Optional.of((CollisionGraphRoadModel) model);
		
		Point p;
		if (initialPosition.isPresent())
		{
			p = initialPosition.get();
		}
		else
		{
			do {
				p = getRandomDestination().get();
			} while (roadModel.get().isOccupied(p));
		}
		roadModel.get().addObjectAt(this, p);

	}
	


	@Override
	public double getSpeed() 
	{
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
			|| PDPStation.isTransportAgentLocation(to)
			|| ChargeStation.isChargeStationLocation(to));
		
		return to;
	}
	
	private void addCharge(TimeLapse timeLapse)
	{
		if (heading == AGV.AGVHeading.CHARGING 
		 && chargeLevel < AGV.CHARGE_CAPACITY)
			chargeLevel = Math.min(CHARGE_CAPACITY, 
				chargeLevel + timeLapse.getTickLength()/1000 * RECHARGE_RATE);
	}
	
	public boolean hasPackage()
	{
		return hasPackage;
	}
	
	private void depleteCharge(double distance)
	{
		if (heading != AGV.AGVHeading.CHARGING) 
			chargeLevel -= (!hasPackage ? 
				CHARGE_PER_METRE_EMPTY : CHARGE_PER_METRE_LOADED) * distance;
		chargeLevel = Math.max(0, chargeLevel);
	}
	

	private void headingMessage()
	{
		StringBuilder b = new StringBuilder();
		b.append("[AGV] New heading: { AGV: ");
		b.append(this.AVGId);
		b.append(", contractId: ");
		b.append(deliveryAgent.getContract() != null ? deliveryAgent.getContract().getContractId() : "<none>");
		b.append(", heading: ");
		b.append(heading);
		b.append(", dest: ");
		b.append(destination.get());
		b.append(", charge: ");
		b.append(chargeLevel);
		b.append(" }");
		b.append(" [" + getPosition().get() + "]"); 
		System.out.println(b.toString());
	}

	/**
	 * determines next destination based on whether contract is being executed
	 * 
	 */
	void nextDestination() 
	{
		boolean newHeading = false;
		
		// if battery is dead, AGV remains in place for a few ticks
		// until moved to ChargingStation
		if (heading == AGVHeading.BATTERY_DEAD)
		{
			if (moveDeadAGVCountdown == 0)
			{
				moveDeadAGVCountdown = moveDeadAGVTime;
			}
			else
			{
				moveDeadAGVCountdown--;
				if (moveDeadAGVCountdown == 0)
				{
					Optional<Point> chargeLoc = ChargeStation.findNearestChargeStation(this).getPosition();
					roadModel.get().removeObject(this);
					roadModel.get().addObjectAt(this, chargeLoc.get());
					destination = chargeLoc;
					heading = AGVHeading.CHARGING;
					headingMessage();
				}
			}
		}
		else if (deliveryAgent.getContract() == null)
		{
			// if charged, get random destination
			if (heading == AGVHeading.CHARGING)
			{
				if (chargeLevel == CHARGE_CAPACITY)
				{
					heading = AGVHeading.RANDOM;
					destination = getRandomDestination();
				}
			}
			else if (heading == AGVHeading.RANDOM 
					&& chargeLevel <= AGV.CRITICAL_CHARGE_LEVEL)
			{
				heading = AGVHeading.TO_CHARGING;
				ChargeStation c = ChargeStation.findNearestChargeStation(this);
				newHeading = true;
				destination = c.getPosition();			
			}
			else if (heading == AGVHeading.TO_CHARGING)
			{
				heading = AGVHeading.CHARGING;
			}
			else
			{
				heading = AGVHeading.RANDOM;
				destination = getRandomDestination();
				newHeading = true;
			}
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
				destination = Optional.of(deliveryAgent.getContract().getOrigin());
				newHeading = true;
				heading = AGVHeading.TO_PICKUP;
				loadingCountDown = loadingTime;
				break;
				
			case TO_PICKUP:
				if (loadingCountDown == 0)
				{
					destination = Optional.of(deliveryAgent.getContract().getDestination());
					deliveryAgent.pickupComplete();
					newHeading = true;
					hasPackage = true;
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
					DeliveryRecorder.setDeliveryDropoffTime(deliveryAgent.getContract().getContractId(), timeIndex);

					destination = getRandomDestination();
					newHeading = true;
					hasPackage = false;
					heading = AGVHeading.RANDOM;
					deliveryAgent.clear();
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
			headingMessage();
		}
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
		if (heading == AGVHeading.TO_CHARGING)
		{
			heading = AGVHeading.RANDOM;
		}
		else if (heading != AGVHeading.REROUTE)
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
	
	public boolean validateContract(ProtocolMessage pm)
	{
		double batteryUse = getContractBatteryUse(pm);
		
		// if doing contract would leave AGV dead, ignore 
		if (chargeLevel - batteryUse < CRITICAL_CHARGE_LEVEL * 1.1)
			return false;
		else
			return true;
		
	}

	private boolean destinationBlocked()
	{
		boolean retVal = false;
		
		if (!(heading == AGVHeading.TO_PICKUP || 
			  heading == AGVHeading.PICKUP_TO_DROP || 
			  heading == AGVHeading.TO_CHARGING ))
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
		timeIndex = timeLapse.getEndTime()/1000;
		
		// don't process messages if we are haading for charge station, or while charging
		if (heading != AGV.AGVHeading.TO_CHARGING && heading != AGV.AGVHeading.CHARGING)
			deliveryAgent.processMessages();
		
		
		if (!destination.isPresent()) {
			nextDestination();
		}
		
		// move; if not possible, reroute;
		// if rerouted, signal end
		MoveProgress m = null;
		try {
			m = roadModel.get().followPath(this, path, timeLapse);

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
		
		// handle charging
		if (m != null)
		{
			depleteCharge(m.distance().getValue());
			if (chargeLevel == 0)
			{
				heading = AGVHeading.BATTERY_DEAD;
				if (deliveryAgent.getContract() != null)
				{
					DeliveryRecorder.setDeliveryFailed(deliveryAgent.getContract().getContractId(),
						timeIndex);
					deliveryAgent.clear();
				}
				nextDestination();
			}
		}
		
		if (heading == AGV.AGVHeading.CHARGING)
			addCharge(timeLapse);

		
		if (heading == AGVHeading.RANDOM && chargeLevel <= AGV.CRITICAL_CHARGE_LEVEL)
		{
			StringBuilder b = new StringBuilder();
			b.append("[AGV-Charge] { AGV: ");
			b.append(AVGId);
			b.append(", charge: ");
			b.append(chargeLevel);
			b.append(" }"); 
			
			System.out.println(b.toString());

			//heading = AGVHeading.TO_CHARGING;
			// find nearest available chargestation and head there
			nextDestination();
		}
		
	
		if (roadModel.get().getPosition(this).equals(destination.get())) {
			nextDestination();
			
		}
		else if (destinationBlocked())
		{
			reroute(null);
		}
		
		deliveryAgent.doSwitch();

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
	
	
	@Override
	public double getContractCost(ProtocolMessage pm)
	{
		Queue<Point> currToPickup = new LinkedList<>(roadModel.get()
				.getShortestPathTo(this, pm.getOrigin()));
		Queue<Point> pickupToDelivery = new LinkedList<>(roadModel.get()
				.getShortestPathTo(pm.getOrigin(), pm.getDestination()));
		return AGV.pathLength(currToPickup) + AGV.pathLength(pickupToDelivery);
	}
	
	double getContractBatteryUse(ProtocolMessage pm)
	{
		Queue<Point> currToPickup = new LinkedList<>(roadModel.get()
				.getShortestPathTo(this, pm.getOrigin()));
		Queue<Point> pickupToDelivery = new LinkedList<>(roadModel.get()
				.getShortestPathTo(pm.getOrigin(), pm.getDestination()));
		return AGV.pathLength(currToPickup) * CHARGE_PER_METRE_EMPTY + 
			   AGV.pathLength(pickupToDelivery) * CHARGE_PER_METRE_LOADED;
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

	@Override
	public int getId() {
		
		return this.AVGId;
	}

	@Override
	public Optional<CommDevice> getDevice() {
		return device;
	}


}
