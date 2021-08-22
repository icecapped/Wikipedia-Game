package serverclient;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

import utilities.Queue;

public class User {
	private String username;
	
	private SocketChannel channel;
	
	public Queue writeQueue;
	
	private boolean ready;
	private boolean finished;
	
	public User(String username, SocketChannel channel) {
		this.username = username;
		this.channel = channel;
		writeQueue = new Queue();
	}
	
	public void setUsername(String username) {
		this.username = username;
	}
	
	public String getUsername() {
		return username;
	}
	
	public boolean isReady() {
		return ready;
	}
	
	public boolean isFinised() {
		return finished;
	}
	
	public void ready() {
		ready = true;
	}
	
	public void unready() {
		ready = false;
	}
	
	public void finished() {
		finished = true;
	}
	
	public void reset() {
		ready = false;
		finished = false;
	}
	
	public SocketChannel getChannel() {
		return channel;
	}
}
