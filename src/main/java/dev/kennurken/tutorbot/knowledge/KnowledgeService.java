package dev.kennurken.tutorbot.knowledge;

import dev.kennurken.tutorbot.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeService {

    private final KnowledgeTopicRepository topics;
    private final Clock clock;

    public KnowledgeService(KnowledgeTopicRepository topics, Clock clock) {
        this.topics = topics;
        this.clock = clock;
    }

    /** Called after every finished verification with a usable score. */
    @Transactional
    public KnowledgeTopic recordAssessment(Long userId, String subject, String topicName, double score, double confidence) {
        String subj = normalize(subject);
        String top = normalize(topicName);
        KnowledgeTopic topic = topics.findByUserIdAndSubjectIgnoreCaseAndTopicIgnoreCase(userId, subj, top)
                .orElseGet(() -> new KnowledgeTopic(userId, subj, top));
        topic.apply(KnowledgeModel.update(topic.state(), score, confidence), clock.instant());
        return topics.save(topic);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeTopic> profile(User user) {
        return topics.findByUserIdOrderBySubjectAscTopicAsc(user.getId());
    }

    /** Topics of the same subject that look forgotten: passed to the examiner as known gaps. */
    @Transactional(readOnly = true)
    public List<String> knownGaps(Long userId, String subject) {
        if (subject == null) {
            return List.of();
        }
        Instant now = clock.instant();
        return topics.findByUserIdAndSubjectIgnoreCase(userId, normalize(subject)).stream()
                .filter(t -> KnowledgeModel.retention(t.state(), t.getLastVerifiedAt(), now) < KnowledgeModel.REVIEW_THRESHOLD)
                .map(KnowledgeTopic::getTopic)
                .limit(5)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<KnowledgeTopic> reviewCandidates(User user) {
        Instant now = clock.instant();
        return profile(user).stream()
                .filter(t -> KnowledgeModel.reviewRecommended(t.state(), t.getLastVerifiedAt(), now))
                .toList();
    }

    public double retentionNow(KnowledgeTopic topic) {
        return KnowledgeModel.retention(topic.state(), topic.getLastVerifiedAt(), clock.instant());
    }

    private static String normalize(String s) {
        if (s == null || s.isBlank()) {
            return "General";
        }
        String trimmed = s.trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
    }
}
