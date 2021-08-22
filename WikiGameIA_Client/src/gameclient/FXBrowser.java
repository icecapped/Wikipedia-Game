package gameclient;

import java.io.IOException;
import java.net.MalformedURLException;

import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import utilities.*;

public class FXBrowser {

	private WebView view;
	private WebEngine engine;
	
	private PageConfigurator config;
	private Stack history;
	
	private static final String WIKI_URL = "https://en.wikipedia.org/wiki/";
	
	public FXBrowser() {
	}
	
	public FXBrowser(String URL) throws MalformedURLException, IOException {
		view = new WebView();
		engine = view.getEngine();
		
		history = new Stack(URL);
		engine.load(URL);
	}
	
	public WebEngine getEngine() {
		return engine;
	}
	
	public WebView getView() {
		return view;
	}
	
	public void start(String URL) {
		engine.load(WIKI_URL + URL);
	}
	
	public void reconfig() {
			
			String s = engine.getLocation();
			System.out.println(s);
			try {
				engine.loadContent(config.navigate(s));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		
	}
	
	/**
	 * Returns true if no changes needed to be made. Returns false if the webpage was not a 
	 * valid wikipedia page.
	 * 
	 */
	public boolean validCheck() {
		System.out.println("loc: " + engine.getLocation());
		if(engine.getLocation().substring(0, WIKI_URL.length()).equals(WIKI_URL))
			return true;
		else
			return false;
	}
	
	/**
	 * Checks if the current URL is a valid game URL. If not, it will return to the previous page in the history.
	 * If it is, it will update the history with the current URL.
	 */
	public void gameNav() {
		if(validCheck()) {
			updateHistory();
			return;
		}
		engine.load(history.peek());
	}
	
	public void updateHistory() {
		history.push(engine.getLocation());
	}
	
	public String getArticleName() {
		return engine.getLocation().substring(WIKI_URL.length());
	}
	
	public void loadRandomArticle() {
		engine.load(WIKI_URL + "Special:Random");
	}
	
	public void back() {
		if(!history.isEmpty()) {
			String tempURL = history.pop();//Pop the current page
			
			if(!history.isEmpty())
				engine.load(history.pop()); //Get previous page
			else
				engine.load(tempURL);
		}
	}
}
