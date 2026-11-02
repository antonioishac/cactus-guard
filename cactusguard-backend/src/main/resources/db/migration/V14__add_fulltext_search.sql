-- V14__add_fulltext_search.sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

ALTER TABLE parties ADD COLUMN IF NOT EXISTS search_vector tsvector;

CREATE INDEX IF NOT EXISTS idx_parties_search_vector ON parties USING gin(search_vector);
CREATE INDEX IF NOT EXISTS idx_parties_name_trgm ON parties USING gin(name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_parties_document_trgm ON parties USING gin(document gin_trgm_ops);

CREATE OR REPLACE FUNCTION parties_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := 
        setweight(to_tsvector('portuguese', coalesce(NEW.name, '')), 'A') ||
        setweight(to_tsvector('portuguese', coalesce(NEW.document, '')), 'A') ||
        setweight(to_tsvector('portuguese', coalesce(NEW.trade_name, '')), 'B') ||
        setweight(to_tsvector('portuguese', coalesce(NEW.email, '')), 'C');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS parties_search_vector_trigger ON parties;
CREATE TRIGGER parties_search_vector_trigger
    BEFORE INSERT OR UPDATE ON parties
    FOR EACH ROW EXECUTE FUNCTION parties_search_vector_update();

UPDATE parties SET search_vector = 
    setweight(to_tsvector('portuguese', coalesce(name, '')), 'A') ||
    setweight(to_tsvector('portuguese', coalesce(document, '')), 'A') ||
    setweight(to_tsvector('portuguese', coalesce(trade_name, '')), 'B') ||
    setweight(to_tsvector('portuguese', coalesce(email, '')), 'C')
WHERE search_vector IS NULL;
