import java.util.ArrayList;
import java.util.HashMap;

import com.github.rinde.rinsim.core.model.comm.Message;
import com.google.common.collect.ImmutableList;



public class ContractNetAGVAgent {

	ArrayList<CallForProposalMessage> incomingCFPs = new ArrayList<CallForProposalMessage>();
	ArrayList<ProtocolMessage> accepts = new ArrayList<ProtocolMessage>();
	ArrayList<ProtocolMessage> aborts = new ArrayList<ProtocolMessage>();
	private HashMap<String, Proposal> offers = new HashMap<String, Proposal>();
	private Proposal currentContract = null;
	private Proposal switchedContract = null;
	ContractNetUser parent = null;
	ContractNet.AGVState deliveryState = ContractNet.AGVState.VOTING;
	
	ContractNetAGVAgent(ContractNetUser agv)
	{
		parent = agv;
	}
	
	public ContractNet.AGVState getState() {
		return deliveryState;
	}
	
	public void doSwitch()
	{
		if (deliveryState == ContractNet.AGVState.SWITCH_INITIATOR)
		{
			sendRetracted(currentContract, false); 
			currentContract = switchedContract;
			
			deliveryState = ContractNet.AGVState.INTENTIONAL;
		}		
	}
	
	public Proposal getContract()
	{
		return currentContract;
	}
	
	
	/**
	 * send a proposal for all waiting calls
	 */
	void receiveCallForProposal()
	{
		// if we are currently bound to a contract, or there are no incomingCFPs do nothing
		if (deliveryState == ContractNet.AGVState.EXECUTING || 
			incomingCFPs.isEmpty())
			return;
		
		double len;
		
		// determine the cost of the proposal
		for (CallForProposalMessage cfp : incomingCFPs)
		{
			len = parent.getContractCost(cfp);
			
			sendProposal(cfp, len);
		}
	}
	
	/**
	 * process list of all received provisional accept messages from TransportAgents
	 * and choose one to execute, including switch from current one
	 */
	void receiveProvisionalAccept()
	{
		// if we are currently bound to a contract, or there are no incomingCFPs do nothing
		if (deliveryState == ContractNet.AGVState.EXECUTING || 
			deliveryState == ContractNet.AGVState.SWITCH_INITIATOR || 
			accepts.isEmpty())
			return;

		double len, minCost = Double.MAX_VALUE;
		ProtocolMessage chosen = null;
		
		// accept only the easiest one, reject the others
		for (ProtocolMessage pa : accepts)
		{
			len = parent.getContractCost(pa);
			minCost = Math.min(len, minCost);
			
			if (!parent.validateContract(pa))
				continue;
			
			if (len == minCost)
				chosen = pa; 
		}
		
		// if AGV rejected all contracts, go to charging point
		// first retract all the others we received
		if (chosen == null)
		{
			//if (deliveryState == ContractNet.AGVState.VOTING)
			//	heading = AGVAgent.AGVHeading.TO_CHARGING;
			
			for (ProtocolMessage pa : accepts)
			{
				sendRetracted(pa, true);
			}
			return;
		}
		
		
		// if idle, switch to best contract
		if (deliveryState == ContractNet.AGVState.VOTING)
		{
			deliveryState = ContractNet.AGVState.INTENTIONAL;			
			currentContract = offers.get(chosen.getContractId());
			
			accepts.remove(chosen);
		}
		// we are in INTENTIONAL state; consider switching
		else if (!(chosen.getContractId().equals(currentContract.getContractId()))
			&& minCost < parent.getContractCost(currentContract))
		{
			deliveryState = ContractNet.AGVState.SWITCH_INITIATOR;

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
	}
	
	void receiveAbort(ProtocolMessage abort)
	{
		if (currentContract != null &&
			abort.getContractId().equals(currentContract.getContractId())
			&& deliveryState == ContractNet.AGVState.EXECUTING)
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
			return;
		}
		else // create new proposal
		{
			p = new Proposal(parent.getCommUser(), cfp, parent.getId(), cost);
			offers.put(cfp.getContractId(), p);

			StringBuilder b = new StringBuilder();
			b.append("[AGV] Proposal: { AGV: ");
			b.append(parent.getId());
			b.append(", ");
			b.append(p);
			b.append(" }");
			b.append(" [" + parent.getPosition().get() + "]"); 
			
			System.out.println(b.toString());
		}
		parent.getDevice().get().send(p, cfp.getSender());
	}
	
