import java.util.*;
import java.io.*;

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.charset.*;

import com.rabbitmq.client.*;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.*;

public class Untar extends ServiceWrapper {

  public static long MAX_POLL = 300000;
  public Properties consumerProps, producerProps;

  public Untar(boolean nsIn, boolean nsOut) {
    super(nsIn, nsOut);

    this.name = "Untar";

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


  public static void createDir(String path) {

    try {
      Path dir = Paths.get(path);

      if (Files.notExists(dir))
        Files.createDirectory(dir);
    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public boolean hasEof(String queueName) {

    TopicPartition partition0 = new TopicPartition(queueName, 0);
    List<TopicPartition> topic_list = Arrays.asList(partition0);


    KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
    consumer.assign(topic_list);
    consumer.seekToEnd(topic_list);

    if (consumer.position(partition0) == 0)
      return false;

    consumer.seek(partition0, consumer.position(partition0) - 1);

    ConsumerRecords<String, String> records = consumer.poll(1000);

    for (ConsumerRecord<String, String> record : records) {
      if (record.key().equals("eof")) {
        consumer.close();
        return true;
      }

    }

    consumer.close();

    return false;
  }

  public void sendFileToQueue(String filePath) {

    System.out.println("sendFileToQueue() file: "+ filePath);

    Path pathLocal = Paths.get(filePath);
    String topic = filePath.replaceAll("/", "-");

    try {
      Producer<String, byte[]> producer = new KafkaProducer<>(producerProps);

        System.out.println("key to send file");
        System.in.read();

      producer.send(new ProducerRecord<String, byte[]>(topic, 0, "0", Files.readAllBytes(pathLocal))).get();



        System.out.println("key to send eof");
        System.in.read();

      producer.send(new ProducerRecord<String, byte[]>(topic, 0, "eof", "".getBytes())).get();

      producer.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }


  public void importDir(String dirPath) {

    System.out.println("importDir()");

    Path path = Paths.get(dirPath);
    String pathGlobalStr = dirPath + "-";

    // create producer
    Producer<String, byte[]> producer = new KafkaProducer<>(producerProps);

    int key = 0;

    try {
      DirectoryStream<Path> realDir = Files.newDirectoryStream(path);

      for (Path entry : realDir) {
        String entryName = entry.toString();
        String fileName = entry.getFileName().toString();

        if (hasEof(pathGlobalStr + fileName)) {
          System.out.println("file " + fileName + " already uploaded. skip");
          continue;
        }

        System.out.println("key to publish name: " + fileName);
        System.in.read();

        producer.send(new ProducerRecord<String, byte[]>(pathGlobalStr, 0, Integer.toString(key), fileName.getBytes())).get();
        sendFileToQueue(entryName);

        key++;
      }

        System.out.println("key to send dir EOF");
        System.in.read();

      producer.send(new ProducerRecord<String, byte[]>(pathGlobalStr, 0, "eof", "".getBytes())).get();
      System.out.println("send eof to queue " + pathGlobalStr);

    } catch (Exception e) {
      System.out.println(e);
    }

  }

  public void removeDir(String dirName) {

    try {
      Files.walkFileTree(Paths.get(dirName), new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.delete(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          Files.delete(dir);
          return FileVisitResult.CONTINUE;
        }
      });
    } catch(Exception e){
      e.printStackTrace();
    }

    return;
  }

  public int run(ArrayList<String> args, ArrayList<String> argNames, String dirQueue) {

    String tarQ = argNames.get(0);

    System.out.println("Untar.run()");
    System.out.println("  in: " + tarQ);
    System.out.println(" out: " + dirQueue);

    String hashDir = dirQueue;
    String tarName = hashDir + "/" + Paths.get(argNames.get(0)).getFileName().toString();

    System.out.println("  dirName: " + hashDir);
    System.out.println("  tarName: " + tarName);

    Process proc;

    try {

      createDir(hashDir);

      // create consumer
      Collection<TopicPartition> partitions = Arrays.asList(new TopicPartition(tarQ, 0));

      KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(consumerProps);
      consumer.assign(partitions);
      consumer.seekToBeginning(partitions);

      // download tar file
      while (true) {
        ConsumerRecords<String, byte[]> records = consumer.poll(MAX_POLL);
        ConsumerRecord<String, byte[]> record = records.iterator().next();

        if (record.key().equals("eof"))
          break;

        Files.write(Paths.get(tarName), record.value());
      }

      tarName = Paths.get(tarName).getFileName().toString();
      System.out.println("  new tarName: " + tarName);

      proc = Runtime.getRuntime().exec("tar -xf " + tarName, null, new File(hashDir));
      proc.waitFor();

      if (proc.exitValue() > 0) {
        // create kafka producer
        Producer<String, byte[]> producer = new KafkaProducer<>(producerProps);
        producer.send(new ProducerRecord<String, byte[]>(hashDir + "-", 0, "err", "".getBytes())).get();

        producer.close();
        consumer.close();

        proc = Runtime.getRuntime().exec("rm -rf " + hashDir);
        proc.waitFor();

        return 1;
      }


      proc = Runtime.getRuntime().exec("rm -rf " + tarName, null, new File(hashDir));
      proc.waitFor();

      importDir(hashDir);
      removeDir(hashDir);

      consumer.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return 0;
  }

}
