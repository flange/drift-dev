import java.util.*;
import java.io.*;

import com.rabbitmq.client.*;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.*;
import org.apache.kafka.common.errors.*;


import org.apache.zookeeper.*;
import org.apache.zookeeper.data.*;

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

  public void deleteTopic(String topic) {

    try {

      //kafka-configs.sh --zookeeper localhost:2181 --alter --entity-type topics --entity-name <topic>  --add-config retention.ms=1000

      String[] cmd1 = new String[]{"/bin/sh", "-c", "kafka-configs.sh --zookeeper localhost:2181 --alter --entity-type topics --entity-name " + topic + " --add-config retention.ms=1000"};

      Process proc = Runtime.getRuntime().exec(cmd1);
      proc.waitFor();

      System.out.println("waiting for delete to propagate ...");
      for (int i = 0; i < 10; i++) {
        System.out.println(i);
        Thread.sleep(12000);
      }

      String[] cmd2 = new String[]{"/bin/sh", "-c", "kafka-configs.sh --zookeeper localhost:2181 --alter --entity-type topics --entity-name " + topic + " --add-config retention.ms=" + "604800000"};
      proc = Runtime.getRuntime().exec(cmd2);
      proc.waitFor();

    } catch (Exception e) {
      System.out.println(e);
    }
  }

  public boolean run(ArrayList<String> args, ArrayList<String> argNames, String resultQueue) {

    String inQ = argNames.get(0);

    System.out.println("Cat.run()");
    System.out.println("  in: " + inQ);
    System.out.println(" out: " + resultQueue);

    try {
      // create unix process
      Process proc = Runtime.getRuntime().exec("cat");

      Reader r = new InputStreamReader(proc.getInputStream());
      BufferedReader stdOut = new BufferedReader(r);
      OutputStream stdIn = proc.getOutputStream();


      // create signal queues (in/out);
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();

      channel.queueDeclare(inQ, false, false, false, null);
      channel.queueDeclare(resultQueue, false, false, false, null);



      // create kafka consumer
      Collection<TopicPartition> partitions = Arrays.asList(new TopicPartition(inQ, 0));

      KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(consumerProps);
      consumer.assign(partitions);
      consumer.seekToBeginning(partitions);

      // create kafka producer
      Producer<String, byte[]> producer = new KafkaProducer<>(producerProps);



      // create inQ delete Listener
      com.rabbitmq.client.Consumer deleteListener = new DefaultConsumer(channel) {

        public void handleCancel(java.lang.String consumerTag) {
          try {

            System.out.println("handleCancel");

            System.out.println("deleting signal out q");
            channel.queueDelete(resultQueue);

            System.out.println("purging data out q");
            deleteTopic(resultQueue);

            System.out.println("wakeup consumer");
            consumer.wakeup();

          } catch (Exception e) {
            System.out.println(e);
          }
        }
      };

      channel.basicConsume(inQ, false, deleteListener);


      // start consuming/producing
      int key = 0;

      while (true) {
        try {

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

        } catch (WakeupException we) {
          System.out.println("wake up exception");

          System.out.println("resetting unix process");
          stdIn.close();
          stdOut.close();
          proc.destroy();


          proc = Runtime.getRuntime().exec("cat");

          r = new InputStreamReader(proc.getInputStream());
          stdOut = new BufferedReader(r);
          stdIn = proc.getOutputStream();


        } catch (Exception e) {
          System.out.println(e);
        }
      }

      // EOF
      System.out.println("press key to send eof");
      System.in.read();

      producer.send(new ProducerRecord<String, byte[]>(resultQueue, 0, "eof", "".getBytes())).get();

      stdIn.close();
      stdOut.close();
      proc.destroy();

      consumer.close();
      producer.close();

      channel.close();
      connection.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return true;
  }



}
