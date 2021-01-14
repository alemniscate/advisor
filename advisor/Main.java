package advisor;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.*;

import com.sun.net.httpserver.*;
import com.google.gson.*;

public class Main {

    static String accessPointAuth = "https://accounts.spotify.com";
    static String accessPointApi = "https://api.spotify.com";
    static int pageLimit = 5;
    static final String client_id = "680095e6630e46bba13522a5d2824cfb";
    static final String client_secret = "c9900e32461f45a68936a31e2613ccaa";
    static final String redirect_uri = "http://localhost:8080";
    static boolean simulateFlag = false;

    public static void main(String[] args) throws IOException, InterruptedException {
        List<String> argList = Arrays.asList(args);
        Map<String, String> argMap = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            argMap.put(argList.get(i), argList.get(i + 1));
        }
        if (!argMap.isEmpty()) {
            if (argMap.get("-access") != null) {
                accessPointAuth = argMap.get("-access");
                System.out.println(accessPointAuth);
            }
            if (argMap.get("-resource") != null) {
                accessPointApi = argMap.get("-resource");
                System.out.println(accessPointApi);
            }
            if (argMap.get("-page") != null) {
                pageLimit = Integer.parseInt(argMap.get("-page"));
                System.out.println(pageLimit);
            }
        }

        if (accessPointAuth.startsWith("http://127.0.0.1")) {
            simulateFlag = true;
        }

        Map<String, List<String>> map = new HashMap<>();

        map.put("new", List.of("---NEW RELEASES---", "Mountains [Sia, Diplo, Labrinth]", "Runaway [Lil Peep]",
                "The Greatest Show [Panic! At The Disco]", "All Out Life [Slipknot]"));

        map.put("featured", List.of("---FEATURED---", "Mellow Morning", "Wake Up and Smell the Coffee",
                "Monday Motivation", "Songs to Sing in the Shower"));

        map.put("categories", List.of("---CATEGORIES---", "Top Lists", "Pop", "Mood", "Latin"));

        map.put("playlists Mood", List.of("---MOOD PLAYLISTS---", "Walk Like A Badass", "Rage Beats",
                "Arab Mood Booster", "Sunday Stroll"));

        map.put("exit", List.of("---GOODBYE!---"));

        if (simulateFlag) {
            execSim();
        } else {
            exec();
        }
    }

    static void exec() throws IOException, InterruptedException {
        Action ac = new Action(accessPointAuth, accessPointApi, pageLimit);
        Scanner scanner = new Scanner(System.in);
        String command = "";
        String json = "";
        while (!"exit".equals(command)) {
            command = scanner.nextLine().trim();
            switch (command) {
                case "new":
                    json = ac.webApi("/v1/browse/new-releases");
                    ac.printNewReleases(json);
                    break;
                case "featured":
                    json = ac.webApi("/v1/browse/featured-playlists");
                    ac.printPlaylists(json);
                    break;
                case "categories":
                    json = ac.webApi("/v1/browse/categories");
                    ac.printCategories(json);
                    break;
                case "auth":
                    ac.auth(client_id, client_secret, redirect_uri);
                    break;
                case "exit":
                    break;
                case "next":
                    ac.next();
                    break;
                case "prev":
                    ac.previous();
                    break;
                default:
                    if (!command.startsWith("playlists ")) {
                        break;
                    }
                    String[] strs = command.split("\\s+");
                    if (strs.length < 2) {
                        System.out.println("Unknown category name.");
                        break;
                    }
                    String categoryName = command.replace("playlists ", "").trim();
                    String id = ac.searchCategoryId("/v1/browse/categories", categoryName);
                    if (!ac.isAccessTokenEnable()) {
                        break;
                    }
                    if ("".equals(id)) {
                        System.out.println("Unknown category name.");
                        break;
                    }
                    String uri = String.format("/v1/browse/categories/%s/playlists", id);
                    json = ac.webApi(uri);
                    ac.printPlaylists(json);
                    break;
            }
        }

        scanner.close();
    }

    static void execSim() throws IOException, InterruptedException {
        Action ac = new Action(accessPointAuth, accessPointApi, pageLimit);
        Scanner scanner = new Scanner(System.in);
        String command = "";
        String json = "";
        SimPage simPage = null;
        while (!"exit".equals(command)) {
            command = scanner.nextLine().trim();
            switch (command) {
                case "new":
                    json = ac.webApi("/v1/browse/new-releases");
                    simPage = ac.buildNewReleasesSim(json);
                    ac.printNewReleases(simPage);
                    break;
                case "featured":
                    json = ac.webApi("/v1/browse/featured-playlists");
                    simPage = ac.buildPlaylistsSim(json);
                    ac.printPlaylists(simPage);
                    break;
                case "categories":
                    json = ac.webApi("/v1/browse/categories");
                    simPage = ac.buildCategoriesSim(json);
                    ac.printCategories(simPage);
                    break;
                case "auth":
                    ac.auth(client_id, client_secret, redirect_uri);
                    break;
                case "exit":
                    break;
                case "next":
                    ac.next(simPage);
                    break;
                case "prev":
                    ac.previous(simPage);
                    break;
                default:
                    if (!command.startsWith("playlists ")) {
                        break;
                    }
                    String[] strs = command.split("\\s+");
                    if (strs.length < 2) {
                        System.out.println("Unknown category name.");
                        break;
                    }
                    String categoryName = command.replace("playlists ", "").trim();
                    String id = ac.searchCategoryIdSim("/v1/browse/categories", categoryName);
                    if (!ac.isAccessTokenEnable()) {
                        break;
                    }
                    if ("".equals(id)) {
                        System.out.println("Unknown category name.");
                        break;
                    }
                    String uri = String.format("/v1/browse/categories/%s/playlists", id);
                    json = ac.webApi(uri);
                    simPage = ac.buildPlaylistsSim(json);
                    ac.printPlaylists(simPage);
                    break;
                
            }
        }
        scanner.close();
    }
}

