CREATE TABLE quizzes (
    id UUID PRIMARY KEY,
    title VARCHAR(180) NOT NULL,
    exam_type VARCHAR(40) NOT NULL,
    subject VARCHAR(140) NOT NULL,
    topic VARCHAR(160),
    difficulty VARCHAR(20),
    duration_minutes INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT quizzes_duration_check CHECK (duration_minutes BETWEEN 1 AND 240),
    CONSTRAINT quizzes_status_check CHECK (status IN ('DRAFT', 'REVIEW', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT quizzes_difficulty_check CHECK (difficulty IS NULL OR difficulty IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED'))
);

CREATE TABLE quiz_questions (
    id UUID PRIMARY KEY,
    quiz_id UUID NOT NULL REFERENCES quizzes(id),
    question_text TEXT NOT NULL,
    question_type VARCHAR(20) NOT NULL,
    topic VARCHAR(160) NOT NULL,
    difficulty VARCHAR(20) NOT NULL,
    marks NUMERIC(8,2) NOT NULL,
    negative_marks NUMERIC(8,2) NOT NULL DEFAULT 0,
    explanation TEXT NOT NULL,
    source_reference VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT quiz_questions_type_check CHECK (question_type IN ('SINGLE', 'MULTIPLE')),
    CONSTRAINT quiz_questions_marks_check CHECK (marks > 0 AND negative_marks >= 0),
    CONSTRAINT quiz_questions_difficulty_check CHECK (difficulty IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    CONSTRAINT quiz_questions_status_check CHECK (status IN ('DRAFT', 'REVIEW', 'PUBLISHED', 'ARCHIVED'))
);
CREATE INDEX quiz_questions_quiz_status_idx ON quiz_questions (quiz_id, status, topic);
CREATE INDEX quiz_questions_topic_difficulty_idx ON quiz_questions (topic, difficulty) WHERE status = 'PUBLISHED';

CREATE TABLE quiz_options (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL REFERENCES quiz_questions(id) ON DELETE CASCADE,
    option_label VARCHAR(4) NOT NULL,
    option_text TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INTEGER NOT NULL,
    CONSTRAINT quiz_options_label_unique UNIQUE (question_id, option_label),
    CONSTRAINT quiz_options_order_unique UNIQUE (question_id, display_order)
);

CREATE TABLE quiz_attempts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    quiz_id UUID REFERENCES quizzes(id) ON DELETE SET NULL,
    quiz_title_snapshot VARCHAR(180) NOT NULL,
    exam_type VARCHAR(40) NOT NULL,
    subject VARCHAR(140) NOT NULL,
    total_questions INTEGER NOT NULL,
    total_marks NUMERIC(10,2) NOT NULL,
    score NUMERIC(10,2),
    correct_count INTEGER,
    incorrect_count INTEGER,
    unanswered_count INTEGER,
    accuracy NUMERIC(6,2),
    started_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    submitted_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    CONSTRAINT quiz_attempts_question_count_check CHECK (total_questions > 0),
    CONSTRAINT quiz_attempts_status_check CHECK (status IN ('IN_PROGRESS', 'SUBMITTED'))
);
CREATE INDEX quiz_attempts_user_started_idx ON quiz_attempts (user_id, started_at DESC);
CREATE INDEX quiz_attempts_status_expires_idx ON quiz_attempts (status, expires_at);

CREATE TABLE quiz_attempt_questions (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL REFERENCES quiz_attempts(id) ON DELETE CASCADE,
    question_id UUID REFERENCES quiz_questions(id) ON DELETE SET NULL,
    display_order INTEGER NOT NULL,
    question_text_snapshot TEXT NOT NULL,
    question_type VARCHAR(20) NOT NULL,
    topic_snapshot VARCHAR(160) NOT NULL,
    marks NUMERIC(8,2) NOT NULL,
    negative_marks NUMERIC(8,2) NOT NULL,
    options_snapshot JSONB NOT NULL,
    correct_option_ids JSONB NOT NULL,
    selected_option_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    explanation_snapshot TEXT NOT NULL,
    source_reference_snapshot VARCHAR(500) NOT NULL,
    visited BOOLEAN NOT NULL DEFAULT FALSE,
    marked_for_review BOOLEAN NOT NULL DEFAULT FALSE,
    is_correct BOOLEAN,
    awarded_marks NUMERIC(8,2),
    CONSTRAINT quiz_attempt_question_order_unique UNIQUE (attempt_id, display_order),
    CONSTRAINT quiz_attempt_question_type_check CHECK (question_type IN ('SINGLE', 'MULTIPLE'))
);
CREATE INDEX quiz_attempt_questions_attempt_order_idx ON quiz_attempt_questions (attempt_id, display_order);
