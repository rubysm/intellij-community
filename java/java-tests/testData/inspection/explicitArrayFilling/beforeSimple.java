// "Replace loop with 'Arrays.setAll' method call" "true"
import java.util.Arrays;
import java.util.List;

public class Test {
  public void test(List<Integer> ints) {
    int[] arr = new int[ints.size()];
    for(int <caret>i = 0; arr.length > i; i++) {
      arr[i] = ints.get(i);
    }
    System.out.println(Arrays.toString(arr));
  }
}