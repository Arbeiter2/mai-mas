import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.lang3.RandomStringUtils;

import com.github.rinde.rinsim.core.model.comm.Message;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.collect.ImmutableList;

public class ContractNetTransportAgent {
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

	ContractNetUser parent = null;
	
	ContractNetTransportAgent(ContractNetUser p)
	{
		parent = p;
	}

	/**
	 * @param p
	 */
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
	public CallForProposalMessage createCFP(Point destination)
	{
		// create a contract Id
		String contractId = RandomStringUtils.randomAlphanumeric(16);
		
		// choose a destination
		if (destination == null || destination.equals(parent.getPosition().get()))
			return null;
		
		CallForProposalMessage retVal = new CallForProposalMessage(parent.getCommUser(), 
			contractId, parent.getPosition().get(), destination);

		StringBuilder b = new StringBuilder();
		b.append("[TA]  CFP: sender: TA: ");
		b.append(this.parent.getId());
		b.append(", ");
		b.append(retVal);
		
		System.out.println(b.toString());		
		calls.put(contractId, retVal);
		state.put(contractId, ContractNet.TransportAgentState.AWARDING);

		
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
		b.append("[TA]  Rcvd Bound: { TA: ");
		b.append(parent.getId());
		b.append(", { ");
		b.append(p);
		b.append(" }}");
		System.out.println(b.toString());
		
		state.put(pm.getContractId(), ContractNet.TransportAgentState.EXECUTING);
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
		ProtocolMessage abort = new ProtocolMessage(parent.getCommUser(),
				ProtocolMessage.MessageType.ABORT, p, true);
		
		// delete currently accepted proposal
		acceptedProposals.put(contractId, null);		

		StringBuilder b = new StringBuilder();
		b.append("[TA]  Send Abort: { TA: :");
		b.append(parent.getId());
		b.append(", { ");
		b.append(p);
		b.append(" }}");
		System.out.println(b.toString());
		
		parent.getDevice().get().send(abort, pm.getSender());
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
		ProtocolMessage accept = new ProtocolMessage(parent.getCommUser(), 
				ProtocolMessage.MessageType.PROVISIONAL_ACCEPT, p, true);
		
		// record the currently accepted proposal
		acceptedProposals.put(contractId, p);

		StringBuilder b = new StringBuilder();
		b.append("[TA]  Send Accept: { TA: ");
		b.append(parent.getId());
		b.append(", {");
		b.append(p);
		b.append(" }}");
		System.out.println(b.toString());
		
		DeliveryRecorder.setProposal(contractId, p);
		
		parent.getDevice().get().send(accept, accept.getReceiver());
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
		b.append(parent.getId());
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
			DeliveryRecorder.setProposal(contractId, null);

			state.put(contractId, ContractNet.TransportAgentState.AWARDING);

			break;

		case WAITING_TO_ABORT:
			Proposal newProposal = switchedProposals.get(contractId);
			
			// accept previously saved one
			acceptedProposals.put(contractId, newProposal);
			sendProvisionalAccept(newProposal);
			state.put(contractId, ContractNet.TransportAgentState.ASSIGNED);

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
	public void processMessages()
	{
		ImmutableList<Message> unread = parent.getDevice().get().getUnreadMessages();
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
	
	/**
	 * 
	 */
	void processAbortSwitch()
	{
		for (String contractId : acceptedProposals.keySet())
		{
			Proposal p = acceptedProposals.get(contractId);
			if (state.get(contractId) == ContractNet.TransportAgentState.ABORTING)
			{
				// send an abort to the currently assigned AGV
				sendAbort(p);

				state.put(contractId, ContractNet.TransportAgentState.WAITING_TO_ABORT);			
			}
		}
	}

	
	/**
	 * @param pm
	 */
	void receiveRefuseAbort(ProtocolMessage pm)
	{
		if (pm == null || pm.getType() != ProtocolMessage.MessageType.REFUSE_ABORT)
			return;
		
		for (String contractId : acceptedProposals.keySet())
		{
			if (state.get(contractId) == ContractNet.TransportAgentState.WAITING_TO_ABORT)
			{
				state.put(contractId, ContractNet.TransportAgentState.EXECUTING);			
			}
		}
	}
	
	/**
	 * 
	 */
	public void broadcastCFPs()
	{
		ContractNet.TransportAgentState s;
		CallForProposalMessage cfp;
		
		for (String contractID : state.keySet())
		{
			s = state.get(contractID);
			if (s == ContractNet.TransportAgentState.ASSIGNED 
			 || s == ContractNet.TransportAgentState.AWARDING )
			{
				cfp = calls.get(contractID);
				parent.getDevice().get().broadcast(cfp);
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
			
			ContractNet.TransportAgentState currentState = state.get(contractId);
			
			// unknown contractId
			if (currentState == null)
				continue;

			// ignore bound contracts
			if (currentState == ContractNet.TransportAgentState.EXECUTING 
			 || currentState == ContractNet.TransportAgentState.ABORTING
			 || currentState == ContractNet.TransportAgentState.WAITING_TO_ABORT)
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
				state.put(contractId, ContractNet.TransportAgentState.ASSIGNED);
				
				// send accept message
				sendProvisionalAccept(best);
			}
			// otherwise we replace the current one if the new one is better, and different
			else if (minCost < current.getCost() 
				&& best.getAVGId() != current.getAVGId())
			{
				newProposal = true;
				state.put(contractId, ContractNet.TransportAgentState.ABORTING);
				
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
}
