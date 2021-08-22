package utilities;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Scanner;
import java.util.regex.Matcher;

/**
 * DEPRECATED
 * 
 * @author Michael
 *
 */
public class PageConfigurator {
	private	URL currentURL;
	private String rawHTML;
	private String gameHTML;
	
	private static final String BODY_MARKER = "<body";
	private static final String CONTENT_MARKER = "<div id=\"bodyContent\"";
	private static final String CONTENT_END_MARKER = "<div id='mw-data-after-content'>";
	private static final String FOOTER_MARKER = "</footer>";
	
	private static final String HREF_MARKER = "href=\"";
	private static final String SRC_MARKER = "src=\"//";
	
	/**
	 * Constructor for PageConfigurator class. This constructor sets the URL for the PageConfigurator
	 * and automatically loads and reformats the page.
	 * 
	 * Exceptions are thrown as this class is a utility class, and the implementing class is
	 * expected to handle the exceptions.
	 * 
	 * @param URL
	 * @throws IOException
	 * @throws MalformedURLException
	 */
	public PageConfigurator(String URL) throws IOException, MalformedURLException {
		navigate(URL);
	}
	
	
	public PageConfigurator() {
	}
	
	/**
	 * Loads the raw HTML given the PageConfigurator's current URL.
	 * 
	 * @return boolean	true if the webpage was loaded successfully
	 */
	private boolean loadPage() {
		rawHTML = "";
		gameHTML = "";
		try {
			BufferedReader webReader = new BufferedReader(new InputStreamReader(currentURL.openStream()));
			String line;
			while((line = webReader.readLine()) != null) {
				rawHTML += line;
				//System.out.println(line);
			}
			rawHTML = rawHTML.substring(4); //not sure why there's an extra null
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	/**
	 * Processes the raw wikipedia page into a form suitable for the game. Specifically, it removes all body content
	 * that is not part of the main article body. (Ex. headers, footers, citations, sidebars) As well as all non-game links.
	 * (Ex. internal navigation links, citation notes)
	 */
	private void processPage() {
		/*
		 * Delete from first > after "<body" to "<div id="bodyContent""
		 */
		
		int bodyDivStart = rawHTML.indexOf(BODY_MARKER);
		int bodyDivEnd = rawHTML.substring(bodyDivStart).indexOf(">"); //RELATIVE
		int contentStart = rawHTML.indexOf(CONTENT_MARKER);
		
		/*
		 * Delete from > after /bodycontent to </footer>
		 */
		int contentEnd = rawHTML.indexOf(CONTENT_END_MARKER) + CONTENT_END_MARKER.length();

		/*
		 * Delete from contentEnd to </footer>
		 */
		int footerEnd = rawHTML.indexOf(FOOTER_MARKER);
		
		//Inclusion lines
		gameHTML = rawHTML.substring(0, bodyDivStart + bodyDivEnd + 1); //head
		gameHTML += rawHTML.substring(contentStart, contentEnd); //content
		gameHTML += rawHTML.substring(footerEnd); //end scripts
		
		
		
		//Replace relative links with explicit wikipedia links
		int relativeLinkIndex = -1;
		int pageLoc = 0;
		
		gameHTML = gameHTML.replaceAll("href=\"", Matcher.quoteReplacement("href=\"" + "https://en.wikipedia.org"));
		gameHTML = gameHTML.replace("src=\"", Matcher.quoteReplacement("src=\"" + "https:"));
		
		while((relativeLinkIndex = gameHTML.indexOf(HREF_MARKER, pageLoc) + HREF_MARKER.length()  - 1) != -1) {
			if(relativeLinkIndex == 5)
				break;
			gameHTML = gameHTML.substring(0, relativeLinkIndex) + "https://en.wikipedia.org/" + gameHTML.substring(relativeLinkIndex);
			pageLoc = relativeLinkIndex;
		}
	}
	
	/**
	 * Navigate to the given URL and load the reformatted Wikipedia
	 * page for the game.
	 * 
	 * @param
	 * @return Full HTML page of the game-ready Wikipedia page. 
	 * @throws MalformedURLException If an invalid URL is provided.
	 * @throws IOException If an error occurred while loading the webpage.
	 */
	public String navigate(String URL) throws IOException, MalformedURLException {
		System.out.println("a: " + URL);
		currentURL = new URL(URL);
		if(!loadPage())
			throw new IOException();
		processPage();
		return gameHTML;
	}
	
	/**
	 * Gets the processed HTML in the form of a string.
	 * @return String 
	 */
	public String getHTML() {
		return gameHTML;
	}
	
	/**
	 * For testing purposes. Takes a URL from console and then 
	 * prints out the reformatted HTML.
	 * 
	 * @param args
	 * @throws IOException 
	 * @throws MalformedURLException 
	 */
	public static void main(String[] args) throws MalformedURLException, IOException {
		Scanner sc = new Scanner(System.in);
		
		PageConfigurator pc = new PageConfigurator(sc.nextLine());
		//System.out.print(pc.getHTML());
		sc.close();
	}
}
