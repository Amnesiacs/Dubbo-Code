package identical.hash;

import java.util.ArrayList;
import java.util.List;

public abstract class Cluster {

	protected List<Node> nodes;

	public Cluster() {
		this.nodes = new ArrayList<>();
	}

	public abstract void addNode(Node node);

	public abstract void removeNode(Node node);

	public abstract Node get(String key);
}