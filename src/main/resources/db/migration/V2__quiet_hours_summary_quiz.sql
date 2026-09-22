-- V2: quiet hours, evening summary, daily quiz, task kinds, knowledge task type.

ALTER TABLE users
    ADD COLUMN quiet_hours_start         TIME,
    ADD COLUMN quiet_hours_end           TIME,
    ADD COLUMN evening_summary_time      TIME,
    ADD COLUMN last_evening_summary_date DATE,
    ADD COLUMN quiz_time                 TIME,
    ADD COLUMN last_quiz_date            DATE;

-- Sensible defaults for existing users; new users get them from the entity.
UPDATE users
SET quiet_hours_start    = '23:00',
    quiet_hours_end      = '08:00',
    evening_summary_time = '21:30',
    quiz_time            = '13:00';

-- REGULAR (user-made), REVIEW (consequence engine), QUIZ (daily retrieval practice)
ALTER TABLE tasks
    ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'REGULAR';

ALTER TABLE knowledge_topics
    ADD COLUMN task_type    VARCHAR(16),
    ADD COLUMN last_quiz_at TIMESTAMPTZ;
