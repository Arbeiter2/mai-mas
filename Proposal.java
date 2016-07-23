import org.apache.commons.lang3.RandomStringUtils;

import com.github.rinde.rinsim.core.model.comm.CommUser;

public class Proposal extends ProtocolMessage {

	double cost;
	String proposalId;
	int AVGId;


	Proposal(CommUser s, CallForProposalMessage cfp, int avgID, double c) 
	{
		super(s, ProtocolMessage.MessageType.PROPOSAL, cfp, true);
		cost = c;
		// create a contract Id
		proposalId = RandomStringUtils.randomAlphanumeric(4);
		AVGId = avgID;
	}
	

	/**
	 * @return the proposalId
	 */
	public String getProposalId() {
		return proposalId;
	}
	
	public double getCost() {
		return cost;
	}
	
	public void setCost(double c) {
		cost = c;
	}	

	/**
	 * @return the aVGId
	 */
	public int getAVGId() {
		return AVGId;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Proposal [AVGId=");
		builder.append(AVGId);
		builder.append(", contractId=");
		builder.append(contractId);
		builder.append(", proposalId=");
		builder.append(proposalId);
		builder.append(", cost=");
		builder.append(cost);
		builder.append(", origin=");
		builder.append(origin);
		builder.append(", destination=");
		builder.append(destination);
		builder.append("]");
		return builder.toString();
	}
}
