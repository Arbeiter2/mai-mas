import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

import org.apache.commons.math3.random.RandomGenerator;

import com.github.rinde.rinsim.core.model.comm.CommDevice;
import com.github.rinde.rinsim.core.model.comm.CommDeviceBuilder;
import com.github.rinde.rinsim.core.model.comm.CommUser;
import com.github.rinde.rinsim.core.model.road.CollisionGraphRoadModel;
import com.github.rinde.rinsim.core.model.road.MovingRoadUser;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.time.TickListener;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.base.Optional;

public class ChargeStation implements TickListener, CommUser, MovingRoadUser {

	// save all agents
	private static ArrayList<ChargeStation> allChargeStations = new ArrayList<ChargeStation>();
	
	private final RandomGenerator rng;
	private Optional<Point> location;
	private Optional<CollisionGraphRoadModel> roadModel;
	private Optional<CommDevice> device;
	private final double range = 12.0;

	private int chargeStationId;
	private Point extent;
	
	private boolean occupied;
	
	ChargeStation(RandomGenerator r, Point loc, Point limit) {
		rng = r;
		roadModel = Optional.absent();
		device = Optional.absent();
		location = Optional.of(loc);
		extent = limit;
		allChargeStations.add(this);
		
		chargeStationId = allChargeStations.size();
	}	


	@Override
	public void initRoadUser(RoadModel model) {
		roadModel = Optional.of((CollisionGraphRoadModel) model);
	}

	@Override
	public double getSpeed() {
		return 0;
	}

	@Override
	public Optional<Point> getPosition() {
		// TODO Auto-generated method stub
		return location;
	}

	@Override
	public void setCommDevice(CommDeviceBuilder builder) {
	    if (range >= 0) {
	        builder.setMaxRange(range);
	      }
	      device = Optional.of(builder
	        .setReliability(1d)
	        .build());		
	}
	
	public int getID()
	{
		return chargeStationId;
	}

	@Override
	public void tick(TimeLapse timeLapse) {
		occupied = (roadModel.get().isOccupied(location.get()));
		
	}
	
	public boolean isOccupied()
	{
		return occupied;
	}

	@Override
	public void afterTick(TimeLapse timeLapse) {
		
	}
	
	public static boolean isChargeStationLocation(Optional<Point> location)
	{
		if (!location.isPresent())
			return false;
		
		for (ChargeStation t : allChargeStations)
		{
			Point limit = new Point(t.location.get().x + t.extent.x, 
					t.location.get().y + t.extent.y);
			
			if (location.get().x >= Math.min(t.location.get().x, limit.x) 
			&&  location.get().x <= Math.max(t.location.get().x, limit.x)
			&&  location.get().y >= Math.min(t.location.get().y, limit.y)
			&&  location.get().y <= Math.max(t.location.get().y, limit.y))
				return true;
		}
		return false;
	}
	
	/**
	 * find nearest unoccupied charge station
	 * 
	 * @param p
	 * @return
	 */
	public static ChargeStation findNearestChargeStation(CommUser m)
	{
		ChargeStation c = null;
		double dist, minDist = Double.MAX_VALUE;
		
		for (ChargeStation cs : allChargeStations)
		{
			if (cs.isOccupied())
				continue;
			Queue<Point> path = new LinkedList<>(cs.roadModel.get()
					.getShortestPathTo(m.getPosition().get(), cs.getPosition().get()));
			dist = AGV.pathLength(path);
			if (dist < minDist)
			{
				minDist = dist;
				c = cs;
			}
		}
		
		return c;
	}


	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ChargeStation [chargeStationId=");
		builder.append(chargeStationId);
		builder.append(", location=");
		builder.append(location);
		builder.append(", extent=");
		builder.append(extent);
		builder.append(", occupied=");
		builder.append(occupied);
		builder.append("]");
		return builder.toString();
	}	

}
