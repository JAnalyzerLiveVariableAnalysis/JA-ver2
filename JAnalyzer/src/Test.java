import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public class Test {

	public static void main(String[] args) {
		int how = 1;
		int are = 0;
		int you = are + how;
		int a, b, c, d, e, hello;
		List<Integer> array = new ArrayList<Integer>();
		a = 10;
		b = 15;
		c = b + a + 20;
		d = a;
		for (int i = a + b; i < b; i++) {
			array.add(++i);
		}
		for (int i = b, k = 2; i < 20; i++) {
			array.add(i);
		}
		for (Integer integer : array) {
			System.out.print(integer + a);
			integer += a;
		}
		if (d == 15 && a == b || c == 30) {
			e = a;
			a = d;
		} else {
			b = c;
			hello = a;
			a = hello + b;
		}
		hello = test(test(a, c), test(b, d));
	}
	
	static int test(int num, int n) {
		int a = 0;
		a = num * n;
		return a;
	}
}
