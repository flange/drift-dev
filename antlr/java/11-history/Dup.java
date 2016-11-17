import java.util.*;
import java.io.*;

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.charset.*;

import com.rabbitmq.client.*;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.*;

public class Dup extends ServiceWrapper {

  public static long MAX_POLL = 300000;
  public Properties consumerProps, producerProps;

  public Dup(boolean nsIn, boolean nsOut) {
    super(nsIn, nsOut);

    this.name = "Dup";

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


  public int run(ArrayList<String> args, ArrayList<String> argNames, String dirQueue) {

    String dirQ = dirQueue + "-";
    String resA = dirQ + "a";
    String resB = dirQ + "b";


    System.out.println("Dup.run()");
    System.out.println("out : " + dirQ);
    System.out.println("outA: " + resA);
    System.out.println("outB: " + resB);

    try {

      // create kafka producer
      Producer<String, byte[]> producer = new KafkaProducer<>(producerProps);


      // produce A
      producer.send(new ProducerRecord<String, byte[]>(dirQ, 0, "0", "a".getBytes())).get();

      for (int i = 0; i < 5; i++)
        producer.send(new ProducerRecord<String, byte[]>(resA, 0, Integer.toString(i), Integer.toString(i).getBytes())).get();


      System.out.println("key for A EOF");
      System.in.read();
      producer.send(new ProducerRecord<String, byte[]>(resA, 0, "eof", "".getBytes())).get();


      System.out.println("key for B");
      System.in.read();


      // produce B
      producer.send(new ProducerRecord<String, byte[]>(dirQ, 0, "1", "b".getBytes())).get();

      for (int i = 0; i < 3; i++)
        producer.send(new ProducerRecord<String, byte[]>(resB, 0, Integer.toString(i), Integer.toString(i).getBytes())).get();

      System.out.println("key for B ERR");
      System.in.read();
      producer.send(new ProducerRecord<String, byte[]>(resB, 0, "err", "".getBytes())).get();


      // send ERR
      producer.send(new ProducerRecord<String, byte[]>(dirQ, 0, "err", "".getBytes())).get();
      producer.close();

      return 1;

    } catch (Exception e) {
      System.out.println(e);
    }

    return 0;
  }

}
