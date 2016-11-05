import java.util.*;
import java.io.*;
import java.util.concurrent.atomic.*;
import java.lang.Exception;


import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.errors.*;
import org.apache.kafka.common.*;

public class Recv {


  public static void main(String[] args) {

    Properties props = new Properties();
    props.put("bootstrap.servers", "localhost:9092");
    props.put("auto.offset.reset", "earliest");
    props.put("group.id", "zeta");
    //props.put("enable.auto.commit", "true");
    //props.put("auto.commit.interval.ms", "1000");
    props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");






    KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);

    Collection<TopicPartition> partitions = Arrays.asList(new TopicPartition("test", 0));
    consumer.assign(partitions);
    consumer.seekToBeginning(partitions);

    AtomicBoolean waitForCancelation = new AtomicBoolean(true);

    Thread cancelationListener = new Thread(){
      public void run() {
        System.out.println("thread: start");

        BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));

        try {

          while (true) {

            while (!stdIn.ready()) {

              System.out.println("thread: no input");
              Thread.sleep(1000);

              if (!waitForCancelation.get()) {
                System.out.println("thread: cancelation stopped");
                stdIn.close();
                return;
              }

            }

            System.out.println("thread: input ready");
            String line = stdIn.readLine();

            System.out.println("thread: input = " + line);

            if (line.equals("q")) {
              System.out.println("thread: wake consumer");
              consumer.wakeup();
              break;
            }

          }

        stdIn.close();

        } catch (Exception e) {
          System.out.println(e);
        }

        System.out.println("thread: done");
        return;
      }
    };

    cancelationListener.start();

    while (true) {

      try {
        System.out.println("polling...");
        ConsumerRecords<String, String> records = consumer.poll(300000);

       for (ConsumerRecord<String, String> record : records)
           System.out.printf("offset = %d, key = %s, value = %s%n", record.offset(), record.key(), record.value());

        waitForCancelation.set(false);
        break;


      } catch (Exception e) {
        //System.out.println("yo");
        System.out.println(e);
        break;
      }

    }

    System.out.println("done");
  }

}
