import java.io.*;
import java.util.*;

import java.nio.file.*;
import java.nio.charset.*;

import java.security.MessageDigest;

import com.rabbitmq.client.*;


public class CmdLineWorker {

  private final static String TASK_QUEUE_NAME = "task";
  private final static String RESULT_QUEUE_NAME = "result";


  public static String byteArrayToHexString(byte[] b) {
    String result = "";
    for (int i=0; i < b.length; i++) {
      result +=
            Integer.toString( ( b[i] & 0xff ) + 0x100, 16).substring( 1 );
    }
    return result;
  }

  public static String hashString(String line) throws Exception {

    MessageDigest md = MessageDigest.getInstance("SHA-256");
    md.update(line.getBytes());

    return byteArrayToHexString(md.digest());
  }


  private static void doWork(String line) throws Exception {

    Process proc = Runtime.getRuntime().exec(line);
    Reader r = new InputStreamReader(proc.getInputStream());
    BufferedReader in = new BufferedReader(r);

    String outputLine;

    List<String> lines;
    Path file = Paths.get(hashString(line));


    while((outputLine = in.readLine()) != null) {
      lines = Arrays.asList(outputLine);
      //Files.write(file, lines, Charset.forName("UTF-8"));
      Files.write(file, lines, Charset.forName("UTF-8"), StandardOpenOption.APPEND);
    }

    in.close();

    System.out.println();
  }

  public static void main(String[] argv) throws Exception {

    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");

    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();

    channel.queueDeclare(TASK_QUEUE_NAME, false, false, false, null);

    channel.basicQos(1);

    final Consumer consumer = new DefaultConsumer(channel) {
      @Override
      public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws java.io.IOException {
        String message = new String(body, "UTF-8");

        System.out.println("[RX] received: " + message);

        try {
          doWork(message);
        } catch (Exception e) {
          System.out.println("whoops");
        } finally {
          System.out.println("[RX] Done");
        }

      }
    };

    boolean autoAck = true;
    channel.basicConsume(TASK_QUEUE_NAME, autoAck, consumer);

    return;
  }
}
