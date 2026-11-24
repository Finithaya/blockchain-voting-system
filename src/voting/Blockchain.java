package voting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Blockchain {
    public ArrayList<Block> chain = new ArrayList<>();
    public ArrayList<String> votedVoters = new ArrayList<>();

    public Blockchain() {
        
        Block genesis = new Block("0", "First block", "0");
        genesis.timeStamp = 0; 
        genesis.hash = genesis.calculateHash(); 
        chain.add(genesis);
    }

    public boolean addVote(String voterId, String candidate) {
        if (votedVoters.contains(voterId)) {
            System.out.println("Already voted! " + voterId);
            return false;
        }
        Block newBlock = new Block(voterId, candidate, chain.get(chain.size() - 1).hash);
        chain.add(newBlock);
        votedVoters.add(voterId);
        System.out.println("Vote Added! Block Hash: " + newBlock.hash);
        return true;
    }

    public void showResults() {
        Map<String, Integer> results = new HashMap<>();
        for (int i = 1; i < chain.size(); i++) {
            String party = chain.get(i).candidate;
            if (party == null) continue;
            results.put(party, results.getOrDefault(party, 0) + 1);
        }
        System.out.println("=== Results ===");
        if (results.isEmpty()) {
            System.out.println("No votes yet");
        } else {
            for (Map.Entry<String, Integer> e : results.entrySet()) {
                System.out.println(e.getKey() + " : " + e.getValue());
            }
        }
    }

    public boolean isChainValid() {
        for (int i = 1; i < chain.size(); i++) {
            Block current = chain.get(i);
            Block previous = chain.get(i - 1);
            if (!current.hash.equals(current.calculateHash())) return false;
            if (!current.previousHash.equals(previous.hash)) return false;
        }
        return true;
    }
}