import java.util.*;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.*;

public class FileRecv {

  public static long MAX_POLL = 300000;

  public static void main(String[] args) {

    if (args.length != 1) {
      System.out.println("need Topic");
      return;
    }

    String topicName = args[0];

    Properties props = new Properties();
    props.put("bootstrap.servers", "localhost:9092");
    props.put("max.poll.records", "1");
    props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    props.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");

    Collection<TopicPartition> partitions = Arrays.asList(new TopicPartition(topicName, 0));

    KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props);
    consumer.assign(partitions);
    consumer.seekToBeginning(partitions);

    while (true) {
      ConsumerRecords<String, byte[]> records = consumer.poll(MAX_POLL);
      ConsumerRecord<String, byte[]> record = records.iterator().next();

      if (record.key().equals("eof"))
        break;

      System.out.println(new String(record.value()));
    }

    return;
  }

}
