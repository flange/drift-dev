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
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");


    String topic = args[0];
    Scanner input = new Scanner(System.in);

    Producer<String, String> producer = new KafkaProducer<>(props);

    int key = 0;

    while (true) {
      String line = input.nextLine();

      if (line.equals("q"))
        break;

      System.out.println("sending: (" + key + ", " + line + ")\n");

      producer.send(new ProducerRecord<String, String>(topic, Integer.toString(key), line));
      key++;
    }

    producer.close();

    return;
  }

}

