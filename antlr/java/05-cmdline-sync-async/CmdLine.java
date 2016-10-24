import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.io.*;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.*;

import com.rabbitmq.client.*;



public class CmdLine {


  private final static String SESSION_ID = "1234";
  private final static String TASK_QUEUE_NAME = "task";

  public CmdInfo cmdInfo;

  public Map<String, String> nameTable = new HashMap<String, String>();

  public void printNameTable() {
    if (nameTable.isEmpty()) {
      System.out.println("name table empty");
      return;
    }

    for (Map.Entry<String, String> entry : nameTable.entrySet())
      System.out.println(entry.getKey() + " - " + entry.getValue());

    return;
  }

  public boolean parseCmd(String line) {
    CmdLineLexer lex = new CmdLineLexer(new ANTLRInputStream(line + ";"));
    lex.removeErrorListeners();
    lex.addErrorListener(CustomErrorListener.INSTANCE);

    CommonTokenStream tokens = new CommonTokenStream(lex);

    CmdLineParser parser = new CmdLineParser(tokens);
    parser.removeErrorListeners();
    parser.addErrorListener(CustomErrorListener.INSTANCE);

    ParseTree tree;

    try {
      tree = parser.script();
    } catch (Exception e) {
      System.out.println("[Err] Invalid Statement\n");
      return false;
    }

    CmdLineWalker cmdWalker = new CmdLineWalker();

    ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(cmdWalker, tree);

    cmdInfo = cmdWalker.cmdInfo;
    cmdInfo.sessionId = SESSION_ID;

    return true;
  }

  public void sendTask() throws Exception {

    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");

    Connection connection = factory.newConnection();

    Channel channel = connection.createChannel();
    channel.queueDeclare(TASK_QUEUE_NAME, false, false, false, null);

    cmdInfo.print();
    channel.basicPublish("", TASK_QUEUE_NAME, null, cmdInfo.toByteArray());

    channel.close();
    connection.close();
  }

  public void waitForResult(String name) {

    String queueName = name + "-" + SESSION_ID;

    try {
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();

      channel.queueDeclare(queueName, false, false, false, null);
      channel.basicQos(1);

      QueueingConsumer qconsumer = new QueueingConsumer(channel);
      channel.basicConsume(queueName, false, qconsumer);

      QueueingConsumer.Delivery delivery = qconsumer.nextDelivery(); // blocking
      channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);

      cmdInfo.fromByteArray(delivery.getBody());

      channel.queueDelete(queueName);

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public void printResult() {

    String fileName = nameTable.get(cmdInfo.name);

    if (fileName == null)
      System.out.println("printResult: invalid name");

    String content = "";

    try {
      content = new String(Files.readAllBytes(Paths.get(fileName)));
    } catch (Exception e) {
      System.out.println(e);
    }

    System.out.println(content);

    return;
  }

  public boolean queryValid() {
    String res = nameTable.get(cmdInfo.name);

    return res != null;
  }

  public static void main(String[] args) throws Exception  {

    CmdLine cmdLine = new CmdLine();
    Scanner scanner = new Scanner(System.in);

    while (true) {
      System.out.print(".> ");
      String line = scanner.nextLine();

      if (line.length() == 0)
        continue;

      if(line.equals("q"))
        break;

      if (line.equals("r")) {
        cmdLine.printNameTable();
        continue;
      }

      boolean cmdValid = cmdLine.parseCmd(line);

      if (!cmdValid)
        continue;

      CmdInfo cmdInfo = cmdLine.cmdInfo;

      if (cmdInfo.isQuery) {

        if (!cmdLine.nameTable.containsKey(cmdInfo.name)) {
          System.out.println("[Err] unknown name\n");
          continue;
        }

        if (cmdLine.nameTable.get(cmdInfo.name).equals("")) {
          cmdLine.waitForResult(cmdInfo.name);
          cmdLine.nameTable.put(cmdInfo.name, cmdInfo.servicesHash());
        }

        cmdLine.printResult();
        continue;
      }

      cmdLine.nameTable.put(cmdInfo.name, "");
      cmdLine.sendTask();

      if (!cmdInfo.isAsync) {
        cmdLine.waitForResult(cmdInfo.name);
        cmdLine.nameTable.put(cmdInfo.name, cmdInfo.servicesHash());
      }


    }

    scanner.close();

    return;
  }


}
