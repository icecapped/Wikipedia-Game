package gameclient;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.Iterator;
import java.util.Set;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.Worker.State;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import utilities.Queue;

public class GameClient extends Application {

	/*
	 * To-Server Data
	 * 
	 * Expected recieved values:
	 * I|(Username) -> set username of the user (SHOULD ONLY BE CALLED IMMEDIATELY FOLLOWING CONNECTION)
	 * R -> change ready state of user to ready
	 * U -> change ready state of user to not ready
	 * Z|(article)|(article) -> Response from client for requested random browser pages.
	 */

	private static final Font TITLE_FONT = Font.font("Segoe UI", 36);
	private static final Font TARGET_FONT = Font.font("Segoe UI", 14);
	
	//Game browser
	FXBrowser browser;
	
	//Network variables
	private int serverPort = 25566; // default server port
	
	private InetSocketAddress serverAddr;
	private Selector selector;
	
	private boolean netThreadActive = false;
	private Thread netThread;
	
	private Queue writeQueue;
	
	private boolean skip = false;
	
	//Static stage (Should only be one GameClient at any time)
	static Stage stage;
	
	String target = "";
	
	/*
	 * Game scene elements
	 */
	private Scene gameScene;
	private Label location;
	private Label targetLabel;
	private Button backButton;
	
	/*
	 * Post-game scene elements
	 */
	private Scene postScene;
	private Button postMenuButton;
	private Button postLobbyButton;
	private Button postExitButton;
	private Label postGameMessage;
	
	/*
	 * Menu scene elements
	 */
	private Scene menuScene;
	private TextField usernameField;
	private TextField ipField;
	private Button connectButton;
	private Button exitButton;
	private Label title;
	private Label errorLabel;
	
	/*
	 * Lobby scene elements
	 */
	private Scene lobbyScene;
	private Label lobbyTitle;
	private Button readyButton;
	private ObservableList<String> playerList;
	private ListView<String> playerListView;
	
	/*
	 * Game operation and other variables
	 */
	private String ip;
	private boolean ready;
	
	//Loading variables for when server requests random articles
	private int loadingArticle = 0; //0 indicates not loading, 1 indicates waiting for load, 3 indicates load completed
	private String[] randomPages;
	
	/**
	 * Start of the GUI application. Automatically called by launch() in GameClient.main().
	 */
	@Override
	public void start(Stage stage) throws Exception {
		GameClient.stage = stage;
		location = new Label();

		//Browser client instantiation
		browser = new FXBrowser("https://en.wikipedia.org/wiki/Special:Random");

		String s = location.textProperty().getValue();
		System.out.println(location.getText());
		
		//Initialize all scenes
		initMenuScene();
		initLobbyScene();
		initGameScene();
		initPostScene();
		
		//Set stage properties
		stage.setScene(menuScene);
		stage.setResizable(false);
		stage.setHeight(600);
		stage.setWidth(800);
		stage.setTitle("The Wiki Game");
		
		stage.show();
	}
	
	
	public static void main(String[] args) {
		launch(args);
	}
	
	
	
