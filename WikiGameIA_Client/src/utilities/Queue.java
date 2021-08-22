package utilities;

/**
 * Node-based stack for network NIO writing.
 * 
 * @author Michael
 */
public class Queue {

	Node head;
	Node tail;
	
	public Queue() {
	}
	
	public Queue(String s) {
		head = new Node(s, null);
		tail = head;
	}
	
	public void push(String s) {
		if(isEmpty()) {
			head = new Node(s, null);
			tail = head;
			return;
		}
		
		tail.next = new Node(s, null);
		tail = tail.next;
	}
	
	public String pop() {
		String val = head.value;
		head = head.next;
		return val;
	}
	
	public String peek() {
		return head.value;
	}
	
	public boolean isEmpty() {
		if(head == null)
			return true;
		return false;
	}
}
