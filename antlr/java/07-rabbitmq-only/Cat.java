import java.util.*;
import java.io.*;
import com.rabbitmq.client.*;

public class Cat extends ServiceWrapper {

  public Cat(boolean nsIn, boolean nsOut) {
    super(nsIn, nsOut);

    this.name = "Cat";
  }

  public void sendMsg(Channel channel, String msg, String queueName) {

    System.out.println("Cat.sendMsg() msg:\n" + msg + "  queueName: " + queueName);

    try {

      Map<String, Object> headers = new HashMap<String, Object>();
      headers.put("eof",  false);

      channel.basicPublish("", queueName.toString(), new AMQP.BasicProperties.Builder()
        .headers(headers)
        .build(),
        msg.getBytes());

    channel.waitForConfirms();

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public void sendEof(Channel channel, String queueName) {


    System.out.println("Cat.sendEof() queueName: " + queueName);

    try {
      Map<String, Object> headers = new HashMap<String, Object>();
      headers.put("eof", true);

      channel.basicPublish("", queueName.toString(), new AMQP.BasicProperties.Builder()
        .headers(headers)
        .build(),
        "".getBytes());

    channel.waitForConfirms();

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public void run(ArrayList<String> args, ArrayList<String> argNames, String resultQueue) {

    String inQ = argNames.get(0);

    System.out.println("Cat.run()");
    System.out.println("  in: " + inQ);
    System.out.println(" out: " + resultQueue);

    try {

      Process proc = Runtime.getRuntime().exec("cat");

      Reader r = new InputStreamReader(proc.getInputStream());
      BufferedReader stdOut = new BufferedReader(r);

      OutputStream stdIn = proc.getOutputStream();


      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();
      channel.confirmSelect();

      channel.queueDeclare(inQ, false, false, false, null);

      QueueingConsumer consumer = new QueueingConsumer(channel);
      channel.basicConsume(inQ, false, consumer);

      while (true) {

        QueueingConsumer.Delivery delivery = consumer.nextDelivery();
        Map<String, Object> headers = delivery.getProperties().getHeaders();

        if ((boolean) headers.get("eof"))
          break;

        String inLine = new String(delivery.getBody());

        stdIn.write(inLine.getBytes());
        stdIn.flush();

        int firstByte = proc.getInputStream().read();
        byte[] remainingBytes = new byte[proc.getInputStream().available()];

        proc.getInputStream().read(remainingBytes);
        String msg = (char) firstByte + new String(remainingBytes);

        sendMsg(channel, msg, resultQueue);
      }

      sendEof(channel, resultQueue);

      stdIn.close();
      stdOut.close();
      proc.destroy();

      channel.close();
      connection.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }



}
