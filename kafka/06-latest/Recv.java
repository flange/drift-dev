import java.util.*;


import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.*;

public class Recv {

  public static void main(String[] args) {

/*
    if (args.length != 1) {
      System.out.println("need Topic");
      return;
    }
*/

    Properties props = new Properties();
    props.put("bootstrap.servers", "localhost:9092");
    props.put("auto.offset.reset", "earliest");
    props.put("group.id", "zeta");
    //props.put("enable.auto.commit", "true");
    //props.put("auto.commit.interval.ms", "1000");
    props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");



    //String topic = args[0];
    TopicPartition partition0 = new TopicPartition("test", 0);
    List<TopicPartition> topic_list = Arrays.asList(partition0);


    KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
    consumer.assign(topic_list);
    consumer.seekToEnd(topic_list);

    consumer.seek(partition0, consumer.position(partition0) - 1);


    //System.out.println("next offset to be fetched: " + consumer.position(partition0));

    //System.out.println(consumer.position(partition0));


    ConsumerRecords<String, String> records = consumer.poll(10000);

    for (ConsumerRecord<String, String> record : records)
       System.out.printf("[%d] (%s, %s)%n", record.offset(), record.key(), record.value());


    consumer.close();

    return;
  }
}
