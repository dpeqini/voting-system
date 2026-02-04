package danjel.blockchain.core;

import java.util.*;

public class Blockchain {
    private List<Block> chain;
    private List<Transaction> pendingTransactions;
    private int difficulty;
    private Set<String> usedCredentials;  // Prevent double voting

    public Blockchain(int difficulty) {
        this.chain = new ArrayList<>();
        this.pendingTransactions = new ArrayList<>();
        this.difficulty = difficulty;
        this.usedCredentials = new HashSet<>();

        // Genesis block
        createGenesisBlock();
    }

    private void createGenesisBlock() {
        Block genesis = new Block("0", new ArrayList<>());
        genesis.mineBlock(difficulty);
        chain.add(genesis);
    }

    public boolean addTransaction(Transaction transaction) {
        // Verify credential hasn't voted
        if (usedCredentials.contains(transaction.getVoterCredentialHash())) {
            System.out.println("Voter already voted!");
            return false;
        }

        pendingTransactions.add(transaction);
        usedCredentials.add(transaction.getVoterCredentialHash());
        return true;
    }

    public void minePendingTransactions() {
        if (pendingTransactions.isEmpty()) return;

        Block block = new Block(getLatestBlock().getHash(),
                new ArrayList<>(pendingTransactions));
        block.mineBlock(difficulty);
        chain.add(block);
        pendingTransactions.clear();
    }

    public Block getLatestBlock() {
        return chain.get(chain.size() - 1);
    }

    public boolean isChainValid() {
        for (int i = 1; i < chain.size(); i++) {
            Block current = chain.get(i);
            Block previous = chain.get(i - 1);

            if (!current.getHash().equals(current.calculateHash())) {
                return false;
            }
            if (!current.getPreviousHash().equals(previous.getHash())) {
                return false;
            }
        }
        return true;
    }

    public Map<String, Integer> countVotes() {
        Map<String, Integer> results = new HashMap<>();
        for (Block block : chain) {
            for (Transaction tx : block.getTransactions()) {
                String candidate = tx.getCandidateId();
                results.merge(candidate, 1, Integer::sum);
            }
        }
        return results;
    }

    // Getters...
}