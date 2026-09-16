-- V3: Normalize Vietnamese canonical words to proper diacritics
-- Preserves IDs, relationships, and ensures clean volume upgrade without checksum conflicts.

-- 1. Remove obsolete alias that collides with the new canonical word 'xe đạp'
DELETE FROM word_aliases WHERE alias = 'xe đạp';

-- 2. Update existing words to standard Vietnamese with correct diacritics
UPDATE words SET word = 'con mèo' WHERE word = 'con meo';
UPDATE words SET word = 'con chó' WHERE word = 'con cho';
UPDATE words SET word = 'quả táo' WHERE word = 'qua tao';
UPDATE words SET word = 'ngôi nhà' WHERE word = 'ngoi nha';
UPDATE words SET word = 'xe đạp' WHERE word = 'xe dap';
UPDATE words SET word = 'ô tô' WHERE word = 'o to';
UPDATE words SET word = 'mặt trời' WHERE word = 'mat troi';
UPDATE words SET word = 'ngôi sao' WHERE word = 'ngoi sao';
UPDATE words SET word = 'máy tính' WHERE word = 'may tinh';
UPDATE words SET word = 'điện thoại' WHERE word = 'dien thoại';
UPDATE words SET word = 'cái ghế' WHERE word = 'cai ghe';
UPDATE words SET word = 'cái bàn' WHERE word = 'cai ban';
UPDATE words SET word = 'con hổ' WHERE word = 'con ho';
UPDATE words SET word = 'con voi' WHERE word = 'con voi';
UPDATE words SET word = 'máy bay' WHERE word = 'may bay';
UPDATE words SET word = 'tàu hỏa' WHERE word = 'tau hoa';
UPDATE words SET word = 'bóng đá' WHERE word = 'bong da';
UPDATE words SET word = 'cái cặp' WHERE word = 'cai cap';
UPDATE words SET word = 'bút chì' WHERE word = 'but chibi';
UPDATE words SET word = 'trái đất' WHERE word = 'trai dat';

-- 3. Update existing aliases to proper diacritics where appropriate
UPDATE word_aliases SET alias = 'mèo' WHERE alias = 'meo';
UPDATE word_aliases SET alias = 'chó' WHERE alias = 'cho';
UPDATE word_aliases SET alias = 'táo' WHERE alias = 'tao';
UPDATE word_aliases SET alias = 'nhà' WHERE alias = 'nha';
UPDATE word_aliases SET alias = 'xe ô tô' WHERE alias = 'xe o to';
UPDATE word_aliases SET alias = 'xe hơi' WHERE alias = 'xe hoi';
UPDATE word_aliases SET alias = 'cọp' WHERE alias = 'copt';
UPDATE word_aliases SET alias = 'phi cơ' WHERE alias = 'phi co';
UPDATE word_aliases SET alias = 'tàu lửa' WHERE alias = 'xe lu';
UPDATE word_aliases SET alias = 'đt' WHERE alias = 'dt';

-- 4. Seed supplementary aliases for newly accented words
INSERT INTO word_aliases (word_id, alias)
SELECT w.id, a.alias
FROM words w
CROSS JOIN (VALUES
    ('trái đất', 'địa cầu'),
    ('trái đất', 'quả đất'),
    ('xe đạp', 'xe cộ'),
    ('bút chì', 'bút việt')
) AS a(canonical_word, alias)
WHERE w.word = a.canonical_word
ON CONFLICT (alias) DO NOTHING;

