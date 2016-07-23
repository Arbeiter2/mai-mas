import com.github.rinde.rinsim.core.model.comm.CommUser;
import com.github.rinde.rinsim.core.model.comm.MessageContents;
import com.github.rinde.rinsim.geom.Point;

public class ProtocolMessage implements MessageContents {

	public enum MessageType { 
		// transport agent -> AGV
		CALL_FOR_PROPOSAL,
		PROVISIONAL_ACCEPT, 
		ABORT, 

		// AGV -> transport agent
		PROPOSAL, 
		RETRACTED, 
		BOUND, 
		ACCEPT_ABORT, 
		REFUSE_ABORT,
		FAILED,
		
		PARTICIPANT_IN_SCOPE,
		PARTICIPANT_NOT_IN_SCOPE,
		TASK_IN_SCOPE,
		TASK_NOT_IN_SCOPE,
	};
	
	private static int msgCounter = 0;
	
	// type of message
	MessageType type;
	
	// unique message 
	int messageId;
	/**
	 * 
	 */
	int previousMessageId = -1;
	
	// for responses to CFPs
	String contractId;
	
	// can be transport agent or AGV
	CommUser sender;
	CommUser receiver;

	Point origin;
	Point destination;
	


	public Point getOrigin() {
		return origin;
	}


	public Point getDestination() {
		return destination;
	}
	
	
	ProtocolMessage(CommUser s, MessageType t, ProtocolMessage m, boolean isResponse)
	{
		super();
		contractId = m.contractId;
		sender = s;
		receiver = (isResponse ? m.sender : m.receiver);
		type = t;
		origin = m.origin;
		destination = m.destination;
		previousMessageId = (isResponse ? m.messageId : -1);

		msgCounter++;
		messageId = msgCounter;
	}
	
	
	ProtocolMessage(CommUser s, CommUser r, MessageType t, String cfp, Point a, Point b)
	{
		super();
		contractId = cfp;
		sender = s;
		receiver = r;
		type = t;
		origin = a;
		destination = b;
		
		msgCounter++;
		messageId = msgCounter;
	}
	
	public static int getMsgCounter() {
		return msgCounter;
	}


	public MessageType getType() {
		return type;
	}

	public int getMessageId() {
		return messageId;
	}


	public CommUser getSender() {
		return sender;
	}


	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ProtocolMessage [type=");
		builder.append(type);
		builder.append(", messageId=");
		builder.append(messageId);
		builder.append(", contractId=");
		builder.append(contractId);
		builder.append(", sender=");
		builder.append(sender);
		builder.append(", origin=");
		builder.append(origin);
		builder.append(", destination=");
		builder.append(destination);
		builder.append("]");
		return builder.toString();
	}


	/**
	 * @return the previousMessageId
	 */
	public int getPreviousMessageId() {
		return previousMessageId;
	}


	/**
	 * @return the contractId
	 */
	public String getContractId() {
		return contractId;
	}


	/**
	 * @return the receiver
	 */
	public CommUser getReceiver() {
		return receiver;
	}


	
}
