package dev.kennurken.tutorbot.knowledge;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeTopicRepository extends JpaRepository<KnowledgeTopic, Long> {

    Optional<KnowledgeTopic> findByUserIdAndSubjectIgnoreCaseAndTopicIgnoreCase(Long userId, String subject, String topic);

    List<KnowledgeTopic> findByUserIdOrderBySubjectAscTopicAsc(Long userId);

    List<KnowledgeTopic> findByUserIdAndSubjectIgnoreCase(Long userId, String subject);
}
