import java.util.*;

import org.apache.kafka.clients.producer.*;

public class Send {

  public static void main(String[] args) {

    if (args.length != 1) {
      System.out.println("need Topic");
      return;
    }

    Properties props = new Properties();
    props.put("bootstrap.servers", "localhost:9092");
    props.put("acks", "all");
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");


    String topic = args[0];
    Scanner input = new Scanner(System.in);

    Producer<String, String> producer = new KafkaProducer<>(props);

    int key = 0;

    while (true) {
      String line = input.nextLine();

      if (line.equals("q")) {
        System.out.println("sending eof to topic " + topic + "\n");

        try {
          producer.send(new ProducerRecord<String, String>(topic, 0, "eof", "")).get();
        } catch (Exception e) {
          System.out.println(e);
        }

        break;
      }

      System.out.println("sending: (" + key + ", " + line + ") to topic " + topic + "\n");
      try {
        producer.send(new ProducerRecord<String, String>(topic, 0, Integer.toString(key), line)).get();
      } catch (Exception e) {
        System.out.println(e);
      }

      key++;
    }

    producer.close();

    return;
  }

}