class Action {

    Server server;
    Client client;
    String accessPointApi;
    String accessPointAuth;
    int pageLimit;
    String nextQuery;
    String previousQuery;
    int totalEntries;
    Page page;
    boolean debugFlag = false;

    Action(String accessPointAuth, String accessPointApi, int pageLimit) {
        server = new Server();
        client = new Client();
        this.accessPointAuth = accessPointAuth;
        this.accessPointApi = accessPointApi;
        this.pageLimit = pageLimit;
    }

    void next() throws IOException, InterruptedException {
        if (client.getAccessToken() == null) {
            System.out.println("Please, provide access for application.");
            return;
        }
        if (!page.next()) {
            System.out.println("No more pages.");
            return;
        }
        if (nextQuery == null) {
            return;
        }
        String json = client.webApi(nextQuery);
        print(json, nextQuery);
        getPagingInfo(json, nextQuery);
    }

    void previous() throws IOException, InterruptedException {
        if (client.getAccessToken() == null) {
            System.out.println("Please, provide access for application.");
            return;
        }
        if (!page.previous()) {
            System.out.println("No more pages.");
            return;
        }
        if (previousQuery == null) {
            return;
        }
        String json = client.webApi(previousQuery);
        print(json, previousQuery);
        getPagingInfo(json, previousQuery);
    }

    void next(SimPage simPage) {
        if (client.getAccessToken() == null) {
            System.out.println("Please, provide access for application.");
            return;
        }
        if (!simPage.next()) {
            System.out.println("No more pages.");
            return;
        }
        print(simPage);
    }
    
    void previous(SimPage simPage) {
        if (client.getAccessToken() == null) {
            System.out.println("Please, provide access for application.");
            return;
        }
        if (!simPage.previous()) {
            System.out.println("No more pages.");
            return;
        }
        print(simPage);
    }

    void print(String json, String endpoint) {
        String rootName = getRootName(endpoint);
        switch (rootName) {
            case "albums":
                printNewReleases(json);
                break;
            case "categories":
                printCategories(json);
                break;
            case "playlists":
                printPlaylists(json);
                break;
        }
    }
  
    void print(SimPage simPage) {
        switch (simPage.type) {
            case "newReleases":
                printNewReleases(simPage);
                break;
            case "categories":
                printCategories(simPage);
                break;
            case "playlists":
                printPlaylists(simPage);
                break;
        }
    }

    String searchCategoryId(String endpoint, String name) throws IOException, InterruptedException {

        String json = "";
        json = webApi(endpoint);
        while (!"".equals(json)) {
            List<Category> list = buildCategories(json);
            if (list.isEmpty()) {
                return "";
            }
            for (Category category: list) {
                if (name.equals(category.getName())) {
                    return category.getId();
                }
            }
            if (nextQuery == null) {
                return "";
            }
            json = client.webApi(nextQuery);
            getPagingInfo(json, endpoint);
        }
        return "";
    }

    String searchCategoryIdSim(String endpoint, String name) throws IOException, InterruptedException {

        String json = "";
        json = webApi(endpoint);
        List<Category> list = buildCategories(json);
        if (list.isEmpty()) {
            return "";
        }
        for (Category category: list) {
            if (name.equals(category.getName())) {
                return category.getId();
            }
        }
        return "";
    }

