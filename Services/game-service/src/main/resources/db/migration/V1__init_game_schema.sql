CREATE TABLE IF NOT EXISTS words (
    id BIGSERIAL PRIMARY KEY,
    word VARCHAR(100) NOT NULL UNIQUE,
    category VARCHAR(50) DEFAULT 'GENERAL',
    difficulty VARCHAR(20) DEFAULT 'MEDIUM'
);

CREATE TABLE IF NOT EXISTS game_results (
    id BIGSERIAL PRIMARY KEY,
    room_id VARCHAR(50) NOT NULL,
    winner_id VARCHAR(50),
    winner_username VARCHAR(100),
    total_rounds INT NOT NULL,
    finished_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS game_player_results (
    id BIGSERIAL PRIMARY KEY,
    game_result_id BIGINT NOT NULL REFERENCES game_results(id) ON DELETE CASCADE,
    player_id VARCHAR(50) NOT NULL,
    username VARCHAR(100) NOT NULL,
    final_score INT NOT NULL,
    rank INT NOT NULL
);

-- Seed initial word pool
INSERT INTO words (word, category, difficulty) VALUES
('con meo', 'ANIMAL', 'EASY'),
('con cho', 'ANIMAL', 'EASY'),
('qua tao', 'FRUIT', 'EASY'),
('ngoi nha', 'OBJECT', 'EASY'),
('xe dap', 'VEHICLE', 'EASY'),
('o to', 'VEHICLE', 'EASY'),
('mat troi', 'NATURE', 'EASY'),
('ngoi sao', 'NATURE', 'EASY'),
('may tinh', 'TECH', 'MEDIUM'),
('dien thoại', 'TECH', 'MEDIUM'),
('cai ghe', 'FURNITURE', 'EASY'),
('cai ban', 'FURNITURE', 'EASY'),
('con ho', 'ANIMAL', 'MEDIUM'),
('con voi', 'ANIMAL', 'MEDIUM'),
('may bay', 'VEHICLE', 'MEDIUM'),
('tau hoa', 'VEHICLE', 'MEDIUM'),
('bong da', 'SPORT', 'EASY'),
('cai cap', 'SCHOOL', 'EASY'),
('but chibi', 'SCHOOL', 'MEDIUM'),
('trai dat', 'NATURE', 'MEDIUM')
ON CONFLICT (word) DO NOTHING;
