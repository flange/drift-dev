import java.util.*;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.*;

public class Recv {

  public static long MAX_POLL = 300000;

  public static void main(String[] args) {

    if (args.length != 1) {
      System.out.println("need Topic");
      return;
    }

    Properties props = new Properties();
    props.put("bootstrap.servers", "localhost:9092");
    props.put("max.poll.records", "1");

    props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");



    String topic = args[0];

    TopicPartition p10 = new TopicPartition("test-1", 0);
    TopicPartition p20 = new TopicPartition("test-2", 0);
    List<TopicPartition> topic_list = Arrays.asList(p10, p20);



    KafkaConsumer<String, String> consumerA = new KafkaConsumer<>(props);
    consumerA.assign(Arrays.asList(p10));
    consumerA.seekToBeginning(Arrays.asList(p10));



    KafkaConsumer<String, String> consumerB = new KafkaConsumer<>(props);
    consumerB.assign(Arrays.asList(p20));
    consumerB.seekToBeginning(Arrays.asList(p20));




    while (true) {
      String a;
      String b;

      ConsumerRecords<String, String> records;

      records = consumerA.poll(MAX_POLL);
      a = records.iterator().next().value();

      records = consumerB.poll(MAX_POLL);
      b = records.iterator().next().value();

      System.out.println(a+b);

    }

  }
}