    boolean isAccessTokenEnable() {
        if (client.getAccessToken() == null) {
            return false;
        } else {
            return true;
        }
    }

    String webApi(String endpoint) throws IOException, InterruptedException {
        page = new Page(pageLimit, 0);
        if (client.getAccessToken() == null) {
            System.out.println("Please, provide access for application.");
            return "";
        }
        String query = String.format("?country=us&limit=%d", pageLimit);
        if (debugFlag) query = String.format("?country=us&limit=%d", 20);
        String json = client.webApi(accessPointApi + endpoint + query);
        getPagingInfo(json, endpoint);
        if (debugFlag) totalEntries = Math.min(totalEntries, 20);
        page = new Page(pageLimit, totalEntries);
        return json;
/*
        String fileName = name + ".txt";
        WriteText.writeAll(fileName, text);
        System.out.println(fileName);
*/
    }

    String getRootName(String endpointInput) {
        String endpoint = endpointInput;
        String rootName = "";
        int index = endpoint.indexOf('?');
        if (index >= 0) {
            endpoint = endpoint.substring(0, index);
        }
        if (endpoint.endsWith("categories")) {
            rootName = "categories";
        }
        if (endpoint.endsWith("playlists")) {
            rootName = "playlists";
        }
        if (endpoint.endsWith("releases")) {
            rootName = "albums";
        }
        return rootName;
    }

    void getPagingInfo(String json, String endpoint) {
        nextQuery = null;
        previousQuery = null;
        totalEntries = 0;

        String rootName = getRootName(endpoint);
        if ("".equals(rootName)) {
            return;
        }

        JsonObject je = new JsonParser().parse(json).getAsJsonObject();
        if (je.get(rootName).isJsonNull()) {
            return;
        }
        JsonObject root = je.get(rootName).getAsJsonObject();
        if (!root.get("total").isJsonNull()) {
            totalEntries = Integer.parseInt(root.get("total").getAsString());
        }
        if (!root.get("next").isJsonNull()) {
            nextQuery = root.get("next").getAsString();
        }
        if (!root.get("previous").isJsonNull()) {
            previousQuery = root.get("previous").getAsString();
        }
    }

    void auth(String client_id, String clent_secret, String redirect_uri) throws IOException, InterruptedException {
        System.out.println("use this link to request the access code:");
        System.out.println(accessPointAuth + "/authorize?client_id=" + client_id + "&redirect_uri=" + redirect_uri + "&response_type=code");
        System.out.println("waiting for code...");
        server.start(accessPointAuth, client, client_id, clent_secret, redirect_uri);
    }

    void printNewReleases(String json) {
        List<NewRelease> list = buildNewReleases(json);
        list.stream().forEach(s -> s.print());
        if (page != null && page.total > 0) {
            page.print();
        }
    }

    void printCategories(String json) {
        List<Category> list = buildCategories(json);
        list.stream().forEach(s -> s.print());
        if (page != null && page.total > 0) {
            page.print();
        }
    }

    void printPlaylists(String json) {
        List<Playlist> list = buildPlaylists(json);
        list.stream().forEach(s -> s.print());
        if (page != null && page.total > 0) {
            page.print();
        }
    }

    List<Category> buildCategories(String json) {
        List<Category> list = new ArrayList<>();
        if ("".equals(json)) {
            return list;
        }
        try {
            JsonObject je = new JsonParser().parse(json).getAsJsonObject();
            JsonObject root = je.get("categories").getAsJsonObject();
            JsonArray items = root.get("items").getAsJsonArray();
            for (JsonElement item: items) {
                JsonObject jo = item.getAsJsonObject();
                String name = jo.get("name").getAsString();
                String id = jo.get("id").getAsString();
                list.add(new Category(name, id));
            }
        } catch (NullPointerException e) {}
        return list;
    }

    List<Playlist> buildPlaylists(String json) {
        List<Playlist> list = new ArrayList<>();
        if ("".equals(json)) {
            return list;
        }
        try {
//            System.out.println(json);
            JsonObject je = new JsonParser().parse(json).getAsJsonObject();
            JsonObject root = je.get("playlists").getAsJsonObject();
            JsonArray items = root.get("items").getAsJsonArray();
            for (JsonElement item: items) {
                JsonObject jo = item.getAsJsonObject();
                String title = jo.get("name").getAsString();
                JsonObject jo3 = jo.get("external_urls").getAsJsonObject();
                String url = jo3.get("spotify").getAsString();
                list.add(new Playlist(title, url));
            }
        } catch (NullPointerException e) {}
        return list;
    }

