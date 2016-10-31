import java.io.*;
import java.util.*;

import java.nio.file.*;
import java.nio.charset.*;

import java.security.MessageDigest;

import com.rabbitmq.client.*;


public class CmdLineWorker {


  public final static String TASK_QUEUE_NAME = "task";
  public final static String NONAME = "anonymous";
  public final static String FS_GLOBAL = "fs/global/";

  CmdInfo cmdInfo = null;
  public static ServiceRegistry serviceRegistry = new ServiceRegistry();

  String cmd = null;
  String resHash = null;
  String[] cmdScript = null;

  public void sendEofToQueue(String queueName) {

    try {
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();

      Channel channel = connection.createChannel();
      channel.queueDeclare(queueName.toString(), false, false, false, null);

      Map<String, Object> headers = new HashMap<String, Object>();
      headers.put("eof", true);

      channel.basicPublish("", queueName.toString(), new AMQP.BasicProperties.Builder()
        .headers(headers)
        .build(),
        "".getBytes());

      channel.close();
      connection.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public void sendMsgToQueue(String msg, String queueName) {

    try {
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();

      Channel channel = connection.createChannel();
      channel.queueDeclare(queueName.toString(), false, false, false, null);

      Map<String, Object> headers = new HashMap<String, Object>();
      headers.put("eof",  false);

      channel.basicPublish("", queueName.toString(), new AMQP.BasicProperties.Builder()
        .headers(headers)
        .build(),
        msg.getBytes());

      channel.close();
      connection.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public void execNameCmd() {

    ServiceInfo si = cmdInfo.services.get(0);
    System.out.println("execNameCmd() about to exec: " + si.binary);

    ServiceWrapper sw = serviceRegistry.db.get(si.binary);
    sw.run(si.args, si.argNames, FS_GLOBAL + resHash);

    return;
  }

  public void execNamespaceCmd() {

    ServiceInfo si = cmdInfo.services.get(0);
    System.out.println("execNamespaceCmd() about to exec: " + si.binary);

    ServiceWrapper sw = serviceRegistry.db.get(si.binary);
    sw.run(si.args, si.argNames, FS_GLOBAL + resHash);

/*
    ServiceInfo si = cmdInfo.services.get(0);
    String exec = serviceRegistry.db.get(si.binary).exec;

    String rm = "rm -rf";

    for (String name : si.argNames) {
      exec += " " + Paths.get(name).getFileName();
      rm += " " + Paths.get(name).getFileName();
    }

    System.out.println("exec: " + exec);
    System.out.println("rm: " + rm);


    String hashDir = FS_GLOBAL + cmdInfo.servicesHash() + "/";
    createDir(hashDir);

    System.out.println("  hashDir: " + hashDir);

    try {

      for (String name : cmdInfo.services.get(0).argNames) {
        System.out.println("  copy " + name + " to " + hashDir + Paths.get(name).getFileName());
        Files.copy(Paths.get(name), Paths.get(hashDir + Paths.get(name).getFileName()));
      }

      Process proc = Runtime.getRuntime().exec(exec, null, new File(hashDir));
      proc = Runtime.getRuntime().exec(rm, null, new File(hashDir));

    } catch (Exception e) {
      System.out.println(e);
    }
*/
    return;
  }

  public void sendResponse(String queueName) {

    System.out.println("sendResponse() to queue: " + queueName);

    try {
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();
      channel.queueDeclare(queueName, false, false, false, null);

      channel.basicPublish("", queueName, null, cmdInfo.toByteArray());

      channel.close();
      connection.close();
    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public void sendNameResponse() {
    String queueName = cmdInfo.targetName + "-" + cmdInfo.sessionId;
    sendResponse(queueName);
    return;
  }

  public void sendNamespaceResponse() {
    String queueName = cmdInfo.targetNamespace + "-" + cmdInfo.sessionId;
    sendResponse(queueName);
    return;
  }

  public void sendAnonymousResponse() {
    String queueName = NONAME + "-" + cmdInfo.sessionId;
    sendResponse(queueName);
    return;
  }

  public void handleNameCmd() {
    execNameCmd();
    //sendNameResponse();
  }

  public void handleNamespaceCmd() {
    execNamespaceCmd();
    //sendNamespaceResponse();
  }

  public void handleAnonymousCmd() {
    execNameCmd();
    //sendAnonymousResponse();
  }

  public void handleCmd() {
    System.out.println("handleCmd()");
    cmdInfo.print();

    cmd     = cmdInfo.servicesString();
    resHash = cmdInfo.servicesHash();
    cmdScript = new String[]{"/bin/sh", "-c", cmd.toLowerCase()};

    System.out.println("  cmd:  " + cmd);
    System.out.println("  hash: " + resHash);

    if (cmdInfo.resultIsNamespace) {
      handleNamespaceCmd();
      return;
    }

    if (cmdInfo.targetName != null) {
      handleNameCmd();
      return;
    }

    handleAnonymousCmd();
    return;
  }

  public static CmdInfo retrieveCmd(byte[] body) throws Exception {

    CmdInfo cmdInfo = new CmdInfo();
    cmdInfo.fromByteArray(body);

    return cmdInfo;
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

  public static void initServiceRegistry() {

    ServiceWrapper cat = new Cat(false, false);
    serviceRegistry.db.put(cat.name, cat);

    ServiceWrapper emitter = new Emitter(false, false);
    serviceRegistry.db.put(emitter.name, emitter);

    ServiceWrapper untar = new Untar(false, true);
    serviceRegistry.db.put(untar.name, untar);

  }








  public static void main(String[] argv) throws Exception {

    initServiceRegistry();

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

        try{
          cmdLineWorker.cmdInfo = retrieveCmd(body);
        } catch (Exception e) {
          System.out.println("Command error");
        }

        cmdLineWorker.handleCmd();

        System.out.println("waiting for new tasks\n\n\n\n");
      }
    };

    boolean autoAck = true;
    channel.basicConsume(TASK_QUEUE_NAME, autoAck, consumer);

    return;
  }
}
