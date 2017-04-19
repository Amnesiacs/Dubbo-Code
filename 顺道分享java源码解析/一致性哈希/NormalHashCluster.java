package identical.hash;

public class NormalHashCluster extends Cluster {
	public NormalHashCluster() {
		super();
	}

	@Override
	public void addNode(Node node) {
		this.nodes.add(node);
	}

	@Override
	public void removeNode(Node node) {
		this.nodes.removeIf(o -> o.getIp().equals(node.getIp())
				|| o.getDomain().equals(node.getDomain()));
	}

	@Override
	public Node get(String key) {
		long hash = hash(key);
		long index = hash % nodes.size();
		return nodes.get((int) index);
	}

	private long hash(String s) {
		return Integer.parseInt(s);
	}
}