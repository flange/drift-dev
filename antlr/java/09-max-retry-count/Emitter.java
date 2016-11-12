import java.util.*;
import java.io.*;
import com.rabbitmq.client.*;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.*;

public class Emitter extends ServiceWrapper {

  public static long MAX_POLL = 300000;
  public Properties consumerProps, producerProps;

  public Emitter(boolean nsIn, boolean nsOut) {
    super(nsIn, nsOut);

    this.name = "Emitter";

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

  public boolean run(ArrayList<String> args, ArrayList<String> argNames, String resultQueue) {

    System.out.println("Emitter.run()");
    System.out.println(" out: " + resultQueue);

    try {

      // create producer
      int key = 0;
      Producer<String, byte[]> producer = new KafkaProducer<>(producerProps);

      for (int i = 0; i < 30; i++) {
        System.out.println(i);
        producer.send(new ProducerRecord<String, byte[]>(resultQueue, 0, Integer.toString(key), Integer.toString(key).getBytes())).get();
        key++;
        Thread.sleep(1000);

        System.out.print(String.format("\033[%dA",1)); // Move up
        System.out.print("\033[2K");
      }

      // EOF
      producer.send(new ProducerRecord<String, byte[]>(resultQueue, 0, "eof", "".getBytes())).get();
      producer.close();

    } catch (Exception e) {
      System.out.println(e);
    }


    return true;
  }

}
