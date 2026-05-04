package com.axin.axinagent.memory;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 情节记忆事件实体。
 */
@Data
@Entity
@Table(name = "episodic_memory_event")
public class EpisodicMemoryEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "agent_name", nullable = false, length = 100)
	private String agentName;

	@Column(name = "user_id", nullable = false, length = 100)
	private String userId;

	@Column(name = "event_type", nullable = false, length = 100)
	private String eventType;

	@Column(name = "summary", nullable = false, columnDefinition = "TEXT")
	private String summary;

	@Column(name = "keywords", length = 500)
	private String keywords;

	@Column(name = "importance", nullable = false)
	private Integer importance;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@PrePersist
	public void prePersist() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}
}
