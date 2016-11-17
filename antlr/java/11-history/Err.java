import java.util.*;
import java.io.*;

import com.rabbitmq.client.*;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.*;

import kafka.admin.TopicCommand;

public class Err extends ServiceWrapper {

  public static long MAX_POLL = 300000;
  public Properties consumerProps, producerProps;

  public Err(boolean nsIn, boolean nsOut) {
    super(nsIn, nsOut);

    this.name = "Err";

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

  public int run(ArrayList<String> args, ArrayList<String> argNames, String resultQueue) {

    //String inQ = argNames.get(0);

    System.out.println("Err.run()");
    //System.out.println("  in: " + inQ);
    System.out.println(" out: " + resultQueue);

    try {
      // create inQ delete Listener
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();

      channel.queueDeclare(resultQueue, false, false, false, null);

      com.rabbitmq.client.Consumer deleteListener = new DefaultConsumer(channel) {


        public void handleCancel(java.lang.String consumerTag) {
          System.out.println("handleCancel");

          try {
            channel.close();
            connection.close();
          } catch (Exception e) {
            System.out.println("handleCancel");
          }
        }

      };

      channel.basicConsume(resultQueue, false, deleteListener);


      // create kafka producer
      Producer<String, byte[]> producer = new KafkaProducer<>(producerProps);

      System.out.println("send err");
      producer.send(new ProducerRecord<String, byte[]>(resultQueue, 0, "err", "".getBytes())).get();



      producer.close();

      channel.close();
      connection.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return 1;
  }



}
