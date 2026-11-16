CREATE TABLE flashcard_categories (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    slug VARCHAR(140) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE flashcards (
    id UUID PRIMARY KEY,
    category_id UUID NOT NULL REFERENCES flashcard_categories(id),
    question TEXT NOT NULL,
    short_answer TEXT NOT NULL,
    detailed_explanation TEXT NOT NULL,
    topic VARCHAR(160) NOT NULL,
    difficulty VARCHAR(20) NOT NULL,
    related_formula VARCHAR(300),
    related_laboratory_test VARCHAR(300),
    source_reference VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT flashcards_difficulty_check CHECK (difficulty IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    CONSTRAINT flashcards_status_check CHECK (status IN ('DRAFT', 'REVIEW', 'PUBLISHED', 'ARCHIVED'))
);
CREATE INDEX flashcards_category_status_idx ON flashcards (category_id, status, topic);
CREATE INDEX flashcards_status_difficulty_idx ON flashcards (status, difficulty, updated_at DESC);

CREATE TABLE flashcard_progress (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    flashcard_id UUID NOT NULL REFERENCES flashcards(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    bookmarked BOOLEAN NOT NULL DEFAULT FALSE,
    last_reviewed_at TIMESTAMPTZ,
    review_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT flashcard_progress_status_check CHECK (status IN ('NEW', 'KNOWN', 'REVISION')),
    CONSTRAINT flashcard_progress_review_count_check CHECK (review_count >= 0),
    CONSTRAINT flashcard_progress_user_card_unique UNIQUE (user_id, flashcard_id)
);
CREATE INDEX flashcard_progress_user_status_idx ON flashcard_progress (user_id, status, last_reviewed_at DESC);
CREATE INDEX flashcard_progress_user_bookmarked_idx ON flashcard_progress (user_id, flashcard_id) WHERE bookmarked = TRUE;
