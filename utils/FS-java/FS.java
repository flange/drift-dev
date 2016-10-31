import java.util.*;

class FS {
  public static void main(String args[]) {

    // Create a TreeMap
    TreeMap<String, String> treemap = new TreeMap<String, String>();

    // Put elements to the map
    treemap.put("/1/foo", "/global/foo");

    treemap.put("/1/res/", "/global/res/");
    treemap.put("/1/res/c1.txt", "/global/res/c1.txt");
    treemap.put("/1/res/c1.txt", "/global/res/c1.txt");

    treemap.put("/1/res/baz/", "/global/res/baz/");
    treemap.put("/1/res/baz/ac.txt", "/global/res/baz/ac.txt");

    treemap.put("/1/bar/", "/global/bar/");
    treemap.put("/1/bar/c1.txt", "/global/bar/c1.txt");
    treemap.put("/1/bar/c1.txt", "/global/bar/c1.txt");




    SortedMap<String, String> resSubMap =  treemap.tailMap("/1/res/");

    System.out.println(resSubMap);



/*
    SortedMap<String, String> sortedMap = treemap.subMap("Key2","Key5");
    System.out.println("SortedMap Contains : " + sortedMap);

    sortedMap.remove("Key4");
*/

  }
}
