package dev.kennurken.tutorbot.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationStateRepository extends JpaRepository<ConversationStateEntity, Long> {
}
