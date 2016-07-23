import com.github.rinde.rinsim.core.model.comm.CommDevice;
import com.github.rinde.rinsim.core.model.comm.CommUser;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.base.Optional;

public interface CNPUser {

	public double getContractCost(ProtocolMessage pm);
	public int getId();
	public boolean validateContract(ProtocolMessage pm);
	public Optional<CommDevice> getDevice();
	public Optional<Point> getPosition();
	public CommUser getCommUser();
}
