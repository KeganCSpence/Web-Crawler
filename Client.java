import java.net.*;
import java.io.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * This Client will parse the url, provided from the command line at args[0], and
 * extract all the href values in the anchor tags and store any unique links in a hashmap.
 * If the current depth being parsed does not return any new urls the client will stop trying to crawl any deeper levels.
 * Then the list is printed to the console.
 * 
 * @author Kegan Spence
 */
public class Client{
    protected final int MAX_LEVELS = 5;         // maximum depth to crawl
    protected HashMap<String,Boolean> webLinks; // the full list of urls
    protected int depth;                        // the current depth being parsed
    protected ArrayList<URI> urlList;           // the list of URI to be changed into HttpRequests
    protected Pattern pattern;                  // the regex to grab the links from the anchor tags 
    protected String website;                   // the website being parsed

    /**
     * Purpose:
     * Client constructor to initialize the class variables.
     * 
     * @param url - The url attained from the user from the command line at args[0]
     * 
     * @exception IllegalArgumentException - thrown by the Pattern.compile() method if the flag is not valid
     *                                       and thrown by the URI.create() method if the url provided violates RFC 2396, the program will then print the error message to stderr and exit the program
     * @exception NullPointerException - thrown by URI.create() if the string is null, the program will then print the error message to stderr and exit the program
     * @exception Exception - For any uncaught exceptions, the program will then print the error message to stderr and exit the program
     */
    public Client(String url){
        try{
            this.webLinks = new HashMap<String,Boolean>();
            this.depth = 0;
            this.urlList = new ArrayList<URI>();
            this.pattern = Pattern.compile("<a\\s[^>]*href=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
            this.website = url;
            urlList.add((URI.create("http://" + url)));
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.out.println("The provided url is invalid.");
            System.exit(-1);
        } catch (NullPointerException e) {
            System.err.println(e.getMessage());
            System.exit(-2);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(-3);
        }
    };

    /**
     * Purpose:
     * Parses the webpage for all anchor tags href values, stores any new links in the hashmap, and creates any new links into a URI.
     * 
     * @param page - A String of the html body of the current page 
     * @exception   Exception - a catch-all for any exceptions thrown by the matcher.group() function and the URI.create() function. 
     *                          Any error messages are printed to stderr.
     * @return - void  
     */
    public void getLinks(String page){
        Matcher matcher = pattern.matcher(page);
        while (matcher.find()){
            try{
                if(!webLinks.containsKey(matcher.group(1))){
                    webLinks.put(matcher.group(1), false);
                    urlList.add((URI.create("http://" + website + matcher.group(1))));       
                }
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
        }
    }

    /**
     * Purpose: 
     * Converts a list of URIs to a list of HttpRequests, clears the list of URIs, then asynchronously visits each HttpRequest, converts the webpage's body to a String 
     * and passes the String of the body to the getLinks() function. Then the program waits until all requests are finished, increases the depth, prints the current 
     * depth and list of links to the console (only will print if new links are added to the list).
     * This process will repeat until the current depth equals MAX_LEVELS or if the previous attempt to find new links returns no new links.
     * 
     * Notes:
     * If an exception is thrown during the while loop the program will continue until the urlList is empty or max depth level has been reached
     * 
     * @exception Exception - This is a catch-all for any exceptions thrown while creating the list of HttpRequests or from the creating or joining of the CompletableFuture array.
     *                        Any error messages are printed to stderr.
     * @return - void
     */
    public void crawl(){
        while (depth < MAX_LEVELS && urlList.size() > 0){
            try{
                List<HttpRequest> requests = urlList.stream()
                    .map( url -> HttpRequest.newBuilder(url))
                    .map(aBuilder -> aBuilder.build())
                    .collect(Collectors.toList());
                HttpClient client = HttpClient.newHttpClient();
                urlList.clear();
                CompletableFuture<?>[] asyncs = requests.stream()
                    .map( request -> client
                        .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenApply(HttpResponse::body)
                        .thenAccept(this::getLinks))
                    .toArray(CompletableFuture<?>[]::new);
                CompletableFuture.allOf(asyncs).join();   
                depth++;
                if (urlList.size() != 0){        
                    System.out.println("Depth: " + depth);
                    this.print();
                }
            } catch(Exception e) {
                System.err.println(e.getMessage());
            }
        }    
    }
    /**
     * Purpose:
     * Iterates through the hashmap of href values and prints each one to the console in the format "[/folderA/fileA, folderB/fileB, /]"
     * Will print[/] if empty
     * 
     * @return - void
     */
    public  void print(){
        System.out.print("[");
        if(!webLinks.isEmpty()){
            for(String link: webLinks.keySet()){
                System.out.print(link + ", ");
            }
        }
        System.out.println("/]");
    }
    public static void main(String[] args){
        if (args.length > 0){
            Client client = new Client(args[0]);
            client.crawl();
        } else {
            System.out.println("Must include url in the command line arguments.");
        }
    }
}
