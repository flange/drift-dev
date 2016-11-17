import java.util.*;
import java.io.*;
import com.rabbitmq.client.*;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.*;
import org.apache.kafka.common.errors.*;

public class Square extends ServiceWrapper {

  public static long MAX_POLL = 300000;
  public Properties consumerProps, producerProps;

  public Square(boolean nsIn, boolean nsOut) {
    super(nsIn, nsOut);

    this.name = "Square";

    consumerProps = new Properties();
    consumerProps.put("bootstrap.servers", "localhost:9092");
    consumerProps.put("max.poll.records", "1");
    consumerProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    consumerProps.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");

    producerProps = new Properties();
    producerProps.put("bootstrap.servers", "localhost:9092");
    producerProps.put("acks", "all");
    producerProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    producerProps.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");

  }

  public void deleteTopic(String topic) {

    try {

      //kafka-configs.sh --zookeeper localhost:2181 --alter --entity-type topics --entity-name <topic>  --add-config retention.ms=1000

      String[] cmd1 = new String[]{"/bin/sh", "-c", "kafka-configs.sh --zookeeper localhost:2181 --alter --entity-type topics --entity-name " + topic + " --add-config retention.ms=1000"};
      Process proc = Runtime.getRuntime().exec(cmd1);
      proc.waitFor();


      System.out.println("waiting for delete to propagate ...");
      for (int i = 0; i < 5; i++) {
        System.out.println(i);
        Thread.sleep(1000);

        System.out.print(String.format("\033[%dA",1)); // Move up
        System.out.print("\033[2K");
      }


      String[] cmd2 = new String[]{"/bin/sh", "-c", "kafka-configs.sh --zookeeper localhost:2181 --alter --entity-type topics --entity-name " + topic + " --add-config retention.ms=" + "604800000"};
      proc = Runtime.getRuntime().exec(cmd2);
      proc.waitFor();

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public int run(ArrayList<String> args, ArrayList<String> argNames, String resultQueue) {

    String inQ = argNames.get(0);

    System.out.println("Square.run()");
    System.out.println("  in: " + inQ);
    System.out.println(" out: " + resultQueue);

    try {

      // create consumer
      Collection<TopicPartition> partitions = Arrays.asList(new TopicPartition(inQ, 0));

      KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(consumerProps);
      consumer.assign(partitions);
      consumer.seekToBeginning(partitions);

      // create producer
      int key = 0;
      Producer<String, byte[]> producer = new KafkaProducer<>(producerProps);

      // create inQ delete Listener
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();

      channel.queueDeclare(inQ, false, false, false, null);

      com.rabbitmq.client.Consumer deleteListener = new DefaultConsumer(channel) {

        public void handleCancel(java.lang.String consumerTag) {

          try {

            System.out.println("--reset--");

            System.out.println("deleting signal out q");
            channel.queueDelete(resultQueue);

            System.out.println("purging data out q");
            deleteTopic(resultQueue);
            channel.queueDelete(resultQueue);

          } catch (Exception e) {
            System.out.println("handleCancel");
          }
        }

      };

      channel.basicConsume(inQ, false, deleteListener);
      System.out.println("listening for resets on queue: " + inQ);

      System.out.println();

      while (true) {
        ConsumerRecords<String, byte[]> records = consumer.poll(MAX_POLL);
        ConsumerRecord<String, byte[]> record = records.iterator().next();

        System.out.print(String.format("\033[%dA",1)); // Move up
        System.out.print("\033[2K");

        if (record.key().equals("eof"))
          break;

        int num = Integer.parseInt(new String(record.value()));

        producer.send(new ProducerRecord<String, byte[]>(resultQueue, 0, Integer.toString(key), Integer.toString(num*num).getBytes())).get();
        key++;

        System.out.println(num+num);
      }

      // EOF
      producer.send(new ProducerRecord<String, byte[]>(resultQueue, 0, "eof", "".getBytes())).get();

      consumer.close();
      producer.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return 0;
  }



}