	/**
	 * Attempts connection with the given IP address.
	 * @param ip
	 * @throws Exception
	 */
	private void connect(String ip) throws Exception {
		serverAddr = new InetSocketAddress(ip, serverPort);
		
		selector = Selector.open();
		
		//Configure socketchannel
		SocketChannel socketChannel = SocketChannel.open();
		socketChannel.configureBlocking(false);
		socketChannel.connect(serverAddr);
		socketChannel.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_WRITE | SelectionKey.OP_READ);
	}
	
	/**
	 * Setup game browser.
	 */
	private void initBrowser() {
		randomPages = new String[3];
		
		/*
		 * Browser listener for when a new page is loaded
		 */
		browser.getEngine().getLoadWorker().stateProperty().addListener(new ChangeListener<State>() {
			public void changed(ObservableValue ov, State oldState, State newState) {
				if (newState == State.SUCCEEDED && !browser.getEngine().getLocation().equals("")) {
					if(loadingArticle == 0) {
						//Game navigation method (history, url verification)
						browser.gameNav();
						
						location.textProperty().set(browser.getEngine().getLocation());
						
						
						//If player has reached the target location
						System.out.println(browser.getArticleName());
						System.out.println(target);
						if(browser.getArticleName().equals(target)) {
							gameEnd(true, null);
							writeQueue.push("F");
						}
					}
					else if(loadingArticle >= 3 && !(browser.getArticleName().equals("Special:Random"))) {
						writeQueue.push("Z|" + randomPages[0] + "|" + randomPages[1]);
						loadingArticle = 0;
					}
					else if(!(browser.getArticleName().equals("Special:Random"))) {
						randomPages[loadingArticle - 1] = browser.getArticleName();
						browser.loadRandomArticle();
						loadingArticle++;
					}
				}
			}
		});
	}
	
	/**
	 * Initialize the network thread responsible for handling read/write parallel to the GUI thread.
	 */
	private void startNetThread() {
		netThreadActive = true;
		writeQueue = new Queue();
		
		Task netTask = new Task<Void>() {
			@Override
			protected Void call() throws Exception {
				while(netThreadActive) {
					Thread.sleep(200); // Only do network updates every 200 ms, too many updates will block GUI and render it laggy
					Platform.runLater(new Runnable() {
						@Override
						public void run() {
							processNet();
						}
					});
				}
				return null;
			}
		};
		
		netThread = new Thread(netTask);
		netThread.setDaemon(true); //Daemon net processing thread
		netThread.start();
	}
	
	/**
	 * Processing method for the network thread. Is called every network tick, and is responsible for processing
	 * the keyset given by the NIO selector.
	 */
	private void processNet() {
		try {
			if(selector.select() <= 0)
				return;
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
		
		Set<SelectionKey> keySet = selector.selectedKeys();
		Iterator<SelectionKey> keyIterator = keySet.iterator();
		
		SelectionKey key;
		while(keyIterator.hasNext() && netThreadActive) {
			key = keyIterator.next();
			keyIterator.remove();
			handle(key);
		}

	}
	
	private void handle(SelectionKey key) {
		writeProcess(key);
		
		if(stage.getScene() == menuScene) {
			handleMenu(key);
		}
		else if(stage.getScene() == gameScene) {
			handleGame(key);
		}
		else if(stage.getScene() == lobbyScene) {
			handleLobby(key);
		}
		//Recieve no data in post-screen
	}
	
	private void writeProcess(SelectionKey key) {
		if(key.isWritable()) {
	         SocketChannel channel = (SocketChannel) key.channel();
	         ByteBuffer buffer;
	         while(!writeQueue.isEmpty()) {
	        	 buffer = ByteBuffer.wrap(writeQueue.pop().getBytes());
	        	 try {
					channel.write(buffer);
				} catch (IOException e) {
					e.printStackTrace();
				}
	         }
	         
		}
	}
	
	/**
	 * Handler for data that is always valid (regardless of gamestate). Namely includes checking if
	 * the connection was disconnected.
	 * 
	 * @return 
	 * @throws IOException 
	 */
	private void defaultProcess(String data, SocketChannel readChannel) throws IOException {
		if(data.length() <= 0) {
			String address = readChannel.getRemoteAddress().toString();
			readChannel.close();
			System.out.println("Connection at address " + address + " closed.");
			netThreadActive = false;
			stage.setScene(menuScene);
		}
	}
	
	/*
	 * Net Selectionkey handling methods
	 */
	private void handleMenu(SelectionKey key) {
		if(key.isConnectable()) {
			SocketChannel channel = (SocketChannel) key.channel();
			while(channel.isConnectionPending()) {
				try {
					channel.finishConnect();
					ip = channel.getRemoteAddress().toString();
					
				} catch (ConnectException edsa) {
					System.exit(1027);
				} catch (IOException e) {
					errorLabel.setText("Connection failed");
					key.cancel();
					e.printStackTrace();

					netThreadActive = false;
					
					return;
				}
			}
			
			writeQueue.push("I|" + usernameField.getText());
			stage.setScene(lobbyScene);
			updateLobby();
		}
	}
	
	private void handleGame(SelectionKey key) {
		if(key.isReadable()) {
			SocketChannel channel = (SocketChannel) key.channel();
			ByteBuffer buffer = ByteBuffer.allocate(1024);
			try{
				channel.read(buffer);
				
				String data = new String(buffer.array()).trim();
				defaultProcess(data, channel);
				
				System.out.println(usernameField.getText() + " : " + data);
				
				/*
				 * DATA PROCESSING START
				 * 
				 * Expected recieved values:
				 * 
				 * W|(username) - > User has won the game
				 * A -> Game aborted
				 */
				
				switch(data.charAt(0)) {
				case 'W':
					gameEnd(false, data.substring(2));
					return;
				case 'A':
					stage.setScene(lobbyScene);
					return;
				}
				
				switch(data.charAt(0)) {
				}
			}
			catch(IOException e) {
				e.printStackTrace();
				System.exit(1027);
			}
		}
	}
	
	private void handleLobby(SelectionKey key) {
		if(key.isReadable()) {
			SocketChannel channel = (SocketChannel) key.channel();
			ByteBuffer buffer = ByteBuffer.allocate(1024);
			try{
				channel.read(buffer);
				
				String data = new String(buffer.array()).trim();
				defaultProcess(data, channel);
				
				System.out.println(usernameField.getText() + " : " + data);
				
				/*
				 * DATA PROCESSING START
				 * 
				 * Expected recieved values:
				 * 
				 * N|(username) -> New user in lobby
				 * D|(username) -> User left lobby
				 * R|(username) -> Change ready state of user to ready
				 * U|(username) -> Change ready state of user to unready
				 * F|(username) -> User finished the game
				 * Z -> Server request for random browser pages
				 * S|(start)|(target) -> Start game with initial and target wiki pages
				 */
				
				//Special case for gamestate and random browser request
				switch(data.charAt(0)) {
				case 'Z':
					browser.loadRandomArticle();
					loadingArticle++;
					return;
				case 'S':
					String start = data.substring(2, data.indexOf('|', 2));
					String target = data.substring(data.indexOf('|', 2) + 1);
					browser.start(start);
					this.target = target;
					stage.setScene(gameScene);
					targetLabel.setText("Target: " + target);
					
					return;
				}
				
				String cUsername = data.substring(2);
				
				switch(data.charAt(0)) {
				case 'N':
					playerList.add(cUsername);
					break;
				case 'D':
					playerList.remove(cUsername);
					break;
				case 'R':
					if(playerList.indexOf(cUsername) != -1) {
						playerList.set(playerList.indexOf(cUsername), cUsername + " (Ready)");
						System.out.println("test");
					}
					break;
				case 'U':
					if(playerList.indexOf(cUsername + " (Ready)") != -1) {
						playerList.set(playerList.indexOf(cUsername + " (Ready)"), cUsername);
					}
					break;
				}
			}
			catch(IOException e) {
				e.printStackTrace();
				System.exit(1027);
			}
		}
	}
	
	private void updateLobby() {
		lobbyTitle.setText("Lobby " + ip);
	}
	
	private void initGameScene() {
		backButton = new Button("Back");
		targetLabel = new Label("Target" + target);
		targetLabel.setFont(TARGET_FONT);
		
		//Button Binding
		backButton.setOnMouseClicked(new EventHandler<MouseEvent>() {
			public void handle(MouseEvent arg0) {
				browser.back();
			}
		});
		
		HBox navBar = new HBox(backButton, location);
		
		HBox.setMargin(location, new Insets(3, 10, 0, 10));
		HBox.setMargin(backButton, new Insets(0, 10, 0, 10));
		VBox.setMargin(targetLabel, new Insets(10, 10, 0, 10));
		
		gameScene = new Scene(new VBox(10, targetLabel, navBar, browser.getView()));
		
		//Initiialize browser
		initBrowser();
	}
	
	private void initPostScene() {
		//Node instantiation
		postMenuButton = new Button("Menu");
		postLobbyButton = new Button("Lobby");
		postExitButton = new Button("Exit");
		postGameMessage = new Label("placeholder");
		
		//Node configuration
		postGameMessage.setFont(TITLE_FONT);
		postExitButton.setCancelButton(true);
		postMenuButton.setPrefSize(50, 20);
		postLobbyButton.setPrefSize(50, 20);
		postExitButton.setPrefSize(50, 20);
		
		//Button Binding
		postMenuButton.setOnMouseClicked(new EventHandler<MouseEvent>() {
			public void handle(MouseEvent arg0) {
				stage.setScene(menuScene);
			}
		});
		postLobbyButton.setOnMouseClicked(new EventHandler<MouseEvent>() {
			public void handle(MouseEvent arg0) {
				stage.setScene(lobbyScene);
			}
		});
		postExitButton.setOnMouseClicked(new EventHandler<MouseEvent>() {
			public void handle(MouseEvent arg0) {
				stage.close();
			}
		});
		
		
		//Parent and layout management
		HBox hbox = new HBox(50, postMenuButton, postLobbyButton, postExitButton);
		hbox.setAlignment(Pos.BASELINE_CENTER);
		VBox vbox = new VBox(50, postGameMessage, hbox);
		vbox.setAlignment(Pos.CENTER);
		
		postScene = new Scene(vbox);
		vbox.prefWidth(800);
		vbox.prefHeight(600);
	}
	
	private void initMenuScene() {
		ipField = new TextField();
		usernameField = new TextField();
		connectButton = new Button("Connect");
		exitButton = new Button("Exit");
		title = new Label("The Wiki Game");
		errorLabel = new Label();
		
		//Button Binding
		connectButton.setOnMouseClicked(new EventHandler<MouseEvent>() {
			public void handle(MouseEvent arg0) {
				if(netThreadActive)
					return;
				
				errorLabel.setText("");
				ip = ipField.getText();
				try {
					connect(ip);
					startNetThread();
				} catch(UnresolvedAddressException | UnsupportedAddressTypeException e) {
					errorLabel.setText("Invalid address!");
				}catch (Exception e) {
					errorLabel.setText(e.getMessage());
					e.printStackTrace();
				}
				
			}
		});
		exitButton.setOnMouseClicked(new EventHandler<MouseEvent>() {
			public void handle(MouseEvent arg0) {
				stage.close();
			}
		});
		
		//JavaFX layouts and parents
		VBox connectBox = new VBox(usernameField, ipField, connectButton, errorLabel);
		HBox buttons = new HBox(50, connectBox, exitButton);
		VBox vbox = new VBox(50, title, buttons);
		vbox.setAlignment(Pos.CENTER);
		buttons.setAlignment(Pos.CENTER);
		
		ipField.setPromptText("IP Address");
		ipField.setPrefWidth(150); 
		usernameField.setPromptText("Username");
		
		title.setFont(TITLE_FONT);
		
		connectBox.setAlignment(Pos.BASELINE_CENTER);
		
		menuScene = new Scene(vbox);
		vbox.prefWidth(800);
		vbox.prefHeight(600);
	}
	
	private void initLobbyScene() {
		lobbyTitle = new Label("Lobby: " + ip);
		readyButton = new Button("Ready");
		playerListView = new ListView<>();
		playerList = FXCollections.observableArrayList();
		
		readyButton.setOnMouseClicked(new EventHandler<MouseEvent>() {
			public void handle(MouseEvent arg0) {
				if(!ready) {
					writeQueue.push("R");
					readyButton.setText("Unready");
					ready = !ready;
				}
				else {
					writeQueue.push("U");
					readyButton.setText("Ready");
					ready = !ready;
				}
			}
		});
		
		lobbyTitle.setFont(TITLE_FONT);
		readyButton.setPrefSize(150, 50);
		playerListView.setItems(playerList);
		
		VBox rightBox = new VBox(readyButton);
		VBox leftBox = new VBox(playerListView);
		HBox hbox = new HBox(leftBox, rightBox);
		VBox root = new VBox(lobbyTitle, hbox);
		
		leftBox.setPrefSize(500, 100);
		leftBox.setMaxSize(500, 100);
		HBox.setMargin(leftBox, new Insets(20, 30, 20, 20));
		HBox.setMargin(rightBox, new Insets(5, 20, 20, 30));
		VBox.setMargin(lobbyTitle, new Insets(20, 20, 0, 20));
		VBox.setMargin(readyButton, new Insets(15, 0, 0, 0));
		
		lobbyScene = new Scene(root);
	}
	
	/**
	 * Method called when the game is finished, either through a player win or server loss notification.
	 * The method switches to the post-game scene.
	 * @param win If the player won the game
	 */
	private void gameEnd(boolean win, String winner) {
		//Switch scenes to menu/win screen
		if(win) {
			postGameMessage.textProperty().set("You've won!");
		}
		else {
			postGameMessage.textProperty().set(winner + " has won!");
		}
		stage.setScene(postScene);
	}
	
	/**
	 * Closes the rest of the application once the GUI is closed.
	 */
	public void stop() {
		System.exit(0);
	}
	
}
