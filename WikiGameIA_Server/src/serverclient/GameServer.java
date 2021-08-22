package serverclient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import utilities.Queue;

/**
 * GameServer application for hosting Wikigame servers.
 * 
 * @author Michael
 *
 */
public class GameServer {
	static int portNumber = 25566; //default port#
	
	private static Selector selector;
	private static ServerSocketChannel socketChannel;
	private static SelectionKey key;
	
	/**
	 *  IP Address-keyed userlist hashmap
	 */
	private static HashMap<String, User> userList;
	
	private static Queue writeQueue;
	
	/**
	 * Gamestates
	 * 0: Lobby
	 * 1: Game
	 * 2: Post
	 */
	private static int gameState;
	
	/**
	 * Switch which calls different a more specific handle method depending
	 * on the server game state.
	 */
	static void handle() {
		if(key.isAcceptable()) { // New user connection
			SocketChannel clientChannel;
			try {
				clientChannel = socketChannel.accept();
				clientChannel.configureBlocking(false);
				clientChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
				//Register read and write operations to client SocketChannel
				
				System.out.println("Client connected at " + clientChannel.getLocalAddress());
				System.out.println("Client address: " + clientChannel.getRemoteAddress());
				
				//Add user to userList with address as default username. Casting is guaranteed to be correct, so long as the channel is bound
				userList.put(clientChannel.getRemoteAddress().toString(), 
						new User(clientChannel.getRemoteAddress().toString(), clientChannel));
				
				refreshUserList(clientChannel.getRemoteAddress().toString());
			} catch (IOException e) {
				System.out.println("Error when accepting client connection!");
				e.printStackTrace();
				return;
			}
		}
		if(key.isReadable()) {
			SocketChannel readChannel = (SocketChannel) key.channel();
			ByteBuffer buffer = ByteBuffer.allocate(1024);
			try {
				try{
					readChannel.read(buffer);
				}
				catch(SocketException e) {
					defaultProcess("", readChannel);
					return;
				}
				
				String data = new String(buffer.array()).trim();
				String address = readChannel.getRemoteAddress().toString();
				
				if(defaultProcess(data, readChannel))
					return;
				
				/*
				 * DATA PROCESSING START
				 * 
				 * Expected recieved values:
				 * I|(Username) -> set username of the user (SHOULD ONLY BE CALLED IMMEDIATELY FOLLOWING CONNECTION)
				 * R -> change ready state of user to ready
				 * U -> change ready state of user to not ready
				 * Z|(article)|(article) -> Response from client for requested random browser pages.
				 * F -> User won the game
				 */
				
				//Special case for user initialization
				if(data.charAt(0) == 'I') {
					if(data.length() < 3) {
						System.out.println("Invalid username registration from " + readChannel.getRemoteAddress().toString());
						return;
					}
					String cUsername = data.substring(2);
					
					User tempUser = userList.remove(address);
					if(tempUser == null) { // User does not exist
						System.out.println("Nonexistent user command " + data + " from address " + address);
						return;
					}
					tempUser.setUsername(cUsername);
					userList.put(address, tempUser);
					
					System.out.println("User " + userList.get(address).getUsername() + " set for address " + readChannel.getRemoteAddress().toString());
					
					writeQueue.push("N|" + cUsername);
					
					return;
				}
				//Case for game start ready
				if(data.charAt(0) == 'Z') {
					String start = data.substring(2, data.indexOf('|', 2));
					String target = data.substring(data.indexOf('|', 2) + 1);
					writeQueue.push("S|" + start + "|" + target);
					System.out.println("GAME STARTED!");
					System.out.println("Start: " + start + " Goal: " + target);
				}
				
				//User not registered (not in userlist)
				if(!userList.containsKey(address)) {
					System.out.println("Command " + data + " from unregistered address " + readChannel.getRemoteAddress().toString());
					return;
				}
				
				String username = userList.get(address).getUsername();
				
				switch(data.charAt(0)) {
				case 'R':
					userList.get(address).ready();
					System.out.println("User " + username + " is ready.");
					writeQueue.push("R|" + username);
					break;
				case 'U':
					userList.get(address).unready();
					System.out.println("User " + username + " is not ready.");
					writeQueue.push("U|" + username);
					break;
				case 'F':
					writeQueue.push("W|" + username);
					System.out.println("User " + username + " has won!");
					break;
				default:
					System.out.println("Invalid command " + data + " from user: " + userList.get(address).getUsername());
				}
				
			} catch (IOException e) {
				System.out.println("Error while handling lobby data!");
				e.printStackTrace();
				System.exit(0);
				return;
			}
		}
	}
	
