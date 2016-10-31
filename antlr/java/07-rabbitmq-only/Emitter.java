import java.util.*;
import java.io.*;
import com.rabbitmq.client.*;

public class Emitter extends ServiceWrapper {

  public Emitter(boolean nsIn, boolean nsOut) {
    super(nsIn, nsOut);

    this.name = "Emitter";
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

  public void run(ArrayList<String> args, ArrayList<String> argNames, String resultQueue) {

    System.out.println("Emitter.run()");
    System.out.println(" out: " + resultQueue);

    try {

      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();

      channel.queueDeclare(resultQueue, false, false, false, null);

      for (int i = 0; i < 10; i++) {
        sendMsg(channel, Integer.toString(i), resultQueue);
        Thread.sleep(1000);
      }

      sendEof(channel, resultQueue);

      channel.close();
      connection.close();


    } catch (Exception e) {
      System.out.println(e);
    }

  }

}
