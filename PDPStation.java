import java.util.ArrayList;
import java.util.HashMap;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.math3.random.RandomGenerator;

import com.github.rinde.rinsim.core.model.comm.CommDevice;
import com.github.rinde.rinsim.core.model.comm.CommDeviceBuilder;
import com.github.rinde.rinsim.core.model.comm.CommUser;
import com.github.rinde.rinsim.core.model.comm.Message;
import com.github.rinde.rinsim.core.model.road.CollisionGraphRoadModel;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.road.RoadUser;
import com.github.rinde.rinsim.core.model.time.TickListener;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

public class PDPStation 
	implements TickListener, CommUser, RoadUser, ContractNetUser {

	// save all agents
	private static ArrayList<PDPStation> allTransportAgents = new ArrayList<PDPStation>();
	
	private final RandomGenerator rng;
	private Optional<Point> location;
	private double range;
	private Optional<CollisionGraphRoadModel> roadModel;
	private Optional<CommDevice> device;
	private int inboxSize = -1;
	private int outboxSize = -1;
	private int transportAgentId;
	private long timeIndex = 0;
	ContractNetTransportAgent transportAgent;
	private static final double cfpProbability = 0.001;
	
	/**
	 * all previous and active calls for proposals
	 */
	HashMap<String, CallForProposalMessage> calls = new HashMap<String, CallForProposalMessage>();
	
	/**
	 * proposals newly arrived from AGVs
	 */
	HashMap<String, ArrayList<Proposal>> incomingProposals = new HashMap<String, ArrayList<Proposal>>();
	
	
	/**
	 * all previous and active proposals
	 */
	HashMap<String, Proposal> acceptedProposals = new HashMap<String, Proposal>();

	/**
	 * all previous and active proposals
	 */
	HashMap<String, Proposal> switchedProposals = new HashMap<String, Proposal>();	
	
	/**
	 * the state of all previous and active calls
	 */
	HashMap<String, ContractNet.TransportAgentState> state = new HashMap<String, ContractNet.TransportAgentState>();

	/**
	 * extra padding for delivery bay
	 */
	private Point extent;

	PDPStation(RandomGenerator r, Point loc, Point limit) {
		rng = r;
		range = 12d;
		roadModel = Optional.absent();
		device = Optional.absent();
		location = Optional.of(loc);
		extent = limit;
		
		allTransportAgents.add(this);
		
		transportAgentId = allTransportAgents.size();
		transportAgent = new ContractNetTransportAgent(this);
	}

	@Override
	public void initRoadUser(RoadModel model) {
		roadModel = Optional.of((CollisionGraphRoadModel) model);
		Point p;
		do {
			p = model.getRandomPosition(rng);
		} while (roadModel.get().isOccupied(p));	
	
		roadModel.get().addObjectAt(this, p);

	}	
	/**
	 * choose a random destination TransportAgent other than the origin point provided
	 * 
	 * @param origin
	 * @return
	 */
	public static PDPStation getDestination(PDPStation origin)
	{
		PDPStation retVal = null;
		
		// this only works if there is somewhere else to go;
		// return null for empty list or origin being the only entry
		if (allTransportAgents.isEmpty())
			return retVal;
		
		if (allTransportAgents.size() == 1)
		{
			return allTransportAgents.get(0);
		}
		
		do
		{
			retVal = allTransportAgents.get((int)(Math.random() * allTransportAgents.size()));
		}
		while (retVal == origin);

		return retVal;
	}
	
	public Proposal[] getProposals() {
		return null;
	}


	@Override
	public Optional<Point> getPosition() {
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
	
	private void addProposal(Proposal p)
	{
		ArrayList<Proposal> pList = incomingProposals.get(p);
		if (pList == null)
		{
			pList = new ArrayList<Proposal>();
			incomingProposals.put(p.getContractId(), pList);
		}
		pList.add(p);
	}

	/**
	 * @param pm
	 */
	private void processRetract(ProtocolMessage pm)
	{
		if (pm == null || pm.getType() != ProtocolMessage.MessageType.RETRACTED)
			return;
		
		String contractId = pm.getContractId();
		
		// this is an unknown contractId
		ContractNet.TransportAgentState cfpState = state.get(contractId);
		if (cfpState == null)
			return;
		
		Proposal currentContract = acceptedProposals.get(contractId);
		
		// if retract is from AGV to which contract was previously assigned,
		// set task back to AWARDING
		if (currentContract != null && cfpState == ContractNet.TransportAgentState.ASSIGNED 
				&& currentContract.getMessageId() == pm.getPreviousMessageId())
		{
			state.put(contractId, ContractNet.TransportAgentState.AWARDING);
		}
	}
	
	/**
	 * use the rng to decide whether to create a CFP
	 * 
	 * @return CallForProposalMessage if successful, null otherwise
	 */
	private CallForProposalMessage createCFP()
	{
		if (rng.nextDouble() >= cfpProbability)
			return null;
		
		// choose a destination
		PDPStation destination = getDestination(this);
		if (destination == this)
			return null;
		
		CallForProposalMessage retVal = transportAgent.createCFP(destination.location.get());

		DeliveryRecorder.addDelivery(retVal, timeIndex);
		
		return retVal;
	}
	


	@Override
	public void tick(TimeLapse timeLapse) 
	{
		timeIndex = timeLapse.getEndTime()/1000;
		transportAgent.processMessages();
		
		// create a CFP, randomly
		createCFP();
		
		transportAgent.broadcastCFPs();
	}
	
	
	@Override
	public void afterTick(TimeLapse timeLapse) {
		// TODO Auto-generated method stub
		
	}


	/**
	 * @return the inboxSize
	 */
	public int getInboxSize() {
		return inboxSize;
	}

	/**
	 * @param inboxSize the inboxSize to set
	 */
	public void setInboxSize(int inboxSize) {
		this.inboxSize = inboxSize;
	}

	/**
	 * @return the outboxSize
	 */
	public int getOutboxSize() {
		return outboxSize;
	}

	/**
	 * @param outboxSize the outboxSize to set
	 */
	public void setOutboxSize(int outboxSize) {
		this.outboxSize = outboxSize;
	}

	/**
	 * @return the transportAgentId
	 */
	public int getTransportAgentId() {
		return transportAgentId;
	}

	/**
	 * whether location is a transport agent location
	 * 
	 * @return true if location contains transport agent, false otherwise
	 */
	public static boolean isTransportAgentLocation(Optional<Point> location) {
		for (PDPStation t : allTransportAgents)
		{
			if (!location.isPresent())
				return false;
			
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

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("TransportAgent [transportAgentId=");
		builder.append(transportAgentId);
		builder.append(", location=");
		builder.append(location.get());
		builder.append(", extent=");
		builder.append(extent);
		builder.append("]");
		return builder.toString();
	}

	@Override
	public double getContractCost(ProtocolMessage pm) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getId() {
		return this.transportAgentId;
	}

	@Override
	public boolean validateContract(ProtocolMessage pm) {
		return false;
	}

	@Override
	public Optional<CommDevice> getDevice() {
		return device;
	}

	@Override
	public CommUser getCommUser() {
		return this;
	}
}
