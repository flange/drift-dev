import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.charset.*;

import com.rabbitmq.client.*;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.*;
import org.apache.kafka.common.errors.*;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.*;


public class CmdLine {

  public static long MAX_POLL = 300000;

  public static String SESSION_ID = "1234";
  public final static String TASK_QUEUE_NAME = "task";
  public final static String DONE_QUEUE_NAME = "done";
  public final  static String FAIL_QUEUE_NAME = "fail";
  public final  static String QUERY_QUEUE_NAME = "query";


  public final static String FS_GLOBAL  = "fs/global/";
  public static String FS_SESSION = "fs/" + SESSION_ID + "/";
  public static String FS_CWD     = FS_SESSION;

  public final static String NONAME = "anonymous";
  public final static String PROMPT = ".> ";

  public Properties consumerProps, producerProps;

  public static BufferedReader stdIn;

  public Set<String> blockedNames;
  public Set<String> failedNames;

  public ZooKeeperConnection zkConn;
  public ZooKeeper zk;

  KafkaConsumer<String, byte[]> resultConsumer;

  public CmdInfo cmdInfo;
  public Map<String, String> nameTable = new HashMap<String, String>();
  public ServiceRegistry serviceRegistry = new ServiceRegistry();

  public CmdLine() {
    stdIn = new BufferedReader(new InputStreamReader(System.in));

    blockedNames = Collections.synchronizedSet(new HashSet<String>());
    failedNames = Collections.synchronizedSet(new HashSet<String>());

    try {

      zkConn = new ZooKeeperConnection();
      zk = zkConn.connect("localhost");

    } catch (Exception e) {
      System.out.println(e);
    }

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

    KafkaConsumer<String, byte[]> resultConsumer;
  }

  public void printNameTable() {
    if (nameTable.isEmpty()) {
      System.out.println("name table empty");
      return;
    }

    for (Map.Entry<String, String> entry : nameTable.entrySet())
      System.out.println(entry.getKey() + " - " + entry.getValue());

    System.out.println();

    return;
  }

  public boolean parseCmd(String line) {

    //TODO ; ??

    CmdLineLexer lex = new CmdLineLexer(new ANTLRInputStream(line));
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
      System.out.println("[Err] invalid statement\n");
      return false;
    }

    CmdLineWalker cmdWalker = new CmdLineWalker();

    ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(cmdWalker, tree);

    cmdInfo = cmdWalker.cmdInfo;
    cmdInfo.sessionId = SESSION_ID;

