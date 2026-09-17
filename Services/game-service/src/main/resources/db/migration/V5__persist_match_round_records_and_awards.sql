ALTER TABLE game_results ADD COLUMN game_id VARCHAR(64);
ALTER TABLE game_results ADD COLUMN round_results_json TEXT NOT NULL DEFAULT '[]';
ALTER TABLE game_results ADD COLUMN awards_json TEXT NOT NULL DEFAULT '[]';

-- Preserve existing result rows while making subsequent rematches independently idempotent.
UPDATE game_results SET game_id = 'legacy-' || id::TEXT WHERE game_id IS NULL;
ALTER TABLE game_results ALTER COLUMN game_id SET NOT NULL;
CREATE UNIQUE INDEX ux_game_results_game_id ON game_results (game_id);
