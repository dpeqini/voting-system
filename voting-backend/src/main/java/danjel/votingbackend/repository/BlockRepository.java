package danjel.votingbackend.repository;

import danjel.votingbackend.model.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlockRepository extends JpaRepository<Block, String> {

    List<Block> findByElectionIdOrderByBlockNumberAsc(String electionId);

    Optional<Block> findByBlockHash(String blockHash);

    Optional<Block> findByBlockNumber(Long blockNumber);

    @Query("SELECT b FROM Block b WHERE b.election.id = :electionId ORDER BY b.blockNumber DESC LIMIT 1")
    Optional<Block> findLatestBlock(@Param("electionId") String electionId);

    @Query("SELECT b FROM Block b WHERE b.election.id = :electionId AND b.validated = false ORDER BY b.blockNumber ASC")
    List<Block> findUnvalidatedBlocks(@Param("electionId") String electionId);

    @Query("SELECT COUNT(b) FROM Block b WHERE b.election.id = :electionId")
    long countBlocksByElection(@Param("electionId") String electionId);

    @Query("SELECT SUM(b.transactionCount) FROM Block b WHERE b.election.id = :electionId")
    Long getTotalTransactionCount(@Param("electionId") String electionId);

    @Query("SELECT b FROM Block b WHERE b.election.id = :electionId AND :voteHash MEMBER OF b.voteHashes")
    Optional<Block> findBlockContainingVote(@Param("electionId") String electionId,
                                            @Param("voteHash") String voteHash);

    boolean existsByBlockHashAndElectionId(String blockHash, String electionId);

    @Query("SELECT b FROM Block b WHERE b.election.id = :electionId AND b.blockNumber > :fromBlock ORDER BY b.blockNumber ASC")
    List<Block> findBlocksAfter(@Param("electionId") String electionId,
                                @Param("fromBlock") Long fromBlock);
}