    return true;
  }

  public void sendTask(ArrayList<CmdInfo> cmds) {

    Set<String> queues = getQueues();
    ArrayList<CmdInfo> sendList = new ArrayList<CmdInfo>();
/*
    System.out.println("sendTask():");
    System.out.println("queues:");
    for (String q : queues)
      System.out.println("  " + q);

    System.out.println();


    System.out.println("cmds result Qs:");
    for (CmdInfo cmd : cmds)
      System.out.println(cmd.servicesHash());
*/

    ListIterator<CmdInfo> cmdsIt = cmds.listIterator(cmds.size());

    while(cmdsIt.hasPrevious()) {
      CmdInfo prev = cmdsIt.previous();

      String cmdResultQueue = prev.servicesHash();

      if (queues.contains(cmdResultQueue))
        break;

      sendList.add(prev);
    }

    if (sendList.size() == 0)
      return;

    try {
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();
      channel.confirmSelect();

      channel.queueDeclare(TASK_QUEUE_NAME, false, false, false, null);


      cmdsIt = sendList.listIterator(sendList.size());

      while(cmdsIt.hasPrevious()) {
        CmdInfo prev = cmdsIt.previous();

        channel.basicPublish("", TASK_QUEUE_NAME, null, prev.toByteArray());
        channel.waitForConfirms();
      }

      channel.close();
      connection.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
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
      channel.close();
      connection.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public void printResult(String topicName) {

    Collection<TopicPartition> partitions = Arrays.asList(new TopicPartition(topicName, 0));

    resultConsumer = new KafkaConsumer<>(consumerProps);
    resultConsumer.assign(partitions);
    resultConsumer.seekToBeginning(partitions);

    AtomicBoolean waitForCancelation = new AtomicBoolean(true);

    try {

/*
      // create signal queue delete listener
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();

      channel.queueDeclare(QUERY_QUEUE_NAME, false, false, false, null);

      com.rabbitmq.client.Consumer failListener = new DefaultConsumer(channel) {

        public void handleCancel(String consumerTag) {

          try {
            System.out.println("[Err] the service providing the result you queried crashed");

            consumer.wakeup();

          } catch (Exception e) {
            System.out.println(e);
          }
        }
      };

      channel.basicConsume(QUERY_QUEUE_NAME, false, failListener);
*/


    // cancelation listener
    Thread cancelationListener = new Thread() {

      public void run() {

        try {

          while (true) {

            while (!stdIn.ready()) {
              Thread.sleep(500);

              if (!waitForCancelation.get())
                return;
            }

            String line = new String(stdIn.readLine());

            if (line.equals("q")) {
              System.out.print(String.format("\033[%dA",1)); // Move up
              System.out.print("\033[2K");
              break;
            }
          }

        resultConsumer.wakeup();

        } catch (Exception e) {
          System.out.println(e);
        }

        return;
      }
    };

    cancelationListener.start();


    // consume poll loop
    while (true) {

      try {
        ConsumerRecords<String, byte[]> records = resultConsumer.poll(MAX_POLL);
        ConsumerRecord<String, byte[]> record = records.iterator().next();

        if (record.key().equals("eof"))
          break;

        if (record.key().equals("err")) {
          System.out.println("[Err] service failed\n");
          break;
        }

        System.out.println(new String(record.value()));

      } catch (WakeupException e) {
        break;
      }

    }

    waitForCancelation.set(false);

    cancelationListener.join();
    resultConsumer.close();

    } catch (Exception e) {
      System.out.println(e);
    }



    return;
  }

  public void handleNameQuery() {

    if (!nameTable.containsKey(cmdInfo.targetName)) {
      System.out.println("[Err] unknown name\n");
      return;
    }

    if (failedNames.contains(cmdInfo.targetName)) {
      System.out.println("[Err] the service providing this name failed\n");
      return;
    }



    printResult(nameTable.get(cmdInfo.targetName));
    return;
  }

  public void handleNamespaceQuery() {

    if (!nameTable.containsKey(cmdInfo.targetNamespace)) {
      System.out.println("[Err] unknown namespace\n");
      return;
    }

    synchronized(failedNames) {

      if (failedNames.contains(cmdInfo.targetNamespace)) {
        System.out.println("[Err] service failed\n");
        return;
      }
    }

    receiveNamespace(nameTable.get(cmdInfo.targetNamespace), cmdInfo.targetNamespace);
    printResult(nameTable.get(cmdInfo.targetNamespace));
    return;
  }

  public void handleQuery() {

    if (cmdInfo.targetName != null) {
      handleNameQuery();
      return;
    }

    handleNamespaceQuery();

    return;
  }

  public void cloneNamespace(String namespace) {

    String dirName = namespace.replaceAll("-", "");

    nameTable.put(FS_SESSION + dirName + "/", namespace + "-");
    createDir(FS_SESSION + dirName);

    String queueName = namespace;

    try {
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();

      channel.queueDeclare(queueName, false, false, false, null);

      QueueingConsumer consumer = new QueueingConsumer(channel);
      channel.basicConsume(queueName, false, consumer);

      while (true) {

        QueueingConsumer.Delivery delivery = consumer.nextDelivery();
        Map<String, Object> headers = delivery.getProperties().getHeaders();

        if ((boolean) headers.get("eof"))
          break;

        String name = new String(delivery.getBody());
        nameTable.put(FS_SESSION + dirName + name, FS_GLOBAL + dirName + name);

        createFile(FS_SESSION + dirName + name);
      }

      channel.close();
      connection.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public void receiveNamespace(String queueName, String namespace) {

    nameTable.put(namespace, queueName);
    createDir(namespace);

    try {
      // create consumer
      Collection<TopicPartition> partitions = Arrays.asList(new TopicPartition(queueName, 0));

      KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(consumerProps);
      consumer.assign(partitions);
      consumer.seekToBeginning(partitions);

      // get file name from dir queue
      while (true) {
        ConsumerRecords<String, byte[]> records = consumer.poll(MAX_POLL);
        ConsumerRecord<String, byte[]> record = records.iterator().next();

        if (record.key().equals("eof"))
          break;

        if (record.key().equals("err")) {
          break;
        }

        String name = new String(record.value());

        nameTable.put(namespace + name, queueName + name);
        createFile(namespace + name);
      }

      consumer.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public ArrayList<CmdInfo> splitCmd() {

    ArrayList<CmdInfo> cmds = new ArrayList<CmdInfo>();

    if (cmdInfo.services.size() == 1) {
      cmds.add(cmdInfo);
      return cmds;
    }

    ServiceInfo firstSi = cmdInfo.services.get(0);
    CmdInfo first = new CmdInfo(cmdInfo);
    first.services = new ArrayList<ServiceInfo>();
    first.services.add(firstSi);

    cmds.add(first);

    String lastHash = first.servicesHash();

    for (int i = 1; i < cmdInfo.services.size(); i++) {

      ServiceInfo si = cmdInfo.services.get(i);

      if (si.argNames.isEmpty())
        si.argNames.add(lastHash);
      else
        si.argNames.set(0, lastHash);

      CmdInfo ci = new CmdInfo(cmdInfo);
      ci.services = new ArrayList<ServiceInfo>();
      ci.services.add(si);

      cmds.add(ci);

      lastHash = ci.servicesHash();
    }

    return cmds;
  }

  public void handleNamespaceCmd() {

    ArrayList<CmdInfo> cmds = splitCmd();
    CmdInfo lastCmd = cmds.get(cmds.size() - 1);


    String targetNamespace = Paths.get(cmdInfo.targetNamespace).getFileName().toString() + "/";


    if (blockedNames.contains(targetNamespace)) {
        System.out.println("[Err] namespace '" + targetNamespace + "' is blocked\n");
        return;
    }


    if (!resultExists(cmdInfo.servicesHash() + "-"))
      sendTask(cmds);

    nameTable.put(cmdInfo.targetNamespace, cmdInfo.servicesHash() + "-");
    createDir(cmdInfo.targetNamespace);
    blockedNames.add(Paths.get(cmdInfo.targetNamespace).getFileName().toString() + "/");

    //receiveNamespace(cmdInfo.servicesHash() + "-", cmdInfo.targetNamespace);

    return;
  }

  public void handleNameCmd() {

    ArrayList<CmdInfo> cmds = splitCmd();
    CmdInfo lastCmd = cmds.get(cmds.size() - 1);

    // anonymous task
    if ((cmdInfo.targetName == null) && (cmdInfo.targetNamespace == null)) {

      if (!resultExists(cmdInfo.servicesHash()))
        sendTask(cmds);

      printResult(lastCmd.servicesHash());

      return;
    }

    String targetName = Paths.get(cmdInfo.targetName).getFileName().toString();

    if (blockedNames.contains(targetName) && !failedNames.contains(cmdInfo.targetName)) {
        System.out.println("[Err] name '" + targetName + "' is blocked\n");
        return;
    }

    createFile(cmdInfo.targetName);
    failedNames.remove(cmdInfo.targetName);

    // result exists
    if (resultExists(cmdInfo.servicesHash())) {
      nameTable.put(cmdInfo.targetName, cmdInfo.servicesHash());
      return;
    }

    // no result

    nameTable.put(cmdInfo.targetName, cmdInfo.servicesHash());
    sendTask(cmds);
    blockedNames.add(Paths.get(cmdInfo.targetName).getFileName().toString());

    //if (!cmdInfo.isAsync)
      //printResult(cmdInfo.servicesHash());

    return;

  }

  public void handleCmd() {

    if (cmdInfo.resultIsNamespace) {
      handleNamespaceCmd();
      return;
    }

    handleNameCmd();
    return;
  }

  public boolean isNamespace(String name) {
    return (name.substring(name.length() - 1)).equals("/");
  }

  public boolean checkCmd() {

    for (ServiceInfo si : cmdInfo.services) {
      if (!serviceRegistry.isValid(si)) {
        System.out.println("[Err] invalid service '" +  si.binary +"'\n");
        return false;
      }

      for (String name : si.argNames) {

        if (!nameTable.containsKey(FS_CWD + name)) {
          System.out.println("[Err] unknown name '" + name + "'\n");
          return false;
        }

        String globalName = nameTable.get(FS_CWD + name);

        si.argNames.set(si.argNames.indexOf(name), globalName);
      }
    }

    if (cmdInfo.targetName != null)
      cmdInfo.targetName = FS_CWD + cmdInfo.targetName;

    if (cmdInfo.targetNamespace != null)
      cmdInfo.targetNamespace = FS_CWD + cmdInfo.targetNamespace;

    for (ServiceInfo si : cmdInfo.services)
      si.toString();

    return true;
  }

  public boolean resultExists(String resName) {

    try {

      String zkResultPath = "/" + resName;
      Stat stat = zk.exists(zkResultPath, true);

      if(stat != null)
        return true;

    } catch (Exception e) {
      System.out.println(e);
    }

    return false;
  }

  public void createFile(String path) {
    try {
      if (Files.notExists(Paths.get(path)))
        Files.createFile(Paths.get(path));
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

  public Set<String> getQueues() {

    KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
    Map<String,List<PartitionInfo>> topicList = consumer.listTopics();

    return topicList.keySet();
  }

  public boolean hasQueue(String queueName) {

    KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
    Map<String,List<PartitionInfo>> topicList = consumer.listTopics();

    if (topicList.keySet().contains(queueName))
      return true;

    return false;
  }

  public void sendFileToQueue(String filePath) {

    if (hasQueue(filePath.replaceAll("/", "-")))
      return;

    Path pathLocal = Paths.get(filePath);
    String topic = filePath.replaceAll("/", "-");

    try {
      Producer<String, byte[]> producer = new KafkaProducer<>(producerProps);
      producer.send(new ProducerRecord<String, byte[]>(topic, 0, "0", Files.readAllBytes(pathLocal))).get();
      producer.send(new ProducerRecord<String, byte[]>(topic, 0, "eof", "".getBytes())).get();

      producer.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public static void importFile(String filePath, CmdLine cmdLine) {

    Path path = Paths.get(filePath);
    Path pathLocal = Paths.get(FS_SESSION + filePath);

    try {
      if (!cmdLine.hasQueue(filePath.replaceAll("/", "-")))
        cmdLine.sendFileToQueue(filePath);

      if (Files.notExists(pathLocal))
        Files.createFile(pathLocal);

    } catch (Exception e) {
      System.out.println(e);
    }
    cmdLine.nameTable.put(FS_SESSION + filePath, filePath.replaceAll("/", "-"));

  }

  public static void importDir(String dirPath, CmdLine cmdLine) {

    Path path = Paths.get(dirPath);
    Path pathLocal = Paths.get(FS_SESSION + dirPath);

    String pathLocalStr = pathLocal.toString() + "/";
    String pathGlobalStr = dirPath + "-";

    try {
      if (cmdLine.hasQueue(pathGlobalStr)) {
        System.out.println("queue exists");
        cmdLine.receiveNamespace(pathGlobalStr, pathLocalStr);
        return;
      }

      if (Files.notExists(pathLocal))
        Files.createDirectory(pathLocal);

      cmdLine.nameTable.put(pathLocalStr, pathGlobalStr);

      DirectoryStream<Path> realDir = Files.newDirectoryStream(path);
      Producer<String, byte[]> producer = new KafkaProducer<>(cmdLine.producerProps);

      int key = 0;

      for (Path entry : realDir) {
        String entryName = entry.toString();
        importFile(entryName, cmdLine);
        producer.send(new ProducerRecord<String, byte[]>(pathGlobalStr, 0, Integer.toString(key), entryName.replaceAll("/", "-").getBytes())).get();
        key++;
      }

      producer.send(new ProducerRecord<String, byte[]>(pathGlobalStr, 0, "eof", "".getBytes())).get();

    } catch (Exception e) {
      System.out.println(e);
    }

  }

  public static void importSetup(CmdLine cmdLine) {

    importFile("my.tar", cmdLine);
    importFile("test.txt", cmdLine);
    //importFile("foo.txt", cmdLine);
    //importFile("nums.txt", cmdLine);



    //importDir("res", cmdLine);

    return;
  }

  public static void initServiceRegistry(CmdLine cmdLine) {

    ServiceWrapper cat = new Cat(false, false);
    cmdLine.serviceRegistry.db.put(cat.name, cat);

    ServiceWrapper square = new Square(false, false);
    cmdLine.serviceRegistry.db.put(square.name, square);

    ServiceWrapper add = new Add(false, false);
    cmdLine.serviceRegistry.db.put(add.name, add);

    ServiceWrapper emitter = new Emitter(false, false);
    cmdLine.serviceRegistry.db.put(emitter.name, emitter);

    ServiceWrapper emitter2 = new Emitter2(false, false);
    cmdLine.serviceRegistry.db.put(emitter2.name, emitter2);

    ServiceWrapper untar = new Untar(false, true);
    cmdLine.serviceRegistry.db.put(untar.name, untar);


  }

  public void handleLs() {

    if (cmdInfo.targetNamespace != null) {

      if (!Files.isDirectory(Paths.get(cmdInfo.targetNamespace))) {
        System.out.println("[Err] not a namespace\n");
        return;
      }

      ls(Paths.get(cmdInfo.targetNamespace));
      return;
    }

    ls(Paths.get(FS_CWD));
    return;
  }

  public static void ls(Path path) {

    if (!Files.isDirectory(path)) {
      System.out.println(path.getFileName());
      return;
    }

    try {
      DirectoryStream<Path> stream = Files.newDirectoryStream(path);

      if (!stream.iterator().hasNext()) {
        System.out.println("(empty)");
        return;
      }

    } catch (IOException e) {
        e.printStackTrace();
    }

    try {
      DirectoryStream<Path> stream = Files.newDirectoryStream(path);

      for (Path entry : stream) {

        if (!Files.isDirectory(entry))
          continue;

        //http://stackoverflow.com/questions/1448858/how-to-color-system-out-println-output
        System.out.println("  \033[0;1m" + (char)27 + "[34m" + entry.getFileName() + "/" + (char)27 + "[37m" + "\u001B[0m");
      }
    } catch (IOException e) {
        e.printStackTrace();
    }

    try {
      DirectoryStream<Path> stream = Files.newDirectoryStream(path);

      for (Path entry : stream) {

        if (Files.isDirectory(entry))
          continue;

        if (entry.toString().endsWith(".tar")) {
          System.out.println("  " + (char)27 + "[31m" + entry.getFileName() + (char)27 + "[37m");
          continue;
        }

        System.out.println("  " + entry.getFileName());
        //System.out.println("\033[0;1m" + entry.getFileName() + "\u001B[0m");
      }
    } catch (IOException e) {
        e.printStackTrace();
    }

    System.out.println();

    return;
  }

  public void handleRm() {

    if (cmdInfo.targetName != null) {

      if (!nameTable.containsKey(cmdInfo.targetName)) {
        System.out.println("[Err] unknown name");
        return;
      }

      nameTable.remove(cmdInfo.targetName);

      try {
        Files.delete(Paths.get(cmdInfo.targetName));
      } catch (Exception e) {
        System.out.println(e);
      }

      return;
    }

    try {
      Files.walkFileTree(Paths.get(cmdInfo.targetNamespace), new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.delete(file);
          nameTable.remove(file.toString());
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          Files.delete(dir);
          nameTable.remove(dir.toString() + "/");
          return FileVisitResult.CONTINUE;
        }
      });
    } catch(Exception e){
      e.printStackTrace();
    }

    return;
  }

  public void handleCd() {

    if (cmdInfo.targetNamespace == null) {

      if (FS_CWD.equals(FS_SESSION)) {
        System.out.println("(root namespace)");
        return;
      }

      Path cwd = Paths.get(FS_CWD);
      Path res = Paths.get(FS_CWD);

      for (int i = 0; i < cmdInfo.upCnt; i++) {
        res = cwd.getParent();

        if (res.toString().equals(FS_SESSION))
          break;
      }

      cd(res);
      return;
    }

    if (!Files.isDirectory(Paths.get(cmdInfo.targetNamespace))) {
      System.out.println("[Err] not a namespace\n");
      return;
    }

    cd(Paths.get(cmdInfo.targetNamespace));

    return;
  }

  public void cd(Path path) {

    FS_CWD = path.toString() + "/";
    System.out.println();
    return;
  }

  public static void main(String[] args) throws Exception  {

    if (args.length > 0) {
      SESSION_ID = args[0];
      FS_SESSION = "fs/" + SESSION_ID + "/";
      FS_CWD = FS_SESSION;
    }

    System.out.println("Session: " + SESSION_ID);

    CmdLine cmdLine = new CmdLine();

    createDir("fs");
    createDir("fs/" + SESSION_ID);

    importSetup(cmdLine);
    initServiceRegistry(cmdLine);

    // create done queue delete listener
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");

    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();

    channel.queueDeclare(DONE_QUEUE_NAME, false, false, false, null);
    channel.queueDeclare(FAIL_QUEUE_NAME, false, false, false, null);

    com.rabbitmq.client.Consumer doneListener = new DefaultConsumer(channel) {

      public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws java.io.IOException {

        String service = new String(body);
        cmdLine.blockedNames.remove(service);
      }
    };

    channel.basicConsume(DONE_QUEUE_NAME, true, doneListener);


    com.rabbitmq.client.Consumer failListener = new DefaultConsumer(channel) {

      public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws java.io.IOException {

        try {

        String resHash = new String(body);

        if (cmdLine.resultConsumer != null)
          cmdLine.resultConsumer.wakeup();

        if (resHash.equals("anonym")) {
          return;
        }

        for(Map.Entry<String, String> entry: cmdLine.nameTable.entrySet()) {
          if (resHash.equals(entry.getValue())) {
            System.out.println("\nadding failed name: " + entry.getKey() + "\n.>");
            cmdLine.failedNames.add(entry.getKey());
          }

        }

      } catch (Exception e) {
        System.out.println("failListener.handleDelivery()");
        System.out.println(e);
      }

      }
    };

    channel.basicConsume(FAIL_QUEUE_NAME, true, failListener);



    while (true) {
      if (FS_CWD.equals(FS_SESSION))
            System.out.print("/ " + PROMPT);
      else
        System.out.print(Paths.get(FS_CWD).getFileName()  + "/ " + PROMPT);


      String line = cmdLine.stdIn.readLine();

      if (line.length() == 0)
        continue;

      if(line.equals("q")) {
        Runtime.getRuntime().exec("rm -rf " + FS_SESSION);
        break;
      }

      if (line.equals("r")) {
        cmdLine.printNameTable();
        continue;
      }

      if (line.equals("cl")) {
        System.out.print("\033[H\033[2J");
        System.out.flush();
        System.out.println("Session: " + SESSION_ID);
        continue;
      }

      if (line.equals("cwd")) {
        System.out.println(FS_CWD + "\n");
        continue;
      }

      boolean parseCorrect = cmdLine.parseCmd(line);
      if (!parseCorrect)
        continue;

      boolean semanticCorrect = cmdLine.checkCmd();
      if (!semanticCorrect)
        continue;

      //cmdLine.cmdInfo.print();


      if (cmdLine.cmdInfo.isQuery) {
        cmdLine.handleQuery();
        continue;
      }

      if (cmdLine.cmdInfo.isLs) {
        cmdLine.handleLs();
        continue;
      }

      if (cmdLine.cmdInfo.isCd) {
        cmdLine.handleCd();
        continue;
      }

      if (cmdLine.cmdInfo.isRm) {
        cmdLine.handleRm();
        continue;
      }


      cmdLine.handleCmd();
    }

    stdIn.close();

    channel.close();
    connection.close();


    return;
  }


}
