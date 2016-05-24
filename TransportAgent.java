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

public class TransportAgent implements TickListener, CommUser, RoadUser {

	// save all agents
	private static ArrayList<TransportAgent> allTransportAgents = new ArrayList<TransportAgent>();
	
	private final RandomGenerator rng;
	private Optional<Point> location;
	private double range;
	private Optional<CollisionGraphRoadModel> roadModel;
	private Optional<CommDevice> device;
	private int inboxSize = -1;
	private int outboxSize = -1;
	private int transportAgentId;
	private static final double cfpProbability = 0.001;
	
	public enum TransportAgentState { 
		AWARDING, 
		ASSIGNED, 
		EXECUTING,
		ABORTING,
		WAITING_TO_ABORT
	};

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
	HashMap<String, TransportAgentState> state = new HashMap<String, TransportAgentState>();

	/**
	 * extra padding for delivery bay
	 */
	private Point extent;

	TransportAgent(RandomGenerator r, Point loc, Point limit) {
		rng = r;
		range = 12d;
		roadModel = Optional.absent();
		device = Optional.absent();
		location = Optional.of(loc);
		extent = limit;
		
		allTransportAgents.add(this);
		
		transportAgentId = allTransportAgents.size();
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
	public static TransportAgent getDestination(TransportAgent origin)
	{
		TransportAgent retVal = null;
		
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
		TransportAgentState cfpState = state.get(contractId);
		if (cfpState == null)
			return;
		
		Proposal currentContract = acceptedProposals.get(contractId);
		
		// if retract is from AGV to which contract was previously assigned,
		// set task back to AWARDING
		if (currentContract != null && cfpState == TransportAgentState.ASSIGNED 
				&& currentContract.getMessageId() == pm.getPreviousMessageId())
		{
			state.put(contractId, TransportAgentState.AWARDING);
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
		
		// create a contract Id
		String contractId = RandomStringUtils.randomAlphanumeric(16);
		
		// choose a destination
		TransportAgent destination = getDestination(this);
		if (destination == this)
			return null;
		
		CallForProposalMessage retVal = new CallForProposalMessage(this, 
			contractId, location.get(), destination.location.get());

		StringBuilder b = new StringBuilder();
		b.append("[TA]  CFP: sender: TA: ");
		b.append(this.transportAgentId);
		b.append(", ");
		b.append(retVal);
		
		System.out.println(b.toString());		
		calls.put(contractId, retVal);
		state.put(contractId, TransportAgentState.AWARDING);
		
		return retVal;
	}
	

	/**
	 * indicate job has started being executed
	 * 
	 * @param m
	 */
	private void setBound(ProtocolMessage pm)
	{
		if (pm.getType() != ProtocolMessage.MessageType.BOUND)
			return;
		
		// validate contractId
		String contractId = pm.getContractId();
		Proposal p = acceptedProposals.get(contractId);
		if (p == null)
			return;
		
		
		StringBuilder b = new StringBuilder();
		b.append("[TA]  Rcvd Bound: { TA: :");
		b.append(transportAgentId);
		b.append(", { ");
		b.append(p);
		b.append(" }}");
		System.out.println(b.toString());
		
		state.put(pm.getContractId(), TransportAgentState.EXECUTING);
	}
	
	/**
	 * send an abort to the AGV currently assigned this contractId
	 * 
	 * @param contractId
	 */
	private void sendAbort(ProtocolMessage pm)
	{
		if (pm == null)
			return;
		
		String contractId = pm.getContractId();
		Proposal p = acceptedProposals.get(contractId);
		if (p == null)
			return;
		
		// create and send abort message from the currently accepted proposal 
		ProtocolMessage abort = new ProtocolMessage(this,
				ProtocolMessage.MessageType.ABORT, p, true);
		
		// delete currently accepted proposal
		acceptedProposals.put(contractId, null);		

		StringBuilder b = new StringBuilder();
		b.append("[TA]  Send Abort: { TA: :");
		b.append(transportAgentId);
		b.append(", { ");
		b.append(p);
		b.append(" }}");
		System.out.println(b.toString());
		
		device.get().send(abort, pm.getSender());
	}


	/**
	 * send provisional accept for incoming proposal
	 * 
	 * @param p
	 */
	private void sendProvisionalAccept(Proposal p)
	{
		if (p == null)
			return;
		
		String contractId = p.getContractId();
		ProtocolMessage accept = new ProtocolMessage(this, 
				ProtocolMessage.MessageType.PROVISIONAL_ACCEPT, p, true);
		
		// record the currently accepted proposal
		acceptedProposals.put(contractId, p);

		StringBuilder b = new StringBuilder();
		b.append("[TA]  Send Accept: { TA: ");
		b.append(transportAgentId);
		b.append(", {");
		b.append(p);
		b.append(" }}");
		System.out.println(b.toString());
		
		device.get().send(accept, accept.getReceiver());
	}

	
	/**
	 * if abort confirmed, reset status of job
	 * 
	 * @param contractId
	 */
	private void receiveAcceptAbort(ProtocolMessage pm)
	{
		if (pm == null || pm.getType() != ProtocolMessage.MessageType.ACCEPT_ABORT)
			return;
		
		String contractId = pm.getContractId();
		Proposal p = acceptedProposals.get(contractId);
		if (p == null)
			return;

		StringBuilder b = new StringBuilder();
		b.append("[TA]  Recv Accept: { TA: :");
		b.append(transportAgentId);
		b.append(", {");
		b.append(p);
		b.append(" }}");
		System.out.println(b.toString());
		
		// reset contract to unassigned
		switch (state.get(contractId))
		{
		case ASSIGNED:
			// remove 
			acceptedProposals.put(contractId, null);
			state.put(contractId, TransportAgentState.AWARDING);

			break;

		case WAITING_TO_ABORT:
			Proposal newProposal = switchedProposals.get(contractId);
			
			// accept previously saved one
			acceptedProposals.put(contractId, newProposal);
			sendProvisionalAccept(newProposal);
			state.put(contractId, TransportAgentState.ASSIGNED);

			break;
			
		default:
			break;
		};
	}	
	
	/**
	 * process all waiting messages
	 * 
	 * build lists of messages of each type, and process separately
	 */
	void processMessages()
	{
		ImmutableList<Message> unread = device.get().getUnreadMessages();
		incomingProposals.clear();
		
		for (Message m : unread)
		{
			ProtocolMessage contents = (ProtocolMessage) m.getContents();
			switch (contents.getType())
			{
			case PROPOSAL:
				addProposal((Proposal) contents);
				break;
				 
			case RETRACTED:
				processRetract(contents);
				break;
				 
			case BOUND:
				setBound(contents);
				break;
				
			case ACCEPT_ABORT:
				receiveAcceptAbort(contents);
				break;
				
			case REFUSE_ABORT:
				receiveRefuseAbort(contents);
				break;
				
			default:
				break;
				 
			};
		}
		
		processProposals();
	}
	
	void processAbortSwitch()
	{
		for (String contractId : acceptedProposals.keySet())
		{
			Proposal p = acceptedProposals.get(contractId);
			if (state.get(contractId) == TransportAgentState.ABORTING)
			{
				// send an abort to the currently assigned AGV
				sendAbort(p);

				state.put(contractId, TransportAgentState.WAITING_TO_ABORT);			
			}
		}
	}

	
	void receiveRefuseAbort(ProtocolMessage pm)
	{
		if (pm == null || pm.getType() != ProtocolMessage.MessageType.REFUSE_ABORT)
			return;
		
		for (String contractId : acceptedProposals.keySet())
		{
			if (state.get(contractId) == TransportAgentState.WAITING_TO_ABORT)
			{
				state.put(contractId, TransportAgentState.EXECUTING);			
			}
		}
	}
	

	
	/**
	 * process incoming Proposals from AGVAgents
	 * 
	 * if better proposal for a currently assigned contract is available, switch to new one
	 */
	void processProposals()
	{
		Proposal best, current;
		boolean newProposal;
		
		for (String contractId : incomingProposals.keySet())
		{
			ArrayList<Proposal> pList = incomingProposals.get(contractId);
			newProposal = false;

			// nop if no proposals for this contract
			if (pList == null || pList.size() == 0)
				continue;
			
			TransportAgentState currentState = state.get(contractId);
			
			// unknown contractId
			if (currentState == null)
				continue;

			// ignore bound contracts
			if (currentState == TransportAgentState.EXECUTING 
			 || currentState == TransportAgentState.ABORTING
			 || currentState == TransportAgentState.WAITING_TO_ABORT)
				continue;

			current = acceptedProposals.get(contractId);
			double minCost = (current == null ? Double.MAX_VALUE : current.getCost());
			best = null;

			for (Proposal p : pList)
			{
				if (p.getCost() < minCost)
				{
					minCost = p.getCost();
					best = p;
				}
			}
			
			// no improvements on current proposal
			if (best == null)
				continue;
			
			// choose the best one if we are idle
			if (current == null)
			{
				newProposal = true;
				state.put(contractId, TransportAgentState.ASSIGNED);
				
				// send accept message
				sendProvisionalAccept(best);
			}
			// otherwise we replace the current one if the new one is better, and different
			else if (minCost < current.getCost() 
				&& best.getAVGId() != current.getAVGId())
			{
				newProposal = true;
				state.put(contractId, TransportAgentState.ABORTING);
				
				switchedProposals.put(contractId, best);
			}
			
			// abort all remaining proposals for this contractId
			if (newProposal)
				pList.remove(best);
			
			for (Proposal p : pList)
			{
				sendAbort(p);
			}
		}
	}
	
	@Override
	public void tick(TimeLapse timeLapse) 
	{
		processMessages();
		
		// create a CFP, randomly
		createCFP();
		
		broadcastCFPs();
	}
	
	void broadcastCFPs()
	{
		TransportAgentState s;
		CallForProposalMessage cfp;
		
		for (String contractID : state.keySet())
		{
			s = state.get(contractID);
			if (s == TransportAgentState.ASSIGNED || s == TransportAgentState.AWARDING )
			{
				cfp = calls.get(contractID);
				device.get().broadcast(cfp);
			}
		}
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
		for (TransportAgent t : allTransportAgents)
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
}
