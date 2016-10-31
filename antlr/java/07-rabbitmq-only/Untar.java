import java.util.*;
import java.io.*;

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.charset.*;

import com.rabbitmq.client.*;

public class Untar extends ServiceWrapper {

  public static String FS_GLOBAL = "fs/global/";

  public Untar(boolean nsIn, boolean nsOut) {
    super(nsIn, nsOut);

    this.name = "Untar";
  }

  public void sendMsg(Channel channel, String msg, String queueName) {

    try {

      Map<String, Object> headers = new HashMap<String, Object>();
      headers.put("eof",  false);

      channel.basicPublish("", queueName.toString(), new AMQP.BasicProperties.Builder()
        .headers(headers)
        .build(),
        msg.getBytes());

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public void sendEof(Channel channel, String queueName) {

    try {
      Map<String, Object> headers = new HashMap<String, Object>();
      headers.put("eof", true);

      channel.basicPublish("", queueName.toString(), new AMQP.BasicProperties.Builder()
        .headers(headers)
        .build(),
        "".getBytes());

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
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


  public void sendFileToQueue(Channel channel, String filePath) {

    System.out.println("sendFileToQueue() file: "+ filePath + "  queue: " + FS_GLOBAL + filePath);

    Path pathLocal = Paths.get(filePath);
    Path queueName = Paths.get(FS_GLOBAL + filePath);

    String fileContent = "";

    try {
      channel.queueDeclare(queueName.toString(), false, false, false, null);

      Map<String, Object> headers = new HashMap<String, Object>();
      headers.put("eof",  false);

      channel.basicPublish("", queueName.toString(), new AMQP.BasicProperties.Builder()
        .headers(headers)
        .build(),
        Files.readAllBytes(pathLocal));

      headers.put("eof", true);

      channel.basicPublish("", queueName.toString(), new AMQP.BasicProperties.Builder()
        .headers(headers)
        .build(),
        "".getBytes());

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }


  public void importDir(Channel channel, String dirPath) {

    Path path = Paths.get(dirPath);
    Path pathGlobal = Paths.get(FS_GLOBAL + dirPath);

    String pathGlobalStr = pathGlobal.toString() + "/";

    try {
      channel.queueDeclare(pathGlobalStr, false, false, false, null);

      DirectoryStream<Path> realDir = Files.newDirectoryStream(path);

      for (Path entry : realDir) {
        sendFileToQueue(channel, entry.toString());

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("eof",  false);

        channel.basicPublish("", pathGlobalStr, new AMQP.BasicProperties.Builder()
          .headers(headers)
          .build(),
          entry.getFileName().toString().getBytes());

      }

      Map<String, Object> headers = new HashMap<String, Object>();
      headers.put("eof", true);

      channel.basicPublish("", pathGlobalStr, new AMQP.BasicProperties.Builder()
        .headers(headers)
        .build(),
        "".getBytes());

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

  public void run(ArrayList<String> args, ArrayList<String> argNames, String dirQueue) {

    String tarQ = argNames.get(0);

    System.out.println("Untar.run()");
    System.out.println("  in: " + tarQ);
    System.out.println(" out: " + dirQueue);

    String hashDir = Paths.get(dirQueue).getFileName().toString();
    String tarName = hashDir + "/" + Paths.get(argNames.get(0)).getFileName().toString();

    System.out.println("  dirName: " + hashDir);
    System.out.println("  tarName: " + tarName);

    Process proc;


    try {

      createDir(hashDir);

      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();

      channel.queueDeclare(tarQ, false, false, false, null);

      QueueingConsumer consumer = new QueueingConsumer(channel);
      channel.basicConsume(tarQ, false, consumer);

      while (true) {

        QueueingConsumer.Delivery delivery = consumer.nextDelivery();
        Map<String, Object> headers = delivery.getProperties().getHeaders();

        if ((boolean) headers.get("eof"))
          break;

        Files.write(Paths.get(tarName), delivery.getBody());
      }

      tarName = Paths.get(tarName).getFileName().toString();
      System.out.println("  new tarName: " + tarName);

      proc = Runtime.getRuntime().exec("tar -xf " + tarName, null, new File(hashDir));
      proc = Runtime.getRuntime().exec("rm -rf " + tarName, null, new File(hashDir));

      importDir(channel, hashDir);
      removeDir(hashDir);

      channel.close();
      connection.close();

    } catch (Exception e) {
      System.out.println(e);
    }

  }

}
