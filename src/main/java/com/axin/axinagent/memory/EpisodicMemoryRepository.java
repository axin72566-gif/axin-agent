package com.axin.axinagent.memory;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 情节记忆 Repository。
 */
public interface EpisodicMemoryRepository extends JpaRepository<EpisodicMemoryEvent, Long> {

	List<EpisodicMemoryEvent> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

	List<EpisodicMemoryEvent> findByAgentNameAndUserIdOrderByCreatedAtDesc(String agentName, String userId, Pageable pageable);

	List<EpisodicMemoryEvent> findByUserIdOrderByImportanceDescCreatedAtDesc(String userId, Pageable pageable);

	@Query("""
			SELECT e
			FROM EpisodicMemoryEvent e
			WHERE e.userId = :userId
			  AND (LOWER(e.keywords) LIKE LOWER(CONCAT('%', :keyword, '%'))
			       OR LOWER(e.summary) LIKE LOWER(CONCAT('%', :keyword, '%')))
			ORDER BY e.createdAt DESC
			""")
	List<EpisodicMemoryEvent> searchByKeyword(@Param("userId") String userId,
	                                          @Param("keyword") String keyword,
	                                          Pageable pageable);
}
