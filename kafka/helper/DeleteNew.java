import java.util.*;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.*;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.*;
import org.apache.kafka.common.errors.*;

import com.rabbitmq.client.*;


public class DeleteNew {

  public static void main(String[] args) {

    Properties consumerProps = new Properties();
    consumerProps.put("bootstrap.servers", "localhost:9092");
    consumerProps.put("max.poll.records", "1");
    consumerProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    consumerProps.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");

    try {

    KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
    Map<String,List<PartitionInfo>> topicList = consumer.listTopics();

    ZooKeeperConnection zkConn = new ZooKeeperConnection();
    ZooKeeper zk = zkConn.connect("localhost");


    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");

    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();



    topicList.remove("__consumer_offsets");
    topicList.remove("test.txt");
    topicList.remove("my.tar");


    if (topicList.isEmpty()) {
      System.out.println("no queues");
      channel.close();
      connection.close();

      return;
    }


    for (String topic : topicList.keySet()) {
      if (topic.equals("__consumer_offsets"))
        continue;

      System.out.println("deleting q: " + topic);

      // delete from Kafka
      String[] deleteCmd = new String[]{"/bin/sh", "-c", "kafka-topics.sh --zookeeper localhost:2181 --delete --topic " + topic};

      Process proc = Runtime.getRuntime().exec(deleteCmd);
      proc.waitFor();

      // delete from Zookeeper
      try {
        zk.delete("/" + topic, -1);
      } catch (Exception e) {}

      // delete from RabbitMQ
      channel.queueDelete(topic);
    }

    channel.queueDelete("task");
    channel.queueDelete("done");
    channel.queueDelete("fail");


    consumer.close();

    channel.close();
    connection.close();


    } catch (Exception e) {
      System.out.println(e);
    }



    return;
  }

}
