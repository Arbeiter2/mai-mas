
public final class ContractNet {

	public enum AGVState { 
		VOTING, 
		INTENTIONAL,
		SWITCH_INITIATOR,
		EXECUTING 
	}

	public enum TransportAgentState { 
		AWARDING, 
		ASSIGNED, 
		EXECUTING,
		ABORTING,
		WAITING_TO_ABORT
	}

}
