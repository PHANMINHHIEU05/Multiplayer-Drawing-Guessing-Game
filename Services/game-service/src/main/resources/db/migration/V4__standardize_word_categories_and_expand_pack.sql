-- Normalize historical category labels into stable room-config identifiers.
UPDATE words
SET category = CASE UPPER(COALESCE(category, ''))
    WHEN 'ANIMAL' THEN 'ANIMALS'
    WHEN 'ANIMALS' THEN 'ANIMALS'
    WHEN 'FRUIT' THEN 'FOOD'
    WHEN 'FOOD' THEN 'FOOD'
    WHEN 'TECH' THEN 'TECHNOLOGY'
    WHEN 'TECHNOLOGY' THEN 'TECHNOLOGY'
    WHEN 'NATURE' THEN 'NATURE'
    WHEN 'OBJECT' THEN 'OBJECTS'
    WHEN 'OBJECTS' THEN 'OBJECTS'
    WHEN 'VEHICLE' THEN 'OBJECTS'
    WHEN 'FURNITURE' THEN 'OBJECTS'
    WHEN 'SCHOOL' THEN 'OBJECTS'
    WHEN 'SPORT' THEN 'OBJECTS'
    WHEN 'PLACE' THEN 'PLACES'
    WHEN 'PLACES' THEN 'PLACES'
    ELSE 'OBJECTS'
END;

UPDATE words SET category = 'PLACES' WHERE word = 'ngôi nhà';

-- Curated accented additions ensure each category has a useful playable pool.
INSERT INTO words (word, category) VALUES
    ('con thỏ', 'ANIMALS'), ('cá heo', 'ANIMALS'), ('con gấu', 'ANIMALS'),
    ('con rùa', 'ANIMALS'), ('con vịt', 'ANIMALS'), ('con khỉ', 'ANIMALS'),
    ('bánh mì', 'FOOD'), ('phở', 'FOOD'), ('cơm', 'FOOD'),
    ('quả chuối', 'FOOD'), ('dưa hấu', 'FOOD'), ('pizza', 'FOOD'),
    ('kem', 'FOOD'), ('trà sữa', 'FOOD'),
    ('cái kéo', 'OBJECTS'), ('chiếc ô', 'OBJECTS'), ('đồng hồ', 'OBJECTS'),
    ('cây đàn', 'OBJECTS'), ('quả bóng', 'OBJECTS'), ('máy ảnh', 'OBJECTS'),
    ('trường học', 'PLACES'), ('bệnh viện', 'PLACES'), ('công viên', 'PLACES'),
    ('nhà hàng', 'PLACES'), ('siêu thị', 'PLACES'), ('thư viện', 'PLACES'),
    ('sân bay', 'PLACES'),
    ('cầu vồng', 'NATURE'), ('đám mây', 'NATURE'), ('ngọn núi', 'NATURE'),
    ('dòng sông', 'NATURE'), ('bông hoa', 'NATURE'), ('cơn mưa', 'NATURE'),
    ('biển', 'NATURE'), ('cây xanh', 'NATURE'),
    ('robot', 'TECHNOLOGY'), ('wifi', 'TECHNOLOGY'), ('tivi', 'TECHNOLOGY'),
    ('máy chơi game', 'TECHNOLOGY'), ('vệ tinh', 'TECHNOLOGY'), ('máy tính bảng', 'TECHNOLOGY')
ON CONFLICT (word) DO UPDATE SET category = EXCLUDED.category;
