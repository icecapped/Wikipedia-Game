package utilities;

/**
 * Node-based stack for FXBrowser history purposes.
 * 
 * @author Michael
 */
public class Stack {

	Node top;
	
	public Stack() {
	}
	
	public Stack(String s) {
		top = new Node(s, null);
	}
	
	public void push(String s) {
		Node temp = new Node(s, top);
		top = temp;
	}
	
	public String pop() {
		String val = top.value;
		top = top.next;
		return val;
	}
	
	public String peek() {
		return top.value;
	}
	
	public boolean isEmpty() {
		if(top == null)
			return true;
		return false;
	}
}
