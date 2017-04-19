package identical.hash;

import java.util.stream.IntStream;

public class test {

	private static final int DATA_COUNT = 10;

	private static final String PRE_KEY = "";

	public static void main(String[] args) {
		Cluster cluster = new ConsistencyHashCluster();
		cluster.addNode(new Node("c1.yywang.info", "192.168.0.1"));
		cluster.addNode(new Node("c2.yywang.info", "192.168.0.2"));
		cluster.addNode(new Node("c3.yywang.info", "192.168.0.3"));
		cluster.addNode(new Node("c4.yywang.info", "192.168.0.4"));
		IntStream.range(0, DATA_COUNT).forEach(index -> {
			Node node = cluster.get(PRE_KEY + index);
			node.put(PRE_KEY + index, "Test Data");
		});
		System.out.println("数据分布情况：");
		cluster.nodes.forEach(node -> {
			System.out.println("IP:" + node.getIp() + ",数据量:"
					+ node.getData().size());
		});
		// 缓存命中率
		long hitCount = IntStream
				.range(0, DATA_COUNT)
				.filter(index -> cluster.get(PRE_KEY + index).get(
						PRE_KEY + index) != null).count();
		System.out.println("缓存命中率：" + hitCount * 1f / DATA_COUNT);

		// 增加一个节点
		cluster.addNode(new Node("c5.yywang.info", "192.168.0.5"));

		cluster.removeNode(new Node("c4.yywang.info", "192.168.0.1"));
//		cluster.removeNode(new Node("c4.yywang.info", "192.168.0.2"));

		System.out.println("数据分布情况：");
		cluster.nodes.forEach(node -> {
			System.out.println("IP:" + node.getIp() + ",数据量:"
					+ node.getData().size());
		});

		// long hitCountAfterAdd = IntStream
		// .range(0, DATA_COUNT)
		// .filter(i -> cluster.get(PRE_KEY + i).get(PRE_KEY + i) !=
		// null).count();

		long hitCountAfterAdd = 0;
		for (int i = 1; i <= DATA_COUNT; i++) {
			Node n = cluster.get(PRE_KEY + i);
			if (n.get(PRE_KEY + i) != null) {
				hitCountAfterAdd++;
			}
		}

		System.out.println("添加之后命中率：" + hitCountAfterAdd * 1.0 / DATA_COUNT);
	}
}