	/**
	 * Handler for data that is always valid (regardless of gamestate). Returns true if the connection
	 * has been closed.
	 * @return 
	 * @throws IOException 
	 */
	static boolean defaultProcess(String data, SocketChannel readChannel) throws IOException {
		if(data.length() <= 0) {
			String address = readChannel.getRemoteAddress().toString();
			readChannel.close();
			System.out.println("Connection at address " + address + " closed.");
			writeQueue.push("D|" + userList.get(address).toString());
			userList.remove(address);
			return true;
		}
		return false;
	}
	
	/**
	 * Tells the clients to add the entire user-list. Used when adding new clients to update
	 * the entire list.
	 */
	static void refreshUserList(String address) {
		//for: user, add every use that is not themself
		for(User user : userList.values()) {
			if(user != userList.get(address))
				userList.get(address).writeQueue.push("N|" + user.getUsername());
		}
	}
	
	/**
	 * Lobby nio data handler.
	 * 
	 * <pre>Expected recieved values:<br>
	 * I|(Username) -> set username of the user (SHOULD ONLY BE CALLED IMMEDIATELY FOLLOWING CONNECTION)<br>
	 * R -> change ready state of user to ready<br>
	 * U -> change ready state of user to not ready<br>
	 * Z|(article)|(article) -> Response from client for requested random browser pages.</pre>
	 */
	static void handleLobby() {
	}
	
	/**
	 * Write to the key channel. Returns true if write operation succeeded.
	 * @param key
	 * @return
	 * @throws IOException 
	 */
	private static void writeProcess(SelectionKey key) throws IOException {
		if(key.isWritable()) {
			SocketChannel channel = (SocketChannel) key.channel();
			ByteBuffer buffer;
			
			User user = userList.get(channel.getRemoteAddress().toString());
			
			while(!user.writeQueue.isEmpty()) {
				buffer = ByteBuffer.wrap(user.writeQueue.pop().getBytes());
				try {
					channel.write(buffer);
				} catch(IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public static void main(String[] args) throws IOException {
		BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
		
		gameState = 0;
		userList = new HashMap<>();
		selector = Selector.open();
		writeQueue = new Queue();
		
		//Socketchannel creation and binding
		socketChannel = ServerSocketChannel.open();
		socketChannel.configureBlocking(false);
		socketChannel.bind(new InetSocketAddress(InetAddress.getByName("localhost"), 25566));
		
		socketChannel.register(selector, SelectionKey.OP_ACCEPT);
		
		System.out.println("WikiGame server started on port: " + portNumber);
		/*
		 * Print list of commands
		 */
		
		System.out.println("***Commands***\n"
				+ "start		Starts the game\n"
				+ "abort		Ends the game, regardless of game state.");
		
		int written = 0;
		
		while(true) {
			
			//Server commands
			String s = "";
			while(input.ready()) {
				s += (char)input.read();
			}
			System.out.print(s);
			s = s.trim();
			
			if(s.equals("start"))
				startGame();
			else if(s.equals("abort"))
				writeQueue.push("A");
			else if(!s.equals(""))
				System.out.println("Invalid command!");
			
			if(selector.select() <= 0)
				continue;
			
			Set<SelectionKey> keySet = selector.selectedKeys();
			Iterator<SelectionKey> keyIterator = keySet.iterator();
			
			//Process all SelectionKeys available
			while(keyIterator.hasNext()) {
				key = keyIterator.next();
				keyIterator.remove();
				
				if(key.isValid())
					writeProcess(key);
				
				handle();
			}
			//Distrbute all write operations to all users
			while(!writeQueue.isEmpty()) {
				for(User user : userList.values()) {
					user.writeQueue.push(writeQueue.peek());
				}
				writeQueue.pop();
			}
		}
	}
	
	static void startGame() {
		//Request articles from an individual client
		if(!userList.values().iterator().hasNext()) {
			System.out.print("No users! Cannot start the game.");
			return;
		}
		
		User user = userList.values().iterator().next();
		user.writeQueue.push("Z");
		System.out.println("Requesting random page from user " + user.getUsername());
	}
}
