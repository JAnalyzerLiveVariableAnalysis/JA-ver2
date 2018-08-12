import java.util.ArrayList;
import java.util.List;

public class Test {

	public static void main(String[] args) {
		int how = 1;
		int are = 0;
		int you = are + how;
		int a, b, c, d, e, hello;
		List<Integer> array = new ArrayList<Integer>();
		a = 10; // def{a} use{} in{} out{a}
		b = 15; // def{b} use{} in{a} out{a, b}
		c = b + a + 20; // def{c} use{b, a} in{a, b} out{a}
		d = a; // def{d} use{a} in{a} out{a, d}
		for (int i = a; i < 10; i++) {
			array.add(++i);
		}
		for (Integer integer : array) {
			System.out.print(integer + a);
			integer += a;
		}
		if (d == 15 && a == b || c == 30) {
			e = a; // def{e} use{a} in{a, d} out{d, e}
			a = d; // def{a} use{d} in{d, e} out{a, e}
		} else {
			b = c; // def{b} use{e} in{a, e} out{a, b}
			hello = a; // def{hello} use{a} in{a, b} out{hello, b}
			a = hello + b; // def{a} use {hello, b} in{hello, b} out{}
		}
		hello = test(test(a, c), test(b, d));
	}
	
	static int test(int num, int n) {
		int a = 0;
		a = num * n;
		return a;
	}
}
