CREATE TABLE categories (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(140) NOT NULL UNIQUE,
    subject VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    resource_type VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE formulas (
    id UUID PRIMARY KEY,
    category_id UUID NOT NULL REFERENCES categories(id),
    title VARCHAR(180) NOT NULL,
    slug VARCHAR(200) NOT NULL UNIQUE,
    topic VARCHAR(140) NOT NULL,
    expression TEXT NOT NULL,
    variable_definitions JSONB NOT NULL,
    explanation TEXT NOT NULL,
    example_calculation JSONB,
    source_reference VARCHAR(500) NOT NULL,
    difficulty VARCHAR(20) NOT NULL,
    related_formula_ids JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

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
    related_reference_ids JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE bookmarks (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    resource_type VARCHAR(20) NOT NULL,
    resource_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
