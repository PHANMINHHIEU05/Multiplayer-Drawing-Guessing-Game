CREATE TABLE IF NOT EXISTS word_aliases (
    id BIGSERIAL PRIMARY KEY,
    word_id BIGINT NOT NULL REFERENCES words(id) ON DELETE CASCADE,
    alias VARCHAR(100) NOT NULL UNIQUE
);

-- Seed initial aliases
INSERT INTO word_aliases (word_id, alias)
SELECT w.id, a.alias
FROM words w
CROSS JOIN (VALUES
    ('con meo', 'meo'),
    ('con cho', 'cho'),
    ('qua tao', 'tao'),
    ('ngoi nha', 'nha'),
    ('xe dap', 'xe đạp'),
    ('o to', 'xe o to'),
    ('o to', 'xe hoi'),
    ('may tinh', 'laptop'),
    ('dien thoại', 'dienthoai'),
    ('dien thoại', 'dt'),
    ('con ho', 'copt'),
    ('con voi', 'voi'),
    ('may bay', 'phi co'),
    ('tau hoa', 'xe lu')
) AS a(canonical_word, alias)
WHERE w.word = a.canonical_word
ON CONFLICT (alias) DO NOTHING;