	void sendRetracted(ProtocolMessage cfp, boolean isResponse)
	{
		ProtocolMessage p = new ProtocolMessage(parent.getCommUser(), ProtocolMessage.MessageType.RETRACTED, cfp, isResponse);
		parent.getDevice().get().send(p, p.getReceiver());
		
		// reset contract if needed
		if (currentContract != null &&
			cfp.getContractId().equals(currentContract.getContractId()))
		{
			currentContract = null;
		}
		
		StringBuilder b = new StringBuilder();
		b.append("[AGV] Retracted: { AGV: ");
		b.append(parent.getId());
		b.append(", ");
		b.append(p);
		b.append(" }");
		b.append(" [" + parent.getPosition().get() + "]"); 
		
		System.out.println(b.toString());
		
	}
	
	/**
	 * send bound message to transport agent
	 * 
	 * @param cfp
	 */
	void sendBound(ProtocolMessage cfp)
	{
		ProtocolMessage p = new ProtocolMessage(parent.getCommUser(), ProtocolMessage.MessageType.BOUND, cfp, false);
		parent.getDevice().get().send(p, p.getReceiver());
		
		deliveryState = ContractNet.AGVState.EXECUTING;

		StringBuilder b = new StringBuilder();
		b.append("[AGV] Send Bound: { AGV: ");
		b.append(parent.getId());
		b.append(", ");
		b.append(p);
		b.append(" }");
		b.append(" [" + parent.getPosition().get() + "]"); 
		
		System.out.println(b.toString());
	
	}
	
	/**
	 * send ACCEPT_ABORT to transport agent
	 * 
	 * @param abort
	 */
	void sendAcceptAbort(ProtocolMessage abort)
	{
		ProtocolMessage p = new ProtocolMessage(parent.getCommUser(), ProtocolMessage.MessageType.ACCEPT_ABORT, abort, true);
		parent.getDevice().get().send(p, abort.getSender());

		StringBuilder b = new StringBuilder();
		b.append("[AGV] Send AcceptAbort: { AGV: ");
		b.append(parent.getId());
		b.append(", ");
		b.append(p);
		b.append(" }");
		b.append(" [" + parent.getPosition().get() + "]"); 
		
		System.out.println(p.toString());
				
	}
	
	void sendRefuseAbort(ProtocolMessage abort)
	{
		ProtocolMessage p = new ProtocolMessage(parent.getCommUser(), ProtocolMessage.MessageType.REFUSE_ABORT, abort, true);
		parent.getDevice().get().send(p, abort.getSender());
		
		StringBuilder b = new StringBuilder();
		b.append("[AGV] Send RefuseAbort: { AGV: ");
		b.append(parent.getId());
		b.append(", ");
		b.append(abort);
		b.append(" }");
		b.append(" [" + parent.getPosition().get() + "]"); 
		
		System.out.println(p.toString());	
	}
	
	/**
	 * process all waiting messages
	 * 
	 * build lists of messages of each type, and process separately
	 */
	public void processMessages()
	{
		ImmutableList<Message> unread = parent.getDevice().get().getUnreadMessages();
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
	
	public void pickupComplete()
	{
		if (currentContract == null)
			return;
		
		sendBound(currentContract);
	}

	public void clear()
	{
		currentContract = null;
		deliveryState = ContractNet.AGVState.VOTING;
	}	
	
	/**
	 * @return the currentContract
	 */
	public final Proposal getCurrentContract() {
		return currentContract;
	}
	
}
