import java.util.HashMap;

public class DeliveryRecorder 
{
	public static class DeliveryRecord
	{
		CallForProposalMessage request;
		boolean failed = false;
		Proposal proposal = null;
		long CFPTime = -1;
		long boundTime = -1;
		long deliveryTime = -1;
		long failedTime = -1;
	
		DeliveryRecord(CallForProposalMessage cfp, long cfpTime)
		{
			request = cfp;
			CFPTime = cfpTime;
		}

		/* (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("DeliveryRecord [request=");
			builder.append(request);
			builder.append(", proposal=");
			builder.append(proposal != null ? proposal : "<none>");
			builder.append(", CFPTime=");
			builder.append(", failed=");
			builder.append(failed);
			builder.append(", failedTime=");
			builder.append(failedTime);
			builder.append(CFPTime);
			builder.append(", boundTime=");
			builder.append(boundTime);
			builder.append(", deliveryTime=");
			builder.append(deliveryTime);
			builder.append("]");
			return builder.toString();
		}

		/**
		 * @return the request
		 */
		public final CallForProposalMessage getRequest() {
			return request;
		}

		/**
		 * @return the failed
		 */
		public final boolean isFailed() {
			return failed;
		}

		/**
		 * @return the proposal
		 */
		public final Proposal getProposal() {
			return proposal;
		}

		/**
		 * @return the cFPTime
		 */
		public final long getCFPTime() {
			return CFPTime;
		}

		/**
		 * @return the boundTime
		 */
		public final long getBoundTime() {
			return boundTime;
		}

		/**
		 * @return the deliveryTime
		 */
		public final long getDeliveryTime() {
			return deliveryTime;
		}

		/**
		 * @return the failedTime
		 */
		public final long getFailedTime() {
			return failedTime;
		}
	}	
	private static HashMap<String, DeliveryRecord> allDeliveries = new HashMap<String, DeliveryRecord>();
	
	
	public static void addDelivery(CallForProposalMessage cfp, long t)
	{
		if (cfp == null)
			return;
					
		allDeliveries.put(cfp.getContractId(), new DeliveryRecord(cfp, t));
	}
	
	public static void setProposal(String contractId, Proposal prop)
	{
		if (contractId == null)
			return;
		
		DeliveryRecord d = allDeliveries.get(contractId);
		if (d == null)
			return;
		
		d.proposal = prop;
		d.boundTime = -1;
		d.deliveryTime = -1;
	}

	public static Proposal getProposal(String contractId)
	{
		if (contractId == null)
			return null;
		
		DeliveryRecord d = allDeliveries.get(contractId);
		if (d == null)
			return null;
		
		return d.proposal;
	}
	
	public static void setDeliveryBoundTime(String contractId, long t)
	{
		if (contractId == null)
			return;
		
		DeliveryRecord d = allDeliveries.get(contractId);
		if (d == null)
			return;
		
		d.boundTime = t;
	}

	public static long getDeliveryBoundTime(String contractId)
	{
		if (contractId == null)
			return -1L;
		
		DeliveryRecord d = allDeliveries.get(contractId);
		if (d == null)
			return -1L;
		
		return d.boundTime;
	}
	
	public static void setDeliveryDropoffTime(String contractId, long t)
	{
		if (contractId == null)
			return;
		
		DeliveryRecord d = allDeliveries.get(contractId);
		if (d == null)
			return;
		
		d.deliveryTime = t;
	}

	public static long getDeliveryDropoffTime(String contractId)
	{
		if (contractId == null)
			return -1L;
		
		DeliveryRecord d = allDeliveries.get(contractId);
		if (d != null)
			return d.deliveryTime;
		else
			return -1L;
	}	
	
	public static void setDeliveryFailed(String contractId, long t)
	{
		if (contractId == null)
			return;
		
		DeliveryRecord d = allDeliveries.get(contractId);
		if (d == null)
			return;
		
		d.failed = true;
		d.failedTime = t;
	}
	
	public static boolean isDeliveryFailed(String contractId)
	{
		if (contractId == null)
			return false;
		
		DeliveryRecord d = allDeliveries.get(contractId);
		if (d == null)
			return false;
		
		return d.failed;	
	}
}
