CREATE TABLE categories (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(140) NOT NULL UNIQUE,
    subject VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    resource_type VARCHAR(30) NOT NULL DEFAULT 'BOTH',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT categories_resource_type_check CHECK (resource_type IN ('FORMULA', 'CODE_REFERENCE', 'BOTH'))
);

CREATE TABLE formulas (
    id UUID PRIMARY KEY,
    category_id UUID NOT NULL REFERENCES categories(id),
    title VARCHAR(180) NOT NULL,
    slug VARCHAR(200) NOT NULL UNIQUE,
    topic VARCHAR(140) NOT NULL,
    expression TEXT NOT NULL,
    variable_definitions JSONB NOT NULL DEFAULT '[]'::jsonb,
    explanation TEXT NOT NULL,
    example_calculation JSONB,
    source_reference VARCHAR(500) NOT NULL,
    difficulty VARCHAR(20) NOT NULL,
    related_formula_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    search_vector TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple'::regconfig, coalesce(title, '') || ' ' || coalesce(topic, '') || ' ' || coalesce(expression, '') || ' ' || coalesce(explanation, '') || ' ' || coalesce(source_reference, ''))) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT formulas_difficulty_check CHECK (difficulty IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    CONSTRAINT formulas_status_check CHECK (status IN ('DRAFT', 'REVIEW', 'PUBLISHED', 'ARCHIVED'))
);
CREATE INDEX formulas_search_vector_idx ON formulas USING GIN (search_vector);
CREATE INDEX formulas_category_status_title_idx ON formulas (category_id, status, lower(title));

CREATE TABLE code_references (
    id UUID PRIMARY KEY,
    category_id UUID NOT NULL REFERENCES categories(id),
    code_number VARCHAR(80) NOT NULL,
    title VARCHAR(220) NOT NULL,
    subject VARCHAR(140) NOT NULL,
    topic VARCHAR(160) NOT NULL,
    edition VARCHAR(120),
    publication_year INTEGER,
    clause_identifier VARCHAR(100),
    summary TEXT NOT NULL,
    permitted_excerpt TEXT,
    source_information VARCHAR(500) NOT NULL,
    source_url VARCHAR(1000),
    verification_level VARCHAR(30) NOT NULL,
    related_reference_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    search_vector TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple'::regconfig, coalesce(code_number, '') || ' ' || coalesce(title, '') || ' ' || coalesce(subject, '') || ' ' || coalesce(topic, '') || ' ' || coalesce(summary, '') || ' ' || coalesce(source_information, ''))) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT code_references_verification_check CHECK (verification_level IN ('VERIFIED', 'EDITORIAL_SUMMARY', 'EDUCATIONAL_NOTE')),
    CONSTRAINT code_references_status_check CHECK (status IN ('DRAFT', 'REVIEW', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT code_references_publication_year_check CHECK (publication_year IS NULL OR publication_year BETWEEN 1900 AND 2200)
);
CREATE INDEX code_references_search_vector_idx ON code_references USING GIN (search_vector);
CREATE INDEX code_references_category_status_title_idx ON code_references (category_id, status, lower(title));
CREATE INDEX code_references_number_idx ON code_references (lower(code_number));

CREATE TABLE bookmarks (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    resource_type VARCHAR(20) NOT NULL,
    resource_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT bookmarks_resource_type_check CHECK (resource_type IN ('FORMULA', 'CODE_REFERENCE')),
    CONSTRAINT bookmarks_user_resource_unique UNIQUE (user_id, resource_type, resource_id)
);
CREATE INDEX bookmarks_user_created_idx ON bookmarks (user_id, created_at DESC);

INSERT INTO categories (id, name, slug, subject, description, resource_type) VALUES
('3b581d57-19fd-4e88-9344-5197829b6b20', 'Concrete and materials', 'concrete-materials', 'Civil Engineering', 'Concrete and construction material references.', 'BOTH'),
('4c4ad5bf-87aa-44ee-8c2e-a46d5f40ca2b', 'Steel structures', 'steel-structures', 'Civil Engineering', 'Steel material and structural engineering references.', 'BOTH'),
('2a3a87d6-75cc-494a-8c5f-6fc467e398c3', 'Surveying', 'surveying', 'Civil Engineering', 'Surveying formulas and reference topics.', 'FORMULA'),
('d37bd552-4b9b-4f4b-b019-87d3b40ef43d', 'Geotechnical engineering', 'geotechnical-engineering', 'Civil Engineering', 'Soil and foundation engineering references.', 'BOTH'),
('8b23af5d-e37b-4d48-850b-fecdf4804fa9', 'Construction management', 'construction-management', 'Civil Engineering', 'Construction planning and management references.', 'BOTH'),
('0a79d8c9-4b6c-46fc-a98f-1a7a68040e0f', 'General civil engineering', 'general-civil-engineering', 'Civil Engineering', 'Cross-discipline civil engineering references.', 'BOTH');
