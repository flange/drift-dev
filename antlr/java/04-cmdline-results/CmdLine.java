import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.io.*;
import java.util.Scanner;

import com.rabbitmq.client.*;



public class CmdLine {

  private final static String TASK_QUEUE_NAME = "task";



  public static void execCmd(String cmd) throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");

    Connection connection = factory.newConnection();

    Channel channel = connection.createChannel();
    channel.queueDeclare(TASK_QUEUE_NAME, false, false, false, null);
    channel.basicPublish("", TASK_QUEUE_NAME, null, cmd.getBytes());

    System.out.println("[TX] sent message\n");

    channel.close();
    connection.close();
  }


  public static boolean parseCmd(String line) {
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

    System.out.println("valid command: " + line + "\n");


    CmdLineWalker cmdWalker = new CmdLineWalker();

    ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(cmdWalker, tree);

    cmdWalker.printInfo();
    cmdWalker.clear();

    return true;
  }


  public static void main(String[] args) throws Exception  {
    Scanner scanner = new Scanner(System.in);

    while (true){
      String line = scanner.nextLine();

      if (line.length() == 0)
        continue;

      if(line.equals("q"))
        break;

      boolean cmdValid = parseCmd(line);

      if (cmdValid)
        execCmd(line);
    }

    scanner.close();


  }

}
