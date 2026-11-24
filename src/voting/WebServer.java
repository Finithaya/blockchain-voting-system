package voting;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import java.sql.*;

public class WebServer {

    static List<String> candidates = new ArrayList<>();
    static Map<String, String> voters = new HashMap<>();
    static Blockchain blockchain = new Blockchain();
    static Set<String> votedVoters = new HashSet<>();

    static Connection getConnection() throws Exception {
        return DriverManager.getConnection("jdbc:mysql://localhost:3306/voting_db", "root", "finzz");
    }

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        initDB();
        loadData();
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/", new MyHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("Server started on http://localhost:8000");
    }

    static void initDB() {
        try (Connection con = getConnection(); Statement st = con.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS party (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL UNIQUE)");
            st.execute("CREATE TABLE IF NOT EXISTS voters (voterId VARCHAR(50) PRIMARY KEY, password VARCHAR(50) NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS votes (id INT AUTO_INCREMENT PRIMARY KEY, voterId VARCHAR(50) NOT NULL UNIQUE, candidate VARCHAR(100) NOT NULL, block_hash VARCHAR(200) NOT NULL, previous_hash VARCHAR(200), timestamp BIGINT)");
            try { st.execute("ALTER TABLE votes ADD COLUMN timestamp BIGINT"); } catch (Exception ignored) {}
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void loadData() {
        try (Connection con = getConnection(); Statement st = con.createStatement()) {
            ResultSet rs1 = st.executeQuery("SELECT name FROM party");
            while (rs1.next()) candidates.add(rs1.getString("name"));

            ResultSet rs2 = st.executeQuery("SELECT voterId, password FROM voters");
            while (rs2.next()) voters.put(rs2.getString("voterId"), rs2.getString("password"));
            ResultSet rs3 = st.executeQuery("SELECT voterId, candidate, block_hash, previous_hash, timestamp FROM votes ORDER BY id ASC");
            while (rs3.next()) {
                String vid = rs3.getString("voterId");
                String cand = rs3.getString("candidate");
                String savedHash = rs3.getString("block_hash");
                String savedPrevHash = rs3.getString("previous_hash");
                long savedTime = rs3.getLong("timestamp");

                if (savedPrevHash == null) {
                    if (!blockchain.chain.isEmpty()) {
                        savedPrevHash = blockchain.chain.get(blockchain.chain.size() - 1).hash;
                    } else {
                        savedPrevHash = "0";
                    }
                }

            
                Block b = new Block(vid, cand, savedPrevHash, savedTime, savedHash);

                blockchain.chain.add(b);
                blockchain.votedVoters.add(vid);
                votedVoters.add(vid);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            try {
                if (path.equals("/")) {
                    sendFile(exchange, "web/index.html", "text/html");
                }
                else if (path.equals("/style.css")) {
                    sendFile(exchange, "web/style.css", "text/css");
                }
                else if (path.equals("/admin.html")) {
                    sendFile(exchange, "web/admin.html", "text/html");
                }
                else if (path.equals("/api/candidates")) {
                    String json = "[";
                    for (int i = 0; i < candidates.size(); i++) {
                        if (i > 0) json += ",";
                        json += "\"" + candidates.get(i) + "\"";
                    }
                    json += "]";
                    sendResponse(exchange, json);
                }
                else if (path.equals("/api/addCandidate") && method.equals("POST")) {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    Map<String, String> params = parseQuery(body);
                    String name = params.get("name");
                    if (name != null && !name.trim().isEmpty() && !candidates.contains(name)) {
                        candidates.add(name);
                        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement("INSERT INTO party(name) VALUES(?)")) {
                            ps.setString(1, name);
                            ps.executeUpdate();
                        }
                        sendResponse(exchange, "Party Added!");
                    } else {
                        sendResponse(exchange, "Already exists or empty!");
                    }
                }
                else if (path.equals("/api/addVoter") && method.equals("POST")) {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    Map<String, String> params = parseQuery(body);
                    String voterId = params.get("voterId");
                    String password = params.get("password");

                    if (voterId == null || voterId.trim().isEmpty() || password == null || password.trim().isEmpty()) {
                        sendResponse(exchange, "Voter ID / Password missing!");
                    } else if (voters.containsKey(voterId)) {
                        sendResponse(exchange, "This Voter ID already exists!");
                    } else if (voters.containsValue(password)) {
                        sendResponse(exchange, "This Password is unavailable!");
                    } else {
                        voters.put(voterId, password);
                        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement("INSERT INTO voters(voterId,password) VALUES(?,?)")) {
                            ps.setString(1, voterId);
                            ps.setString(2, password);
                            ps.executeUpdate();
                        }
                        sendResponse(exchange, "Voter Added!");
                    }
                }
                else if (path.equals("/api/voters")) {
                    String json = "[";
                    int i = 0;
                    for (String id : voters.keySet()) {
                        if (i++ > 0) json += ",";
                        json += "\"" + id + "\"";
                    }
                    json += "]";
                    sendResponse(exchange, json);
                }
                else if (path.equals("/api/voterLogin") && method.equals("POST")) {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    Map<String, String> params = parseQuery(body);
                    String voterId = params.get("voterId");
                    String password = params.get("password");
                    if (voterId != null && password != null && password.equals(voters.get(voterId))) {
                        sendResponse(exchange, "OK");
                    } else {
                        sendResponse(exchange, "FAIL");
                    }
                }
                else if (path.equals("/api/vote") && method.equals("POST")) {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    Map<String, String> params = parseQuery(body);
                    String voterId = params.get("voterId");
                    String candidate = params.get("candidate");
                    if (voterId == null || !voters.containsKey(voterId)) {
                        sendResponse(exchange, "Invalid Voter!");
                    } else if (votedVoters.contains(voterId)) {
                        sendResponse(exchange, "Already Voted!");
                    } else if (candidate == null || !candidates.contains(candidate)) {
                        sendResponse(exchange, "Invalid Party!");
                    } else {
                        boolean success = blockchain.addVote(voterId, candidate);
                        if (success) {
                            votedVoters.add(voterId);
                            Block last = blockchain.chain.get(blockchain.chain.size() - 1);

                            
                            try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement("INSERT INTO votes(voterId,candidate,block_hash,previous_hash,timestamp) VALUES(?,?,?,?,?)")) {
                                ps.setString(1, voterId);
                                ps.setString(2, candidate);
                                ps.setString(3, last.hash);
                                ps.setString(4, last.previousHash);
                                ps.setLong(5, last.timeStamp);
                                ps.executeUpdate();
                            }
                            sendResponse(exchange, "Vote Recorded for " + candidate + " ✅");
                        } else {
                            sendResponse(exchange, "Already Voted!");
                        }
                    }
                }
                else if (path.equals("/api/results")) {
                    Map<String, Integer> results = new HashMap<>();
                    for (String c : candidates) results.put(c, 0);
                    for (Block b : blockchain.chain) {
                        if (b.candidate != null && results.containsKey(b.candidate)) {
                            results.put(b.candidate, results.get(b.candidate) + 1);
                        }
                    }
                    StringBuilder json = new StringBuilder();
                    json.append("{\"results\":{");
                    int i = 0;
                    for (Map.Entry<String, Integer> e : results.entrySet()) {
                        if (i++ > 0) json.append(",");
                        json.append("\"").append(e.getKey()).append("\":").append(e.getValue());
                    }
                    json.append("},\"valid\":").append(blockchain.isChainValid());
                    json.append("}");
                    sendResponse(exchange, json.toString());
                }
                else if (path.equals("/api/adminHashes")) {
                    StringBuilder json = new StringBuilder();
                    json.append("[");
                    try (Connection con = getConnection(); Statement st = con.createStatement()) {
                        ResultSet rs = st.executeQuery("SELECT voterId, candidate, block_hash, previous_hash FROM votes ORDER BY id ASC");
                        int i = 0;
                        while (rs.next()) {
                            if (i++ > 0) json.append(",");
                            json.append("{\"voterId\":\"").append(rs.getString("voterId"))
                                .append("\",\"candidate\":\"").append(rs.getString("candidate"))
                                .append("\",\"hash\":\"").append(rs.getString("block_hash"))
                                .append("\",\"previousHash\":\"").append(rs.getString("previous_hash")).append("\"}");
                        }
                    }
                    json.append("]");
                    sendResponse(exchange, json.toString());
                }
                else {
                    sendResponse(exchange, "404 Not Found");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, "Server Error: " + e.getMessage());
            }
        }

        private void sendFile(HttpExchange exchange, String filePath, String contentType) throws IOException {
            try {
                byte[] bytes = Files.readAllBytes(Paths.get(filePath));
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
            } catch (Exception e) {
                String msg = "File not found: " + filePath;
                exchange.sendResponseHeaders(404, msg.length());
                exchange.getResponseBody().write(msg.getBytes());
                exchange.getResponseBody().close();
            }
        }

        private void sendResponse(HttpExchange exchange, String response) throws IOException {
            byte[] bytes = response.getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }

        private Map<String, String> parseQuery(String query) throws UnsupportedEncodingException {
            Map<String, String> map = new HashMap<>();
            if (query == null || query.isEmpty()) return map;
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    map.put(java.net.URLDecoder.decode(kv[0], "UTF-8"),
                            java.net.URLDecoder.decode(kv[1], "UTF-8"));
                }
            }
            return map;
        }
    }
}