    List<NewRelease> buildNewReleases(String json) {
        List<NewRelease> list = new ArrayList<>();
        if ("".equals(json)) {
            return list;
        }
        try {
            JsonObject je = new JsonParser().parse(json).getAsJsonObject();
            JsonObject root = je.get("albums").getAsJsonObject();
            JsonArray items = root.get("items").getAsJsonArray();
            for (JsonElement item: items) {
                JsonObject jo = item.getAsJsonObject();
                String title = jo.get("name").getAsString();
                JsonArray array = jo.get("artists").getAsJsonArray();
                List<String> artists = new ArrayList<>();
                for (JsonElement elm: array) {
                    JsonObject jo2 = elm.getAsJsonObject();
                    String artist = jo2.get("name").getAsString();
                    artists.add(artist);
                }
                JsonObject jo3 = jo.get("external_urls").getAsJsonObject();
                String url = jo3.get("spotify").getAsString();
                list.add(new NewRelease(title, artists, url));
            }
        } catch (NullPointerException e) {}
        return list;
    }

    SimPage buildNewReleasesSim(String json) {
        List<NewRelease> newReleases = buildNewReleases(json);
        SimPage simPage = new SimPage(pageLimit, totalEntries);
        simPage.addNewReleases(newReleases);
        return simPage;
    }

    SimPage buildPlaylistsSim(String json) {
        List<Playlist> playlists = buildPlaylists(json);
        SimPage simPage = new SimPage(pageLimit, totalEntries);
        simPage.addPlaylists(playlists);
        return simPage;
    }

    SimPage buildCategoriesSim(String json) {
        List<Category> categories = buildCategories(json);
        SimPage simPage = new SimPage(pageLimit, totalEntries);
        simPage.addCategories(categories);
        return simPage;
    }

    void printNewReleases(SimPage simPage) {
        List<NewRelease> list = simPage.getNewReleases();
        list.stream().forEach(s -> s.print());
        if (simPage != null && simPage.total > 0) {
            simPage.print();
        }
    }

    void printPlaylists(SimPage simPage) {
        List<Playlist> list = simPage.getPlaylists();
        list.stream().forEach(s -> s.print());
        if (simPage != null && simPage.total > 0) {
            simPage.print();
        }
    }
    
    void printCategories(SimPage simPage) {
        List<Category> list = simPage.getCategories();
        list.stream().forEach(s -> s.print());
        if (simPage != null && simPage.total > 0) {
            simPage.print();
        }
    }
}

class Server {
    HttpServer server;
    String authentication_code = "";

    void start(String accessPointAuth, Client client, String client_id, String client_secret, String redirect_uri) throws IOException,
            InterruptedException {
        server = HttpServer.create();
        server.bind(new InetSocketAddress(8080), 0);
        server.createContext("/", new HttpHandler() {
            public void handle(HttpExchange exchange) throws IOException {
                String query = exchange.getRequestURI().getQuery();
                String message = "Got the code. Return back to your program.";
                if (query == null || !query.startsWith("code=")) {
                    message = "Authorization code not found. Try again.";
                }
//                System.out.println(query);
                exchange.sendResponseHeaders(200, message.length());
                exchange.getResponseBody().write(message.getBytes());
                exchange.getResponseBody().close();
                if (query == null || !query.startsWith("code=")) {
                    return;
                }
                authentication_code = query;
                System.out.println("code received");
            }
        });
        server.start();
        int i = 0;
        int limit = 100000;
        for (; i < limit && authentication_code.equals(""); i++) {
            Thread.sleep(10);
        }
        server.stop(1);
        if (i == limit) {
            System.out.println("Authorization code not found. Try again.");
        }
        try {
            client.start(accessPointAuth, authentication_code, client_id, client_secret, redirect_uri);
        } catch (InterruptedException e) {
            e.getMessage();
        }
    }
}

class Client {

    String accessToken;

    void start(String accessPointAuth, String code, String client_id, String client_secret, String redirect_uri) throws IOException, InterruptedException {
        System.out.println("making http request for access_token...");
        HttpClient httpClient = HttpClient.newBuilder().build();
        String authentication = client_id + ":" + client_secret;
        String encodedString = Base64.getEncoder().encodeToString(authentication.getBytes());
        String requestBody = "grant_type=authorization_code&" + code + "&redirect_uri=" + redirect_uri;
//        System.out.println(requestBody);
        HttpRequest request = HttpRequest.newBuilder()
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic " + encodedString)
                .uri(URI.create(accessPointAuth + "/api/token"))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
//        System.out.println(response.body());
//        System.out.println(response.statusCode());
        if (response.statusCode() != 200) {
            return;
        }
        System.out.println(response.body());
        System.out.println("Success!");
        String access_token = extractAccessToken(response.body());
        setAccessToken(access_token);
//        System.out.println(access_token);
    }

