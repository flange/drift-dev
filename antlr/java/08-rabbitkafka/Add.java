import java.util.*;
import java.io.*;
import com.rabbitmq.client.*;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.*;

public class Add extends ServiceWrapper {

  public static long MAX_POLL = 300000;
  public Properties consumerProps, producerProps;

  public Add(boolean nsIn, boolean nsOut) {
    super(nsIn, nsOut);

    this.name = "Add";

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

  public void singleQueueConsume(String inQ, String resultQueue) {

    System.out.println("singleQueueConsume()");

    try {

      // create consumer
      Collection<TopicPartition> partitions = Arrays.asList(new TopicPartition(inQ, 0));

      KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(consumerProps);
      consumer.assign(partitions);
      consumer.seekToBeginning(partitions);

      // create producer
      int key = 0;
      Producer<String, byte[]> producer = new KafkaProducer<>(producerProps);

      while (true) {
        ConsumerRecords<String, byte[]> records = consumer.poll(MAX_POLL);
        ConsumerRecord<String, byte[]> record = records.iterator().next();

        if (record.key().equals("eof"))
          break;

        int num = Integer.parseInt(new String(record.value()));

        producer.send(new ProducerRecord<String, byte[]>(
          resultQueue,                            // queue
          0,                                      // partition
          Integer.toString(key),                  // key
          Integer.toString(num+num).getBytes()))  // value
          .get();                                 // sync wait on Future

        key++;
      }

      // EOF
      producer.send(new ProducerRecord<String, byte[]>(resultQueue, 0, "eof", "".getBytes())).get();

      consumer.close();
      producer.close();


    } catch (Exception e) {
      System.out.println(e);
    }

  }

  public void run(ArrayList<String> args, ArrayList<String> argNames, String resultQueue) {

    String inQ_A = argNames.get(0);
    String inQ_B = argNames.get(1);

    System.out.println("Add.run()");
    System.out.println("  in A: " + inQ_A);
    System.out.println("  in B: " + inQ_B);
    System.out.println("  out:  " + resultQueue);

    if (inQ_A.equals(inQ_B)) {
      singleQueueConsume(inQ_A, resultQueue);
      return;
    }

    try {

      // create consumers A, B
      Collection<TopicPartition> pA = Arrays.asList(new TopicPartition(inQ_A, 0));

      KafkaConsumer<String, byte[]> consumerA = new KafkaConsumer<>(consumerProps);
      consumerA.assign(pA);
      consumerA.seekToBeginning(pA);


      Collection<TopicPartition> pB = Arrays.asList(new TopicPartition(inQ_B, 0));

      KafkaConsumer<String, byte[]> consumerB = new KafkaConsumer<>(consumerProps);
      consumerB.assign(pB);
      consumerB.seekToBeginning(pB);


      // create producer
      int key = 0;
      Producer<String, byte[]> producer = new KafkaProducer<>(producerProps);


      while (true) {
        ConsumerRecord<String, byte[]> recordA;
        ConsumerRecord<String, byte[]> recordB;

        ConsumerRecords<String, byte[]> records;

        records = consumerA.poll(MAX_POLL);
        recordA = records.iterator().next();

        if (recordA.key().equals("eof"))
          break;

        records = consumerB.poll(MAX_POLL);
        recordB = records.iterator().next();

        if (recordB.key().equals("eof"))
          break;

        int numA = Integer.parseInt(new String(recordA.value()));
        int numB = Integer.parseInt(new String(recordB.value()));

        producer.send(new ProducerRecord<String, byte[]>(
          resultQueue,                                // queue
          0,                                          // partition
          Integer.toString(key),                      // key
          Integer.toString(numA + numB).getBytes()))  // value
          .get();                                     // sync wait on Future

        key++;
      }

      // EOF
      producer.send(new ProducerRecord<String, byte[]>(resultQueue, 0, "eof", "".getBytes())).get();

      consumerA.close();
      consumerB.close();

      producer.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }



}
