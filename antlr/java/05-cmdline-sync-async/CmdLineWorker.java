import java.io.*;
import java.util.*;

import java.nio.file.*;
import java.nio.charset.*;

import java.security.MessageDigest;

import com.rabbitmq.client.*;


public class CmdLineWorker {

  private final static String TASK_QUEUE_NAME = "task";
  private final static  String RESULT_QUEUE_NAME = "result";


  public static void execCmd(String cmd, String resHash) {

    try {
      Process proc = Runtime.getRuntime().exec(cmd);
      Reader r = new InputStreamReader(proc.getInputStream());
      BufferedReader in = new BufferedReader(r);

      String outputLine;

      List<String> lines;
      Path file = Paths.get(resHash);

      while((outputLine = in.readLine()) != null) {
        lines = Arrays.asList(outputLine);
        Files.write(file, lines, Charset.forName("UTF-8"));
      }

      in.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public static void sendResponse(CmdInfo cmdInfo) {

    String queueName = cmdInfo.name + "-" + cmdInfo.sessionId;

    System.out.println("sending response to queue: " + queueName);

    try {
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();
      channel.queueDeclare(queueName, false, false, false, null);

      channel.basicPublish("", queueName, null, cmdInfo.toByteArray());

      System.out.println("send done Msg\n");

      channel.close();
      connection.close();
    } catch (Exception e) {
      System.out.println(e);
    }

  }

  public static void handleCmd(CmdInfo cmdInfo) {
    System.out.println("handleCmd()");
    cmdInfo.print();

    String resHash = cmdInfo.servicesHash();
    String cmd     = cmdInfo.servicesString();

    System.out.println("cmd complete: " + cmd);
    System.out.println("res hash: " + resHash);

    execCmd(cmd.toLowerCase(), resHash);

    sendResponse(cmdInfo);

    System.out.println("done\n\n");

  }

  public static CmdInfo retrieveCmd(byte[] body) throws Exception {

    CmdInfo cmdInfo = new CmdInfo();
    cmdInfo.fromByteArray(body);

    return cmdInfo;
  }

  public static void main(String[] argv) throws Exception {

    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");

    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();

    channel.queueDeclare(TASK_QUEUE_NAME, false, false, false, null);

    channel.basicQos(1);

    CmdLineWorker cmdLineWorker = new CmdLineWorker();

    final Consumer consumer = new DefaultConsumer(channel) {
      @Override
      public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws java.io.IOException {


        System.out.println("[RX] received task");

        CmdInfo cmdInfo = new CmdInfo();

        try{
          cmdInfo = retrieveCmd(body);
        } catch (Exception e) {
          System.out.println("Command error");
        }

        handleCmd(cmdInfo);
      }
    };

    boolean autoAck = true;
    channel.basicConsume(TASK_QUEUE_NAME, autoAck, consumer);

    return;
  }
}