    void setAccessToken(String access_token) {
        this.accessToken = access_token;
    }

    String getAccessToken() {
        return accessToken;
    }

    String extractAccessToken(String reponseBody) {
        String str = reponseBody;
        str = trimchar(str, '{', '}');
        if (str.isEmpty()) {
            return str;
        }

        Map<String, String> map = Stream.of(str.split(",")).map(s -> s.split(":")).collect(Collectors.toMap(s -> s[0], s -> s[1]));
        String value = map.get("\"access_token\"");
        return trimchar(value, '"', '"');
    }

    String trimchar(String str, char startch, char endch) {
        String s = str;
        if (s.length() < 2) {
            return s;
        }
        if (s.charAt(0) == startch && s.charAt(s.length() - 1) == endch) {
            s = s.substring(1);
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    String webApi(String endpoint) throws IOException, InterruptedException {
        if (getAccessToken() == null) {
            return "";
        }
        HttpClient httpClient = HttpClient.newBuilder().build();
        HttpRequest request = HttpRequest.newBuilder()
                .header("Authorization", "Bearer " + getAccessToken())
                .uri(URI.create(endpoint))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
//        System.out.println(response.statusCode());
        if (response.statusCode() != 200) {
            return "";
        }
//        System.out.println(response.body());
        return response.body();
    }
}

class WriteText {

    static void writeAll(String fileName, String text) {
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(fileName));
            bw.write(text, 0, text.length());
            bw.close();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
}

class NewRelease{
    String title;
    List<String> artists;
    String url;

    NewRelease(String title, List<String> artists, String url) {
        this.title = title;
        this.artists = artists;
        this.url = url;
    }

    void print() {
        System.out.println(title);
        printList(artists);
        System.out.println(url);
        System.out.println();
    }

    void printList(List<String> list) {
        System.out.print("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                System.out.print(", ");
            }
            System.out.print(list.get(i));
        }
        System.out.println("]");
    }
}

class Playlist{
    String title;
    String url;

    Playlist(String title, String url) {
        this.title = title;
        this.url = url;
    }

    void print() {
        System.out.println(title);
        System.out.println(url);
        System.out.println();
    }
}

class Category{
    String name;
    String id;

    Category(String name, String id) {
        this.name = name;
        this.id = id;
    }

    void print() {
        System.out.println(name);
    }

    String getId() {
        return id;
    }

    String getName() {
        return name;
    }
}

class Page {
    int total;
    int current;
    int pageLimit;
    int totalEntries;

    Page(int pageLimit, int totalEntries) {
        this.pageLimit = pageLimit;
        this.totalEntries = totalEntries;
        total = (totalEntries + pageLimit - 1) / pageLimit;
        current = 1;
    }

    boolean next() {
        if (current == total) {
            return false;
        }
        current++;
        return true;
    }

    boolean previous() {
        if (current == 1) {
            return false;
        }
        current--;
        return true;
    }

    void print() {
        System.out.println(String.format("---PAGE %d OF %d---", current, total));
    }
}

class SimPage extends Page {

    List<NewRelease> newReleases;
    List<Category> categories;
    List<Playlist> playlists;
    String type = "";

    SimPage(int pageLimit, int totalEntries) {
        super(pageLimit, totalEntries);
    }

    void addNewReleases(List<NewRelease> newReleases) {
        this.newReleases = newReleases;
        type = "newReleases";
    }

    void addCategories(List<Category>categories) {
        this.categories = categories;
        type = "categories";
    }

    void addPlaylists(List<Playlist> playlists) {
        this.playlists = playlists;
        type = "playlists";
    }

    List<NewRelease> getNewReleases() {
        int i = (current - 1) * pageLimit;
        List<NewRelease> list = new ArrayList<>();
        for (int j = 0; i < newReleases.size() && j < pageLimit; i++, j++) {
            list.add(newReleases.get(i));
        }
        return list;   
    }
    
    List<Playlist> getPlaylists() {
        int i = (current - 1) * pageLimit;
        List<Playlist> list = new ArrayList<>();
        for (int j = 0; i < playlists.size() && j < pageLimit; i++, j++) {
            list.add(playlists.get(i));
        }
        return list;   
    }
   
    List<Category> getCategories() {
        int i = (current - 1) * pageLimit;
        List<Category> list = new ArrayList<>();
        for (int j = 0; i < categories.size() && j < pageLimit; i++, j++) {
            list.add(categories.get(i));
        }
        return list;   
    }
}