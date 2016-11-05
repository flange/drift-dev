import java.util.*;
import java.io.*;

import com.rabbitmq.client.*;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.*;


public class Cat extends ServiceWrapper {

  public static long MAX_POLL = 300000;
  public Properties consumerProps, producerProps;

  public Cat(boolean nsIn, boolean nsOut) {
    super(nsIn, nsOut);

    this.name = "Cat";

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

  public void run(ArrayList<String> args, ArrayList<String> argNames, String resultQueue) {

    String inQ = argNames.get(0);

    System.out.println("Cat.run()");
    System.out.println("  in: " + inQ);
    System.out.println(" out: " + resultQueue);

    try {
      // create uinux process
      Process proc = Runtime.getRuntime().exec("cat");

      Reader r = new InputStreamReader(proc.getInputStream());
      BufferedReader stdOut = new BufferedReader(r);

      OutputStream stdIn = proc.getOutputStream();

      // create consumer
      Collection<TopicPartition> partitions = Arrays.asList(new TopicPartition(inQ, 0));

      KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(consumerProps);
      consumer.assign(partitions);
      consumer.seekToBeginning(partitions);

      // create producer
      Producer<String, byte[]> producer = new KafkaProducer<>(producerProps);

      int key = 0;

      while (true) {
        ConsumerRecords<String, byte[]> records = consumer.poll(MAX_POLL);
        ConsumerRecord<String, byte[]> record = records.iterator().next();

        if (record.key().equals("eof"))
          break;

        stdIn.write(record.value());
        stdIn.flush();

        int firstByte = proc.getInputStream().read();
        byte[] remainingBytes = new byte[proc.getInputStream().available()];

        proc.getInputStream().read(remainingBytes);
        String outData = (char) firstByte + new String(remainingBytes);

        producer.send(new ProducerRecord<String, byte[]>(resultQueue, 0, Integer.toString(key), outData.getBytes())).get();
        key++;
      }

      // EOF
      producer.send(new ProducerRecord<String, byte[]>(resultQueue, 0, "eof", "".getBytes())).get();

      stdIn.close();
      stdOut.close();
      proc.destroy();

      consumer.close();
      producer.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }



}
