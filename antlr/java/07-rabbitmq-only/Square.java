import java.util.*;
import java.io.*;
import com.rabbitmq.client.*;

public class Square extends ServiceWrapper {

  public Square(boolean nsIn, boolean nsOut) {
    super(nsIn, nsOut);

    this.name = "Square";
  }

  public void sendMsg(Channel channel, String msg, String queueName) {

    System.out.println("Square.sendMsg() msg:\n" + msg + "  queueName: " + queueName);

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


    System.out.println("Square.sendEof() queueName: " + queueName);

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

    System.out.println("Square.run()");
    System.out.println("  in: " + inQ);
    System.out.println(" out: " + resultQueue);

    try {

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

        int num = Integer.parseInt(new String(delivery.getBody()));


        sendMsg(channel, Integer.toString(num*num), resultQueue);
      }

      sendEof(channel, resultQueue);

      channel.close();
      connection.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }



}
