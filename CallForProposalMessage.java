import org.apache.commons.lang3.RandomStringUtils;

import com.github.rinde.rinsim.core.model.comm.CommUser;
import com.github.rinde.rinsim.geom.Point;

public class CallForProposalMessage extends ProtocolMessage {

	CallForProposalMessage(CommUser s, String contractId, Point a, Point b) 
	{
		super(s, null, ProtocolMessage.MessageType.CALL_FOR_PROPOSAL, contractId, a, b);
		
		origin = a;
		destination = b;
		contractId = RandomStringUtils.randomAlphanumeric(16);
	}
	
	CallForProposalMessage(CommUser s, ProtocolMessage p) 
	{
		super(s, ProtocolMessage.MessageType.CALL_FOR_PROPOSAL, p, true);
		contractId = RandomStringUtils.randomAlphanumeric(16);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("CallForProposalMessage [contractId=");
		builder.append(contractId);
		builder.append(", origin=");
		builder.append(origin);
		builder.append(", destination=");
		builder.append(destination);
		builder.append("]");
		return builder.toString();
	}	

}
